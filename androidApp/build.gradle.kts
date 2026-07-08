plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    // Compose Multiplatform + compiler so the app can host the shared `WorkbenchApp` composables.
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "dev.hnm.workbench.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hnm.workbench.android"
        minSdk = 31 // VibratorManager + VibrationEffect.Composition primitives
        targetSdk = 34
        versionCode = 25
        versionName = "0.24.0"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    // The shared Compose workbench UI (timeline, palettes, library, inspector, export).
    implementation(project(":ui"))

    // Compose runtime/UI for Android, sourced from the Compose Multiplatform plugin's `compose` DSL.
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    // `setContent { }` entrypoint for a ComponentActivity.
    implementation(libs.androidx.activity.compose)
}
