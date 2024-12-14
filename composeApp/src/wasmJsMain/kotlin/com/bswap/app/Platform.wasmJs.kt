package com.bswap.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.JsClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window

class WasmPlatform : Platform {
    override val name: String = "web"
}

actual fun getPlatform(): Platform = WasmPlatform()

actual fun networkClient(): HttpClient = HttpClient(JsClient()) {
    install(ContentNegotiation) {
        json()
    }
}

actual fun openLink(link: String): Boolean {
    return try {
        window.open(link, "_blank")
        true
    } catch (e: Exception) {
        false
    }
}