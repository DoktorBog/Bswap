# Acceptance Test Checklist - Solana Trading Bot Refactor

## ✅ Test Scenario 1: Missing Price on Multiple Tokens

**Setup:**
- Configure tokens with unreliable/missing price feeds
- Set `allowBuyWithoutPrice=false` (default)
- Set `sellOnPriceMissing=true`, `priceMissingMaxStrikes=4`, `priceMissingWindowMs=60000`

**Expected Results:**
- [ ] Buy attempts are skipped with log: `BUY blocked for <mint>: no price available and allowBuyWithoutPrice=false`
- [ ] Price miss logs appear: `PRICE_MISS <mint> strike=X/4 in 60000ms`
- [ ] After 4 misses in 60 seconds: `EMERGENCY_SELL <mint> reason=no-price-fallback`
- [ ] Emergency sell is enqueued and executed successfully
- [ ] Bot continues operating normally

**Verification Commands:**
```bash
# Check logs for price miss patterns
grep "PRICE_MISS" bot.log
grep "EMERGENCY_SELL" bot.log
grep "BUY blocked" bot.log
```

---

## ✅ Test Scenario 2: High Sell Burst

**Setup:**
- Trigger mass liquidation (multiple tokens ready to sell)
- Monitor RPC request rate
- Configure sell queue with `spacingMs=500`, `maxConcurrency=1`

**Expected Results:**
- [ ] All sells enqueued: `QUEUE SELL: <mint> (reason=bulk-sell)`
- [ ] Sells processed sequentially with 500ms spacing
- [ ] RPC rate never exceeds 14 requests/second
- [ ] No RPC 429 errors or timeouts
- [ ] All queued sells eventually processed

**Verification Commands:**
```bash
# Monitor sell queue activity
grep "QUEUE SELL" bot.log | wc -l
grep "Processing sell order" bot.log | head -20

# Check RPC rate limiting
grep "RPC_WAIT" bot.log
```

---

## ✅ Test Scenario 3: Mass Liquidation via sellAllOnce

**Setup:**
- Ensure wallet contains multiple SPL tokens
- Trigger `sellAllOnce()` function
- Monitor execution timing and spacing

**Expected Results:**
- [ ] Tokens are discovered and added to state map if not tracked
- [ ] Each token enqueued via sell queue: `QUEUE SELL: <mint> (reason=bulk-sell)`
- [ ] Sells execute with proper spacing (no burst)
- [ ] RPC usage remains within 14 RPS limit
- [ ] No direct execution bypassing the queue

**Verification Commands:**
```bash
# Check bulk sell pattern
grep "bulk-sell" bot.log
grep "SELL NOW: Transaction successful" bot.log
```

---

## ✅ Test Scenario 4: Whitelist Active Filtering

**Setup:**
- Configure whitelist with `enabled=true` and specific token symbols
- Ensure discovery sources provide mix of whitelisted/non-whitelisted tokens
- Have some non-whitelisted tokens in wallet (existing positions)

**Expected Results:**
- [ ] Non-whitelisted discoveries skipped: `Skip <mint>: not in whitelist`
- [ ] Only whitelisted tokens passed to strategies
- [ ] Existing wallet positions (non-whitelisted) still sellable
- [ ] Whitelist loaded at startup with verified tokens from Jupiter
- [ ] Diagnostic shows correct whitelist size

**Verification Commands:**
```bash
# Check whitelist filtering
grep "Skip.*not in whitelist" bot.log
grep "Whitelist updated" bot.log

# Check diagnostics
curl -s http://localhost:8080/diagnostics | jq .whitelistSize
```

---

## ✅ Test Scenario 5: Jito Integration

**Setup:**
- Enable Jito bundler: `useJito=true`
- Execute both buy and sell operations
- Monitor transaction enqueueing

**Expected Results:**
- [ ] Buy transactions: `BUY: Creating and enqueueing transaction for Jito`
- [ ] Sell transactions: `SELL NOW: Creating and enqueueing transaction for Jito`
- [ ] Successful enqueuing: `Successfully enqueued to Jito for <mint>`
- [ ] No "flush when empty" spam during normal operation
- [ ] Trades complete successfully via Jito bundler

**Verification Commands:**
```bash
# Check Jito integration
grep "enqueuing transaction for Jito" bot.log
grep "Successfully enqueued to Jito" bot.log
```

---

## 🔧 Additional Verification Steps

### Configuration Validation
```bash
# Verify all config keys are properly loaded
grep "sellQueue.enabled" application.conf
grep "rpcRateLimiter.maxRps" application.conf
grep "priceService.sellOnPriceMissing" application.conf
```

### Diagnostic Endpoint Check
```bash
# Get full diagnostics
curl -s http://localhost:8080/diagnostics | jq '.'

# Expected structure:
{
  "isActive": true,
  "activeTokensCount": <number>,
  "whitelistEnabled": true,
  "whitelistSize": <number>,
  "sellQueueEnabled": true,
  "rpcRateLimiterEnabled": true,
  "priceMissStats": {
    "trackedTokens": <number>,
    "maxStrikes": 4,
    "sellOnMissing": true
  }
}
```

### Performance Monitoring
```bash
# Check RPC rate limiting effectiveness
grep "RPC_WAIT" bot.log | wc -l

# Monitor sell queue performance  
grep "Processing sell order" bot.log | tail -10

# Check price fallback usage
grep "Price found via" bot.log | cut -d' ' -f6 | sort | uniq -c
```

### Error Handling Verification
```bash
# Check graceful error handling
grep "Failed to" bot.log | head -10
grep "exception" bot.log | wc -l

# Verify no unhandled crashes
grep "ERROR" bot.log | grep -v "expected_error_pattern"
```

---

## 📊 Success Criteria

**All tests must pass with:**
- ✅ No bot stalls or hangs
- ✅ RPC rate consistently ≤ 14 requests/second
- ✅ Emergency sells execute when price unavailable
- ✅ Whitelist filtering works correctly
- ✅ Sell queue processes orders sequentially
- ✅ Jito integration functions properly
- ✅ Logs are clear and actionable
- ✅ Graceful shutdown works

**Performance Benchmarks:**
- Sell queue processing: < 1 second per order including spacing
- Price fetching: < 5 seconds for fallback pipeline completion
- Memory usage: Bounded price histories prevent growth
- Error recovery: Bot resumes normal operation after transient failures

---

## 🐛 Known Issues & Limitations

1. **Jupiter Quote Estimation**: Requires valid `jupiterSwapService` instance; quotes may fail for very low-liquidity tokens
2. **DexScreener SOL Conversion**: Depends on SOL price availability; may fail if CoinGecko is down
3. **Emergency Sells**: Override `minHoldMs` by design - this is intentional behavior
4. **Whitelist Refresh**: Manual endpoint required; no automatic refresh implemented
5. **Rate Limiter Accuracy**: Uses approximate token bucket; burst may slightly exceed 14 RPS for very short periods