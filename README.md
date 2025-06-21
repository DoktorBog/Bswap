# Solana Token Swap Bot

[![Solana](https://img.shields.io/badge/Solana-Blockchain-2E333C?logo=solana&logoColor=white)](https://solana.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)

## Overview

**SolanaTokenSwapBot** is an automated trading bot designed to help you monitor new Solana tokens (particularly memecoins), automatically buy them under specified conditions, and optionally sell them using predefined strategies. It integrates with:

- **[JupiterSwapService](https://jup.ag/)** for swap quotes and transactions.
- **Metaplex RPC** for executing on-chain transactions in Solana.
- **[Jito Bundles](https://docs.jito.wtf/lowlatencytxnsend/)** (optional) for low-latency, MEV-protected transactions on Solana.

### Key Features

- **Automated Buy Logic**  
  Watches token feeds (like DexScreener or PumpFun) and automatically buys tokens that pass configurable filters.

- **Periodic Selling**  
  Optionally sells SPL tokens at a configurable interval (e.g. every 10 seconds).

- **Account Cleanup**  
  Closes zero-balance token accounts on a schedule to keep your wallet uncluttered.

- **Customizable Parameters**  
  Tune parameters such as `solAmountToTrade`, `maxKnownTokens`, `autoSellAllSpl`, `closeAccountsIntervalMs`, etc.

- **Jito Integration**  
  If `useJito` is enabled in the config, transactions are bundled (up to 5 at a time) and sent via `sendBundle`, enhancing MEV protection and faster landing on Solana.

## Requirements

- **Kotlin 1.9+ with JDK 17**
- **Gradle** (or Maven) for dependency management. Gradle's toolchain will automatically use JDK 17 if it is installed or configured.

## Installation

1. **Clone** this repository.
2. **Configure** environment variables or directly edit `SolanaSwapBotConfig` to set:
   - **RPC endpoint** (defaults to `https://api.mainnet-beta.solana.com` if `RPC_URL` isn't set)
   - **JupiterSwapService**
   - **JitoBundlerService** (if desired, set `useJito = true`)
   - **Private key** via the `SOLANA_PRIVATE_KEY` environment variable or a `solana_private_key.txt` file
3. **Build** the project:
   ```bash
   ./gradlew server:run
   ```

   To run the Compose UI in your browser, execute:
   ```bash
   ./gradlew :composeApp:wasmJsBrowserProductionRun
   ```

### Wallet API

Once the server is running you can query wallet balances via HTTP:

```bash
curl http://localhost:9090/wallet/<ADDRESS>/balance
```

Replace `<ADDRESS>` with any valid Solana public key.
## Compose UI

The `composeApp` module contains reusable Jetpack Compose widgets under `com.bswap.ui`. They adapt their colors through `WalletTheme` and can be reused across the application. See [composeApp/README.md](composeApp/README.md) for the list of available components and a brief overview of Compose 1.7 features.
