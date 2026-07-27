# Clackpad ⟷ Workbench integration contract

**Status: agreed and implemented in code on both sides, this session — not device-verified or
Clackpad-engineer signed off yet.** The shape below is what `ClackpadExporter.kt` actually emits and
what the Android glue actually wires up, not a proposal. Outstanding: a real-device run against a real
Clackpad build, and sign-off from whoever owns the Clackpad repo.

Clackpad (`com.clackpad.ime`) is a WebView-backed split-QWERTY IME with a Pro perk letting a user design
key-feedback haptics. This doc covers the Workbench-side half; only Clackpad's public surface — package
name, intent actions/extras, MIME type — is described here.

## 1. Launch: Clackpad → Workbench

Clackpad starts `WorkbenchActivity` with an **explicit `ComponentName`**, not an implicit action match —
no third app can intercept it. Explicit-component intents also skip Android 11+ package-visibility
filtering, so neither app needs a `<queries>` entry.

| Extra | Type | Value |
|---|---|---|
| action | — | `dev.hnm.workbench.action.DESIGN_HAPTIC` |
| `dev.hnm.workbench.extra.ROLE` | `String`, required | one of `key`, `del`, `action`, `suggest`, `swipe` — Clackpad's own `vibrate(type)` vocabulary, verbatim |
| `dev.hnm.workbench.extra.SOURCE` | `String`, required | Clackpad's package name, `com.clackpad.ime` |

Constants: `androidApp/.../WorkbenchHandoff.kt`. `WorkbenchActivity.incomingHandoffRole()` validates
action + source + role (`null` on mismatch) — no `<intent-filter>` needed, since `WorkbenchActivity` is
already `exported=true` for `MAIN`/`LAUNCHER` and explicit intents reach `onCreate()` regardless. A
malformed launch just degrades to a normal, non-handoff open.

A valid launch makes `EditorState.beginHandoff(role)` load a role-appropriate seed pattern
(`RoleSeedPatterns.kt`: `key→TAP`, `del→SNAP`, `action→CONFIRM`, `suggest→SELECTION`, `swipe→SWIPE`),
swapping `ExportPanel`'s format-chip row for one "Send to Clackpad" button. No protocol-version extra on
the launch — versioning lives in the export JSON's `format`/`version` instead.

## 2. Export: Workbench → Clackpad

`core/.../export/ClackpadExporter.kt` is the single source of truth for the `clackpad-haptic` format —
`object ClackpadExporter` plus its `@Serializable` file classes, pinned field-by-field by
`ClackpadExporterTest.kt`. MIME **`application/vnd.clackpad.haptic+json`**, distinct from
`application/json`, already claimed by Clackpad's settings backup.

```json
{
  "format": "clackpad-haptic", "version": 1,
  "contract": { "status": "agreed", "spec": "docs/CLACKPAD_CONTRACT.md" },
  "name": "Heartbeat", "role": "key",
  "source": { "app": "Haptics + Audio Workbench", "version": "0.14.0", "build": "15" },
  "durationMs": 420, "envelopeBaked": false,
  "pattern": {
    "format": "workbench-pattern", "version": 1, "name": "Heartbeat",
    "events": [
      { "atMs": 0,   "intensity": 0.65, "sharpness": 0.06, "duration": 110, "voice": "thud" },
      { "atMs": 160, "intensity": 0.55, "sharpness": 0.06, "duration": 110, "voice": "thud" }
    ]
  },
  "render": {
    "api": "android.os.VibrationEffect.createWaveform(timings, amplitudes, -1)",
    "timings":    [0, 110, 50, 110],
    "amplitudes": [0, 166, 0, 140]
  },
  "units": { "...": "field docs inline — see ClackpadExporter.kt's UNITS map" }
}
```

**`render.timings`/`render.amplitudes` is the entire contract Clackpad's importer needs** — everything
above it is provenance only. `amplitudes` is `0..255`, the native `createWaveform` scale, zero
conversion; `timings` is `List<Long>` ms segments (index 0 always off), both reusing
`KotlinVibrationEffectExporter.buildAmplitudeTimeline()`. `sharpness` is baked in at export time, never
sent live: `vibrate()` has no sharpness/frequency channel. Un-authored `voice` derives as
`Transient`→`click`/`thud` by a `sharpness>=0.6` threshold, `Continuous`→`swell`,
`Primitive`→`click`/`thud`/`grain`/`swell`. One pattern per file; no multi-role wrapper.

Two delivery paths: **(a)** round trip for a Clackpad-initiated session — no file, JSON returns as a
plain `String` Activity-result extra (`shipToClackpad()`), `RESULT_CANCELED` if the user backs out; and
**(b)** standalone hand-off — `ClackpadShare.kt` writes the JSON under `cacheDir/`, wraps it in this
app's `FileProvider`, and sends `ACTION_SEND` + `setPackage("com.clackpad.ime")`, falling back to a
chooser then a toast. Role is always explicit in (b). Manifest footprint: one `FileProvider`
(`exported=false`, `grantUriPermissions=true`); no new `<intent-filter>` or `<uses-permission>`.

## 3. Constraints and ownership

Zero network calls anywhere — local `Intent`s, a Binder string extra, or an in-process `FileProvider`
URI only; no new permission. Nothing leaves the device or gets logged; payload is haptic parameters
only, never typed text or keystrokes.

hnm_playground owns the `clackpad-haptic` schema, signaled by `version`; Clackpad owns its own import
code and treats an unrecognized major `version` as "don't understand this file."
