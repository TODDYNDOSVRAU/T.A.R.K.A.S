"""HRTF medida, lida de um arquivo SOFA (convenção SimpleFreeFieldHRIR, AES69).

Mesma interface de SphericalHeadHrtf.response(), para o Virtualizer51 aceitar uma ou outra:
azimute positivo à esquerda, som frontal chegando em delay_samples, e normalização de campo
difuso (potência média sobre a esfera = 1 por faixa), para o timbre da cabeça medida não se
somar ao da correção do fone (AutoEq).

Testado com o SADIE II, sujeito D1 (Neumann KU100), 48 kHz, 8802 direções, licença Apache 2.0:
https://zenodo.org/records/12092466 (arquivo D1_HRIR_SOFA.zip). O arquivo não vai para o git;
fica em data/sadie2/.

A direção pedida usa a medida mais próxima (a grade do D1 tem passo de poucos graus). Não há
reamostragem: a taxa do arquivo tem de ser a do áudio.
"""
import numpy as np

from .hrtf import direction

NORM_LIMIT_DB = 12.0     # a correção de campo difuso não passa disto, para cima ou para baixo
NORM_SMOOTH_OCT = 1 / 3  # largura da média em frequência da correção
MIN_LEAD = 8             # amostras livres antes do primeiro som, depois do alinhamento


def _text(v):
    v = np.ravel(v)[0] if isinstance(v, np.ndarray) else v
    return v.decode() if isinstance(v, bytes) else str(v)


class SofaHrtf:
    def __init__(self, path, fs, nfft, delay_samples=64):
        try:
            import h5py
        except ImportError as e:
            raise SystemExit("HRTF medida precisa do pacote h5py: pip install h5py") from e
        with h5py.File(path, "r") as f:
            conv = _text(f.attrs.get("SOFAConventions", ""))
            if conv != "SimpleFreeFieldHRIR":
                raise ValueError(f"{path}: convenção SOFA {conv!r}; só SimpleFreeFieldHRIR é lida")
            file_fs = float(np.ravel(f["Data.SamplingRate"][:])[0])
            if abs(file_fs - fs) > 0.5:
                raise ValueError(f"{path}: medido a {file_fs:.0f} Hz, áudio a {fs} Hz; "
                                 "use o arquivo da mesma taxa")
            kind = _text(f["SourcePosition"].attrs.get("Type", "spherical"))
            if kind != "spherical":
                raise ValueError(f"{path}: posições em {kind!r}; só coordenadas esféricas são lidas")
            pos = f["SourcePosition"][:]
            ir = f["Data.IR"][:]                      # (M, 2, taps): ouvido esquerdo, direito
        if ir.ndim != 3 or ir.shape[1] != 2 or ir.shape[2] > nfft // 2:
            raise ValueError(f"{path}: formato de Data.IR não tratado: {ir.shape}")
        self.fs, self.nfft, self.delay = fs, nfft, delay_samples
        self.freqs = np.fft.rfftfreq(nfft, 1.0 / fs)
        self.units = direction(pos[:, 0], pos[:, 1])  # (M, 3)

        # alinhamento: o som frontal (az 0, el 0) passa a chegar em delay_samples
        front = ir[self._nearest(0.0, 0.0)]
        onset = int(round(np.mean([_onset(front[0]), _onset(front[1])])))
        self.shift = onset - delay_samples
        first = min(_onset(h) for h in ir.reshape(-1, ir.shape[2]))
        if first - self.shift < MIN_LEAD:
            raise ValueError(f"{path}: delay_samples curto demais para esta medida")
        # O que vem antes de `shift` é pré-eco da equalização de fase linear da própria medida
        # (no D1: -36,8 dB da energia total). Sai, com borda suave, para não dar a volta no quadro.
        ir = ir[:, :, max(self.shift, 0):].copy()
        ir[:, :, :MIN_LEAD] *= 0.5 - 0.5 * np.cos(np.pi * (np.arange(MIN_LEAD) + 0.5) / MIN_LEAD)
        H = np.fft.rfft(ir, nfft, axis=2)
        if self.shift < 0:
            H = H * np.exp(2j * np.pi * self.freqs * self.shift / fs)

        # campo difuso: média de potência sobre a esfera, com peso pela área de cada medida
        w = self._area_weights()[:, None]
        p = (w * (np.abs(H[:, 0]) ** 2 + np.abs(H[:, 1]) ** 2) / 2).sum(axis=0) / w.sum()
        norm_db = -10 * np.log10(_smooth(p, self.freqs, NORM_SMOOTH_OCT) + 1e-20)
        band = (self.freqs >= 200) & (self.freqs <= 8000)
        mid = norm_db[band].mean()
        norm_db = np.clip(norm_db - mid, -NORM_LIMIT_DB, NORM_LIMIT_DB) + mid
        self.norm = 10 ** (norm_db / 20)
        # fase mínima: com fase zero a correção teria metade da resposta antes do som,
        # que daria a volta no quadro (medido: -53 dB depois de 1025 amostras)
        self.H = H * _min_phase(self.norm, nfft)

    def _nearest(self, az, el):
        return int(np.argmax(self.units @ direction(az, el)))

    def _area_weights(self, n=20000):
        """Fração da esfera mais perto de cada medida (pontos de Fibonacci)."""
        k = np.arange(n) + 0.5
        z = 1 - 2 * k / n
        phi = np.pi * (1 + 5 ** 0.5) * k
        r = np.sqrt(1 - z * z)
        pts = np.stack([r * np.cos(phi), r * np.sin(phi), z], axis=1)
        idx = np.concatenate([np.argmax(pts[i:i + 2000] @ self.units.T, axis=1)
                              for i in range(0, n, 2000)])
        return np.bincount(idx, minlength=self.units.shape[0]).astype(float)

    def response(self, az, el):
        """HRTF normalizada (esq., dir.) da medida mais próxima da direção pedida."""
        h = self.H[self._nearest(float(az), float(el))]
        return h[0].copy(), h[1].copy()


def _onset(h):
    """Primeira amostra que passa de 20% do pico (-14 dB)."""
    a = np.abs(h)
    return int(np.argmax(a >= 0.2 * a.max()))


def _min_phase(mag, nfft):
    """Resposta de fase mínima com o módulo dado (cepstro real dobrado)."""
    c = np.fft.irfft(np.log(np.maximum(mag, 1e-8)), nfft)
    c[1:nfft // 2] *= 2
    c[nfft // 2 + 1:] = 0
    return np.exp(np.fft.rfft(c, nfft))


def _smooth(p, freqs, octaves):
    """Média de potência numa janela de largura constante em oitavas."""
    c = np.concatenate([[0.0], np.cumsum(p)])
    lo = np.searchsorted(freqs, freqs / 2 ** (octaves / 2), "left")
    hi = np.maximum(np.searchsorted(freqs, freqs * 2 ** (octaves / 2), "right"), lo + 1)
    return (c[hi] - c[lo]) / (hi - lo)
