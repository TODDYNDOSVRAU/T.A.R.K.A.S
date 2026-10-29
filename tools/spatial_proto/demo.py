"""Gera um mix de teste com posições conhecidas e renderiza todos os presets para
escuta A/B. Use fones. Saída em out/demo/."""
import os
from dataclasses import replace
import numpy as np

from tarkas_spatial import PRESETS, Params, render
from tarkas_spatial import wavio
from tarkas_spatial.viz import plot_scene

FS = 48000
SEC = 12
OUT = os.path.join("out", "demo")


def pan(sig, p):
    a = (p + 1) * np.pi / 4
    return np.stack([sig * np.cos(a), sig * np.sin(a)], axis=1)


def env(t, period, decay):
    return np.exp(-(t % period) / decay)


def build_mix():
    rng = np.random.default_rng(11)
    t = np.arange(FS * SEC) / FS
    beat = 60 / 100
    kick = np.sin(2 * np.pi * (50 + 80 * env(t, beat, 0.03)) * t) * env(t, beat, 0.12) * 0.6
    bass_f = np.where((t // (beat * 4)) % 2 == 0, 55.0, 73.4)
    bass = np.tanh(2 * np.sin(2 * np.pi * bass_f * t)) * 0.18
    hat = rng.standard_normal(t.size) * env(t + beat / 2, beat / 2, 0.02)
    hat = np.diff(hat, prepend=0) * 0.12
    vib = 1 + 0.006 * np.sin(2 * np.pi * 5.5 * t)
    voice = sum(np.sin(2 * np.pi * 220 * k * vib * t) / k ** 1.3 for k in range(1, 12))
    voice *= 0.09 * (0.6 + 0.4 * np.sin(2 * np.pi * 0.25 * t) ** 2)
    gtr = sum(np.sin(2 * np.pi * 330 * k * t) * np.exp(-k * 0.35) for k in range(1, 9))
    gtr *= env(t, beat / 2, 0.25) * 0.10
    synth = sum(np.sin(2 * np.pi * 440 * 1.5 * k * t + k) / k for k in range(1, 7)) * 0.05
    pad = np.stack([np.convolve(rng.standard_normal(t.size), np.hanning(400), "same"),
                    np.convolve(rng.standard_normal(t.size), np.hanning(400), "same")], 1)
    pad *= 0.012
    mix = (pan(kick, 0) + pan(bass, 0) + pan(voice, 0) + pan(gtr, -0.6) + pan(synth, 0.7)
           + pan(hat, 0.35) + pad)
    return mix / np.abs(mix).max() * 0.7


def main():
    os.makedirs(OUT, exist_ok=True)
    x = build_mix()
    wavio.write(os.path.join(OUT, "00_mix_estereo.wav"), x, FS)
    print("Fontes: bumbo/baixo/voz no centro, guitarra -0,6 (esq.), sintetizador +0,7 (dir.),"
          " chimbal +0,35, pad descorrelacionado (ambiente)")
    dry, _ = render(x, FS, Params(intensity=0.0))
    wavio.write(os.path.join(OUT, "01_original_alinhado.wav"), dry, FS)
    for i, (name, p) in enumerate(sorted(PRESETS.items()), start=2):
        y, eng = render(x, FS, p, record=True)
        path = os.path.join(OUT, f"{i:02d}_espacial_{name}.wav")
        wavio.write(path, y, FS)
        plot_scene(eng, os.path.splitext(path)[0] + ".png", f"Demo – preset {name}")
        print(path)
    # head tracking simulado: cabeça vira 60° para a esquerda; a cena deve ficar parada no mundo,
    # logo tudo parece ir para a direita
    y, _ = render(x, FS, replace(PRESETS["padrao"], head_yaw=60.0))
    wavio.write(os.path.join(OUT, "05_cabeca_virada_60.wav"), y, FS)
    print("pronto:", os.path.abspath(OUT))


if __name__ == "__main__":
    main()
