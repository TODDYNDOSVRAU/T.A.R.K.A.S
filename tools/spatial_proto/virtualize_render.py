"""Virtualiza áudio para fones: 5.1 -> binaural, ou estéreo -> upmix 5.1 -> binaural.

    python virtualize_render.py filme_5.1.flac            # fonte de 6 canais
    python virtualize_render.py musica.flac               # fonte estéreo: passa pelo upmixer
    opções do upmixer (só para fonte estéreo): --center --surround --ambience --dimension

Grava em out/virtualize/<nome>_binaural.wav (16 bits, para fones). Com fonte estéreo grava também
<nome>_original.wav com o mesmo atraso, para comparar. Com --room 0.5 grava
<nome>_binaural_sala050.wav, para comparar com a versão sem sala.
"""
import argparse, os
import numpy as np
from tarkas_spatial import wavio
from tarkas_spatial.upmix import UpmixParams, upmix
from tarkas_spatial.virtualize import VirtualParams, virtualize

ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
ap.add_argument("input")
ap.add_argument("--center", type=float, default=0.7)
ap.add_argument("--surround", type=float, default=0.8)
ap.add_argument("--ambience", type=float, default=0.25)
ap.add_argument("--dimension", type=float, default=0.0)
ap.add_argument("--room", type=float, default=0.0,
                help="sala virtual: nível das primeiras reflexões, 0 (sem sala) a 1; sugerido 0.5")
ap.add_argument("--hrtf", default=None,
                help="arquivo SOFA de HRTF medida (ex.: data/sadie2/D1_HRIR_SOFA/"
                     "D1_48K_24bit_256tap_FIR_SOFA.sofa); sem isto usa a cabeça esférica")
a = ap.parse_args()
if not os.path.isfile(a.input):
    raise SystemExit(f"Arquivo não encontrado: {a.input}. Confira o caminho e use aspas se tiver espaços.")

x, fs = wavio.read(a.input, stereo=False)
if x.shape[1] == 1:
    x = np.repeat(x, 2, axis=1)
if x.shape[1] == 2:
    src = x
    x = upmix(x, fs, UpmixParams(a.center, a.surround, a.ambience, a.dimension))
elif x.shape[1] == 6:
    src = None
else:
    raise SystemExit(f"{x.shape[1]} canais: só estéreo e 5.1 (6 canais) são tratados.")
hrtf = None
if a.hrtf:
    from tarkas_spatial.engine import NFFT, HRTF_DELAY
    from tarkas_spatial.sofa_hrtf import SofaHrtf
    try:
        hrtf = SofaHrtf(a.hrtf, fs, NFFT, HRTF_DELAY)
    except (OSError, ValueError) as e:
        raise SystemExit(f"Não foi possível usar a HRTF medida: {e}")
y = virtualize(x, fs, VirtualParams(room_level=a.room), hrtf=hrtf)
base = os.path.join("out", "virtualize", os.path.splitext(os.path.basename(a.input))[0])
peak = max(np.abs(y).max(), np.abs(src).max() if src is not None else 0, 1e-9)
g = 0.9 / peak if peak > 0.9 else 1.0
tag = f"_sala{round(a.room * 100):03d}" if a.room > 0 else ""
if a.hrtf:
    tag += "_medida"
wavio.write(f"{base}_binaural{tag}.wav", y * g, fs)
if src is not None:
    wavio.write(f"{base}_original.wav", src * g, fs)
print("gravado em", os.path.dirname(base))
