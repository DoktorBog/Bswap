# AI Strategy Integration for Bswap Trading Bot

## Overview

This integration adds advanced AI-powered trading strategies to the existing Bswap trading system. The AI strategy uses machine learning models to make trading decisions based on market data analysis.

## Features Added

### 1. AI Trading Strategy (`AITradingStrategy`)
- **Neural Network Model**: Deep learning model with configurable architecture
- **Random Forest Model**: Ensemble tree-based model for robust predictions
- **Ensemble Model**: Combines multiple models for improved accuracy
- **Adaptive Learning**: Continuously retrains models based on new market data

### 2. Advanced Features
- **Real-time Prediction**: Makes buy/sell decisions based on market features
- **Risk Management**: Built-in stop-loss, take-profit, and risk scoring
- **Feature Engineering**: Extracts price action, volume, momentum, volatility, and sentiment
- **Confidence Thresholds**: Only trades when model confidence exceeds threshold
- **Periodic Retraining**: Automatically improves models over time

### 3. Configuration Options
```json
{
  "modelType": "NEURAL_NETWORK",
  "learningRate": 0.001,
  "hiddenLayers": [128, 64, 32],
  "lookbackPeriod": 50,
  "predictionHorizon": 5,
  "confidenceThreshold": 0.75,
  "retrainIntervalMs": 3600000,
  "maxTrainingSamples": 10000,
  "takeProfitPct": 0.20,
  "stopLossPct": 0.12,
  "adaptiveLearning": true,
  "useEnsemble": true,
  "sentimentAnalysis": true
}
```

## Files Modified/Added

### New Files
- `server/src/main/kotlin/com/bswap/server/ai/AIPredictor.kt` - Core AI prediction engine
- `Dockerfile` - Docker container with Java 21 support
- `setup-java.sh` - Java installation script for Termux/Linux

### Modified Files
- `server/src/main/kotlin/com/bswap/server/SolanaSwapBotConfig.kt` - Added AI strategy configuration
- `server/src/main/kotlin/com/bswap/server/stratagy/Stratagy.kt` - Integrated AI strategy
- `server/src/main/resources/default-strategy-config.json` - Updated with AI strategy defaults

## AI Model Types

### 1. Neural Network (`NEURAL_NETWORK`)
- Multi-layer perceptron with configurable architecture
- Uses backpropagation for learning
- Good for complex non-linear patterns
- Default: 3 hidden layers [128, 64, 32 neurons]

### 2. Random Forest (`RANDOM_FOREST`) 
- Ensemble of decision trees
- Robust against overfitting
- Handles missing data well
- Good interpretability

### 3. Ensemble (`ENSEMBLE`)
- Combines Neural Network + Random Forest
- Averages predictions from multiple models
- Better generalization and stability
- Recommended for production use

## Market Features Used

1. **Price Action**: Normalized price change over lookback period
2. **Volume**: Relative volume analysis (simplified proxy)
3. **Momentum**: Rate of change analysis
4. **Volatility**: Standard deviation of returns
5. **Sentiment**: Derived from recent price movements

## Trading Logic

### Buy Conditions
- Model prediction favors buying over selling
- Confidence above threshold (default: 75%)
- Expected return > 2%
- Risk score < 70%
- Sentiment > 30%

### Sell Conditions
- Take profit: +20% (configurable)
- Stop loss: -12% (configurable)  
- AI exit signal: Model predicts sell with high confidence
- High risk: Risk score > 80%
- Timed exit: Minimum hold period exceeded

## Installation & Setup

### For Docker (Recommended)
```bash
# Build and run with Docker
docker build -t bswap-ai .
docker run -p 8080:8080 bswap-ai
```

### For Termux/Android
```bash
# Run setup script
./setup-java.sh

# Note: Termux has JVM limitations, Docker is recommended
```

### For Linux/macOS
```bash
# Install Java 21
./setup-java.sh

# Build and run
./gradlew :server:build
./gradlew :server:run
```

## Usage

### 1. Set AI Strategy as Default
Update `default-strategy-config.json`:
```json
{
  "strategies": [
    { 
      "name": "ai_strategy", 
      "weight": 1.0, 
      "params": { ... } 
    }
  ]
}
```

### 2. Configure Strategy Type
In your bot configuration:
```kotlin
val strategySettings = TradingStrategySettings(
    type = StrategyType.AI_STRATEGY,
    aiStrategy = AIStrategyConfig(
        modelType = AIModelType.ENSEMBLE,
        confidenceThreshold = 0.75,
        // ... other parameters
    )
)
```

## Model Training

The AI strategy automatically:
1. **Collects Data**: Tracks price history and market features
2. **Labels Data**: Uses future returns to create training samples
3. **Trains Models**: Retrains every hour (configurable) when enough data exists
4. **Validates**: Only uses models with >55% accuracy
5. **Adapts**: Continuously improves with new market data

## Risk Management

### Built-in Risk Controls
- **Position Sizing**: Configurable max position size (default: 10%)
- **Drawdown Limits**: Max 5% drawdown protection
- **Volatility Adjustment**: Reduces position size in high volatility
- **Sharpe Ratio**: Monitors risk-adjusted returns

### Stop Loss Mechanisms
- **Fixed Stop**: -12% loss limit
- **AI-based Exit**: Model predicts further losses
- **Risk-based Exit**: High risk score triggers exit
- **Time-based Exit**: Minimum hold period protection

## Performance Monitoring

The AI strategy logs:
- Model accuracy after each retraining
- Buy/sell decisions with confidence scores
- Performance metrics and trade outcomes
- Risk scores and position management actions

## Advanced Configuration

### Feature Weights
Adjust the importance of different market signals:
```json
"featureWeights": {
  "priceAction": 0.3,
  "volume": 0.25,
  "momentum": 0.2, 
  "volatility": 0.15,
  "sentiment": 0.1
}
```

### Risk Parameters
Fine-tune risk management:
```json
"riskParameters": {
  "maxPositionSize": 0.1,
  "volatilityAdjustment": true,
  "drawdownLimit": 0.05,
  "sharpeRatioThreshold": 1.0
}
```

## Troubleshooting

### Common Issues
1. **Model Not Training**: Need at least 100 samples, wait for data collection
2. **Low Accuracy**: Adjust learning rate, try different model types
3. **Too Conservative**: Lower confidence threshold, adjust risk parameters
4. **Build Issues**: Use Docker for consistent environment

### Logging
Enable debug logging to monitor AI decisions:
```kotlin
logger.info("AI Strategy bought ${token} with confidence ${confidence}")
logger.warn("AI model retraining failed")
```

## Future Enhancements

- **Sentiment Analysis**: Integration with social media/news APIs
- **Technical Indicators**: More sophisticated feature engineering
- **Reinforcement Learning**: Advanced AI trading algorithms
- **Multi-timeframe**: Analysis across different time horizons
- **Market Regime Detection**: Adapt strategy to market conditions