plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}
android {
    namespace = "dev.hnm.workbench.wear"
    compileSdk = 35
    defaultConfig {
        // Data Layer requires the same application ID and signing identity as the phone app.
        applicationId = "dev.hnm.workbench.android"
        minSdk = 31
        targetSdk = 34
        versionCode = 30
        versionName = "0.25.0-mvp02"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":androidRuntime"))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.ui:ui:1.8.2")
    implementation("androidx.compose.foundation:foundation:1.8.2")
    // Stable Wear components compatible with this repository's Compose/Kotlin toolchain.
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear.compose:compose-navigation:1.4.1")
}
