"""Faithful numpy port of core DefaultPatternRenderer.renderAudio (osc events only) to check the
intro's raw peak exactly as the Kotlin core will compute it (naive oscillators, RBJ biquad with
per-sample cutoff, Catmull-Rom curves, hard clamp)."""
import json, math, sys
import numpy as np
SR = 48000
p = json.load(open(sys.argv[1]))

def sampler(c):
    P = sorted(c['points'], key=lambda q: q['time']); mode = c.get('interpolation', 'LINEAR')
    T = np.array([q['time'] for q in P]); V = np.array([q['value'] for q in P])
    def f(t):
        t = np.asarray(t, float); out = np.empty_like(t)
        out[t <= T[0]] = V[0]; out[t >= T[-1]] = V[-1]
        mid = (t > T[0]) & (t < T[-1]); tm = t[mid]
        hi = np.searchsorted(T, tm, side='left'); lo = hi - 1
        u = (tm - T[lo]) / (T[hi] - T[lo])
        if mode == 'STEP': r = V[lo]
        elif mode == 'LINEAR': r = V[lo] + (V[hi] - V[lo]) * u
        else:
            p0 = V[np.maximum(0, lo - 1)]; p1 = V[lo]; p2 = V[hi]; p3 = V[np.minimum(len(V) - 1, hi + 1)]
            r = 0.5 * (2*p1 + (-p0+p2)*u + (2*p0-5*p1+4*p2-p3)*u**2 + (-p0+3*p1-3*p2+p3)*u**3)
        out[mid] = r; return out
    return f

def adsr(env, dur, t):
    a = env.get('attack', 0); d = env.get('decay', 0); s = min(1, max(0, env.get('sustain', 1))); r = env.get('release', 0)
    g = np.zeros_like(t)
    m = t <= a; g[m] = 1.0 if a <= 0 else t[m] / a
    m2 = (t > a) & (t <= a + d); g[m2] = s if d <= 0 else 1 + (s - 1) * ((t[m2] - a) / d)
    m3 = (t > a + d) & (t <= dur); g[m3] = s
    m4 = (t > max(dur, a + d)) & (t <= dur + r); g[m4] = 0 if r <= 0 else s * (1 - (t[m4] - dur) / r)
    return g

n = int(10.0 * SR) + SR
mix = np.zeros(n)
rng = np.random.default_rng(0x51EED)
for tr in p['tracks']:
    if tr['type'] != 'audio': continue
    cv = {c['parameter']: sampler(c) for c in tr.get('curves', [])}
    for e in tr['events']:
        env = e.get('envelope', {}); total = e['duration'] + env.get('release', 0)
        start = round(e['time'] * SR); L = round(total * SR)
        idx = np.arange(start, min(n, start + L)); absT = idx / SR; local = (idx - start) / SR
        semis = cv['AUDIO_PITCH'](absT) if 'AUDIO_PITCH' in cv else 0.0
        freq = e['frequencyHz'] * 2 ** (np.asarray(semis) / 12) * np.ones_like(absT)
        phase = np.concatenate([[0], np.cumsum(freq / SR)[:-1]]) % 1.0
        w = e['waveform']
        s = {'SINE': np.sin(2*np.pi*phase), 'SQUARE': np.where(phase < 0.5, 1.0, -1.0), 'SAW': 2*phase - 1,
             'TRIANGLE': 4*np.abs(phase - 0.5) - 1, 'NOISE': rng.uniform(-1, 1, len(idx))}[w]
        if 'filter' in e:
            F = e['filter']; q = max(1e-4, F.get('resonance', 0.7)); typ = F['type']
            cut = cv['AUDIO_FILTER_CUTOFF'](absT) if 'AUDIO_FILTER_CUTOFF' in cv else np.full(len(idx), F['cutoffHz'])
            cut = np.clip(cut, 10, SR * 0.45); y = np.empty_like(s); z1 = z2 = 0.0
            w0 = 2*np.pi*cut/SR; cw = np.cos(w0); al = np.sin(w0)/(2*q); a0 = 1 + al
            if typ == 'LOWPASS': b0 = (1-cw)/2; b1 = 1-cw; b2 = b0
            elif typ == 'HIGHPASS': b0 = (1+cw)/2; b1 = -(1+cw); b2 = b0
            else: b0 = al; b1 = 0*al; b2 = -al
            B0, B1, B2, A1, A2 = b0/a0, b1/a0, b2/a0, -2*cw/a0, (1-al)/a0
            for i in range(len(s)):
                x = s[i]; yy = B0[i]*x + z1; z1 = B1[i]*x - A1[i]*yy + z2; z2 = B2[i]*x - A2[i]*yy; y[i] = yy
            s = y
        gm = cv['AUDIO_GAIN'](absT) if 'AUDIO_GAIN' in cv else 1.0
        mix[idx] += s * e['gain'] * gm * adsr(env, e['duration'], local)
pk = np.abs(mix).max(); at = np.abs(mix).argmax() / SR
print(json.dumps({'kotlinPeak': round(float(pk), 3), 'at': round(at, 3), 'pinnedSamples': int((np.abs(np.clip(mix, -1, 1)) >= 0.999).sum())}))
