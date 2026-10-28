// Deterministic renderer: frames via the page's own drawFrame, audio via OfflineAudioContext.
import chromium from '@sparticuz/chromium';
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';
import { spawn } from 'node:child_process';
const [mode = 'stills', W = 1080, H = 1920, out = 'out'] = process.argv.slice(2);
const w = +W, h = +H;
const b = await puppeteer.launch({ executablePath: await chromium.executablePath(), args: [...chromium.args, '--autoplay-policy=no-user-gesture-required'], headless: true });
const pg = await b.newPage();
pg.on('console', (m) => console.log('[page]', m.text())); pg.on('pageerror', (e) => console.log('[pageerror]', e.message));
await pg.evaluateOnNewDocument(() => { window.__HAFX_RENDER__ = true; });
await pg.goto('file://' + process.cwd() + '/dist/hafx-intro.html');
await pg.waitForFunction(() => window.HAFX);
await pg.evaluate((w, h) => { const c = document.createElement('canvas'); c.width = w; c.height = h; c.id = 'r'; document.body.append(c); }, w, h);
fs.mkdirSync(out, { recursive: true });
const frame = (t) => pg.evaluate((t) => { const c = document.getElementById('r'); HAFX.drawTo(c, t, { fadeOut: true }); return c.toDataURL('image/png').split(',')[1]; }, t);
if (mode === 'stills') {
  for (const t of process.argv.slice(6).map(Number)) fs.writeFileSync(`${out}/t${t.toFixed(2)}.png`, Buffer.from(await frame(t), 'base64'));
} else if (mode === 'video') {
  const fps = 60, n = Math.round(10 * fps);
  const { wav, peak } = await pg.evaluate(() => HAFX.renderAudio(48000, 0.45));
  console.log('audio peak', peak.toFixed(3));
  fs.writeFileSync(`${out}/audio.wav`, Buffer.from(wav, 'base64'));
  const ff = spawn('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'image2pipe', '-framerate', String(fps), '-i', '-', '-i', `${out}/audio.wav`,
    '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '16', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '256k', '-shortest', '-movflags', '+faststart', `${out}/hafx-intro-${w}x${h}.mp4`], { stdio: ['pipe', 'inherit', 'inherit'] });
  for (let i = 0; i < n; i++) { const buf = Buffer.from(await frame(i / fps), 'base64'); if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once('drain', r)); }
  ff.stdin.end(); await new Promise((r) => ff.on('close', r));
} else if (mode === 'probe') {
  console.log(JSON.stringify(await pg.evaluate(() => ({ cells: HAFX.wordCells, transients: HAFX.transients.length, peakI: Math.max(...Array.from({ length: 10000 }, (_, i) => HAFX.hapticAt(i / 1000))) }))));
}
await b.close();
