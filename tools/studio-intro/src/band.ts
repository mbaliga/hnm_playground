// The band under the instrument: drone, bass, pad (side-chained to the cell's pulse),
// hats, membrane claps, risers, air. Built on the same Feel engine, so anything that
// should be felt (claps, risers) lands on the haptic bus too. All methods take an
// absolute AudioContext time; the conductor schedules them ahead on the beat grid.
import { Feel } from './feel';

export const N = {
  A1: 55, Bb1: 58.27, C2: 65.41, D2: 73.42, F2: 87.31, A2: 110, Bb2: 116.54, C3: 130.81, Cs3: 138.59, D3: 146.83,
  E3: 164.81, F3: 174.61, G3: 196, A3: 220, C4: 261.63, D4: 293.66,
};
export const CHORDS: Record<string, { root: number; pad: number[] }> = {
  Dm: { root: N.D2, pad: [N.D3, N.F3, N.A3] },
  Bb: { root: N.Bb1, pad: [N.Bb2, N.D3, N.F3] },
  F: { root: N.F2, pad: [N.F3, N.A3, N.C4] },
  C: { root: N.C2, pad: [N.C3, N.E3, N.G3] },
  A: { root: N.A1, pad: [N.A2, N.Cs3, N.E3] },
};

export class Band {
  padBus: GainNode;      // pad → padBus (ducked by the pulse) → out + wet
  bassBus: GainNode;
  constructor(private f: Feel) {
    this.padBus = f.gain(1); this.padBus.connect(f.out); const s = f.gain(0.5); this.padBus.connect(s).connect(f.wet);
    this.bassBus = f.gain(1); this.bassBus.connect(f.out);
  }

  /** side-chain pump: the pad and bass breathe around every pulse */
  duck(t: number, depth = 0.65) {
    for (const g of [this.padBus.gain, this.bassBus.gain]) {
      g.setValueAtTime(1, t); g.linearRampToValueAtTime(1 - depth, t + 0.012); g.setTargetAtTime(1, t + 0.05, 0.09);
    }
  }

  /** low D and A in the dark, breathing; the air thickens toward the downbeat */
  drone(t0: number, t1: number) {
    const f = this.f, e = f.gain(0.0001), lp = f.filt('lowpass', 180, 1.2);
    [[N.D2, 0.5], [N.A2, 0.22], [N.D3, 0.08]].forEach(([fq, g]) => { const o = f.osc('sine', fq), og = f.gain(g); o.connect(og).connect(lp); f.play(o, t0, t1 - t0 + 0.6); });
    // tremolo on its own stage, so it scales the swell instead of overriding it
    const trem = f.gain(0.75), lfo = f.osc('sine', 0.5), lg = f.gain(0.25); lfo.connect(lg).connect(trem.gain); f.play(lfo, t0, t1 - t0 + 0.6);
    lp.connect(trem).connect(e).connect(f.out);
    e.gain.setValueAtTime(0.0001, t0); e.gain.exponentialRampToValueAtTime(0.16, t1 - 0.2); e.gain.setTargetAtTime(0.0001, t1, 0.25);
    lp.frequency.setValueAtTime(140, t0); lp.frequency.exponentialRampToValueAtTime(420, t1);
    this.air(t0, t1, 0.025);
  }

  /** filtered noise that swells toward a moment */
  air(t0: number, t1: number, peak: number, felt = 0) {
    const f = this.f, n = f.noise(), bp = f.filt('bandpass', 600, 0.8), e = f.gain(0.0001);
    n.connect(bp).connect(e).connect(f.out); e.connect(f.wet);
    bp.frequency.setValueAtTime(500, t0); bp.frequency.exponentialRampToValueAtTime(7000, t1);
    e.gain.setValueAtTime(0.0001, t0); e.gain.exponentialRampToValueAtTime(peak, t1 - 0.02); e.gain.linearRampToValueAtTime(0.0001, t1 + 0.01);
    f.play(n, t0, t1 - t0 + 0.05);
    if (felt) f.bus.continuous(t0, t1 - t0, (u) => felt * u * u, (u) => 0.2 + 0.6 * u);
  }

  /** a riser: two detuned saws climbing an octave through an opening filter */
  riser(t0: number, t1: number, root = N.A2) {
    const f = this.f, lp = f.filt('lowpass', 300, 5), e = f.gain(0.0001);
    [1, 1.007].forEach((d) => { const o = f.osc('sawtooth', root * d); o.frequency.setValueAtTime(root * d, t0); o.frequency.exponentialRampToValueAtTime(root * 2 * d, t1); o.connect(lp); f.play(o, t0, t1 - t0); });
    lp.frequency.setValueAtTime(300, t0); lp.frequency.exponentialRampToValueAtTime(5200, t1);
    lp.connect(e).connect(f.out); e.connect(f.wet);
    e.gain.setValueAtTime(0.0001, t0); e.gain.exponentialRampToValueAtTime(0.1, t1 - 0.03); e.gain.linearRampToValueAtTime(0.0001, t1);
    this.air(t0, t1, 0.08, 0.35);
  }

  bass(t: number, fq: number, dur: number, vel = 1) {
    const f = this.f, o = f.osc('sawtooth', fq), sub = f.osc('sine', fq), lp = f.filt('lowpass', 900, 3), e = f.gain();
    o.connect(lp); const sg = f.gain(0.6); sub.connect(sg).connect(e); lp.connect(e); e.connect(this.bassBus);
    lp.frequency.setValueAtTime(1500 * vel, t); lp.frequency.exponentialRampToValueAtTime(240, t + Math.min(dur, 0.3));
    const pk = 0.1 * vel;
    e.gain.setValueAtTime(0.0001, t); e.gain.linearRampToValueAtTime(pk, t + 0.006); e.gain.setTargetAtTime(pk * 0.45, t + 0.02, 0.09);
    e.gain.setValueAtTime(pk * 0.45, t + dur * 0.85); e.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    f.play(o, t, dur); f.play(sub, t, dur);
  }

  pad(t: number, chord: number[], dur: number, vel = 1, attack = 0.25) {
    const f = this.f, lp = f.filt('lowpass', 700, 0.9), e = f.gain();
    chord.forEach((fq) => [-0.006, 0.006].forEach((d, i) => { const o = f.osc('sawtooth', fq * (1 + d)), p = f.pan(i ? 0.4 : -0.4); o.connect(p).connect(lp); f.play(o, t, dur + 0.8); }));
    lp.connect(e).connect(this.padBus);
    lp.frequency.setValueAtTime(500, t); lp.frequency.linearRampToValueAtTime(900 + 900 * vel, t + dur * 0.6);
    e.gain.setValueAtTime(0.0001, t); e.gain.linearRampToValueAtTime(0.028 * vel, t + attack); e.gain.setValueAtTime(0.028 * vel, t + dur); e.gain.exponentialRampToValueAtTime(0.0001, t + dur + 0.7);
  }

  hat(t: number, vel = 1, open = false) {
    const f = this.f, pan = f.pan(Math.sin(t * 7.3) * 0.35);
    pan.connect(f.out);
    f.hiss(t, 'highpass', open ? 6500 : 8200, 0.9, (open ? 0.06 : 0.045) * vel, 0.0005, open ? 0.14 : 0.03, pan);
  }

  /** the membrane clap: three tight bursts and a wet tail */
  clap(t: number, vel = 1) {
    const f = this.f;
    [0, 0.009, 0.019].forEach((d) => f.hiss(t + d, 'bandpass', 1450, 1.4, 0.16 * vel, 0.0005, 0.012));
    f.hiss(t + 0.02, 'bandpass', 1300, 1, 0.12 * vel, 0.001, 0.12);
    f.hiss(t + 0.02, 'highpass', 2500, 0.7, 0.06 * vel, 0.002, 0.22, f.wet);
    f.bus.transient(t, 0.42 * vel, 0.6);
  }

  crash(t: number) {
    const f = this.f;
    f.hiss(t, 'highpass', 4200, 0.6, 0.13, 0.001, 1.6, f.out);
    f.hiss(t, 'highpass', 3000, 0.6, 0.08, 0.002, 2.2, f.wet);
  }

  /** everything ducks out for the beat before a drop (the breath before the hit) */
  vacuum(t0: number, t1: number) {
    for (const g of [this.padBus.gain, this.bassBus.gain]) { g.setTargetAtTime(0.0001, t0, 0.03); g.setTargetAtTime(1, t1, 0.01); }
  }
}
