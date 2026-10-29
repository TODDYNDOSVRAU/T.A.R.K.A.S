"""Leitura/escrita de áudio. WAV (PCM 16/24/32 e float) sem dependências; outros
formatos (mp3, flac, m4a...) via ffmpeg, se estiver no PATH."""
import os, shutil, struct, subprocess, tempfile
import numpy as np


def read(path):
    if not path.lower().endswith(".wav"):
        return _read_ffmpeg(path)
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError(f"{path}: não é um WAV")
    pos, fmt, raw = 12, None, None
    while pos + 8 <= len(data):
        cid, size = data[pos:pos + 4], struct.unpack("<I", data[pos + 4:pos + 8])[0]
        body = data[pos + 8:pos + 8 + size]
        if cid == b"fmt ":
            tag, ch, fs, _, _, bits = struct.unpack("<HHIIHH", body[:16])
            if tag == 0xFFFE:  # WAVE_FORMAT_EXTENSIBLE: subformato nos 2 primeiros bytes do GUID
                tag = struct.unpack("<H", body[24:26])[0]
            fmt = (tag, ch, fs, bits)
        elif cid == b"data":
            raw = body
        pos += 8 + size + (size & 1)
    if fmt is None or raw is None:
        raise ValueError(f"{path}: WAV sem fmt/data")
    tag, ch, fs, bits = fmt
    if tag == 3 and bits == 32:
        x = np.frombuffer(raw, "<f4").astype(float)
    elif tag == 3 and bits == 64:
        x = np.frombuffer(raw, "<f8").copy()
    elif tag == 1 and bits == 16:
        x = np.frombuffer(raw, "<i2") / 32768.0
    elif tag == 1 and bits == 24:
        b = np.frombuffer(raw[:len(raw) // 3 * 3], np.uint8).reshape(-1, 3).astype(np.int32)
        v = b[:, 0] | (b[:, 1] << 8) | (b[:, 2] << 16)
        x = np.where(v >= 1 << 23, v - (1 << 24), v) / float(1 << 23)
    elif tag == 1 and bits == 32:
        x = np.frombuffer(raw, "<i4") / 2147483648.0
    else:
        raise ValueError(f"{path}: formato WAV não suportado (tag {tag}, {bits} bits)")
    x = x[:len(x) // ch * ch].reshape(-1, ch)
    return _to_stereo(x, path), fs


def _to_stereo(x, path):
    if x.shape[1] == 1:
        return np.repeat(x, 2, axis=1)
    if x.shape[1] == 2:
        return x
    raise ValueError(f"{path}: {x.shape[1]} canais. A fase 0 trata só estéreo "
                     "(alto-falantes virtuais multicanal são a fase 4).")


def _read_ffmpeg(path):
    exe = shutil.which("ffmpeg")
    if not exe:
        raise ValueError(f"{path}: só WAV é lido sem ffmpeg. Converta antes ou instale o ffmpeg.")
    with tempfile.TemporaryDirectory() as d:
        tmp = os.path.join(d, "in.wav")
        subprocess.run([exe, "-v", "error", "-y", "-i", path, "-ac", "2", "-c:a", "pcm_f32le", tmp],
                       check=True)
        return read(tmp)


def write(path, x, fs, bits=16):
    """bits=16 grava PCM com dither TPDF; bits=32 grava float."""
    x = np.asarray(x, float)
    if bits == 32:
        raw, tag = x.astype("<f4").tobytes(), 3
    else:
        rng = np.random.default_rng(0)
        d = (rng.random(x.shape) - rng.random(x.shape)) / 32768.0
        raw, tag = np.clip(np.round((x + d) * 32767), -32768, 32767).astype("<i2").tobytes(), 1
    ch = x.shape[1]
    hdr = struct.pack("<4sI4s4sIHHIIHH4sI", b"RIFF", 36 + len(raw), b"WAVE", b"fmt ", 16, tag, ch, fs,
                      fs * ch * bits // 8, ch * bits // 8, bits, b"data", len(raw))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as f:
        f.write(hdr + raw)
