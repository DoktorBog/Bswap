package com.bswap.app.interactor

import org.sol4k.Keypair

actual fun walletInteractor(): WalletInteractor = WasmWalletInteractor

private object WasmWalletInteractor : WalletInteractor {
    override suspend fun createWallet(mnemonic: List<String>): Keypair = Keypair.generate()
}
