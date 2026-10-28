// The ghost fingertip, animated on Disney's principles:
//  squash & stretch — stretches along fast travel, squashes flat on contact
//  anticipation     — rises and pulls back before every strike
//  arcs             — travels on curved paths, never straight lines
//  slow in/out      — every pose eases in and out
//  follow-through   — overshoots its rebound and settles
//  solid drawing    — height off the glass shown by a shadow that separates and softens
// Only transform and opacity change, so it never repaints.

const clamp = (x: number, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a: number, b: number, u: number) => a + (b - a) * u;
export const ease = (u: number) => { u = clamp(u); return u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2; };
export const easeIn = (u: number) => Math.pow(clamp(u), 2.4);
export const easeOutBack = (u: number, k = 1.9) => { u = clamp(u) - 1; return 1 + (k + 1) * u * u * u + k * u * u; };

export type P = { x: number; y: number };
export type Pose = P & { h: number };              // h: 0 pressed on the glass, 1 hovering, >1 wound up
type Seg = { b0: number; b1: number; at: (b: number, from: Pose) => Pose };

export class Ghost {
  private segs: Seg[] = [];
  private last: Pose = { x: innerWidth / 2, y: innerHeight * 1.1, h: 1 };
  private prev = { x: 0, y: 0, t: 0 };
  private tip: HTMLElement; private shadow: HTMLElement;
  visible = false;

  constructor(private el: HTMLElement) {
    el.innerHTML = '<i class="g-shadow"></i><i class="g-tip"></i>';
    this.shadow = el.firstElementChild as HTMLElement; this.tip = el.lastElementChild as HTMLElement;
  }
  clear() { this.segs = []; }
  park(p: Pose) { this.last = { ...p }; }

  /** glide along an arc from wherever it is to a target, rising a little mid-flight */
  travel(b0: number, b1: number, to: () => P, lift = 0.28) {
    let from: Pose | null = null;
    this.segs.push({ b0, b1, at: (b, cur) => {
      from ??= { ...cur };
      const u = ease((b - b0) / (b1 - b0)), T = to(), dx = T.x - from.x, dy = T.y - from.y, d = Math.hypot(dx, dy);
      const arc = Math.sin(Math.PI * u) * Math.min(90, d * 0.22);
      // bow the path upward-and-outward (perpendicular, always to the same side)
      const nx = d ? -dy / d : 0, ny = d ? dx / d : 0, side = ny > 0 ? -1 : 1;
      return { x: lerp(from.x, T.x, u) + nx * arc * side, y: lerp(from.y, T.y, u) + ny * arc * side, h: lerp(from.h, 1, u) + lift * Math.sin(Math.PI * u) };
    } });
  }
  /** idle above a target, breathing on the beat */
  hover(b0: number, b1: number, at: () => P) {
    this.segs.push({ b0, b1, at: (b) => { const T = at(); return { x: T.x, y: T.y - 4 * Math.sin(b * Math.PI), h: 1 + 0.06 * Math.abs(Math.sin(b * Math.PI)) }; } });
  }
  /** anticipation → strike → contact → rebound with overshoot; contact lands exactly on beat b */
  press(b: number, at: () => P, wind = 0.5) {
    this.segs.push({ b0: b - wind, b1: b - 0.14, at: (bb) => { const T = at(), u = ease((bb - (b - wind)) / (wind - 0.14)); return { x: T.x + 3 * u, y: T.y - 12 * u, h: lerp(1, 1.42, u) }; } });
    this.segs.push({ b0: b - 0.14, b1: b, at: (bb) => { const T = at(), u = easeIn((bb - (b - 0.14)) / 0.14); return { x: T.x + 3 * (1 - u), y: T.y - 12 * (1 - u), h: lerp(1.42, 0, u) }; } });
    this.segs.push({ b0: b, b1: b + 0.18, at: () => ({ ...at(), h: 0 }) });
    this.segs.push({ b0: b + 0.18, b1: b + 0.7, at: (bb) => { const T = at(), u = easeOutBack((bb - (b + 0.18)) / 0.52); return { x: T.x, y: T.y - 6 * u, h: u }; } });
  }
  /** touch down (with a small anticipation), then follow a moving grip point */
  grab(b: number, at: () => P) {
    this.segs.push({ b0: b - 0.3, b1: b, at: (bb) => { const T = at(), u = (bb - (b - 0.3)) / 0.3; const h = u < 0.55 ? lerp(1, 1.3, ease(u / 0.55)) : lerp(1.3, 0, easeIn((u - 0.55) / 0.45)); return { x: T.x, y: T.y - 8 * Math.sin(Math.PI * u), h }; } });
  }
  drag(b0: number, b1: number, at: (b: number) => P) { this.segs.push({ b0, b1, at: (b) => ({ ...at(b), h: 0 }) }); }
  lift(b: number, at: () => P, dur = 0.5) {
    this.segs.push({ b0: b, b1: b + dur, at: (bb) => { const T = at(), u = easeOutBack((bb - b) / dur); return { x: T.x, y: T.y - 8 * u, h: u }; } });
  }

  /** evaluate the performance at beat b and draw */
  frame(b: number, now: number) {
    if (!this.visible) { this.el.style.opacity = '0'; return; }
    // first remember where each finished segment left us, so the next one starts there
    for (const s of this.segs) if (b >= s.b1 && !(s as any).done) { (s as any).done = true; this.last = s.at(s.b1 - 1e-6, this.last); }
    let pose = this.last;
    for (const s of this.segs) if (b >= s.b0 && b < s.b1) { pose = s.at(b, this.last); break; }
    this.draw(pose, now);
  }

  private draw(p: Pose, now: number) {
    const dt = Math.max(1 / 240, now - this.prev.t), vx = (p.x - this.prev.x) / dt, vy = (p.y - this.prev.y) / dt;
    this.prev = { x: p.x, y: p.y, t: now };
    const speed = Math.hypot(vx, vy), h = Math.max(0, p.h);
    // stretch along velocity when airborne; squash flat when on the glass
    let sx = 1, sy = 1, rot = 0;
    if (h < 0.12) { const k = 1 - h / 0.12; sx = 1 + 0.2 * k; sy = 1 - 0.18 * k; }
    else { const st = Math.min(0.42, speed / 2600); sx = 1 + st; sy = 1 / (1 + st * 0.8); rot = Math.atan2(vy, vx); }
    const lift = h * 16, size = 1 + 0.12 * Math.min(1.4, h);
    this.el.style.opacity = '1';
    this.el.style.transform = `translate3d(${(p.x - 20).toFixed(1)}px,${(p.y - 20).toFixed(1)}px,0)`;
    this.tip.style.transform = `translate3d(0,${(-lift).toFixed(1)}px,0) rotate(${rot.toFixed(3)}rad) scale(${(sx * size).toFixed(3)},${(sy * size).toFixed(3)})`;
    // contact light: spreads and fades as the tip lifts, tightens and brightens as it lands
    this.shadow.style.transform = `translate3d(${(h * 5).toFixed(1)}px,${(h * 9).toFixed(1)}px,0) scale(${(0.9 + h * 0.6).toFixed(3)})`;
    this.shadow.style.opacity = Math.max(0.12, 0.85 - 0.55 * Math.min(1.3, h)).toFixed(3);
  }
}
