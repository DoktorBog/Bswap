package com.bswap.app.di

import com.bswap.app.networkClient
import com.bswap.app.api.WalletApi
import com.bswap.app.models.*
import com.bswap.ui.home.HomeViewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
    single { networkClient() }
    single { WalletApi(get()) }

    factory { TokenViewModel(get()) }
    factory { ConfirmSeedViewModel() }
    factory { ExchangeRateViewModel(get()) }
    factory { (address: String) -> WalletViewModel(get(), address) }
    factory { (address: String) -> HomeViewModel(get(), address) }
}

fun initKoin() = startKoin { modules(appModule) }
