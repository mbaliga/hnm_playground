# Modules, the critical seam, and roadmap

## Intended layout

```
/core            (commonMain) — IR, sequencer/clock, coupling transforms, PatternRenderer,
                                backend interfaces, exporters.  ZERO platform dependencies.
/backend-android (androidMain) — AndroidHapticBackend, AndroidAudioBackend, CapabilityProbe
/backend-desktop (jvmMain)     — JvmAudioBackend, controllers (SDL rumble, DualSense HID)
/ui              (commonMain, Compose MP) — timeline, envelope editor, palette, screens
/androidApp                    — Android entrypoint
/desktopApp                    — JVM entrypoint
```

## What exists in this repo

- **`:core`** — the keystone, implemented and unit-tested on a `jvm()` target. Source sets are
  arranged so an `androidTarget()` is a pure build-config addition (nothing in `commonMain` touches a
  platform API).
- **`:ui`** — the Compose Multiplatform editor. Composables live in `commonMain` (so an Android UI can
  reuse them); the desktop window entrypoint is in the `jvm` target. It build-verifies headlessly:
  `:ui:jvmTest` paints the whole tree off-screen with `ImageComposeScene` to a PNG, no display needed.
- **`:desktopApp`** — a JVM entrypoint that wires `core` to a `javax.sound` audio backend and drives
  the full export/render path from the command line (`JvmAudioBackend`, `Main.kt`).

The other modules are documented below and in [ANDROID.md](ANDROID.md). They are intentionally not
wired into `settings.gradle.kts` yet because they require the Android SDK or native HID toolchains
that are not provisioned in this build image. Adding them does not change `core`.

## The critical seam (why this split matters)

`core` exposes two *kinds* of output so a Rust core can later be grafted **only where it pays off**
(the DSP) without touching the IR, sequencer, UI, or Android:

- **Continuous signal** (audio + voice-coil haptics) → a pull-based `FloatStream`. The Rust-graftable
  path. Consumed by `AudioBackend` and the DualSense voice-coil backend.
- **Discrete commands** (event-scheduled hardware, i.e. Android Vibrator/Composition) → a list of
  timed `HapticCommand`s the OS schedules itself. Stays Kotlin; it's a native API. Consumed by the
  Android backend.

Both share one `TransportClock` so audio and haptics line up, with per-backend latency compensation
(`scheduleAt(time, latencyComp, action)`).

## Build order & status (M0–M7)

| Milestone | Scope | Status |
|---|---|---|
| **M0** Skeleton | KMP + serialization wired, interfaces, Linux JVM CI | ✅ done |
| **M1** Android haptic loop | IR → `scheduleHaptics` → Vibrator; capability probe | ✅ core done · 📋 Android glue in [ANDROID.md](ANDROID.md) |
| **M2** Audio engine | oscillators + envelopes + filter → `renderAudio`; WAV export | ✅ done (audio out is desktop; Android audio is glue) |
| **M3** Coupling | envelope follower + sonify on shared clock, latency comp | ✅ done in `core` |
| **M4** Editor UI | Compose MP timeline / envelope editor / palette / inspector / library / A-B | ✅ done (`:ui`, headless-rendered in CI) |
| **M5** Exporters | Kotlin `VibrationEffect` + AHAP (+ JSON + WAV) | ✅ done |
| **M6** Desktop backend | JVM audio ✅ · Xbox rumble (SDL) → DualSense HID | ✅ audio · 📋 controllers |
| **M7** Polish | mutate/randomize ✅ · capture-a-rhythm ✅ · ERM degrade testing ✅ · device-variance | ✅ core done · 📋 device-variance needs hardware |

## Adding the Android target

1. Add the Android Gradle plugin + `androidTarget()` to `core/build.gradle.kts` and create
   `:androidApp` / `:backend-android` modules (`minSdk 26`).
2. Implement `HapticBackend`/`AudioBackend` per [ANDROID.md](ANDROID.md) — they consume the
   `HapticCommand` list and `FloatStream` that `core` already produces.
3. In CI, add `android-actions/setup-android` and `./gradlew :androidApp:assembleDebug`.

## Adding desktop controllers (M6)

- **Xbox/SDL rumble first** to validate the controller path (e.g. jamepad / libgdx-controllers).
- **DualSense voice-coil over raw HID** (e.g. hid4java): consume `renderHapticWaveform(...)` and
  stream the `FloatStream` as audio-rate samples in the vendor HID output report. This is the
  trickiest piece (§8); validate with simple rumble before layering DualSense fidelity on top.
- Export-only fallback (clearly shown in the UI) when no controller is present.
