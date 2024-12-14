package com.bswap.server.routes

import com.bswap.server.data.dexscreener.models.TokenProfile
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun Route.apiRoute(tokenProfiles: Flow<List<TokenProfile>>) {
    get("/api/tokens") {
        val tokens = tokenProfiles.firstOrNull()
        call.respondText(Json.encodeToString(tokens), ContentType.Application.Json)
    }
}