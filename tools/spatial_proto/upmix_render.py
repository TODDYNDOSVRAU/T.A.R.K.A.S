"""Gera arquivos para ouvir o upmixer 2.0 -> 5.1 (fones comuns bastam).

    python upmix_render.py musica.flac [--center 0.7 --surround 0.8 --ambience 0.25 --dimension 0]

Grava em out/upmix/<nome>_*.wav (estéreo, 16 bits), todos com o mesmo atraso do original:
  original  entrada alinhada
  frente    L, R e C (C a -3 dB nos dois lados): o que o upmixer deixou na frente
  tras      Ls no ouvido esquerdo, Rs no direito: só o que foi para os surrounds
  centro    só o canal C, nos dois ouvidos
  5ch       WAV de 6 canais (L R C LFE Ls Rs), para tocar num receptor ou no VLC
"""
import argparse, os
import numpy as np
from tarkas_spatial import wavio
from tarkas_spatial.upmix import UpmixParams, upmix, LATENCY

ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
ap.add_argument("input")
ap.add_argument("--center", type=float, default=0.7)
ap.add_argument("--surround", type=float, default=0.8)
ap.add_argument("--ambience", type=float, default=0.25)
ap.add_argument("--dimension", type=float, default=0.0)
ap.add_argument("--lfe", action="store_true")
a = ap.parse_args()

if not os.path.isfile(a.input):
    raise SystemExit(f"Arquivo não encontrado: {a.input}. Confira o caminho e use aspas se tiver espaços.")
x, fs = wavio.read(a.input)
y = upmix(x, fs, UpmixParams(a.center, a.surround, a.ambience, a.dimension, a.lfe))
k = 1 / np.sqrt(2)
base = os.path.join("out", "upmix", os.path.splitext(os.path.basename(a.input))[0])
peak = max(np.abs(y).max(), np.abs(x).max(), 1e-9)
g = 0.9 / peak if peak > 0.9 else 1.0   # um ganho só, para os arquivos continuarem comparáveis
outs = {
    "original": x,
    "frente": y[:, [0, 1]] + k * y[:, [2]],
    "tras": y[:, [4, 5]],
    "centro": np.repeat(y[:, [2]], 2, axis=1),
}
for name, sig in outs.items():
    wavio.write(f"{base}_{name}.wav", sig * g, fs)
wavio.write(f"{base}_5ch.wav", y * g, fs)
e = (y ** 2).sum(axis=0) / (y ** 2).sum()
print("energia por canal (L R C LFE Ls Rs):", " ".join(f"{10*np.log10(v+1e-12):.1f}" for v in e), "dB")
print("gravado em", os.path.dirname(base))
