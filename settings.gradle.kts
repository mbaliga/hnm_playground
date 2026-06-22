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

// A runnable JVM target that renders the IR to audio/WAV. Validates the `core` seam
// without needing the Android SDK or a physical device.
include(":desktopApp")

// NOTE: `:backend-android`, `:backend-desktop` (controllers) and `:ui` (Compose MP) are
// described in docs/MODULES.md. They require the Android SDK / native HID toolchains that
// are not provisioned in this CI image, so they are intentionally not wired into the build
// yet. See docs/MODULES.md for the intended layout and how to enable them.
