"""Exporta a HRTF medida para o app: só as direções que o virtualizador usa.

    python export_hrtf.py data/sadie2/D1_HRIR_SOFA/D1_48K_24bit_256tap_FIR_SOFA.sofa 48000

Grava em out/export/:
  hrtf_ku100_<fs>.bin   respostas ao impulso já alinhadas (som frontal em HRTF_DELAY) e
                        normalizadas em campo difuso, das caixas e das reflexões da sala padrão
  ref_filters_<fs>.bin  filtros finais do protótipo montados a partir do arquivo gravado
                        (6 canais x 2 ouvidos x FILTER_LEN, float32, sala 0,35), para o teste
                        do porte comparar

Formato de hrtf_ku100_<fs>.bin (little-endian):
  "TKHR", u32 versão = 1, u32 taxa, u32 nº de direções, u32 taps
  por direção: f32 azimute, f32 elevação (graus, da medida; azimute positivo à esquerda),
               f32[taps] ouvido esquerdo, f32[taps] ouvido direito
A busca no app é pela direção mais próxima (maior produto escalar), como no protótipo.
"""
import os, struct, sys
import numpy as np
from tarkas_spatial.engine import NFFT, HRTF_DELAY
from tarkas_spatial.sofa_hrtf import SofaHrtf
from tarkas_spatial.virtualize import Virtualizer51, VirtualParams, room_paths, FILTER_LEN

TAPS = 512
ROOM = VirtualParams(room_level=0.35)

path, fs = sys.argv[1], int(sys.argv[2])
h = SofaHrtf(path, fs, NFFT, HRTF_DELAY)
p = ROOM
spk = [(p.front_az, 0.0), (-p.front_az, 0.0), (0.0, 0.0),
       (p.surround_az, p.surround_el), (-p.surround_az, p.surround_el)]
want = list(spk)
for d in spk:
    want += [(az, el) for az, el, _, _ in room_paths(d, p, fs)]
idx = sorted({h._nearest(float(az), float(el)) for az, el in want})
ir = np.fft.irfft(h.H[idx], NFFT, axis=2)
lost = np.abs(ir[:, :, TAPS:]).max() / np.abs(ir).max()
units = h.units[idx]
az = np.degrees(np.arctan2(units[:, 1], units[:, 0]))
el = np.degrees(np.arcsin(np.clip(units[:, 2], -1, 1)))

os.makedirs(os.path.join("out", "export"), exist_ok=True)
out = os.path.join("out", "export", f"hrtf_ku100_{fs}.bin")
with open(out, "wb") as f:
    f.write(b"TKHR" + struct.pack("<4I", 1, fs, len(idx), TAPS))
    for i in range(len(idx)):
        f.write(struct.pack("<2f", az[i], el[i]))
        f.write(ir[i, 0, :TAPS].astype("<f4").tobytes())
        f.write(ir[i, 1, :TAPS].astype("<f4").tobytes())

# conferência: o virtualizador montado só com o que foi gravado dá os mesmos filtros
class Stored:
    def __init__(self):
        from tarkas_spatial.hrtf import direction
        self.dir = direction
        self.units = direction(az.astype("f4").astype(float), el.astype("f4").astype(float))
        self.H = np.fft.rfft(ir[:, :, :TAPS].astype("f4").astype(float), NFFT, axis=2)

    def response(self, a, e):
        k = int(np.argmax(self.units @ self.dir(float(a), float(e))))
        return self.H[k, 0].copy(), self.H[k, 1].copy()

full = Virtualizer51(fs, ROOM, h).H
back = Virtualizer51(fs, ROOM, Stored()).H
err = np.abs(np.fft.irfft(full - back, NFFT, axis=2)).max() / np.abs(np.fft.irfft(full, NFFT, axis=2)).max()
# A referência sai do que foi gravado (o que o app consegue refazer); o corte em TAPS fica
# medido à parte, em `err` (a 96 kHz: -78 dB, acima da tolerância de 1e-4 do teste do porte).
ref = np.fft.irfft(back, NFFT, axis=2)[:, :, :FILTER_LEN]
with open(os.path.join("out", "export", f"ref_filters_{fs}.bin"), "wb") as f:
    f.write(ref.astype("<f4").tobytes())
print(f"{out}: {len(idx)} direções, {os.path.getsize(out)} bytes; "
      f"resto depois de {TAPS} taps {20 * np.log10(lost + 1e-30):.0f} dB; "
      f"erro dos filtros refeitos a partir do arquivo {20 * np.log10(err + 1e-30):.0f} dB")
