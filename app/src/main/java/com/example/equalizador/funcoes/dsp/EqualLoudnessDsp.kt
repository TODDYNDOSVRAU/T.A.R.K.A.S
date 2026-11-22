package com.example.data.audio.dsp

import com.example.data.model.FilterType
import kotlin.math.abs

/**
 * ISO 226:2003 equal-loudness contour compensation.
 *
 * Human hearing loses sensitivity to bass (and slightly to extreme treble) at low listening levels.
 * Relative to an 80 phon reference, lowering the listening level boosts a low shelf (~85 Hz) and a
 * gentle high shelf (~10 kHz) so tonal balance remains consistent at quiet volumes.
 *
 * At 80 phon (or when disabled), both shelf gains are strictly 0.0 dB (zero-CPU flat bypass).
 */
object EqualLoudnessDsp {
  const val REF_PHON = 80f
  const val MIN_PHON = 40f
  const val MAX_PHON = 85f
  const val LOW_SHELF_HZ = 85.0
  const val HIGH_SHELF_HZ = 10_000.0
  const val SHELF_Q = 0.65

  data class ContourGains(val bassDb: Double, val trebleDb: Double) {
    val isActive: Boolean get() = abs(bassDb) >= 0.05 || abs(trebleDb) >= 0.05
  }

  /**
   * Computes differential bass/treble compensation in dB relative to [REF_PHON] (80 phon),
   * derived from ISO 226:2003 loudness contour slope differences at 63–100 Hz and 10 kHz.
   */
  fun gainsFor(enabled: Boolean, levelPhon: Float): ContourGains {
    if (!enabled) return ContourGains(0.0, 0.0)
    val phon = levelPhon.coerceIn(MIN_PHON, MAX_PHON)
    val deltaPhon = (REF_PHON - phon).toDouble()
    if (abs(deltaPhon) < 0.25) return ContourGains(0.0, 0.0)
    // ISO 226:2003 average differential slope: ~0.28 dB/phon in sub/low bass, ~0.06 dB/phon at 10 kHz.
    val bass = (deltaPhon * 0.28).coerceIn(-3.0, 12.0)
    val treble = (deltaPhon * 0.06).coerceIn(-1.0, 3.5)
    return ContourGains(bass, treble)
  }

  /** Builds the 2 biquad stages (low shelf + high shelf) for [sampleRate], or empty if inactive. */
  fun biquads(enabled: Boolean, levelPhon: Float, sampleRate: Int): List<BiquadCoefficients> {
    val g = gainsFor(enabled, levelPhon)
    if (!g.isActive) return emptyList()
    val list = ArrayList<BiquadCoefficients>(2)
    if (abs(g.bassDb) >= 0.05) {
      list += BiquadCoefficients.design(FilterType.LOW_SHELF, LOW_SHELF_HZ, g.bassDb, SHELF_Q, sampleRate.toDouble())
    }
    if (abs(g.trebleDb) >= 0.05) {
      list += BiquadCoefficients.design(FilterType.HIGH_SHELF, HIGH_SHELF_HZ, g.trebleDb, SHELF_Q, sampleRate.toDouble())
    }
    return list
  }
}
