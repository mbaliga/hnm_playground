// HAfx Studio — tactile intro.
// The Hyle Tactile Kit, played as a piece of music. A conductor runs one 120 bpm score:
// every press, detent, ridge, pulse and division sits on the beat grid and is scheduled on
// the audio clock, with a band underneath (drone, bass, side-chained pad, hats, claps,
// risers). A ghost fingertip performs on the real components, animated pose-to-pose on
// Disney's principles; the camera stages each control in turn. When the score ends, the
// controls keep their feels and the instrument is yours.
import 'hyle/components/index.js';
import { setKitFeel } from 'hyle/kit/kit-runtime.js';
import { Feel, type Voice } from './feel';
import { Feels } from './feels';
import { Band, CHORDS, N } from './band';
import { Ghost, ease, easeOutBack, type P } from './ghost';
import { Specimen, FOCUS_TRUE } from './specimen';

const $ = <T extends Element = HTMLElement>(s: string, r: ParentNode = document) => r.querySelector(s) as T;
const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a: number, b: number, u: number) => a + (b - a) * u;

// The kit's own three sounds give way to authored feels bound to each control.
setKitFeel({ sfx: { click: null, tick: null, thunk: null }, hx: { click: null, toggle: null, tick: null } });

// ─────────────────────────────────────────────────────────────── elements
const el = {
  power: $('#power') as any, lamp: $('#lamp') as any, focus: $('#focus') as any, zoom: $('#zoom') as any,
  stick: $('#stick') as any, voice: $('#voice') as any, rec: $('#rec') as any, transport: $('#transport') as any,
  vu: $('#vu') as any, meter: $('#meter') as any, led: $('#led') as any, field: $('#field') as HTMLCanvasElement,
  ghost: $('#ghost'), begin: $('#begin'), again: $('#again'), rack: $('#rack'), tip: $('#tip'), info: $('#info'),
  rig: $('.rig'), center: $('.center'),
};
const specimen = new Specimen(el.field);
new ResizeObserver(() => specimen.resize()).observe(el.field);
const ghost = new Ghost(el.ghost);

let feel: Feel | null = null, feels: Feels | null = null, band: Band | null = null;
let voice: Voice = [0.2, 0.2, 0.2, 0.2, 0.2];
let powered = false, lampOn = false, auto = false;
const fx = () => (auto ? null : feels); // during the score, the conductor plays every feel on the grid

// ─────────────────────────────────────────────────────────────── touch path detection
const ua = navigator.userAgent;
const isIOS = /iP(hone|ad|od)/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
const coarse = matchMedia('(pointer: coarse)').matches;
const pads = () => [...(navigator.getGamepads ? navigator.getGamepads() : [])].filter((g) => g && (g as any).vibrationActuator);
const touchKind = (): Feel['touch'] => (pads().length ? 'gamepad' : typeof navigator.vibrate === 'function' && coarse ? 'vibrate' : isIOS ? 'taptic' : null);
const RM = matchMedia('(prefers-reduced-motion: reduce)').matches;

function led(text: string) { el.led.text = `${text}      `; }

// ─────────────────────────────────────────────────────────────── feel bindings (a person's hand)
el.power.addEventListener('hy-change', (e: any) => {
  powered = !!e.detail.value; fx()?.power(powered);
  if (!auto) led(powered ? 'POWER  ·  HAfx STUDIO' : 'STANDBY');
  if (!powered) { lampOn = false; el.lamp.on = false; specimen.s.lamp = 0; }
});
el.lamp.addEventListener('hy-change', (e: any) => {
  lampOn = !!e.detail.value; fx()?.lamp(lampOn);
  specimen.s.lamp = lampOn && powered ? 1 : 0;
  if (lampOn && !powered && !auto) led('NO POWER');
});
{
  let prevDet = -1, prevV = 0, prevT = 0;
  el.focus.addEventListener('hy-input', (e: any) => {
    const v = clamp(e.detail.value / 100), now = performance.now() / 1000, det = Math.floor(v * 20);
    const speed = clamp(Math.abs(v - prevV) / Math.max(0.008, now - prevT) / 1.2);
    if (det !== prevDet && prevDet !== -1) fx()?.detent(v, speed, det === Math.floor(FOCUS_TRUE * 20));
    prevDet = det; prevV = v; prevT = now; specimen.s.focus = v;
  });
}
{
  let prevV = -1, prevT = 0, prevRidge = -1, lastLed = 0;
  el.zoom.addEventListener('hy-input', (e: any) => {
    const v = clamp(e.detail.value / 100), now = performance.now() / 1000, F = fx();
    if (prevV >= 0 && F) {
      const vel = (v - prevV) / Math.max(0.008, now - prevT);
      F.friction(v, vel);
      const ridge = Math.floor(v * 24); if (ridge !== prevRidge) F.ridge(v);
      if ((v >= 0.999 && prevV < 0.999) || (v <= 0.001 && prevV > 0.001)) F.stop(v > 0.5);
      prevRidge = ridge;
    }
    prevV = v; prevT = now; specimen.s.zoom = v;
    if (now - lastLed > 0.25) { lastLed = now; led(`MAG ${Math.round(10 * Math.pow(10, v))}×`); }
  });
}
{
  let atGate = false;
  el.stick.addEventListener('hy-input', (e: any) => {
    const { x, y } = e.detail, m = clamp(Math.hypot(x, y));
    fx()?.tension(m);
    if (m > 0.97 && !atGate) fx()?.gate(); atGate = m > 0.97;
    specimen.s.stick = { x, y };
  });
  el.stick.addEventListener('hy-change', () => { fx()?.release(); specimen.s.stick = { x: 0, y: 0 }; atGate = false; });
}
{
  let pulseTimer = 0;
  el.voice.addEventListener('hy-input', (e: any) => {
    voice = e.detail.values; fx()?.bead(e.detail.row, e.detail.act);
    clearTimeout(pulseTimer);
    if (!auto) pulseTimer = window.setTimeout(() => beat(), 150); // hear and feel the new character
  });
}
function beat(amt = 1) {
  if (!feels || !feel) return;
  const t = feel.now + 0.01; feels.pulse(voice, t, amt); specimen.pulse(t, amt);
}
el.rec.addEventListener('hy-change', () => {
  setTimeout(() => (el.rec.on = false), 900);
  if (auto || !feels || !feel) return;
  feels.rec(); led('REC  ●');
  if (!specimen.divided && lampOn && powered) { const t = feel.now + 0.25; feels.mitosis(t, 1.5); specimen.divide(t, 1.5); }
});
el.transport.addEventListener('hy-change', (e: any) => {
  if (auto || !feels || !feel) return;
  feels.key();
  if (e.detail.action === 'play') {
    led('PLAY  ▶');
    feels.groove(feel.now + 0.08, voice, 120, (kind, t) => {
      if (kind === 'K') specimen.pulse(t, 1, specimen.divided ? 0 : -1);
      else if (kind === 'S') specimen.pulse(t, 0.8, specimen.divided ? 1 : -1);
      else specimen.flick(t);
    });
  }
});
el.field.addEventListener('pointerdown', (e) => { if (e.isTrusted && powered && lampOn) beat(); });

// ─────────────────────────────────────────────────────────────── geometry + synthetic touch
const sr = (host: any, sel: string) => host.renderRoot.querySelector(sel) as HTMLElement;
const center = (n: Element): P => { const r = n.getBoundingClientRect(); return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; };
function ptr(type: string, target: EventTarget, x: number, y: number) {
  target.dispatchEvent(new PointerEvent(type, { clientX: x, clientY: y, bubbles: true, composed: true, cancelable: true, pointerId: 77, pointerType: 'touch', isPrimary: true, button: 0, buttons: type === 'pointerup' ? 0 : 1 }));
}
/** squash & stretch on the control itself when it's struck */
function squash(host: HTMLElement, k = 1) {
  if (RM) return;
  host.animate([{ transform: 'scale(1)' }, { transform: `scale(${1 + 0.07 * k},${1 - 0.1 * k})`, offset: 0.22 }, { transform: `scale(${1 - 0.03 * k},${1 + 0.045 * k})`, offset: 0.58 }, { transform: 'scale(1)' }], { duration: 360, easing: 'cubic-bezier(.2,.7,.3,1)' });
}

// where the fingertip touches each control
const at = {
  power: () => center(sr(el.power, '.pbtn')),
  lamp: () => center(sr(el.lamp, '.stog')),
  knob: () => { const c = center(sr(el.focus, '.knob-root')), r = sr(el.focus, '.knob-root').getBoundingClientRect().width / 2, a = ((-135 + 270 * el.focus.value / 100) * Math.PI) / 180; return { x: c.x + Math.sin(a) * r * 0.62, y: c.y - Math.cos(a) * r * 0.62 }; },
  cap: () => { const r = sr(el.zoom, '.vf-cap').getBoundingClientRect(); return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; },
  stick: () => center(sr(el.stick, '.j2-knob')),
  dot: (row: number) => center(el.voice.renderRoot.querySelectorAll('.sset-dot')[row]),
  detent: (row: number, act: number) => { const tr = el.voice.renderRoot.querySelectorAll('.sset-track')[row].getBoundingClientRect(); return { x: tr.left + tr.width * (0.2 + 0.2 * act), y: tr.top + tr.height / 2 }; },
  rec: () => center(sr(el.rec, '.crater-cell')),
  play: () => center(sr(el.transport, '.seg[aria-label="Play"]')),
  offscreen: () => ({ x: innerWidth * 0.5, y: innerHeight + 60 }),
};

/** drives a dragged control from a value curve through its own pointer handlers */
class Drag {
  held = false; private o: P = { x: 0, y: 0 }; private v0 = 0;
  constructor(private kind: 'knob' | 'fader' | 'stick') {}
  set(v: number | P | null) {
    const k = this.kind;
    if (v === null) { if (this.held) { const t = k === 'stick' ? sr(el.stick, '.j2-housing') : window; ptr('pointerup', t, this.o.x, this.o.y); this.held = false; } return; }
    if (k === 'knob') {
      const n = sr(el.focus, '.knob-root');
      if (!this.held) { (n as any).setPointerCapture = () => {}; this.o = center(n); this.v0 = el.focus.value / 100; ptr('pointerdown', n, this.o.x, this.o.y); this.held = true; }
      ptr('pointermove', window, this.o.x, this.o.y - ((v as number) - this.v0) * 165);
    } else if (k === 'fader') {
      const slot = sr(el.zoom, '.vf-slot'), r = slot.getBoundingClientRect(), y = r.top + (1 - (v as number)) * (r.height - 22), x = r.left + r.width / 2;
      if (!this.held) { ptr('pointerdown', sr(el.zoom, '.vf'), x, y); this.held = true; }
      ptr('pointermove', window, x, y); this.o = { x, y };
    } else {
      const h = sr(el.stick, '.j2-housing'), c = center(h), R = sr(el.stick, '.j2-bowl').getBoundingClientRect().width / 2, p = v as P;
      if (!this.held) { ptr('pointerdown', h, c.x, c.y); this.held = true; }
      this.o = { x: c.x + p.x * R, y: c.y - p.y * R }; ptr('pointermove', h, this.o.x, this.o.y);
    }
  }
}
const knob = new Drag('knob'), fader = new Drag('fader'), stick = new Drag('stick');

// ─────────────────────────────────────────────────────────────── THE SCORE (in beats, 120 bpm)
const BPM = 120, SPB = 60 / BPM, END = 54;
type Cue = { b: number; audio?: (t: number) => void; visual?: () => void };

// voice over time (the score knows it ahead of the slider, so pulses can be scheduled early)
const VOICE_CHANGES: { b: number; row: number; act: number; label: string }[] = [
  { b: 24, row: 1, act: 3, label: 'HARD' }, { b: 26, row: 0, act: 3, label: 'BRIGHT' }, { b: 28, row: 2, act: 2, label: 'WET' },
  { b: 30, row: 4, act: 2, label: 'DRIVEN' }, { b: 31.5, row: 3, act: 3, label: 'WIDE' },
];
const voiceAt = (b: number): Voice => {
  const v: Voice = [0.2, 0.2, 0.2, 0.2, 0.2];
  for (const c of VOICE_CHANGES) if (b >= c.b - 1e-6) v[c.row] = 0.2 + 0.2 * c.act;
  return v;
};
// control curves (pose-to-pose, with anticipation and follow-through)
const KNOB_STEPS = Array.from({ length: 12 }, (_, k) => ({ b: 8 + k * 0.25, v: 0.12 + 0.05 * (k + 1) }))
  .concat([{ b: 11.5, v: 0.67 }, { b: 12, v: FOCUS_TRUE + 0.004 }]);
function knobAt(b: number): number | null {
  if (b < 7.75 || b > 12.45) return null;
  if (b < 8) return 0.12 - 0.025 * Math.sin(Math.PI * ((b - 7.75) / 0.25));          // anticipation: a counter-turn
  let v = 0.12, from = 0.12, tb = 7.75;
  for (const s of KNOB_STEPS) if (b >= s.b) { from = v; v = s.v; tb = s.b; }
  const u = clamp((b - tb) / 0.09);                                                   // each detent snaps in ~45 ms
  let out = lerp(from, v, ease(u));
  if (tb === 12) out = v - 0.02 * Math.exp(-(b - 12) / 0.08) * Math.cos((b - 12) * 14); // follow-through settle at true focus
  return out;
}
function faderAt(b: number): number | null {
  if (b < 15.75 || b > 19.9) return null;
  if (b < 16) return 0.1 - 0.035 * Math.sin(Math.PI * ((b - 15.75) / 0.25));          // anticipation: dips before it rises
  if (b < 18.5) return lerp(0.1, 1, ease((b - 16) / 2.5));
  if (b < 19) return 1;
  const u = (b - 19) / 0.85; return lerp(1, 0.62, easeOutBack(u, 2.2));               // settles with an overshoot past 0.62
}
function stickAt(b: number): P | null {
  if (b < 20.75 || b > 22.75) return null;
  const seg = (b0: number, b1: number, p0: P, p1: P) => { const u = ease((b - b0) / (b1 - b0)); return { x: lerp(p0.x, p1.x, u), y: lerp(p0.y, p1.y, u) }; };
  const A = { x: 0, y: 0 }, B = { x: -0.62, y: -0.36 }, C = { x: -1, y: -0.5 }, D = { x: -0.5, y: -0.25 };
  if (b < 21.25) return seg(20.75, 21.25, A, B);
  if (b < 21.75) return B;
  if (b < 22) return seg(21.75, 22, B, C);
  if (b < 22.25) return C;
  return seg(22.25, 22.6, C, D);
}

function buildScore(): Cue[] {
  const F = feels!, B = band!, S = specimen, cues: Cue[] = [];
  const a = (b: number, fn: (t: number) => void) => cues.push({ b, audio: fn });
  const v = (b: number, fn: () => void) => cues.push({ b, visual: fn });
  const pulse = (b: number, amt = 1, who = -1, duck = true) => a(b, (t) => { F.pulse(voiceAt(b), t, amt); S.pulse(t, amt, who); if (duck) B.duck(t); });
  const bars = ['Dm', 'Dm', 'Bb', 'F', 'C', 'Dm', 'Bb', 'F', 'A', 'Dm', 'Bb', 'F'];

  // ── bar 0 · the dark: a drone, a heartbeat you feel before you see
  a(0, (t) => B.drone(t, t + 4 * SPB));
  pulse(1, 0.28, -1, false); pulse(3, 0.34, -1, false);
  // felt more than heard: the heartbeat in the dark is mostly haptic
  [1, 3].forEach((b) => a(b, (t) => { feel!.bus.transient(t, 0.78, 0.14); feel!.bus.transient(t + 0.17, 0.48, 0.12); }));
  v(0, () => { led('HAfx STUDIO'); stage('power'); });
  // ── bar 1 · power on the downbeat, the lamp on beat 3
  a(4, (t) => { F.power(true, t); B.crash(t); });
  v(4, () => { el.power.shadowRoot.querySelector('.pbtn').click(); squash(el.power); led('POWER'); });
  a(6, (t) => F.lamp(true, t));
  v(6, () => { sr(el.lamp, '.stog').click(); squash(el.lamp); led('LAMP'); });
  // pads and bass follow the progression from bar 1
  for (let bar = 1; bar <= 10; bar++) {
    const ch = CHORDS[bars[bar]], b0 = bar * 4;
    a(b0, (t) => B.pad(t, ch.pad, 4 * SPB, bar < 3 ? 0.7 : bar >= 9 ? 1 : 0.8, bar === 1 ? 1.2 : 0.2));
    if (bar <= 2) a(b0, (t) => B.bass(t, ch.root, 3.8 * SPB, 0.75));
    else if (bar <= 7) [0, 1, 2, 3].forEach((q) => a(b0 + q, (t) => B.bass(t, q === 3 ? ch.root * 2 : ch.root, 0.8 * SPB, 0.8)));
    else if (bar === 8) for (let e = 0; e < 7; e++) a(b0 + e * 0.5, (t) => B.bass(t, ch.root, 0.42 * SPB, 0.6 + e * 0.06));
    else for (let e = 0; e < 8; e++) a(b0 + e * 0.5, (t) => B.bass(t, e % 2 ? ch.root * 2 : ch.root, 0.45 * SPB, 1));
  }
  // hats: off-beat eighths from bar 2, ghost sixteenths from bar 5, sixteenths in the drop
  for (let b = 7.5; b < 32; b += 1) a(b, (t) => B.hat(t, 0.55));
  for (let b = 20.75; b < 32; b += 1) a(b, (t) => B.hat(t, 0.25));
  for (let b = 36; b < 44; b += 0.25) a(b, (t) => B.hat(t, b % 1 === 0.5 ? 0.8 : 0.4, b % 1 === 0.5));

  // ── bars 2-3 · FOCUS: the knob ratchets one detent per sixteenth; true focus lands on bar 3
  v(6.6, () => { stage('focus'); led('FOCUS'); });
  KNOB_STEPS.forEach((s, i) => a(s.b, (t) => F.detent(s.v, i < 12 ? 0.55 + i * 0.03 : 0.5, i === KNOB_STEPS.length - 1, t)));
  pulse(12, 1); pulse(14, 0.8); pulse(15.5, 0.7);

  // ── bar 4 · MAGNIFICATION: the fader is the riser; the end stop is the hit
  v(12.9, () => stage('zoom'));
  for (let b = 16; b < 18.5; b += 0.25) {
    const p = faderAt(b + 0.125)!, vel = (faderAt(b + 0.2)! - faderAt(b + 0.05)!) / (0.15 * SPB);
    a(b, (t) => { F.friction(p, vel, t); F.ridge(p, t); });
  }
  a(16, (t) => B.riser(t, t + 2.5 * SPB, N.C3));
  a(18.5, (t) => F.stop(true, t));
  for (let b = 19; b < 19.9; b += 0.25) { const p = faderAt(b + 0.1)!, vel = (faderAt(b + 0.2)! - faderAt(b)!) / (0.2 * SPB); a(b, (t) => F.friction(p, vel, t)); }

  // ── bar 5 · STAGE: tension, the gate as the backbeat, a spring that wobbles home
  v(19.9, () => { stage('stage'); led('STAGE'); });
  pulse(20, 1); pulse(21.5, 0.8);
  for (let b = 20.75; b < 22.75; b += 0.125) { const p = stickAt(b); if (p) { const m = Math.hypot(p.x, p.y); a(b, (t) => F.tension(Math.min(1, m), t)); } }
  a(22, (t) => F.gate(t));
  a(22.75, (t) => F.release(t));

  // ── bars 6-7 · VOICE: the pulse is the kick; each slider move changes its character on the beat
  v(23.05, () => { stage('voice'); led('VOICE'); });
  [24, 25.5, 26, 27.5, 28, 29.5, 30, 31.5].forEach((b, i) => pulse(b, i % 2 ? 0.8 : 1));
  [25, 27, 29, 31].forEach((b) => a(b, (t) => B.clap(t, 0.55)));
  VOICE_CHANGES.forEach((c) => {
    a(c.b, (t) => F.bead(c.row, c.act, t));
    v(c.b, () => { el.voice.setDetent(c.row, c.act); led(`VOICE  ·  ${c.label}`); });
  });

  // ── bar 8 · REC: the cell gathers itself, stretches, creaks … (the build)
  v(31.8, () => stage('rec'));
  a(32.5, (t) => { F.rec(t); });
  v(32.5, () => { sr(el.rec, '.crater-cell').click(); squash(el.rec); led('REC  ●'); });
  a(33, (t) => F.mitosis(t, 3 * SPB));
  a(33, (t) => B.riser(t, t + 2.75 * SPB));
  [34, 34.5, 35, 35.25, 35.5, 35.625].forEach((b, i) => a(b, (t) => F.snap(t, 0.3 + i * 0.1)));
  for (let b = 33; b < 35.75; b += 0.25) a(b, (t) => B.hat(t, 0.15 + 0.5 * ((b - 33) / 2.75), b % 1 === 0.5)); // crescendo into the drop
  a(35.75, (t) => B.vacuum(t, t + 0.25 * SPB));                                 // the breath before the drop

  // ── bars 9-10 · PLAY on the pop: the daughters' groove, everything in
  a(36, (t) => { F.key(t); B.crash(t); });
  v(36, () => { sr(el.transport, '.seg[aria-label="Play"]').click(); squash(el.transport, 0.7); bump(); stage(null, 'drop'); led('PLAY  ▶'); });
  const pat = 'K.t.S.tKK.t.S.tt';
  for (let bar = 0; bar < 2; bar++) [...pat].forEach((ch, i) => {
    const b = 36 + bar * 4 + i * 0.25;
    if (ch === 'K') pulse(b, b === 36 ? 0.7 : 0.95, 0);
    if (ch === 'S') a(b, (t) => { F.snap(t); B.clap(t, 0.5); S.pulse(t, 0.8, 1); });
    if (ch === 't') a(b, (t) => { F.cilia(t); S.flick(t); });
  });

  // ── bar 11 · lamp off; the field becomes a dot matrix; one last hit on bar 12
  v(42.4, () => stage('power'));
  a(44, (t) => { F.lamp(false, t); B.pad(t, CHORDS.F.pad, 4 * SPB, 0.6, 0.6); B.bass(t, N.F2, 3.6 * SPB, 0.6); });
  v(44, () => { sr(el.lamp, '.stog').click(); squash(el.lamp); led('HAfx STUDIO'); stage(null, 'field'); });
  a(44.42, (t) => { const hit = F.reveal(t, 14, 0.125); S.reveal(t, 14, 0.125, hit); });
  v(48, () => { bump(1.4); led('HAfx STUDIO      FEEL THE SOUND'); });
  cues.sort((x, y) => x.b - y.b);
  return cues;
}

function choreograph() {
  const g = ghost; g.clear(); g.park({ ...at.offscreen(), h: 1.2 });
  g.travel(0.4, 2.9, at.power, 0.5); g.hover(2.9, 3.5, at.power); g.press(4, at.power);
  g.travel(4.7, 5.45, at.lamp); g.press(6, at.lamp);
  g.travel(6.7, 7.45, at.knob); g.grab(7.75, at.knob); g.drag(7.75, 12.45, () => at.knob()); g.lift(12.45, at.knob);
  g.travel(12.95, 15.1, at.cap); g.hover(15.1, 15.45, at.cap); g.grab(15.75, at.cap); g.drag(15.75, 19.9, () => at.cap()); g.lift(19.9, at.cap, 0.3);
  g.travel(20.2, 20.45, at.stick); g.grab(20.75, at.stick); g.drag(20.75, 22.75, () => at.stick()); g.lift(22.75, at.stick, 0.3);
  let prev = 23.05;
  const acts = [0, 0, 0, 0, 0];
  VOICE_CHANGES.forEach((c) => {
    const from = acts[c.row], to = c.act; acts[c.row] = to;
    g.travel(prev, c.b - 0.55, () => at.dot(c.row), 0.2);
    g.grab(c.b - 0.25, () => at.detent(c.row, from));
    g.drag(c.b - 0.25, c.b, (b) => { const p0 = at.detent(c.row, from), p1 = at.detent(c.row, to), u = ease((b - (c.b - 0.25)) / 0.25); return { x: lerp(p0.x, p1.x, u), y: p0.y }; });
    g.lift(c.b, () => at.detent(c.row, to), 0.3);
    prev = c.b + 0.3;
  });
  g.travel(31.8, 32.1, at.rec); g.press(32.5, at.rec, 0.4);
  g.travel(33.2, 35.0, at.play); g.hover(35.0, 35.5, at.play); g.press(36, at.play);
  g.hover(36.7, 42.4, () => { const p = at.play(); return { x: p.x + 34, y: p.y - 46 }; });   // it watches, nodding to the beat
  g.travel(42.4, 43.45, at.lamp); g.press(44, at.lamp);
  g.travel(44.75, 46.2, at.offscreen, 0.6);
}

// ─────────────────────────────────────────────────────────────── staging + camera
const GROUPS: Record<string, string> = { power: '.g-power', focus: '.g-focus', zoom: '.g-zoom', stage: '.g-stage', voice: '.g-voice', rec: '.g-rec' };
const phone = () => matchMedia('(max-width: 979px), (max-height: 560px)').matches;
function stage(id: string | null, mode: 'drop' | 'field' | null = null) {
  document.querySelectorAll('.grp').forEach((g) => g.classList.remove('lit'));
  el.rig.classList.toggle('staged', true);
  el.rig.dataset.mode = mode ?? '';
  const g = id ? $(GROUPS[id]) : null;
  if (g) g.classList.add('lit');
  // the cell looks toward whatever is being played (appeal)
  const fc = center(el.field);
  if (g) { const c = center(g); specimen.lookAt(c.x - fc.x, c.y - fc.y); } else specimen.lookAt(0, 0);
  if (phone()) {
    if (g) { const target = (g as HTMLElement).offsetLeft - (el.rack.clientWidth - (g as HTMLElement).offsetWidth) / 2; el.rack.scrollTo({ left: target, behavior: RM ? 'auto' : 'smooth' }); }
    return;
  }
  // a slow dolly toward the active control; push in on the field for the drop and the reveal
  if (RM) return;
  let dx = 0, dy = 0, s = 1;
  if (g) { const c = center(g); dx = (innerWidth / 2 - c.x) * 0.1; dy = (innerHeight / 2 - c.y) * 0.08; s = 1.035; }
  else if (mode) { dx = (innerWidth / 2 - fc.x) * 0.2; dy = (innerHeight / 2 - fc.y) * 0.2; s = mode === 'drop' ? 1.06 : 1.09; }
  el.rig.style.setProperty('--camx', `${dx.toFixed(1)}px`); el.rig.style.setProperty('--camy', `${dy.toFixed(1)}px`); el.rig.style.setProperty('--cams', s.toFixed(3));
}
function unstage() {
  el.rig.classList.remove('staged'); el.rig.dataset.mode = '';
  document.querySelectorAll('.grp').forEach((g) => g.classList.remove('lit'));
  el.rig.style.setProperty('--camx', '0px'); el.rig.style.setProperty('--camy', '0px'); el.rig.style.setProperty('--cams', '1');
}
/** exaggeration: the eyepiece jolts on the big hits */
function bump(k = 1) {
  if (RM) return;
  el.center.animate([{ transform: 'scale(1)' }, { transform: `scale(${1 + 0.06 * k})`, offset: 0.18 }, { transform: `scale(${1 - 0.015 * k})`, offset: 0.55 }, { transform: 'scale(1)' }], { duration: 620, easing: 'cubic-bezier(.2,.8,.3,1)' });
}

// ─────────────────────────────────────────────────────────────── the conductor
let cues: Cue[] = [], ai = 0, vi = 0, songStart = 0, sched = 0, running = false;
const beatNow = () => (feel ? (feel.now - songStart) / SPB : -1);
function conduct() {
  if (!running || !feel) return;
  const horizon = feel.now + 0.3;
  while (ai < cues.length && songStart + cues[ai].b * SPB < horizon) { const c = cues[ai++]; c.audio?.(songStart + c.b * SPB); }
}
function perform() {
  if (!feel) return;
  auto = true; running = true;
  cues = buildScore(); ai = 0; vi = 0;
  songStart = feel.now + 0.25;
  specimen.divide(songStart + 33 * SPB, 3 * SPB); // known ahead, so the cell can anticipate it
  choreograph(); ghost.visible = true;
  clearInterval(sched); sched = window.setInterval(conduct, 25); conduct();
}
function finish() {
  running = false; clearInterval(sched);
  knob.set(null); fader.set(null); stick.set(null);
  ghost.visible = false; auto = false; unstage(); specimen.panOverride = null;
  el.again.hidden = false;
}
// a real touch ends the performance and hands the instrument over
addEventListener('pointerdown', (e) => {
  if (!e.isTrusted || !running || (e.target as Element)?.closest?.('.info, .tip')) return;
  cues = cues.slice(0, ai); finish(); // nothing further is scheduled; what's already sounding rings out
}, true);

/** put knob, fader and voice back to their opening positions, silently */
function resetControls() {
  const was = auto; auto = true;
  knob.set(0.12); knob.set(null);
  fader.set(0.1); fader.set(null);
  for (let i = 0; i < 5; i++) el.voice.setDetent(i, 0);
  el.voice.hot = -1; voice = [0.2, 0.2, 0.2, 0.2, 0.2];
  auto = was;
}

async function start() {
  if (!feel) {
    feel = new Feel(); feels = new Feels(feel); band = new Band(feel);
    if ((window as any).__trace) traceFeels(feels);
    feel.tapticEl = $('#tapticLabel');
    setInterval(() => feel!.pump(), 10);
  }
  await feel.resume();
  feel.touch = touchKind(); feel.setBody(feel.touch === 'vibrate' || feel.touch === 'gamepad' ? 0.2 : 0.42);
  paintPaths();
  el.begin.hidden = true; el.again.hidden = true;
  auto = true;
  if (el.power.on) el.power.on = false; if (el.lamp.on) el.lamp.on = false;
  powered = false; lampOn = false;
  specimen.reset(); specimen.s.lamp = 0;
  resetControls();
  perform();
}
el.begin.addEventListener('click', start);
el.again.addEventListener('click', (e) => { e.stopPropagation(); start(); });

// ─────────────────────────────────────────────────────────────── frame loop
const buf = new Float32Array(1024);
const perf = { spec: 0, dt: 16, last: 0 };
(window as any).__studio = { perf, get feel() { return feel; }, get feels() { return feels; }, specimen, el, log: [] as string[], beatNow };
function loop() {
  const fz = (window as any).__studio?.freeze;
  const t = fz ?? (feel ? feel.now : performance.now() / 1000);
  if (running && fz === undefined) {
    // heard time: the picture follows what reaches the ears, not what was scheduled
    const lat = feel ? (feel.ac.outputLatency || feel.ac.baseLatency || 0) : 0;
    const b = (t - lat - songStart) / SPB;
    while (vi < cues.length && cues[vi].b <= b) { const c = cues[vi++]; c.visual?.(); }
    // a drag always lands its final pose before it lets go, however few frames this device draws
    const drive = (d: Drag, fn: (b: number) => any, end: number) => { const val = fn(b); if (val === null && d.held) d.set(fn(end - 1e-6)); d.set(val); };
    drive(knob, knobAt, 12.45); drive(fader, faderAt, 19.9); drive(stick, stickAt, 22.75);
    // the stage follows the stick on the score (frame-rate independent): the cell slides home
    const pu = ease(clamp((b - 20.9) / 1.9)); specimen.panOverride = { x: lerp(14, 0, pu), y: lerp(-8, 0, pu) };
    ghost.frame(b, performance.now() / 1000);
    if (b > END) finish();
  } else if (!running) ghost.frame(-99, performance.now() / 1000);
  if (feel) {
    specimen.s.haptic = feel.bus.level();
    feel.analyser.getFloatTimeDomainData(buf);
    let s = 0; for (let i = 0; i < buf.length; i++) s += buf[i] * buf[i];
    const lvl = clamp(Math.pow(Math.sqrt(s / buf.length) * 3.2, 0.6));
    el.vu.level = powered ? lvl : 0; el.meter.level = powered ? lvl : 0; el.meter.levelR = powered ? clamp(lvl * (0.9 + 0.2 * voice[3])) : 0;
    paintPaths(specimen.s.haptic);
  } else { el.vu.level = 0; el.meter.level = 0; el.meter.levelR = 0; }
  const f0 = performance.now(); specimen.frame(t); perf.spec = perf.spec * 0.95 + (performance.now() - f0) * 0.05;
  const now = performance.now(); if (perf.last) perf.dt = perf.dt * 0.95 + (now - perf.last) * 0.05; perf.last = now;
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);

// offline render of the whole score through the same code (used by the test harness)
(window as any).__renderSong = async (sr = 44100) => {
  const saved = { feel, feels, band, songStart };
  const ctx = new OfflineAudioContext(2, Math.ceil(sr * (END * SPB + 3)), sr);
  feel = new Feel(ctx); feels = new Feels(feel); band = new Band(feel); songStart = 0.1;
  for (const c of buildScore()) c.audio?.(songStart + c.b * SPB);
  const buf = await ctx.startRendering();
  ({ feel, feels, band, songStart } = saved); specimen.reset();
  const L = buf.getChannelData(0), R = buf.getChannelData(1);
  let peak = 0; for (let i = 0; i < L.length; i++) peak = Math.max(peak, Math.abs(L[i]), Math.abs(R[i]));
  const out = new Int16Array(L.length * 2); for (let i = 0; i < L.length; i++) { out[2 * i] = clamp(L[i], -1, 1) * 32767; out[2 * i + 1] = clamp(R[i], -1, 1) * 32767; }
  const bytes = new Uint8Array(out.buffer); let bin = ''; for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000) as any);
  return { peak, sr, b64: btoa(bin) };
};

// debug trace of every feel (only when a test harness sets window.__trace)
function traceFeels(fs: Feels) {
  for (const k of Object.getOwnPropertyNames(Feels.prototype)) {
    if (k === 'constructor') continue; const orig = (fs as any)[k].bind(fs);
    (fs as any)[k] = (...args: any[]) => { const L = (window as any).__studio.log; if (L.length < 6000) L.push(`${((feel!.now - songStart) / SPB).toFixed(3)} ${k}`); return orig(...args); };
  }
}

// ─────────────────────────────────────────────────────────────── paths read-out
function paintPaths(I = 0) {
  const k = feel?.touch ?? touchKind();
  const on: Record<string, boolean> = { sound: !!feel, body: !!feel, touch: !!k, sight: true };
  document.querySelectorAll<HTMLElement>('.path').forEach((p) => {
    const key = p.dataset.k!, lit = on[key]; p.dataset.on = lit ? '1' : '0';
    const lvl = lit ? (key === 'sound' ? clamp(I + 0.15) : I) : 0;
    p.style.setProperty('--lvl', String(lvl));
  });
}
el.info.addEventListener('click', (e) => {
  e.stopPropagation(); const open = el.tip.hidden; el.tip.hidden = !open; el.info.setAttribute('aria-expanded', String(open));
  if (!open) return;
  const k = feel?.touch ?? touchKind();
  const touch = k === 'gamepad' ? 'your controller: low motor for weight, high motor for edge' : k === 'vibrate' ? 'your phone motor, shaped to each feel' : k === 'taptic' ? 'taptic ticks on the beats (iOS 18+ Safari, best effort)' : 'no motor here — plug in a controller to add rumble';
  el.tip.innerHTML = `Every control carries its own HAfx feel, and the intro is scored: each press lands on the beat.<br><b>sound</b> · its voice<br><b>body</b> · its haptic as bass you feel<br><b>touch</b> · ${touch}<br><b>sight</b> · its haptic as light and shake<br>After the intro, play the instrument. Tap the cell to hear its pulse.`;
});
addEventListener('gamepadconnected', () => { if (feel) feel.touch = touchKind(); paintPaths(); });
paintPaths();
