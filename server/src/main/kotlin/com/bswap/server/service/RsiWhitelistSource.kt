package com.bswap.server.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

/**
 * Simple whitelist source for RSI trading strategy
 * Contains the list of tokens that the bot is allowed to trade
 */
class RsiWhitelistSource {
    private val logger = LoggerFactory.getLogger(javaClass)
    
    // Default whitelist tokens for RSI trading
    private val defaultWhitelist = setOf(
        // Major liquid tokens
        "So11111111111111111111111111111111111111112", // SOL
        "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", // USDC
        "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", // USDT
        "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN", // JUP
        "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3", // PYTH
        "jtojtomepa8beP8AuQc6eXt5FriJwfFMwQx2v2f9mCL", // JTO
        "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R", // RAY
        "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE", // ORCA
        
        // Liquid staking tokens
        "mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So", // mSOL
        "bSo13r4TkiE4KumL71LsHTPpL2euBYLFx6h9HP3piy1", // bSOL
        "J1toso1uCk3RLmjorhTtrVwY9HJ7X8V9yYac6Y7kGCPn", // jitoSOL
        
        // Popular meme tokens with good liquidity
        "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263", // BONK
        "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm", // WIF  
        "7GCihgDB8fe6KNjn2MYtkzZcRjQy3t9GHdC8uHYmW2hr", // POPCAT
        
        // Ecosystem tokens
        "TNSRxcUxoT9xBG3de7PiJyTDYu7kskLqcpddxnEJAS6", // TNSR
        "hntyVP6YFm1Hg25TN9WGLqM12b8TQmcknKrdu1oxWux", // HNT
        "WENWENvqqNya429ubCdR81ZmD69brwQaaBYY6p3LCpk", // WEN
        "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU"  // SAMO
    )
    
    private val _whitelist = MutableStateFlow(defaultWhitelist)
    val whitelist: StateFlow<Set<String>> = _whitelist.asStateFlow()
    
    /**
     * Check if a token mint is in the whitelist
     */
    fun isWhitelisted(mint: String): Boolean {
        return _whitelist.value.contains(mint)
    }
    
    /**
     * Add a token to the whitelist
     */
    fun addToken(mint: String) {
        _whitelist.value = _whitelist.value + mint
        logger.info("Added token to RSI whitelist: $mint")
    }
    
    /**
     * Remove a token from the whitelist
     */
    fun removeToken(mint: String) {
        _whitelist.value = _whitelist.value - mint
        logger.info("Removed token from RSI whitelist: $mint")
    }
    
    /**
     * Update the entire whitelist
     */
    fun updateWhitelist(newWhitelist: Set<String>) {
        _whitelist.value = newWhitelist
        logger.info("Updated RSI whitelist with ${newWhitelist.size} tokens")
    }
    
    /**
     * Clear the whitelist
     */
    fun clearWhitelist() {
        _whitelist.value = emptySet()
        logger.info("Cleared RSI whitelist")
    }
    
    /**
     * Reset to default whitelist
     */
    fun resetToDefault() {
        _whitelist.value = defaultWhitelist
        logger.info("Reset RSI whitelist to default ${defaultWhitelist.size} tokens")
    }
    
    /**
     * Get current whitelist size
     */
    fun getWhitelistSize(): Int = _whitelist.value.size
    
    /**
     * Get all whitelisted tokens
     */
    fun getWhitelistedTokens(): Set<String> = _whitelist.value.toSet()
}