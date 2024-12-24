# Solana Token Swap Bot

[![Solana](https://img.shields.io/badge/Solana-Blockchain-2E333C?logo=solana&logoColor=white)](https://solana.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)

## Overview

**SolanaTokenSwapBot** is an automated trading bot designed to help you monitor new Solana tokens (particularly memecoins), automatically buy them under specified conditions, and optionally sell them using predefined strategies. 

It integrates with:
- **[JupiterSwapService](https://jup.ag/)** for swap quotes and transactions.
- Not implemented!! **[RugCheck](https://api.rugcheck.xyz/)** to detect potential scams.
- **OkHttp** for HTTP requests.
- **Metaplex RPC** for executing on-chain transactions in Solana.

## Features

- **Automated Buy Logic**  
  Schedules buys after detection of new tokens that pass your filters (e.g., market cap, rug-check status).

- **Periodic Selling**  
  Includes a routine to periodically sell all non-zero token balances or handle partial sells if you prefer custom logic.

- **Account Cleanup**  
  Periodically closes zero-balance SPL token accounts to keep your wallet tidy.

- **Customizable Parameters**  
  Adjustable parameters like `minMarketCapSol`, `buyDelayMs`, `skipRugFlaggedTokens`, `solAmountToTrade`, etc.

## Requirements

1. **Kotlin 2+** (or Java 11+ if compiled with Kotlin)
2. **Gradle** or **Maven** for dependency management
