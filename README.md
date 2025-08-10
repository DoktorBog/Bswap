# Solana Token Swap Bot

[![Solana](https://img.shields.io/badge/Solana-Blockchain-2E333C?logo=solana&logoColor=white)](https://solana.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)

## Overview

**SolanaTokenSwapBot** is an automated trading bot designed to help you monitor new Solana tokens (particularly memecoins), automatically buy them under specified conditions, and optionally sell them using predefined strategies. It integrates with:

- **[JupiterSwapService](https://jup.ag/)** for swap quotes and transactions.
- **Metaplex RPC** for executing on-chain transactions in Solana.
- **[Jito Bundles](https://docs.jito.wtf/lowlatencytxnsend/)** (optional) for low-latency, MEV-protected transactions on Solana.

## Quick Start
1. `git clone` this repository
2. Run `./gradlew build` to fetch dependencies. The build uses Gradle's
   **configuration cache** and **parallel execution** to speed things up.
3. Launch the backend with `./gradlew server:run`
4. To open the Compose UI in a browser, set `enableWasm=true` in `gradle.properties`
   and run `./gradlew :composeApp:wasmJsBrowserProductionRun`
5. To run Android unit tests, set the `ANDROID_HOME` environment variable or
   create a `local.properties` file with `sdk.dir=<path-to-sdk>`.

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
<img width="358" height="682" alt="Screenshot 2025-08-11 at 01 29 34" src="https://github.com/user-attachments/assets/1a4c7cc6-7e19-4d8f-b0c2-520920198120" />


### Wallet API

Once the server is running you can query wallet balances via HTTP:

```bash
curl http://localhost:9090/wallet/<ADDRESS>/balance
```

Replace `<ADDRESS>` with any valid Solana public key.
## Compose UI

The `composeApp` module contains reusable Jetpack Compose widgets under `com.bswap.ui`. They adapt their colors through `WalletTheme` and can be reused across the application. See [composeApp/README.md](composeApp/README.md) for the list of available components and a brief overview of Compose 1.7 features.
