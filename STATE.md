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

## Owner-verified
- On-actuator feel is owner-verified only — the CI image has no actuator/device; render/schedule cores are the machine-verified parts.
