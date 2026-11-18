#!/usr/bin/env node
// Generates windows/src/main/composeResources from app/src/main/res (never edited by hand).
//   node windows/scripts/gen-resources.js [--check]
// values*/strings*.xml  -> composeResources/values*/  (Android escapes and attributes normalised)
// drawable/*.xml (vector) -> composeResources/drawable/
// font/*.ttf -> composeResources/font/
// Then verifies every R.string.X / R.drawable.X / R.font.X used under app/src/main/java exists.
const fs = require('fs'), path = require('path');
const root = path.resolve(__dirname, '..', '..');
const res = path.join(root, 'app/src/main/res');
const out = path.join(root, 'windows/src/main/composeResources');
const java = path.join(root, 'app/src/main/java');

fs.rmSync(out, { recursive: true, force: true });
const put = (rel, data) => { const p = path.join(out, rel); fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, data); };

function readText(p) {
  const b = fs.readFileSync(p); // some Android files are UTF-16 LE
  const t = (b[0] === 0xFF && b[1] === 0xFE) ? b.toString('utf16le') : b.toString('utf8');
  return t.replace(/^﻿/, '');
}
function fixXml(s) {
  return s
    .replace(/\s+translatable="false"/g, '')
    .replace(/\\(['"@?])/g, '$1')
    .replace(/<xliff:g[^>]*>([\s\S]*?)<\/xliff:g>/g, '$1')
    .replace(/\s+xmlns:xliff="[^"]*"/g, '');
}
const have = { string: new Set(), drawable: new Set(), font: new Set(), plurals: new Set(), array: new Set() };
for (const dir of fs.readdirSync(res).filter(d => /^values(-pt)?$/.test(d))) {
  for (const f of fs.readdirSync(path.join(res, dir)).filter(f => f.startsWith('strings'))) {
    const txt = fixXml(readText(path.join(res, dir, f)));
    put(`${dir}/${f}`, txt);
    for (const m of txt.matchAll(/<string name="([^"]+)"/g)) have.string.add(m[1]);
    for (const m of txt.matchAll(/<plurals name="([^"]+)"/g)) have.plurals.add(m[1]);
    for (const m of txt.matchAll(/<string-array name="([^"]+)"/g)) have.array.add(m[1]);
  }
}
const skipped = [];
for (const f of fs.readdirSync(path.join(res, 'drawable'))) {
  const txt = readText(path.join(res, 'drawable', f));
  if (/<vector\b/.test(txt) && !/\?attr\/|@android:|<aapt:/.test(txt)) { put(`drawable/${f}`, txt); have.drawable.add(f.replace(/\.xml$/, '')); }
  else skipped.push(f);
}
for (const f of fs.readdirSync(path.join(res, 'font'))) { put(`font/${f}`, fs.readFileSync(path.join(res, 'font', f))); have.font.add(f.replace(/\.\w+$/, '')); }

// Usage check
const used = { string: new Map(), drawable: new Map(), font: new Map(), plurals: new Map(), array: new Map() };
(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith('.kt')) {
      const t = fs.readFileSync(p, 'utf8');
      for (const m of t.matchAll(/\bR\.(string|drawable|font|plurals|array)\.(\w+)/g)) used[m[1]].set(m[2], (used[m[1]].get(m[2]) || 0) + 1);
    }
  }
})(java);
let total = 0, missing = [];
for (const k of Object.keys(used)) for (const [n, c] of used[k]) { total += c; if (!have[k].has(n)) missing.push(`${k}.${n}(${c})`); }
console.log(`strings ${have.string.size}, plurals ${have.plurals.size}, arrays ${have.array.size}, vector drawables ${have.drawable.size}, fonts ${have.font.size}`);
console.log(`R.* references in app: ${total}; missing on desktop: ${missing.length}`);
if (skipped.length) console.log(`drawables not vector (Android-only, skipped): ${skipped.join(', ')}`);
if (missing.length) console.log('missing:', missing.join(' '));
process.exit(process.argv.includes('--check') && missing.length ? 1 : 0);
