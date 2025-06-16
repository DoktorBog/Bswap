package com.bswap.app.interactor

import org.sol4k.Keypair

interface WalletInteractor {
    suspend fun createWallet(mnemonic: List<String>): Keypair
}

expect fun walletInteractor(): WalletInteractor
