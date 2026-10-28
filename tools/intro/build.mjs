import fs from 'node:fs';
import { buildIntro } from './score.mjs';
const ir = buildIntro();
fs.mkdirSync('dist', { recursive: true });
fs.writeFileSync('dist/intro.hafx.json', JSON.stringify(ir, null, 2) + '\n');
const html = fs.readFileSync('player.html', 'utf8').replace('/*__IR__*/null', JSON.stringify(ir));
fs.writeFileSync('dist/hafx-intro.html', html);
console.log('built', html.length, 'bytes');
