package com.bswap.server.config

/**
 * Simple configuration object that reads endpoint URLs from environment variables.
 * Defaults match the previously hard coded values so existing behaviour does not change
 * when no environment variables are provided.
 */
object ServerConfig {
    val rpcUrl: String = System.getenv("RPC_URL")
        ?: "https://api.mainnet-beta.solana.com"
    val jupiterApiUrl: String = System.getenv("JUPITER_API_URL") ?: "https://quote-api.jup.ag/v6"

    val dexTokenProfilesUrl: String = System.getenv("DEX_TOKEN_PROFILES_URL")
        ?: "https://api.dexscreener.com/token-profiles/latest/v1"
    val dexTokenBoostsLatestUrl: String = System.getenv("DEX_TOKEN_BOOSTS_LATEST_URL")
        ?: "https://api.dexscreener.com/token-boosts/latest/v1"
    val dexTokenBoostsTopUrl: String = System.getenv("DEX_TOKEN_BOOSTS_TOP_URL")
        ?: "https://api.dexscreener.com/token-boosts/top/v1"
    val dexOrdersBaseUrl: String = System.getenv("DEX_ORDERS_BASE_URL") ?: "https://api.dexscreener.com/orders/v1"
    val dexPairsBaseUrl: String = System.getenv("DEX_PAIRS_BASE_URL") ?: "https://api.dexscreener.com/latest/dex"

    val jitoBundlerEndpoints: List<String> = (System.getenv("JITO_BUNDLER_ENDPOINTS")
        ?: "https://mainnet.block-engine.jito.wtf/api/v1/bundles,https://amsterdam.mainnet.block-engine.jito.wtf/api/v1/bundles,https://frankfurt.mainnet.block-engine.jito.wtf/api/v1/bundles,https://ny.mainnet.block-engine.jito.wtf/api/v1/bundles,https://tokyo.mainnet.block-engine.jito.wtf/api/v1/bundles,https://slc.mainnet.block-engine.jito.wtf/api/v1/bundles")
        .split(',').map { it.trim() }

    val pumpFunWsUrl: String = System.getenv("PUMPFUN_WS_URL") ?: "wss://pumpportal.fun/api/data"
    val pumpFunBaseUrl: String = System.getenv("PUMPFUN_BASE_URL") ?: "https://frontend-api-v3.pump.fun/coins"

    val logMonitorUrl: String = System.getenv("LOG_MONITOR_URL") ?: "https://api.mainnet-beta.solana.com"
    val poolMonitorUrl: String = System.getenv("POOL_MONITOR_URL")
        ?: "https://api-v3.raydium.io/pools/info/list?poolType=all&poolSortField=default&sortType=desc&pageSize=10&page=1"
}
