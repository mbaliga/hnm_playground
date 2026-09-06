# STATE

## Current state
- v0.14.0 — four design stages done (motion → texture → material → navigator).
- JVM CI is green: `core` fully unit-tested; `:ui` renders headlessly to a preview PNG.
- Android build is gated behind `ENABLE_ANDROID=1` (SDK not always provisioned); default `./gradlew build` stays JVM-only.
- One backend-agnostic IR (`HapticAudioPattern`) is the spine; render/export seam is swappable per backend.

## Next steps
- Controller HID backends (M6): SDL rumble + DualSense HID.
- Multi-device variance (M7): capability probing + graceful degradation across LRA / ERM / wideband actuators.

## Owner-verified
- On-actuator feel is owner-verified only — the CI image has no actuator/device; render/schedule cores are the machine-verified parts.
- **Crash recovery** (`dev.aarso:crash-recovery`) — pending device verification (design review, not just compile). **Preview the recovery screen without a real crash:** in the "Feel-test gallery" (`MainActivity`), long-press the "build vX.XX" diagnostics caption (debug builds only) — calls `CrashRecovery.previewIntent(context, "Haptics Workbench")`. No dedicated About screen exists here, so this is the nearest existing version-adjacent text.
