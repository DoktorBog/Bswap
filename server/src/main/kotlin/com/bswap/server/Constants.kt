package com.bswap.server

import com.bswap.server.config.ServerConfig

/**
 * Configuration constants. Endpoint URLs are read from environment variables via
 * [ServerConfig] so they can be customised without code changes.
 */
val SERVER_PORT: Int = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 9090
const val LAMPORTS_PER_SOL = 1_000_000_000L // 1 billion lamports per SOL
val RPC_URL: String = ServerConfig.rpcUrl
val JUPITER_API_URL: String = ServerConfig.jupiterApiUrl
