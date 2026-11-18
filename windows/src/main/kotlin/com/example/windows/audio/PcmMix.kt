package com.example.windows.audio

import com.example.data.playback.CrossfadePlanner

/** Sample-level helpers on interleaved 16-bit little-endian PCM, pure so they are tested without a device. */
object PcmMix {

  /** Scales [len] bytes of [buf] by [g] (0..1) in place. 1 is untouched. */
  fun gain(buf: ByteArray, len: Int, g: Float) {
    if (g >= 1f) return
    val k = g.coerceAtLeast(0f)
    var i = 0
    while (i + 1 < len) {
      val v = ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)).toShort().toInt()
      val s = Math.round(v * k).coerceIn(-32768, 32767)
      buf[i] = s.toByte(); buf[i + 1] = (s shr 8).toByte()
      i += 2
    }
  }

  /**
   * Mixes the incoming chunk [inc] ([incLen] bytes valid, the rest silent) into [out] over [frames] frames,
   * equal power: out·cos + in·sin ([CrossfadePlanner]). [done] of [total] fade frames are already behind.
   */
  fun crossfade(out: ByteArray, inc: ByteArray, incLen: Int, frames: Int, channels: Int, done: Long, total: Long) {
    for (f in 0 until frames) {
      val t = ((done + f).toFloat() / total).coerceIn(0f, 1f)
      val go = CrossfadePlanner.outGain(t)
      val gi = CrossfadePlanner.inGain(t)
      for (c in 0 until channels) {
        val o = (f * channels + c) * 2
        val a = ((out[o].toInt() and 0xFF) or (out[o + 1].toInt() shl 8)).toShort().toInt()
        val b = if (o + 1 < incLen) ((inc[o].toInt() and 0xFF) or (inc[o + 1].toInt() shl 8)).toShort().toInt() else 0
        val s = Math.round(a * go + b * gi).coerceIn(-32768, 32767)
        out[o] = s.toByte(); out[o + 1] = (s shr 8).toByte()
      }
    }
  }
}
