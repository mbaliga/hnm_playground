# Haptics + Audio Workbench

A cross-platform tool for **designing, feeling, and exporting haptic + sound effects**.
Android-first, with JVM desktop next. Two equally-weighted intents: a *playground* to explore and
feel effects, and a *dev tool* that exports effects into real apps.

Built with **Kotlin Multiplatform**; `kotlinx.serialization` is the native save format.

## The keystone: a backend-agnostic IR

Audio is universal (synthesized identically everywhere); haptics is platform-specific (and most
desktops have *no actuator at all*). So everything rests on a backend-agnostic **effect definition
(the IR)** that renders to whatever playback backend exists on the current platform — Android's
Vibrator, a controller voice-coil, or pure audio. Design the effect once; each backend interprets the
same data.

The IR borrows Apple's Core Haptics model: events on a timeline, each carrying **intensity +
sharpness** plus animatable breakpoint curves. See [`core/.../ir/Ir.kt`](core/src/commonMain/kotlin/dev/hnm/workbench/core/ir/Ir.kt).

## What's implemented now

This repo currently delivers the **`core` module** — the platform-agnostic keystone — fully built and
unit-tested on a JVM target, plus a runnable desktop driver:

| Area | Status | Where |
|---|---|---|
| IR (`@Serializable` events/tracks/curves/couplings) | ✅ | `core/.../ir/Ir.kt` |
| Native JSON save/load (polymorphic, `"type"` discriminator) | ✅ | `core/.../ir/Serialization.kt` |
| `PatternRenderer`: `renderAudio`, `renderHapticWaveform`, `scheduleHaptics` | ✅ | `core/.../dsp/DefaultPatternRenderer.kt` |
| DSP: oscillators, ADSR, biquad filter, parameter curves | ✅ | `core/.../dsp/` |
| Coupling: envelope follower (audio→haptic), sonify (haptic→audio) | ✅ | `core/.../dsp/` |
| Shared `TransportClock` + latency compensation | ✅ | `core/.../playback/TransportClock.kt` |
| Capability model + graceful degradation (LRA / ERM / wideband) | ✅ | `core/.../playback/Backends.kt` |
| Exporters: Native JSON, Kotlin `VibrationEffect`, AHAP, WAV | ✅ | `core/.../export/` |
| JVM desktop audio backend (`javax.sound`) + CLI driver | ✅ | `desktopApp/` |
| Android Vibrator backend + capability probe | 📋 reference | [docs/ANDROID.md](docs/ANDROID.md) |
| Controller backends (SDL rumble, DualSense HID) | 📋 planned | [docs/MODULES.md](docs/MODULES.md) |
| Compose Multiplatform editor UI | 📋 planned | [docs/MODULES.md](docs/MODULES.md) |

> The Android app, controller-HID backends, and Compose UI need the Android SDK / native toolchains
> that aren't provisioned in this CI image, so they're documented as a copy-ready reference rather
> than shipped as code that wouldn't compile here. Nothing in `core` depends on a platform, so adding
> those targets is purely build-config + glue. See [docs/MODULES.md](docs/MODULES.md).

## Run it

```bash
# Build everything and run the test suite
./gradlew build
./gradlew :core:jvmTest

# Render the worked "Confirm" example: prints native JSON / Kotlin / AHAP exports and
# writes confirm-audio.wav + confirm-haptic.wav. Add --play to hear it (if an output device exists).
./gradlew :desktopApp:run
./gradlew :desktopApp:run --args="--play"
```

The driver reproduces the brief's worked examples exactly — e.g. the Kotlin export:

```kotlin
fun confirmEffect(): VibrationEffect =
    VibrationEffect.startComposition()
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.8f, 0)
        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 80)
        .compose()
```

## Architecture & roadmap

See [docs/MODULES.md](docs/MODULES.md) for the module layout, the critical Rust-graftable seam, and
the M0–M7 build order with current status.
