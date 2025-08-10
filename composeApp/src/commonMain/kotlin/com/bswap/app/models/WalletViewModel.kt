package com.bswap.app.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bswap.app.api.WalletApi
import com.bswap.shared.model.WalletInfo
import com.bswap.shared.model.SolanaTx
import com.bswap.shared.model.HistoryPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WalletViewModel(
    private val api: WalletApi,
    private val address: String,
) : ViewModel() {

    private val _walletInfo = MutableStateFlow<WalletInfo?>(null)
    val walletInfo: StateFlow<WalletInfo?> = _walletInfo

    private val _history = MutableStateFlow<List<SolanaTx>>(emptyList())
    val history: StateFlow<List<SolanaTx>> = _history
    private var cursor: String? = null

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch { refresh() }
    }

    fun refresh() = viewModelScope.launch {
        _isLoading.value = true
        runCatching { api.walletInfo(address) }
            .onSuccess { _walletInfo.value = it }
            .onFailure { it.printStackTrace() }
        // Start with smaller batch - only load 50 transactions initially
        runCatching { api.getHistory(limit = 50) }
            .onSuccess {
                _history.value = it.transactions
                cursor = it.nextCursor
            }
            .onFailure { it.printStackTrace() }
        _isLoading.value = false
    }

    fun loadMore() = viewModelScope.launch {
        if (cursor == null || _isLoading.value) return@launch
        _isLoading.value = true
        // Load next page with smaller batch size (50 instead of 100)  
        runCatching { api.getHistory(limit = 50, cursor = cursor) }
            .onSuccess {
                _history.value = _history.value + it.transactions
                cursor = it.nextCursor
            }
            .onFailure { it.printStackTrace() }
        _isLoading.value = false
    }
}
