rootProject.name = "hnm-playground"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// The keystone: a backend-agnostic IR + DSP + exporters, with zero platform deps.
include(":core")

// Compose Multiplatform editor UI (timeline, envelope editor, palette). commonMain composables so an
// Android UI can reuse them; the desktop window entrypoint lives in the module's jvm target.
include(":ui")

// A runnable JVM target that renders the IR to audio/WAV. Validates the `core` seam
// without needing the Android SDK or a physical device.
include(":desktopApp")

// The Android player app. It needs the Android SDK, so it is only wired into the build when an SDK is
// available (CI installs one). In SDK-less environments the module is skipped and `:core` stays a
// JVM-only KMP module — keeping `./gradlew build` green here while CI builds the real APK.
if (androidSdkAvailable()) {
    include(":androidApp")
}

// NOTE: `:backend-desktop` (controller HID backends) is described in docs/MODULES.md. It requires
// native HID/SDL toolchains that are not provisioned here, so it is not wired in yet.

fun androidSdkAvailable(): Boolean {
    if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null) return true
    val localProps = rootDir.resolve("local.properties")
    return localProps.exists() && localProps.readText().contains("sdk.dir")
}
