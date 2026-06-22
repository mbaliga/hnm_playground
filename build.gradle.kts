plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    // Android plugins are only *applied* when an SDK is present (see :core / :androidApp). Declaring
    // them `apply false` just puts AGP on the classpath; it does not require the SDK, so the JVM-only
    // build in SDK-less environments stays green.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}

allprojects {
    group = "dev.hnm.workbench"
    version = "0.1.0"
}
