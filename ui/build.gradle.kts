import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Android is opt-in via an EXPLICIT signal (see :core for the full rationale) — never by sniffing
// ANDROID_HOME, since hosted CI runners ship the SDK preset and would otherwise drag a full Android +
// Compose toolchain into the JVM-only build. The Android APK workflow sets ENABLE_ANDROID=1; local dev
// is detected via local.properties' sdk.dir. Everywhere else `:ui` stays a JVM-only Compose module
// (the desktop window + headless render tests still build). All composables live in commonMain, so the
// `androidTarget()` is purely a build-config addition that lets the Android app host the same UI.
val androidEnabled = System.getenv("ENABLE_ANDROID") == "1" ||
    rootProject.file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidEnabled) {
    pluginManager.apply("com.android.library")
}

kotlin {
    jvm()

    if (androidEnabled) {
        androidTarget {
            compilations.all {
                compileTaskProvider.configure {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

if (androidEnabled) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "dev.hnm.workbench.ui"
        compileSdk = 35
        defaultConfig {
            minSdk = 31
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

// `./gradlew :ui:run` launches the editor window (needs a display). The composables themselves are in
// commonMain and compile headlessly in CI, so the UI is build-verified even without a screen.
compose.desktop {
    application {
        mainClass = "dev.hnm.workbench.ui.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "HapticsAudioWorkbench"
            packageVersion = "1.0.0"
        }
    }
}
