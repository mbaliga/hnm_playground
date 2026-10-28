# HAfx intro

One score, every surface. `score.mjs` authors the 10 s intro as native HAfx IR (`HapticAudioPattern`).

- `node build.mjs` → `dist/intro.hafx.json` (the score) + `dist/hafx-intro.html` (live web player).
- Web player: Web Audio renders the IR; touch cascades by device — gamepad dual-rumble, `navigator.vibrate`
  PWM (Android), taptic ticks via the `<input switch>` hook (iOS 18+ Safari, best effort), a "body" layer
  that sonifies the haptic track as 46 Hz + harmonics (felt through speakers/chassis, implied on phone
  speakers), and an always-on visual proxy (shake / luminance). All paths read the audio clock.
- `node render.mjs video 1080 1920 out` → MP4 with the haptic track baked into the bass and the picture.
- Android: `androidApp/src/main/assets/intro/` holds the score + a silent 720p render; `IntroView` starts the
  IR on the first rendered frame. `IntroScoreTest` (jvmTest) pins the asset to the IR contract.
- `python3 kotlin_render_port.py dist/intro.hafx.json` mirrors the core audio renderer to check headroom.

Copy `dist/intro.hafx.json` into `androidApp/src/main/assets/intro/` whenever the score changes.
