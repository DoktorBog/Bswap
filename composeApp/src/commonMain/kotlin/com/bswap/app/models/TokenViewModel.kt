package com.bswap.app.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bswap.app.baseUrl
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TokenViewModel(
    private val client: HttpClient,
) : ViewModel() {

    private val _tokens = MutableStateFlow<List<TokenProfile>>(emptyList())
    val tokens: StateFlow<List<TokenProfile>> = _tokens

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        fetchTokens()
    }

    fun fetchTokens() = viewModelScope.launch {
        _isLoading.value = true
        runCatching {
            val response: List<TokenProfile> = client.get("$baseUrl/api/tokens").body()
            _tokens.value = response
            _isLoading.value = false
        }.onFailure { e ->
            e.printStackTrace()
            _isLoading.value = false
        }
    }
}