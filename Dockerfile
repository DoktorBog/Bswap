# Multi-stage build for Bswap server with Java support
FROM openjdk:21-jdk-slim AS builder

# Install required tools
RUN apt-get update && apt-get install -y \
    curl \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Copy project files
WORKDIR /app
COPY . .

# Make gradlew executable and build
RUN chmod +x ./gradlew
RUN ./gradlew :server:build --no-daemon

# Runtime stage
FROM openjdk:21-jre-slim

# Install runtime dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r bswap && useradd --no-log-init -r -g bswap bswap

# Copy built application
COPY --from=builder /app/server/build/libs/bswap-*.jar /app/bswap.jar

# Change ownership
RUN chown -R bswap:bswap /app

# Switch to non-root user
USER bswap

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run the application
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/bswap.jar"]