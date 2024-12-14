package com.bswap.server.routes

import com.bswap.server.data.dexscreener.models.TokenProfile
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.flow.Flow
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.unsafe

fun Route.tokensRoute(tokenProfiles: Flow<List<TokenProfile>>) {
    get("/tokens") {
        tokenProfiles.collect { tokens ->
            call.respondText(
                createHTML().html {
                    head {
                        style {
                            unsafe {
                                raw(
                                    """
                            body {
                                font-family: Arial, sans-serif;
                                margin: 20px;
                            }
                            table {
                                border-collapse: collapse;
                                width: 100%;
                                margin-top: 20px;
                            }
                            th, td {
                                border: 1px solid #ddd;
                                text-align: left;
                                padding: 12px;
                            }
                            th {
                                background-color: #4CAF50;
                                color: white;
                                font-weight: bold;
                            }
                            tr:nth-child(even) {
                                background-color: #f2f2f2;
                            }
                            tr:hover {
                                background-color: #ddd;
                            }
                            img {
                                max-height: 50px;
                                max-width: 50px;
                            }
                            a {
                                color: #007BFF;
                                text-decoration: none;
                            }
                            a:hover {
                                text-decoration: underline;
                            }
                        """.trimIndent()
                                )
                            }
                        }
                    }
                    body {
                        h1 { +"Token Profiles" }
                        table {
                            thead {
                                tr {
                                    th { +"Token Address" }
                                    th { +"Chain ID" }
                                    th { +"Description" }
                                    th { +"Icon" }
                                    th { +"Open Graph" }
                                    th { +"URL" }
                                }
                            }
                            tbody {
                                tokens.forEach { token ->
                                    tr {
                                        td { +token.tokenAddress }
                                        td { +token.chainId }
                                        td {
                                            +(token.description
                                                ?: "No description available")
                                        }
                                        td {
                                            if (token.icon.isNotBlank()) {
                                                img(src = token.icon, alt = "Token Icon")
                                            } else {
                                                +"No Icon"
                                            }
                                        }
                                        td {
                                            if (token.openGraph?.isNotBlank() == true) {
                                                img(
                                                    src = token.openGraph,
                                                    alt = "Open Graph Image"
                                                )
                                            } else {
                                                +"No Image"
                                            }
                                        }
                                        td {
                                            a(
                                                href = token.url,
                                                target = "_blank"
                                            ) { +"Open Link" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                ContentType.Text.Html
            )
        }
    }
}