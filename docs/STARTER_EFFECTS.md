# Referenced starter effects

HAfx ships six original starter designs. Unlock confirmation and Lock latch combine synthesized sound with haptics; the four frequent/navigation cues remain haptic-only. Open **Feel → Starter effects → Reference** on the phone, or **Starter effects → an effect → About & source** on the watch. The phone search also matches reference titles, so searching for Apple finds the relevant starters. Existing saved libraries do not need to be replaced to access them.

| Effect | Intended sensation | Reference and relationship |
| --- | --- | --- |
| Unlock confirmation | Two taps with a short rising, percussive tone | Inspired by [Apple Watch unlocking an iPhone](https://support.apple.com/en-gb/102438), with [Mac Auto Unlock](https://support.apple.com/en-us/102442) as an additional workflow reference. The iPhone article explicitly documents a wrist haptic and alert. It does not provide the waveform or timing used here. |
| Lock latch | Firm closure and a smaller latch with a descending click | Original companion to Unlock confirmation. Apple's same iPhone article documents a Lock iPhone action; it does not specify a lock haptic. |
| Soft detent | A restrained single tick | Original tuning informed by [Android haptic design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles), including restrained frequent feedback. |
| Boundary bump | A rounded end-of-range cue | Original interpretation of boundary/rejection feedback, with [Android HapticFeedbackConstants](https://developer.android.com/reference/android/view/HapticFeedbackConstants#REJECT) as a semantic reference. It does not invoke that view-feedback API. |
| Search match | Two quiet, spaced ticks | Original search-navigation cue informed by [Android design principles](https://developer.android.com/develop/ui/views/haptics/haptics-principles). It is not a validated readability intervention. |
| Completion bloom | A lift, landing and softer final tick | Original design using the semantic role documented for [WatchKit success](https://developer.apple.com/documentation/watchkit/wkhaptictype/success). It is not Apple's success waveform. |

The in-app reference view includes short source quotations and direct links, checked on 18 October 2026. Quotes describe documented behaviours or design principles; event timing, primitive choice and strength are HAfx-authored. No Apple audio, vibration files or measured Taptic Engine waveforms are bundled. Exact reproduction would require measurement and device-specific validation; timing alone cannot guarantee the same sensation on different motors.

Native JSON metadata carries the starter identity, description, reference titles/URLs, quotations and interpretation note. Save, personalization and phone/watch transfer preserve it. References stay attached to derived work, while the reference view explains that edits can change its feel. Strength tuning preserves the relative light/firm relationship between pulses.

The effects are previews and editable templates. They do not unlock or lock another device or replace system security feedback. There is no claim that Apple endorses HAfx.

## Paired sound — alpha03

Alpha02 shipped Unlock and Lock as haptic-only effects. Alpha03 adds original synthesized audio to both. Each sound onset shares an authored timestamp with its corresponding haptic onset. Unlock moves from a quieter low tone to a fuller higher tone; Lock closes with a stronger sound followed by a lower, quieter latch. Short attacks and decays keep both cues compact.

The phone labels the full preview **Hear & feel** and offers **Haptics-only preview** under More. The watch's full audition includes both layers by default and has a persistent Sound on/off preview preference using device media volume. This preference does not remove audio from the saved effect. Search-lab crossings intentionally use the haptic component without sound.

Watch spacing edits move sound and haptic onsets together while retaining tone pitch and decay. Haptic strength preserves relative pulse dynamics; it does not silently change sound gain. Stock alpha02 presets are refreshed by stable preset identity. Saved user documents, including quiet custom copies, are preserved.

The sound layer is an original HAfx composition, not an Apple recording. Authored alignment is checked in software; physical speaker/motor latency, audio routing and perceived congruence still need on-device measurement and listening. No claim of measured Apple-equivalent playback is made.
