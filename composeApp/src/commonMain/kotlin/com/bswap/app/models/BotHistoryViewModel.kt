package com.bswap.app.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bswap.app.api.WalletApi
import com.bswap.shared.model.SolanaTx
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BotHistoryViewModel(
    private val api: WalletApi
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
        cursor = null
        
        try {
            val historyPage = api.getHistory(limit = 50)
            _transactions.value = historyPage.transactions
            cursor = historyPage.nextCursor
        } catch (e: Exception) {
            _error.value = e.message ?: "Unknown error occurred"
            e.printStackTrace()
        } finally {
            _isLoading.value = false
        }
    }

    fun loadMore() = viewModelScope.launch {
        if (cursor == null || _isLoading.value) return@launch
        
        _isLoading.value = true
        
        try {
            val historyPage = api.getHistory(limit = 50, cursor = cursor)
            _transactions.value = _transactions.value + historyPage.transactions
            cursor = historyPage.nextCursor
        } catch (e: Exception) {
            _error.value = e.message ?: "Failed to load more transactions"
            e.printStackTrace()
        } finally {
            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}