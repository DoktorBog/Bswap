plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    kotlin("plugin.serialization") version "2.1.21"
}

group = "com.bswap.server"
version = "1.0.0"
application {
    mainClass.set("com.bswap.server.ApplicationKt")
    applicationDefaultJvmArgs =
        listOf("-Dio.ktor.development=${extra["io.ktor.development"] ?: "false"}")
}

// Allow interactive input when running via `./gradlew :server:run`
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

ktor {
    fatJar {
        archiveFileName.set("bswap-$version.jar")
    }
}


dependencies {

    implementation(libs.logback)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    testImplementation(libs.kotlin.test.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.server.content.negotiation)

    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.html.jvm)
    implementation(libs.sol4k)
    implementation(libs.metaplex.solana)
    implementation(libs.metaplex.solana.rpc)

    implementation(project(":shared"))
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")   // актуальная на август 2025
}

// Exclude Android-only wallet-core AAR when resolving JVM server configurations
configurations.configureEach {
    exclude(group = "com.portto", module = "walletcore")
}
