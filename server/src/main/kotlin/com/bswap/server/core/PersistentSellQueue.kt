package com.bswap.server.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.random.Random

/**
 * Persistent sell queue with SQLite backing for reliability across restarts
 * Implements idempotency, retries with exponential backoff, and graceful shutdown
 */
class PersistentSellQueue(
    private val config: PersistentSellQueueConfig,
    private val sellExecutor: suspend (PersistentSellOrder) -> SellResult,
    private val dbPath: String = "data/sell_queue.db"
) {
    private val log = LoggerFactory.getLogger("PersistentSellQueue")
    private val json = Json { ignoreUnknownKeys = true }
    
    private val isRunning = AtomicBoolean(false)
    private val workers = mutableListOf<Job>()
    private val newJobChannel = Channel<String>(Channel.UNLIMITED)
    
    private lateinit var connection: Connection
    private val metrics = SellQueueMetrics()
    
    init {
        initializeDatabase()
    }
    
    /**
     * Start the sell queue workers
     */
    suspend fun start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("Starting PersistentSellQueue with ${config.workerCount} workers")
            
            // Resume pending jobs from database
            resumePendingJobs()
            
            // Start worker coroutines
            repeat(config.workerCount) { workerId ->
                val worker = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    runWorker(workerId)
                }
                workers.add(worker)
            }
            
            log.info("PersistentSellQueue started with ${workers.size} workers")
        }
    }
    
    /**
     * Stop the sell queue gracefully
     */
    suspend fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("Stopping PersistentSellQueue...")
            
            newJobChannel.close()
            
            // Wait for workers to finish current jobs
            workers.forEach { worker ->
                worker.cancelAndJoin()
            }
            workers.clear()
            
            connection.close()
            log.info("PersistentSellQueue stopped")
        }
    }
    
    /**
     * Enqueue a sell order
     */
    suspend fun enqueue(sellOrder: PersistentSellOrder): String {
        val jobId = generateJobId()
        val now = Instant.now()
        
        // Store in database
        val stmt = connection.prepareStatement("""
            INSERT INTO sell_jobs (id, mint, amount_raw, reason, status, created_at, idempotency_key, retry_count, last_error)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0, NULL)
        """)
        
        stmt.setString(1, jobId)
        stmt.setString(2, sellOrder.mint)
        stmt.setString(3, sellOrder.amountRaw.toPlainString())
        stmt.setString(4, sellOrder.reason)
        stmt.setString(5, SellJobStatus.QUEUED.name)
        stmt.setLong(6, now.toEpochMilli())
        stmt.setString(7, sellOrder.idempotencyKey)
        
        stmt.executeUpdate()
        stmt.close()
        
        // Notify workers
        newJobChannel.trySend(jobId)
        
        metrics.recordEnqueue(sellOrder.mint)
        log.info("Enqueued sell job: $jobId for ${sellOrder.mint} (${sellOrder.reason})")
        
        return jobId
    }
    
    /**
     * Check if a sell order with the same idempotency key already exists
     */
    suspend fun isDuplicate(idempotencyKey: String): Boolean {
        val stmt = connection.prepareStatement("""
            SELECT COUNT(*) FROM sell_jobs WHERE idempotency_key = ?
        """)
        stmt.setString(1, idempotencyKey)
        val rs = stmt.executeQuery()
        rs.next()
        val count = rs.getInt(1)
        rs.close()
        stmt.close()
        return count > 0
    }
    
    /**
     * Get queue statistics
     */
    fun getStats(): SellQueueStats {
        val stmt = connection.prepareStatement("""
            SELECT status, COUNT(*) as count FROM sell_jobs 
            WHERE created_at > ? 
            GROUP BY status
        """)
        stmt.setLong(1, System.currentTimeMillis() - 86400000) // Last 24 hours
        
        val rs = stmt.executeQuery()
        val statusCounts = mutableMapOf<String, Int>()
        
        while (rs.next()) {
            statusCounts[rs.getString("status")] = rs.getInt("count")
        }
        rs.close()
        stmt.close()
        
        return SellQueueStats(
            queuedJobs = statusCounts[SellJobStatus.QUEUED.name] ?: 0,
            processingJobs = statusCounts[SellJobStatus.PROCESSING.name] ?: 0,
            completedJobs = statusCounts[SellJobStatus.COMPLETED.name] ?: 0,
            failedJobs = statusCounts[SellJobStatus.FAILED.name] ?: 0,
            retryingJobs = statusCounts[SellJobStatus.RETRYING.name] ?: 0,
            totalEnqueued = metrics.totalEnqueued.get(),
            totalProcessed = metrics.totalProcessed.get(),
            totalFailed = metrics.totalFailed.get()
        )
    }
    
    /**
     * Get recent job history
     */
    fun getRecentJobs(limit: Int = 50): List<SellJobInfo> {
        val stmt = connection.prepareStatement("""
            SELECT * FROM sell_jobs 
            ORDER BY created_at DESC 
            LIMIT ?
        """)
        stmt.setInt(1, limit)
        
        val rs = stmt.executeQuery()
        val jobs = mutableListOf<SellJobInfo>()
        
        while (rs.next()) {
            jobs.add(SellJobInfo(
                id = rs.getString("id"),
                mint = rs.getString("mint"),
                amountRaw = BigDecimal(rs.getString("amount_raw")),
                reason = rs.getString("reason"),
                status = SellJobStatus.valueOf(rs.getString("status")),
                createdAt = Instant.ofEpochMilli(rs.getLong("created_at")),
                processedAt = rs.getLong("processed_at").takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) },
                retryCount = rs.getInt("retry_count"),
                lastError = rs.getString("last_error")
            ))
        }
        rs.close()
        stmt.close()
        
        return jobs
    }
    
    private fun initializeDatabase() {
        // Create data directory if it doesn't exist
        File(dbPath).parentFile?.mkdirs()
        
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        connection.autoCommit = true
        
        // Create tables
        connection.createStatement().executeUpdate("""
            CREATE TABLE IF NOT EXISTS sell_jobs (
                id TEXT PRIMARY KEY,
                mint TEXT NOT NULL,
                amount_raw TEXT NOT NULL,
                reason TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                processed_at INTEGER DEFAULT NULL,
                idempotency_key TEXT UNIQUE NOT NULL,
                retry_count INTEGER DEFAULT 0,
                last_error TEXT DEFAULT NULL,
                next_retry_at INTEGER DEFAULT NULL
            )
        """)
        
        // Create indexes
        connection.createStatement().executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_sell_jobs_status ON sell_jobs(status)
        """)
        connection.createStatement().executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_sell_jobs_created_at ON sell_jobs(created_at)
        """)
        connection.createStatement().executeUpdate("""
            CREATE INDEX IF NOT EXISTS idx_sell_jobs_next_retry ON sell_jobs(next_retry_at)
        """)
        
        log.info("Initialized sell queue database at: $dbPath")
    }
    
    private suspend fun resumePendingJobs() {
        val stmt = connection.prepareStatement("""
            SELECT id FROM sell_jobs 
            WHERE status IN (?, ?) 
            OR (status = ? AND next_retry_at <= ?)
            ORDER BY created_at ASC
        """)
        stmt.setString(1, SellJobStatus.QUEUED.name)
        stmt.setString(2, SellJobStatus.PROCESSING.name)
        stmt.setString(3, SellJobStatus.RETRYING.name)
        stmt.setLong(4, System.currentTimeMillis())
        
        val rs = stmt.executeQuery()
        val resumedJobs = mutableListOf<String>()
        
        while (rs.next()) {
            val jobId = rs.getString("id")
            resumedJobs.add(jobId)
            newJobChannel.trySend(jobId)
        }
        rs.close()
        stmt.close()
        
        if (resumedJobs.isNotEmpty()) {
            log.info("Resumed ${resumedJobs.size} pending sell jobs")
        }
    }
    
    private suspend fun runWorker(workerId: Int) {
        log.info("Worker $workerId started")
        
        try {
            while (isRunning.get()) {
                try {
                    // Wait for job or timeout
                    val jobId = withTimeoutOrNull(config.workerPollTimeoutMs) {
                        newJobChannel.receive()
                    }
                    
                    if (jobId != null) {
                        processJob(workerId, jobId)
                    }
                    
                    // Also check for retry jobs
                    checkRetryJobs(workerId)
                    
                } catch (e: CancellationException) {
                    log.debug("Worker $workerId cancelled")
                    break
                } catch (e: Exception) {
                    log.error("Worker $workerId error: ${e.message}", e)
                    delay(1000) // Brief pause before continuing
                }
            }
        } finally {
            log.info("Worker $workerId stopped")
        }
    }
    
    private suspend fun processJob(workerId: Int, jobId: String) {
        val job = getJob(jobId) ?: return
        
        if (job.status != SellJobStatus.QUEUED && job.status != SellJobStatus.RETRYING) {
            return // Job already processed
        }
        
        log.info("Worker $workerId processing job $jobId: ${job.mint}")
        
        // Mark as processing
        updateJobStatus(jobId, SellJobStatus.PROCESSING)
        
        try {
            val sellOrder = PersistentSellOrder(
                mint = job.mint,
                amountRaw = job.amountRaw,
                reason = job.reason,
                idempotencyKey = job.idempotencyKey
            )
            
            val result = sellExecutor(sellOrder)
            
            when (result) {
                is SellResult.Success -> {
                    updateJobStatus(jobId, SellJobStatus.COMPLETED, processedAt = Instant.now())
                    metrics.recordSuccess(job.mint)
                    log.info("Job $jobId completed successfully: ${result.transactionId}")
                }
                is SellResult.Failure -> {
                    handleJobFailure(jobId, job, result.error, result.isRetryable)
                }
            }
            
        } catch (e: Exception) {
            log.error("Job $jobId execution failed: ${e.message}", e)
            handleJobFailure(jobId, job, e.message ?: "Unknown error", isRetryable = true)
        }
        
        // Add spacing between jobs
        if (config.jobSpacingMs > 0) {
            delay(config.jobSpacingMs)
        }
    }
    
    private suspend fun handleJobFailure(jobId: String, job: SellJobInfo, error: String, isRetryable: Boolean) {
        val newRetryCount = job.retryCount + 1
        
        if (isRetryable && newRetryCount <= config.maxRetries) {
            // Schedule retry with exponential backoff
            val backoffMs = calculateBackoff(newRetryCount)
            val nextRetryAt = Instant.now().plusMillis(backoffMs)
            
            updateJobForRetry(jobId, newRetryCount, error, nextRetryAt)
            metrics.recordRetry(job.mint)
            
            log.warn("Job $jobId failed (attempt $newRetryCount/${config.maxRetries}), retrying in ${backoffMs}ms: $error")
        } else {
            // Mark as failed
            updateJobStatus(jobId, SellJobStatus.FAILED, error = error)
            metrics.recordFailure(job.mint)
            
            log.error("Job $jobId permanently failed after $newRetryCount attempts: $error")
        }
    }
    
    private suspend fun checkRetryJobs(workerId: Int) {
        val now = System.currentTimeMillis()
        val stmt = connection.prepareStatement("""
            SELECT id FROM sell_jobs 
            WHERE status = ? AND next_retry_at <= ?
            ORDER BY next_retry_at ASC
            LIMIT 10
        """)
        stmt.setString(1, SellJobStatus.RETRYING.name)
        stmt.setLong(2, now)
        
        val rs = stmt.executeQuery()
        while (rs.next()) {
            val jobId = rs.getString("id")
            newJobChannel.trySend(jobId)
        }
        rs.close()
        stmt.close()
    }
    
    private fun getJob(jobId: String): SellJobInfo? {
        val stmt = connection.prepareStatement("""
            SELECT * FROM sell_jobs WHERE id = ?
        """)
        stmt.setString(1, jobId)
        
        val rs = stmt.executeQuery()
        val job = if (rs.next()) {
            SellJobInfo(
                id = rs.getString("id"),
                mint = rs.getString("mint"),
                amountRaw = BigDecimal(rs.getString("amount_raw")),
                reason = rs.getString("reason"),
                status = SellJobStatus.valueOf(rs.getString("status")),
                createdAt = Instant.ofEpochMilli(rs.getLong("created_at")),
                processedAt = rs.getLong("processed_at").takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) },
                retryCount = rs.getInt("retry_count"),
                lastError = rs.getString("last_error"),
                idempotencyKey = rs.getString("idempotency_key")
            )
        } else null
        
        rs.close()
        stmt.close()
        return job
    }
    
    private fun updateJobStatus(
        jobId: String, 
        status: SellJobStatus, 
        processedAt: Instant? = null,
        error: String? = null
    ) {
        val stmt = connection.prepareStatement("""
            UPDATE sell_jobs 
            SET status = ?, processed_at = ?, last_error = ?
            WHERE id = ?
        """)
        stmt.setString(1, status.name)
        stmt.setLong(2, processedAt?.toEpochMilli() ?: 0)
        stmt.setString(3, error)
        stmt.setString(4, jobId)
        stmt.executeUpdate()
        stmt.close()
    }
    
    private fun updateJobForRetry(
        jobId: String,
        retryCount: Int,
        error: String,
        nextRetryAt: Instant
    ) {
        val stmt = connection.prepareStatement("""
            UPDATE sell_jobs 
            SET status = ?, retry_count = ?, last_error = ?, next_retry_at = ?
            WHERE id = ?
        """)
        stmt.setString(1, SellJobStatus.RETRYING.name)
        stmt.setInt(2, retryCount)
        stmt.setString(3, error)
        stmt.setLong(4, nextRetryAt.toEpochMilli())
        stmt.setString(5, jobId)
        stmt.executeUpdate()
        stmt.close()
    }
    
    private fun calculateBackoff(retryCount: Int): Long {
        val baseDelay = config.baseRetryDelayMs
        val maxDelay = config.maxRetryDelayMs
        val jitter = Random.nextLong(0, 1000)
        
        val exponentialDelay = (baseDelay * Math.pow(2.0, retryCount.toDouble())).toLong()
        return min(exponentialDelay, maxDelay) + jitter
    }
    
    private fun generateJobId(): String = UUID.randomUUID().toString()
}

/**
 * Configuration for the persistent sell queue
 */
data class PersistentSellQueueConfig(
    val workerCount: Int = 2,
    val maxRetries: Int = 3,
    val baseRetryDelayMs: Long = 1000,
    val maxRetryDelayMs: Long = 30000,
    val jobSpacingMs: Long = 500,
    val workerPollTimeoutMs: Long = 5000
)

/**
 * Persistent sell order data class
 */
@Serializable
data class PersistentSellOrder(
    val mint: String,
    @Contextual val amountRaw: BigDecimal,
    val reason: String,
    val idempotencyKey: String = UUID.randomUUID().toString()
)

/**
 * Sell execution result
 */
sealed class SellResult {
    data class Success(val transactionId: String, val actualAmount: BigDecimal) : SellResult()
    data class Failure(val error: String, val isRetryable: Boolean = true) : SellResult()
}

/**
 * Job status enumeration
 */
enum class SellJobStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRYING
}

/**
 * Job information data class
 */
data class SellJobInfo(
    val id: String,
    val mint: String,
    val amountRaw: BigDecimal,
    val reason: String,
    val status: SellJobStatus,
    val createdAt: Instant,
    val processedAt: Instant? = null,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val idempotencyKey: String = ""
)

/**
 * Queue statistics
 */
data class SellQueueStats(
    val queuedJobs: Int,
    val processingJobs: Int,
    val completedJobs: Int,
    val failedJobs: Int,
    val retryingJobs: Int,
    val totalEnqueued: Long,
    val totalProcessed: Long,
    val totalFailed: Long
)

/**
 * Metrics collection for the sell queue
 */
private class SellQueueMetrics {
    val totalEnqueued = java.util.concurrent.atomic.AtomicLong(0)
    val totalProcessed = java.util.concurrent.atomic.AtomicLong(0)
    val totalFailed = java.util.concurrent.atomic.AtomicLong(0)
    private val retriesByMint = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    
    fun recordEnqueue(mint: String) {
        totalEnqueued.incrementAndGet()
    }
    
    fun recordSuccess(mint: String) {
        totalProcessed.incrementAndGet()
    }
    
    fun recordFailure(mint: String) {
        totalFailed.incrementAndGet()
    }
    
    fun recordRetry(mint: String) {
        retriesByMint.computeIfAbsent(mint) { java.util.concurrent.atomic.AtomicLong(0) }.incrementAndGet()
    }
}