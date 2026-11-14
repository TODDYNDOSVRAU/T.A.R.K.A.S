"""HRTF medida (SOFA). Pulados se o arquivo do SADIE II ou o h5py não estiverem presentes."""
import os
import unittest
import numpy as np

from tarkas_spatial.engine import NFFT, HRTF_DELAY
from tarkas_spatial.virtualize import Virtualizer51, VirtualParams, virtualize, FILTER_LEN, LATENCY

SOFA = os.path.join(os.path.dirname(__file__), "..", "data", "sadie2", "D1_HRIR_SOFA",
                    "D1_48K_24bit_256tap_FIR_SOFA.sofa")
try:
    import h5py  # noqa: F401
    HAVE = os.path.isfile(SOFA)
except ImportError:
    HAVE = False
FS = 48000


@unittest.skipUnless(HAVE, "sem data/sadie2 ou sem h5py")
class Measured(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from tarkas_spatial.sofa_hrtf import SofaHrtf
        cls.h = SofaHrtf(SOFA, FS, NFFT, HRTF_DELAY)
        cls.f = cls.h.freqs

    def ir(self, az, el=0.0):
        return [np.fft.irfft(x, NFFT) for x in self.h.response(az, el)]

    def test_front_arrives_at_common_delay(self):
        for x in self.ir(0.0):
            self.assertLessEqual(abs(int(np.argmax(np.abs(x))) - HRTF_DELAY), 6)

    def test_positive_azimuth_is_left(self):
        band = (self.f >= 2000) & (self.f <= 6000)
        for az in (30.0, 90.0, 110.0):
            l, r = self.h.response(az, 0.0)
            ild = 10 * np.log10(np.mean(np.abs(l[band]) ** 2) / np.mean(np.abs(r[band]) ** 2))
            self.assertGreater(ild, 5.0, az)
            il, ir = self.ir(az)
            itd = (np.argmax(np.abs(ir)) - np.argmax(np.abs(il))) / FS
            # pelo pico, o ouvido na sombra atrasa mais que o ITD real (medido: 0,98 ms a 90 graus)
            self.assertTrue(150e-6 < itd < 1100e-6, (az, itd))

    def test_mirror_directions_mirror_ears(self):
        band = (self.f >= 300) & (self.f <= 6000)
        l1, r1 = self.h.response(30.0, 0.0)
        l2, r2 = self.h.response(-30.0, 0.0)
        for a, b in ((l1, r2), (r1, l2)):
            d = 10 * np.log10(np.mean(np.abs(a[band]) ** 2) / np.mean(np.abs(b[band]) ** 2))
            self.assertLess(abs(d), 1.5)

    def test_response_fits_the_filter(self):
        for az in (0.0, 30.0, 110.0, -110.0, 180.0):
            for x in self.ir(az):
                self.assertLess(np.abs(x[FILTER_LEN:]).max(), 1e-3 * np.abs(x).max())

    def test_diffuse_field_average_is_flat(self):
        w = self.h._area_weights()[:, None]
        p = (w * (np.abs(self.h.H[:, 0]) ** 2 + np.abs(self.h.H[:, 1]) ** 2) / 2).sum(0) / w.sum()
        edges = 1000 * 2.0 ** (np.arange(-9, 12) / 3)          # 125 Hz a 12,7 kHz
        t3 = [10 * np.log10(p[(self.f >= c / 2 ** (1 / 6)) & (self.f < c * 2 ** (1 / 6))].mean())
              for c in edges]
        self.assertLess(max(np.abs(t3)), 1.0)

    def test_wrong_rate_is_refused(self):
        from tarkas_spatial.sofa_hrtf import SofaHrtf
        with self.assertRaises(ValueError):
            SofaHrtf(SOFA, 44100, NFFT, HRTF_DELAY)

    def test_virtualizer_exact_and_same_latency(self):
        for p in (VirtualParams(), VirtualParams(room_level=0.35)):
            v = Virtualizer51(FS, p, self.h)
            ir = np.fft.irfft(v.H, NFFT, axis=2)
            self.assertLess(np.abs(ir[:, :, FILTER_LEN:]).max(), 1e-9)
            rng = np.random.default_rng(3)
            x = rng.standard_normal((6000, 6)) * 0.1
            y = virtualize(x, FS, p, hrtf=self.h)
            ref = np.zeros((6000 + NFFT - 1, 2))
            for ch in range(6):
                for e in range(2):
                    ref[:, e] += np.convolve(x[:, ch], ir[ch, e])
            # virtualize() já tira LATENCY, que inclui o atraso comum da HRTF
            np.testing.assert_allclose(y, ref[HRTF_DELAY:HRTF_DELAY + 6000], atol=1e-9)

    def test_loudness_matches_spherical_model(self):
        rng = np.random.default_rng(5)
        x = rng.standard_normal((FS, 6)) * 0.1
        x[:, 3] = 0
        a = virtualize(x, FS, VirtualParams(room_level=0.35))
        b = virtualize(x, FS, VirtualParams(room_level=0.35), hrtf=self.h)
        self.assertLess(abs(10 * np.log10(np.mean(b ** 2) / np.mean(a ** 2))), 1.5)


if __name__ == "__main__":
    unittest.main()
