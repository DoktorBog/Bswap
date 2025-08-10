package com.bswap.app

private val port = ":9090"
val baseUrl = "http://${
    when (getPlatform().name) {
        // Android emulator default route to host machine
        "android" -> "192.168.0.152"
        else -> "localhost"
    }
}$port"
