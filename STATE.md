# STATE

## Current state
- v0.14.0 — four design stages done (motion → texture → material → navigator).
- **Android (`androidApp/`) is the primary, first-shipping target**: full Compose workbench UI +
  Vibrator backend + capability probe. `:androidApp` (and the Android target on `core`/`:ui`) wires
  into the Gradle build only when `ENABLE_ANDROID=1` or a local `sdk.dir` is present — an environment
  guard for this SDK-less dev container / the JVM-only `ci.yml` runner, not a priority signal.
  `android.yml` builds the debug APK on every push/PR (mirrors `ci.yml`'s trigger), not just
  tags/releases, so Android is checked continuously.
- JVM desktop (`desktopApp/`) is the secondary dev/debug driver: validates `core` + `:ui` on a plain
  JVM with no SDK needed. JVM CI (`ci.yml`) is green: `core` fully unit-tested; `:ui` renders
  headlessly to a preview PNG.
- One backend-agnostic IR (`HapticAudioPattern`) is the spine; render/export seam is swappable per backend.

## Next steps
- Controller HID backends (M6): SDL rumble + DualSense HID.
- Multi-device variance (M7): capability probing + graceful degradation across LRA / ERM / wideband actuators.

## Owner-verified
- On-actuator feel is owner-verified only — the CI image has no actuator/device; render/schedule cores are the machine-verified parts.
