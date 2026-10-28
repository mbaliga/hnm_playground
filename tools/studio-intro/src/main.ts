// HAfx Studio — tactile intro.
// The Hyle Tactile Kit, played. A ghost hand performs on the real components (real pointer
// events into their own handlers); every control's HAfx feel is bound to that control's
// events, so the performance and a person's own hand produce exactly the same feel.
// When the performance ends, the instrument is yours.
import 'hyle/components/index.js';
import { setKitFeel } from 'hyle/kit/kit-runtime.js';
import { Feel, type Voice } from './feel';
import { Feels } from './feels';
import { Specimen, FOCUS_TRUE } from './specimen';

const $ = <T extends Element = HTMLElement>(s: string, r: ParentNode = document) => r.querySelector(s) as T;
const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a: number, b: number, u: number) => a + (b - a) * u;
const easeIO = (u: number) => { u = clamp(u); return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2; };
const easeOut = (u: number) => 1 - Math.pow(1 - clamp(u), 3);

// The kit's own three sounds are replaced by authored feels bound to each control.
setKitFeel({ sfx: { click: null, tick: null, thunk: null }, hx: { click: null, toggle: null, tick: null } });

// ─────────────────────────────────────────────────────────────── elements
const el = {
  power: $('#power') as any, lamp: $('#lamp') as any, focus: $('#focus') as any, zoom: $('#zoom') as any,
  stick: $('#stick') as any, voice: $('#voice') as any, rec: $('#rec') as any, transport: $('#transport') as any,
  vu: $('#vu') as any, meter: $('#meter') as any, led: $('#led') as any, field: $('#field') as HTMLCanvasElement,
  ghost: $('#ghost'), begin: $('#begin'), again: $('#again'), rack: $('#rack'), tip: $('#tip'), info: $('#info'),
};
const specimen = new Specimen(el.field);
new ResizeObserver(() => specimen.resize()).observe(el.field);

let feel: Feel | null = null, feels: Feels | null = null;
let voice: Voice = [0.2, 0.2, 0.2, 0.2, 0.2];
let powered = false, lampOn = false, auto = false, aborted = false, quiet = false;
const fx = () => (quiet ? null : feels);

// ─────────────────────────────────────────────────────────────── touch path detection
const ua = navigator.userAgent;
const isIOS = /iP(hone|ad|od)/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
const coarse = matchMedia('(pointer: coarse)').matches;
const pads = () => [...(navigator.getGamepads ? navigator.getGamepads() : [])].filter((g) => g && (g as any).vibrationActuator);
const touchKind = (): Feel['touch'] => (pads().length ? 'gamepad' : typeof navigator.vibrate === 'function' && coarse ? 'vibrate' : isIOS ? 'taptic' : null);

function led(text: string) { el.led.text = `${text}      `; }

// ─────────────────────────────────────────────────────────────── feel bindings (same for ghost and hand)
el.power.addEventListener('hy-change', (e: any) => {
  powered = !!e.detail.value; fx()?.power(powered);
  led(powered ? 'POWER  ·  HAfx STUDIO' : 'STANDBY');
  if (!powered) { lampOn = false; el.lamp.on = false; specimen.s.lamp = 0; }
});
el.lamp.addEventListener('hy-change', (e: any) => {
  lampOn = !!e.detail.value; fx()?.lamp(lampOn);
  specimen.s.lamp = lampOn && powered ? 1 : 0;
  if (lampOn && !powered) led('NO POWER');
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
    const v = clamp(e.detail.value / 100), now = performance.now() / 1000;
    const F = fx();
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
    if (!quiet) pulseTimer = window.setTimeout(() => beat(), 150); // hear and feel the new character
  });
}
function beat(amt = 1) {
  if (!feels || !feel) return;
  const t = feel.now + 0.01; feels.pulse(voice, t, amt); specimen.pulse(t, amt);
}
el.rec.addEventListener('hy-change', () => {
  if (!feels || !feel) return;
  feels.rec(); led('REC  ●');
  if (!specimen.divided && lampOn && powered) { const t = feel.now + 0.25; feels.mitosis(t, 1.5); specimen.divide(t, 1.5); }
  setTimeout(() => (el.rec.on = false), 900);
});
el.transport.addEventListener('hy-change', (e: any) => {
  if (!feels || !feel) return;
  feels.key();
  if (e.detail.action === 'play') {
    led('PLAY  ▶');
    const t0 = feel.now + 0.08;
    const end = feels.groove(t0, voice, 124, (kind, t) => {
      if (kind === 'K') specimen.pulse(t, 1, specimen.divided ? 0 : -1);
      else if (kind === 'S') specimen.pulse(t, 0.8, specimen.divided ? 1 : -1);
      else specimen.flick(t);
    });
    void end;
  }
});
el.field.addEventListener('pointerdown', (e) => { if (e.isTrusted && powered && lampOn) beat(); });

// ─────────────────────────────────────────────────────────────── the ghost hand
const sr = (host: any, sel: string) => host.renderRoot.querySelector(sel) as HTMLElement;
const center = (n: Element) => { const r = n.getBoundingClientRect(); return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; };
const gpos = { x: innerWidth / 2, y: innerHeight * 0.75 };
let lastPress = -1;
function placeGhost(x: number, y: number, press = 0) {
  gpos.x = x; gpos.y = y;
  el.ghost.style.transform = `translate3d(${(x - 20).toFixed(1)}px, ${(y - 20).toFixed(1)}px, 0) scale(${(1 - 0.18 * press).toFixed(3)})`;
  const p = Math.round(press * 20) / 20; if (p !== lastPress) { lastPress = p; el.ghost.style.setProperty('--press', String(p)); }
}
const frame = () => new Promise<number>((r) => requestAnimationFrame(() => r(performance.now() / 1000)));
async function waitUntil(t: number) { while (!aborted && performance.now() / 1000 < t) await frame(); if (aborted) throw new Error('abort'); }
async function tween(dur: number, fn: (u: number) => void) {
  const t0 = performance.now() / 1000;
  for (;;) { if (aborted) throw new Error('abort'); const u = clamp((performance.now() / 1000 - t0) / dur); fn(u); if (u >= 1) break; await frame(); }
}
/** glide the fingertip to a point along a slight arc */
async function travel(x: number, y: number, dur = 0.45) {
  const x0 = gpos.x, y0 = gpos.y, lift = Math.min(60, Math.hypot(x - x0, y - y0) * 0.18);
  await tween(dur, (u) => { const e = easeIO(u); placeGhost(lerp(x0, x, e), lerp(y0, y, e) - Math.sin(Math.PI * e) * lift); });
}
/** bring a control into view (phone rack scrolls; desktop is static) */
async function show(n: Element) {
  const g = n.closest('.grp') as HTMLElement | null;
  if (g && el.rack.scrollWidth > el.rack.clientWidth + 4) {
    const target = g.offsetLeft - (el.rack.clientWidth - g.offsetWidth) / 2;
    const s0 = el.rack.scrollLeft; await tween(0.35, (u) => (el.rack.scrollLeft = lerp(s0, target, easeIO(u))));
  }
}
function ptr(type: string, target: EventTarget, x: number, y: number) {
  const ev = new PointerEvent(type, { clientX: x, clientY: y, bubbles: true, composed: true, cancelable: true, pointerId: 77, pointerType: 'touch', isPrimary: true, button: 0, buttons: type === 'pointerup' ? 0 : 1 });
  target.dispatchEvent(ev);
}
async function tap(host: any, sel: string) {
  const n = sr(host, sel); await show(host); const c = center(n);
  await travel(c.x, c.y);
  await tween(0.07, (u) => placeGhost(c.x, c.y, u));
  n.click();
  await tween(0.12, (u) => placeGhost(c.x, c.y, 1 - u));
}
async function turnKnob(host: any, to: number[], durs: number[]) {
  const n = sr(host, '.knob-root'); await show(host); (n as any).setPointerCapture = () => {};
  const c = center(n); await travel(c.x + 26, c.y - 30);
  placeGhost(c.x + 26, c.y - 30, 1);
  const y0 = c.y - 30; let v = host.value / 100;
  ptr('pointerdown', n, c.x + 26, y0); const v0 = v;
  for (let i = 0; i < to.length; i++) {
    const from = v; await tween(durs[i], (u) => { v = lerp(from, to[i], easeOut(u)); const y = y0 - (v - v0) * 165; ptr('pointermove', window, c.x + 26, y); placeGhost(c.x + 26 + Math.sin(u * 3) * 2, y0 - (v - v0) * 40, 1); });
  }
  ptr('pointerup', window, c.x, y0); await tween(0.1, (u) => placeGhost(gpos.x, gpos.y, 1 - u));
}
async function slideFader(host: any, to: number[], durs: number[], holds: number[] = []) {
  const wrap = sr(host, '.vf'), slot = sr(host, '.vf-slot'); await show(host);
  const yFor = (v: number) => { const r = slot.getBoundingClientRect(); return r.top + (1 - v) * (r.height - 22); };
  const x = center(slot).x; let v = host.value / 100;
  await travel(x, yFor(v) + 11); placeGhost(x, yFor(v) + 11, 1);
  ptr('pointerdown', wrap, x, yFor(v));
  for (let i = 0; i < to.length; i++) {
    const from = v; await tween(durs[i], (u) => { v = lerp(from, to[i], easeIO(u)); ptr('pointermove', window, x, yFor(v)); placeGhost(x, yFor(v) + 11, 1); });
    if (holds[i]) await tween(holds[i], () => {});
  }
  ptr('pointerup', window, x, yFor(v)); await tween(0.1, (u) => placeGhost(gpos.x, gpos.y, 1 - u));
}
async function pushStick(host: any, path: { x: number; y: number; d: number; hold?: number }[]) {
  const housing = sr(host, '.j2-housing'), bowl = sr(host, '.j2-bowl'); await show(host);
  const c = center(housing), R = bowl.getBoundingClientRect().width / 2;
  await travel(c.x, c.y); placeGhost(c.x, c.y, 1);
  ptr('pointerdown', housing, c.x, c.y);
  let px = 0, py = 0;
  for (const p of path) {
    const fx = px, fy = py; await tween(p.d, (u) => { const e = easeIO(u); px = lerp(fx, p.x, e); py = lerp(fy, p.y, e); const X = c.x + px * R, Y = c.y - py * R; ptr('pointermove', housing, X, Y); placeGhost(X, Y, 1); });
    if (p.hold) await tween(p.hold, () => ptr('pointermove', housing, c.x + px * R + Math.sin(performance.now() / 40) * 0.5, c.y - py * R));
  }
  ptr('pointerup', housing, c.x + px * R, c.y - py * R);
  await tween(0.12, (u) => placeGhost(gpos.x, gpos.y, 1 - u));
}
async function setVoice(row: number, act: number) {
  const rows = el.voice.renderRoot.querySelectorAll('.sset-track') as NodeListOf<HTMLElement>;
  const tr = rows[row].getBoundingClientRect(), x = tr.left + tr.width * (0.2 + 0.2 * act), y = tr.top + tr.height / 2;
  const dot = rows[row].querySelector('.sset-dot') as HTMLElement, d = center(dot);
  await travel(d.x, d.y, 0.32); placeGhost(d.x, d.y, 1);
  await tween(0.16, (u) => placeGhost(lerp(d.x, x, easeIO(u)), y, 1));
  el.voice.setDetent(row, act);
  await tween(0.08, (u) => placeGhost(x, y, 1 - u));
}

/** put the knob, fader and voice back to their opening positions without a sound */
function resetControls() {
  quiet = true;
  const k = sr(el.focus, '.knob-root'); (k as any).setPointerCapture = () => {};
  const c = center(k), dv = 0.12 - el.focus.value / 100;
  ptr('pointerdown', k, c.x, c.y); ptr('pointermove', window, c.x, c.y - dv * 165); ptr('pointerup', window, c.x, c.y);
  const wrap = sr(el.zoom, '.vf'), slot = sr(el.zoom, '.vf-slot'), r = slot.getBoundingClientRect(), y = r.top + (1 - 0.1) * (r.height - 22);
  ptr('pointerdown', wrap, r.left + r.width / 2, y); ptr('pointerup', window, r.left + r.width / 2, y);
  for (let i = 0; i < 5; i++) el.voice.setDetent(i, 0);
  el.voice.hot = -1;
  voice = [0.2, 0.2, 0.2, 0.2, 0.2];
  quiet = false;
}

// ─────────────────────────────────────────────────────────────── the performance
async function perform() {
  auto = true; aborted = false; document.body.classList.add('auto');
  el.ghost.hidden = false; placeGhost(innerWidth * 0.5, innerHeight * 0.92);
  el.ghost.style.opacity = '0'; await tween(0.3, (u) => (el.ghost.style.opacity = String(u)));
  try {
    led('HAfx STUDIO');
    await tap(el.power, '.pbtn');                                   // 1  power: a heavy latch
    await tap(el.lamp, '.stog');                                    // 2  lamp: two-stage snap
    led('FOCUS');
    await turnKnob(el.focus, [0.7, FOCUS_TRUE + 0.004], [1.25, 0.6]); // 3  focus: detents climbing, a notch at true focus
    await slideFader(el.zoom, [1, 0.62], [0.9, 0.55], [0.18]);     // 4  zoom: friction, ridges, the end stop
    led('STAGE');
    await pushStick(el.stick, [{ x: -0.62, y: -0.36, d: 0.25, hold: 0.45 }, { x: -1, y: -0.5, d: 0.16, hold: 0.12 }, { x: -0.5, y: -0.25, d: 0.12, hold: 0.1 }]); // 5  stage: tension, gate, spring
    led('VOICE  ·  DESIGN THE PULSE');
    await show(el.voice);
    beat(); await waitUntil(performance.now() / 1000 + 0.45);       // 6  the cell's own pulse: soft, warm, dry
    await setVoice(1, 3); await waitUntil(performance.now() / 1000 + 0.32); // hard
    await setVoice(0, 3); await waitUntil(performance.now() / 1000 + 0.32); // bright
    await setVoice(2, 2); await waitUntil(performance.now() / 1000 + 0.36); // wet
    await setVoice(4, 2); await waitUntil(performance.now() / 1000 + 0.36); // driven
    await setVoice(3, 3); await waitUntil(performance.now() / 1000 + 0.4);  // wide
    await tap(el.rec, '.crater-cell');                              // 7  record: the cell divides
    await waitUntil(performance.now() / 1000 + 1.55);
    await tap(el.transport, '.seg[aria-label="Play"]');                   // 8  play: the daughters' rhythm
    await waitUntil(performance.now() / 1000 + 2.0);
    await tap(el.lamp, '.stog');                                    // 9  lamp off …
    await waitUntil(performance.now() / 1000 + 0.25);
    finale();                                                       // 10 … HAfx Studio
    await travel(innerWidth * 0.5, innerHeight * 1.05, 0.6);
  } catch (_) { /* a real hand took over */ }
  el.ghost.hidden = true; auto = false; document.body.classList.remove('auto');
  el.again.hidden = false;
}
function finale() {
  if (!feels || !feel) return;
  const t = feel.now + 0.05, steps = 22, step = 0.07;
  const hit = feels.reveal(t, steps, step);
  specimen.reveal(t, steps, step, hit);
  led('HAfx STUDIO      FEEL THE SOUND');
}

// a real touch anywhere ends the performance and hands the instrument over
addEventListener('pointerdown', (e) => { if (e.isTrusted && auto) aborted = true; }, true);

// ─────────────────────────────────────────────────────────────── start / loop
async function start() {
  if (!feel) {
    feel = new Feel(); feels = new Feels(feel); if ((window as any).__trace) traceFeels(feels);
    feel.tapticEl = $('#tapticLabel');
    setInterval(() => feel!.pump(), 10);
  }
  await feel.resume();
  feel.touch = touchKind(); feel.setBody(feel.touch === 'vibrate' || feel.touch === 'gamepad' ? 0.2 : 0.42);
  paintPaths();
  el.begin.hidden = true; el.again.hidden = true;
  // reset the instrument to its opening state
  if (el.power.on) { el.power.on = false; powered = false; }
  if (el.lamp.on) { el.lamp.on = false; lampOn = false; }
  specimen.reset(); specimen.s.lamp = 0;
  resetControls();
  perform();
}
el.begin.addEventListener('click', start);
el.again.addEventListener('click', (e) => { e.stopPropagation(); start(); });

const buf = new Float32Array(1024);
const perf = { spec: 0, dt: 16, last: 0 };
(window as any).__studio = { perf, get feel() { return feel; }, get feels() { return feels; }, specimen, el, log: [] as string[] };
// debug trace of every feel (read by the test harness; harmless otherwise)
const traceFeels = (fs: Feels) => {
  for (const k of Object.getOwnPropertyNames(Feels.prototype)) {
    if (k === 'constructor') continue; const orig = (fs as any)[k].bind(fs);
    (fs as any)[k] = (...a: any[]) => { const L = (window as any).__studio.log; if (L.length < 4000) L.push(`${(feel!.now).toFixed(3)} ${k}${k === 'friction' || k === 'tension' ? '' : ' ' + a.filter((x) => typeof x !== 'function' && !Array.isArray(x)).map((x) => typeof x === 'number' ? x.toFixed(2) : x).join(' ')}`); return orig(...a); };
  }
};
function loop() {
  const fz = (window as any).__studio?.freeze;
  const t = fz ?? (feel ? feel.now : performance.now() / 1000);
  if (feel) {
    specimen.s.haptic = feel.bus.level();
    feel.analyser.getFloatTimeDomainData(buf);
    let s = 0; for (let i = 0; i < buf.length; i++) s += buf[i] * buf[i];
    const rms = Math.sqrt(s / buf.length), lvl = clamp(Math.pow(rms * 3.2, 0.6));
    el.vu.level = powered ? lvl : 0; el.meter.level = powered ? lvl : 0; el.meter.levelR = powered ? clamp(lvl * (0.9 + 0.2 * voice[3])) : 0;
    paintPaths(specimen.s.haptic);
  } else { el.vu.level = 0; el.meter.level = 0; el.meter.levelR = 0; }
  const f0 = performance.now(); specimen.frame(t); perf.spec = perf.spec * 0.95 + (performance.now() - f0) * 0.05;
  const now = performance.now(); if (perf.last) perf.dt = perf.dt * 0.95 + (now - perf.last) * 0.05; perf.last = now;
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);

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
  el.tip.innerHTML = `Every control carries its own HAfx feel.<br><b>sound</b> · its voice<br><b>body</b> · its haptic as bass you feel<br><b>touch</b> · ${touch}<br><b>sight</b> · its haptic as light and shake<br>After the intro, play the instrument. Tap the cell to hear its pulse.`;
});
addEventListener('gamepadconnected', () => { if (feel) feel.touch = touchKind(); paintPaths(); });
paintPaths();
