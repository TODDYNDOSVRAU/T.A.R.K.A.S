# AutoEq offline package

`app/src/main/assets/autoeq/parametric.aeq` holds every parametric profile of the AutoEq project
(github.com/jaakkopasanen/AutoEq, MIT), so fone corrections work without internet. The app still
reads the online index once a week and downloads, on demand, profiles added after the package.

Rebuild it (Node 22+, run from any folder):

```bash
node tools/autoeq/fetch.mjs <work-dir>
node tools/autoeq/pack.mjs <work-dir> app/src/main/assets/autoeq/parametric.aeq
```

`fetch.mjs` downloads `results/INDEX.md` into `<work-dir>` first if you place it there yourself
(`curl -o <work-dir>/INDEX.md https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/INDEX.md`),
then every `ParametricEQ.txt` it lists (skips files already present). `pack.mjs` writes one line per
profile: name, source, rig, path, preamp and the filters exactly as AutoEq wrote them, gzipped. The
file is not named `.gz` on purpose: the Android build unpacks `.gz` assets and drops the extension.

Package of 2026-11-02: 8,849 profiles, 88,490 filters, 2.5 MB of text, 609 KB gzipped. One index entry
had no ParametricEQ.txt on the server and is left out.
