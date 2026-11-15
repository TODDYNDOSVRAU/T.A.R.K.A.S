"""Virtualização 5.1 -> binaural (fones), protótipo em NumPy.

Cada canal vira uma caixa virtual num azimute fixo, filtrada pela HRTF de cabeça esférica do
engine (mesma SphericalHeadHrtf, convenção: azimute positivo à esquerda):

    L +30   R -30   C 0   Ls +110   Rs -110   LFE nos dois ouvidos, sem HRTF

É linear: um filtro por (canal, ouvido), aplicado por STFT com a mesma janela do engine
(Hann 1024, FFT 2048, hop 512). Filtros mais curtos que NFFT - WIN + 1 = 1025 amostras dão
convolução exata no overlap-add. Cada canal é normalizado para que a soma da potência dos dois
ouvidos, na faixa 100 Hz a 10 kHz, seja a da entrada: som descorrelacionado mantém o loudness.

Ordem dos canais de entrada = Android CHANNEL_OUT_5POINT1: L, R, C, LFE, Ls, Rs.
Latência: (WIN - HOP) + HRTF_DELAY = 576 amostras (12 ms a 48 kHz).

Sala virtual (room_level > 0): além do som direto, cada caixa ganha as primeiras reflexões de
uma sala retangular (fontes-imagem até a 2ª ordem). Cada reflexão chega pela HRTF da sua
direção, atrasada pelo caminho extra (arredondado para amostra inteira) e atenuada por
distância e absorção das paredes. Só entram reflexões que cabem no filtro de 1025 amostras
(até ROOM_MAX_DELAY depois do som direto, 16 ms); cauda de reverberação não cabe.
Abaixo de ROOM_HP as reflexões são retiradas: ali elas não ajudam a localizar e, somadas ao som
direto, tiram grave (medido sem o corte: até -7,6 dB no terço de oitava de 100 Hz).
A latência não muda e o custo por bloco é o mesmo: a sala só muda o conteúdo de H.
"""
from dataclasses import dataclass
import numpy as np

from .engine import WIN, NFFT, HOP, BINS, HRTF_DELAY
from .hrtf import SphericalHeadHrtf

LATENCY = WIN - HOP + HRTF_DELAY
INV_SQRT2 = 1 / np.sqrt(2)
FILTER_LEN = NFFT - WIN + 1   # 1025: maior filtro com convolução exata no overlap-add
FADE_IN, FADE_OUT = 16, 128   # amostras das bordas do filtro suavizadas (meia janela de Hann)
ROOM_MAX_DELAY = 768   # amostras depois do som direto (16 ms); sobra para a cauda do corte de graves
ROOM_MAX_ORDER = 2
ROOM_HP = 250.0        # Hz: abaixo disso as reflexões saem (fase zero, 12 dB/oitava)


def fit_filter(h_freq):
    """Corta a resposta ao impulso em FILTER_LEN amostras, com bordas suaves, e volta para a
    frequência. Sem isso o atraso interaural fracionário da HRTF espalha energia pelo quadro
    inteiro (medido: -27,6 dB depois de 1025 amostras nas traseiras), que a STFT dobra de
    volta como erro. Depois do corte a convolução por quadros é exata."""
    h = np.fft.irfft(h_freq, NFFT, axis=-1)
    w = np.zeros(NFFT)
    w[:FILTER_LEN] = 1.0
    w[:FADE_IN] = 0.5 - 0.5 * np.cos(np.pi * (np.arange(FADE_IN) + 0.5) / FADE_IN)
    w[FILTER_LEN - FADE_OUT:FILTER_LEN] = 0.5 + 0.5 * np.cos(np.pi * (np.arange(FADE_OUT) + 0.5) / FADE_OUT)
    return np.fft.rfft(h * w, NFFT, axis=-1)


@dataclass(frozen=True)
class VirtualParams:
    front_az: float = 30.0       # graus, L e R
    surround_az: float = 110.0   # graus, Ls e Rs
    surround_el: float = 0.0     # graus de elevação das traseiras
    lfe_gain: float = 1.0        # ganho do LFE em cada ouvido (relativo ao canal)
    room_level: float = 0.0      # 0 = sem sala (anecoico); 1 = reflexões no nível físico
    room_size: tuple = (6.0, 5.0, 2.8)   # m: comprimento (x), largura (y), altura (z)
    listener_back: float = 2.7   # m do ouvinte até a parede de trás; centrado na largura
    ear_height: float = 1.2      # m
    speaker_dist: float = 2.0    # m do ouvinte a cada caixa
    absorption: float = 0.3      # fração da energia absorvida em cada reflexão


def room_paths(d, p, fs):
    """Reflexões de uma caixa na direção d = (az, el): lista de (az, el, atraso em amostras
    depois do som direto, ganho relativo ao som direto). Ouvinte na origem, x para a frente."""
    from .hrtf import direction, SPEED_OF_SOUND
    src = direction(*d) * p.speaker_dist
    lx, ly, lz = p.room_size
    walls = ((-p.listener_back, lx - p.listener_back), (-ly / 2, ly / 2),
             (-p.ear_height, lz - p.ear_height))
    for (a, b), c in zip(walls, src):
        if not a < c < b:
            raise ValueError("caixa fora da sala: aumente room_size ou reduza speaker_dist")
    r = np.sqrt(1.0 - p.absorption)
    ks = range(-ROOM_MAX_ORDER, ROOM_MAX_ORDER + 1)
    out = []
    for kx in ks:
        for ky in ks:
            for kz in ks:
                order = abs(kx) + abs(ky) + abs(kz)
                if not 1 <= order <= ROOM_MAX_ORDER:
                    continue
                img = np.array([(c if k % 2 == 0 else a + b - c) + k * (b - a)
                                for k, (a, b), c in zip((kx, ky, kz), walls, src)])
                dist = np.linalg.norm(img)
                delay = int(round((dist - p.speaker_dist) / SPEED_OF_SOUND * fs))
                if delay > ROOM_MAX_DELAY:
                    continue
                az = np.degrees(np.arctan2(img[1], img[0]))
                el = np.degrees(np.arcsin(img[2] / dist))
                out.append((az, el, delay, p.room_level * r ** order * p.speaker_dist / dist))
    return out


class Virtualizer51:
    def __init__(self, fs=48000, params=VirtualParams(), hrtf=None):
        self.fs = fs
        self.p = params
        hrtf = hrtf or SphericalHeadHrtf(fs, NFFT, HRTF_DELAY)
        freqs = np.fft.rfftfreq(NFFT, 1 / fs)
        n = np.arange(WIN)
        self.win = 0.5 - 0.5 * np.cos(2 * np.pi * n / WIN)
        band = (freqs >= 100) & (freqs <= 10000)
        delay = np.exp(-2j * np.pi * freqs * HRTF_DELAY / fs)
        spk = [(params.front_az, 0.0), (-params.front_az, 0.0), (0.0, 0.0), None,
               (params.surround_az, params.surround_el), (-params.surround_az, params.surround_el)]
        self.H = np.zeros((6, 2, BINS), complex)
        for ch, d in enumerate(spk):
            if d is None:   # LFE: sem direção, mesmo atraso comum dos outros canais
                self.H[ch, :] = params.lfe_gain * INV_SQRT2 * delay
                continue
            hl, hr = hrtf.response(*d)
            if params.room_level > 0:
                hl, hr = hl.copy(), hr.copy()
                hp = freqs ** 2 / (freqs ** 2 + ROOM_HP ** 2)
                for az, el, n_del, gain in room_paths(d, params, fs):
                    rl, rr = hrtf.response(az, el)
                    z = gain * hp * np.exp(-2j * np.pi * freqs * n_del / fs)
                    hl += z * rl
                    hr += z * rr
            hl, hr = fit_filter(hl), fit_filter(hr)
            g = 1 / np.sqrt(np.mean(np.abs(hl[band]) ** 2 + np.abs(hr[band]) ** 2))
            self.H[ch, 0], self.H[ch, 1] = g * hl, g * hr
        self.reset()

    def reset(self):
        self.hist = np.zeros((6, WIN))
        self.pending = np.zeros((0, 6))
        self.acc = np.zeros((2, NFFT))

    def process(self, block):
        """block: (n, 6) float. Retorna (m, 2), atrasado em LATENCY amostras."""
        block = np.nan_to_num(np.asarray(block, float), nan=0.0, posinf=0.0, neginf=0.0)
        self.pending = np.concatenate([self.pending, block])
        outs = []
        while self.pending.shape[0] >= HOP:
            hop, self.pending = self.pending[:HOP], self.pending[HOP:]
            self.hist = np.concatenate([self.hist[:, HOP:], hop.T], axis=1)
            outs.append(self._frame())
        return np.concatenate(outs) if outs else np.zeros((0, 2))

    def flush(self):
        return self.process(np.zeros((LATENCY + HOP, 6)))

    def _frame(self):
        X = np.fft.rfft(self.hist * self.win, NFFT, axis=1)            # (6, BINS)
        Y = np.einsum("ceb,cb->eb", self.H, X)                         # (2, BINS)
        self.acc += np.fft.irfft(Y, NFFT, axis=1)
        out = self.acc[:, :HOP].T.copy()
        self.acc = np.concatenate([self.acc[:, HOP:], np.zeros((2, HOP))], axis=1)
        return out


def virtualize(x, fs, params=VirtualParams(), block=4096, hrtf=None):
    """Processa um sinal inteiro (n, 6) e devolve (n, 2) alinhado ao original."""
    v = Virtualizer51(fs, params, hrtf)
    outs = [v.process(x[i:i + block]) for i in range(0, x.shape[0], block)]
    outs.append(v.flush())
    return np.concatenate(outs)[LATENCY:LATENCY + x.shape[0]]
