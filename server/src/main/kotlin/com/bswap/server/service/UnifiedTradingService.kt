package com.bswap.server.service

import com.bswap.server.SolanaSwapBotConfig
import com.bswap.server.TradingRuntime
import com.bswap.server.config.*
import com.bswap.server.execution.EnhancedExecutionEngine
import com.bswap.server.execution.HyperliquidExecutionEngine
import com.bswap.server.stratagy.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Unified Trading Service that supports both Solana DEX and Hyperliquid
 * Provides seamless switching between exchanges and unified interface
 */
class UnifiedTradingService(
    private val solanaConfig: SolanaSwapBotConfig,
    private val hyperliquidConfig: HyperliquidConfig,
    private val enhancedConfig: EnhancedTradingConfig,
    private val runtime: TradingRuntime? = null
) {
    companion object {
        private val logger = LoggerFactory.getLogger(UnifiedTradingService::class.java)
        private const val STRATEGY_TICK_MS = 1000L
    }

    private val currentExchange = AtomicReference(hyperliquidConfig.exchangeType)
    private val isRunning = AtomicBoolean(false)
    
    // Services
    private var hyperliquidService: HyperliquidService? = null
    private var jupiterLiquidityService: JupiterLiquidityService? = null
    
    // Execution engines
    private var hyperliquidEngine: HyperliquidExecutionEngine? = null
    private var solanaEngine: EnhancedExecutionEngine? = null
    
    // Strategy manager
    private var strategyManager: EnhancedStrategyManager? = null
    
    private val tradingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tradingJob: Job? = null
    
    private val _tradeFlow = MutableSharedFlow<TradeEvent>()
    val tradeFlow: SharedFlow<TradeEvent> = _tradeFlow.asSharedFlow()
    
    data class TradeEvent(
        val exchange: ExchangeType,
        val symbol: String,
        val action: String,
        val price: Double?,
        val amount: Double?,
        val pnl: Double?,
        val success: Boolean,
        val timestamp: Long = System.currentTimeMillis(),
        val metadata: Map<String, Any> = emptyMap()
    )

    init {
        logger.info("🚀 Initializing Unified Trading Service")
        logger.info("📊 Default Exchange: ${currentExchange.get()}")
        initializeServices()
    }

    private fun initializeServices() {
        try {
            // Initialize Hyperliquid if enabled
            if (hyperliquidConfig.enabled) {
                logger.info("🔧 Initializing Hyperliquid services...")
                hyperliquidService = HyperliquidService(hyperliquidConfig)
                hyperliquidEngine = HyperliquidExecutionEngine(
                    hyperliquidConfig,
                    enhancedConfig,
                    hyperliquidService!!
                )
            }
            
            // Initialize Solana services
            if (runtime != null) {
                logger.info("🔧 Initializing Solana services...")
                val httpClient = HttpClient(CIO)
                jupiterLiquidityService = JupiterLiquidityService(httpClient)
                solanaEngine = EnhancedExecutionEngine(enhancedConfig, jupiterLiquidityService!!)
            }
            
            // Initialize strategy manager
            strategyManager = EnhancedStrategyManager(enhancedConfig)
            
            logger.info("✅ Services initialized successfully")
        } catch (e: Exception) {
            logger.error("❌ Failed to initialize services", e)
            throw e
        }
    }

    // =================================================================================================
    // EXCHANGE MANAGEMENT
    // =================================================================================================

    suspend fun switchExchange(newExchange: ExchangeType): Boolean {
        return try {
            logger.info("🔄 Switching exchange from ${currentExchange.get()} to $newExchange")
            
            // Stop current trading
            if (isRunning.get()) {
                stopTrading()
            }
            
            // Close all positions on current exchange
            when (currentExchange.get()) {
                ExchangeType.HYPERLIQUID -> {
                    hyperliquidEngine?.closeAllPositions("Exchange switch")
                }
                ExchangeType.SOLANA -> {
                    // Sell all SPL tokens on Solana
                    runtime?.allTokens()?.forEach { token ->
                        runtime.sell(token.address)
                    }
                }
            }
            
            // Switch exchange
            currentExchange.set(newExchange)
            
            // Restart trading if it was running
            if (isRunning.get()) {
                startTrading()
            }
            
            logger.info("✅ Successfully switched to $newExchange")
            true
        } catch (e: Exception) {
            logger.error("❌ Failed to switch exchange", e)
            false
        }
    }

    fun getCurrentExchange(): ExchangeType = currentExchange.get()

    // =================================================================================================
    // TRADING CONTROL
    // =================================================================================================

    fun startTrading() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("▶️ Starting unified trading service")
            
            tradingJob = tradingScope.launch {
                when (currentExchange.get()) {
                    ExchangeType.HYPERLIQUID -> runHyperliquidTrading()
                    ExchangeType.SOLANA -> runSolanaTrading()
                }
            }
            
            logger.info("✅ Trading started on ${currentExchange.get()}")
        }
    }

    fun stopTrading() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("⏹️ Stopping unified trading service")
            
            tradingJob?.cancel()
            
            // Stop engines
            hyperliquidEngine?.stop()
            
            logger.info("✅ Trading stopped")
        }
    }

    private suspend fun runHyperliquidTrading() {
        logger.info("🔄 Running Hyperliquid trading loop")
        
        while (isRunning.get() && currentExchange.get() == ExchangeType.HYPERLIQUID) {
            try {
                // Get active markets
                val markets = getActiveMarkets()
                
                for (market in markets) {
                    if (!isRunning.get()) break
                    
                    // Get market data
                    val marketData = getMarketData(market)
                    
                    // Generate strategy signal
                    val signal = strategyManager?.generateSignal(
                        symbol = market,
                        price = marketData.price,
                        volume = marketData.volume,
                        priceHistory = marketData.priceHistory
                    )
                    
                    if (signal != null && signal.action != Action.HOLD) {
                        // Execute trade
                        val result = hyperliquidEngine?.executeTrade(signal)
                        
                        // Emit trade event
                        _tradeFlow.emit(TradeEvent(
                            exchange = ExchangeType.HYPERLIQUID,
                            symbol = market,
                            action = signal.action.name,
                            price = result?.executedPrice,
                            amount = result?.executedSize,
                            pnl = null,
                            success = result?.success ?: false,
                            metadata = mapOf(
                                "confidence" to signal.confidence,
                                "strategy" to (signal.metadata["strategy"] ?: "unknown")
                            )
                        ))
                    }
                }
                
                delay(STRATEGY_TICK_MS)
            } catch (e: Exception) {
                logger.error("❌ Error in Hyperliquid trading loop", e)
                delay(5000)
            }
        }
    }

    private suspend fun runSolanaTrading() {
        logger.info("🔄 Running Solana trading loop")
        
        while (isRunning.get() && currentExchange.get() == ExchangeType.SOLANA) {
            try {
                if (runtime == null) {
                    logger.warn("⚠️ Solana runtime not available")
                    delay(10000)
                    continue
                }
                
                // Get tokens to monitor
                val tokens = runtime.allTokens()
                
                for (token in tokens) {
                    if (!isRunning.get()) break
                    
                    // Get token price
                    val price = runtime.getTokenUsdPrice(token.address) ?: continue
                    
                    // Generate strategy signal (simplified for Solana)
                    val signal = StrategySignal(
                        symbol = token.address,
                        action = determineAction(token.address, price),
                        confidence = 0.7,
                        metadata = mapOf("exchange" to "solana")
                    )
                    
                    // Execute trade
                    when (signal.action) {
                        Action.BUY -> {
                            val success = runtime.buy(token.address)
                            _tradeFlow.emit(TradeEvent(
                                exchange = ExchangeType.SOLANA,
                                symbol = token.address,
                                action = "BUY",
                                price = price,
                                amount = solanaConfig.solAmountToTrade.toDouble(),
                                pnl = null,
                                success = success
                            ))
                        }
                        Action.SELL -> {
                            val success = runtime.sell(token.address)
                            _tradeFlow.emit(TradeEvent(
                                exchange = ExchangeType.SOLANA,
                                symbol = token.address,
                                action = "SELL",
                                price = price,
                                amount = null,
                                pnl = null,
                                success = success
                            ))
                        }
                        Action.HOLD -> {
                            // Do nothing
                        }
                    }
                }
                
                delay(solanaConfig.strategyTickMs)
            } catch (e: Exception) {
                logger.error("❌ Error in Solana trading loop", e)
                delay(5000)
            }
        }
    }

    private suspend fun getActiveMarkets(): List<String> {
        return when (currentExchange.get()) {
            ExchangeType.HYPERLIQUID -> {
                // Get top volume markets from Hyperliquid (using correct symbol format)
                listOf("BTC", "ETH", "SOL", "ARB", "OP")
            }
            ExchangeType.SOLANA -> {
                // Get active SPL tokens
                runtime?.allTokens()?.map { it.address } ?: emptyList()
            }
        }
    }

    private suspend fun getMarketData(symbol: String): MarketData {
        return when (currentExchange.get()) {
            ExchangeType.HYPERLIQUID -> {
                val ticker = hyperliquidService?.getTicker(symbol)
                val ohlcv = hyperliquidService?.getOHLCV(symbol)
                
                MarketData(
                    symbol = symbol,
                    price = ticker?.get("last") as? Double ?: 0.0,
                    volume = ticker?.get("baseVolume") as? Double ?: 0.0,
                    priceHistory = ohlcv?.map { it[4] as Double } ?: emptyList()
                )
            }
            ExchangeType.SOLANA -> {
                val price = runtime?.getTokenUsdPrice(symbol) ?: 0.0
                val priceHistory = runtime?.getPriceHistory?.invoke(symbol) ?: emptyList()
                
                MarketData(
                    symbol = symbol,
                    price = price,
                    volume = 0.0, // Volume not available for SPL tokens
                    priceHistory = priceHistory
                )
            }
        }
    }

    // Public method to get all markets in the format expected by trading routes
    suspend fun getMarkets(): List<com.bswap.server.routes.MarketData> {
        logger.info("📊 Getting markets for exchange: ${currentExchange.get()}")
        
        return when (currentExchange.get()) {
            ExchangeType.HYPERLIQUID -> {
                try {
                    val markets = getActiveMarkets()
                    logger.info("📈 Found ${markets.size} Hyperliquid markets: $markets")
                    
                    markets.mapNotNull { symbol ->
                        try {
                            val ticker = hyperliquidService?.getTicker(symbol)
                            val fundingRate = hyperliquidService?.getFundingRate(symbol)
                            
                            logger.debug("💹 Market data for $symbol: ticker=$ticker, funding=$fundingRate")
                            
                            com.bswap.server.routes.MarketData(
                                symbol = symbol,
                                price = ticker?.get("last") as? Double ?: 50000.0,
                                bid = ticker?.get("bid") as? Double ?: 49950.0,
                                ask = ticker?.get("ask") as? Double ?: 50050.0,
                                volume24h = ticker?.get("baseVolume") as? Double ?: 1000000.0,
                                change24h = 0.025, // Would need historical data
                                fundingRate = fundingRate?.fundingRate,
                                markPrice = fundingRate?.markPrice,
                                indexPrice = fundingRate?.indexPrice,
                                openInterest = fundingRate?.openInterest
                            )
                        } catch (e: Exception) {
                            logger.error("❌ Error getting market data for $symbol", e)
                            null
                        }
                    }
                } catch (e: Exception) {
                    logger.error("❌ Error getting Hyperliquid markets", e)
                    emptyList()
                }
            }
            ExchangeType.SOLANA -> {
                try {
                    val tokens = runtime?.allTokens() ?: emptyList()
                    logger.info("🚀 Found ${tokens.size} Solana tokens")
                    
                    tokens.mapNotNull { token ->
                        try {
                            val price = runtime?.getTokenUsdPrice(token.address) ?: 0.0
                            
                            com.bswap.server.routes.MarketData(
                                symbol = token.address,
                                price = price,
                                bid = price * 0.999,
                                ask = price * 1.001,
                                volume24h = 0.0, // Not available for SPL tokens
                                change24h = 0.0, // Would need historical data
                                fundingRate = null, // Not applicable for spot
                                markPrice = price,
                                indexPrice = price,
                                openInterest = null // Not applicable for spot
                            )
                        } catch (e: Exception) {
                            logger.error("❌ Error getting market data for ${token.address}", e)
                            null
                        }
                    }
                } catch (e: Exception) {
                    logger.error("❌ Error getting Solana markets", e)
                    emptyList()
                }
            }
        }
    }

    private fun determineAction(symbol: String, price: Double): Action {
        // Simplified logic for Solana tokens
        val status = runtime?.status(symbol)
        return when (status?.state) {
            null -> Action.BUY // New token
            else -> {
                // Check if we should sell based on hold time
                val holdTime = System.currentTimeMillis() - status.createdAt
                if (holdTime > 60000) Action.SELL else Action.HOLD
            }
        }
    }

    data class MarketData(
        val symbol: String,
        val price: Double,
        val volume: Double,
        val priceHistory: List<Double>
    )

    // =================================================================================================
    // BALANCE & POSITION MANAGEMENT
    // =================================================================================================

    suspend fun getBalance(): Map<String, Double> {
        return when (currentExchange.get()) {
            ExchangeType.HYPERLIQUID -> {
                val balances = hyperliquidService?.fetchBalances() ?: emptyMap()
                balances.mapValues { it.value.total }
            }
            ExchangeType.SOLANA -> {
                val tokens = runtime?.allTokens() ?: emptyList()
                tokens.associate { it.address to (it.tokenAmount.uiAmount ?: 0.0) }
            }
        }
    }

    suspend fun getPositions(): List<Position> {
        return when (currentExchange.get()) {
            ExchangeType.HYPERLIQUID -> {
                val positions = hyperliquidEngine?.getActivePositions() ?: emptyList()
                positions.map { pos ->
                    Position(
                        symbol = pos.symbol,
                        side = pos.side.name,
                        size = pos.size,
                        entryPrice = pos.entryPrice,
                        markPrice = pos.markPrice,
                        pnl = pos.unrealizedPnl,
                        leverage = pos.leverage
                    )
                }
            }
            ExchangeType.SOLANA -> {
                val tokens = runtime?.allTokens() ?: emptyList()
                tokens.map { token ->
                    val price = runtime?.getTokenUsdPrice(token.address) ?: 0.0
                    Position(
                        symbol = token.address,
                        side = "LONG",
                        size = token.tokenAmount.uiAmount ?: 0.0,
                        entryPrice = price, // Simplified
                        markPrice = price,
                        pnl = 0.0, // Would need to track entry price
                        leverage = 1.0
                    )
                }
            }
        }
    }

    data class Position(
        val symbol: String,
        val side: String,
        val size: Double,
        val entryPrice: Double,
        val markPrice: Double,
        val pnl: Double,
        val leverage: Double
    )

    suspend fun closePosition(symbol: String): Boolean {
        return try {
            when (currentExchange.get()) {
                ExchangeType.HYPERLIQUID -> {
                    val result = hyperliquidEngine?.closePosition(symbol)
                    result?.success ?: false
                }
                ExchangeType.SOLANA -> {
                    runtime?.sell(symbol) ?: false
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Failed to close position for $symbol", e)
            false
        }
    }

    suspend fun closeAllPositions(): Boolean {
        return try {
            when (currentExchange.get()) {
                ExchangeType.HYPERLIQUID -> {
                    hyperliquidEngine?.closeAllPositions("Manual close all")
                    true
                }
                ExchangeType.SOLANA -> {
                    runtime?.allTokens()?.forEach { token ->
                        runtime.sell(token.address)
                    }
                    true
                }
            }
        } catch (e: Exception) {
            logger.error("❌ Failed to close all positions", e)
            false
        }
    }

    // =================================================================================================
    // STATISTICS & MONITORING
    // =================================================================================================

    suspend fun getStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>(
            "exchange" to currentExchange.get().name,
            "isRunning" to isRunning.get()
        )
        
        when (currentExchange.get()) {
            ExchangeType.HYPERLIQUID -> {
                hyperliquidEngine?.getStats()?.let { stats.putAll(it) }
            }
            ExchangeType.SOLANA -> {
                stats["tokenCount"] = runtime?.allTokens()?.size ?: 0
            }
        }
        
        return stats
    }

    suspend fun getPnL(): Pair<Double, Double> {
        return when (currentExchange.get()) {
            ExchangeType.HYPERLIQUID -> {
                hyperliquidEngine?.getTotalPnL() ?: Pair(0.0, 0.0)
            }
            ExchangeType.SOLANA -> {
                // Simplified PnL calculation for Solana
                Pair(0.0, 0.0)
            }
        }
    }

    // =================================================================================================
    // EMERGENCY CONTROLS
    // =================================================================================================

    suspend fun emergencyStop(reason: String = "Manual emergency stop") {
        logger.error("🚨 EMERGENCY STOP TRIGGERED: $reason")
        
        // Stop trading
        stopTrading()
        
        // Close all positions
        when (currentExchange.get()) {
            ExchangeType.HYPERLIQUID -> {
                hyperliquidEngine?.emergencyStop(reason)
            }
            ExchangeType.SOLANA -> {
                runtime?.allTokens()?.forEach { token ->
                    try {
                        runtime.sell(token.address)
                    } catch (e: Exception) {
                        logger.error("Failed to sell ${token.address}", e)
                    }
                }
            }
        }
        
        logger.info("✅ Emergency stop completed")
    }

    fun shutdown() {
        logger.info("🛑 Shutting down Unified Trading Service")
        
        // Stop trading
        stopTrading()
        
        // Shutdown services
        hyperliquidEngine?.shutdown()
        solanaEngine?.shutdown()
        hyperliquidService?.shutdown()
        
        // Cancel scope
        tradingScope.cancel()
        
        logger.info("✅ Shutdown complete")
    }
}