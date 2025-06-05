package com.bswap.app.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PriceDetails(val usd: Double, val eur: Double)

@Serializable
data class SolPriceResponse(val solana: PriceDetails)

class ExchangeRateViewModel(
    private val client: HttpClient,
) : ViewModel() {
    private val _rate = MutableStateFlow<PriceDetails?>(null)
    val rate: StateFlow<PriceDetails?> = _rate

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _lastFetched = MutableStateFlow<Instant?>(null)
    val lastFetched: StateFlow<Instant?> = _lastFetched

    init {
        viewModelScope.launch {
            fetchRates()
            while (true) {
                delay(60_000)
                fetchRates()
            }
        }
    }

    fun fetchRates() = viewModelScope.launch {
        _isLoading.value = true
        runCatching {
            val response: SolPriceResponse = client.get(
                "https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd,eur"
            ).body()
            _rate.value = response.solana
            _lastFetched.value = Clock.System.now()
        }.onFailure {
            it.printStackTrace()
        }
        _isLoading.value = false
    }
}
