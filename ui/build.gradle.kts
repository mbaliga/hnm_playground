import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Android is opt-in: only when an SDK is present (CI). This keeps `:ui` a JVM-only Compose module in
// SDK-less environments (the desktop window + headless render tests still build), while letting CI add
// a real `androidTarget()` so the Android app can host the very same `WorkbenchApp` composables. All
// the composables live in commonMain, so this is purely a build-config addition.
val androidEnabled = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
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
        compileSdk = 34
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
