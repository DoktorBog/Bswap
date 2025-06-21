package com.bswap.server.routes

import com.bswap.server.model.ApiError
import com.bswap.server.model.SwapRequest
import com.bswap.server.service.SwapService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.swapRoutes(service: SwapService) {
    get("/swap/quote") {
        val input = call.request.queryParameters["inputMint"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("missing inputMint"))
        val output = call.request.queryParameters["outputMint"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("missing outputMint"))
        val amount = call.request.queryParameters["amount"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("missing amount"))
        val quote = service.quote(SwapRequest(owner = "", inputMint = input, outputMint = output, amount = amount))
        call.respond(quote)
    }

    post("/swap/transaction") {
        val req = call.receive<SwapRequest>()
        val tx = service.buildSwap(req)
        call.respond(tx)
    }

    get("/swap/status/{id}") {
        val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("missing id"))
        val status = service.status(id) ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("not found"))
        call.respond(mapOf("status" to status))
    }
}
