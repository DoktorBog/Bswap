package com.bswap.app

import io.ktor.client.HttpClient

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun networkClient(): HttpClient

expect fun openLink(link: String): Boolean