package com.example.data.model

import com.example.data.audio.dsp.EqDsp
import kotlin.math.abs
import kotlin.math.pow

/**
 * Whether a curve still sounds like the preset it came from. Switching between the graphic and the
 * parametric layout, with every band flat, changes the settings but not the sound, so it must not mark
 * the preset as edited.
 */
object EqCompare {
  /** Points from 20 Hz to 20 kHz, and the largest difference still heard as the same curve. */
  private const val POINTS = 64
  private const val TOLERANCE_DB = 0.05

  fun edited(current: EqSettings, preset: EqSettings): Boolean {
    val a = current.userCurve
    val b = preset.userCurve
    if (a == b) return false
    // Anything outside the bands (preamp, tone, balance, width, limiter, virtualizer) must match exactly.
    if (a.withoutLayout() != b.withoutLayout()) return true
    return (0 until POINTS).any { i ->
      val f = 20.0 * 1000.0.pow(i / (POINTS - 1.0))
      abs(EqDsp.responseDb(a, f) - EqDsp.responseDb(b, f)) > TOLERANCE_DB
    }
  }

  private fun EqSettings.withoutLayout() = copy(parametric = false, bands = emptyList(), paramBands = emptyList())
}
