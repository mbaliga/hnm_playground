# HAfx Studio intro — the Tactile Kit, played

**Scored.** A conductor (`src/main.ts`) runs one 120 bpm, 12-bar score: every press, detent,
ridge, pulse and division sits on the beat grid and is scheduled on the audio clock, with a band
(`src/band.ts`: drone, bass, side-chained pad, hats, membrane claps, risers) underneath.
Form: the dark (a heartbeat you feel) → power/lamp → focus (detents as sixteenths, true focus on
the downbeat) → magnification (the fader is the riser) → stage (the gate is the backbeat) →
voice (the cell's pulse is the kick; each slider move changes it on the beat) → REC (division as
the build) → PLAY on the pop (the drop) → lamp off → HAfx Studio.

**Animated on Disney's 12 principles.** The fingertip (`src/ghost.ts`) squashes on contact,
stretches along fast moves, winds up before every strike, travels on arcs, and shows its height as
a contact glow. Controls squash when struck; the knob counter-turns before it turns and settles
past true focus; the fader dips before it rises and overshoots on return. The cell squashes and
stretches on each pulse, gathers itself before it divides, glows with its heartbeat in the dark,
and its nucleus looks toward whatever is being played. The camera stages one control at a time.

A ghost hand performs on the real Hyle Tactile Kit components (synthetic pointer events into
their own handlers). Every control carries an authored HAfx feel — a sound voice plus haptic
primitives — bound to that control's `hy-input` / `hy-change`, so the performance and a person's
own hand feel identical. When the performance ends the instrument is live.

| Control (Hyle)        | Feel (`src/feels.ts`)                                        |
| --------------------- | ------------------------------------------------------------ |
| `hy-crater` power     | `power` — heavy latch + supply spin-up                       |
| `hy-toggle` smooth    | `lamp` — lever travel, sprung snap, spring ring (two stages) |
| `hy-knob` precision   | `detent` — pitch climbs with value; a notch at true focus    |
| `hy-fader`            | `friction` / `ridge` / `stop` — speed-following rail         |
| `hy-joystick`         | `tension` / `gate` / `release` — spring wobble on return     |
| `hy-voice`            | `bead` per detent; `pulse` shaped by all five axes           |
| `hy-crater` record    | `rec`, then `mitosis` (stretch, creaks, wet pop)             |
| `hy-transport` play   | `groove` — three haptic characters in one bar                |
| finale                | `reveal` — dot-matrix arpeggio into one big hit              |

Output paths (`src/feel.ts`): one 1 kHz haptic bus feeds the phone motor (vibrate PWM), controller
dual-rumble, iOS taptic ticks, a felt-bass "body" layer, and on-screen light/shake.

Build: `HYLE=/path/to/hyle-design-system node build.mjs` (needs Hyle's `feat/driven-kit`:
`hy-voice`, driven meters, LED text, `setKitFeel`). Output: `dist/hafx-studio-intro.html`.
