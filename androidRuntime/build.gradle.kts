plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}
android {
    namespace = "dev.hnm.workbench.runtime"
    compileSdk = 35
    defaultConfig { minSdk = 31; testInstrumentationRunner = "android.test.InstrumentationTestRunner" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    androidTestCompileOnly(files(
        "${android.sdkDirectory}/platforms/android-35/optional/android.test.runner.jar",
        "${android.sdkDirectory}/platforms/android-35/optional/android.test.base.jar",
    ))
    api(project(":core"))
    api(libs.kotlinx.coroutines.core)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
