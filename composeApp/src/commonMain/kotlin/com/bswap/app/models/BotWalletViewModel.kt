package com.bswap.app.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bswap.app.api.WalletApi
import com.bswap.app.api.WalletBalance
import com.bswap.shared.model.TokenInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BotWalletState(
    val balance: WalletBalance? = null,
    val tokens: List<TokenInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false
)

class BotWalletViewModel(
    private val api: WalletApi,
    private val botWalletAddress: String = "F277zfVkW6VBfkfWPNVXKoBEgCCeVcFYdiZDUX9yCPDW"
) : ViewModel() {

    private val _state = MutableStateFlow(BotWalletState())
    val state: StateFlow<BotWalletState> = _state

    init {
        loadWalletData()
    }

    fun refresh() {
        _state.value = _state.value.copy(isRefreshing = true, error = null)
        loadWalletData()
    }

    private fun loadWalletData() = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true, error = null)
        
        try {
            // Load balance and tokens in parallel
            val balanceDeferred = async { 
                api.getWalletBalance(botWalletAddress) 
            }
            val tokensDeferred = async { 
                api.getTokens(botWalletAddress) 
            }
            
            val balance = balanceDeferred.await()
            val tokens = tokensDeferred.await()
            
            _state.value = _state.value.copy(
                balance = balance,
                tokens = tokens,
                isLoading = false,
                isRefreshing = false,
                error = null
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = e.message ?: "Unknown error occurred"
            )
            e.printStackTrace()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun getTotalUSDValue(): Double {
        val balance = _state.value.balance
        return balance?.totalValueUSD ?: 0.0
    }

    fun getSolBalance(): Double {
        val balance = _state.value.balance
        return balance?.solBalance ?: 0.0
    }

    fun getTokenBalances(): Map<String, Double> {
        val balance = _state.value.balance
        return balance?.tokenBalances ?: emptyMap()
    }
}