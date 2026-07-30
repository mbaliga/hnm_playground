# Clackpad ⟷ Workbench hand-off — manual verification checklist

Everything in `docs/CLACKPAD_CONTRACT.md` is implemented and passes on JVM
(`core:jvmTest`, `ui:jvmTest`), but the hand-off itself only exists as real
Android code — Intents, `VibrationEffect.createWaveform`, a `FileProvider` —
none of which runs in the SDK-less environment this was built in. This is the
checklist for whoever has a real Android 12+ device and both apps' debug
builds. Nothing here should be treated as done until it's been run for real.

## Prerequisites

- One physical or emulated device, **Android 12 (API 31) or newer** — the
  Workbench's `minSdk`, and the hard floor for the whole hand-off (Clackpad's
  own launch code checks this and refuses below it).
- `dev.hnm.workbench.android` debug build installed (`./gradlew
  :androidApp:installDebug` with `ENABLE_ANDROID=1`, or the APK artifact off
  the `hnm_playground` CI run for this branch).
- `com.clackpad.ime` debug build installed (`bb-keyboard/gradlew
  installDebug`), set as the active keyboard (Settings → System → Languages →
  On-screen keyboard → Clackpad, enabled).
- Clackpad Pro unlocked on that build — the hand-off row is gated behind
  `pro_unlocked` like the rest of that card. Real billing is awkward to test
  repeatedly; the pragmatic shortcut is setting the flag directly:
  `adb shell run-as com.clackpad.ime sh -c "echo unlock"` won't work on a
  release-signed build, but on a debug build you can flip it from within the
  app's own Settings → Clackpad Pro → Unlock flow once, or, if a test/sandbox
  Play Billing track is wired, use that. (There's no separate debug bypass
  flag in the code today — `BillingManager` only reads/writes
  `clackpad_prefs["pro_unlocked"]` — so unlocking once persists for all
  further runs on that install.)

## Test 1 — Launch: Clackpad → Workbench

1. Open Clackpad's own Settings app (its launcher icon, not the IME itself).
2. Scroll to **Clackpad Pro** and confirm the perk row now reads
   **"Design and use your own key sound effects and haptics with the Audio
   Effects and Haptics Workbench ↗"** in the accent colour (violet/lilac),
   not plain grey — grey means Pro isn't detected as unlocked; stop and fix
   that first.
3. Tap that row.

**Expected:** the Haptics + Audio Workbench opens directly (not a chooser,
not the Play Store) — confirms the explicit-`ComponentName` intent resolved
without any `<queries>` entry. It should land straight in the **Editor**
(not the Library tab) with the "Tap" seed pattern loaded, and the Export
panel showing a single **"Send to Clackpad"** button in place of the usual
format-chip row.

**If it fails:** check `adb logcat | grep -i workbench` for an
`ActivityNotFoundException` (Workbench not installed, or its `applicationId`
drifted from `dev.hnm.workbench.android` — check `androidApp/build.gradle.kts`
against `WorkbenchHandoff.PACKAGE` in *both* repos, they're copy-pasted
constants with no shared module to catch drift).

## Test 2 — Round trip: Workbench → Clackpad

With Test 1's Workbench session still open:

1. Optionally tweak the pattern (drag the intensity/sharpness handles) —
   confirms the shipped JSON reflects live edits, not just the seed.
2. Tap **"Send to Clackpad"**.

**Expected:** the screen returns to Clackpad (whatever was in the foreground
before Test 1 — likely its Settings screen) within about a second, no
visible flash of a file picker or share sheet (this path never touches
disk), followed by a toast reading **`Haptic pattern set for "key"`**.

**If it fails silently (no toast, nothing happens):** check
`onActivityResult`'s `REQ_HAPTIC_WORKBENCH` branch is actually being hit —
add a temporary log line, or check `adb logcat` for the
`"Couldn't read that pattern."` toast's `catch` block firing, which would
mean the JSON round-tripped but failed `importHapticJson`'s validation
(`format`/`version`/`role`/`render` shape check).

## Test 3 — Playback actually changed

1. Switch to any text field and bring up the Clackpad keyboard.
2. Tap a few letter keys and compare the feel/sound against what it felt
   like *before* Test 2 (worth doing Test 3 once **before** Test 1/2 too, as
   a baseline, if you can restore state between runs).
3. Drag the **Haptics intensity** slider in Clackpad's settings from 0 up to
   10 while typing.

**Expected:** the keypress haptic is audibly/physically different from the
stock click (it's now playing the shipped pattern's baked
`render.timings`/`render.amplitudes` via `VibrationEffect.createWaveform`
instead of the single-shot formula) — but the intensity slider should still
visibly scale it: 0 stays silent, 10 stays the strongest. If dragging the
slider does nothing, the amplitude-scaling multiply in
`HorizKeebService.vibrate()` regressed.

4. Tap **Backspace** and a suggestion chip (if any are showing) — these use
   the `"del"`/`"suggest"` roles, which have **no** imported pattern yet
   (only `"key"` was shipped in Test 2), so they should feel **exactly like
   before** — unchanged. This confirms the per-role gate isn't accidentally
   applying the `"key"` pattern to every role.

## Test 4 — Fallback behavior

- **Workbench not installed:** uninstall the Workbench app, repeat Test 1.
  Expected: a toast — *"Haptics + Audio Workbench isn't installed."* — no
  crash, no ANR.
- **Device below API 31:** if you have an older device/emulator handy, repeat
  Test 1 there. Expected: an immediate toast — *"The Haptics + Audio
  Workbench needs Android 12 or newer."* — the launch is never attempted.
- **Back out instead of shipping:** repeat Test 1, then press the system
  Back button instead of tapping "Send to Clackpad". Expected: Clackpad
  regains focus with **no** toast and **no** change to its stored patterns
  (this is the `RESULT_CANCELED` path — `onActivityResult` should no-op).

## Test 5 — Regression sweep (make sure this didn't break what already worked)

- Clackpad's existing **Save/Load configuration** (Settings → Advanced →
  Backup & Restore) still exports/imports the full `clackpad_config` JSON
  correctly — this shares `onCreate`'s `ACTION_VIEW` handling with the new
  hand-off routing (`handleIncomingIntent`), so it's the one place a bug here
  could plausibly break something unrelated.
- Opening a `.json` file that is genuinely a Clackpad config backup (not a
  `clackpad-haptic` one) from a file manager still restores settings and
  shows "Settings restored." — not the new pattern-import toast.
- GIF import (chin sticker / GIF picker flows) still works — the new
  `FileProvider` addition on the *Workbench* side is separate from
  Clackpad's own existing `FileProvider`, but worth a quick sanity check
  that adding one didn't somehow collide with the other on a shared device.

## Known gaps this checklist does *not* cover (by design, not oversight)

- **Only the `"key"` role is reachable from the UI today.** The Pro-perk row
  hardcodes `launchHapticsWorkbench("key")`; `"del"`/`"action"`/`"suggest"`/
  `"swipe"` all work at the code level (`ClackpadHapticStore`/
  `HorizKeebService.vibrate()` check every role) but there's no picker UI
  yet to launch a hand-off *for* them. To test one manually, an engineer can
  drive the explicit intent directly:
  ```
  adb shell am start -n dev.hnm.workbench.android/.WorkbenchActivity \
    -a dev.hnm.workbench.action.DESIGN_HAPTIC \
    --es dev.hnm.workbench.extra.ROLE del \
    --es dev.hnm.workbench.extra.SOURCE com.clackpad.ime
  ```
  (Ship it from there the normal way; Clackpad's `onActivityResult` doesn't
  care how the launch happened, only that the result extra is present.)
- **The standalone hand-off (contract §2 delivery b) has no UI entry point
  yet.** `ClackpadShare.kt`/the `FileProvider` are implemented and would work
  if wired to a "Send to Clackpad" action in the Library's long-press menu,
  but that UI wasn't built in this pass — it's the natural next slice, not a
  bug in what's here.
