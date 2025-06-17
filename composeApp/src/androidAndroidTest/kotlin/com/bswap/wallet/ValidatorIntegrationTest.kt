package com.bswap.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.sol4k.RpcClient
import java.io.File

/** Integration test verifying derived keypair works against a local validator. */
@RunWith(AndroidJUnit4::class)
class ValidatorIntegrationTest {
    private var process: Process? = null

    @Before
    fun setup() {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val command = listOf("solana-test-validator", "--ledger", File(filesDir, "validator").absolutePath, "--reset", "--quiet")
        process = ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.INHERIT).start()
        Thread.sleep(5000) // wait for validator
    }

    @After
    fun tearDown() {
        process?.destroy()
    }

    @Test
    fun airdrop_and_balance() = runBlocking {
        val mnemonic = "bottom drive obey lake curtain smoke basket hold race lonely fit walk".split(" ")
        val rpc = RpcClient("http://localhost:8899")
        rpc.requestAirdrop(kp.publicKey.toBase58(), 1_000_000_000L)
        // wait for airdrop
        var balance = rpc.getBalance(kp.publicKey.toBase58())
        var retries = 10
        while (balance == 0L && retries-- > 0) {
            Thread.sleep(1000)
            balance = rpc.getBalance(kp.publicKey.toBase58())
        }
        assertTrue(balance > 0)
    }
}
