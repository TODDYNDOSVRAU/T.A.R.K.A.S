package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Drawing and maths for the waveform progress bar: the track's 100 stored amplitudes (one byte each,
 * 0..255) resampled to the bars that fit the width, drawn twice, played part in one colour and the rest
 * in another, split at the current position.
 */
object WaveformBar {
  /** Width of a bar and the gap after it, in dp. */
  const val BAR_DP = 3f
  const val GAP_DP = 2f

  /** The shortest a bar is drawn, as a fraction of the height, so silence still shows as a line. */
  const val MIN_FRACTION = 0.1f

  /** The most a bar fills of the view's height; the rest is breathing room. */
  const val MAX_FRACTION = 0.8f

  /** How many bars fit in [widthPx]; at least 1. */
  fun barCount(widthPx: Float, barPx: Float, gapPx: Float): Int =
    max(1, floor((widthPx + gapPx) / (barPx + gapPx)).toInt())

  /**
   * [source] (0..255 per byte) resampled to [bars] values in 0f..1f. With more source points than bars
   * each bar takes the peak of the points it covers (a loud moment is never smoothed away); with fewer,
   * values are interpolated linearly. An empty source gives an empty array.
   */
  fun resample(source: ByteArray, bars: Int): FloatArray {
    if (source.isEmpty() || bars <= 0) return FloatArray(0)
    val n = source.size
    val out = FloatArray(bars)
    if (bars <= n) {
      for (i in 0 until bars) {
        val from = (i.toLong() * n / bars).toInt()
        val to = max(from + 1, ((i + 1).toLong() * n / bars).toInt())
        var peak = 0
        for (j in from until min(to, n)) peak = max(peak, source[j].toInt() and 0xFF)
        out[i] = peak / 255f
      }
    } else {
      for (i in 0 until bars) {
        val pos = if (bars == 1) 0f else i.toFloat() * (n - 1) / (bars - 1)
        val lo = floor(pos).toInt().coerceIn(0, n - 1)
        val hi = min(lo + 1, n - 1)
        val t = pos - lo
        val a = source[lo].toInt() and 0xFF
        val b = source[hi].toInt() and 0xFF
        out[i] = (a + (b - a) * t) / 255f
      }
    }
    return out
  }

  /**
   * Draws [amplitudes] as centred rounded bars. A bar is [played] when its centre is at or before
   * [fraction] of the width, [rest] otherwise. [grow] (0..1) raises the bars a little while the finger
   * is down; the layout does not change.
   */
  fun DrawScope.drawBars(
    amplitudes: FloatArray,
    fraction: Float,
    played: Color,
    rest: Color,
    barPx: Float,
    gapPx: Float,
    grow: Float
  ) {
    if (amplitudes.isEmpty()) return
    val count = amplitudes.size
    val step = (size.width - barPx) / max(1, count - 1).toFloat()
    val radius = CornerRadius(barPx / 2f)
    val maxH = size.height * (MAX_FRACTION + (1f - MAX_FRACTION) * 0.5f * grow)
    for (i in 0 until count) {
      val h = max(barPx, maxH * max(MIN_FRACTION, amplitudes[i].coerceIn(0f, 1f)))
      val x = if (count == 1) 0f else i * step
      val centre = (x + barPx / 2f) / size.width
      drawRoundRect(
        color = if (centre <= fraction) played else rest,
        topLeft = Offset(x, (size.height - h) / 2f),
        size = Size(barPx, h),
        cornerRadius = radius
      )
    }
  }
}
