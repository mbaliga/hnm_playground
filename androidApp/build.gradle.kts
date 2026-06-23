plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "dev.hnm.workbench.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.hnm.workbench.android"
        minSdk = 31 // VibratorManager + VibrationEffect.Composition primitives
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.1"
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
}
