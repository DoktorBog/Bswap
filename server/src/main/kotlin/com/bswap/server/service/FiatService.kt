package com.bswap.server.service

class FiatService {
    private val providers = listOf("MoonPay", "Transak")

    fun providers(): List<String> = providers

    fun createSession(provider: String, amount: Double, address: String): String {
        val base = provider.lowercase()
        return "https://$base.example/checkout?amount=$amount&address=$address"
    }
}
