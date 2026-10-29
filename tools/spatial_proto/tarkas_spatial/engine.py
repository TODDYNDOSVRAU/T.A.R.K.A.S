"""Motor espacial do Tarkas, fase 0 (protótipo em NumPy).

Implementa o algoritmo da seção 8 do PDF "Áudio espacial de cabo a rabo":
STFT por bin -> coerência e pan -> direto/ambiente -> regras de destino ->
HRTF por bin -> overlap-add -> loudness -> limitador.

A estrutura é de streaming (process() aceita blocos de qualquer tamanho e guarda
estado), igual à do futuro núcleo C++, para que os números de latência e os
testes valham para os dois.
"""
from dataclasses import dataclass, replace
import numpy as np

from .hrtf import SphericalHeadHrtf

WIN = 1024        # janela de análise L
NFFT = 2048       # FFT com zero-padding (L + M - 1 <= N)
HOP = 512         # 50% de sobreposição
BINS = NFFT // 2 + 1
HRTF_DELAY = 64   # atraso comum que deixa as partes de fase zero causais
LIM_CHUNK = 32    # resolução do limitador (lookahead de um bloco)
# Atraso em amostras entre entrada e saída. Em tempo real soma-se o buffer de um
# hop (512) esperando o quadro encher: 1.024 + 64 + 32 amostras = 23,3 ms a 48 kHz.
LATENCY = WIN - HOP + HRTF_DELAY + LIM_CHUNK
TAN30 = np.tan(np.radians(30.0))
INV_SQRT2 = 1 / np.sqrt(2)


@dataclass(frozen=True)
class Params:
    intensity: float = 1.0        # 0 = estéreo original (alinhado), 1 = só espacial
    width: float = 1.8            # fator w do azimute
    height: float = 25.0          # elevação máxima dos agudos (graus)
    gamma: float = 2.0            # D = max(psi, dominância) ** gamma
    alpha: float = 0.8            # memória das médias espectrais (~50 ms)
    anchor: bool = True           # prende a voz central (300 Hz-4 kHz) no plano do ouvido
    ambience_height: float = 0.5  # quanto do ambiente vai para o "teto"
    head_yaw: float = 0.0         # graus; simula head tracking (cabeça virada p/ esquerda > 0)
    bass_hz: float = 120.0        # abaixo disso o sinal passa intacto
    ceiling_db: float = -1.0      # teto do limitador (pico de amostra)
    loudness_match: bool = True   # mantém a energia do original


PRESETS = {
    "sutil": Params(intensity=0.7, width=1.3, height=12.0, ambience_height=0.3),
    "padrao": Params(),
    "amplo": Params(width=2.4, height=35.0, ambience_height=0.8),
}


def erb_rate(f):
    return 21.4 * np.log10(1 + 0.00437 * f)


def pan_and_coherence(pll, prr, plr):
    """Estimativas por bin a partir das médias espectrais (PDF, seção 8.2)."""
    tot = pll + prr
    eps = 1e-9 * tot + 1e-20
    psi = np.clip(np.abs(plr) / np.sqrt(pll * prr + eps), 0.0, 1.0)
    gl, gr = np.sqrt(pll), np.sqrt(prr)
    pan = (4 / np.pi) * np.arctan2(gr, gl) - 1          # -1 esq ... +1 dir
    theta = np.degrees(np.arctan(TAN30 * (gl - gr) / (gl + gr + 1e-20)))  # + = esquerda
    # Fonte totalmente num lado só: coerência fica indefinida (0/0) e o bin seria
    # tratado como ambiente. A dominância de nível corrige isso.
    dominance = np.abs(pll - prr) / (tot + 1e-20)
    return pan, theta, psi, dominance


class SpatialEngine:
    def __init__(self, fs=48000, params=Params(), hrtf=None, record=False):
        self.fs = fs
        self.hrtf = hrtf or SphericalHeadHrtf(fs, NFFT, HRTF_DELAY)
        self.freqs = np.fft.rfftfreq(NFFT, 1 / fs)
        n = np.arange(WIN)
        self.win = 0.5 - 0.5 * np.cos(2 * np.pi * n / WIN)  # Hann periódica: soma 1 com hop L/2
        self.dry_delay = np.exp(-2j * np.pi * self.freqs * HRTF_DELAY / fs)
        self.band = np.floor(erb_rate(self.freqs)).astype(int)
        self.nbands = self.band.max() + 1
        self.record = record
        self.frames = []  # (energia, az, el, D) por banda ERB, para o visualizador
        self.set_params(params)
        self.reset()

    # -- configuração (fora do caminho de áudio) -----------------------------
    def set_params(self, p):
        self.p = p
        f = np.maximum(self.freqs, 1.0)
        lo, hi = p.bass_hz * 0.8, p.bass_hz * 1.25
        x = np.clip(np.log(f / lo) / np.log(hi / lo), 0, 1)
        self.bass_xf = 0.5 - 0.5 * np.cos(np.pi * x)       # 0 = graves intactos, 1 = espacial
        self.elev = p.height / (1 + np.exp(-1.2 * np.log2(f / 4000.0)))
        self.voice_band = (self.freqs > 300) & (self.freqs < 4000)
        self._build_ambience(p.ambience_height)

    def _build_ambience(self, h):
        """Soma das HRTFs das direções de ambiente com fases descorrelacionadas,
        pré-computada como um filtro por (lado, ouvido) (PDF, nota da seção 8.6)."""
        dirs = [(90, 0, 1.0), (120, 0, 1.0), (150, 0, 1.0), (60, 40, h), (130, 40, h)]
        gains = np.array([g for *_, g in dirs])
        gains = gains / np.sqrt((gains ** 2).sum())
        rng = np.random.default_rng(7)
        ramp = np.clip(self.freqs / 300.0, 0, 1)  # graves/médios-graves ficam coerentes
        kern = np.hanning(9) / np.hanning(9).sum()
        out = {}
        for side, sgn in (("L", 1), ("R", -1)):
            acc_l = np.zeros(BINS, complex)
            acc_r = np.zeros(BINS, complex)
            for (az, el, _), g in zip(dirs, gains):
                hl, hr = self.hrtf.response(sgn * az, el)
                phi = np.convolve(rng.uniform(-np.pi, np.pi, BINS), kern, mode="same") * 2.5 * ramp
                rot = np.exp(1j * phi)
                acc_l += g * hl * rot
                acc_r += g * hr * rot
            out[side] = (acc_l * INV_SQRT2, acc_r * INV_SQRT2)
        (self.amb_ll, self.amb_lr), (self.amb_rl, self.amb_rr) = out["L"], out["R"]

    def reset(self):
        self.pll = np.zeros(BINS)
        self.prr = np.zeros(BINS)
        self.plr = np.zeros(BINS, complex)
        self.prev_mag = np.zeros(BINS)
        self.transient = 0.0
        self.e_dry = self.e_wet = 1e-12
        self.hist = np.zeros((2, WIN))
        self.pending = np.zeros((0, 2))
        self.acc = np.zeros((2, NFFT))
        self.ola_out = np.zeros((0, 2))
        self.lim_gain = 1.0
        self.lim_chunk = np.zeros((LIM_CHUNK, 2))  # bloco atrasado do limitador
        self.lim_need = 1.0

    # -- áudio ---------------------------------------------------------------
    def process(self, block):
        """block: (n, 2) float. Retorna (m, 2), atrasado em LATENCY amostras."""
        block = np.asarray(block, float)
        if not np.all(np.isfinite(block)):
            # NaN/Inf: zera o estado e segue com silêncio nesse trecho
            block = np.nan_to_num(block, nan=0.0, posinf=0.0, neginf=0.0)
            self.pll[:] = self.prr[:] = 0
            self.plr[:] = 0
        self.pending = np.concatenate([self.pending, block])
        outs = []
        while self.pending.shape[0] >= HOP:
            hop, self.pending = self.pending[:HOP], self.pending[HOP:]
            self.hist = np.concatenate([self.hist[:, HOP:], hop.T], axis=1)
            outs.append(self._frame())
        if not outs:
            return np.zeros((0, 2))
        return self._limit(np.concatenate(outs))

    def flush(self):
        tail = self.process(np.zeros((LATENCY + HOP, 2)))
        return tail

    def _frame(self):
        p = self.p
        XL = np.fft.rfft(self.hist[0] * self.win, NFFT)
        XR = np.fft.rfft(self.hist[1] * self.win, NFFT)
        a = p.alpha
        self.pll = a * self.pll + (1 - a) * np.abs(XL) ** 2
        self.prr = a * self.prr + (1 - a) * np.abs(XR) ** 2
        self.plr = a * self.plr + (1 - a) * XL * np.conj(XR)

        _, theta, psi, dom = pan_and_coherence(self.pll, self.prr, self.plr)
        D = np.maximum(psi, dom) ** p.gamma
        E = self.pll + self.prr
        Db = np.bincount(self.band, D * E, self.nbands) / (np.bincount(self.band, E, self.nbands) + 1e-20)
        D = 0.5 * D + 0.5 * Db[self.band]  # suavização em bandas ERB contra "ruído musical"

        # transientes: fluxo espectral relativo
        mag = np.abs(XL) + np.abs(XR)
        flux = np.maximum(mag - self.prev_mag, 0).sum() / (mag.sum() + 1e-20)
        self.prev_mag = mag
        self.transient = 1.0 if flux > 0.3 else self.transient * 0.6
        tr = self.transient

        w = 1 + (p.width - 1) * (1 - 0.7 * tr)
        az = np.clip(w * theta, -110.0, 110.0)
        el = self.elev * (1 - 0.7 * tr)
        if p.anchor:
            el = np.where(self.voice_band & (np.abs(theta) < 3.0), 0.0, el)
        az_world = az - p.head_yaw

        # primário estimado: projeção no vetor de pan (com polaridade)
        tot = np.sqrt(E) + 1e-20
        ul = np.sqrt(self.pll) / tot
        ur = np.sqrt(self.prr) / tot * np.where(self.plr.real < 0, -1.0, 1.0)
        S = ul * XL + ur * XR
        hl, hr = self.hrtf.lookup(az_world, el)
        direct = np.sqrt(D) * S * INV_SQRT2
        sa = np.sqrt(1 - D)
        AL, AR = sa * XL, sa * XR
        wl = hl * direct + self.amb_ll * AL + self.amb_rl * AR
        wr = hr * direct + self.amb_lr * AL + self.amb_rr * AR

        DL, DR = XL * self.dry_delay, XR * self.dry_delay
        bx = self.bass_xf
        g = 1.0
        if p.loudness_match:
            beta = np.exp(-HOP / (self.fs * 1.5))
            self.e_dry = beta * self.e_dry + (1 - beta) * (bx ** 2 * (np.abs(DL) ** 2 + np.abs(DR) ** 2)).sum()
            self.e_wet = beta * self.e_wet + (1 - beta) * (bx ** 2 * (np.abs(wl) ** 2 + np.abs(wr) ** 2)).sum()
            g = float(np.clip(np.sqrt(self.e_dry / (self.e_wet + 1e-20)), 0.25, 4.0))
        k = p.intensity * bx
        YL = blend(DL, g * wl, k)
        YR = blend(DR, g * wr, k)

        if self.record:
            ew = E * bx
            nb = self.nbands
            den = np.bincount(self.band, ew * D, nb) + 1e-20
            self.frames.append(np.stack([
                np.bincount(self.band, ew, nb),
                np.bincount(self.band, ew * D * az_world, nb) / den,
                np.bincount(self.band, ew * D * el, nb) / den,
                np.bincount(self.band, ew * D, nb) / (np.bincount(self.band, ew, nb) + 1e-20)]))

        self.acc[0] += np.fft.irfft(YL, NFFT)
        self.acc[1] += np.fft.irfft(YR, NFFT)
        out = self.acc[:, :HOP].T.copy()
        self.acc = np.concatenate([self.acc[:, HOP:], np.zeros((2, HOP))], axis=1)
        return out

    def _limit(self, x):
        """Limitador com lookahead de um bloco de 32 amostras: ataque instantâneo,
        release exponencial de ~50 ms. Atrasa a saída em LIM_CHUNK amostras."""
        ceil = 10 ** (self.p.ceiling_db / 20)
        chunks = x.reshape(-1, LIM_CHUNK, 2)
        need = np.minimum(1.0, ceil / (np.abs(chunks).max(axis=(1, 2)) + 1e-20))
        rel = 1 - np.exp(-LIM_CHUNK / (self.fs * 0.05))
        out = np.empty_like(chunks)
        for c in range(chunks.shape[0]):
            # o ganho no fim do bloco atrasado já respeita o bloco seguinte (lookahead),
            # e a rampa linear fica entre dois valores que respeitam o bloco atual
            g_new = min(self.lim_need, need[c], self.lim_gain + (1 - self.lim_gain) * rel)
            ramp = np.linspace(self.lim_gain, g_new, LIM_CHUNK, endpoint=False)[:, None]
            out[c] = self.lim_chunk * ramp
            self.lim_gain, self.lim_chunk, self.lim_need = g_new, chunks[c], need[c]
        return out.reshape(-1, 2)


def blend(dry, wet, k):
    """Mistura seco/espacial preservando a magnitude. Somar os dois sinais direto cria
    filtro pente (as HRTFs atrasam e mudam a fase) e buracos de até -13 dB; aqui a
    magnitude e o fasor são interpolados separadamente. k = 0 devolve o seco exato."""
    md, mw = np.abs(dry), np.abs(wet)
    ph = (1 - k) * dry / (md + 1e-20) + k * wet / (mw + 1e-20)
    return ((1 - k) * md + k * mw) * ph / (np.abs(ph) + 1e-20)


def render(x, fs, params=Params(), record=False, block=4096):
    """Processa um sinal inteiro (n, 2) e devolve a saída alinhada ao original."""
    eng = SpatialEngine(fs, params, record=record)
    outs = [eng.process(x[i:i + block]) for i in range(0, x.shape[0], block)]
    outs.append(eng.flush())
    y = np.concatenate(outs)[LATENCY:LATENCY + x.shape[0]]
    return y, eng


def with_params(p, **kw):
    return replace(p, **kw)
