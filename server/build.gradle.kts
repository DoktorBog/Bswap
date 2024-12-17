plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.bswap.server"
version = "1.0.0"
application {
    mainClass.set("com.bswap.server.ApplicationKt")
    applicationDefaultJvmArgs =
        listOf("-Dio.ktor.development=${extra["io.ktor.development"] ?: "false"}")
}

ktor {
    fatJar {
        archiveFileName.set("bswap-$version.jar")
    }
}


dependencies {

    implementation(libs.metaplex.solana)
    implementation(libs.metaplex.solana.rpc)

    implementation(libs.logback)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    //testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test.junit)

    // Ktor Client for HTTP Requests
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // Ktor Content Negotiation for JSON Parsing
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.server.content.negotiation)

    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.html.jvm)

    implementation(libs.sol4k)
}