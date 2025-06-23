package com.bswap.server.routes

import com.bswap.shared.model.ApiError
import com.bswap.shared.model.SwapRequest
import com.bswap.shared.model.BatchSwapRequest
import com.bswap.shared.model.SignedTx
import com.bswap.server.service.WalletService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.walletRoutes(service: WalletService) {
    get("/wallet/{address}/balance") {
        val address = call.parameters["address"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            ApiError("missing address")
        )
        val result = service.getBalance(address)
        result.onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "error")) }
    }

    get("/wallet/{address}/tokens") {
        val address = call.parameters["address"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            ApiError("missing address")
        )
        val result = service.getTokens(address)
        result.onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "error")) }
    }

    get("/wallet/{address}/history") {
        val address = call.parameters["address"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            ApiError("missing address")
        )
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
        val cursor = call.request.queryParameters["cursor"]
        val result = service.getHistory(address, limit, cursor)
        result.onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "error")) }
    }

    get("/tokens/search") {
        val q = call.request.queryParameters["q"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            ApiError("missing q")
        )
        val result = service.searchTokens(q)
        result.onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "error")) }
    }

    post("/swap") {
        val req = call.receive<SwapRequest>()
        val result = service.swap(req)
        result.onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "error")) }
    }

    post("/swapBatch") {
        val req = call.receive<BatchSwapRequest>()
        val result = service.swapBatch(req.swaps)
        result.onSuccess { call.respond(it) }
            .onFailure { call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "error")) }
    }

    post("/submitTx") {
        val tx = call.receive<SignedTx>()
        val result = service.submit(tx)
        result.onSuccess { call.respond(HttpStatusCode.OK) }
            .onFailure {
                call.respond(HttpStatusCode.InternalServerError, ApiError(it.message ?: "error"))
            }
    }
}
