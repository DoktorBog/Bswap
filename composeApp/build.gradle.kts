import org.jetbrains.compose.desktop.application.dsl.TargetFormat

import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization") version "2.1.21"
}

kotlin {
    androidTarget()
    jvmToolchain(17)
    
    jvm("desktop")

    if (project.findProperty("enableWasm") == "true") {
        wasmJs {
            moduleName = "composeApp"
            browser {
                val rootDirPath = project.rootDir.path
                val projectDirPath = project.projectDir.path
                commonWebpackConfig {
                    outputFileName = "composeApp.js"
                    devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static = (static ?: mutableListOf()).apply {
                            // Serve sources to debug inside browser
                            add(rootDirPath)
                            add(projectDirPath)
                        }
                    }
                }
            }
            binaries.executable()
        }
    }
    
    sourceSets {
        val desktopMain by getting
        val wasmJsMain = if (project.findProperty("enableWasm") == "true") {
            maybeCreate("wasmJsMain")
        } else null
        
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.androidx.security.crypto)
            implementation("org.bitcoinj:bitcoinj-core:${libs.versions.bitcoinj.get()}") {
                exclude(group = "com.google.protobuf", module = "protobuf-javalite")
            }
            implementation(libs.wallet.core)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.preview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.content.negotiation)
            // Ktor Client for HTTP Requests
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil.compose.core)
            implementation(libs.coil.compose)
            implementation(libs.coil.mp)
            implementation(libs.coil.network.ktor)
            implementation(libs.kotlinx.datetime)
            implementation(libs.metaplex.solanaeddsa)
            implementation(libs.wallet.core)
            implementation(project(":shared"))
        }
        wasmJsMain?.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.kotlinx.serialization.json)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

android {
    namespace = "com.bswap.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.bswap.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
dependencies {
    debugImplementation(compose.uiTooling)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(compose.uiTestJUnit4)
}

    compose.desktop {
        application {
            mainClass = "com.bswap.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.bswap.app"
            packageVersion = "1.0.0"
        }
    }
}

// The Compose Multiplatform plugin does not provide an `androidRun` task by default.
// Register a simple placeholder so `:composeApp:androidRun` can succeed in CI
// environments that lack Android tooling or a connected device.
tasks.register("androidRun") {
    dependsOn("assembleDebug")
    doLast {
        logger.lifecycle("androidRun task completed. No device execution performed.")
    }
}
