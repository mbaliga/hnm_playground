# STATE

## Current state
- v0.14.0 — four design stages done (motion → texture → material → navigator).
- JVM CI is green: `core` fully unit-tested; `:ui` renders headlessly to a preview PNG.
- Android build is gated behind `ENABLE_ANDROID=1` (SDK not always provisioned); default `./gradlew build` stays JVM-only.
- One backend-agnostic IR (`HapticAudioPattern`) is the spine; render/export seam is swappable per backend.
- Home-screen widgets (2026-11-04, unreleased): six standard App Widgets in `:androidApp` (Effect pads, Pattern of the day, Pattern shape, Recently felt, This phone's haptics, Library at a glance), each with collapsed/small/medium/large layouts. Pure logic + the gallery list (`FeelTestCatalog`) live in `core` and are covered by `:core:jvmTest`; pads play through a translucent `FeelActivity`. Builds; adds no new lint findings (the existing `WrongConstant` error in `AndroidHaptics` predates it); nothing seen on a device yet. See [docs/widgets.md](docs/widgets.md).

## Next steps
- Controller HID backends (M6): SDL rumble + DualSense HID.
- Multi-device variance (M7): capability probing + graceful degradation across LRA / ERM / wideband actuators.

## Owner-verified
- On-actuator feel is owner-verified only — the CI image has no actuator/device; render/schedule cores are the machine-verified parts.
- Widgets: rendering/resizing on real launchers, a pad's feel from the home screen (Android 12+ background-vibration rule), the midnight redraw, TalkBack and font scale — all owner-verified only (list in docs/widgets.md).
