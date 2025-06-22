plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    kotlin("plugin.serialization") version "2.1.21"
}

kotlin {
    androidTarget()
    jvm()
    wasmJs()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sol4k)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.sol4k)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.wallet.core)
            }
        }
        val wasmJsMain by getting
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
