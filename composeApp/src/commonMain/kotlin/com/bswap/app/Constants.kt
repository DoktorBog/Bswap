package com.bswap.app

private val port = ":9090"
val baseUrl = "http://${
    when (getPlatform().name) {
        "android" -> "192.168.0.102"
        else -> "localhost"
    }
}$port"