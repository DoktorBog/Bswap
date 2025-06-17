package com.bswap.wallet

import com.bswap.seed.MnemonicValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Bip44DerivationStrategyTest {
    private val strategy = Bip44WalletDerivationStrategy()
    private val mnemonic = "bottom drive obey lake curtain smoke basket hold race lonely fit walk".split(" ")

    @Test
    fun valid_mnemonic_passes_validation() {
        assertTrue(MnemonicValidator.isValidMnemonic(mnemonic))
    }

    @Test
    fun invalid_mnemonic_fails() {
        val invalid = mnemonic.toMutableList().apply { this[0] = "invalid" }
        assertFalse(MnemonicValidator.isValidMnemonic(invalid))
    }
    @Test
    fun derives_account_zero() {
        val kp = strategy.deriveKeypair(mnemonic)
        assertEquals("AK7AACuihtCk6abEywXtg7sPW2Qh9iYg5C6BA38h9ciE", kp.publicKey.toBase58())
    }

    @Test
    fun derives_other_account() {
        val kp = strategy.deriveKeypair(mnemonic, accountIndex = 1)
        assertEquals("8yUQzXmmZJjfYpNFLadyT6LPP4ciPPpSj1PuL232nWDr", kp.publicKey.toBase58())
    }

    /** Regression for issue #XYZ where account indices >1 derived incorrectly. */
    @Test
    fun regression_issue_XYZ() {
        val kp = strategy.deriveKeypair(mnemonic, accountIndex = 5)
        assertEquals("7P1y9mEEXffJyBBS9fjqRWbGdQdVsA9Aes7RGzQW1cRu", kp.publicKey.toBase58())
    }
}
