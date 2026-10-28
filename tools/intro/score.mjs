// HAfx intro sting — the single score. Everything (web player, Android, MP4) plays THIS pattern.
// Output is the native HAfx IR (HapticAudioPattern JSON, kotlinx "type" discriminators,
// encodeDefaults=false conventions), so the Kotlin core decodes it unchanged.

const D = {
  D2: 73.42, A2: 110.0, D3: 146.83, A3: 220.0, D4: 293.66, E4: 329.63, F4: 349.23,
  A4: 440.0, C5: 523.25, D5: 587.33, E5: 659.25, F5: 698.46, A5: 880.0, C6: 1046.5, D6: 1174.66,
};

export const ACTS = {
  nucleus: 0.0, mitosis: 1.6, rise: 3.45, vacuum: 5.55, impact: 5.8, echo: 6.3, settle: 8.3, end: 10.0,
};
export const HEARTBEATS = [0.25, 0.47, 1.05, 1.27];
export const DIVISIONS = [1.6, 2.05, 2.42, 2.72, 2.96, 3.15, 3.3, 3.42]; // 1 -> 256 cells
export const ECHOES = [6.32, 6.76, 7.14, 7.46];
export const CHIME = [8.6, 8.68];

const r3 = (x) => Math.round(x * 1000) / 1000;
const env = (attack = 0, decay = 0, sustain = 1, release = 0) => {
  const e = {};
  if (attack) e.attack = r3(attack);
  if (decay) e.decay = r3(decay);
  if (sustain !== 1) e.sustain = r3(sustain);
  if (release) e.release = r3(release);
  return e;
};
const transient = (time, intensity, sharpness) => ({ type: 'transient', time: r3(time), intensity: r3(intensity), sharpness: r3(sharpness) });
const continuous = (time, duration, intensity, sharpness, e) => ({ type: 'continuous', time: r3(time), duration: r3(duration), intensity: r3(intensity), sharpness: r3(sharpness), envelope: e });
// Master trim: the whole score is scaled so the raw (un-limited) mix peaks near 0.9 in the Kotlin
// renderer, which sums tracks and hard-clamps. Measured, not guessed — see probe-audio.mjs.
export const MASTER = 0.86;
const osc = (time, duration, waveform, frequencyHz, gain, e, filter) => {
  const o = { type: 'osc', time: r3(time), duration: r3(duration), waveform, frequencyHz: r3(frequencyHz), gain: r3(gain * MASTER), envelope: e };
  if (filter) o.filter = filter;
  return o;
};
const curve = (parameter, points, interpolation = 'SMOOTH') => ({
  parameter, points: points.map(([time, value]) => ({ time: r3(time), value: r3(value) })), ...(interpolation === 'LINEAR' ? {} : { interpolation }),
});
const htrack = (id, name, events, curves) => ({ type: 'haptic', id, name, events, ...(curves ? { curves } : {}) });
const atrack = (id, name, events, curves) => ({ type: 'audio', id, name, events, ...(curves ? { curves } : {}) });

export function buildIntro() {
  // ---- Act 1 · nucleus: lub-dub heartbeat in the dark ---------------------------------------
  const heart = HEARTBEATS.map((t, i) => transient(t, i % 2 ? 0.55 : 0.85, i % 2 ? 0.1 : 0.18));
  const heartAudio = HEARTBEATS.flatMap((t, i) => [
    osc(t, 0.1, 'SINE', 52, i % 2 ? 0.34 : 0.5, env(0.002, 0, 1, 0.2)),
    osc(t, 0.05, 'TRIANGLE', 104, i % 2 ? 0.09 : 0.14, env(0.002, 0, 1, 0.1)),
  ]);

  // ---- Act 2 · mitosis: each division is a felt tick + a rising pluck -------------------------
  const notes = [D.D4, D.F4, D.A4, D.C5, D.D5, D.F5, D.A5, D.C6];
  const mitosis = DIVISIONS.map((t, k) => transient(t, 0.38 + 0.07 * k, 0.3 + 0.09 * k));
  const plucks = DIVISIONS.flatMap((t, k) => [
    osc(t, 0.05, 'TRIANGLE', notes[k], 0.26, env(0.002, 0.03, 0.4, 0.28), { type: 'LOWPASS', cutoffHz: 3200, resonance: 2.0 }),
    osc(t, 0.04, 'SINE', notes[k] * 2, 0.07, env(0.001, 0, 1, 0.5)),
    osc(t, 0.06, 'SINE', 60 + 6 * k, 0.22, env(0.002, 0, 1, 0.09)), // body under each split
  ]);

  // ---- Act 3 · rise: the organism winds up ---------------------------------------------------
  const riseHaptic = [continuous(ACTS.rise, 2.1, 1.0, 0.5, env(0.05, 0, 1, 0.02))];
  const riseCurves = [
    curve('HAPTIC_INTENSITY', [[3.45, 0.08], [4.4, 0.3], [5.1, 0.7], [5.5, 1.0], [5.55, 1.0]]),
    curve('HAPTIC_SHARPNESS', [[3.45, 0.15], [5.55, 0.85]]),
  ];
  const riserAudio = [
    osc(ACTS.rise, 2.1, 'SAW', D.D2, 0.2, env(0.4, 0, 1, 0.02), { type: 'LOWPASS', cutoffHz: 200, resonance: 4.0 }),
    osc(ACTS.rise, 2.1, 'SAW', D.D2 * 1.006, 0.16, env(0.4, 0, 1, 0.02), { type: 'LOWPASS', cutoffHz: 200, resonance: 4.0 }),
  ];
  const riserCurves = [
    curve('AUDIO_FILTER_CUTOFF', [[3.45, 180], [4.6, 700], [5.3, 3200], [5.55, 6500]]),
    curve('AUDIO_PITCH', [[3.45, 0], [5.55, 12]]),
  ];
  const washAudio = [osc(ACTS.rise, 2.1, 'NOISE', 1, 0.2, env(1.2, 0, 1, 0.02), { type: 'BANDPASS', cutoffHz: 500, resonance: 1.2 })];
  const washCurves = [curve('AUDIO_FILTER_CUTOFF', [[3.45, 400], [5.55, 9000]])];
  const padAudio = [
    osc(0.0, 5.5, 'TRIANGLE', D.D3, 0.16, env(1.4, 0, 1, 0.05), { type: 'LOWPASS', cutoffHz: 400 }),
    osc(0.0, 5.5, 'TRIANGLE', D.A3, 0.1, env(2.2, 0, 1, 0.05), { type: 'LOWPASS', cutoffHz: 400 }),
  ];
  const padCurves = [curve('AUDIO_FILTER_CUTOFF', [[0, 260], [3.4, 700], [5.55, 2600]])];

  // ---- Act 4 · vacuum (5.55–5.80): silence on every channel. Then IMPACT. ---------------------
  const T = ACTS.impact;
  const impactHaptic = [
    transient(T, 1.0, 0.6),
    continuous(T + 0.02, 0.9, 0.9, 0.12, env(0.004, 0.32, 0.35, 0.55)),
  ];
  const dropAudio = [osc(T, 0.75, 'SINE', 110, 0.6, env(0.002, 0, 1, 0.6))];
  const dropCurves = [curve('AUDIO_PITCH', [[T, 0], [T + 0.08, -10], [T + 0.7, -24]])];
  const impactAudio = [
    osc(T, 0.12, 'NOISE', 1, 0.36, env(0.001, 0.05, 0.3, 0.45), { type: 'LOWPASS', cutoffHz: 1600, resonance: 0.9 }),
    osc(T, 0.03, 'NOISE', 1, 0.22, env(0.0005, 0, 1, 0.06), { type: 'HIGHPASS', cutoffHz: 3000 }),
    // open sus2 bloom — the "logo chord"
    ...[D.D3, D.A3, D.D4, D.E4, D.A4].map((f, i) => osc(T, 1.8, 'SAW', f, 0.05 + (i === 0 ? 0.03 : 0), env(0.006, 0.4, 0.55, 1.7), { type: 'LOWPASS', cutoffHz: 2200, resonance: 1.1 })),
    osc(T, 1.2, 'SINE', D.A5, 0.06, env(0.01, 0, 1, 2.2)),
    osc(T, 1.2, 'SINE', D.D6, 0.045, env(0.02, 0, 1, 2.4)),
  ];

  // ---- Act 5 · echoes: aftershocks rippling through the wordmark --------------------------
  const echoGains = [0.16, 0.1, 0.06, 0.036];
  const echoHaptic = ECHOES.map((t, i) => transient(t, [0.6, 0.4, 0.26, 0.16][i], 0.35 + 0.12 * i));
  const echoAudio = ECHOES.flatMap((t, i) => [
    osc(t, 0.04, 'TRIANGLE', D.D5, echoGains[i], env(0.002, 0, 1, 0.35), { type: 'LOWPASS', cutoffHz: 2400 }),
    osc(t, 0.05, 'SINE', 58, echoGains[i] * 1.6, env(0.002, 0, 1, 0.12)),
  ]);

  // ---- Act 6 · settle: the confirm blip under the tagline ---------------------------------
  const chimeHaptic = [transient(CHIME[0], 0.45, 0.95), transient(CHIME[1], 0.3, 1.0)];
  const chimeAudio = [
    osc(CHIME[0], 0.07, 'SQUARE', D.E5, 0.09, env(0.001, 0, 1, 0.06), { type: 'LOWPASS', cutoffHz: 3600 }),
    osc(CHIME[1], 0.12, 'SQUARE', D.A5, 0.09, env(0.001, 0, 1, 0.3), { type: 'LOWPASS', cutoffHz: 3600 }),
  ];

  return {
    name: 'HAfx · Intro',
    tracks: [
      htrack('h-pulse', 'Pulse', [...heart, ...mitosis, ...impactHaptic, ...echoHaptic, ...chimeHaptic].sort((a, b) => a.time - b.time)),
      htrack('h-rise', 'Rise', riseHaptic, riseCurves),
      atrack('a-heart', 'Heart', heartAudio),
      atrack('a-pluck', 'Mitosis', plucks),
      atrack('a-pad', 'Pad', padAudio, padCurves),
      atrack('a-riser', 'Riser', riserAudio, riserCurves),
      atrack('a-wash', 'Wash', washAudio, washCurves),
      atrack('a-drop', 'Drop', dropAudio, dropCurves),
      atrack('a-impact', 'Impact', impactAudio),
      atrack('a-echo', 'Echo', echoAudio),
      atrack('a-chime', 'Chime', chimeAudio),
    ],
    metadata: {
      'intro.duration': '10.0',
      'intro.acts': Object.entries(ACTS).map(([k, v]) => `${k}:${v}`).join(','),
      'intro.divisions': DIVISIONS.join(','),
      'intro.heartbeats': HEARTBEATS.join(','),
      'intro.echoes': ECHOES.join(','),
      'intro.chime': CHIME.join(','),
      author: 'A System of Cells',
    },
  };
}
