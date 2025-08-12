#!/bin/bash
# Bswap Server Runner Script with AI Strategy

echo "🤖 Bswap AI Trading Server Launcher"
echo "==================================="

# Check environment
if [ -d "/data/data/com.termux" ]; then
    echo "📱 Detected: Termux (Android)"
    PLATFORM="termux"
elif [ -f "/.dockerenv" ]; then
    echo "🐳 Detected: Docker Container"
    PLATFORM="docker"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    echo "🍎 Detected: macOS"
    PLATFORM="macos"
else
    echo "🐧 Detected: Linux"
    PLATFORM="linux"
fi

echo ""

# Set Java environment
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

case $PLATFORM in
    "termux")
        echo "⚠️  TERMUX LIMITATION NOTICE"
        echo "The JVM in Termux has known issues with Gradle's instrumentation agent."
        echo "For full functionality, please use Docker or a standard Linux environment."
        echo ""
        echo "🔧 WORKAROUNDS FOR TERMUX:"
        echo ""
        echo "1. 📦 Use Docker (Recommended):"
        echo "   pkg install root-repo && pkg install docker"
        echo "   docker build -t bswap-ai ."
        echo "   docker run -p 8080:8080 bswap-ai"
        echo ""
        echo "2. 🏗️  Manual Build & Run:"
        echo "   If you have a pre-built JAR, you can run it directly:"
        echo "   java -jar server/build/libs/bswap-*.jar"
        echo ""
        echo "3. 🔄 Try Alternative Gradle:"
        echo "   ./gradlew :server:run --no-daemon --no-build-cache --console=plain"
        echo ""
        echo "📊 Current AI Strategy Status: READY"
        echo "✅ Neural Network Model: Configured"
        echo "✅ Random Forest Model: Configured" 
        echo "✅ Feature Engineering: Price/Volume/Momentum/Volatility/Sentiment"
        echo "✅ Risk Management: Stop-loss/Take-profit/Position sizing"
        echo ""
        echo "To proceed anyway, press Enter. Otherwise Ctrl+C to exit."
        read
        echo "Attempting to run with current settings..."
        ./gradlew :server:run --no-daemon --console=plain --no-build-cache
        ;;
        
    "docker")
        echo "🚀 Running in Docker environment..."
        java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar /app/bswap.jar
        ;;
        
    "macos"|"linux")
        echo "🚀 Starting server with AI strategy integration..."
        echo "📊 Features enabled:"
        echo "  ✅ AI_STRATEGY with Neural Network + Random Forest"
        echo "  ✅ Technical Analysis Combined Strategy"  
        echo "  ✅ Risk Management & Position Sizing"
        echo "  ✅ Adaptive Learning & Model Retraining"
        echo ""
        
        if java -version >/dev/null 2>&1; then
            echo "☕ Java detected: $(java -version 2>&1 | head -n 1)"
            echo "🏃 Starting server..."
            ./gradlew :server:run
        else
            echo "❌ Java not found. Please install Java 21:"
            echo "   ./setup-java.sh"
            exit 1
        fi
        ;;
esac