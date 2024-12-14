package com.bswap.server.routes

import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.routing.Route

fun Route.startRoute() {
    val baseProjectPath = System.getProperty("user.dir").substringBefore("/server")
    singlePageApplication {
        filesPath =
            "$baseProjectPath/composeApp/build/dist/wasmJs/productionExecutable"
    }
}