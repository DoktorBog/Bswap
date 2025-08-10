package com.bswap.app

private val port = ":9090"
val baseUrl = "http://${
    when (getPlatform().name) {
        // Android emulator default route to host machine
        "android" -> "10.0.2.2"
        else -> "localhost"
    }
}$port"
