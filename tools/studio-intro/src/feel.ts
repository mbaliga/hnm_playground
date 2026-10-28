// HAfx feel engine — sound + touch, from one event.
//
// Every feel is authored as HAfx haptic primitives (transient: intensity+sharpness;
// continuous: intensity/sharpness over time) plus a synthesized sound voice. Both are
// scheduled on the same AudioContext clock. The haptic side lands on a 1 kHz bus that
// every output path reads, so a device gets whatever it can render:
//   touch  — Android motor (navigator.vibrate PWM), controller dual-rumble, iOS taptic ticks
//   body   — the haptic track as felt bass (46 Hz + harmonics) through speakers/chassis
//   sight  — `bus.level()` drives light and shake on screen

export type Voice = number[]; // hy-voice detent positions, 0..1 per axis

const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));

// ───────────────────────────────────────────────────────────── haptic bus
const HZ = 1000, SPAN = 4000; // 4 s ring at 1 kHz

export class HapticBus {
  I = new Float32Array(SPAN);
  S = new Float32Array(SPAN);
  marks: { t: number; i: number }[] = []; // transients, for taptic ticks
  dirty = false;
  private origin = 0; // absolute ms index of slot 0's epoch (ring is indexed by ms % SPAN)
  private cleared = 0;
  constructor(private now: () => number) {}

  private ms(t: number) { return Math.round(t * HZ); }
  private sweep(nowMs: number) {
    // clear slots that fell behind 'now' so the ring can be reused
    for (let m = Math.max(this.cleared, nowMs - SPAN + 1); m < nowMs - 50; m++) { const k = ((m % SPAN) + SPAN) % SPAN; this.I[k] = 0; this.S[k] = 0; }
    this.cleared = Math.max(this.cleared, nowMs - 50);
    void this.origin;
  }
  private add(m: number, i: number, s: number) {
    const now = this.ms(this.now());
    if (m < now - 20 || m >= now + SPAN - 100) return;
    const k = ((m % SPAN) + SPAN) % SPAN, prev = this.I[k];
    this.I[k] = 1 - (1 - prev) * (1 - clamp(i));
    this.S[k] = prev + i > 0 ? (this.S[k] * prev + s * i) / (prev + i) : s;
  }
  transient(t: number, i: number, s: number) {
    const m0 = this.ms(t);
    for (let k = 0; k < 120; k++) this.add(m0 + k, i * Math.exp(-k / 26), s);
    this.marks.push({ t, i }); if (this.marks.length > 64) this.marks.shift();
    this.dirty = true;
  }
  continuous(t: number, dur: number, i: (u: number) => number, s: (u: number) => number) {
    const m0 = this.ms(t), n = Math.max(1, Math.round(dur * HZ));
    for (let k = 0; k < n; k++) { const u = k / n; this.add(m0 + k, i(u), s(u)); }
    this.dirty = true;
  }
  level(t = this.now()) { const m = this.ms(t); this.sweep(this.ms(this.now())); const k = ((m % SPAN) + SPAN) % SPAN; return this.I[k]; }
  sharp(t = this.now()) { const k = ((this.ms(t) % SPAN) + SPAN) % SPAN; return this.S[k]; }
}

// ───────────────────────────────────────────────────────────── engine
export class Feel {
  ac: AudioContext;
  bus: HapticBus;
  out: GainNode;
  wet: GainNode;
  analyser: AnalyserNode;
  private noiseBuf: AudioBuffer;
  private body: GainNode;
  private bodyLevel = 0.4;
  touch: 'vibrate' | 'gamepad' | 'taptic' | null = null;
  muted = false;

  constructor() {
    this.ac = new (window.AudioContext || (window as any).webkitAudioContext)({ latencyHint: 'interactive' });
    this.bus = new HapticBus(() => this.ac.currentTime);
    const ac = this.ac;
    const comp = ac.createDynamicsCompressor();
    comp.threshold.value = -10; comp.knee.value = 8; comp.ratio.value = 6; comp.attack.value = 0.002; comp.release.value = 0.15;
    this.out = ac.createGain(); this.out.gain.value = 0.9;
    this.analyser = ac.createAnalyser(); this.analyser.fftSize = 1024; this.analyser.smoothingTimeConstant = 0.2;
    this.out.connect(comp); comp.connect(this.analyser); this.analyser.connect(ac.destination);
    // reverb send: a generated room (exponential noise tail, slightly dark)
    const conv = ac.createConvolver(), len = Math.round(ac.sampleRate * 1.25), ir = ac.createBuffer(2, len, ac.sampleRate);
    for (let c = 0; c < 2; c++) { const d = ir.getChannelData(c); let lp = 0; for (let i = 0; i < len; i++) { lp = lp * 0.55 + (Math.random() * 2 - 1) * 0.45; d[i] = lp * Math.pow(1 - i / len, 3.2); } }
    conv.buffer = ir; this.wet = ac.createGain(); this.wet.gain.value = 0.55; this.wet.connect(conv); conv.connect(this.out);
    this.noiseBuf = ac.createBuffer(1, ac.sampleRate * 2, ac.sampleRate);
    { const d = this.noiseBuf.getChannelData(0); for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1; }
    // body: the haptic bus as bass you can feel
    this.body = ac.createGain(); this.body.gain.value = 0; this.body.connect(this.out);
    ([[46, 1], [92, 0.42], [138, 0.2]] as const).forEach(([f, l]) => { const o = ac.createOscillator(); o.frequency.value = f; const g = ac.createGain(); g.gain.value = l; o.connect(g).connect(this.body); o.start(); });
  }

  get now() { return this.ac.currentTime; }
  async resume() { try { (navigator as any).audioSession && ((navigator as any).audioSession.type = 'playback'); } catch (_) {} await this.ac.resume(); }
  setBody(level: number) { this.bodyLevel = level; }

  // ── node helpers
  noise() { const s = this.ac.createBufferSource(); s.buffer = this.noiseBuf; s.loop = true; s.loopStart = Math.random(); return s; }
  osc(type: OscillatorType, f: number) { const o = this.ac.createOscillator(); o.type = type; o.frequency.value = f; return o; }
  filt(type: BiquadFilterType, f: number, q = 0.7) { const b = this.ac.createBiquadFilter(); b.type = type; b.frequency.value = f; b.Q.value = q; return b; }
  gain(v = 0) { const g = this.ac.createGain(); g.gain.value = v; return g; }
  pan(p: number) { const s = this.ac.createStereoPanner(); s.pan.value = clamp(p, -1, 1); return s; }
  /** percussive envelope on a gain: attack a, exponential decay to silence over d */
  perc(g: GainNode, at: number, peak: number, a: number, d: number) {
    g.gain.setValueAtTime(0.0001, at); g.gain.linearRampToValueAtTime(peak, at + Math.max(0.0008, a)); g.gain.exponentialRampToValueAtTime(0.0001, at + a + d);
  }
  play(src: AudioScheduledSourceNode, at: number, dur: number) { src.start(at); src.stop(at + dur + 0.05); }
  shaper(amount: number) {
    const ws = this.ac.createWaveShaper(), n = 1024, k = amount * 40, c = new Float32Array(n);
    for (let i = 0; i < n; i++) { const x = (i / (n - 1)) * 2 - 1; c[i] = ((1 + k) * x) / (1 + k * Math.abs(x)); }
    ws.curve = c; ws.oversample = '2x'; return ws;
  }
  /** a short ping: sine + optional bell partial */
  ping(at: number, f: number, g: number, d: number, dest: AudioNode = this.out, bell = 0) {
    const o = this.osc('sine', f), e = this.gain(); o.connect(e).connect(dest); this.perc(e, at, g, 0.001, d); this.play(o, at, d);
    if (bell) { const o2 = this.osc('sine', f * 2.76), e2 = this.gain(); o2.connect(e2).connect(dest); this.perc(e2, at, g * bell, 0.001, d * 0.6); this.play(o2, at, d); }
  }
  /** filtered noise burst */
  hiss(at: number, type: BiquadFilterType, f: number, q: number, g: number, a: number, d: number, dest: AudioNode = this.out) {
    const n = this.noise(), b = this.filt(type, f, q), e = this.gain(); n.connect(b).connect(e).connect(dest); this.perc(e, at, g, a, d); this.play(n, at, a + d);
  }
  /** sine with a pitch glide (thuds, drops, boings) */
  glide(at: number, f0: number, f1: number, t: number, g: number, d: number, type: OscillatorType = 'sine', dest: AudioNode = this.out) {
    const o = this.osc(type, f0), e = this.gain(); o.frequency.setValueAtTime(f0, at); o.frequency.exponentialRampToValueAtTime(Math.max(1, f1), at + t);
    o.connect(e).connect(dest); this.perc(e, at, g, 0.002, d); this.play(o, at, d);
  }

  // ── touch dispatch (10 ms tick, plus immediate on new content)
  private nextChunk = 0; private lastVib = 0; private nextPad = 0; private lastMark = 0; private tickAcc = 0; private tapticLast = 0;
  tapticEl: HTMLElement | null = null;
  pump() {
    const t = this.now, I = this.bus.level(t), S = this.bus.sharp(t);
    this.body.gain.setTargetAtTime(this.muted ? 0 : this.bodyLevel * Math.pow(I, 1.15), t, 0.006);
    if (this.muted) return;
    if (this.touch === 'vibrate') {
      const due = t >= this.nextChunk || (this.bus.dirty && t - this.lastVib > 0.03);
      if (due) { (navigator as any).vibrate(this.vibrateChunk(t + 0.025)); this.lastVib = t; this.nextChunk = t + 0.45; this.bus.dirty = false; }
    } else if (this.touch === 'gamepad' && t >= this.nextPad) {
      for (const g of navigator.getGamepads ? [...navigator.getGamepads()] : []) (g as any)?.vibrationActuator?.playEffect('dual-rumble', { startDelay: 0, duration: 60, strongMagnitude: clamp(I * (1 - 0.6 * S)), weakMagnitude: clamp(I * (0.3 + 0.7 * S)) }).catch(() => {});
      this.nextPad = t + 0.04;
    } else if (this.touch === 'taptic') {
      for (const m of this.bus.marks) if (m.i >= 0.22 && m.t > this.lastMark && m.t <= t) this.tap();
      if (I > 0.25) { this.tickAcc += (t - this.lastMark) * (6 + 26 * I); if (this.tickAcc >= 1) { this.tickAcc = 0; this.tap(); } }
      this.lastMark = t;
    }
  }
  private tap() { const n = performance.now(); if (n - this.tapticLast < 28) return; this.tapticLast = n; try { this.tapticEl?.click(); } catch (_) {} }
  /** on/off motor: the bus as PWM (20 ms frames, duty = intensity) + a solid pulse per transient */
  vibrateChunk(t0: number, span = 0.6) {
    const ms = Math.round(span * 1000), on = new Uint8Array(ms);
    for (let f = 0; f < ms; f += 20) {
      let I = 0; for (let k = 0; k < 20; k += 2) I = Math.max(I, this.bus.level(t0 + (f + k) / 1000));
      const duty = I < 0.07 ? 0 : Math.round(Math.max(5, Math.pow(I, 0.8) * 20));
      on.fill(1, f, Math.min(ms, f + duty));
    }
    for (const m of this.bus.marks) { const a = Math.round((m.t - t0) * 1000); if (a > -40 && a < ms) on.fill(1, Math.max(0, a), Math.min(ms, a + 10 + 30 * m.i)); }
    for (let i = 1, gap = 0; i < ms; i++) { if (!on[i]) gap++; else { if (gap > 0 && gap < 6 && on[i - gap - 1]) on.fill(1, i - gap, i); gap = 0; } }
    const pat: number[] = []; let cur = 1, run = 0;
    for (let i = 0; i < ms; i++) { if (on[i] === cur) run++; else { pat.push(run); cur = on[i]; run = 1; } }
    pat.push(run); if (pat.length > 98) pat.length = 98; return pat;
  }
}
