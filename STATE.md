# STATE

## Current state
- v0.15.0 — the app is **HAfx Studio**: name + light/dark app icon (Android adaptive + themed icon, desktop window); masters in `docs/brand/`.
- Four design stages done (motion → texture → material → navigator).
- JVM CI is green: `core` fully unit-tested; `:ui` renders headlessly to a preview PNG.
- Android build is gated behind `ENABLE_ANDROID=1` (SDK not always provisioned); default `./gradlew build` stays JVM-only.
- One backend-agnostic IR (`HapticAudioPattern`) is the spine; render/export seam is swappable per backend.

## Next steps
- Controller HID backends (M6): SDL rumble + DualSense HID.
- Multi-device variance (M7): capability probing + graceful degradation across LRA / ERM / wideband actuators.
- Multi-platform porting (Ubuntu Touch, Linux desktop, iOS/iPadOS, macOS, Windows): plan only, nothing built — see [docs/PORTING_PLAN.md](docs/PORTING_PLAN.md).

## Owner-verified
- On-actuator feel is owner-verified only — the CI image has no actuator/device; render/schedule cores are the machine-verified parts.

## Porting progress
Plan: [docs/PORTING_PLAN.md](docs/PORTING_PLAN.md). Recorded only with the real command used; nothing here is device-verified.

- 2026-11-11 L1 (wave 1, commit 07d03c2): `:backend-desktop` with `JvmAudioBackend` and `JvmPatternPlayer`. Evidence: `./gradlew :core:jvmTest :ui:jvmTest` (see below). Audible output: NDV.
- 2026-11-11 X1 (wave 1, commit 07d03c2): no `System.` call left in `ui/src/commonMain`; checked by `python3 -I tools/port_guards.py --paths --purity` (0 violations, 112 tracked paths).
- 2026-11-11 X2: `tools/port_guards.py`, `tools/test_port_guards.py`, `.github/workflows/port-guards.yml`. `python3 -I tools/test_port_guards.py` 12 tests OK; `python3 -I tools/port_guards.py --projects` 0 violations. The workflow itself: `CI-ONLY / NOT RUN`.
- 2026-11-11 X3, X4 and desktop shortcuts: `EditorState.hostCapabilities`, `PlatformIo` seam, `KeyShortcuts.kt`. `./gradlew :core:jvmTest :ui:jvmTest` -> 114 tests, 0 failures; `PreviewRenderTest` (8) unchanged. Desktop `Main.kt` sets `hostCapabilities`; no `PlatformIo` actual exists yet (L2), so the Open/Save/Copy controls stay hidden. Key handling through a real window is not tested: NOV.
- 2026-11-11 M1, W1: `TargetFormat.Dmg` and `TargetFormat.Msi` added. `./gradlew :ui:tasks --all` diff on Linux: adds `notarizeDmg`, `notarizeReleaseDmg`, `packageDmg`, `packageReleaseDmg`, `packageMsi`, `packageReleaseMsi` and nothing else; none is in the `build` task graph (`./gradlew build --dry-run`). Templates under `packaging/macos` and `packaging/windows` are disabled and unbuilt.
- 2026-11-11 `desktop-macos.yml`: unsigned DMG lane, public repo only, no uploads. `CI-ONLY / NOT RUN`.
- 2026-11-11 `apple/` scaffold (Info.plist, PrivacyInfo.xcprivacy, XcodeGen `project.yml`): plists parsed with `plistlib`, YAML with PyYAML; never run through XcodeGen or Xcode.
- Checklists, all results empty: `docs/DEVICE_CHECKLIST_{LINUX,MACOS,WINDOWS,IOS}.md`.
- Not started: I1 to I4, I6 (`ENABLE_IOS`, `:backend-ios`, `ios.yml`) wait on the Kotlin/Compose/Xcode pin (OQ-38); L2, L3.
