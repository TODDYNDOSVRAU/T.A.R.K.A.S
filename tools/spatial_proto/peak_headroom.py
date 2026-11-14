"""Mede quanto o estágio espacial (upmix + virtualizador, sala 0,35) sobe o pico do sinal.

    python peak_headroom.py musica.wav [--hrtf arquivo.sofa]

Imprime o pico de entrada e de saída (amostra e pico real com sobreamostragem 4x) e o ganho
de pico em dB. Um ganho positivo é a folga que o pré-amplificador precisa dar quando o
limitador está desligado. Também imprime o pior caso teórico dos filtros (soma de |h|).
"""
import argparse
import numpy as np
from tarkas_spatial import wavio
from tarkas_spatial.engine import NFFT, HRTF_DELAY
from tarkas_spatial.upmix import UpmixParams, upmix
from tarkas_spatial.virtualize import Virtualizer51, VirtualParams, virtualize

ap = argparse.ArgumentParser()
ap.add_argument("input")
ap.add_argument("--hrtf", default=None)
ap.add_argument("--room", type=float, default=0.35)
a = ap.parse_args()


def true_peak(x, over=4):
    """Pico entre amostras: sobreamostragem por FFT, em blocos."""
    n, best = 1 << 16, 0.0
    for i in range(0, x.shape[0], n // 2):
        b = x[i:i + n]
        if b.shape[0] < 16:
            break
        X = np.fft.rfft(b, axis=0)
        y = np.fft.irfft(X, b.shape[0] * over, axis=0) * over
        m = b.shape[0] * over
        best = max(best, np.abs(y[m // 8: m - m // 8]).max() if i else np.abs(y[: m - m // 8]).max())
    return best


db = lambda v: 20 * np.log10(max(v, 1e-12))
x, fs = wavio.read(a.input, stereo=False)
p = VirtualParams(room_level=a.room)
hrtf = None
if a.hrtf:
    from tarkas_spatial.sofa_hrtf import SofaHrtf
    hrtf = SofaHrtf(a.hrtf, fs, NFFT, HRTF_DELAY)
src = x
if x.shape[1] == 2:
    x = upmix(x, fs, UpmixParams())
y = virtualize(x, fs, p, hrtf=hrtf)
pi, po = np.abs(src).max(), np.abs(y).max()
ti, to = true_peak(src), true_peak(y)
rms = lambda v: np.sqrt(np.mean(v ** 2))
print(f"{a.input}  fs {fs}  canais {src.shape[1]}  {'medida' if a.hrtf else 'esférica'}  sala {a.room}")
print(f"  pico de amostra: entrada {db(pi):+.2f} dBFS, saída {db(po):+.2f} dBFS, ganho {db(po / pi):+.2f} dB")
print(f"  pico real (4x):  entrada {db(ti):+.2f} dBFS, saída {db(to):+.2f} dBFS, ganho {db(to / ti):+.2f} dB")
print(f"  RMS: ganho {db(rms(y) / rms(src)):+.2f} dB")
v = Virtualizer51(fs, p, hrtf)
l1 = np.abs(np.fft.irfft(v.H, NFFT, axis=2)).sum(axis=2)     # (6, 2)
print(f"  pior caso 5.1 (todos os canais em fundo de escala, soma de |h|): {db(l1.sum(axis=0).max()):+.1f} dB")
