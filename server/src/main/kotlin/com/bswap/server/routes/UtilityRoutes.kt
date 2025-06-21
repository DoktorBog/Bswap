package com.bswap.server.routes

import com.bswap.server.model.ApiError
import com.bswap.server.service.PriceService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.utilityRoutes(version: String, priceService: PriceService) {
    get("/healthz") { call.respond(mapOf("status" to "ok")) }

    get("/version") { call.respond(mapOf("version" to version)) }

    get("/prices") {
        val symbols = call.request.queryParameters["symbols"]
            ?.split(',')?.filter { it.isNotBlank() } ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("missing symbols"))
        val prices = priceService.prices(symbols)
        call.respond(prices)
    }
}
