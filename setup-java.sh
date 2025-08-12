#!/bin/bash
# Setup script for Java development environment

set -e

echo "Setting up Java development environment for Bswap..."

# Check if we're on Termux (Android)
if [ -d "/data/data/com.termux" ]; then
    echo "Detected Termux environment"
    
    # Update packages
    pkg update && pkg upgrade -y
    
    # Install Java 21 and required tools
    pkg install -y openjdk-21 gradle git curl unzip
    
    # Set JAVA_HOME for Termux
    export JAVA_HOME="$PREFIX/opt/openjdk"
    echo "export JAVA_HOME=\"\$PREFIX/opt/openjdk\"" >> ~/.bashrc
    echo "export PATH=\"\$JAVA_HOME/bin:\$PATH\"" >> ~/.bashrc
    
else
    # Standard Linux/Unix environment
    echo "Detected standard Linux environment"
    
    # Check for common package managers and install Java 21
    if command -v apt-get >/dev/null 2>&1; then
        sudo apt-get update
        sudo apt-get install -y openjdk-21-jdk gradle curl unzip
    elif command -v yum >/dev/null 2>&1; then
        sudo yum update -y
        sudo yum install -y java-21-openjdk-devel gradle curl unzip
    elif command -v dnf >/dev/null 2>&1; then
        sudo dnf update -y
        sudo dnf install -y java-21-openjdk-devel gradle curl unzip
    elif command -v brew >/dev/null 2>&1; then
        brew update
        brew install openjdk@21 gradle curl
        echo 'export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"' >> ~/.zshrc
        echo 'export JAVA_HOME="/opt/homebrew/opt/openjdk@21"' >> ~/.zshrc
    else
        echo "No supported package manager found. Please install Java 21 manually."
        exit 1
    fi
fi

# Verify Java installation
echo "Verifying Java installation..."
java -version
javac -version
gradle -version

# Make gradlew executable
chmod +x ./gradlew

echo "Java development environment setup complete!"
echo "You can now build the server with: ./gradlew :server:build"
echo "Run the server with: ./gradlew :server:run"