// The specimen: a darkfield microscope field of view.
// World units are micrometres. Objects scatter light at their edges (darkfield), sit at
// depths, and blur by distance from the focal plane — so turning focus genuinely
// pulls the cell out of the soup while the debris around it softens.

const TAU = Math.PI * 2;
const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a: number, b: number, u: number) => a + (b - a) * u;
const ease = (u: number) => { u = clamp(u); return u * u * (3 - 2 * u); };
const hash = (i: number) => { const x = Math.sin(i * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };
const vn = (x: number, s: number) => { const i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f); return lerp(hash(i + s * 91.7), hash(i + 1 + s * 91.7), u) * 2 - 1; };
function rng(seed: number) { return () => { seed |= 0; seed = (seed + 0x6d2b79f5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

type RGB = [number, number, number];
const VIOLET: RGB = [142, 123, 255], CYAN: RGB = [8, 254, 213];
const mixc = (a: RGB, b: RGB, u: number): RGB => [lerp(a[0], b[0], u), lerp(a[1], b[1], u), lerp(a[2], b[2], u)];
const rgba = (c: RGB, a: number) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${clamp(a)})`;

interface Cell { x: number; y: number; z: number; r: number; seed: number; tint: number; drift: number }
interface Mote { x: number; y: number; z: number; r: number; seed: number; rod: boolean }

export interface SpecimenState {
  lamp: number;       // 0..1 target (the toggle)
  focus: number;      // 0..1 (knob)
  zoom: number;       // 0..1 (fader) → 10×..100×
  stick: { x: number; y: number }; // joystick, -1..1 (rate control on the stage)
  haptic: number;     // bus level, for sight
}

const MAIN_Z = 0.24;            // the cell's depth; true focus sits at knob ≈ 0.62
export const FOCUS_TRUE = (MAIN_Z + 1) / 2;

export class Specimen {
  s: SpecimenState = { lamp: 0, focus: 0.12, zoom: 0.1, stick: { x: 0, y: 0 }, haptic: 0 };
  private lampLevel = 0;
  private panX = 14; private panY = -8;   // µm, stage position
  private cells: Cell[] = []; private motes: Mote[] = [];
  private bins: HTMLCanvasElement[] = []; private bctx: CanvasRenderingContext2D[] = [];
  private c: CanvasRenderingContext2D; private W = 0; private H = 0; private R = 0; private dpr = 1;
  // events
  private pulses: { t: number; amt: number; who: number }[] = [];
  private mitosisAt = -1; private mitosisDur = 1.5;
  private revealAt = -1; private revealSteps = 22; private revealStep = 0.07; private revealHit = -1;
  private textMask: { x: number; y: number }[] = [];
  private lastT = 0;
  /** scripted stage position (µm) while the score plays; null = joystick rate control */
  panOverride: { x: number; y: number } | null = null;
  private gaze = { x: 0, y: 0 }; private gazeTarget = { x: 0, y: 0 };
  /** appeal: the nucleus looks toward whatever is being played (unit-ish vector in screen space) */
  lookAt(x: number, y: number) { const d = Math.hypot(x, y) || 1; this.gazeTarget = { x: x / d, y: y / d }; }

  constructor(private cv: HTMLCanvasElement) {
    this.c = cv.getContext('2d')!;
    const r = rng(7);
    for (let i = 0; i < 9; i++) {
      const a = r() * TAU, d = 55 + r() * 120;
      this.cells.push({ x: Math.cos(a) * d, y: Math.sin(a) * d, z: lerp(-0.95, 0.95, r()), r: 7 + r() * 8, seed: i * 17 + 3, tint: r(), drift: 0.4 + r() });
    }
    for (let i = 0; i < 260; i++) this.motes.push({ x: (r() - 0.5) * 420, y: (r() - 0.5) * 420, z: lerp(-1, 1, r()), r: 0.25 + Math.pow(r(), 3) * 1.4, seed: i, rod: i % 7 === 0 });
    for (let i = 0; i < 6; i++) { const b = document.createElement('canvas'); this.bins.push(b); this.bctx.push(b.getContext('2d')!); }
    this.resize();
  }

  resize() {
    const r = this.cv.getBoundingClientRect();
    this.dpr = Math.min(2, devicePixelRatio || 1);
    this.W = Math.max(1, Math.round(r.width * this.dpr)); this.H = Math.max(1, Math.round(r.height * this.dpr));
    this.cv.width = this.W; this.cv.height = this.H;
    this.R = Math.min(this.W, this.H) / 2 - 2 * this.dpr;
    this.buildText();
  }

  // ── events from the controls
  pulse(t: number, amt = 1, who = -1) { this.pulses.push({ t, amt, who }); }
  divide(t: number, dur: number) { this.mitosisAt = t; this.mitosisDur = dur; }
  private flicks: number[] = []; private flickNow = 0;
  flick(t: number) { this.flicks.push(t); }
  reveal(t: number, steps: number, step: number, hit: number) { this.revealAt = t; this.revealSteps = steps; this.revealStep = step; this.revealHit = hit; }
  reset() { this.flicks = []; this.pulses = []; this.mitosisAt = -1; this.revealAt = -1; this.revealHit = -1; this.panX = 14; this.panY = -8; }
  get divided() { return this.mitosisAt >= 0; }

  private mag() { return 10 * Math.pow(10, this.s.zoom); }
  private pxPerUm() { return (2 * this.R) / (2400 / this.mag()); }

  private buildText() {
    // dot-matrix "HAfx Studio" mask, grid in field-relative units
    const cv = document.createElement('canvas'), w = 200, h = 100; cv.width = w; cv.height = h;
    const x = cv.getContext('2d', { willReadFrequently: true })!;
    x.fillStyle = '#fff'; x.textAlign = 'center'; x.textBaseline = 'middle';
    x.font = '800 50px "Archivo", "Helvetica Neue", Arial, sans-serif'; x.fillText('HAfx', w / 2, 34);
    x.font = '800 21px "Archivo", "Helvetica Neue", Arial, sans-serif'; [...'STUDIO'].forEach((ch, i) => x.fillText(ch, w / 2 + (i - 2.5) * 22, 80));
    const d = x.getImageData(0, 0, w, h).data, pts: { x: number; y: number }[] = [];
    for (let j = 0; j < h; j += 3) for (let i = 0; i < w; i += 3) if (d[(j * w + i) * 4 + 3] > 90) pts.push({ x: (i - w / 2) / (w / 2), y: (j - h / 2) / (w / 2) });
    this.textMask = pts;
  }

  // ── render
  frame(t: number) {
    const dt = Math.min(0.05, Math.max(0, t - this.lastT)); this.lastT = t;
    const { c, W, H, R } = this, cx = W / 2, cy = H / 2, s = this.s;
    // lamp warm-up with filament flicker
    const target = s.lamp;
    this.lampLevel += (target - this.lampLevel) * (target > this.lampLevel ? 1 - Math.exp(-dt * 7) : 1 - Math.exp(-dt * 10));
    const L = this.lampLevel * (1 + 0.06 * vn(t * 37, 3) * (1 - this.lampLevel));
    this.pulses = this.pulses.filter((p) => p.t > t - 1 || p.t > t); this.flicks = this.flicks.filter((f) => f > t - 0.5);
    this.flickNow = 0; for (const f of this.flicks) if (t >= f) this.flickNow = Math.max(this.flickNow, Math.exp(-(t - f) / 0.12));
    const gk = 1 - Math.exp(-dt * 4); this.gaze.x += (this.gazeTarget.x - this.gaze.x) * gk; this.gaze.y += (this.gazeTarget.y - this.gaze.y) * gk;
    // stage pan (joystick is rate control, like a motorised stage)
    const sp = 60 / Math.sqrt(this.mag() / 10);
    if (this.panOverride) { this.panX = this.panOverride.x; this.panY = this.panOverride.y; }
    else { this.panX += s.stick.x * sp * dt; this.panY -= s.stick.y * sp * dt; }
    this.panX = clamp(this.panX, -140, 140); this.panY = clamp(this.panY, -140, 140);

    c.setTransform(1, 0, 0, 1, 0, 0); c.clearRect(0, 0, W, H);
    const shake = Math.pow(s.haptic, 1.6) * 3.2 * this.dpr;
    c.save();
    c.translate(vn(t * 41, 1) * shake, vn(t * 41, 2) * shake);
    // field stop
    c.beginPath(); c.arc(cx, cy, R, 0, TAU); c.save(); c.clip();
    c.fillStyle = '#020104'; c.fillRect(0, 0, W, H);
    const lampCol: RGB = mixc([255, 176, 110], [226, 220, 255], ease(this.lampLevel * 1.15));
    // condenser haze
    const hz = c.createRadialGradient(cx, cy, 0, cx, cy, R);
    hz.addColorStop(0, rgba(mixc(lampCol, VIOLET, 0.55), 0.09 * L)); hz.addColorStop(1, rgba(VIOLET, 0.02 * L));
    c.fillStyle = hz; c.fillRect(0, 0, W, H);

    const zf = lerp(-1, 1, s.focus), ppu = this.pxPerUm(), magK = Math.sqrt(this.mag() / 30);
    const blurOf = (z: number) => Math.abs(z - zf) * 30 * magK * this.dpr;
    const toPx = (x: number, y: number): [number, number] => [cx + (x - this.panX) * ppu, cy + (y - this.panY) * ppu];

    // depth bins for motes + neighbour cells, back to front
    const NB = this.bins.length;
    for (let b = 0; b < NB; b++) {
      const zc = lerp(-1, 1, (b + 0.5) / NB), blur = blurOf(zc), sc = clamp(1 / (1 + blur * 0.28), 0.06, 1);
      const bw = Math.max(2, Math.round(W * sc)), bh = Math.max(2, Math.round(H * sc));
      const bc = this.bins[b], x = this.bctx[b];
      if (bc.width !== bw || bc.height !== bh) { bc.width = bw; bc.height = bh; }
      x.setTransform(1, 0, 0, 1, 0, 0); x.clearRect(0, 0, bw, bh); x.setTransform(sc, 0, 0, sc, 0, 0);
      x.globalCompositeOperation = 'lighter';
      const fade = 1 / (1 + blur * 0.05);
      for (const m of this.motes) {
        if (Math.min(NB - 1, Math.floor(((m.z + 1) / 2) * NB)) !== b) continue;
        const bx = m.x + vn(t * 0.35 + m.seed, 1) * 3, by = m.y + vn(t * 0.35 + m.seed, 2) * 3;
        const [px, py] = toPx(bx, by); if (Math.hypot(px - cx, py - cy) > R + 40) continue;
        const rr = Math.max(0.6, m.r * ppu);
        x.fillStyle = rgba(mixc(lampCol, CYAN, (m.seed % 5) / 9), 0.75 * L * fade);
        if (m.rod) { x.save(); x.translate(px, py); x.rotate(m.seed + t * 0.2 * vn(m.seed, 4)); x.beginPath(); x.ellipse(0, 0, rr * 2.6, rr * 0.9, 0, 0, TAU); x.lineWidth = Math.max(1, rr * 0.35); x.strokeStyle = x.fillStyle; x.stroke(); x.restore(); }
        else { x.beginPath(); x.arc(px, py, rr, 0, TAU); x.fill(); }
      }
      for (const cl of this.cells) {
        if (Math.min(NB - 1, Math.floor(((cl.z + 1) / 2) * NB)) !== b) continue;
        const [px, py] = toPx(cl.x + vn(t * 0.12 + cl.seed, 5) * 4 * cl.drift, cl.y + vn(t * 0.12 + cl.seed, 6) * 4 * cl.drift);
        if (Math.hypot(px - cx, py - cy) > R + cl.r * ppu * 2) continue;
        this.drawCell(x, px, py, cl.r * ppu, t, cl.seed, L * fade * 0.8, lampCol, cl.tint, 0, 0, 0.6);
      }
      c.globalCompositeOperation = 'lighter';
      c.imageSmoothingQuality = 'high';
      c.drawImage(bc, 0, 0, bw, bh, 0, 0, W, H);
      c.globalCompositeOperation = 'source-over';
    }

    // the main cell (and its daughters), on its own focus layer
    {
      const blur = blurOf(MAIN_Z), sc = clamp(1 / (1 + blur * 0.28), 0.06, 1);
      const bc = this.bins[0]; // reuse: bins already composited
      const bw = Math.max(2, Math.round(W * sc)), bh = Math.max(2, Math.round(H * sc));
      if (bc.width !== bw || bc.height !== bh) { bc.width = bw; bc.height = bh; }
      const x = this.bctx[0]; x.setTransform(1, 0, 0, 1, 0, 0); x.clearRect(0, 0, bw, bh); x.setTransform(sc, 0, 0, sc, 0, 0);
      x.globalCompositeOperation = 'lighter';
      this.drawMain(x, toPx, ppu, t, L, lampCol);
      c.globalCompositeOperation = 'lighter';
      c.drawImage(bc, 0, 0, bw, bh, 0, 0, W, H);
      c.globalCompositeOperation = 'source-over';
    }

    // exaggeration: the division pops with a flash and a ring that runs to the field stop
    if (this.mitosisAt >= 0) {
      const a = t - (this.mitosisAt + this.mitosisDur);
      if (a >= 0 && a < 0.7) {
        const [mx, my] = toPx(0, 0);
        c.globalCompositeOperation = 'lighter';
        c.fillStyle = rgba(mixc(VIOLET, CYAN, 0.5), 0.32 * Math.exp(-a / 0.07) * Math.max(0.3, L)); c.fillRect(0, 0, W, H);
        c.beginPath(); c.arc(mx, my, 16 * ppu + a * R * 1.8, 0, TAU); c.lineWidth = (2 + 14 * Math.exp(-a / 0.12)) * this.dpr;
        c.strokeStyle = rgba(CYAN, 0.55 * (1 - a / 0.7)); c.stroke();
        c.globalCompositeOperation = 'source-over';
      }
    }

    // reveal: the lamp has gone dark; the field becomes a dot matrix
    if (this.revealAt >= 0 && t >= this.revealAt) this.drawReveal(c, cx, cy, R, t);

    // vignette + field-stop diffraction rim
    const vg = c.createRadialGradient(cx, cy, R * 0.55, cx, cy, R);
    vg.addColorStop(0, 'rgba(0,0,0,0)'); vg.addColorStop(1, 'rgba(0,0,0,0.78)');
    c.fillStyle = vg; c.fillRect(0, 0, W, H);
    c.restore(); // clip
    c.beginPath(); c.arc(cx, cy, R, 0, TAU);
    c.lineWidth = 1.2 * this.dpr; c.strokeStyle = rgba(mixc(lampCol, VIOLET, 0.6), 0.12 + 0.35 * L + 0.4 * s.haptic); c.stroke();
    const bev = c.createLinearGradient(cx - R, cy - R, cx + R, cy + R);
    bev.addColorStop(0, 'rgba(255,255,255,0.32)'); bev.addColorStop(0.45, 'rgba(255,255,255,0.05)'); bev.addColorStop(1, 'rgba(0,0,0,0)');
    c.beginPath(); c.arc(cx, cy, R + 4 * this.dpr, 0, TAU); c.lineWidth = 3 * this.dpr; c.strokeStyle = bev; c.stroke();
    const sh = c.createLinearGradient(cx - R, cy - R, cx + R, cy + R);
    sh.addColorStop(0, 'rgba(0,0,0,0)'); sh.addColorStop(1, 'rgba(0,0,0,0.85)');
    c.beginPath(); c.arc(cx, cy, R + 1.2 * this.dpr, 0, TAU); c.lineWidth = 2.4 * this.dpr; c.strokeStyle = sh; c.stroke();
    c.restore();
  }

  private pulseEnv(t: number, who: number) {
    let e = 0;
    for (const p of this.pulses) { if (p.who !== -1 && who !== -1 && p.who !== who) continue; const a = t - p.t; if (a >= 0 && a < 0.6) e += p.amt * (Math.exp(-a / 0.07) + 0.55 * Math.exp(-Math.pow((a - 0.17) / 0.05, 2))); }
    return Math.min(1.4, e);
  }

  /** squash & stretch: a damped wobble after each pulse (squash on the hit, stretch on the rebound) */
  private pulseWob(t: number, who: number) {
    let w = 0;
    for (const p of this.pulses) { if (p.who !== -1 && who !== -1 && p.who !== who) continue; const a = t - p.t; if (a >= 0 && a < 0.7) w += p.amt * Math.exp(-a / 0.12) * Math.cos((a * TAU) / 0.22); }
    return clamp(w, -1.2, 1.2);
  }

  private drawMain(x: CanvasRenderingContext2D, toPx: (x: number, y: number) => [number, number], ppu: number, t: number, L: number, lampCol: RGB) {
    const R0 = 16, axis = 0.55;
    const p = this.mitosisAt < 0 ? 0 : clamp((t - this.mitosisAt) / this.mitosisDur);
    const split = this.mitosisAt >= 0 && t >= this.mitosisAt + this.mitosisDur;
    const [mx, my] = toPx(0, 0);
    if (!split) {
      const pe = this.pulseEnv(t, -1), Lc = Math.max(L, 0.34 * Math.min(1, pe)); // a heartbeat glows even in the dark
      if (p <= 0) {
        // anticipation: the cell gathers itself (contracts, nucleus brightens) just before it divides
        const k = this.mitosisAt >= 0 ? ease(clamp((t - (this.mitosisAt - 0.4)) / 0.4)) : 0;
        this.drawCell(x, mx, my, R0 * ppu * (1 + 0.035 * pe - 0.07 * k), t, 901, Lc, lampCol, 0.35, pe + 0.6 * k, 0, 1, this.pulseWob(t, -1) - 0.5 * k);
        return;
      }
      // mitosis: two lobes and a cleavage furrow, outlined as one membrane
      const e = ease(p), D = R0 * 0.62 * e, r = R0 * lerp(1, 0.8, e);
      const pinch = Math.sqrt(Math.max(0, r * r - D * D)) * Math.pow(p, 2.1) * 0.97;
      const ca = Math.cos(axis), sa = Math.sin(axis), N = 120;
      const wob = (th: number) => 1 + 0.03 * Math.sin(3 * th + t * 2.1) + 0.02 * Math.sin(5 * th - t * 2.7) + 0.018 * p * Math.sin(7 * th + t * 9);
      const outline = (th: number): [number, number, number, number] => {
        const ct = Math.cos(th), st = Math.sin(th);
        const r1 = D * ct + Math.sqrt(Math.max(0, r * r - D * D * st * st)), r2 = -D * ct + Math.sqrt(Math.max(0, r * r - D * D * st * st));
        let rr = Math.max(r1, r2) - pinch * (Math.exp(-Math.pow((th - Math.PI / 2) / 0.32, 2)) + Math.exp(-Math.pow((th - 1.5 * Math.PI) / 0.32, 2)));
        rr *= wob(th);
        const lx = ct * rr, ly = st * rr;
        return [mx + (lx * ca - ly * sa) * ppu, my + (lx * sa + ly * ca) * ppu, ct * ca - st * sa, ct * sa + st * ca];
      };
      x.beginPath();
      for (let i = 0; i <= N; i++) { const [px, py] = outline((i / N) * TAU); if (i === 0) x.moveTo(px, py); else x.lineTo(px, py); }
      x.closePath();
      const memCol = mixc(lampCol, VIOLET, 0.55);
      x.fillStyle = rgba(memCol, 0.05 * L); x.fill();
      x.lineWidth = Math.max(1, 0.7 * ppu); x.strokeStyle = rgba(memCol, (0.55 + 0.4 * p) * L); x.stroke();
      x.lineWidth = Math.max(2, 2.4 * ppu); x.strokeStyle = rgba(memCol, 0.08 * L); x.stroke();
      // cilia keep beating along the dividing membrane, faster as the furrow tightens
      x.lineWidth = Math.max(0.6, R0 * ppu * 0.012); x.strokeStyle = rgba(mixc(memCol, CYAN, 0.6), 0.42 * L); x.beginPath();
      const cl = R0 * 0.8 * ppu * 0.17;
      for (let i = 0; i < 90; i++) {
        const th = (i / 90) * TAU, [bx, by, nx, ny] = outline(th), tx = -ny, ty = nx, wave = Math.sin(th * 7 - t * (8 + 10 * p)) * 0.6;
        x.moveTo(bx, by); x.quadraticCurveTo(bx + nx * cl * 0.6 + tx * wave * cl * 0.4, by + ny * cl * 0.6 + ty * wave * cl * 0.4, bx + nx * cl + tx * wave * cl * 0.85, by + ny * cl + ty * wave * cl * 0.85);
      }
      x.stroke();
      // cytoplasm sorting itself into the two lobes
      for (let i = 0; i < 90; i++) {
        const sd = i % 2 ? 1 : -1, d0 = Math.sqrt(hash(901 * 7 + i)) * 0.78, a0 = hash(901 * 11 + i) * TAU + t * 0.25 * (i % 3 ? 1 : -0.7);
        const lx = sd * D * ease(p * 1.2) + Math.cos(a0) * d0 * r, ly = Math.sin(a0) * d0 * r * lerp(1, 0.92, p);
        const gx = mx + (lx * ca - ly * sa) * ppu, gy = my + (lx * sa + ly * ca) * ppu;
        x.beginPath(); x.arc(gx, gy, Math.max(0.5, r * ppu * (0.012 + 0.012 * hash(i + 901))), 0, TAU);
        x.fillStyle = rgba(mixc(lampCol, CYAN, hash(i) * 0.5), 0.42 * L); x.fill();
      }
      // two nuclei pulling apart, spindle glow between
      for (const sd of [-1, 1]) {
        const d = D * 0.9 * ease(clamp((p - 0.15) / 0.7));
        const [nx, ny] = [mx + sd * d * ca * ppu, my + sd * d * sa * ppu];
        this.nucleus(x, nx, ny, 5.2 * lerp(1, 0.78, e) * ppu, t, 77 + sd, L, lampCol, 0.3 * p);
      }
      const sg = x.createLinearGradient(mx - D * ca * ppu, my - D * sa * ppu, mx + D * ca * ppu, my + D * sa * ppu);
      sg.addColorStop(0, rgba(CYAN, 0)); sg.addColorStop(0.5, rgba(CYAN, 0.22 * L * Math.sin(Math.PI * p))); sg.addColorStop(1, rgba(CYAN, 0));
      x.strokeStyle = sg; x.lineWidth = 3 * ppu; x.beginPath(); x.moveTo(mx - D * ca * ppu, my - D * sa * ppu); x.lineTo(mx + D * ca * ppu, my + D * sa * ppu); x.stroke();
      return;
    }
    // daughters
    const a = t - (this.mitosisAt + this.mitosisDur), sep = R0 * lerp(0.62, 0.9, ease(a / 1.2));
    const ca = Math.cos(axis), sa = Math.sin(axis);
    [-1, 1].forEach((sd, k) => {
      const pe = this.pulseEnv(t, k);
      const wobble = Math.exp(-a / 0.35) * Math.sin(a * 28) * 0.06;
      const [px, py] = [mx + sd * sep * ca * ppu, my + sd * sep * sa * ppu];
      this.drawCell(x, px, py, R0 * 0.8 * ppu * (1 + wobble + 0.04 * pe), t, 950 + k, L, lampCol, k ? 0.75 : 0.2, pe, 1, 1, this.pulseWob(t, k));
    });
  }

  private nucleus(x: CanvasRenderingContext2D, nx: number, ny: number, nr: number, t: number, seed: number, L: number, lampCol: RGB, glow: number) {
    const col = mixc(lampCol, VIOLET, 0.7);
    x.beginPath(); x.arc(nx, ny, nr, 0, TAU); x.fillStyle = rgba(col, (0.07 + glow * 0.3) * L); x.fill();
    x.lineWidth = Math.max(1, nr * 0.08); x.strokeStyle = rgba(col, 0.55 * L); x.stroke();
    for (let i = 0; i < 18; i++) { const a = hash(seed + i) * TAU, d = Math.sqrt(hash(seed * 3 + i)) * nr * 0.8; x.beginPath(); x.arc(nx + Math.cos(a + t * 0.05) * d, ny + Math.sin(a + t * 0.05) * d, Math.max(0.5, nr * 0.05), 0, TAU); x.fillStyle = rgba(col, 0.35 * L); x.fill(); }
    x.beginPath(); x.arc(nx + nr * 0.22, ny - nr * 0.15, nr * 0.24, 0, TAU); x.fillStyle = rgba(mixc(VIOLET, [255, 255, 255], 0.3 + glow), (0.55 + glow) * L); x.fill();
  }

  /** one cell: membrane, cilia (metachronal wave), streaming granules, vacuoles, nucleus */
  private drawCell(x: CanvasRenderingContext2D, px: number, py: number, r: number, t: number, seed: number, L: number, lampCol: RGB, tint: number, pe: number, flickWho: number, detail: number, sq = 0) {
    if (r < 0.8) return;
    if (sq) { x.save(); x.translate(px, py); x.scale(1 + 0.075 * sq, 1 - 0.075 * sq); x.translate(-px, -py); }
    this.cellBody(x, px, py, r, t, seed, L, lampCol, tint, pe, flickWho, detail);
    if (sq) x.restore();
  }

  private cellBody(x: CanvasRenderingContext2D, px: number, py: number, r: number, t: number, seed: number, L: number, lampCol: RGB, tint: number, pe: number, flickWho: number, detail: number) {
    const memCol = mixc(mixc(lampCol, VIOLET, 0.5), CYAN, tint * 0.4), N = 72;
    const ph1 = hash(seed) * TAU, ph2 = hash(seed + 1) * TAU;
    const rad = (th: number) => r * (1 + 0.035 * Math.sin(3 * th + ph1 + t * 1.3) + 0.022 * Math.sin(5 * th + ph2 - t * 1.9));
    x.beginPath();
    for (let i = 0; i <= N; i++) { const th = (i / N) * TAU, rr = rad(th); const qx = px + Math.cos(th) * rr, qy = py + Math.sin(th) * rr; if (i) x.lineTo(qx, qy); else x.moveTo(qx, qy); }
    x.closePath();
    x.fillStyle = rgba(memCol, (0.035 + 0.06 * pe) * L); x.fill();
    x.lineWidth = Math.max(0.8, r * 0.045); x.strokeStyle = rgba(memCol, (0.6 + 0.35 * pe) * L); x.stroke();
    x.lineWidth = Math.max(1.6, r * 0.16); x.strokeStyle = rgba(memCol, (0.06 + 0.1 * pe) * L); x.stroke();
    if (detail < 0.3 || r < 6) return;
    // cilia
    const flick = this.flickNow;
    const nc = Math.round(70 * detail), cl = r * 0.17;
    x.lineWidth = Math.max(0.6, r * 0.012); x.strokeStyle = rgba(mixc(memCol, CYAN, 0.6), 0.42 * L * (1 + flick));
    x.beginPath();
    for (let i = 0; i < nc; i++) {
      const th = (i / nc) * TAU + ph1 * 0.1, rr = rad(th), wave = Math.sin(th * 7 - t * (8 + 20 * flick)) * (0.55 + 0.6 * flick);
      const bx = px + Math.cos(th) * rr, by = py + Math.sin(th) * rr, nx = Math.cos(th), ny = Math.sin(th), tx = -ny, ty = nx;
      x.moveTo(bx, by); x.quadraticCurveTo(bx + nx * cl * 0.6 + tx * wave * cl * 0.4, by + ny * cl * 0.6 + ty * wave * cl * 0.4, bx + nx * cl + tx * wave * cl * 0.85, by + ny * cl + ty * wave * cl * 0.85);
    }
    x.stroke(); void flickWho;
    // cytoplasm: streaming granules (cyclosis), jolted by a pulse
    const ng = Math.round(80 * detail);
    for (let i = 0; i < ng; i++) {
      const d0 = Math.sqrt(hash(seed * 7 + i)) * 0.82, a0 = hash(seed * 11 + i) * TAU + t * (0.18 + 0.25 * (1 - d0)) * (i % 2 ? 1 : 0.6);
      const d = d0 * r * (1 + 0.06 * pe);
      x.beginPath(); x.arc(px + Math.cos(a0) * d, py + Math.sin(a0) * d, Math.max(0.5, r * (0.012 + 0.012 * hash(i + seed))), 0, TAU);
      x.fillStyle = rgba(mixc(lampCol, CYAN, hash(i) * 0.5), (0.35 + 0.3 * pe) * L); x.fill();
    }
    // vacuoles
    for (let i = 0; i < 3; i++) { const a = hash(seed + 40 + i) * TAU + t * 0.07, d = r * (0.35 + 0.3 * hash(seed + 50 + i)); x.beginPath(); x.arc(px + Math.cos(a) * d, py + Math.sin(a) * d, r * (0.07 + 0.05 * hash(seed + 60 + i)), 0, TAU); x.lineWidth = Math.max(0.6, r * 0.01); x.strokeStyle = rgba(memCol, 0.3 * L); x.stroke(); }
    this.nucleus(x, px + r * (0.04 + 0.2 * this.gaze.x), py + r * (-0.04 + 0.2 * this.gaze.y), r * 0.33, t, seed + 5, L, lampCol, pe * 0.6);
  }

  private drawReveal(c: CanvasRenderingContext2D, cx: number, cy: number, R: number, t: number) {
    const a = t - this.revealAt, sweep = clamp(a / (this.revealSteps * this.revealStep));
    const hitA = this.revealHit >= 0 ? t - this.revealHit : -1;
    const flare = hitA >= 0 ? Math.exp(-hitA / 0.18) : 0;
    const S = R * 0.8, cell = S * (3 / 100);
    c.globalCompositeOperation = 'lighter';
    for (const p of this.textMask) {
      const u = (p.x + 1) / 2; if (u > sweep + 0.02) continue;
      const fresh = clamp(1 - (sweep - u) / 0.08);
      const k = clamp(u * 0.9 + (p.y + 0.3) * 0.25), col = mixc(VIOLET, CYAN, k);
      const br = Math.sin(t * 2 + p.x * 3) * 0.06 + 0.82 + 0.6 * fresh + 0.9 * flare;
      c.beginPath(); c.arc(cx + p.x * S, cy + p.y * S, cell * (0.4 + 0.1 * fresh + 0.18 * flare), 0, TAU);
      c.fillStyle = rgba(col, clamp(br)); c.fill();
    }
    if (flare > 0.01) { c.fillStyle = rgba(mixc(VIOLET, CYAN, 0.5), 0.25 * flare); c.fillRect(cx - R, cy - R, 2 * R, 2 * R); }
    c.globalCompositeOperation = 'source-over';
  }
}
