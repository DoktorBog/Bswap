package com.bswap.server.examples

import com.bswap.server.SolanaTokenSwapBot
import com.bswap.server.SolanaSwapBotConfig
import com.bswap.server.StrategyType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Simple example showing how whitelist works with RSI strategy
 */
fun main() = runBlocking {
    
    println("=== Simple Whitelist Trading Example ===")
    
    // 1. Create bot with RSI strategy (or any other strategy)
    val config = SolanaSwapBotConfig(
        strategySettings = com.bswap.server.TradingStrategySettings(
            type = StrategyType.RSI_BASED // Can be any strategy
        )
    )
    
    val bot = SolanaTokenSwapBot(config = config)
    
    // 2. Start the bot - it will automatically observe whitelist tokens
    bot.start()
    println("✅ Bot started with ${config.strategySettings.type} strategy")
    println("✅ Bot will only trade whitelisted tokens")
    
    // 3. Show current whitelist
    val whitelist = bot.getWhitelistSource()
    println("\n📋 Current whitelist has ${whitelist.getWhitelistSize()} tokens:")
    whitelist.getWhitelistedTokens().take(5).forEach { mint ->
        println("  • ${mint.take(8)}...")
    }
    
    // 4. Add a new token to whitelist
    val newToken = "NewTokenMint123456789"
    bot.addToWhitelist(newToken)
    println("\n✅ Added new token to whitelist: ${newToken.take(8)}...")
    
    // 5. Remove a token from whitelist
    bot.removeFromWhitelist(newToken)
    println("✅ Removed token from whitelist: ${newToken.take(8)}...")
    
    // 6. Run for a short time
    println("\n🔄 Bot is running and observing whitelist tokens...")
    println("   Strategy: ${config.strategySettings.type}")
    println("   Whitelist size: ${whitelist.getWhitelistSize()}")
    println("   Bot will check whitelisted tokens every 30 seconds")
    
    delay(10000) // Run for 10 seconds
    
    // 7. Stop the bot
    bot.stop()
    println("\n✅ Bot stopped")
    
    println("\n=== How it works ===")
    println("1. Bot has a whitelist of tokens (SOL, USDC, JUP, etc)")
    println("2. Every 30 seconds, bot checks all whitelisted tokens")
    println("3. For each token, bot runs the configured strategy (RSI, etc)")
    println("4. If strategy signals BUY, bot buys the token")
    println("5. Strategy handles SELL decisions based on its logic")
}