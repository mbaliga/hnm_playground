// Build the HAfx Studio intro into one self-contained HTML page.
//   HYLE=/path/to/hyle-design-system node build.mjs
// The Hyle components are bundled from source (the feel bindings import them as `hyle/...`).
import fs from 'node:fs';
import path from 'node:path';
const HYLE = path.resolve(process.env.HYLE || '../../hyle-design-system');
const { build } = await import(path.join(HYLE, 'node_modules/esbuild/lib/main.js'));
const r = await build({
  entryPoints: ['src/main.ts'], bundle: true, format: 'iife', minify: true, write: false, target: 'es2021',
  alias: { hyle: path.join(HYLE, 'src') }, nodePaths: [path.join(HYLE, 'node_modules')],
  tsconfig: path.join(HYLE, 'tsconfig.json'), logLevel: 'warning', legalComments: 'none',
});
const js = r.outputFiles[0].text.replace(/<\/script/gi, '<\\/script');
const page = fs.readFileSync('index.html', 'utf8').replace('/*__BUNDLE__*/', () => js);
fs.mkdirSync('dist', { recursive: true });
fs.writeFileSync('dist/hafx-studio-intro.html', page);
fs.writeFileSync('dist/preview.html', '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body>' + page + '</body></html>');
console.log('built', (page.length / 1024).toFixed(0), 'KB');
