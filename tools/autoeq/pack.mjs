// Packs AutoEq parametric profiles into one gzipped TSV for the app's assets.
// Line: name \t source \t rig \t path \t preampDb \t filters, filters "TYPE fc gain q on;..." as AutoEq wrote them.
import fs from 'node:fs'; import path from 'node:path'; import zlib from 'node:zlib';
const [dir, out] = process.argv.slice(2);
const entries = JSON.parse(fs.readFileSync(path.join(dir, 'entries.json'), 'utf8'));
const pre = /Preamp:\s*(-?[\d.]+)\s*dB/i;
const fl = /Filter\s+\d+:\s*(ON|OFF)\s+(\w+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+(-?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?/gi;
const clean = s => s.replace(/[\t\n\r]/g, ' ');
const lines = [`#AutoEq ${new Date().toISOString().slice(0, 10)} https://github.com/jaakkopasanen/AutoEq (MIT)`];
let n = 0, skipped = 0, filters = 0;
entries.forEach((e, i) => {
  const f = path.join(dir, 'eq', `${i}.txt`);
  if (!fs.existsSync(f)) { skipped++; return; }
  const text = fs.readFileSync(f, 'utf8');
  const parts = [];
  for (const m of text.matchAll(fl)) { parts.push(`${m[2].toUpperCase()} ${m[3]} ${m[4] ?? '0'} ${m[5] ?? '0.7'} ${m[1].toUpperCase() === 'ON' ? 1 : 0}`); }
  if (!parts.length) { skipped++; return; }
  filters += parts.length;
  lines.push([clean(e.name), clean(e.source), clean(e.rig), e.path, (pre.exec(text)?.[1] ?? '0'), parts.join(';')].join('\t'));
  n++;
});
const raw = Buffer.from(lines.join('\n') + '\n', 'utf8');
const gz = zlib.gzipSync(raw, { level: 9 });
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, gz);
console.log(`profiles ${n} skipped ${skipped} filters ${filters} raw ${raw.length} gz ${gz.length}`);
