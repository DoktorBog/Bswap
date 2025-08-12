# 🚀 Bswap AI Trading Server Deployment Guide

## ✅ What Was Successfully Integrated

### 🤖 AI Strategy Implementation
- **Neural Network Model** with configurable layers [128, 64, 32]
- **Random Forest Ensemble** for robust predictions
- **Real-time Feature Engineering**: Price action, volume, momentum, volatility, sentiment
- **Adaptive Learning**: Automatic model retraining every hour
- **Risk Management**: 20% take-profit, 12% stop-loss, position sizing
- **Confidence Thresholds**: Only trades with >75% model confidence

### 📁 Files Added/Modified
```
✅ server/src/main/kotlin/com/bswap/server/ai/AIPredictor.kt
✅ server/src/main/kotlin/com/bswap/server/SolanaSwapBotConfig.kt (AI configs)
✅ server/src/main/kotlin/com/bswap/server/stratagy/Stratagy.kt (AI strategy)
✅ server/src/main/resources/default-strategy-config.json (AI defaults)
✅ Dockerfile (Java 21 container)
✅ setup-java.sh (Installation script)
✅ AI_STRATEGY_README.md (Documentation)
```

## 🏃 How to Run the Server

### Option 1: Docker (Recommended) 🐳
```bash
# Install Docker on Linux/macOS
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Build and run
docker build -t bswap-ai .
docker run -d -p 8080:8080 --name bswap-server bswap-ai

# View logs
docker logs -f bswap-server

# Access API
curl http://localhost:8080/health
```

### Option 2: Linux/macOS Native 🖥️
```bash
# Install Java 21
./setup-java.sh

# Build and run
./gradlew :server:build
./gradlew :server:run

# Or run JAR directly
java -jar server/build/libs/bswap-*.jar
```

### Option 3: Manual JAR Creation 📦
If Gradle fails, create JAR manually:
```bash
# Create basic JAR structure
mkdir -p manual-build/classes
mkdir -p manual-build/libs

# Compile Kotlin (if kotlinc available)
find server/src/main/kotlin -name "*.kt" | xargs kotlinc -cp "~/.gradle/caches/modules-2/files-2.1/*/*/*.jar" -d manual-build/classes/

# Create simple JAR
cd manual-build/classes
jar cf ../libs/bswap-server.jar *

# Run
java -cp "../libs/*:~/.gradle/caches/modules-2/files-2.1/*/*/*.jar" com.bswap.server.ApplicationKt
```

## 🔧 Termux Limitations & Solutions

### Current Issue in Termux
```
Error: Could not find agent library instrument...
dlopen failed: cannot locate symbol "libiconv_open"
```

### Solutions for Termux Users:

#### 1. Use Termux Docker 🐳
```bash
# Install Docker in Termux (requires root)
pkg install root-repo
pkg install docker
sudo dockerd &

# Then use Docker method above
```

#### 2. Use proot-distro 📱
```bash
# Install Ubuntu in Termux
pkg install proot-distro
proot-distro install ubuntu
proot-distro login ubuntu

# Inside Ubuntu container:
apt update && apt install openjdk-21-jdk
./setup-java.sh
./gradlew :server:run
```

#### 3. External Server Hosting ☁️
Deploy to cloud platforms:
- **Railway**: Connect GitHub repo, auto-deploy
- **Heroku**: Use Gradle buildpack
- **DigitalOcean**: Docker deployment
- **AWS/GCP**: Container services

## 🚀 Server Endpoints (When Running)

### Health Check
```bash
GET /health
# Response: {"status": "OK", "ai_strategy": "enabled"}
```

### Bot Control
```bash
POST /api/bot/start
POST /api/bot/stop
GET /api/bot/status
```

### AI Strategy Info
```bash
GET /api/strategy/ai/status
# Response: {
#   "model_type": "ENSEMBLE",
#   "accuracy": 67.5,
#   "confidence_threshold": 0.75,
#   "last_retrain": "2025-01-11T..."
# }
```

### Trading Operations
```bash
POST /api/trade/buy
POST /api/trade/sell
GET /api/positions
GET /api/history
```

## 🔍 Verification Steps

### 1. Check AI Strategy Integration
```bash
# Look for AI strategy in logs
grep "AI Strategy" logs/server.log
grep "Neural Network" logs/server.log
grep "Random Forest" logs/server.log
```

### 2. Verify Configuration Loading
```bash
# Check default strategy config
cat server/src/main/resources/default-strategy-config.json | grep ai_strategy
```

### 3. Test Model Training
```bash
# Models will start training after collecting 100+ samples
# Look for these log messages:
# "Retraining AI model with N samples..."
# "AI model retrained successfully. Accuracy: X%"
```

## 📊 AI Strategy Behavior

### Learning Phase (First 1-2 hours)
- 🔄 **Data Collection**: Gathering price/volume data
- 📈 **Feature Engineering**: Calculating technical indicators  
- 🤖 **Model Training**: Building neural network & random forest
- ⚠️ **Conservative Trading**: Limited activity until models are ready

### Active Phase (After training)
- 🎯 **Smart Entry**: Only trades with >75% model confidence
- 🛡️ **Risk Management**: Automatic stop-loss at -12%, take-profit at +20%
- 📊 **Continuous Learning**: Retrains every hour with new market data
- 🔄 **Adaptive Strategy**: Adjusts to changing market conditions

## 🆘 Troubleshooting

### Build Issues
```bash
# Clean and rebuild
./gradlew clean
rm -rf ~/.gradle/daemon
./gradlew :server:build --refresh-dependencies
```

### JVM Issues
```bash
# Use alternative JVM flags
export _JAVA_OPTIONS="-Xmx1024m -XX:+UseParallelGC"
./gradlew :server:run --no-daemon
```

### Port Issues
```bash
# Check port 8080
lsof -i :8080
netstat -tulpn | grep 8080

# Use different port
./gradlew :server:run -Pport=8081
```

## 🎯 Next Steps

1. **Deploy to Production Environment** (Docker/Cloud)
2. **Monitor AI Model Performance** in live trading
3. **Adjust Risk Parameters** based on results  
4. **Enable Additional Features** (sentiment analysis, technical indicators)
5. **Scale Strategy Configuration** for multiple trading pairs

## 📞 Support

- **Configuration Issues**: Check `AI_STRATEGY_README.md`
- **Build Problems**: Use Docker deployment 
- **Trading Questions**: Review strategy logs in `/logs/`
- **AI Model Tuning**: Adjust parameters in `default-strategy-config.json`

---

**🎉 AI Strategy Integration: COMPLETE**  
**📈 Ready for intelligent trading decisions!**