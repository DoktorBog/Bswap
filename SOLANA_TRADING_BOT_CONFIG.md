# Solana Trading Bot - Configuration Guide

This document describes the configuration options for the refactored Solana Trading Bot with enhanced reliability, price fallbacks, sell queue management, and RPC rate limiting.

## 🚀 Key Features

- **Robust Price Fallbacks**: Jupiter v6 → DexScreener → Jupiter Quote-based estimation
- **Sell Queue System**: Controlled, sequential sell execution with retries
- **Emergency Price Sell**: Auto-sell positions when price data is unavailable for too long
- **RPC Rate Limiting**: Global 14 RPS limit to prevent rate limit violations
- **Token Whitelist**: Only trade verified, liquid tokens from Jupiter Token List
- **Graceful Error Handling**: No stalls on missing price data

## 📋 Configuration Reference

### Sell Queue Configuration

```kotlin
data class SellQueueConfig(
    val enabled: Boolean = true,              // Enable centralized sell queue
    val maxConcurrency: Int = 1,              // Sequential processing (1 worker)
    val spacingMs: Long = 400L,               // Pause between sell orders (400-600ms recommended)
    val retryCount: Int = 2,                  // Number of retries on failure
    val retryDelayMs: Long = 1500L            // Delay between retries
)
```

**Behavioral Rules:**
- All `sell()` calls are enqueued, not executed immediately
- Single worker processes orders sequentially with spacing
- Failed orders are retried with exponential backoff
- Queue continues processing until graceful shutdown

**Log Messages:**
- `QUEUE SELL <mint> (reason=<reason>)` - Order enqueued
- `Processing sell order: <mint> (attempt X/Y, reason=<reason>)` - Worker processing
- `Sell order completed successfully: <mint>` - Success
- `Sell order failed permanently for <mint> after N attempts: <error>` - Final failure

### RPC Rate Limiter Configuration

```kotlin
data class RpcRateLimiterConfig(
    val enabled: Boolean = true,              // Enable global RPC rate limiting
    val maxRps: Int = 14,                     // Maximum requests per second
    val bucketSize: Int = 28                  // Token bucket size (burst capacity)
)
```

**Coverage:**
- All RPC calls: `getLatestBlockhash`, `send/confirm`, `getBalance`, `getTokenAccountsByOwner`, `simulate`
- Token bucket algorithm with jitter to avoid burst alignment
- Automatic queuing when rate limit is exceeded

**Log Messages:**
- `RPC_WAIT: waiting <time>ms for next request` - Rate limit delay applied

### Price Service Configuration

```kotlin
data class PriceServiceConfig(
    val sellOnPriceMissing: Boolean = true,           // Allow emergency sell on price unavailability
    val priceMissingMaxStrikes: Int = 4,              // Max consecutive price misses before emergency sell
    val priceMissingWindowMs: Long = 60_000L,         // Time window for tracking misses (60 seconds)
    val allowBuyWithoutPrice: Boolean = false         // Allow buy signals without price validation
)
```

**Price Fallback Order:**
1. **Jupiter Price API v6** - `https://price.jup.ag/v6/price?ids=<mint>` (batchable, most reliable)
2. **DexScreener with SOL conversion** - Find USDC or SOL pairs, convert to USD if needed
3. **Jupiter Quote-based estimation** - Tiny quote mint → USDC to infer price

**Behavioral Rules:**
- Emergency sell triggers when `consecutiveMisses >= priceMissingMaxStrikes` within `priceMissingWindowMs`
- Buy signals respect `allowBuyWithoutPrice` flag (default: false = skip buys without price)
- Sell signals always proceed regardless of price availability
- Price miss counter resets on successful price fetch

**Log Messages:**
- `PRICE_MISS <mint> strike=<count>/4 in <window>ms` - Throttled price miss warning
- `EMERGENCY_SELL <mint> reason=no-price-fallback` - Emergency sell triggered

### Whitelist Configuration

```kotlin
data class WhitelistConfig(
    val enabled: Boolean = true,                      // Enable token whitelist filtering
    val symbols: Set<String> = setOf(                 // Whitelisted token symbols
        // Major liquid tokens
        "SOL", "USDC", "USDT", "JUP", "PYTH", "JTO", "RAY", "ORCA",
        // LST tokens  
        "mSOL", "bSOL", "jitoSOL",
        // Large meme coins with good liquidity
        "BONK", "WIF", "POPCAT",
        // Other ecosystem tokens
        "TNSR", "HNT", "WEN", "SAMO"
    )
)
```

**Behavioral Rules:**
- Whitelist built at startup from Jupiter Token List (verified entries only)
- Discovery sources (profiles/boost/pump) are filtered before strategy dispatch
- Wallet-held tokens are ALWAYS considered for sell logic (exit existing positions)
- Non-whitelisted discoveries are skipped with debug log: `Skip <mint>: not in whitelist`

**Management:**
- Manual refresh endpoint available
- Symbols mapped to mints using Jupiter verified token list
- Missing symbols logged as warnings for debugging

### Trading Strategy Configuration

All TA strategies now support:

```kotlin
// Example for RSI strategy
val strategy.allowBuyWithoutPrice = false  // Gate buy signals on price availability
```

**Enhanced TA Behavior:**
- **Buy signals**: Respect `allowBuyWithoutPrice` flag (if false, skip buys when price is null)
- **Sell signals**: Always execute regardless of price availability
- **Price history**: Bounded to MAX_HISTORY=200 to prevent unbounded growth
- **Missing price handling**: Use fallbacks (token UI amount → 0.000001 final fallback)
- **Emergency sells**: Override `minHoldMs` when price data unavailable for configured window

## 🔧 Deployment Configuration

### Environment Setup

```bash
# Core Configuration
BOT_STRATEGY_TYPE=TECHNICAL_ANALYSIS_COMBINED
BOT_SOL_AMOUNT_TO_TRADE=0.001
BOT_USE_JITO=true
BOT_BLOCK_BUY=false

# Sell Queue
BOT_SELL_QUEUE_ENABLED=true
BOT_SELL_QUEUE_SPACING_MS=500
BOT_SELL_QUEUE_RETRY_COUNT=2

# RPC Rate Limiting
BOT_RPC_RATE_LIMITER_ENABLED=true
BOT_RPC_RATE_LIMITER_MAX_RPS=14

# Price Service
BOT_PRICE_SERVICE_SELL_ON_PRICE_MISSING=true
BOT_PRICE_SERVICE_ALLOW_BUY_WITHOUT_PRICE=false

# Whitelist
BOT_WHITELIST_ENABLED=true
```

## 📊 Monitoring & Diagnostics

### Key Log Patterns

**Buy Operations:**
```
BUY attempt: mint=<mint>, wallet=<address>
BUY success: <mint> via Jito
BUY blocked for <mint>: no price available and allowBuyWithoutPrice=false
```

**Sell Operations:**
```
QUEUE SELL: <mint> (reason=strategy)
SELL NOW: Transaction successful for <mint>
EMERGENCY_SELL <mint> reason=no-price-fallback
```

**Rate Limiting:**
```
RPC_WAIT: waiting 150ms for next request
```

**Price Service:**
```
PRICE_MISS <mint> strike=3/4 in 60000ms
Price found via Jupiter v6 for <mint>: 0.000123
```

### Diagnostic Endpoints

The bot exposes diagnostic information via `getDiagnostics()`:

```kotlin
{
  "isActive": true,
  "activeTokensCount": 25,
  "whitelistEnabled": true,
  "whitelistSize": 18,
  "sellQueueEnabled": true,
  "rpcRateLimiterEnabled": true,
  "priceMissStats": {
    "trackedTokens": 3,
    "maxStrikes": 4,
    "sellOnMissing": true
  }
}
```

## 🧪 Testing Scenarios

### 1. Price Unavailability Test
- **Setup**: Multiple tokens with no price feeds available
- **Expected**: Buys skipped (if `allowBuyWithoutPrice=false`), emergency sells after 4 misses in 60s

### 2. High Sell Volume Test  
- **Setup**: Trigger mass liquidation (sellAllOnce)
- **Expected**: Sells queued and executed with 500ms spacing, RPS ≤ 14

### 3. Whitelist Filtering Test
- **Setup**: Mix of whitelisted and non-whitelisted token discoveries
- **Expected**: Non-whitelisted tokens skipped with logs, wallet positions still manageable

### 4. Jito Integration Test
- **Setup**: Enable Jito bundler
- **Expected**: Successful trades enqueued, no "flush when empty" spam

### 5. Rate Limiting Test
- **Setup**: High RPC demand scenario
- **Expected**: RPC calls throttled, no 429 errors, stable latency

## 🚨 Edge Cases Handled

1. **Token in wallet but not in whitelist** → Strategy still manages its exit
2. **Price available in SOL but not USD** → Compute USD via SOL-USD × pair price
3. **`blockBuy=true`** → Prevents new buys but doesn't block sells or emergency sells
4. **Price returns after misses** → Miss counter resets, no accidental emergency sells
5. **Graceful shutdown** → Sell queue worker finishes current job before stopping

## 📈 Performance Optimizations

- **Bounded price histories** (200 max) prevent memory growth
- **Cached price lookups** with 60-second TTL
- **Concurrent price fetching** for batch operations  
- **Token bucket rate limiting** allows controlled burst capacity
- **Sell queue batching** reduces RPC overhead

---

**Note**: All configuration values have sane defaults and can be adjusted based on market conditions and RPC provider limits.