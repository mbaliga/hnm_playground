# STATE

> Short status. For the full, detailed engineering handoff see **[HANDOFF.md](HANDOFF.md)**.

## Current state (v0.15.0)
- One backend-agnostic IR (`HapticAudioPattern`) is the spine; the render/export seam is swappable per backend.
- Design vocabulary done: motion → texture → material → navigator, plus rhythm capture and variations.
- **AI assistant** (on-device intent→pattern engine, offline; cloud LLM seam wired but inactive).
- **Recorder2 dark aesthetic** across the Compose workbench and the Android player.
- **Multi-device variance:** capability probing + a seeded **device database** (Pixel/Galaxy/iPhone/DualSense/ERM…) and an in-editor **device simulator**; graceful degradation across LRA/ERM/wideband.
- **Wideband envelope/PWLE path** in `core` (`PlayEnvelope`); AHAP **import** (not just export); a **pattern registry** (publish-channel seed).
- **Procedural splash** (visual + sound + haptics from one shared timeline) on both apps.
- JVM CI green: `core` fully unit-tested; `:ui` renders headlessly to preview PNGs. Android build gated behind `ENABLE_ANDROID=1`; default `./gradlew build` stays JVM-only and validated by CI's Android lane.

## Next steps
- Bump `compileSdk` to 36 and implement the real Android PWLE render (`WaveformEnvelopeBuilder`) — activates true HD haptics (`PlayEnvelope` is currently collapsed to an amplitude waveform on Android).
- Host the pattern registry as a fetched remote index; grow the device DB from real "device capability report" submissions.
- Controller HID backends: SDL rumble + DualSense HID.
- On-actuator feel calibration on real LRA hardware.

## Owner-verified
- On-actuator feel is owner-verified only — the CI image has no actuator/device; the render/schedule/geometry cores are the machine-verified parts.
