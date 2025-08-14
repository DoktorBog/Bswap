# Whitelist Trading System

## Обзор

Система whitelist позволяет боту торговать только определенными токенами, используя любую настроенную стратегию (RSI, SMA, и т.д.).

## Как это работает

1. **Whitelist Source** (`RsiWhitelistSource`) содержит список разрешенных токенов
2. **Bot** (`SolanaTokenSwapBot`) каждые 30 секунд проверяет токены из whitelist
3. **Strategy** (RSI или любая другая) решает, покупать или продавать токен

## Whitelist по умолчанию

Система включает популярные токены Solana:
- **Основные**: SOL, USDC, USDT
- **DEX токены**: JUP, RAY, ORCA
- **Инфраструктура**: PYTH, JTO
- **LST токены**: mSOL, bSOL, jitoSOL  
- **Мемы**: BONK, WIF, POPCAT
- **Экосистема**: TNSR, HNT, WEN, SAMO

## Использование

### Запуск бота с whitelist

```kotlin
// Бот автоматически использует whitelist при запуске
val bot = SolanaTokenSwapBot(config)
bot.start() // Whitelist активируется автоматически
```

### Управление whitelist

```kotlin
// Добавить токен
bot.addToWhitelist("TokenMintAddress123...")

// Удалить токен
bot.removeFromWhitelist("TokenMintAddress123...")

// Обновить весь список
bot.updateWhitelist(setOf("mint1", "mint2", "mint3"))

// Сбросить к defaults
bot.getWhitelistSource().resetToDefault()
```

### API методы в BotManagementService

```kotlin
// Получить статус whitelist
botManagementService.getWhitelistStatus()

// Добавить токен
botManagementService.addTokenToWhitelist("mint")

// Удалить токен  
botManagementService.removeTokenFromWhitelist("mint")

// Обновить список
botManagementService.updateWhitelist(setOf("mint1", "mint2"))

// Сбросить к defaults
botManagementService.resetWhitelistToDefault()
```

## Интеграция со стратегиями

Whitelist работает с ЛЮБОЙ стратегией:

- **RSI_BASED** - покупка при RSI < 30, продажа при RSI > 70
- **SMA_CROSS** - покупка/продажа по пересечению SMA
- **IMMEDIATE** - немедленная покупка
- И другие...

Бот проверяет каждый токен из whitelist и передает его в выбранную стратегию для анализа.

## Настройка

В `SolanaSwapBotConfig`:
```kotlin
val config = SolanaSwapBotConfig(
    strategySettings = TradingStrategySettings(
        type = StrategyType.RSI_BASED // или любая другая
    )
)
```

## Логирование

Бот логирует:
- 📊 Проверку whitelist токенов каждые 30 секунд
- 🔍 Обработку каждого токена выбранной стратегией
- 🚀 Решения о покупке/продаже
- ✅/❌ Результаты торговых операций

## Пример

```kotlin
// 1. Создать бота с RSI стратегией
val config = SolanaSwapBotConfig(
    strategySettings = TradingStrategySettings(
        type = StrategyType.RSI_BASED
    )
)

val bot = SolanaTokenSwapBot(config = config)

// 2. Запустить бота (whitelist активируется автоматически)
bot.start()

// 3. Бот будет:
//    - Каждые 30 секунд проверять токены из whitelist
//    - Для каждого токена запускать RSI стратегию
//    - Покупать если RSI < 30
//    - Продавать если RSI > 70

// 4. Управление whitelist
bot.addToWhitelist("NewToken...")
bot.removeFromWhitelist("OldToken...")
```

## Итог

Система whitelist обеспечивает:
- ✅ Торговлю только разрешенными токенами
- ✅ Работу с любой стратегией
- ✅ Простое управление списком
- ✅ Автоматическую активацию при запуске бота