"""Renderiza uma música com o motor espacial do Tarkas (fase 0).

Exemplos (da pasta tools/spatial_proto):
    python render.py musica.flac                      # gera out/musica_padrao.wav
    python render.py musica.wav --preset amplo --ab   # também gera a versão original alinhada
    python render.py musica.mp3 --width 2.0 --height 30 --plot
"""
import argparse, os, time
from dataclasses import replace

from tarkas_spatial import PRESETS, render
from tarkas_spatial import wavio


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("input")
    ap.add_argument("-o", "--output", help="WAV de saída (padrão: out/<nome>_<preset>.wav)")
    ap.add_argument("--preset", choices=sorted(PRESETS), default="padrao")
    ap.add_argument("--intensity", type=float)
    ap.add_argument("--width", type=float)
    ap.add_argument("--height", type=float)
    ap.add_argument("--ambience-height", type=float)
    ap.add_argument("--head-yaw", type=float, help="graus; simula a cabeça virada")
    ap.add_argument("--no-anchor", action="store_true", help="não prende a voz central à frente")
    ap.add_argument("--ab", action="store_true", help="grava também o original com a mesma latência e loudness")
    ap.add_argument("--plot", action="store_true", help="gera o PNG de onde cada banda está")
    ap.add_argument("--float", action="store_true", help="WAV float 32 em vez de 16 bits")
    a = ap.parse_args()

    p = PRESETS[a.preset]
    over = {k: v for k, v in dict(intensity=a.intensity, width=a.width, height=a.height,
                                  ambience_height=a.ambience_height, head_yaw=a.head_yaw).items()
            if v is not None}
    if a.no_anchor:
        over["anchor"] = False
    p = replace(p, **over)

    x, fs = wavio.read(a.input)
    base = os.path.splitext(os.path.basename(a.input))[0]
    out = a.output or os.path.join("out", f"{base}_{a.preset}.wav")
    bits = 32 if a.float else 16

    t0 = time.perf_counter()
    y, eng = render(x, fs, p, record=a.plot)
    dt = time.perf_counter() - t0
    wavio.write(out, y, fs, bits)
    print(f"{out}: {x.shape[0] / fs:.1f} s de áudio em {dt:.1f} s "
          f"({x.shape[0] / fs / dt:.0f}x tempo real), {fs} Hz, parâmetros {p}")
    if a.ab:
        dry, _ = render(x, fs, replace(p, intensity=0.0))
        ref = os.path.splitext(out)[0] + "_original.wav"
        wavio.write(ref, dry, fs, bits)
        print(f"{ref}: original com a mesma latência e o mesmo limitador, para A/B justo")
    if a.plot:
        from tarkas_spatial.viz import plot_scene
        png = os.path.splitext(out)[0] + ".png"
        plot_scene(eng, png, f"{base} – preset {a.preset}")
        print(png)


if __name__ == "__main__":
    main()
