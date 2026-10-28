import chromium from '@sparticuz/chromium'; import puppeteer from 'puppeteer-core';
const b = await puppeteer.launch({ executablePath: await chromium.executablePath(), args: chromium.args, headless: true });
const pg = await b.newPage(); await pg.evaluateOnNewDocument(() => { window.__HAFX_RENDER__ = true; });
await pg.goto('file://' + process.cwd() + '/dist/hafx-intro.html'); await pg.waitForFunction(() => window.HAFX);
console.log(JSON.stringify(await pg.evaluate(() => {
  const res = []; let maxLen = 0;
  for (let t = 0; t < 10; t += 0.5) { const p = HAFX.vibrateChunk(t); maxLen = Math.max(maxLen, p.length); const on = p.reduce((s, v, i) => s + (i % 2 ? 0 : v), 0); res.push(on); }
  const imp = HAFX.vibrateChunk(5.75);
  return { maxEntries: maxLen, onMsPerHalfSecond: res, impactChunkFrom5_75: imp.slice(0, 8) };
})));
await b.close();
