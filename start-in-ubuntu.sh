#!/bin/bash
echo "🚀 Starting Bswap AI Trading Server in Ubuntu Container..."

# Login to Ubuntu and start server
proot-distro login ubuntu --bind /data/data/com.termux/files/home/Bswap:/workspace << 'EOF'
cd /workspace
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export PATH=$JAVA_HOME/bin:$PATH

echo "☕ Java Version: $(java -version 2>&1 | head -n 1)"
echo "🏗️  Starting build and server..."
echo ""

# Build and run the server
./gradlew :server:run --no-daemon --console=plain --info | grep -E "(AI|Strategy|server|ERROR|BUILD|FAILED)"

EOF