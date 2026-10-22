# HAfx Studio + Watch MVP candidate

Version 0.25.0-mvp02. Android and Wear OS first. The product loop is: choose or make an effect on the device where it is felt, audition its sound and vibration, tune it, save it offline, and exchange editable revisions with Studio.

This is an installable debug candidate for pilot users, not a Play Store release. Physical sensory quality and paired delivery are release gates until recorded on actual paired devices. Automated checks cannot certify motor/speaker onset or perceived congruence.

## Start on the watch

Open HAfx → Starter effects → Unlock confirmation → Hear & feel. This is an original HAfx sound-and-haptic design inspired by the documented unlock workflow, not Apple's recording or waveform. About & source retains the references and short quotations.

Make a pulse or Tap a rhythm to create locally. Add paired sound gives each pulse a short soft tone. Sound level changes the saved audio layer. Sound on/off only changes previews and uses the device's media volume. Spacing moves both modalities' onsets together; Strength adjusts motor intensity when the hardware supports it. Compare the original, then Keep. Saved effects contains your work; starters remain on Home. Rename and Delete are available on saved personal effects, with confirmation before deletion.

The watch creates, plays, tunes and keeps effects offline. Leaving HAfx stops playback. A new effect is never automatically played when received.

## Continue on the phone

Feel puts Yours before the reference catalogue. Open an effect to reach the timeline editor. Make offers local generation and rhythm creation. The technical workspace exposes native JSON editing; Device contains preferences, actual hardware capability reporting, connection status and file tools. Reference profiles affect compatibility previews and export; they do not change the physical device's capabilities.

Use Save in the editor's library panel. Export supports native effect JSON and a rendered sound WAV as files, as well as the existing code/AHAP text formats. A WAV contains sound only. Native JSON is the editable format that retains haptics, synthesized sound, attached text and references. Imported effects appear in Yours without replacing the current draft.

## Use your own text on the watch

Device → Attach text for watch search accepts UTF-8 plain text up to 2,000 characters. It attaches the text to the current effect. Save and send the effect, open the received version on the watch, then choose Search lab. Change the query and scrub, or use Next/Previous match. Haptic cues mark crossings in either direction; Cues off provides a comparison. Long or unsuitable effects use an explicitly labelled short cue. The included sample text is used when no text is attached.

Attached text is stored with the effect and included in native exports, backups and companion transfers. Choose text you intend to share. This is a navigation tool and research prototype; it makes no validated readability or comprehension claim.

## Pair, send and receive

Install both APKs with the same application ID and signing identity. Pair the phone/watch through their normal Wear OS companion setup. HAfx → Device on the phone reports connected nodes; Check connection & delivery refreshes both availability and receipts. Watch → Keep → Send to Studio sends an immutable revision. Phone → Device → Save and send current effect sends it back.

Queued means Google Play services accepted the item locally. Saved on phone/watch means the receiving app durably stored it. Neither means the receiver auditioned it. No connection is required for local creation or saving. Failed queueing is reported; reopen HAfx and check delivery after reconnecting. Wear Data Layer can route through Google services. See [Android's pairing guide](https://developer.android.com/training/wearables/get-started/connect-phone).

Repeated delivery is suppressed. Concurrent revisions are separate copies. Deleting a received effect locally does not delete the sender's copy; replay of an acknowledged received revision does not resurrect the local copy. There is no deletion synchronization or automatic draft replacement.

## Back up and recover

Phone → Device → Back up library to file. Keep this native JSON somewhere outside HAfx before uninstalling. Import effect or library restores/adds user work in one transaction: invalid input or a failed write leaves the previous library unchanged. Existing revisions are not imported twice. Renaming preserves document identity and creates a new revision.

Drafts and the library use atomic private files. If the library is unreadable, HAfx preserves it and disables new saves. Device offers Export original recovery file and Archive & recover. Recovery archives the original before creating a fresh library; the draft is kept. Import an earlier backup to restore work. The watch offers the same archive-and-recover action; restore backups through Studio and resend the effects. Exported backups contain the saved library, not an unsaved draft: save work first.

## Supported scope and limits

- Android 12+ phone and Wear OS based on Android 12+, with a vibrator. The apps share application ID `dev.hnm.workbench.android`.
- Native effects: 30 seconds including release tails, 64 KiB per effect, 16 tracks, 512 events. Library: 256 effects / 4 MiB. Imports validate before durable changes.
- Synthesized audio travels with the native document. Sample assets are not transported; sample-based imports and playback report this limitation. Android playback of audio-driven haptic coupling is rejected explicitly; use explicit haptic events or waveform export. Coupled effects require Studio for tuning.
- Audio uses media volume/routing and transient audio focus. Losing focus, Stop, or leaving the activity cancels the paired session. Actual latency and fallback motor character vary by device.
- No Apple Watch app, ring/strap controller, marketplace, general notification replacement, video scrubbing integration or participant study runner is included.
- Data Layer retains revisions/receipts. Retention cleanup and multi-watch targeting are outside this first MVP; use one phone/watch pair for the pilot.

## Release acceptance

The software validation record accompanying the APKs lists exact test, lint and emulator results. The following physical checks remain necessary before declaring the pilot device-ready:

1. On the target Galaxy Watch 8: compare Unlock and Lock at comfortable media volume, then preview without sound. Confirm relative loudness, congruence, motor strength, silent-mode behavior and actual speaker/Bluetooth routing.
2. Interrupt preparation/playback, rapidly replay, leave the app, lock the screen and receive an audio interruption. Output must stop without later pulses.
3. Pair a phone and watch: send both ways, edit concurrently, receive while backgrounded, repeat delivery, disconnect/reconnect and restart both apps. Require a durable receipt and preserved sound/haptic/text/reference layers.
4. Force-stop/reopen after saving and after an edit. Check the saved library and draft separately. Export a backup, import it, rename/delete a received copy, and confirm it stays deleted after reconciliation.
5. Smallest round display, larger font, TalkBack, touch-only and rotary input: reach all actions, read long names, operate the editor and always reach Stop.

Record model/OS, media route/volume, capability report and pass/fail evidence. These checks evaluate a pilot build; a research paper additionally requires a study design, instrumentation, participant protocol and analysis.

## Build

Use JDK 17 and Android SDK 35. Set `ANDROID_HOME`, then:

```sh
ENABLE_ANDROID=1 ./gradlew :core:jvmTest :ui:jvmTest :androidApp:assembleDebug :wearApp:assembleDebug :androidApp:lintDebug :wearApp:lintDebug :androidRuntime:lintDebug
```

Both debug APKs must be signed with the same key. Preserve existing work before changing signing identities or uninstalling. The accompanying source ZIP and patch include this implementation and its CI workflow.

## Android-native regression checks

`androidRuntime/src/androidTest` exercises real Android static-buffer audio preparation/start/replacement and atomic library/draft round-trips, corrupt-file protection and archived recovery. Build with `:androidRuntime:assembleDebugAndroidTest`. Install the resulting test APK on an emulator and run `adb -s <serial> shell am instrument -w dev.hnm.workbench.runtime.test/android.test.InstrumentationTestRunner`. These checks use isolated test storage, not the pilot app's library. The test APK requires the platform's legacy Android test libraries; production APKs do not.

MVP02 fixes static AudioTrack initialization (the valid pre-write state is STATE_NO_STATIC_DATA), and makes watch renaming preserve unsaved tuning in one durable commit. MVP01 is superseded.

Native effect file export and native sharing create a fresh immutable snapshot without changing the draft. This keeps unsaved edits from being mistaken for an already received revision. Reusing a revision ID with different contents is rejected visibly; an identical repeat remains idempotent.

There is one active draft per device. Keep/Save work before explicitly opening another effect or starting a new one. Audition alone never replaces that draft.

The audio initialization fix follows [Android's AudioTrack state contract](https://developer.android.com/reference/android/media/AudioTrack#STATE_NO_STATIC_DATA).

Imports skip unchanged starter-catalogue entries and identical revisions already present. Use a native export snapshot or a custom effect to add new work.

## Validation recorded — 20 October 2026

185 JVM/UI tests and three Android-native tests pass. Both APKs assemble; Android lint reports no errors. Installed APK hashes match the delivery artifacts. Phone checks cover actual system-picker import, paired playback, save/restart, real text attachment, backup contents, upgrade retention and readable service-unavailable feedback. Watch checks cover paired Unlock, on-surface sound/spacing edits, save/restart, rename without discarding tuning, stable spacing during sound edits, and own-text search/cues-off comparison using a local fixture.

The watch's local Wear API queued a revision without a receiver receipt. The phone emulator reports Wearable.API unavailable. This is not an end-to-end pairing result. The own-text fixture was loaded into isolated emulator storage and then removed; it is not evidence of transfer. Emulators used disabled host audio and virtual haptics, so perceived sound, vibration, latency and congruence remain physical-device checks.
