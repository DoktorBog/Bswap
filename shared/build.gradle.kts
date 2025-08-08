plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    kotlin("plugin.serialization") version "2.1.21"
}

kotlin {
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
                implementation(libs.sol4k)
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
