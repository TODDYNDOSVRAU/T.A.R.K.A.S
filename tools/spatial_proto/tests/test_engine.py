"""Testes objetivos da fase 0 (PDF, seção 11.2). Rodar da pasta tools/spatial_proto:
    python -m unittest discover -s tests -v
"""
import os, sys, time, unittest
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from tarkas_spatial.engine import (Params, SpatialEngine, LATENCY, HOP, NFFT, render,
                                   pan_and_coherence, erb_rate)
from tarkas_spatial.hrtf import SphericalHeadHrtf

FS = 48000


def sine(f, sec=1.5, amp=0.3):
    return amp * np.sin(2 * np.pi * f * np.arange(int(FS * sec)) / FS)


def pan(sig, p):
    a = (p + 1) * np.pi / 4
    return np.stack([sig * np.cos(a), sig * np.sin(a)], axis=1)


def pink(n, seed=0):
    rng = np.random.default_rng(seed)
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / FS)
    spec[1:] /= np.sqrt(f[1:])
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return x / np.sqrt(np.mean(x ** 2))


def stats_after(x, params=Params()):
    eng = SpatialEngine(FS, params)
    eng.process(x)
    return eng


def bin_of(f):
    return int(round(f / (FS / NFFT)))


def db(x):
    return 10 * np.log10(x + 1e-30)


class Reconstruction(unittest.TestCase):
    def test_dry_path_is_transparent(self):
        x = 0.1 * pink(FS * 2).reshape(-1, 1) * np.array([[1.0, 0.7]])  # abaixo do limitador
        y, _ = render(x, FS, Params(intensity=0.0, loudness_match=False))
        err = y[4096:] - x[4096:]
        self.assertLess(db(np.mean(err ** 2) / np.mean(x[4096:] ** 2)), -100)

    def test_streaming_block_size_does_not_matter(self):
        x = pan(pink(FS), 0.3) * 0.3
        y1, _ = render(x, FS, block=100)
        y2, _ = render(x, FS, block=4096)
        np.testing.assert_allclose(y1, y2, atol=1e-9)

    def test_latency_constant_matches_impulse(self):
        x = np.zeros((FS, 2))
        x[FS // 2] = 0.5
        eng = SpatialEngine(FS, Params(intensity=0.0, loudness_match=False))
        y = np.concatenate([eng.process(x), eng.flush()])
        self.assertEqual(int(np.argmax(np.abs(y[:, 0]))), FS // 2 + LATENCY)


class Analysis(unittest.TestCase):
    def test_pan_estimate_per_bin(self):
        x = (pan(sine(440), -0.7) + pan(sine(2500), 0.6) + pan(sine(110), 0.0))
        eng = stats_after(x)
        p, _, psi, _ = pan_and_coherence(eng.pll, eng.prr, eng.plr)
        for f, ref in ((440, -0.7), (2500, 0.6), (110, 0.0)):
            k = bin_of(f)
            self.assertAlmostEqual(p[k], ref, delta=0.02, msg=f"{f} Hz")
            self.assertGreater(psi[k], 0.99)

    def test_coherence_separates_ambience(self):
        rng = np.random.default_rng(3)
        amb = stats_after(rng.standard_normal((FS, 2)) * 0.1)
        _, _, psi, _ = pan_and_coherence(amb.pll, amb.prr, amb.plr)
        self.assertLess(psi[50:900].mean(), 0.45)
        mono = stats_after(np.repeat(pink(FS)[:, None] * 0.1, 2, axis=1))
        _, _, psi_m, _ = pan_and_coherence(mono.pll, mono.prr, mono.plr)
        self.assertGreater(psi_m[50:900].min(), 0.99)

    def test_hard_panned_source_counts_as_direct(self):
        x = np.stack([sine(1000), np.zeros(int(FS * 1.5))], axis=1)
        eng = stats_after(x)
        _, theta, psi, dom = pan_and_coherence(eng.pll, eng.prr, eng.plr)
        k = bin_of(1000)
        self.assertGreater(max(psi[k], dom[k]) ** 2, 0.95)
        self.assertAlmostEqual(theta[k], 30.0, delta=0.5)


class Binaural(unittest.TestCase):
    def test_hrtf_ild_and_itd_for_left_source(self):
        h = SphericalHeadHrtf(FS, NFFT)
        hl, hr = h.response(90.0, 0.0)
        k = bin_of(4000)
        self.assertGreater(20 * np.log10(abs(hl[k]) / abs(hr[k])), 10)
        f = h.freqs
        band = (f > 200) & (f < 800)
        gd = lambda H: -np.gradient(np.unwrap(np.angle(H[band])), 2 * np.pi * f[band]).mean()
        itd_ms = (gd(hr) - gd(hl)) * 1e3
        # Abaixo de ~1 kHz a ITD real é maior que a de Woodworth (0,656 ms): Kuhn (1977)
        # mostra ~3a/c·sen θ = 0,765 ms em baixas frequências. O modelo deve cair entre os dois.
        self.assertTrue(0.62 < itd_ms < 0.85, f"ITD {itd_ms:.3f} ms")

    def test_left_source_is_louder_in_left_ear(self):
        x = pan(pink(FS * 2), -0.8) * 0.2
        y, _ = render(x, FS)
        el, er = np.mean(y[FS:, 0] ** 2), np.mean(y[FS:, 1] ** 2)
        self.assertGreater(db(el / er), 3)

    def test_mono_compatibility(self):
        x = pan(pink(FS * 3), 0.0) * 0.2
        y, eng = render(x, FS)
        seg = slice(FS, 3 * FS)
        mono_out = np.fft.rfft(y[seg].sum(axis=1))
        mono_in = np.fft.rfft(x[seg].sum(axis=1))
        f = np.fft.rfftfreq(mono_in.size * 2 - 2, 1 / FS)[:mono_in.size]
        bands = np.floor(erb_rate(f)).astype(int)
        e_out = np.bincount(bands, np.abs(mono_out) ** 2)
        e_in = np.bincount(bands, np.abs(mono_in) ** 2)
        valid = np.unique(bands[(f > 150) & (f < 16000)])
        worst = db(e_out[valid] / e_in[valid]).min()
        self.assertGreater(worst, -6.0, f"pior banda {worst:.1f} dB")


class Safety(unittest.TestCase):
    def test_silence_in_silence_out(self):
        y, _ = render(np.zeros((FS, 2)), FS)
        self.assertEqual(np.abs(y).max(), 0.0)

    def test_nan_input_recovers(self):
        x = pan(pink(FS * 2), 0.2) * 0.2
        x[FS // 2:FS // 2 + 100] = np.nan
        y, _ = render(x, FS)
        self.assertTrue(np.all(np.isfinite(y)))
        self.assertGreater(np.abs(y[FS + FS // 2:]).max(), 0.01)

    def test_limiter_ceiling_on_full_scale_noise(self):
        rng = np.random.default_rng(1)
        x = np.clip(rng.standard_normal((FS * 2, 2)), -1, 1)
        y, _ = render(x, FS)
        self.assertLessEqual(np.abs(y).max(), 10 ** (-1 / 20) + 1e-9)

    def test_loudness_is_preserved(self):
        rng = np.random.default_rng(5)
        common = pink(FS * 6, 1)
        x = 0.1 * (np.stack([common, common], 1) * 0.7 + rng.standard_normal((FS * 6, 2)) * 0.3)
        y, _ = render(x, FS)
        seg = slice(3 * FS, 6 * FS)
        self.assertLess(abs(db(np.mean(y[seg] ** 2) / np.mean(x[seg] ** 2))), 1.5)


class Performance(unittest.TestCase):
    def test_frame_time(self):
        eng = SpatialEngine(FS, Params())
        x = pan(pink(FS * 2), 0.3) * 0.2
        eng.process(x[:FS // 2])  # aquece
        t = []
        for i in range(FS // 2, FS * 2 - HOP, HOP):
            t0 = time.perf_counter()
            eng.process(x[i:i + HOP])
            t.append(time.perf_counter() - t0)
        med = np.median(t) * 1e3
        print(f"\n  tempo por quadro (NumPy): mediana {med:.2f} ms de um orçamento de "
              f"{HOP / FS * 1e3:.1f} ms")
        self.assertLess(med, HOP / FS * 1e3)


if __name__ == "__main__":
    unittest.main()
