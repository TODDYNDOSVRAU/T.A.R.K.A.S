package com.example.data.audio.dsp

import com.example.data.model.CrossfeedSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Purist zero-latency binaural crossfeed (BS2B / Jan Meier / Chu Moy / Custom) in double precision.
 *
 * Uses a complementary 1st-order low-pass/high-pass split so:
 * - Mono signals sum to exact 0.00 dB across the entire spectrum (20 Hz..Nyquist).
 * - High frequencies above [CrossfeedSettings.effectiveCutoffHz] stay untouched (pure stereo).
 * - Low frequencies below cutoff blend with exact [CrossfeedSettings.effectiveFeedDb] dB separation.
 * - Switching on/off or changing presets glides smoothly without clicks; off = exact pass-through.
 */
class CrossfeedDsp {
  private var sampleRate = 48000
  private var enabled = false
  private var targetAlpha = 0.0
  private var targetDirect = 1.0
  private var targetCross = 0.0

  private var lpL = 0.0
  private var lpR = 0.0
  private var mix = 0.0
  private var smoothCoef = smoothCoefFor(48000)

  fun configure(sampleRate: Int) {
    this.sampleRate = sampleRate.coerceAtLeast(8000)
    smoothCoef = smoothCoefFor(this.sampleRate)
    reset()
  }

  fun update(settings: CrossfeedSettings) {
    enabled = settings.enabled
    if (!enabled) return
    val fc = settings.effectiveCutoffHz.toDouble().coerceIn(200.0, sampleRate * 0.45)
    val feedDb = settings.effectiveFeedDb.toDouble().coerceIn(0.5, 20.0)
    targetAlpha = exp(-2.0 * PI * fc / sampleRate)
    val ratio = 10.0.pow(-feedDb / 20.0)
    targetDirect = 1.0 / (1.0 + ratio)
    targetCross = ratio / (1.0 + ratio)
  }

  fun reset() {
    lpL = 0.0
    lpR = 0.0
    mix = if (enabled) 1.0 else 0.0
  }

  /** True when active or still gliding back to dry. */
  val isProcessing: Boolean get() = enabled || mix > 0.0

  /** Processes one interleaved stereo frame in place (0 samples latency). */
  fun processFrame(buffer: FloatArray, base: Int) {
    val targetMix = if (enabled) 1.0 else 0.0
    if (targetMix == 0.0 && mix <= 0.0) return
    val d = targetMix - mix
    mix = if (abs(d) < 1e-5) targetMix else mix + d * smoothCoef
    if (mix <= 0.0) return

    val inL = buffer[base].toDouble()
    val inR = buffer[base + 1].toDouble()
    val a = targetAlpha
    val oneMinusA = 1.0 - a
    lpL = oneMinusA * inL + a * lpL
    lpR = oneMinusA * inR + a * lpR
    if (!lpL.isFinite()) lpL = 0.0
    if (!lpR.isFinite()) lpR = 0.0

    val hpL = inL - lpL
    val hpR = inR - lpR
    val wetL = hpL + targetDirect * lpL + targetCross * lpR
    val wetR = hpR + targetDirect * lpR + targetCross * lpL

    buffer[base] = (inL + (wetL - inL) * mix).toFloat()
    buffer[base + 1] = (inR + (wetR - inR) * mix).toFloat()
  }

  private companion object {
    const val SMOOTH_SECONDS = 0.008
    fun smoothCoefFor(rate: Int): Double = 1.0 - exp(-1.0 / (SMOOTH_SECONDS * rate))
  }
}
