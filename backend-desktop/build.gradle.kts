plugins {
    alias(libs.plugins.kotlinJvm)
}

// Pure JVM: javax.sound only, no Compose and no Android. Native controller backends (SDL rumble,
// DualSense HID) are a later step and would live here too (docs/MODULES.md).
dependencies {
    api(project(":core"))
    testImplementation(kotlin("test"))
}
