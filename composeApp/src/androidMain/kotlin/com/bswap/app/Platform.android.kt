package com.bswap.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AndroidPlatform : Platform {
    override val name: String = "android"
}

lateinit var appContext: Context

fun initializeAppContext(application: Context) {
    appContext = application
}

actual fun getPlatform(): Platform = AndroidPlatform()
actual fun networkClient(): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

actual fun openLink(link: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        appContext.startActivity(intent)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}