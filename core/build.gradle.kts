import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// Android is opt-in via an EXPLICIT signal, not by sniffing ANDROID_HOME — GitHub's hosted runners
// ship the SDK preset, so sniffing would (wrongly) turn the Android target on in the JVM-only CI job
// and require a full Android toolchain there. The Android APK workflow sets ENABLE_ANDROID=1; local
// dev is detected via local.properties' sdk.dir (what Android Studio writes). Everywhere else this
// stays a pure JVM-only KMP module. Nothing in commonMain touches a platform API, so this is purely a
// build-config addition.
val androidEnabled = System.getenv("ENABLE_ANDROID") == "1" ||
    rootProject.file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }

if (androidEnabled) {
    pluginManager.apply("com.android.library")
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

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
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

if (androidEnabled) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "dev.hnm.workbench.core"
        compileSdk = 35
        defaultConfig {
            minSdk = 26
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
