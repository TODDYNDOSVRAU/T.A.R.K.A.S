"""Testes do upmixer 2.0 -> 5.1. Rodar da pasta tools/spatial_proto:
    python -m unittest discover -s tests -v
Fontes independentes (ruído rosa de sementes diferentes) são codificadas em Lt/Rt por um
codificador de matriz próprio, escrito aqui, e a saída é medida canal a canal.
"""
import os, sys, unittest
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from tarkas_spatial.upmix import (UpmixParams, Upmixer, upmix, LATENCY,
                                  CH_L, CH_R, CH_C, CH_LFE, CH_LS, CH_RS)

FS = 48000
SKIP = 8192  # descarta o início, enquanto as médias espectrais enchem


def pink(n, seed):
    rng = np.random.default_rng(seed)
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / FS)
    spec[1:] /= np.sqrt(f[1:])
    spec[0] = 0
    x = np.fft.irfft(spec, n)
    return 0.2 * x / np.sqrt(np.mean(x ** 2))


def hilbert(x):
    """Defasagem de -90 graus de todas as frequências (a do codificador de matriz)."""
    X = np.fft.rfft(x)
    X[1:] *= -1j
    return np.fft.irfft(X, len(x))


def encode(L, R, C, Ls, Rs):
    """Codificador de matriz próprio: C entra em fase nos dois lados, Ls e Rs com 90 graus
    e polaridade oposta entre os lados (PDF/estudo, seção 2)."""
    k = 1 / np.sqrt(2)
    hs, hr = hilbert(Ls), hilbert(Rs)
    return np.stack([L + k * C + k * hs, R + k * C - k * hr], axis=1)


def powers(y):
    return np.mean(y[SKIP:] ** 2, axis=0)


def db(x):
    return 10 * np.log10(x + 1e-30)


N = FS * 3


class Steering(unittest.TestCase):
    def setUp(self):
        self.z = np.zeros(N)

    def check(self, x, main, floor_db):
        p = powers(upmix(x, FS))
        p[CH_LFE] = 0
        share = db(p / p.sum())
        self.assertGreater(share[main], -1.5, f"canal {main} pouco dominante: {share.round(1)}")
        for ch in range(6):
            if ch != main and ch != CH_LFE:
                self.assertLess(share[ch], floor_db, f"vazamento em {ch}: {share.round(1)}")

    def test_center_goes_to_C(self):
        c = pink(N, 1)
        self.check(encode(self.z, self.z, c, self.z, self.z), CH_C, -6)

    def test_hard_left_stays_in_L(self):
        self.check(encode(pink(N, 2), self.z, self.z, self.z, self.z), CH_L, -15)

    def test_hard_right_stays_in_R(self):
        self.check(encode(self.z, pink(N, 3), self.z, self.z, self.z), CH_R, -15)

    def test_matrix_surround_goes_to_rear(self):
        s = pink(N, 4)
        x = encode(self.z, self.z, self.z, s, s)  # surround mono (PL I): Lt = +S, Rt = -S
        p = powers(upmix(x, FS)); p[CH_LFE] = 0
        rear = (p[CH_LS] + p[CH_RS]) / p.sum()
        self.assertGreater(db(rear), -1.5, f"traseiras com {db(rear):.1f} dB do total")


class Energy(unittest.TestCase):
    def test_total_energy_is_kept(self):
        for name, x in (
            ("estéreo largo", np.stack([pink(N, 5), pink(N, 6)], axis=1)),
            ("5 fontes", encode(pink(N, 7), pink(N, 8), pink(N, 9), pink(N, 10), pink(N, 11))),
        ):
            y = upmix(x, FS)
            e_in = np.sum(x[SKIP:] ** 2)
            e_out = np.sum(y[SKIP:, [CH_L, CH_R, CH_C, CH_LS, CH_RS]] ** 2)
            self.assertLess(abs(db(e_out / e_in)), 1.5, f"{name}: {db(e_out / e_in):+.2f} dB")


class Structure(unittest.TestCase):
    def test_everything_off_is_a_delayed_copy(self):
        x = np.stack([pink(N, 12), pink(N, 13)], axis=1)
        off = UpmixParams(center=0, surround=0, ambience=0)
        y = upmix(x, FS, off)
        np.testing.assert_allclose(y[SKIP:, :2], x[SKIP:], atol=1e-9)
        self.assertEqual(np.abs(y[:, [CH_C, CH_LS, CH_RS, CH_LFE]]).max(), 0.0)

    def test_streaming_block_size_does_not_matter(self):
        x = np.stack([pink(FS, 14), pink(FS, 15)], axis=1)
        np.testing.assert_allclose(upmix(x, FS, block=100), upmix(x, FS, block=4096), atol=1e-9)

    def test_latency_is_declared_value(self):
        x = np.zeros((FS, 2)); x[1000, :] = 1.0
        up = Upmixer(FS, UpmixParams(center=0, surround=0, ambience=0))
        y = np.concatenate([up.process(x), up.flush()])
        self.assertEqual(int(np.argmax(np.abs(y[:, CH_L]))), 1000 + LATENCY)

    def test_lfe_has_only_bass(self):
        x = np.stack([pink(N, 16)] * 2, axis=1)
        y = upmix(x, FS, UpmixParams(lfe=True))
        spec = np.abs(np.fft.rfft(y[SKIP:, CH_LFE])) ** 2
        f = np.fft.rfftfreq(len(y[SKIP:]), 1 / FS)
        low, high = spec[f < 120].sum(), spec[f > 400].sum()
        self.assertGreater(db(low / high), 30)

    def test_nan_input_does_not_poison_output(self):
        x = np.stack([pink(FS, 17), pink(FS, 18)], axis=1); x[5000:5100] = np.nan
        self.assertTrue(np.all(np.isfinite(upmix(x, FS))))


if __name__ == "__main__":
    unittest.main()
