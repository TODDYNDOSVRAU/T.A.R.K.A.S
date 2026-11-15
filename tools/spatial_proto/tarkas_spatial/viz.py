"""Visualizador "onde cada som está" (PDF, seção 10.5), a partir de engine.frames."""
import numpy as np


def plot_scene(engine, path, title=""):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    fr = np.array(engine.frames)  # (quadros, 4, bandas)
    if fr.size == 0:
        raise ValueError("motor sem quadros gravados (use record=True)")
    energy, az, el, direct = fr[:, 0], fr[:, 1], fr[:, 2], fr[:, 3]
    nb = energy.shape[1]
    centers = np.array([engine.freqs[engine.band == b].mean() if np.any(engine.band == b) else np.nan
                        for b in range(nb)])
    keep = ~np.isnan(centers)
    t = np.arange(fr.shape[0]) * 512 / engine.fs
    edb = 10 * np.log10(energy + 1e-20)
    alpha = np.clip((edb - (edb.max() - 60)) / 60, 0, 1)

    fig = plt.figure(figsize=(12, 4.8))
    ax = fig.add_subplot(1, 2, 1)
    img = plt.get_cmap("coolwarm_r")((np.clip(az, -60, 60) + 60) / 120)
    img[..., 3] = alpha
    ax.imshow(np.transpose(img[:, keep], (1, 0, 2)), aspect="auto", origin="lower",
              extent=[0, t[-1] if t.size else 1, 0, keep.sum()], interpolation="nearest")
    ticks = np.linspace(0, keep.sum() - 1, 6).astype(int)
    ax.set_yticks(ticks + 0.5)
    ax.set_yticklabels([f"{centers[keep][i]:.0f}" for i in ticks])
    ax.set_xlabel("tempo (s)")
    ax.set_ylabel("banda ERB (Hz)")
    ax.set_title("Azimute por banda (azul = esquerda, vermelho = direita, ±60°)")

    ax2 = fig.add_subplot(1, 2, 2, projection="polar")
    w = energy * direct
    tot = w.sum(axis=0) + 1e-20
    mean_az = (w * az).sum(axis=0) / tot
    mean_el = (w * el).sum(axis=0) / tot
    size = 10 * np.log10(tot / tot.max() + 1e-6) + 60
    sc = ax2.scatter(np.radians(mean_az[keep]), 90 - mean_el[keep], c=np.log10(centers[keep]),
                     s=np.clip(size[keep], 5, 60) * 3, cmap="viridis", alpha=0.85)
    ax2.set_theta_zero_location("N")
    ax2.set_rlim(0, 90)
    ax2.set_rticks([30, 60, 90])
    ax2.set_yticklabels(["el 60°", "el 30°", "el 0°"], fontsize=7)
    ax2.set_title("Posição média de cada banda (vista de cima, frente = topo)", fontsize=10)
    cb = fig.colorbar(sc, ax=ax2, pad=0.1)
    cb.set_label("frequência (log10 Hz)")
    if title:
        fig.suptitle(title)
    fig.tight_layout()
    fig.savefig(path, dpi=130)
    plt.close(fig)
