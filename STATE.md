# STATE

> Short status. For the full, detailed engineering handoff see **[HANDOFF.md](HANDOFF.md)**.

## Current state (v0.22.0)
- One backend-agnostic IR (`HapticAudioPattern`) is the spine; the render/export seam is swappable per backend.
- Design vocabulary done: motion → texture → material → navigator, plus rhythm capture and variations.
- **AI assistant** (on-device intent→pattern engine, offline; cloud LLM seam wired but inactive).
- **Hyle-derived design system**: vendored tokens/Provenance glow semantics (`HyleTokens`/`HyleProvenance`/`HyleColors`/`HyleRoles`), hand-built Multiplatform-safe dot-grid + glass widgets (`HyleWidgets.kt`) — Hyle itself ships no reusable Compose Multiplatform visuals, so these are original implementations informed by its token values, not a live dependency.
- **Onboarding + Device hero card (Phase 6):** a six-beat walkthrough (`OnboardingScreen`) shows once, after the splash — the last interactive beat picks a `WorkspaceMode` (Vibe/Technical), the same setting that now gates the Editor's Edit-as-JSON tool (hidden by default). The Device tab has a real hero card showing the simulated device's name, resonant frequency, and Q factor; picking a device in the Editor's `CapabilityPanel` and viewing it on the Device tab now read the same `EditorState.selectedDevice` (previously each screen kept its own local selection, so the Device tab couldn't reflect what was picked in the Editor).
- **New IA (UX Build Brief v1.1), Phases 0–6 done (Phases 5–6 partially scoped down — see Next steps):** a `Feel` / `Make` / `Device` tab shell (`AppShell`) plus a full-screen `Editor` route reached from any tab and always returning to it; `Feel` lists built-ins + your own patterns with search, rename/duplicate/export/delete; `Make` hosts the Assistant plus five generative mini-flows (Material/Texture/Motion/Rhythm/Blend) reusing existing palette panels; `Device` shows capability diagnostics + report capture. The Editor (`WorkbenchApp`, still the dense panel-based layout) has a real top bar: back arrow, tap-to-rename, undo/redo (slider drags coalesce into one undo step via `commitEdit()`/`onValueChangeFinished`), and an **Edit-as-JSON sheet** (`{ }` button — round-trips the raw IR through `PatternSerialization`, rejecting invalid JSON with an inline error instead of crashing). The timeline (`TimelineView`) already renders haptic + audio as two lanes on one canvas, which covers the brief's "rendered lanes" ask.
- **`chrome.*` interface-feel vocabulary**: ambient UI feedback (tap/confirm/detent/land/boundary/error/navigate) routed through the same capability-degrade path as content haptics, gated by `ChromePlayer` (busy latch, capability gate, `InterfaceFeelLevel` Off/Subtle/Full).
- **Recorder2 dark aesthetic** across the Compose workbench and the Android player, now re-keyed onto Hyle roles (violet primary action, radium/cyan provenance glow) rather than literal colors.
- **Multi-device variance:** capability probing + a seeded **device database** (Pixel/Galaxy/iPhone/DualSense/ERM…) and an in-editor **device simulator**; graceful degradation across LRA/ERM/wideband.
- **Wideband envelope/PWLE path** in `core` (`PlayEnvelope`); AHAP **import** (not just export); a **pattern registry** (publish-channel seed).
- **Procedural splash v2**: one seed drives visual (`RIPPLE`/`BLOOM`/`SWEEP`/`SPARK`/`LATTICE`), sound, and haptics; palette/voice are seeded too (drifts toward Hyle's cloud-cyan hue and a material-derived tone); shows on first 3 launches by default (`SplashPreferences`) and respects system reduced-motion. Native Android `SplashView` retired in favor of the shared Compose splash.
- JVM CI green: `core` fully unit-tested; `:ui` renders headlessly to preview PNGs. Android build gated behind `ENABLE_ANDROID=1`; default `./gradlew build` stays JVM-only and validated by CI's Android lane.

## Next steps
- Phase 5 remainder — a command strip and a tabular envelope-breakpoint editor (alternative to the ADSR sliders), and a loop region for preview playback. (The Simple/Technical workspace preference itself landed as part of Phase 6's `WorkspaceMode` — see above.)
- Phase 6 remainder — the onboarding walkthrough isn't yet reachable again after first completion (no "replay onboarding" entry point in Settings/About, mirroring the splash's `lastSeed` replay gap).
- Phase 7 — Polish pass: motion tokens on transitions, reduced-motion/TalkBack audit, copy audit, delete legacy `MainActivity`, desktop adaptive layout.
- Full Editor timeline gesture set (pinch-zoom, drag-to-move-with-snap-detents, long-press-add menu, scrub-to-feel) — deliberately not attempted in Phase 4's scoped-down pass; only the top bar (back/rename/undo-redo) was built.
- Bump `compileSdk` to 36 and implement the real Android PWLE render (`WaveformEnvelopeBuilder`) — activates true HD haptics (`PlayEnvelope` is currently collapsed to an amplitude waveform on Android).
- Host the pattern registry as a fetched remote index; grow the device DB from real "device capability report" submissions.
- Controller HID backends: SDL rumble + DualSense HID.
- On-actuator feel calibration on real LRA hardware.

## Owner-verified
- On-actuator feel is owner-verified only — the CI image has no actuator/device; the render/schedule/geometry cores are the machine-verified parts.
- 60fps-on-device, TalkBack pass, and stopwatch-timed "≤60s to first satisfying pattern" acceptance criteria from the UX brief also require a physical device/human tester and are not machine-verifiable in this environment.
