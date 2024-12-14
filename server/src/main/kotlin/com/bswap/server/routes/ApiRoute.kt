package com.bswap.server.routes

import com.bswap.server.data.dexscreener.TokenInfoService
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.apiRoute(tokenInfoService: TokenInfoService) {
    get("/api/tokens") {
        val tokens = tokenInfoService.fetchTokenProfiles()
        call.respondText(Json.encodeToString(tokens), ContentType.Application.Json)
    }
}