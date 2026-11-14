"""Testes da virtualização 5.1 -> binaural. Rodar da pasta tools/spatial_proto:
    python -m unittest discover -s tests -v
"""
import os, sys, unittest
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from tarkas_spatial.virtualize import (Virtualizer51, VirtualParams, virtualize, LATENCY,
                                       room_paths, ROOM_MAX_DELAY, FILTER_LEN)
HRTF_SHIFT = 64  # virtualize() tira LATENCY, que inclui o atraso comum da HRTF (64 amostras)
from tarkas_spatial.engine import NFFT
from tarkas_spatial.upmix import upmix
from test_upmix import pink, FS, SKIP, db, N


def only(ch, sig):
    x = np.zeros((len(sig), 6)); x[:, ch] = sig
    return x


def ear_power(y):
    return np.mean(y[SKIP:] ** 2, axis=0)


class Placement(unittest.TestCase):
    def test_center_is_symmetric(self):
        p = ear_power(virtualize(only(2, pink(N, 1)), FS))
        self.assertLess(abs(db(p[0] / p[1])), 0.5)

    def level_diff_4k(self, ch):
        s = 0.3 * np.sin(2 * np.pi * 4000 * np.arange(N) / FS)
        p = ear_power(virtualize(only(ch, s), FS))
        return db(p[0] / p[1])

    def test_level_difference_at_4k_points_to_the_speaker_side(self):
        """Em 4 kHz a cabeça faz sombra: caixas à esquerda mais fortes no ouvido esquerdo
        (medido: +30 graus = 10,3 dB, +110 graus = 15,3 dB; piso do teste 6 dB)."""
        for ch in (0, 4):
            self.assertGreater(self.level_diff_4k(ch), 6, f"canal {ch}")
        for ch in (1, 5):
            self.assertLess(self.level_diff_4k(ch), -6, f"canal {ch}")

    def test_itd_matches_woodworth(self):
        """Atraso interaural = (a/c)(theta + sin theta), a = 8,75 cm, c = 343 m/s, theta = ângulo
        lateral (30 graus para L, 70 graus para Ls). Tolerância de 2 amostras."""
        for ch, theta in ((0, 30.0), (4, 70.0)):
            t = np.radians(theta)
            expected = (0.0875 / 343.0) * (t + np.sin(t)) * FS
            x = np.zeros((FS // 2, 6)); x[2000, ch] = 1.0
            y = virtualize(x, FS)
            itd = int(np.argmax(np.abs(y[:, 1]))) - int(np.argmax(np.abs(y[:, 0])))
            self.assertAlmostEqual(itd, expected, delta=2, msg=f"canal {ch}")

    def test_mirror_symmetry(self):
        s = pink(N, 4)
        for a, b in ((0, 1), (4, 5)):
            pa = ear_power(virtualize(only(a, s), FS)); pb = ear_power(virtualize(only(b, s), FS))
            self.assertLess(abs(db(pa[0] / pb[1])), 0.2)
            self.assertLess(abs(db(pa[1] / pb[0])), 0.2)


class Loudness(unittest.TestCase):
    def test_uncorrelated_channels_keep_total_power(self):
        x = np.stack([pink(N, s) for s in range(10, 16)], axis=1)
        x[:, 3] = 0
        y = virtualize(x, FS)
        e_in, e_out = np.sum(x[SKIP:] ** 2), np.sum(y[SKIP:] ** 2)
        self.assertLess(abs(db(e_out / e_in)), 2.0, f"{db(e_out / e_in):+.2f} dB")


class Structure(unittest.TestCase):
    def test_streaming_block_size_does_not_matter(self):
        x = np.stack([pink(FS, s) for s in range(20, 26)], axis=1)
        np.testing.assert_allclose(virtualize(x, FS, block=100), virtualize(x, FS, block=4096), atol=1e-9)

    def test_latency_is_declared_value(self):
        x = np.zeros((FS // 2, 6)); x[1000, 2] = 1.0
        v = Virtualizer51(FS)
        y = np.concatenate([v.process(x), v.flush()])
        self.assertEqual(int(np.argmax(np.abs(y[:, 0]))), 1000 + LATENCY)  # C: igual nos dois ouvidos

    def test_lfe_reaches_both_ears_equally(self):
        p = ear_power(virtualize(only(3, pink(N, 30)), FS))
        self.assertLess(abs(db(p[0] / p[1])), 0.1)
        self.assertGreater(p[0], 0)

    def test_nan_input_does_not_poison_output(self):
        x = np.stack([pink(FS, s) for s in range(40, 46)], axis=1); x[5000:5100] = np.nan
        self.assertTrue(np.all(np.isfinite(virtualize(x, FS))))

    def test_full_chain_stereo_upmix_then_virtualize(self):
        """Estéreo -> upmix 5.1 -> binaural: roda, tem o tamanho certo e mantém o loudness."""
        x = np.stack([pink(N, 50), pink(N, 51)], axis=1)
        y = virtualize(upmix(x, FS), FS)
        self.assertEqual(y.shape, x.shape)
        self.assertLess(abs(db(np.sum(y[SKIP:] ** 2) / np.sum(x[SKIP:] ** 2))), 2.5)


ROOM = VirtualParams(room_level=0.5)


class ExactConvolution(unittest.TestCase):
    """Os filtros cabem em FILTER_LEN amostras, então a STFT dá a convolução exata."""

    def test_nothing_after_filter_len(self):
        for p in (VirtualParams(), ROOM):
            h = np.fft.irfft(Virtualizer51(FS, p).H, NFFT, axis=2)
            self.assertLess(np.abs(h[:, :, FILTER_LEN:]).max(), 1e-12)

    def test_output_equals_direct_convolution(self):
        x = np.stack([pink(FS, s) for s in range(60, 66)], axis=1)
        for p in (VirtualParams(), ROOM):
            v = Virtualizer51(FS, p)
            h = np.fft.irfft(v.H, NFFT, axis=2)[:, :, :FILTER_LEN]
            ref = np.stack([sum(np.convolve(x[:, c], h[c, e]) for c in range(6)) for e in range(2)], axis=1)
            y = virtualize(x, FS, p)
            want = ref[HRTF_SHIFT:HRTF_SHIFT + len(x)]
            np.testing.assert_allclose(y, want, atol=1e-9)

    def test_cut_changes_the_response_very_little(self):
        """Em 100 Hz a 16 kHz o filtro cortado fica a menos de 0,05 dB do modelo sem corte
        (medido: 0,002 dB)."""
        from tarkas_spatial.hrtf import SphericalHeadHrtf
        from tarkas_spatial.virtualize import fit_filter
        f = np.fft.rfftfreq(NFFT, 1 / FS); m = (f >= 100) & (f <= 16000)
        hr = SphericalHeadHrtf(FS, NFFT, 64)
        for az in (30.0, 110.0):
            for h in hr.response(az, 0.0):
                self.assertLess(np.abs(db(np.abs(fit_filter(h)[m]) ** 2 / np.abs(h[m]) ** 2)).max(), 0.05, f"{az}")


class Room(unittest.TestCase):
    """Sala virtual: primeiras reflexões somadas ao som direto, dentro do mesmo filtro."""

    def test_off_by_default(self):
        self.assertEqual(VirtualParams().room_level, 0.0)

    def test_reflections_arrive_after_the_direct_sound_and_are_weaker(self):
        for d in ((30.0, 0.0), (0.0, 0.0), (110.0, 0.0)):
            paths = room_paths(d, VirtualParams(room_level=1.0), FS)
            self.assertGreater(len(paths), 6, f"{d}")
            for az, el, delay, gain in paths:
                self.assertGreater(delay, 48, f"{d}: reflexão a menos de 1 ms do som direto")
                self.assertLessEqual(delay, ROOM_MAX_DELAY)
                self.assertLess(gain, 1.0)

    def test_direct_sound_still_comes_first_at_the_same_sample(self):
        """O pico do ouvido do lado da caixa continua sendo o som direto (localização)."""
        h0 = np.fft.irfft(Virtualizer51(FS).H, NFFT, axis=2)
        h1 = np.fft.irfft(Virtualizer51(FS, ROOM).H, NFFT, axis=2)
        for ch, ear in ((0, 0), (1, 1), (2, 0), (2, 1), (4, 0), (5, 1)):
            self.assertEqual(int(np.argmax(np.abs(h1[ch, ear]))), int(np.argmax(np.abs(h0[ch, ear]))),
                             f"canal {ch}")

    def test_mirror_symmetry(self):
        s = pink(N, 4)
        for a, b in ((0, 1), (4, 5)):
            pa = ear_power(virtualize(only(a, s), FS, ROOM)); pb = ear_power(virtualize(only(b, s), FS, ROOM))
            self.assertLess(abs(db(pa[0] / pb[1])), 0.2)
            self.assertLess(abs(db(pa[1] / pb[0])), 0.2)

    def test_loudness_is_kept(self):
        x = np.stack([pink(N, s) for s in range(10, 16)], axis=1)
        x[:, 3] = 0
        y = virtualize(x, FS, ROOM)
        self.assertLess(abs(db(np.sum(y[SKIP:] ** 2) / np.sum(x[SKIP:] ** 2))), 2.0)

    def test_timbre_stays_close_to_the_anechoic_one(self):
        """Potência dos dois ouvidos em terços de oitava, 100 Hz a 10 kHz, contra a versão sem
        sala (medido em room_level 0,5: -3,1 a +1,5 dB; limite do teste 3,5 dB)."""
        f = np.fft.rfftfreq(NFFT, 1 / FS)
        h0, h1 = Virtualizer51(FS).H, Virtualizer51(FS, ROOM).H
        for ch in (0, 2, 4):
            p0 = (np.abs(h0[ch]) ** 2).sum(0); p1 = (np.abs(h1[ch]) ** 2).sum(0)
            for i in range(21):
                fc = 100 * 2 ** (i / 3)
                m = (f >= fc / 2 ** (1 / 6)) & (f < fc * 2 ** (1 / 6))
                self.assertLess(abs(db(p1[m].mean() / p0[m].mean())), 3.5, f"canal {ch}, {fc:.0f} Hz")

    def test_lfe_gets_no_room(self):
        np.testing.assert_array_equal(Virtualizer51(FS, ROOM).H[3], Virtualizer51(FS).H[3])

    def test_streaming_and_latency_unchanged(self):
        x = np.stack([pink(FS, s) for s in range(20, 26)], axis=1)
        np.testing.assert_allclose(virtualize(x, FS, ROOM, block=100), virtualize(x, FS, ROOM), atol=1e-9)
        x = np.zeros((FS // 2, 6)); x[1000, 2] = 1.0
        v = Virtualizer51(FS, ROOM)
        y = np.concatenate([v.process(x), v.flush()])
        self.assertEqual(int(np.argmax(np.abs(y[:, 0]))), 1000 + LATENCY)

    def test_speaker_outside_the_room_is_refused(self):
        with self.assertRaises(ValueError):
            Virtualizer51(FS, VirtualParams(room_level=0.5, speaker_dist=4.0))


if __name__ == "__main__":
    unittest.main()
