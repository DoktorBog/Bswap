package com.bswap.wallet

import com.bswap.seed.MnemonicValidator
import com.bswap.shared.wallet.Keypair

expect class Bip44WalletDerivationStrategy() : WalletDerivationStrategy
