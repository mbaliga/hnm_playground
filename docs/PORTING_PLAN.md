# Haptics + Audio Workbench — multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06;
> "master §n" below is that file's section n, and ids such as R3, I-1, F7 and OQ-12 are its own).
> Status: **PLAN — nothing in this document has been built.** Every claim about a target platform is
> labelled with its evidence class (§0). This file is owned by the lead planning session; a platform
> track updates only its own §4 row and appends dated entries under a `Porting progress` heading in
> this repo's state file, `STATE.md` (proposed in §8, Proposal 4; created by the first track that has
> real command output).
> Written 2026-10-06 against commit `30ac7df` on `claude/multi-platform-porting-plan-nnvjug`. Nothing
> was compiled, run or installed to write it.

## 0. Evidence labels (never dropped)

The program's set (`Personal-Tracker/PORTING_PROGRAM.md` §2), verbatim: `LAB` · `CI (hosted VM) evidence` ·
`EMULATOR EVIDENCE` · `SIMULATOR` · `CI-APPROX — NOT DEVICE EVIDENCE` · `SIMULATED — NOT DEVICE EVIDENCE` ·
`VIRTUALIZED — NOT DEVICE EVIDENCE` · `SYNTHETIC` · `CI-ONLY / NOT RUN` · `NEEDS-DEVICE-VALIDATION` (NDV) ·
`NEEDS-OWNER-VALIDATION` (NOV) · `PLAN` · `NOT-APPLICABLE (<reason>)` · `CONTAINER-BUILD-ONLY` ·
`BROWSER-HEADLESS`.

This repo's own rule (`STATE.md`, "Owner-verified") is that on-actuator feel is owner-verified only. So here:
haptic feel on any actuator or controller is NOV; audio output, window behaviour, installers and containers
are NDV until run on the device; a simulator or hosted runner never upgrades either.

## 1. What this repo is, in porting terms

- **Product.** A tool for designing, feeling and exporting haptic and sound effects around one
  backend-agnostic IR, `HapticAudioPattern` (shaped like Apple Core Haptics: timeline events with intensity,
  sharpness and breakpoint curves). Swappable backends render it; it exports to native JSON, Kotlin
  `VibrationEffect`, AHAP and WAV (`README.md`, `core/src/commonMain/kotlin/dev/hnm/workbench/core/ir/Ir.kt`).
- **State** (`STATE.md`, `README.md` line 3). v0.14.0, `state: active`, public, JVM CI reported green, four
  design stages done. Next steps are M6 controller backends and M7 device variance, both hardware-bound.
  `androidApp` is versionName 0.14.0 / versionCode 15; `android.yml` publishes a rolling debug-APK
  pre-release (sideload only, not a store).
- **Current targets.** (a) Android arm64 phone, minSdk 31, `androidApp`, behind `ENABLE_ANDROID=1`. (b) Linux
  JVM editor window, `./gradlew :ui:run`: no playback, no file dialogs. (c) JVM CLI driver
  `./gradlew :desktopApp:run [--play]`. (d) Headless render: `:ui:jvmTest` paints the editor to
  `ui/build/preview/workbench.png`. No build file declares an iOS, macOS, Windows, wasm or native target and
  no document plans one as a build target, although `docs/AUTHORING-INTERFACES.md` (Key Finding 2) says the
  IR "can target both" Android and Core Haptics. The Apple artefacts today are Core-Haptics-shaped data:
  `AhapExporter`, `AhapImporter` and the `apple-iphone-15` row in `core/.../device/DeviceDatabase.kt`.
- **Stack.** Kotlin 2.1.21 (KMP, JVM, Android); Compose Multiplatform 1.8.2 with Material3; Gradle 8.14.3
  wrapper; AGP 8.7.3, applied only when `ENABLE_ANDROID=1` or `local.properties` has `sdk.dir`;
  kotlinx.serialization 1.8.1 (the native save format); kotlinx.coroutines 1.10.2; `javax.sound.sampled` in
  `desktopApp`; Android `Vibrator`/`AudioTrack` in `androidApp`. `jvmTarget` 17; `ci.yml` runs JDK 21,
  `android.yml` JDK 17. Native dependencies: none today (M6's SDL and HID libraries are planned, not wired).
- **Size** (measured 2026-10-06, `git ls-files | grep -E '\.kt$'`): 80 Kotlin files, 64 main and 16 test;
  10,243 lines in all, 8,594 main; 106 `@Test` (core 98, ui 8). Per-module counts are in §2; the
  commands are listed in §9.
- **Doc drift to know about.** `README.md`'s table, `docs/MODULES.md`, `docs/ANDROID.md` and the trailing
  comment in `ci.yml` still call the Android backend "reference only"; `androidApp/` ships 1,168 lines of it
  and `android.yml` builds it. This plan treats code and workflows as the truth and the docs as intent.
  Reconciling them is a separate hygiene change (§8 Q15); this PR touches neither.

## 2. Portable core vs platform-bound layers

| Module / dir | Role | Portability | Approx LOC | Notes |
|---|---|---|---|---|
| `core/src/commonMain` (`:core`) | IR, DSP, `PatternRenderer`, playback seam (`AudioBackend`, `HapticBackend`, `PatternPlayer`, `FloatStream`, `TransportClock`), exporters/importers, design stages, device DB, library | kmp-common | 4,137 (34 files) | No `android.*`, `java.*` or `javax.*` import (grep re-run 2026-10-06); no expect/actual; platform-ish API is only `kotlin.time.TimeSource.Monotonic` and `kotlin.math`. Tests: 98 `@Test`, 15 files, 1,469 LOC, in `commonTest`. Declared targets: `jvm()` plus conditional `androidTarget()`. That more targets compile is by import scan, not by compiling. |
| `ui/src/commonMain` (`:ui`) | Compose MP editor: `WorkbenchApp` (narrow below 720 dp, else wide, in a "recorder2" device shell), 17 components, `EditorState`, own `WorkbenchTheme` | kmp-common, one JVM leak | 3,147 (20 files) | All `androidx.*` imports are Compose MP namespaces. Leak: `components/RhythmCapturePanel.kt:82` calls `System.currentTimeMillis()`; it compiles only because today's targets are jvm and android. Export is on-screen text, import is paste into a text field: no file I/O, no clipboard. |
| `ui/src/jvmMain`, `ui/src/jvmTest` | Desktop window (`Main.kt`); headless `PreviewRenderTest` (8 tests) | jvm-only | 24 + 180 | `EditorState()` keeps `PatternPlayer.None`. `compose.desktop.currentOs` resolves the host's Skiko. `nativeDistributions`: `Deb`, `AppImage`, `packageName = "HapticsAudioWorkbench"`, `packageVersion = "1.0.0"`. |
| `desktopApp` | CLI driver; `JvmAudioBackend` (16-bit mono PCM pull loop, daemon thread) | pure-kotlin-jvm | 118 | Depends on `:core` only, so `:ui` cannot reach `JvmAudioBackend`. Runs on any JVM. |
| `androidApp` | Compose host plus the real backends | android-bound | 1,168 (7 files) | The pattern each new backend follows: `AndroidHaptics` 327, `AndroidPatternPlayer` 34, `AudioPlayer` 55, `WorkbenchActivity` 62. The Views gallery and splash (`MainActivity` 454, `SplashView` 194, `GrilleView` 42) stay Android-only; Compose equivalents exist in `ui`. |
| `:backend-desktop`, `:backend-android` | Named in `docs/MODULES.md` "Intended layout" | planned | 0 | Not in `settings.gradle.kts`. |

Platform-bound APIs that matter:

| API | Where | Porting impact |
|---|---|---|
| `Vibrator`, `VibrationEffect`, `VibrationAttributes` | `androidApp/.../AndroidHaptics.kt` | Per-platform by design. iOS reimplements against Core Haptics (the IR is Core-Haptics-shaped); desktops have no actuator; Ubuntu Touch has only QtFeedback. |
| `AudioTrack` (static mode) | `androidApp/.../AudioPlayer.kt` | One `AudioBackend` per platform, roughly 50 to 80 lines each: `javax.sound` exists, iOS `AVAudioEngine`, web Web Audio. |
| `ComponentActivity`, `Intent`, `ClipboardManager`, `Toast` | `androidApp/.../WorkbenchActivity.kt`, `MainActivity.kt` | Host shell only; none is in `commonMain`, so no refactor is forced. |
| `javax.sound.sampled` | `desktopApp/.../JvmAudioBackend.kt` | Same on Linux, macOS and Windows JVMs; unavailable on iOS, wasm, native. Must move where `:ui` can reach it. |
| `System.currentTimeMillis()` in `commonMain` | `RhythmCapturePanel.kt:82` | Compile error on every non-JVM target; `EditorState.startOrTap` only uses differences, so any monotonic millisecond counter is sufficient. |
| `compose.desktop.currentOs`, `ImageComposeScene`, `nativeDistributions` | `ui/build.gradle.kts`, `PreviewRenderTest.kt` | Per-OS packages must be built on that OS's runner; `Dmg`, `Msi` are not in the format list. |
| GitHub Actions, ubuntu-latest only | `.github/workflows/*.yml` | No macOS, Windows or iOS runner and no packaging job today. |

## 3. Binding rules this port must not break

- **Android is opt-in by an explicit signal**, never by sniffing `ANDROID_HOME`; the default `./gradlew build`
  stays JVM-only and green in SDK-less images; any new target follows the same pattern (`README.md` "Do not
  touch" 1; comments in `settings.gradle.kts`, `core/build.gradle.kts`, `ui/build.gradle.kts`).
- **One IR is the spine.** Ports add backends behind `HapticBackend`, `AudioBackend` and `PatternPlayer`;
  they do not fork the IR or re-implement render/degrade logic (`README.md` "Do not touch" 2; `STATE.md`).
- **`core` has zero platform dependencies** (`docs/MODULES.md`, "Intended layout"). Platform code lives in
  `backend-*` or app modules.
- **The critical seam is kept**: the continuous path (`FloatStream`) and the discrete path (`HapticCommand`)
  both survive, on one `TransportClock` with per-backend latency compensation (`docs/MODULES.md`).
- **On-actuator feel is owner-verified only** (`STATE.md`). No port claims feel from CI or a simulator.
- **Deterministic, on-device synthesis; AI only navigates a parameter space; cloud is an optional upgrade,
  never a hidden default** (`docs/AUTHORING-INTERFACES.md`; `core/.../design/PatternGenerator.kt`, where
  `HybridPatternGenerator(cloud = null)` is unwired).
- **No network, no telemetry.** Source has none (re-run: `grep -rniE 'http|socket|okhttp|ktor|java\.net'
  core ui androidApp desktopApp` matches only the Android XML namespace; the manifest holds `VIBRATE` only).
  The repo states no telemetry rule of its own; the constellation rule applies by owner convention (I-1).
- **Native save format is kotlinx.serialization JSON** with a `"type"` discriminator
  (`core/.../ir/Serialization.kt`); every port reads and writes the same files (I-5).
- **CI storage is scarce.** `cleanup-artifacts.yml` exists because Actions storage was exhausted twice
  (commits `8af91f8`, `30ac7df`). Two existing behaviours matter: `ci.yml` uploads `editor-preview` on every
  run (push to any branch, and pull requests), and `android.yml` (paths `core/**`, `ui/**`, `gradle/**`,
  `*.gradle.kts`, `settings.gradle.kts`) re-publishes the rolling pre-release on every push to `main` that
  touches them. Port work lands in those paths, so each merge re-runs it. Existing workflow files are not
  edited (R3).
- **Licence is unknown**: no `LICENSE`, no SPDX headers; the README says "public". The existing rolling APK
  pre-release is untouched; this plan adds no published Release, store listing or package-manager entry
  until the owner rules (OQ-12).
- **Design.** The repo has its own "recorder2" `WorkbenchTheme` (red `#E22C24` is the record dot, playhead
  and active accent in `ui/.../theme/Theme.kt`; no green is used) and does not use Hyle. The
  red-green rule is not stated here; whether it binds this repo is OQ-26. Whatever the answer, states this
  port adds (capability banner, "audio only", "no haptics in this build") are text plus shape, never red or
  green alone (I-3).
- **Conventions.** `kotlin.code.style=official`; flat UPPERCASE `docs/` names; one-line pointers go in
  `STATE.md` and `README.md` ("Architecture & roadmap").

## 4. Target matrix (owner's order)

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | hard | No JVM or Compose target exists for Lomiri, so the editor cannot run natively. Preferred if proven: a `wasmJs` head of `:core` and `:ui`, a Web Audio `AudioBackend`, no haptic output, packaged as a webapp-container click. Fallback, a labelled reframe (R12): a "pattern player" click, QML over F7's headless JVM core, QtFeedback for coarse haptics. Bundled JVM + Compose Desktop (unconfined, XMir) is not pursued. | S-UT1 and the 24.04-2.x WasmGC engine spike (OQ-1); Compose Web is Beta from CMP 1.9.0 and the repo is on 1.8.2 (OQ-17); QtFeedback has no waveform API; audio latency on Halium unmeasured; licence (OQ-12). | 6 if the wasm path holds (excludes the shared spike); fallback not estimated here | PLAN |
| Linux desktop | native-fit | `:ui:run` and Deb/app-image config exist. Close three usability gaps: wire a player so Play produces audio (audio only), add file open/save and clipboard behind a `PlatformIo` seam (actuals in `jvmMain`), add a packaging workflow (Deb, app-image tarball, Flatpak manifest). Separate: M6 controller haptics. | The window has no player (`PatternPlayer.None`) and no file I/O; no packaging job; licence (OQ-12) before any release; arm64 and Deck/RedMagic runs are NDV. | 2; M6 would add 3 to 4 (a pre-plan estimate) | PLAN |
| iOS / iPadOS | straight | CMP iOS in a thin SwiftUI shell, `iosArm64` + `iosSimulatorArm64` behind `ENABLE_IOS=1`; a Core Haptics `PatternPlayer` fed from `AhapExporter` output, an `AVAudioEngine` `AudioBackend`, document-picker file I/O. iPad uses the existing wide layout. | One-line `RhythmCapturePanel` fix; a Kotlin/Compose/Xcode set that builds (OQ-17); Apple Program and delivery route (OQ-2, OQ-3); macOS runner (OQ-20); simulator has no Taptic Engine; the owner's only Apple device on record is an iPad, which I expect has no haptic hardware (unverified); licence. | 5 | PLAN |
| macOS | native-fit | The Linux JVM build plus `TargetFormat.Dmg`, packaged on `macos-latest`; audio preview and export only. Not Compose's native macOS target. | Linux gaps first; macOS runner; Developer ID and notarisation (OQ-3); no Mac on record, so device gates stay NOV (OQ-5). | 1.5 | PLAN |
| Windows | native-fit | The same JVM build plus `TargetFormat.Msi` on `windows-2025`; audio preview and export only (haptics only through M6 controllers). | Linux gaps first; signing route (OQ-3); WiX on the runner image; `javax.sound` latency on Windows NDV; the Dell is the only Windows device and its fate is OQ-5. | 1.5 | PLAN |

Existing evidence, for context only: `ci.yml` runs `:core:jvmTest :ui:jvmTest` on ubuntu-latest (`CI (hosted VM)`)
and `STATE.md` reports it green; this plan did not re-run it. Effort figures are estimates, one engineer who
knows the stack, excluding shared F-items, spikes, owner-device sessions and review time; the five figures
equal the master §5 cells.

What "feel" means per target (every line is a plan):

| Target | Audio backend | Haptic output | `HapticCapabilities` to report | Feel evidence |
|---|---|---|---|---|
| Ubuntu Touch | Web Audio (wasm) or QtMultimedia (fallback) | none in the web build; QtFeedback `HapticsEffect` in the fallback | `NONE`; at best `ERM_BASIC` (unknown whether QtFeedback exposes intensity on a given phone) | NDV, NOV |
| Linux | `javax.sound` | none until M6 (SDL rumble, then DualSense HID) | `NONE`; then `CONTROLLER_RUMBLE` / `CONTROLLER_VOICECOIL` | audio NDV; controller NOV |
| iOS / iPadOS | `AVAudioEngine` | Core Haptics, probed with `CHHapticEngine.capabilitiesForHardware()` | iPhone: from the probe, matching the `apple-iphone-15` profile; iPad: expected `NONE` (unverified) | NOV on a physical iPhone; NDV on the iPad |
| macOS | `javax.sound` | none (trackpad canned patterns only; not pursued) | `NONE` | audio NOV (no Mac) |
| Windows | `javax.sound`; WASAPI via a native path only if latency proves too high | none until M6 (XInput or SDL) | `NONE`; then controller types | audio NDV; controller NOV |

## 5. Tier and sequencing

**Tier B (port in sequence)**, matching the master §5 row. The repo is among the most port-ready: `:core` and
`:ui` are KMP-clean, 106 JVM tests exist, a desktop window and CLI already run, Deb/app-image packaging is
half-configured, and the IR already speaks AHAP both ways. It is not A because the desktop editor is not yet a
usable app (no playback, no file I/O), the docs are stale against the shipped Android app, there is no
licence, the repo's own next milestones are hardware-bound, and it is a developer tool. If the owner weights
iOS haptic authoring highly, iOS alone could justify promotion (OQ-28; the master keeps B).

Gate before any wave (master §5): desktop usability gaps first; no `LICENSE` (OQ-12); toolchain pins (OQ-17).
Build order inside this repo is Linux gaps, then iOS, then the macOS and Windows packaging lanes, then Ubuntu
Touch. The owner's order names Ubuntu Touch first, but here it is last to build and first to need a device
(OQ-1), so its row waits on the shared spike.

| Platform | Program wave | Repo-local gate before the wave starts |
|---|---|---|
| Ubuntu Touch | P-UT a (Kotlin/Wasm click), **only after** the 24.04-2.x engine and WasmGC spike | CI green on `main`; F7's webapp template; a pin that gives Compose Web Beta (OQ-17); S-UT1 verdict or an explicit CI-only waiver (OQ-1) |
| Linux | P-LX, order slot "hnm desktop window", after nooz and csapp | CI green on `main`; §6.2 steps L1 and L2 merged before L3 packages anything; OQ-12 ruled before any Release is published |
| iOS / iPadOS | P-iOS, after Clavis's iOS simulator proof and nooz/csapp ("hnm (Core Haptics)") | pin ruled (OQ-17); the `RhythmCapturePanel` fix merged; the `ENABLE_IOS=1` gate proven inert on the default build; device builds also need OQ-2 and OQ-3 |
| macOS | P-mac, over P-LX binaries (lane already in scope for this repo) | Linux steps merged; hosted `macos-latest` budget (OQ-20); signing templates stay disabled until OQ-3 |
| Windows | P-win, over P-LX binaries (lane already in scope) | Linux steps merged; the path lint (§6.1) green before the lane exists; signing route or accepted-unsigned (OQ-3) |

## 6. Work breakdown

**Reading of the program rules for this repo.** R1: each track owns disjoint directories and source sets.
The repo's own `docs/MODULES.md` names `backend-*` modules at the root, so: the desktop track owns
`backend-desktop/` and `ui/src/jvmMain`; the Apple track owns `apple/` (Xcode project) and `backend-ios/`
(Gradle module); the UT track owns `web/` and `ubuntu-touch/`; packaging templates go under
`packaging/<os>/`. R2: "root build unchanged" means that with no `ENABLE_*` variable set, `./gradlew build`,
`:core:jvmTest` and `:ui:jvmTest` resolve and pass exactly as today. Every new target is behind an explicit
opt-in (mirroring `androidSdkAvailable()`) or is a JVM-only module; no step touches `ci.yml` or `android.yml`.
R3: new workflow files only, actions pinned by SHA. R4: pure core first: before any UI on a target, the 98
`commonTest` tests run on it. R6: every artefact is `UNSIGNED — not for release`; package lanes run on `main`
and tags only; tagged builds go to a draft GitHub Release, never to Actions artifact storage; signing and
store jobs exist only as disabled templates until OQ-3 and OQ-4. R11: no click name, bundle id, Flatpak id,
MSI upgrade GUID or winget id is written into a manifest until it has a `NAMES.md` row (OQ-25); simulator and
unsigned CI builds take a throwaway value from a build parameter. This container has no device, simulator,
Mac, Ubuntu Touch image, sound card or Clickable; each table says what it can and cannot check.

### 6.1 Cross-cutting steps (all targets; X1 is costed under iOS, X2 to X4 under Linux, and later waves inherit them)

| # | Step | Where | Done when | In the build container? |
|---|---|---|---|---|
| X1 | Replace `System.currentTimeMillis()` with a monotonic millisecond counter (`kotlin.time`) | `ui/src/commonMain/.../components/RhythmCapturePanel.kt:82` (shared source: reviewed against the Android gate) | no `import java.`, `javax.` or `android.` and no `System.` call in `core/src/commonMain` or `ui/src/commonMain` (comments excluded, so the KDoc at line 34 is reworded too); `:ui:jvmTest` and `android.yml` unchanged | JVM compile and tests: `CONTAINER-BUILD-ONLY` |
| X2 | New `port-guards.yml` (ubuntu-latest, PRs, no artifacts): the root-unchanged guard (a `./gradlew projects` check that no iOS, wasm or UT module appears without its `ENABLE_*` variable), the commonMain purity grep, and the Windows path lint | `.github/workflows/port-guards.yml` (new file) | green on a PR; the lint reports 0 violations today (107 tracked paths, no reserved names, no case collisions: measured by a throwaway script, 2026-10-06) | lint and grep yes; workflow `CI (hosted VM)` |
| X3 | Record the host actuator honestly: a ui-only, nullable `EditorState.hostCapabilities` (default `null`, meaning say nothing) so a desktop or iOS host can make the Play button say "audio only, no haptic actuator here"; `capabilities` keeps meaning "target profile" | `ui/src/commonMain/.../model/EditorState.kt` plus `CapabilityPanel` copy; no change to `core` | desktop Play shows the sentence as text; Android and the headless test render as today until the Android host opts in through its own PR | compile: yes; wording and layout NOV |
| X4 | A `PlatformIo` seam (open, save text and bytes, copy) with a `None` default, in the style of `PatternPlayer.None`; the Open, Save and Copy controls appear only when the host supplies one, so hosts without it (Android today, the headless test) render exactly as now and export stays on-screen text | `ui/src/commonMain` (new file plus wiring) | Android and the headless render test compile and behave as today | JVM compile: yes |

### 6.2 Linux desktop (2 weeks, estimate: X2 0.25, X3 and X4 0.5, L1 0.5, L2 0.25, L3 0.5)

| # | Step | Where | Root-build impact | Done when |
|---|---|---|---|---|
| L1 | Desktop audio and player: move `JvmAudioBackend` into a new pure-JVM module (the repo's documented `:backend-desktop`), add `JvmPatternPlayer`, set `player` and `hostCapabilities` in `ui/jvmMain/Main.kt` the way `WorkbenchActivity` does. If no audio line exists (headless CI, RedMagic-Edge), catch the failure, keep `canPlay` false and say so in the UI. | `backend-desktop/`, `desktopApp` (depends on it), `ui/src/jvmMain` | one additive `include(":backend-desktop")` in `settings.gradle.kts`, JVM-only, no gate; zero-root-change alternative is `ui/src/jvmMain` (Q16) | a jvmTest drives `JvmPatternPlayer` with a fake `AudioBackend` and asserts the rendered stream length; real sound on the Deck or the Dell is NDV |
| L2 | File and clipboard actuals for `PlatformIo` on AWT (`FileDialog`, system clipboard), no new dependency: open and save native JSON, save AHAP, Kotlin snippet and WAV. F6's picker replaces it when F6 exists. | `ui/src/jvmMain` | none | a jvmTest round-trips a pattern through `PlatformIo` over a temp directory; the dialogs on XWayland are NDV |
| L3 | Packaging workflow. x86_64 on `ubuntu-24.04`, arm64 on `ubuntu-24.04-arm`: `packageDeb` (fakeroot is on the runner image), the `AppImage` format (jpackage's app-image directory, not a `.AppImage` file; tar it with an `install.sh`, the primary path on RedMagic-Edge), a Flatpak manifest consuming that app-image (disabled template: needs a Flathub id, OQ-4 and OQ-25). Runs on `main` and tags; tags create a draft Release; icons and `.desktop` metadata do not exist in the repo and are not invented. | `.github/workflows/desktop-linux.yml`, `packaging/linux/` | none | green on `main` with `.deb` and tarball built (`CI (hosted VM)`); install and launch on Deck Desktop Mode, Gaming Mode, RedMagic under Termux:X11 are NDV via `DEVICE_CHECKLIST_LINUX.md` (F11) |
| L4 | M6 controller backends (SDL rumble first, DualSense over HID) in `backend-desktop`, consuming `renderHapticWaveform`. Outside the 2 weeks. Native libraries are built on the oldest glibc runner (`ubuntu-22.04`) and their licences recorded as added (I-11). | `backend-desktop/` | native toolchains only inside this module | rumble and voice-coil feel NOV; Q12 orders it |

Not verifiable here: `packageDeb` (no fakeroot), Flatpak, arm64, any audible output, Wayland, HiDPI.
`createDistributable` for Linux x86_64 would be `CONTAINER-BUILD-ONLY`. Skiko on software-GL Termux:X11 is
untested (a software render fallback may be needed; unverified). Whether jpackage's jlinked runtime keeps
`javax.sound` (module `java.desktop`) is expected but unverified.

### 6.3 macOS (1.5 weeks, estimate: M1 0.25, M2 1.0 with the disabled signing templates, checklist and docs 0.25)

| # | Step | Where | Done when |
|---|---|---|---|
| M1 | Add `TargetFormat.Dmg` to the same `targetFormats` list (Clavis precedent: one list with all formats); on Linux the task graph must gain no tasks (`./gradlew :ui:tasks --all` diffed in the PR) | `ui/build.gradle.kts` (additive) | the Linux and default builds are unchanged; no `bundleID` written (R11) |
| M2 | `desktop-macos.yml` on `macos-latest`: `:core:jvmTest :ui:jvmTest` first, then `packageDmg` unsigned on `main` and tags; draft Release on tags. Hardened-runtime entitlements (`allow-jit` as the target, to be confirmed by asom's S-M3 self-test, which needs a Mac), the `notarytool` script and a Homebrew cask are disabled templates until OQ-3 and OQ-4 | `.github/workflows/desktop-macos.yml`, `packaging/macos/` | DMG built on a hosted runner (`CI (hosted VM)`); Gatekeeper behaviour of the unsigned DMG, launch and audio are NOV: no Mac on record (OQ-5) |

Container: M1 can be checked on Linux by diffing `./gradlew :ui:tasks --all` (`CONTAINER-BUILD-ONLY`); M2 cannot
run here (no macOS, no `notarytool`). Haptics on macOS: none; the Play button is audio-only and says so (X3).
Mac Catalyst of the iOS app, or "Designed for iPad" on Apple silicon, is an option only after the iOS port
exists and is not planned here.

### 6.4 Windows (1.5 weeks, estimate: W1 0.25, W2 0.75, templates 0.5)

| # | Step | Where | Done when |
|---|---|---|---|
| W1 | Add `TargetFormat.Msi` (not `Exe`) to the format list; no `upgradeUuid` until it has a `NAMES.md` row (R11) | `ui/build.gradle.kts` (additive) | default and Linux builds unchanged |
| W2 | `desktop-windows.yml` on `windows-2025`, only after X2's path lint is green: `:core:jvmTest :ui:jvmTest` first (catches charset and CRLF traps; the repo's raw strings go through `trimIndent` or `trimMargin`, which normalise line endings, so CRLF checkouts are not expected to matter: read from source, unrun), then `packageMsi` unsigned on `main` and tags; draft Release on tags. WiX presence is taken from the program's record and confirmed on the first run, otherwise `BLOCKED(<reason>)` | `.github/workflows/desktop-windows.yml`, `packaging/windows/` | MSI built on a hosted runner; install, launch and audio latency are NDV on the Dell only while it is still Windows (OQ-5) |
| W3 | winget manifest and Authenticode/MSIX re-signing as disabled templates (Azure Artifact Signing is unavailable to the owner as an individual in India; routes in OQ-3) | `packaging/windows/` | templates only; nothing signed or submitted |

Container: W1 is checkable by the same `:ui:tasks` diff on Linux; W2 and W3 cannot run here (no Windows, no WiX).
Play is audio-only on Windows and says so (X3); haptics arrive only with M6 controllers (L4).

### 6.5 iOS / iPadOS (5 weeks, estimate: X1 0.1, I1 0.5, I2 0.75, I3 1.5, I4 0.5, I5 and I6 1.25, checklist 0.4)

| # | Step | Where | Root-build impact | Done when |
|---|---|---|---|---|
| I0 | Preconditions: X1 merged; OQ-17 has fixed a Kotlin, Compose MP and Xcode set (the program's platform brief says Kotlin 2.2.20 or later is recommended for CMP 1.12.1 on iOS; whether Kotlin 2.1.21 builds against Xcode 26 is unknown); Clavis's simulator build has proven the CMP-iOS recipe (F9, F10) | none | none | pin recorded in the PR; no code |
| I1 | `val iosEnabled = System.getenv("ENABLE_IOS") == "1"`; when true, declare `iosArm64()` and `iosSimulatorArm64()` in `core` and `ui` | `core/build.gradle.kts`, `ui/build.gradle.kts` | additive, gated, same shape as the Android gate | with the variable unset, X2's guard shows an identical project list; set, on `macos-latest`, `:core:iosSimulatorArm64Test` runs the 98 tests (`SIMULATOR`) before any UI work (R4) |
| I2 | Gated Gradle module `:backend-ios` with `MainViewController()` hosting `WorkbenchWithSplash`; the splash seed uses `kotlin.time`; static framework `WorkbenchKit` | `backend-ios/` | gated `include` in `settings.gradle.kts`, mirroring `androidSdkAvailable()` | framework links for `iosSimulatorArm64` and `iosArm64` (`CI (hosted VM)`) |
| I3 | Backends in `backend-ios/src/iosMain`: `CoreHapticsPlayer` (a `PatternPlayer`: `AhapExporter.export` JSON through `NSJSONSerialization` into `CHHapticPattern(dictionary:)`, `CHHapticEngine` start, play, and rebuild on its `stoppedHandler` and `resetHandler`); capability probe mapped to `HapticCapabilities`; `AvAudioEnginePlayer` (an `AudioBackend`: patterns are short offline renders, so `readAll()` into an `AVAudioPCMBuffer` on an `AVAudioPlayerNode` is enough). If K/N bindings for Core Haptics prove awkward, the same interface is implemented in Swift in `apple/` and injected at launch: decide here with compile evidence. AHAP decomposes IR primitives (`AhapExporter.decomposedPrimitiveCount`); keep the existing UI note. Latency compensation values are uncalibrated until the owner measures them. | `backend-ios/src/iosMain` | none | compiles for both targets; the simulator runs the audio path and shows `NONE` haptics; feel on an iPhone is NOV |
| I4 | `PlatformIo` actuals: `UIDocumentPickerViewController` (open, save), the share sheet (export), `UIPasteboard` | `backend-ios/src/iosMain` | none | compiles; picker behaviour NDV |
| I5 | Xcode head generated by XcodeGen: SwiftUI `@main` shell, `Info.plist`, `PrivacyInfo.xcprivacy` (no tracking, no collected data: the source has no network code), launch screen; bundle id passed as a build setting (R11). Safe-area handling is unverified: the root uses a fixed 16 dp padding | `apple/` | none | `xcodebuild` simulator build with `CODE_SIGNING_ALLOWED=NO` succeeds |
| I6 | `ios.yml` on `macos-latest`: simulator build, `iosSimulatorArm64Test`, the three Apple lints (F10); unsigned `.xcarchive` on `main` and tags only, not uploaded as an Actions artifact; TestFlight and ad-hoc delivery are disabled templates until OQ-2 and OQ-3 | `.github/workflows/ios.yml` | none | green on a hosted runner (`CI (hosted VM)`, `SIMULATOR`); an entry in `DEVICE_CHECKLIST_IOS.md` (F11) lists the NDV items; device install NDV |

Container: nothing in I1 to I6 can be built here (no Xcode, no Swift, no Apple SDK); only the default-build side
of I1 (the variable unset) is checkable here, by the X2 guard. iPadOS first: the iPad Pro M4 is the only Apple
device on record (OQ-2), so iPad checks cover layout (the 720 dp switch gives the wide layout; Split View widths
will give the narrow one), audio and file I/O. I expect no haptic hardware on iPad (unverified), so Taptic feel
stays NDV and NOV until an iPhone exists.

### 6.6 Ubuntu Touch (6 weeks if the wasm path holds, estimate)

| # | Step | Where | Done when |
|---|---|---|---|
| U0 | Consume the shared spike verdict (S-UT1 plus "does the 24.04-2.x webapp-container run WasmGC", needs a device: OQ-1). No wasm code before it. Outcomes: yes, then U1 to U6; no, then U7, Waydroid (OQ-21) or "export on desktop, play on the phone" (Q5) | none | verdict recorded in `STATE.md` with real output (R7) |
| U1 | `ENABLE_WASM=1` gate adds `wasmJs { browser() }` to `core` and `ui` (about 1.0 week); core's 98 tests on wasm first (R4) | `core/build.gradle.kts`, `ui/build.gradle.kts` | default build unchanged (X2); wasm tests pass in `BROWSER-HEADLESS` or Node |
| U2 | `web/` Gradle module: wasm entry hosting `WorkbenchWithSplash`, `index.html`; needs CMP 1.9.0 or later (about 1.25 weeks) | `web/` | bundle builds on ubuntu-latest |
| U3 | Web Audio `AudioBackend`: render with `readAll()`, play an `AudioBuffer` from the Play click (autoplay needs a gesture) (about 0.75 week) | `web/src/wasmJsMain` | compiles; sound and latency NDV |
| U4 | Web `PlatformIo`: file input and Blob download; clipboard if the context allows it. Whether the container handles downloads is unknown; if not, export stays on-screen text (about 0.5 week) | `web/src/wasmJsMain` | compiles; behaviour NDV |
| U5 | Haptics: none. Capability `NONE` and a visible "no haptic output in this build" (about 0.25 week). Nothing is faked | `web/` | text present |
| U6 | Click packaging from F7's webapp template (policy groups per F7, common only), `ubuntu-touch.yml` on ubuntu-latest in the digest-pinned Clickable image; package name from a build parameter (R11) (about 1.5 weeks); 0.75 week evidence and contingency | `ubuntu-touch/`, `.github/workflows/ubuntu-touch.yml` | click builds (`CI (hosted VM)`); install and run NDV |
| U7 | Fallback reframe, only if U0 says no: a "pattern player" click, QML over F7's headless JVM core consuming `:core`'s jvm artefact, content-hub import of JSON and AHAP, QtFeedback for coarse haptics. Not the Workbench (R12). Not estimated: the pre-plan figure of roughly 3 weeks assumed a C++ re-port of schedule and degrade logic, which this plan rejects because it forks `core` logic against the repo's own rule | `ubuntu-touch/` | needs S-UT1 and a ruling (Q5) |

Container: U1 to U3 can produce a wasm bundle on ubuntu-latest, and `BROWSER-HEADLESS` tests need a Chrome binary
this container may lack; U6 needs Docker and Clickable, which it lacks; U0 and every device claim need the owner.

Waydroid running the unmodified Android APK is owner-device evidence only and is never counted as this port
(OQ-21); whether it exposes any vibrator to the app is unknown.

Not planned: a bundled JVM running Compose Desktop (needs the `unconfined` template, manual review, open source
only, and no licence exists). Not planned without a ruling: a hosted static build for a Morph bookmark (a new
public surface), and any 127.0.0.1 server (OQ-15; I-1).

## 7. Shared foundation this repo consumes or provides

**Consumes** (home and gate in `Personal-Tracker/PORTING_PROGRAM.md` §6):

| Item | Use here |
|---|---|
| F9 CI matrix template | The per-target workflows above (`desktop-linux`, `desktop-macos`, `desktop-windows`, `ios`, `ubuntu-touch`) start as plain jobs; each becomes a SHA-pinned `uses:` caller when F9 exists (I-6), never a copied template. The R3 path lint comes from F9 when available; X2 is the interim. |
| F10 packaging templates | All five targets: jpackage and Flatpak (Linux), WiX and winget, entitlements and notarisation, XcodeGen and the Apple lints, the clickable set. Typewright's Linux pilot and Clavis's iOS simulator proof come first. |
| F7 ubuntu-touch-shell | Webapp template, S-UT1 verdict, `DEVICE_CHECKLIST_UT.md`, the headless-JVM bridge for U7. |
| F11 evidence and checklists | `DEVICE_CHECKLIST_{UT,LINUX,IOS,MACOS,WINDOWS}.md`; the README gets an Evidence table later. |
| F6 platform-ports | Its file picker and share sheet can replace `PlatformIo` actuals. Secure-key storage: NOT-APPLICABLE (this repo holds no keys; OQ-22 matters only if the cloud seam is ever wired). |
| F5 kmp-conventions | Optional later: shared version catalogue at OQ-17's pin; `ENABLE_IOS` and `ENABLE_WASM` become `asocTargets`. Until then the gates are written inline, mirroring the Android ones. Adoption must not break R2. |

Not consumed: F1 (own theme; OQ-26, OQ-29), F2 (no crash-recovery wiring exists on this repo's main: `grep -rni crash`
finds nothing), F3, F4 (the unwired `cloud` seam is a natural `InferenceClient` consumer, but F4 is unusable
off-Android until asom rules D25(b), OQ-7d, so the seam stays null on every port), F8 (the only native
dependencies are the M6 controller libraries, which only this repo needs).

**Provides.** (1) `androidSdkAvailable()` and the explicit-opt-in gating style are the seed for F5's conditional
inclusion. (2) BOS_launcher's profile proposes copying this repo's `ui/build.gradle.kts`; this plan recommends
BOS consume F5 instead, and notes that edits here have a downstream reader. (3) The second CMP-iOS data point
after Clavis, and the Core Haptics actuals. No other repo's 2026-10-06 profile lists a dependency on this one.

**Discrepancy for the program lead.** Master OQ-17 lists hnm_playground among Hyle `includeBuild` consumers bound
by the PT:D-Q AGP lockstep. `settings.gradle.kts` has no `includeBuild` and nothing references Hyle (grep:
no `hyle`, `includeBuild` or `dev.aarso`); AGP is pinned locally at 8.7.3. OQ-17 reaches this repo only through
the Kotlin and Compose versions iOS and Compose Web need, and through F5's catalogue if adopted. Separately, an
AGP 9.x move would need a check of this repo's Android gate (`pluginManager.apply("com.android.library")`,
`androidTarget`, the `kotlinAndroid` plugin); that is unsized and unverified.

Master OQ-26 and OQ-29 do not name hnm_playground among the non-Hyle repos whose I-3 scope and Hyle-consumer
status are open; Q11 is routed to them as the nearest ids and asks the lead to add this repo to both lists.

## 8. Open questions for the owner

Deduplicated from the pre-plan repo profile; "Master OQ" is the program's id where one exists. Defaults are
what this plan does if unanswered; none of them ships anything.

| # | Question | Master OQ | Blocks | Default |
|---|---|---|---|---|
| Q1 | Licence: none tracked, README says "public". Apache-2.0 throughout, or Typewright's split (Apache engine modules, FSL app)? | OQ-12 | every published binary, Flathub, App Store, OpenStore, winget, a public Release | build and test only; draft Releases stay drafts |
| Q2 | Is iOS the primary port for this product (promote to tier A for this repo), or does desktop packaging go first? | OQ-28 | the order of §6.5 against §6.2 | desktop gaps first, then iOS |
| Q3 | Toolchain pin: staged (Kotlin 2.1.x, CMP 1.8.2: iOS stable, web below Beta) or the Option A set (Kotlin 2.4.20, CMP 1.12.1: web Beta, the iOS recommendation)? Confirm hnm is not a Hyle lockstep consumer (§7). | OQ-17 | I0, U1, U2, any F5 adoption | iOS planned on whatever builds; wasm waits |
| Q4 | A UT device and the engine spike (QtWebEngine or Morph, WasmGC); Waydroid acceptable for the Android APK? | OQ-1, OQ-21 | all of §6.6 | UT stays `CI (hosted VM)` or `CI-APPROX` |
| Q5 | UT shape if WasmGC fails or no device: pattern-player reframe (U7), "export on desktop, play on the phone", Waydroid, or skip? | none (hnm-local) | U7 | no UT build before U0 |
| Q6 | Apple: Developer Program, delivery route to the iPad without a Mac, and acceptance of TestFlight's automatic crash-report egress (disclosed). An iPhone for feel validation? | OQ-2 | device builds, iPhone feel | simulator only |
| Q7 | Channels and signing custody: notarised DMG vs App Store; winget vs MSIX; Flathub or a tarball only; where certificates live. | OQ-3, OQ-4 | disabled templates in M2, W3, L3, I6 | unsigned, templates disabled |
| Q8 | Hardware stance: is there a Mac; does the Dell stay Windows long enough to be the Windows gate; is the Steam Deck an acceptable place to run device checklists? | OQ-5 | macOS and Windows device gates | NOV and NDV |
| Q9 | CI cost: the repo is public, so five-OS matrices are free; may new lanes follow R6 (no artifacts, draft Releases) while `ci.yml` (screenshot upload) and `android.yml` (artifact, rolling pre-release, re-run by port merges) stay as they are, or may a later PR add path filters? | OQ-20 | lane triggers in X2, L3, M2, W2, I6 | existing workflows untouched |
| Q10 | Identifiers and versions: `dev.hnm.workbench.*` for bundle, Flatpak, MSI and click ids, or the studio's naming? Three version numbers disagree (root `0.1.0`, app `0.14.0`, `packageVersion` `1.0.0`): which is the version of record for installers? | OQ-25 | M1, W1, L3 manifests, I5 | no ids written; throwaway build values |
| Q11 | Design: keep "recorder2" on every port, or adopt Hyle? Does the colour-never-alone rule bind this non-Hyle repo? | OQ-26, OQ-29 (nearest; neither lists hnm, see §7) | copy and colour of port-added states | text plus shape regardless |
| Q12 | M6 controller backends (JVM-only, all three desktops at once): `STATE.md` lists them as the next step, the program's gate puts the desktop usability gaps first. Before or after the packaging lanes? | none (hnm-local) | L4 | after |
| Q13 | Is the Rust and cpal graft at the `FloatStream` seam still intended, and should it precede iOS and wasm (it needs per-platform native builds)? | none (hnm-local) | iOS and wasm audio paths | deferred, Kotlin backends only |
| Q14 | Should the `cloud` seam in `HybridPatternGenerator` route through asom when asom has an off-Android surface, or stay null (no network) on all ports? | OQ-7d (F4) | any network code | stays null |
| Q15 | May a separate hygiene PR correct the stale Android rows in `README.md`, `docs/MODULES.md`, `docs/ANDROID.md` and `ci.yml`'s comment? This PR does not. | none (hnm-local) | doc accuracy only | untouched |
| Q16 | Does the desktop audio backend get its own `:backend-desktop` module now (the repo's documented layout, one `include`), or stay in `ui/src/jvmMain` until M6? | none (hnm-local) | L1 | new module |
| Q17 | iOS and iPadOS deployment floor (the pre-plan reading suggests 16 or 17; Compose MP and Core Haptics both need 13 or later). macOS follows the program's 15+ floor unless told otherwise. | none (hnm-local) | I5 | iOS 17 |

**Proposals (nothing here is ruled).** The repo keeps no decision log, so proposals live only here until the
owner moves them. (1) A licence per Q1. (2) `docs/MODULES.md` gains rows for `:backend-desktop`, `:backend-ios`,
`web/`, and milestones for desktop packaging, iOS and a web/UT head. (3) The opt-in rule in `README.md`
"Do not touch" 1 is extended in words to `ENABLE_IOS` and `ENABLE_WASM`. (4) `STATE.md` gains a dated
`Porting progress` heading, filled only with real command output (R7). (5) One version source for installers (Q10).

## 9. Sources read

Repo files (relative to the repo root): `README.md`, `STATE.md`, `settings.gradle.kts`, `build.gradle.kts`,
`gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`,
`core/build.gradle.kts`, `ui/build.gradle.kts`, `desktopApp/build.gradle.kts`, `androidApp/build.gradle.kts`,
`androidApp/src/main/AndroidManifest.xml`, `.github/workflows/ci.yml`, `.github/workflows/android.yml`,
`.github/workflows/cleanup-artifacts.yml`, `docs/MODULES.md`, `docs/ANDROID.md`, `docs/AUTHORING-INTERFACES.md`
(headings, TL;DR, Key Findings, Recommendations, Caveats),
`desktopApp/src/main/kotlin/dev/hnm/workbench/desktop/Main.kt`, `.../JvmAudioBackend.kt`,
`ui/src/jvmMain/kotlin/dev/hnm/workbench/ui/Main.kt`, `ui/src/jvmTest/kotlin/dev/hnm/workbench/ui/PreviewRenderTest.kt`,
`ui/src/commonMain/kotlin/dev/hnm/workbench/ui/WorkbenchApp.kt`, `.../model/EditorState.kt`,
`.../components/RhythmCapturePanel.kt`, `.../components/ExportPanel.kt`, `.../components/ImportPanel.kt`,
`.../components/CapabilityPanel.kt`, `.../theme/Theme.kt`,
`core/src/commonMain/kotlin/dev/hnm/workbench/core/playback/{Backends,PatternPlayer,FloatStream,TransportClock,PatternTransport}.kt`,
`.../design/{PatternGenerator,ParameterNavigator,RhythmCapture}.kt`, `.../device/{DeviceDatabase,DeviceProfile}.kt`,
`.../export/{AhapExporter,AhapImporter,KotlinVibrationEffectExporter}.kt`,
`androidApp/src/main/kotlin/dev/hnm/workbench/android/*.kt`.

Program inputs: `Personal-Tracker/PORTING_PROGRAM.md` §0 to §3, §4 (each platform), the §5 row for this repo, §6,
§7, §8; the pre-plan repo profile of 2026-10-06 (a reader's structured notes on this checkout, not checked in); the
program's platform briefs under `porting/platforms/`.

Commands run for the measured figures: `git ls-files | grep -E '\.kt$' | wc -l` (80) and the same piped to
`xargs cat | wc -l` per module (core 4,137 main; ui 3,171 main; desktopApp 118; androidApp 1,168; tests 1,649);
`grep -c '@Test'` over test files (106); an import grep over `core/src/commonMain` and `ui/src/commonMain` for
`java|javax|android` (no match) and for `System.|Thread.|String.format` (the one `System.currentTimeMillis()`
call); a network grep over all modules (no match); a path lint over `git ls-files` (107 paths, 0 violations).
