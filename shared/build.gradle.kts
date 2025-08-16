plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    kotlin("plugin.serialization") version "2.1.21"
}

kotlin {
    jvmToolchain(17)
    val enableWasm = project.findProperty("enableWasm") == "true"

    androidTarget()
    jvm()
    if (enableWasm) {
        wasmJs {
            browser()
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sol4k)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.sol4k)
                implementation("org.bouncycastle:bcprov-jdk15on:1.70")
                implementation(libs.ktor.client.cio)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.wallet.core)
                implementation(libs.sol4k)
                implementation(libs.ktor.client.okhttp)
            }
        }
        if (enableWasm) {
            val wasmJsMain by getting {
                dependencies {
                    // WASM doesn't support sol4k yet
                }
            }
        }
    }
}

android {
    namespace = "com.bswap.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
    }
}
