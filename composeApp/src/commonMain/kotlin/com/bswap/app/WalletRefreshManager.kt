package com.bswap.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object WalletRefreshManager {
    private val _refreshTrigger = MutableSharedFlow<Unit>()
    val refreshTrigger: SharedFlow<Unit> = _refreshTrigger
    
    fun triggerRefresh() {
        _refreshTrigger.tryEmit(Unit)
    }
}