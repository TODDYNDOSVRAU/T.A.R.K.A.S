"""HRTF paramétrica de cabeça esférica (Brown & Duda, 1998) amostrada numa grade.

Não depende de nenhuma base de dados: serve para validar o algoritmo na fase 0.
A grade e a busca pela direção mais próxima imitam o que o núcleo C++ fará com
uma base SOFA pré-convertida (ex.: SADIE II), então trocar a fonte das HRTFs
depois não muda o motor.

Convenção de coordenadas (igual ao ITU-R BS.2051 / PDF, seção 4.1):
x para a frente, y para a esquerda, z para cima; azimute positivo à esquerda.
"""
import numpy as np

HEAD_RADIUS = 0.0875  # m
SPEED_OF_SOUND = 343.0  # m/s


def direction(az_deg, el_deg):
    az = np.radians(az_deg)
    el = np.radians(el_deg)
    return np.stack([np.cos(el) * np.cos(az), np.cos(el) * np.sin(az), np.sin(el)], axis=-1)


class SphericalHeadHrtf:
    AZ_STEP = 5.0
    EL_VALUES = np.arange(-40.0, 91.0, 10.0)

    def __init__(self, fs, nfft, delay_samples=64, a=HEAD_RADIUS, c=SPEED_OF_SOUND):
        self.fs, self.nfft, self.delay = fs, nfft, delay_samples
        self.a, self.c = a, c
        self.freqs = np.fft.rfftfreq(nfft, 1.0 / fs)
        self.az_values = np.arange(-180.0, 180.0, self.AZ_STEP)
        az, el = np.meshgrid(self.az_values, self.EL_VALUES, indexing="ij")
        hl, hr = self.evaluate(az, el)
        # Normalização de campo difuso: potência média sobre a esfera = 1 por bin,
        # assim o "timbre" da HRTF não se soma ao do AutoEq (PDF, seção 10.4).
        w = np.cos(np.radians(el))[..., None]
        p = (w * (np.abs(hl) ** 2 + np.abs(hr) ** 2) / 2).sum(axis=(0, 1)) / w.sum(axis=(0, 1))
        self.norm = 1.0 / np.sqrt(p + 1e-20)
        self.table_l = (hl * self.norm).astype(np.complex64)
        self.table_r = (hr * self.norm).astype(np.complex64)
        self._k = np.arange(self.freqs.size)

    # -- modelo -------------------------------------------------------------
    def _ear(self, u, ear):
        cos_t = np.clip((u * ear).sum(axis=-1), -1.0, 1.0)[..., None]
        theta = np.arccos(cos_t)  # ângulo entre a fonte e o eixo da orelha
        w = 2 * np.pi * self.freqs
        w0 = self.c / self.a
        a_min, th_min = 0.1, np.radians(150.0)
        alpha = (1 + a_min / 2) + (1 - a_min / 2) * np.cos(theta / th_min * np.pi)
        shadow = (1 + 1j * alpha * w / (2 * w0)) / (1 + 1j * w / (2 * w0))
        t = np.where(theta < np.pi / 2, -self.a / self.c * np.cos(theta),
                     self.a / self.c * (theta - np.pi / 2))
        # som frontal chega em t = 0 (alinhado ao caminho seco); o mínimo é -a/c,
        # coberto pelo atraso comum de 64 amostras
        return shadow * np.exp(-1j * w * t)

    def _pinna(self, az, el):
        f = np.maximum(self.freqs, 1.0)
        az, el = az[..., None], el[..., None]
        # notch principal sobe com a elevação: ~6 kHz em -40° até ~11 kHz em +90°
        fn = 6000.0 * (11.0 / 6.0) ** ((el + 40.0) / 130.0)
        depth = 1 - 10 ** (-12.0 / 20)
        notch = 1 - depth * np.exp(-0.5 * (np.log2(f / fn) / 0.25) ** 2)
        # sombra da concha para fontes atrás: até ~-3,7 dB acima de 3 kHz
        rear = np.maximum(0.0, -np.cos(np.radians(az)))
        shelf = 1 - 0.35 * rear / (1 + np.exp(-4 * np.log2(f / 3000.0)))
        return notch * shelf

    def evaluate(self, az, el):
        """HRTF complexa (esq., dir.) para arrays de azimute/elevação em graus.
        Formato de saída: formato(az) + (bins,)."""
        az = np.asarray(az, float)
        el = np.asarray(el, float)
        u = direction(az, el)
        hl = self._ear(u, np.array([0.0, 1.0, 0.0]))
        hr = self._ear(u, np.array([0.0, -1.0, 0.0]))
        common = self._pinna(az, el) * np.exp(-2j * np.pi * self.freqs * self.delay / self.fs)
        return hl * common, hr * common

    def response(self, az, el):
        """HRTF normalizada (mesma escala da grade) para uma direção exata."""
        hl, hr = self.evaluate(az, el)
        return hl * self.norm, hr * self.norm

    # -- tempo real ---------------------------------------------------------
    def lookup(self, az, el):
        """Uma direção por bin -> HRTF daquele bin (vizinho mais próximo na grade)."""
        ia = np.rint((np.asarray(az) + 180.0) / self.AZ_STEP).astype(int) % self.az_values.size
        ie = np.clip(np.rint((np.asarray(el) - self.EL_VALUES[0]) / 10.0).astype(int),
                     0, self.EL_VALUES.size - 1)
        ia = np.broadcast_to(ia, self._k.shape)
        ie = np.broadcast_to(ie, self._k.shape)
        return self.table_l[ia, ie, self._k], self.table_r[ia, ie, self._k]
