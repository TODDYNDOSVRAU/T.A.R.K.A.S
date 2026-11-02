// Downloads every ParametricEQ.txt listed in AutoEq's INDEX.md into <dir>/eq/<n>.txt (skips existing).
import fs from 'node:fs'; import path from 'node:path';
const dir = process.argv[2];
const line = /^- \[(.+?)\]\(\.\/(.+?)\) by (.+?)(?: on (.+))?\s*$/;
const base = 'https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/';
const entries = [];
for (const l of fs.readFileSync(path.join(dir, 'INDEX.md'), 'utf8').split('\n')) {
  const m = line.exec(l.trim()); if (!m) continue;
  entries.push({ name: m[1], path: m[2], source: m[3].trim(), rig: m[4] || '' });
}
fs.mkdirSync(path.join(dir, 'eq'), { recursive: true });
fs.writeFileSync(path.join(dir, 'entries.json'), JSON.stringify(entries));
let next = 0, ok = 0, fail = 0;
async function worker() {
  while (next < entries.length) {
    const i = next++; const out = path.join(dir, 'eq', `${i}.txt`);
    if (fs.existsSync(out)) { ok++; continue; }
    const folder = entries[i].path.split('/').pop();
    const url = `${base}${entries[i].path}/${folder}%20ParametricEQ.txt`;
    for (let t = 0; t < 3; t++) {
      try { const r = await fetch(url); if (r.ok) { fs.writeFileSync(out, await r.text()); ok++; break; } if (r.status === 404) { fail++; break; } }
      catch (e) { if (t === 2) fail++; }
    }
  }
}
await Promise.all(Array.from({ length: 24 }, worker));
console.log(`entries ${entries.length} ok ${ok} missing ${fail}`);
