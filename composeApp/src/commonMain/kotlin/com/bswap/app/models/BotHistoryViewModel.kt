package com.bswap.app.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bswap.app.api.WalletApi
import com.bswap.shared.model.SolanaTx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BotHistoryViewModel(
    private val api: WalletApi,
    private val botWalletAddress: String = "F277zfVkW6VBfkfWPNVXKoBEgCCeVcFYdiZDUX9yCPDW"
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<SolanaTx>>(emptyList())
    val transactions: StateFlow<List<SolanaTx>> = _transactions
    
    private var cursor: String? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() = viewModelScope.launch {
        _isLoading.value = true
        _error.value = null
        
        runCatching { 
            // Start with smaller batch - only load 50 transactions initially
            api.getHistory(botWalletAddress, limit = 50) 
        }
        .onSuccess { historyPage ->
            _transactions.value = historyPage.transactions
            cursor = historyPage.nextCursor
        }
        .onFailure { 
            _error.value = it.message
            it.printStackTrace() 
        }
        
        _isLoading.value = false
    }

    fun loadMore() = viewModelScope.launch {
        if (cursor == null || _isLoading.value) return@launch
        
        _isLoading.value = true
        
        runCatching { 
            // Load next page with smaller batch size (50 instead of 100)
            api.getHistory(botWalletAddress, limit = 50, cursor = cursor) 
        }
        .onSuccess { historyPage ->
            _transactions.value = _transactions.value + historyPage.transactions
            cursor = historyPage.nextCursor
        }
        .onFailure { 
            _error.value = it.message
            it.printStackTrace() 
        }
        
        _isLoading.value = false
    }

    fun clearError() {
        _error.value = null
    }
}