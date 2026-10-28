// The HAfx Studio feel library: one authored feel per Hyle control.
// Each feel = sound voice + HAfx haptic primitives, played at the same instant.
// These are the reusable examples: bind them to a control's hy-input / hy-change.
import { Feel, type Voice } from './feel';

const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a: number, b: number, u: number) => a + (b - a) * u;

export class Feels {
  constructor(private f: Feel) {}

  /** POWER crater — a heavy latch, then the instrument's lamp supply spinning up. */
  power(on: boolean, at = this.f.now) {
    const f = this.f;
    f.hiss(at, 'highpass', 2400, 0.8, 0.22, 0.0005, 0.012);           // latch tooth
    f.glide(at, on ? 96 : 70, on ? 44 : 32, 0.14, 0.75, 0.22);       // the button bottoming out
    f.hiss(at + 0.004, 'lowpass', 500, 1, 0.3, 0.001, 0.07);
    f.bus.transient(at, 0.95, 0.18);
    if (on) {
      // supply whine rising from mains hum
      const o = f.osc('sawtooth', 50), o2 = f.osc('sine', 100), lp = f.filt('lowpass', 260, 2), e = f.gain();
      o.frequency.setValueAtTime(50, at + 0.05); o.frequency.exponentialRampToValueAtTime(64, at + 0.9);
      o.connect(lp); o2.connect(lp); lp.connect(e).connect(f.out);
      e.gain.setValueAtTime(0.0001, at + 0.05); e.gain.exponentialRampToValueAtTime(0.06, at + 0.45); e.gain.exponentialRampToValueAtTime(0.0001, at + 1.5);
      f.play(o, at + 0.05, 1.5); f.play(o2, at + 0.05, 1.5);
      f.bus.continuous(at + 0.06, 0.9, (u) => 0.32 * Math.sin(Math.PI * Math.min(1, u * 1.6)) * (1 - u * 0.6), () => 0.08);
    }
  }

  /** LAMP toggle — lever travel, a sprung snap, the spring ringing out. Two felt stages. */
  lamp(on: boolean, at = this.f.now) {
    const f = this.f, snap = at + 0.028;
    f.hiss(at, 'bandpass', on ? 2600 : 2000, 3, 0.07, 0.004, 0.02);   // lever scraping over its detent
    f.bus.transient(at, 0.32, 0.85);
    [2350, 3720, 5130].forEach((p, i) => f.ping(snap, p * (on ? 1 : 0.86), [0.11, 0.07, 0.04][i], [0.07, 0.05, 0.03][i]));
    f.hiss(snap, 'highpass', 4000, 0.7, 0.16, 0.0005, 0.01);
    f.hiss(snap, 'bandpass', 1150, 18, 0.22, 0.001, 0.16);               // the spring
    f.bus.transient(snap, 0.85, 0.62);
    f.bus.continuous(snap + 0.01, 0.08, (u) => 0.18 * (1 - u), () => 0.92); // spring buzz
    if (on) f.ping(snap + 0.05, 7900, 0.012, 0.25);                     // filament tink
  }

  /** FOCUS knob detent — a resonant tick whose pitch climbs with the value; a deeper notch at true focus. */
  detent(v: number, speed: number, notch: boolean, at = this.f.now) {
    const f = this.f, k = clamp(speed);
    f.ping(at, lerp(950, 2600, v), 0.06 + 0.06 * k, 0.03, f.out, 0.3);
    f.hiss(at, 'highpass', 5200, 0.9, 0.05 + 0.05 * k, 0.0004, 0.006);
    f.bus.transient(at, 0.3 + 0.35 * k, lerp(0.45, 0.9, v));
    if (notch) {
      f.glide(at + 0.004, 240, 150, 0.05, 0.22, 0.09);
      f.hiss(at, 'bandpass', 700, 2, 0.12, 0.001, 0.05);
      f.bus.transient(at + 0.004, 0.8, 0.3);
    }
  }

  /** ZOOM fader — the cap riding its rail: friction that follows speed, ridges you feel, a hard stop. */
  private fr?: { n: AudioBufferSourceNode; bp: BiquadFilterNode; g: GainNode };
  friction(pos: number, vel: number, at = this.f.now) {
    const f = this.f;
    if (!this.fr) {
      const n = f.noise(), bp = f.filt('bandpass', 800, 2.5), g = f.gain(0);
      n.connect(bp).connect(g).connect(f.out); n.start();
      this.fr = { n, bp, g };
    }
    const sp = clamp(Math.abs(vel) * 0.9);
    this.fr.bp.frequency.setTargetAtTime(lerp(380, 2600, pos), at, 0.02);
    this.fr.g.gain.setTargetAtTime(0.2 * sp, at, 0.015);
    this.fr.g.gain.setTargetAtTime(0, at + 0.06, 0.05);
    f.bus.continuous(at, 0.05, () => 0.14 + 0.32 * sp, () => 0.35);
  }
  ridge(pos: number, at = this.f.now) {
    this.f.hiss(at, 'bandpass', lerp(1400, 3600, pos), 6, 0.05, 0.0005, 0.012);
    this.f.bus.transient(at, 0.22, 0.75);
  }
  stop(top: boolean, at = this.f.now) {
    const f = this.f;
    f.glide(at, top ? 170 : 120, top ? 82 : 60, 0.06, 0.45, 0.12);
    f.hiss(at, 'lowpass', 900, 1, 0.3, 0.0005, 0.05);
    f.ping(at, top ? 3100 : 2500, 0.05, 0.04);
    f.bus.transient(at, 0.92, 0.38);
  }

  /** JOYSTICK — spring tension that tightens with travel, a clunk at the gate, a wobbling return. */
  private js?: { o: OscillatorNode; g: GainNode; lp: BiquadFilterNode };
  tension(mag: number, at = this.f.now) {
    const f = this.f;
    if (!this.js) {
      const o = f.osc('triangle', 110), lp = f.filt('lowpass', 700, 3), g = f.gain(0);
      o.connect(lp).connect(g).connect(f.out); o.start(); this.js = { o, g, lp };
    }
    this.js.o.frequency.setTargetAtTime(90 + 230 * mag, at, 0.03);
    this.js.lp.frequency.setTargetAtTime(500 + 1600 * mag, at, 0.03);
    this.js.g.gain.setTargetAtTime(mag > 0.02 ? 0.015 + 0.05 * mag : 0, at, 0.04);
    f.bus.continuous(at, 0.06, () => 0.12 + 0.55 * Math.pow(mag, 1.5), () => 0.2 + 0.5 * mag);
  }
  gate(at = this.f.now) {
    this.f.hiss(at, 'bandpass', 1800, 4, 0.14, 0.0005, 0.03);
    this.f.glide(at, 260, 160, 0.03, 0.18, 0.06);
    this.f.bus.transient(at, 0.75, 0.72);
  }
  release(at = this.f.now) {
    const f = this.f;
    if (this.js) this.js.g.gain.setTargetAtTime(0, at, 0.02);
    const o = f.osc('sine', 230), vib = f.osc('sine', 17), vg = f.gain(26), e = f.gain();
    vib.connect(vg).connect(o.frequency); o.frequency.setValueAtTime(230, at); o.frequency.exponentialRampToValueAtTime(105, at + 0.35);
    vg.gain.setValueAtTime(26, at); vg.gain.exponentialRampToValueAtTime(0.5, at + 0.4);
    o.connect(e).connect(f.out); f.perc(e, at, 0.16, 0.003, 0.42); f.play(o, at, 0.45); f.play(vib, at, 0.45);
    [0, 0.058, 0.116, 0.174, 0.232].forEach((d, i) => f.bus.transient(at + d, [0.7, 0.46, 0.3, 0.18, 0.1][i], 0.5));
  }

  /** VOICE step — a glass bead per detent; each axis has its own pitch row. */
  bead(row: number, act: number, at = this.f.now) {
    const scale = [523.25, 587.33, 659.25, 783.99, 880.0];
    const fq = scale[(row + act) % 5] * (act >= 2 ? 2 : 1);
    this.f.ping(at, fq, 0.07, 0.22, this.f.out, 0.35);
    this.f.ping(at, fq, 0.03, 0.4, this.f.wet);
    this.f.bus.transient(at, 0.38, 0.5 + 0.12 * act);
  }

  /**
   * The cell's PULSE, shaped by the voice (the HAfx moment: you are designing a haptic).
   *  warm↔bright  → filter cutoff, haptic sharpness
   *  soft↔hard    → attack time + click layer, transient vs swell
   *  dry↔wet      → reverb send
   *  narrow↔wide  → stereo spread of two detuned bodies
   *  clean↔driven → saturation, and a rough buzz in the haptic
   */
  pulse(v: Voice, at = this.f.now, amt = 1) {
    const f = this.f, [w, h, d, n, c] = v.map((x) => clamp((x - 0.2) / 0.6));
    const lp = f.filt('lowpass', 260 + 4200 * w * w, 0.9 + 1.4 * h), drive = f.shaper(0.05 + c * 1.6), post = f.gain(1 / (1 + c * 0.9));
    const dry = f.gain(1), wet = f.gain(d * 0.7);
    lp.connect(drive).connect(post); post.connect(dry).connect(f.out); post.connect(wet).connect(f.wet);
    const att = lerp(0.03, 0.0015, h);
    [-1, 1].forEach((side) => {
      const p = f.pan(side * n * 0.85);
      p.connect(lp);
      [[58, 0.55], [116, 0.22], [174, 0.08]].forEach(([fq, g]) => {
        const o = f.osc('sine', fq * (1 + side * n * 0.012)), e = f.gain();
        o.frequency.setValueAtTime(fq * 1.35, at); o.frequency.exponentialRampToValueAtTime(fq, at + 0.06);
        o.connect(e).connect(p); f.perc(e, at, g * amt * 0.7, att, 0.22 + 0.15 * (1 - h)); f.play(o, at, 0.5);
        // dub: the second, softer beat
        const o2 = f.osc('sine', fq * 0.94), e2 = f.gain(); o2.connect(e2).connect(p);
        f.perc(e2, at + 0.17, g * amt * 0.42, att * 1.5, 0.2); f.play(o2, at + 0.17, 0.4);
      });
    });
    if (h > 0.05) f.hiss(at, 'bandpass', 1800 + 3000 * w, 1.5, 0.12 * h * amt, 0.0005, 0.018, lp);
    const S = lerp(0.12, 0.88, w), I = lerp(0.5, 0.95, h) * amt;
    if (h > 0.45) f.bus.transient(at, I, S); else f.bus.continuous(at, 0.11, (u) => I * Math.sin(Math.PI * u), () => S);
    f.bus.transient(at + 0.17, I * 0.55, S * 0.8);
    if (c > 0.1) f.bus.continuous(at + 0.01, 0.14, (u) => 0.32 * c * amt * (1 - u) * (0.6 + 0.4 * Math.sin(u * 90)), () => 0.95);
  }

  /** REC crater — a sunken key, then a two-note arm chime. */
  rec(at = this.f.now) {
    const f = this.f;
    f.hiss(at, 'bandpass', 2200, 1.5, 0.18, 0.0005, 0.02); f.glide(at, 180, 90, 0.04, 0.3, 0.07);
    f.bus.transient(at, 0.55, 0.7);
    const sq = (t: number, fq: number) => { const o = f.osc('square', fq), lp = f.filt('lowpass', 3200), e = f.gain(); o.connect(lp).connect(e).connect(f.out); f.perc(e, t, 0.06, 0.001, 0.09); f.play(o, t, 0.1); };
    sq(at + 0.09, 880); sq(at + 0.15, 1318.5);
    f.bus.transient(at + 0.09, 0.32, 0.92); f.bus.transient(at + 0.15, 0.32, 0.98);
  }

  /** MITOSIS — membrane stretching (rubbery, creaking), the furrow closing, a wet pop, daughters settling. */
  mitosis(at: number, dur = 1.5) {
    const f = this.f, end = at + dur;
    const car = f.osc('sine', 150), mod = f.osc('sine', 6), md = f.gain(0), e = f.gain(0.0001), lp = f.filt('lowpass', 900, 3);
    mod.connect(md).connect(car.frequency); car.frequency.setValueAtTime(150, at); car.frequency.exponentialRampToValueAtTime(330, end);
    mod.frequency.setValueAtTime(5, at); mod.frequency.linearRampToValueAtTime(16, end);
    md.gain.setValueAtTime(4, at); md.gain.linearRampToValueAtTime(48, end);
    car.connect(lp).connect(e).connect(f.out); e.connect(f.wet);
    e.gain.setValueAtTime(0.0001, at); e.gain.exponentialRampToValueAtTime(0.16, end - 0.05); e.gain.linearRampToValueAtTime(0.0001, end);
    lp.frequency.setValueAtTime(500, at); lp.frequency.exponentialRampToValueAtTime(2600, end);
    f.play(car, at, dur); f.play(mod, at, dur);
    // creaks: sparse grains, accelerating
    let t = at + 0.12, k = 0;
    while (t < end - 0.06) {
      const u = (t - at) / dur;
      f.hiss(t, 'bandpass', 700 + 1900 * u + Math.random() * 400, 9, 0.05 + 0.06 * u, 0.0006, 0.012);
      f.bus.transient(t, 0.12 + 0.2 * u, 0.6);
      t += lerp(0.12, 0.028, u) * (0.75 + 0.5 * ((k++ * 0.618) % 1));
    }
    f.bus.continuous(at, dur, (u) => 0.08 + 0.6 * u * u, (u) => 0.2 + 0.45 * u);
    // pop
    f.hiss(end, 'bandpass', 1300, 1.2, 0.5, 0.0005, 0.05); f.glide(end, 320, 70, 0.07, 0.6, 0.14);
    f.glide(end + 0.012, 1400, 2300, 0.03, 0.08, 0.05); f.hiss(end, 'lowpass', 600, 0.8, 0.25, 0.001, 0.2, f.wet);
    f.bus.transient(end, 1.0, 0.68);
    f.glide(end + 0.13, 520, 380, 0.05, 0.08, 0.08); f.bus.transient(end + 0.13, 0.42, 0.42);
    f.glide(end + 0.22, 470, 350, 0.05, 0.05, 0.08); f.bus.transient(end + 0.22, 0.26, 0.42);
    return end;
  }

  /** PLAY — a bar of the daughters' rhythm: heavy beats, membrane snaps, cilia ticks. Three haptic characters. */
  groove(at: number, v: Voice, bpm = 124, onHit?: (kind: 'K' | 'S' | 't', t: number) => void) {
    const f = this.f, step = 60 / bpm / 4;
    const pat = 'K.t.S.tKK.t.S.tt';
    [...pat].forEach((ch, i) => {
      const t = at + i * step;
      if (ch === 'K') { this.pulse(v, t, i === 0 ? 1 : 0.8); onHit?.('K', t); }
      if (ch === 'S') { f.hiss(t, 'bandpass', 1500, 1.1, 0.3, 0.001, 0.09); f.glide(t, 260, 150, 0.04, 0.22, 0.09); f.hiss(t, 'highpass', 3000, 0.7, 0.08, 0.001, 0.16, f.wet); f.bus.transient(t, 0.62, 0.58); onHit?.('S', t); }
      if (ch === 't') { f.hiss(t, 'highpass', 7200, 1, 0.07, 0.0005, 0.025); f.ping(t, 5200, 0.015, 0.02); f.bus.transient(t, 0.24, 0.96); onHit?.('t', t); }
    });
    return at + pat.length * step;
  }

  /** REVEAL — a dot-matrix arpeggio climbing into the HAfx Studio chord and one big felt hit. */
  reveal(at: number, steps: number, stepDur: number) {
    const f = this.f, notes = [293.66, 369.99, 440, 554.37, 659.25, 739.99, 880, 1108.73];
    for (let i = 0; i < steps; i++) {
      const t = at + i * stepDur, fq = notes[i % notes.length] * (i >= notes.length ? 2 : 1);
      const o = f.osc('triangle', fq), e = f.gain(); o.connect(e).connect(f.out); e.connect(f.wet); f.perc(e, t, 0.045, 0.001, 0.14); f.play(o, t, 0.16);
      f.bus.transient(t, 0.16 + 0.02 * (i % 4), 0.92);
    }
    const hit = at + steps * stepDur + 0.04;
    f.glide(hit, 110, 34, 0.5, 0.75, 0.9); f.hiss(hit, 'lowpass', 1500, 0.9, 0.35, 0.001, 0.35);
    [146.83, 220, 293.66, 329.63, 440, 554.37].forEach((fq, i) => {
      const o = f.osc('sawtooth', fq * (1 + (i % 2 ? 0.003 : -0.003))), lp = f.filt('lowpass', 2000, 1), e = f.gain();
      o.connect(lp).connect(e); e.connect(f.out); e.connect(f.wet);
      e.gain.setValueAtTime(0.0001, hit); e.gain.linearRampToValueAtTime(0.045, hit + 0.01); e.gain.setTargetAtTime(0.0001, hit + 0.3, 0.6);
      lp.frequency.setValueAtTime(3200, hit); lp.frequency.setTargetAtTime(700, hit + 0.1, 0.5);
      f.play(o, hit, 3.2);
    });
    f.ping(hit, 1760, 0.03, 2.4, f.wet); f.ping(hit, 2349.3, 0.02, 2.6, f.wet);
    f.bus.transient(hit, 1.0, 0.55);
    f.bus.continuous(hit + 0.02, 0.9, (u) => 0.7 * Math.pow(1 - u, 1.6), () => 0.14);
    return hit;
  }

  /** the daughters' membrane snap (the groove's backbeat) */
  snap(at: number, amt = 1) {
    const f = this.f;
    f.hiss(at, 'bandpass', 1500, 1.1, 0.3 * amt, 0.001, 0.09); f.glide(at, 260, 150, 0.04, 0.22 * amt, 0.09); f.hiss(at, 'highpass', 3000, 0.7, 0.08 * amt, 0.001, 0.16, f.wet);
    f.bus.transient(at, 0.62 * amt, 0.58);
  }
  /** cilia flick — the smallest, sharpest thing you can feel */
  cilia(at: number, amt = 1) {
    this.f.hiss(at, 'highpass', 7200, 1, 0.07 * amt, 0.0005, 0.025); this.f.ping(at, 5200, 0.015 * amt, 0.02);
    this.f.bus.transient(at, 0.24 * amt, 0.96);
  }

  /** transport key */
  key(at = this.f.now) {
    this.f.hiss(at, 'bandpass', 2600, 1.3, 0.16, 0.0005, 0.018); this.f.ping(at, 3400, 0.03, 0.02);
    this.f.bus.transient(at, 0.45, 0.82);
  }
}
