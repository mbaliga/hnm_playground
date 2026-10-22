# HAfx Watch alpha

> Historical alpha record. For the current MVP candidate, see [MVP.md](MVP.md).

The first Android/Wear OS creation loop lives on the receiving surface: make a pulse or tap a rhythm, feel it, tune it, keep it offline, use it in the local search lab, and send an editable revision to Studio.

## What is implemented

- A dedicated `wearApp`, using native Wear Compose navigation, round-screen lists, touch alternatives and rotary adjustment. The existing phone editor is not scaled onto the watch.
- Pulse creation, bounded rhythm capture (six taps/four seconds), spacing and capability-dependent strength controls, original/edit audition, a persistent local library and draft restoration.
- A local text-search playground with an editable query, continuous position control, next/previous match, haptics on/off, crossing detection in either direction and rate-limited cues. Long/audio patterns are preserved but replaced by an explicitly labelled short search cue in this lab.
- A shared `androidRuntime` with cancellable preparation, playback status, dedicated scheduling, cancellation of queued vibration events, audio release and lifecycle cleanup. Audio preparation precedes either output starting. This is not a measured synchronization guarantee.
- Atomic Android library/draft files. Failed writes do not mutate the visible library. An unreadable library is retained and new writes are disabled rather than overwriting it with an empty library.
- Built-in templates create user copies. Audition does not replace the draft. Saved patterns carry document, revision and parent IDs in native metadata.
- Explicit bidirectional Wear Data Layer transfers of native effect revisions, checksums, bounded decoding, duplicate suppression and a receipt after durable save. Conflicts are received as separate copies; neither side silently overwrites concurrent work. A transfer never automatically plays anything.
- Phone improvements: visible Stop outside scrolling content, real playback status, platform Back, restoration of navigation on recreation, draft autosave, explicit Feel/More actions, search/no-result states, sharing export text, and separate playback versus compatibility-profile labels. Startup is quiet and generation waits for an explicit audition.

## Build

Requirements: JDK 17+, Android SDK platform 35 and build tools. The repository uses the checked-in Gradle wrapper. Keep the phone and watch signed with the same signing identity and application ID (`dev.hnm.workbench.android`), as required by Data Layer. The two debug APKs built on the same workstation normally use the same debug key. A previously installed debug app signed elsewhere may require a compatible signing key or uninstall; export needed user work before uninstalling.

```sh
./gradlew :core:jvmTest :ui:jvmTest
ENABLE_ANDROID=1 ./gradlew :androidApp:assembleDebug :wearApp:assembleDebug
ENABLE_ANDROID=1 ./gradlew :androidApp:lintDebug :wearApp:lintDebug :androidRuntime:lintDebug
```

Set `ANDROID_HOME` to an installed SDK, or use a local `local.properties` containing `sdk.dir`. Android modules stay opt-in for JVM-only contributors. Debug outputs are under each app's `build/outputs/apk/debug/` directory. This alpha uses the repository's compatible Kotlin/Compose versions and Wear Compose 1.4.1; it is not a store-ready release or a claim of latest-platform API support.

Install the phone APK on an Android 12+ phone and the watch APK on a Wear OS watch based on Android 12+. Pair them through the normal Wear OS setup. Both can create and play local effects without a connection. Enable normal developer deployment only on devices you control.

## First walkthrough

1. Watch → Make a pulse → Feel. Stop must interrupt output, including future scheduled pulses.
2. Open Spacing; adjust with touch or rotary input, then Feel. Strength is shown only for reported amplitude-capable hardware. Compare with Feel original.
3. Keep; close and reopen the app. Find the saved effect and resume its draft.
4. Use in Search lab. Enter a query from the included local document, scrub past matches in both directions and compare with cues off.
5. Send to Studio. The watch first says queued. It only says saved on phone after the phone persists the revision.
6. On the phone, open the received effect from Yours; edit, save and send it back. On the watch, open Saved effects and inspect the received revision. Existing work must remain intact.
7. With the phone unavailable, repeat local creation and save. A pending transfer must not be mistaken for delivery.

## Transfer and persistence contract

`PatternTransfer` uses native pattern JSON plus metadata keys `hafx.document`, `hafx.revision`, `hafx.parent`, and `hafx.source`. Revisions are immutable wire items under `/hafx/v1/pattern/{revision}`. Receipts live under `/hafx/v1/receipt/{revision}/{role}`. Repeated receipt/delivery events are idempotent. Receipts mean durable receipt, not installation as a system notification or a physical audition.

Transfers are explicit. They do not synchronize deletions or overwrite the current draft. Incoming effects with colliding names receive a distinct name. The alpha preserves competing revisions as separate entries rather than presenting a merge UI. Revision and receipt data remain in Data Layer; retention controls are future work.

Payloads are limited to 64 KiB, 30 seconds, 16 tracks and 512 events, with bounded curves and numeric validation. The library holds up to 256 effects/4 MiB. Synthesized audio can travel with a pattern; sample-based audio is rejected until asset transfer is implemented. Android persistence uses private files `hafx/library.json` and `hafx/draft.json`. Watch drafts and library items remain available offline. Wear Data Layer can route through Google infrastructure; this is not a strictly LAN-only transport promise.

## Validation before calling this device-ready

- Physical Watch8: exact model/OS, primitives, amplitude behaviour, silent-mode policy, continuous waveforms, Stop during preparation/playback, rapid replay, activity backgrounding and screen off.
- Paired devices with matching signatures: first transfer, duplicate delivery, queued/offline transfer, timeout/retry, receiving while backgrounded, process restart, invalid/oversized payload and simultaneous edits.
- Storage: force-stop and relaunch; rotation on phone; failed write; corrupt-library recovery. The alpha exposes the failure and preserves the file, but does not yet include a recovery UI.
- Accessibility: smallest supported round display, large text, TalkBack, keyboard input, rotary focus, touch-only navigation, overlay Stop reachability and long effect names.
- Audio/haptics: actual measured onset and jitter; the code's dispatch time is not motor onset. Search cue discriminability and rate limits require human testing.

The local search lab is an interaction prototype, not a completed experiment. It has no participant logger, randomized trial runner or validated readability claim yet. Paired video scrubbing, ring/strap controllers, Apple Watch adaptation, an effects marketplace, cloud generation, general file picking/export, and system-wide feedback replacement are not implemented in this alpha.

## Paired Unlock and Lock — alpha03

Alpha02's Unlock and Lock presets were haptic-only. Alpha03 adds original synthesized sound co-designed with the two taps: a short rising cue for Unlock and a descending closing click for Lock. Each sound onset shares the corresponding haptic's authored time. Spacing edits move both onsets together, preserving sound pitch/decay and relative haptic dynamics. Native JSON saves and transfers include both layers and the source references.

Phone: choose Hear & feel, or More → Haptics-only preview. Watch: Hear & feel includes both layers by default. Sound on/off is a persistent preview preference using device media volume; it does not erase the sound layer from the document. Search-lab crossings use haptics without sound. Stock alpha02 presets are refreshed by stable identity, while saved custom documents are preserved.

These sounds are HAfx originals, not Apple recordings. See docs/STARTER_EFFECTS.md for the source relationships.

## Validation recorded — 19 October 2026, alpha03

Both APKs built. 169 tests passed (132 core; 37 UI including rendering checks). Android lint passed with no errors. APK signing certificates are valid and match. Local build outputs were isolated in a temporary directory to avoid duplicated generated files in the workspace.


Tests cover onset alignment, spacing edits across both layers, pitch/decay preservation, finite unclipped audio, the gap between taps, non-destructive haptics-only preview, stock preset upgrades and preservation of quiet user copies. Audio previews are exported by the app's own renderer.

Authored timing is not a physical synchronization guarantee. Real speaker/motor latency, routing, listening quality, relative loudness and perceived congruence still need device testing. Alpha03 has not completed a watch runtime/navigation check. Earlier alpha builds passed phone launch/reference checks and watch tuning/save/draft checks; those do not establish alpha03's physical sensory quality. Paired Data Layer delivery, rotary hardware and accessibility/large text also remain unverified.
