package com.bswap.server.routes

import com.bswap.server.data.solana.pool.PoolMonitorService
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

fun Route.newPullsRoute(poolMonitorService: PoolMonitorService) {
    get("/new-pools") {
        val newPools = poolMonitorService.getNewPools()
        call.respondText(createHTML().table {
            thead {
                tr {
                    th { +"Pool ID" }
                    th { +"Name" }
                    th { +"Price" }
                    th { +"TVL" }
                    th { +"Token A" }
                    th { +"Token B" }
                }
            }
            tbody {
                newPools.forEach { pool ->
                    tr {
                        td { +pool.id }
                        td { +(pool.name ?: "N/A") }
                        td { +(pool.price?.toString() ?: "N/A") }
                        td { +(pool.tvl?.toString() ?: "N/A") }
                        td { +"${pool.mintA.symbol} (${pool.mintA.name})" }
                        td { +"${pool.mintB.symbol} (${pool.mintB.name})" }
                    }
                }
            }
        }, ContentType.Text.Html)
    }
}