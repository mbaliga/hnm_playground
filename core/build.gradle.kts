import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

// Android is opt-in: only when an SDK is present (CI). This keeps `core` a pure JVM-only KMP module in
// SDK-less environments while letting CI add a real `androidTarget()` for the player APK. Nothing in
// commonMain touches a platform API, so this is purely a build-config addition.
val androidEnabled = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
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
        compileSdk = 34
        defaultConfig {
            minSdk = 26
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
