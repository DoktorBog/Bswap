package com.bswap.server.routes

import com.bswap.server.model.FiatSessionRequest
import com.bswap.server.model.FiatSessionResponse
import com.bswap.server.service.FiatService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.fiatRoutes(service: FiatService) {
    get("/fiat/providers") {
        call.respond(service.providers())
    }

    post("/fiat/session") {
        val req = call.receive<FiatSessionRequest>()
        val url = service.createSession(req.provider, req.amount, req.address)
        call.respond(FiatSessionResponse(url))
    }
}
