package com.bswap.server.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

class WhitelistResolver(
    private val jup: JupiterTokensClient,
    private val symbols: Set<String> = setOf(
        // Major liquid tokens
        "SOL", "USDC", "USDT", "JUP", "PYTH", "JTO", "RAY", "ORCA",
        // LST tokens
        "mSOL", "bSOL", "jitoSOL",
        // Large meme coins with good liquidity
        "BONK", "WIF", "POPCAT",
        // Other ecosystem tokens
        "TNSR", "HNT", "WEN", "SAMO"
    )
) {
    private val log = LoggerFactory.getLogger("WhitelistResolver")
    private val allowedMintsRef = AtomicReference<Set<String>>(emptySet())

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            val list = jup.getVerifiedTokens()
            
            // Map symbols to mints, taking first verified match for each symbol
            val mints = symbols.mapNotNull { sym ->
                list.firstOrNull { it.symbol.equals(sym, ignoreCase = true) }?.address
            }.toSet()

            allowedMintsRef.set(mints)
            log.info("Whitelist updated: ${mints.size} mints loaded from ${symbols.size} symbols")
            
            // Log missing symbols for debugging
            val missing = symbols.filterNot { s ->
                list.any { it.symbol.equals(s, true) }
            }
            if (missing.isNotEmpty()) {
                log.warn("Symbols not found in Jupiter verified list: $missing")
            }
            
            // Log first few mints for debugging
            if (mints.size > 0) {
                log.info("Sample whitelisted mints: ${mints.take(5)}")
            }
            
        } catch (e: Exception) {
            log.error("Failed to refresh whitelist: ${e.message}", e)
        }
    }

    fun isAllowed(mint: String): Boolean = allowedMintsRef.get().contains(mint)
    
    fun snapshot(): Set<String> = allowedMintsRef.get()
    
    fun getSymbols(): Set<String> = symbols
    
    fun getCacheSize(): Int = allowedMintsRef.get().size
}