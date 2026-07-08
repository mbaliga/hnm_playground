# Haptics + Audio Workbench — Engineering Handoff

> A detailed transfer document for the next engineer or agent. It covers what the
> project is, how it's built, every subsystem and where it lives, the current state,
> the honest limitations, and the concrete next steps. Read this top-to-bottom once;
> then use the **Key files** table as a map.

**Repo:** `mbaliga/hnm_playground`
**Default branch:** `main`
**Working/feature branch:** `claude/haptics-audio-workbench-lgmpsf`
**Current app version:** `0.20.0` (versionCode 21) — in-app diagnostics line reads `build v0.20`
**Latest debug APK:** published to the rolling GitHub Release tag `android-player-debug`
(filename is version+SHA stamped, e.g. `haptics-player-vX.Y.Z-<shortsha>.apk`).

---

## 0. UX rebuild status (v1.1 brief, Phases 0–4 of 8)

Since `v0.15.0` the app has been rebuilt around a new IA and a Hyle-derived design system,
per a separate "UX Build Brief v1.1" (D1–D6 decisions, per-screen specs, an 8-phase plan).
Phases 0–4 are done; **see [STATE.md](STATE.md) for the up-to-date one-paragraph summary of
each**. In short:

- `AppShell` replaces the old single-activity gallery flow: three tabs (**Feel** home / **Make**
  / **Device**) plus a full-screen **Editor** route entered from any tab.
- Hyle Design System (`mbaliga/Hyle-Design-System`) turned out to ship **tokens only** — no
  reusable Compose Multiplatform visuals (its glass/motion/grid demos are AGSL `RuntimeShader`
  code, Android-API-33+-only, living in a throwaway demo app, not the library module). So
  `ui/.../theme/HyleTokens.kt` / `HyleProvenance.kt` are **vendored verbatim** from Hyle's token
  source; the dot-grid/glass/glow widgets in `ui/.../components/HyleWidgets.kt` are **original,
  hand-built** Multiplatform-safe implementations informed by Hyle's values, not a live dependency
  or a literal port of its shaders.
- The Editor (`WorkbenchApp`) is still the pre-existing dense panel layout — Phase 4 scoped down
  to giving it a real top bar (back arrow, tap-to-rename, undo/redo) rather than the brief's full
  ask (pinch-zoom timeline, drag-snap, long-press-add, scrub-to-feel). That gesture-level rewrite
  is explicitly deferred, not silently dropped — see `STATE.md`'s Next steps.
- Phases 5–7 (technical workspace, onboarding + device hero card, polish pass) are not started.
- Acceptance criteria that need a physical device or a human tester (60fps on-device, a TalkBack
  pass, "≤60s to first satisfying pattern" by stopwatch) cannot be machine-verified in this
  environment, consistent with this doc's existing §9 caveat that on-actuator feel is
  owner-verified only.

---

## 1. What this is

A cross-platform tool for authoring **haptic + audio patterns together** and feeling them
on a real phone actuator. The thesis: you design a *feeling* by describing it or by
manipulating a physical/material handle (a texture, a material strike, a motion primitive),
**not** by drawing raw vibration waveforms — because a perceptual handle controls felt
vibration far more reliably than a number does. The same authored pattern renders to:

- **Audio** (speaker),
- a **continuous haptic waveform** (voice-coil / wideband actuators, e.g. DualSense),
- **discrete OS haptic commands** (Android `VibrationEffect`).

It ships two front-ends off one shared core:
1. **Compose Multiplatform workbench** — the editor (timeline, inspector, palettes, export,
   AI assistant, device simulator, AHAP import). Runs on desktop (JVM window) and Android.
2. **Android on-device player** — a flat "feel-test gallery" that plays the whole built-in
   vocabulary on the real actuator with honest diagnostics.

---

## 2. Modules & repository layout

Gradle multi-module KMP build. `settings.gradle.kts` wires:

| Module | Type | What it is |
|--------|------|------------|
| `:core` | KMP (jvm + optional android) | **The keystone.** Backend-agnostic IR, DSP renderer, exporters/importers, design vocabulary, device DB, splash logic. Zero platform deps. Fully unit-tested in `commonTest`. |
| `:ui` | KMP Compose (jvm + optional android) | Shared Compose workbench UI. All composables live in `commonMain`; `jvmMain` has the desktop window entry; `jvmTest` has headless render tests. |
| `:desktopApp` | JVM app | Runnable JVM target that renders IR → audio/WAV; validates the `core` seam without an Android device. `JvmAudioBackend` plays audio on desktop. |
| `:androidApp` | Android app | The phone app. **Only wired into the build when Android is explicitly enabled** (see below). |

**Android is opt-in, never sniffed.** `:androidApp` is included and `:core`/`:ui` add the
`androidTarget()` only when `ENABLE_ANDROID=1` (CI) **or** a `local.properties` with `sdk.dir`
exists. This is deliberate: hosted CI runners ship a preset `ANDROID_HOME`, and sniffing it
would drag a full Android/Compose toolchain into the JVM-only build. See `settings.gradle.kts`
`androidSdkAvailable()` and the `androidEnabled` blocks in `core/build.gradle.kts` /
`ui/build.gradle.kts`.

**Consequence for local dev in this environment:** there is **no Android SDK**, so you
**cannot compile `:androidApp` locally**. Core + UI (JVM) compile and test locally; the
Android app is validated only by CI. Write Android code carefully and lean on CI.

**Toolchain versions** (`gradle/libs.versions.toml`): Kotlin 2.1.21, Compose MP 1.8.2,
AGP 8.7.3, kotlinx-serialization 1.8.1, coroutines 1.10.2. Android `compileSdk=35`,
`minSdk=31`, `targetSdk=34`, JVM target 17.

---

## 3. Build / test / run

```bash
# JVM-only (works locally; no Android SDK needed):
./gradlew build                 # compiles + tests core, ui, desktopApp
./gradlew :core:jvmTest         # core unit tests
./gradlew :ui:jvmTest           # Compose headless render tests (writes PNGs to ui/build/preview/)
./gradlew :ui:run               # launches the desktop workbench window (needs a display)
./gradlew :desktopApp:run       # renders IR to audio on the JVM

# Android (CI only here; requires the SDK):
ENABLE_ANDROID=1 ./gradlew :androidApp:assembleDebug
```

**Environment gotchas:**
- Outbound HTTPS goes through an agent proxy with a custom CA bundle. Gradle already trusts
  it via `JAVA_TOOL_OPTIONS` (see the truststore in build logs). Don't disable TLS.
- `Date.now()` / `Math.random()` / arg-less `new Date()` are **blocked inside Workflow
  scripts** (not in normal app code). Splash seeds in app code use `SystemClock` /
  `System.currentTimeMillis()`, which is fine.

---

## 4. Core concepts (read these before touching anything)

### 4a. The IR — `core/.../ir/Ir.kt`
`HapticAudioPattern` = the single source of truth. Modeled on Apple Core Haptics so it maps
cleanly to AHAP. Shape:

- `HapticAudioPattern { version, name, tracks[], couplings[], metadata }`
- `Track` (sealed): `HapticTrack` | `AudioTrack`, each with `events[]` + `curves[]`.
- `HapticEvent` (sealed):
  - `Transient(time, intensity, sharpness)` — a tap/click.
  - `Continuous(time, duration, intensity, sharpness, envelope)` — a sustained buzz.
  - `Primitive(time, type, scale)` — an Android composition primitive (CLICK/TICK/LOW_TICK/
    THUD/SPIN/QUICK_RISE/SLOW_RISE/QUICK_FALL).
- `AudioEvent` (sealed): `OscEvent` (waveform, freq, gain, envelope, filter) | `SampleEvent`.
- `ParameterCurve` — "live knobs over time" (intensity/sharpness/gain/pitch/cutoff) with
  STEP/LINEAR/SMOOTH interpolation.
- `Coupling` — audio↔haptics cross-drive (AUDIO_DRIVES_HAPTICS / HAPTICS_DRIVES_AUDIO).
- `intensity`/`sharpness` are 0..1; `sharpness` is perceptual (dull thud → crisp tick).

Serialization: `ir/Serialization.kt` (`PatternSerialization`) — kotlinx.serialization JSON
with a polymorphic `SerializersModule` and `"type"` class discriminator. This JSON is the
**native save format**; AHAP/Kotlin/WAV are *exports*.

### 4b. The rendering seam — `core/.../playback/Backends.kt` + `dsp/DefaultPatternRenderer.kt`
`PatternRenderer` exposes three output kinds so each backend consumes the side that fits:
- `renderAudio(pattern, sr): FloatStream` — continuous audio (Rust-graftable DSP).
- `renderHapticWaveform(pattern, sr): FloatStream` — continuous haptic signal for voice-coils.
- `scheduleHaptics(pattern, caps): List<HapticCommand>` — discrete timed commands for the
  Android Vibrator (stays Kotlin; it maps onto a native OS API).

`HapticCommand` (sealed): `PlayPrimitive`, `PlayWaveform`, `PlayOneShot`, **`PlayEnvelope`**
(amplitude+frequency breakpoints — the wideband/PWLE path, added recently).

### 4c. Capabilities & devices — `playback/Backends.kt` + `core/.../device/`
`HapticCapabilities(hasVibrator, hasAmplitudeControl, supportedPrimitives, hasFrequencyControl,
actuatorType)` with 4 canned tiers: `NONE`, `ERM_BASIC`, `LRA_FULL`, `WIDEBAND`.
`ActuatorType`: ERM, LRA, WIDEBAND, CONTROLLER_RUMBLE, CONTROLLER_VOICECOIL, NONE.

`scheduleHaptics` degrades honestly by capability:
- primitives present → `PlayPrimitive` (best fidelity),
- else amplitude control + frequency control → **`PlayEnvelope`** (amplitude+frequency contour),
- else amplitude control → `PlayWaveform` (sampled amplitude),
- else → `PlayOneShot` on/off.

---

## 5. Feature inventory (what exists, where)

### Design vocabulary (`core/.../design/`)
- `MotionPrimitives.kt` — spring/swell motion feels (`MotionPrimitive` enum → pattern).
- `TextureField.kt` — procedural texture fields (Perlin/FBM/…); roughness + scrub velocity.
- `ModalSynth.kt` — material strikes (metal/wood/glass): sound + felt ring-down from one modal model. `MaterialPreset`.
- `ParameterNavigator.kt` — interpolated *families* between two feels (monotonic gradients).
- `RhythmCapture.kt` — turn tap timings into a pattern.
- `Variations.kt` — mutate/vary a pattern (seeded).
- `PatternGenerator.kt` — **the AI assistant engine** (see §8).
- `SplashScene.kt` / `SplashGeometry.kt` — **procedural splash** (see §7).

### Library & registry (`core/.../library/`)
- `BuiltInPatterns.kt` — reference vocabulary (CONFIRM, etc.).
- `PatternLibrary.kt` — in-memory, serializable save store (`withBuiltIns()`).
- `PatternRegistry.kt` — **`RegistryIndex`/`RegistryEntry`**: an attributed, tagged, versioned
  catalog of IR patterns; the seed of a shareable **publish channel** (entries are plain IR
  JSON — no new format). `seed()` builds from built-ins; `toLibrary()`/`encodePatterns()` helpers.

### Exporters / importers (`core/.../export/`)
- `AhapExporter.kt` — IR → Apple AHAP JSON (primitives decomposed to transient/continuous).
- `AhapImporter.kt` — **AHAP JSON → IR** (inverse). Tolerant of unknown keys, skips audio
  events, maps ParameterCurves. `import()` throws `AhapParseException`; `importOrNull()` is safe.
- `KotlinVibrationEffectExporter.kt` — IR → ready-to-paste Android `VibrationEffect` code.
- `WavExporter.kt` — IR → WAV.

### Device database (`core/.../device/`)
- `DeviceProfile.kt` — portable, serializable row of a device's haptic capabilities
  (actuator type, amplitude control, primitives, effects, resonant frequency, Q, frequency/
  envelope control, source, notes). `toCapabilities()` maps into the renderer model.
- `DeviceDatabase.kt` — seeded with real reference tiers: Pixel 8/6, Galaxy S23, budget ERM,
  Android-16 wideband, iPhone 15 (Taptic), PS5 DualSense, desktop. JSON load/save +
  `upsert()`. **Resonant frequencies in seed rows are approximate/typical** (published
  per-model figures are rare); the on-device probe captures real values.

### UI (`ui/.../components/`, `ui/.../WorkbenchApp.kt`, `ui/.../model/EditorState.kt`)
`EditorState` is the single Compose state holder (current pattern, selection, target caps,
library, assistant, import). Panels: `TimelineView`, `InspectorPanel`, `EnvelopeEditor`,
`CapabilityPanel` (**device simulator** — abstract tiers + real-phone chips),
`LibraryPanel`, `ImportPanel` (**paste AHAP**), `AssistantPanel` (AI), `WalkthroughCard`,
`MotionPalette`, `TexturePalette`, `MaterialPalette`, `NavigatorPanel`, `PalettePanel`,
`RhythmCapturePanel`, `ExportPanel`. Recorder widgets: `RecorderWidgets.kt` (crater
`KeypadCell`, `SpeakerGrille`, `BatteryBadge`, `ScanlineOverlay`, `RecordingDot`, playhead
helpers), `SplashScreen.kt`.

`WorkbenchApp(state, onOpenGallery)` = the editor (responsive: narrow single column <720dp,
else two columns). **`WorkbenchWithSplash(state, seed, onOpenGallery)`** = the app with the
splash overlaid on first launch (this is what real entry points use; `WorkbenchApp` stays
splash-free so render tests are stable).

### Android app (`androidApp/.../`)
- **`WorkbenchActivity`** — the **LAUNCHER** activity. Hosts the Compose `WorkbenchWithSplash`,
  wires the Play button to the real actuator + speaker via `AndroidPatternPlayer`, initializes
  the target profile from what the device actually reports. "Gallery" button → `MainActivity`.
- `MainActivity` — the **flat feel-test gallery** (native Views, not Compose). Lists the whole
  vocabulary with per-row Play, a vibration self-test, honest diagnostics, and a **"Capture
  device capability report"** button (probes the device → copies `DeviceProfile` JSON to
  contribute to the DB). Also shows the splash on its own launch.
- `AndroidHaptics.kt` — bridges `core`'s schedule to the real `Vibrator`; picks the best
  rendering (composition primitives → predefined effects → stitched waveform). `probe()` reads
  amplitude/primitive/effect support; **`probeProfile()`** additionally reads resonant freq + Q
  (API 34) into a `DeviceProfile`. Renders `PlayEnvelope` by collapsing to an amplitude
  waveform (see caveat §9). Honest labeling: "on/off via standard API (rich haptics likely
  behind a vendor SDK)".
- `AndroidPatternPlayer.kt` — the `PatternPlayer` wired into `EditorState.player`.
- `AudioPlayer.kt` — plays the rendered audio stream via AudioTrack.
- `GrilleView.kt` — native Canvas dot-grid grille.
- `SplashView.kt` — native Canvas splash (see §7).

---

## 6. Visual design system — "recorder2" aesthetic

Ported faithfully from a supplied HTML mock (`recorder2.html`, the dark revision). All-dark,
premium, minimal, red accent. Colors live in `ui/.../theme/Theme.kt` (`WorkbenchColors`) and
are mirrored as constants in the Android `MainActivity`.

| Token | Hex | Use |
|-------|-----|-----|
| Screen | `#141210` | dark grey-brown content surface (not pure black) |
| Ink / InkDim | `#DDDBD6` / `#5C5A56` | primary / secondary text |
| Red | `#E22C24` | record dot, playhead, active accent, Generate/Play |
| Bar | `#D2CFC8` | waveform bars |
| Shell gradient | `#1C1A18→#111009→#0A0908` | device shell (160°), border `#282420` |
| Battery | `#0A0908` bg / `#999` text / `#252220` border | chin battery pill |
| Grille | `#0A0908` bg, white-.22 dots, `#1C1A16` border | speaker grille |
| Keypad slab / cell | `#050402` / `#161412` | transport slab + crater cells |
| Crater dome | `#020201→#2D2A1F` vertical | recessed concave key (light pools at bottom) |

Signature elements: a **device shell** (42dp radius, deep shadow), a **dark screen** with CRT
scanlines, a **monolithic keypad slab** of recessed **crater** keys with 2dp hairline seams,
**top-anchored hanging waveform bars** with a red playhead dot-cap, and a dark chin
(battery pill + dotted grille). The Compose `KeypadCell` reproduces the concave crater with a
vertical gradient + rim vignette + inner-wall shadow/highlight.

---

## 7. Procedural splash (visual + sound + haptics from one timeline)

The key idea: the animation, audio, and haptics are **all generated from one shared
`SplashScene`**, so they're coincident by construction. A seed picks one of four motifs per
launch.

- `core/design/SplashScene.kt` — `SplashVisual { RIPPLE, BLOOM, SWEEP, SPARK }`,
  `SplashScene(title, visual, pattern, beats, durationSeconds, seed)`, and `SplashMotifs`
  (`generate(seed)` deterministic; `all()` = one scene per visual). Each motif is a short IR
  "sting" (~1.8–2.0s); `beats` are the haptic event times the visual animates against.
- `core/design/SplashGeometry.kt` — **pure, tested visual math** (expanding rings, blooming
  mirrored bars, sweeping playhead + ticks, bursting/decaying sparks, master fade in/out).
  Both platforms call this so the animation logic lives in one place.
- `ui/components/SplashScreen.kt` — Compose renderer; `fixedTimeSec` draws a deterministic
  frame for tests. `WorkbenchWithSplash` overlays it on first launch and dismisses when done.
- `androidApp/SplashView.kt` — native Canvas + `Choreographer` loop; `begin()` fires the
  coincident haptics+audio via the existing player path; tap-to-skip.

**Where the splash appears:** `WorkbenchActivity` (Android launcher, plays on the real
actuator), `MainActivity` (gallery), and the desktop `Main.kt`. `WorkbenchApp` itself stays
splash-free so headless editor render tests are unaffected.

---

## 8. AI assistant (describe-a-feel)

`core/design/PatternGenerator.kt`: a deterministic, **on-device**, offline intent→pattern
engine. Routes a prompt through edits ("softer/sharper/longer/slower"), named built-ins,
materials, textures, motion primitives, or synthesis — every result carries a plain-language
`explanation`. `HybridPatternGenerator` wraps it with an **optional cloud LLM seam** (wired but
inactive — no key is baked into the public debug APK). `EditorState.generate(prompt)` runs it,
loads the result, shows the explanation, and auto-plays on a wired actuator. UI: `AssistantPanel`.

---

## 9. Known limitations / honest caveats

1. **True PWLE isn't active on Android yet.** `PlayEnvelope` (amplitude+frequency contour) is
   real and tested in `core`, but Android's `WaveformEnvelopeBuilder`/`areEnvelopeEffectsSupported()`
   are **API 36**, above the current `compileSdk=35`. So `AndroidHaptics` **collapses
   `PlayEnvelope` to an amplitude waveform** (frequency dropped). The contour is preserved in
   the IR and lights up the moment `compileSdk` is bumped to 36 and a real render path is added.
   `probeProfile()` reports `hasEnvelopeControl=false` for the same reason.
2. **Seed device resonant frequencies are approximate.** Real values come from the on-device
   probe (API 34 `getResonantFrequency()`/`getQFactor()`). The "contribute report" button is how
   the DB should grow with real data.
3. **Vendor "4D" haptics are invisible to AOSP.** Many gaming phones expose only on/off to the
   standard API and keep rich haptics behind a vendor SDK. The code says so honestly
   (`actuatorLabel()`); "no primitives" ≠ "cheap ERM".
4. **Cloud LLM path is a seam, not active.** No key shipped; the on-device generator is the
   default and fully functional offline.
5. **You cannot compile `:androidApp` in this dev environment** (no SDK). CI is the validator.
6. **Simulation is down-render only.** You can faithfully simulate *lesser* devices on richer
   hardware; you cannot synthesize frequency control a chip lacks.

---

## 10. Roadmap / next steps (highest-leverage first)

1. **Bump `compileSdk` to 36 and implement the real PWLE render** in `AndroidHaptics`
   (`VibrationEffect.WaveformEnvelopeBuilder`, gated by `areEnvelopeEffectsSupported()`), and set
   `probeProfile().hasEnvelopeControl` accordingly. Verify AGP 8.7.3 supports SDK 36 or bump AGP.
   This activates genuine "HD" haptics — the biggest felt upgrade.
2. **Remote pattern registry.** `RegistryIndex` is already plain JSON; host `starter-pack.json`
   in-repo (or a CDN), fetch it in the app, and let the Gallery load community entries.
   Add a CI step to publish the registry JSON as a release asset.
3. **Grow the device DB from real reports.** Wire the "Capture device capability report" output
   to a GitHub issue template / simple endpoint; merge submitted `DeviceProfile`s into the seed.
4. **On-device feel calibration.** Test texture roughness, navigator grading, and material
   ring-downs on real LRA hardware; tune `HapticMapping` (sharpness→frequency, intensity→amp)
   against measured output.
5. **F-Droid / Play internal-testing distribution** beyond the debug-APK release.
6. ~~"Skip intro after first run" preference for the splash~~ — done (`SplashPreferences`,
   `ui/.../splash/SplashPreferences.kt`): shows for the first 3 launches, then skips by default.
7. Optionally, **import more formats** (Interhaptics `.haps`, MPEG-I `.hjif`).
8. **UX Build Brief Phases 5–7** — see §0 above and `STATE.md`'s Next steps.

---

## 11. Research references (verified, primary sources)

- Apple Core Haptics + AHAP: https://developer.apple.com/documentation/corehaptics and
  https://developer.apple.com/documentation/corehaptics/representing-haptic-patterns-in-ahap-files
- Android `VibrationEffect.Composition` (primitives API 30/31): https://developer.android.com/reference/android/os/VibrationEffect.Composition
- Android custom haptics + fallback strategy (no auto-fallback!): https://developer.android.com/develop/ui/views/haptics/custom-haptic-effects
- Android `Vibrator` introspection (`getResonantFrequency`/`getQFactor` API 34; envelope API 36): https://developer.android.com/reference/android/os/Vibrator
- Interhaptics/Razer (free SDK, `.haps`, Wyvrn): https://doc.wyvrn.com/docs/interhaptics-sdk/
- Lofelt **Nice Vibrations** (acquired by **Meta**; MIT; archived Aug 2025; `.haptic` format —
  parametric amplitude+frequency envelopes): https://github.com/Lofelt/NiceVibrations
- MPEG-I Haptics ISO/IEC 23090-31:2025 (`.hjif` JSON) + reference software:
  https://github.com/MPEGGroup/HapticReferenceSoftware
- **Correction to an earlier note:** Lofelt was acquired by **Meta, not Apple**; its format is
  `.haptic`, not `.nice`.

The load-bearing insight from the research: portable haptics = **author once → store a
hardware-agnostic *parametric* IR (amplitude + frequency envelopes) → a runtime engine
re-synthesizes per actuator**, degrading LRA/voice-coil down to coarse ERM. **This project's IR
+ capability-driven `scheduleHaptics` is already that architecture** — the `PlayEnvelope` path
is the parametric layer; the remaining work is the Android API-36 render.

---

## 12. CI/CD, versioning, git workflow

**Workflows** (`.github/workflows/`):
- `ci.yml` — JVM build + tests (core, ui, desktopApp). Runs on push.
- `android.yml` — sets `ENABLE_ANDROID=1`, `assembleDebug`, stamps the APK filename with
  versionName + short SHA, uploads it as an artifact, and publishes to the **rolling release
  tag `android-player-debug`** (a fresh version-stamped URL each build so it's never
  cache-served). Triggered by pushes touching `androidApp/**`, `core/**`, `ui/**`, `gradle/**`,
  `*.gradle.kts`, or the workflow itself.

**Versioning:** bump `versionCode` + `versionName` in `androidApp/build.gradle.kts` **and** the
`build vX.Y` string in `MainActivity.diagnostics()` together on every user-facing change, so the
installed app self-identifies.

**Git workflow (important):** develop on `claude/haptics-audio-workbench-lgmpsf`; push with
`git push -u origin <branch>`; open a **draft PR** if none is open. **PR #1 has already been
merged** — so treat any follow-up as a *fresh* change: restart the branch from the latest
default (`git fetch origin main && git checkout -B claude/haptics-audio-workbench-lgmpsf
origin/main`), apply the new work, push, and open a **new** PR. Never stack new commits on the
already-merged history. End commit messages with the `Co-Authored-By` / `Claude-Session`
trailers used throughout this repo. Do **not** put the model identifier in any committed artifact.

---

## 13. Key files (quick map)

| Concern | File |
|---------|------|
| IR / data model | `core/.../ir/Ir.kt`, `ir/Serialization.kt` |
| Renderer (3 seams) | `core/.../dsp/DefaultPatternRenderer.kt` |
| Commands / capabilities | `core/.../playback/Backends.kt` |
| IR→backend mappings | `core/.../dsp/HapticMapping.kt` |
| AHAP export / import | `core/.../export/AhapExporter.kt` / `AhapImporter.kt` |
| Kotlin/WAV export | `core/.../export/KotlinVibrationEffectExporter.kt`, `WavExporter.kt` |
| Device DB | `core/.../device/DeviceProfile.kt`, `DeviceDatabase.kt` |
| Pattern registry | `core/.../library/PatternRegistry.kt`, `PatternLibrary.kt`, `BuiltInPatterns.kt` |
| AI assistant | `core/.../design/PatternGenerator.kt` |
| Design vocabulary | `core/.../design/{MotionPrimitives,TextureField,ModalSynth,ParameterNavigator,RhythmCapture,Variations}.kt` |
| Splash (core) | `core/.../design/SplashScene.kt`, `SplashGeometry.kt` |
| Editor state | `ui/.../model/EditorState.kt` |
| App shell / splash wire | `ui/.../WorkbenchApp.kt` (`WorkbenchApp` + `WorkbenchWithSplash`) |
| Theme / widgets | `ui/.../theme/Theme.kt`, `ui/.../components/RecorderWidgets.kt`, `SplashScreen.kt` |
| Device simulator UI | `ui/.../components/CapabilityPanel.kt` |
| AHAP import UI | `ui/.../components/ImportPanel.kt` |
| Desktop entry | `ui/src/jvmMain/.../Main.kt` |
| Android launcher | `androidApp/.../WorkbenchActivity.kt` |
| Android gallery/player | `androidApp/.../MainActivity.kt` |
| Android haptics bridge | `androidApp/.../AndroidHaptics.kt`, `AndroidPatternPlayer.kt`, `AudioPlayer.kt` |
| Android splash | `androidApp/.../SplashView.kt` |
| Tests (core) | `core/src/commonTest/.../*Test.kt` (15 files) |
| Tests (render) | `ui/src/jvmTest/.../PreviewRenderTest.kt` |
| CI | `.github/workflows/ci.yml`, `android.yml` |
| Deeper docs | `docs/ANDROID.md`, `docs/AUTHORING-INTERFACES.md`, `docs/MODULES.md`, `README.md` |

---

*Handoff generated at v0.15.0. When you make the next change, bump the version, keep the
`build vX` diagnostics string in sync, and update this file's "current state" + roadmap.*
