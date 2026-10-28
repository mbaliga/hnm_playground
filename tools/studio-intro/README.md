# HAfx Studio intro — the Tactile Kit, played

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
