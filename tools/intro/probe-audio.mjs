import chromium from '@sparticuz/chromium'; import puppeteer from 'puppeteer-core';
const b = await puppeteer.launch({ executablePath: await chromium.executablePath(), args: chromium.args, headless: true });
const pg = await b.newPage(); await pg.evaluateOnNewDocument(() => { window.__HAFX_RENDER__ = true; });
await pg.goto('file://' + process.cwd() + '/dist/hafx-intro.html'); await pg.waitForFunction(() => window.HAFX);
console.log(JSON.stringify(await pg.evaluate(async () => {
  const ctx = new OfflineAudioContext(1, 48000 * 10, 48000);
  const r = await HAFX.renderAudio(48000, 0, true, true);
  // onset check: first audio sample above threshold after each transient
  const wav = Uint8Array.from(atob(r.wav), c => c.charCodeAt(0)); const dv = new DataView(wav.buffer);
  const n = (wav.length - 44) / 4, L = (i) => dv.getInt16(44 + i * 4, true) / 32767;
  const env = new Float32Array(n); for (let i = 0; i < n; i++) env[i] = Math.abs(L(i));
  const win = (a, len) => { let m = 0; for (let i = a; i < a + len && i < n; i++) m = Math.max(m, env[i]); return m; };
  const onsets = HAFX.transients.map(tr => { const s0 = Math.round(tr.t * 48000); for (let k = s0 - 960; k < s0 + 2400; k += 24) { const before = win(k - 1440, 1440), now = win(k, 96); if (now > 0.03 && now > before * 1.8 + 0.01) return +((k - s0) / 48).toFixed(1); } return null; });
  let pk = 0, pt = 0; for (let i = 0; i < n; i++) if (env[i] > pk) { pk = env[i]; pt = i / 48000; }
  const peaks = {}; [[0,1.5],[1.5,3.45],[3.45,5.55],[5.8,6.3],[6.3,8.3],[8.3,10]].forEach(([a,z]) => peaks[a+'-'+z] = +win(Math.round(a*48000), Math.round((z-a)*48000)).toFixed(2));
  // RMS per act
  const rms = (a, z) => { let s = 0, c = 0; for (let i = Math.round(a * 48000); i < Math.round(z * 48000); i++) { s += L(i) ** 2; c++; } return +Math.sqrt(s / c).toFixed(3); };
  return { rawPeak: +r.peak.toFixed(3), peakAt: +pt.toFixed(3), peaks, onsetsMs: onsets, rms: { heart: rms(0.2, 1.4), mitosis: rms(1.6, 3.4), rise: rms(3.5, 5.5), vacuum: rms(5.6, 5.79), impact: rms(5.8, 6.3), echo: rms(6.3, 8.2), settle: rms(8.5, 9.4) } };
})));
await b.close();
