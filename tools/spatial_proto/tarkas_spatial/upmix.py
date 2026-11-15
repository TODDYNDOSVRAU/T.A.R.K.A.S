"""Upmixer 2.0 -> 5.1 do Tarkas, protótipo em NumPy (estudo docs/research/prologic-ii-integracao.md).

Decodificador de matriz ativa próprio, não uma cópia do Pro Logic II (a matemática interna
dele não foi obtida). Mesmo STFT do engine.py (janela 1024, FFT 2048, hop 512), por bin:

  rho = Re(Plr) / sqrt(Pll * Prr)      correlação com sinal: +1 em fase, -1 em oposição de fase
  b   = (|L| - |R|) / (|L| + |R|)      balanço de nível (0 = no meio)

  centro   gc = center * max(rho, 0)^2 * (1 - |b|)^2     o que está em fase e no meio vai ao C
  surround gs = clip(surround * max(-rho, 0)^2 + ambience * (1 - |rho|)) * (1 + 0.5 * dimension)
                                                        o que está fora de fase (e um pouco do
                                                        incoerente) vai a Ls/Rs

  C  = gc * M                     M = (L + R) / sqrt2
  Ls = gs * S / sqrt2 * e^{-j phi}   S = (L - R) / sqrt2, fase descorrelacionada entre Ls e Rs
  Rs = gs * S / sqrt2 * e^{+j phi}
  L' = L - gc * M / sqrt2 - gs * S / sqrt2         (o que foi para C e Ls/Rs sai de L e R)
  R' = R - gc * M / sqrt2 + gs * S / sqrt2

A subtração mantém a energia total por bin (para gc, gs em 0..1). LFE opcional: M passa-baixa,
cópia sem remover os graves de L e R.

Ordem dos canais de saída = AudioFormat do Android (CHANNEL_OUT_5POINT1): L, R, C, LFE, Ls, Rs.
Latência: WIN - HOP = 512 amostras (10,7 ms a 48 kHz), sem HRTF nem limitador.
"""
from dataclasses import dataclass
import numpy as np

from .engine import WIN, NFFT, HOP, BINS

LATENCY = WIN - HOP
CH_L, CH_R, CH_C, CH_LFE, CH_LS, CH_RS = range(6)
INV_SQRT2 = 1 / np.sqrt(2)


@dataclass(frozen=True)
class UpmixParams:
    center: float = 0.7      # 0..1 quanto do conteúdo central vai ao C (0 = nada)
    surround: float = 0.8    # 0..1 quanto do conteúdo fora de fase vai a Ls/Rs
    ambience: float = 0.25   # 0..1 quanto do conteúdo incoerente (ambiente) vai a Ls/Rs
    dimension: float = 0.0   # -1..1 desloca o campo para a frente (-) ou para trás (+)
    lfe: bool = False        # gera o canal LFE (cópia passa-baixa do mid)
    lfe_hz: float = 120.0
    alpha: float = 0.8       # memória das médias espectrais, igual ao engine.py


class Upmixer:
    def __init__(self, fs=48000, params=UpmixParams()):
        self.fs = fs
        self.p = params
        freqs = np.fft.rfftfreq(NFFT, 1 / fs)
        n = np.arange(WIN)
        self.win = 0.5 - 0.5 * np.cos(2 * np.pi * n / WIN)
        rng = np.random.default_rng(11)
        kern = np.hanning(9) / np.hanning(9).sum()
        ramp = np.clip(freqs / 300.0, 0, 1)
        self.phi = np.convolve(rng.uniform(-np.pi, np.pi, BINS), kern, mode="same") * 1.2 * ramp
        x = np.clip(np.log(np.maximum(freqs, 1.0) / (params.lfe_hz * 0.8)) / np.log(1.5625), 0, 1)
        self.lfe_gain = 1 - (0.5 - 0.5 * np.cos(np.pi * x))
        self.reset()

    def reset(self):
        self.pll = np.zeros(BINS)
        self.prr = np.zeros(BINS)
        self.plr = np.zeros(BINS, complex)
        self.hist = np.zeros((2, WIN))
        self.pending = np.zeros((0, 2))
        self.acc = np.zeros((6, NFFT))

    def process(self, block):
        """block: (n, 2) float. Retorna (m, 6) na ordem L R C LFE Ls Rs, atrasado em LATENCY."""
        block = np.nan_to_num(np.asarray(block, float), nan=0.0, posinf=0.0, neginf=0.0)
        self.pending = np.concatenate([self.pending, block])
        outs = []
        while self.pending.shape[0] >= HOP:
            hop, self.pending = self.pending[:HOP], self.pending[HOP:]
            self.hist = np.concatenate([self.hist[:, HOP:], hop.T], axis=1)
            outs.append(self._frame())
        return np.concatenate(outs) if outs else np.zeros((0, 6))

    def flush(self):
        return self.process(np.zeros((LATENCY + HOP, 2)))

    def gains(self, pll, prr, plr):
        """(gc, gs) por bin a partir das médias espectrais. Separado para os testes."""
        p = self.p
        gl, gr = np.sqrt(pll), np.sqrt(prr)
        rho = np.clip(plr.real / (gl * gr + 1e-20), -1.0, 1.0)
        b = (gl - gr) / (gl + gr + 1e-20)
        gc = p.center * np.maximum(rho, 0.0) ** 2 * (1 - np.abs(b)) ** 2
        gs = (p.surround * np.maximum(-rho, 0.0) ** 2 + p.ambience * (1 - np.abs(rho)))
        gs = gs * (1 + 0.5 * p.dimension)
        return np.clip(gc, 0, 1), np.clip(gs, 0, 1)

    def _frame(self):
        p = self.p
        XL = np.fft.rfft(self.hist[0] * self.win, NFFT)
        XR = np.fft.rfft(self.hist[1] * self.win, NFFT)
        a = p.alpha
        self.pll = a * self.pll + (1 - a) * np.abs(XL) ** 2
        self.prr = a * self.prr + (1 - a) * np.abs(XR) ** 2
        self.plr = a * self.plr + (1 - a) * XL * np.conj(XR)
        gc, gs = self.gains(self.pll, self.prr, self.plr)

        M = (XL + XR) * INV_SQRT2
        S = (XL - XR) * INV_SQRT2
        C = gc * M
        rot = np.exp(1j * self.phi)
        Ls = gs * S * INV_SQRT2 * np.conj(rot)
        Rs = gs * S * INV_SQRT2 * rot
        YL = XL - gc * M * INV_SQRT2 - gs * S * INV_SQRT2
        YR = XR - gc * M * INV_SQRT2 + gs * S * INV_SQRT2
        LFE = M * self.lfe_gain if p.lfe else np.zeros(BINS, complex)

        for ch, spec in ((CH_L, YL), (CH_R, YR), (CH_C, C), (CH_LFE, LFE), (CH_LS, Ls), (CH_RS, Rs)):
            self.acc[ch] += np.fft.irfft(spec, NFFT)
        out = self.acc[:, :HOP].T.copy()
        self.acc = np.concatenate([self.acc[:, HOP:], np.zeros((6, HOP))], axis=1)
        return out


def upmix(x, fs, params=UpmixParams(), block=4096):
    """Processa um sinal inteiro (n, 2) e devolve (n, 6) alinhado ao original."""
    up = Upmixer(fs, params)
    outs = [up.process(x[i:i + block]) for i in range(0, x.shape[0], block)]
    outs.append(up.flush())
    return np.concatenate(outs)[LATENCY:LATENCY + x.shape[0]]
