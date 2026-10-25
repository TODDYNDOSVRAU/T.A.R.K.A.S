package com.example.data.audio.dsp

import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The audio chain applied to everything the app plays: preamp, the parametric bands, bass and
 * treble shelves, stereo width, balance and a peak limiter. Pure Kotlin on float samples, so it can
 * be unit-tested without a device.
 *
 * Threading: [update] is called from the UI thread and only swaps an immutable [Plan]; [process]
 * runs on the audio thread and reads the current plan once per buffer.
 */
class EqDsp {

  /** Everything the audio thread needs for one buffer, computed ahead of time. */
  private class Plan(
    val enabled: Boolean,
    val filters: Array<BiquadCoefficients>,
    val preampGain: Float,
    val leftGain: Float,
    val rightGain: Float,
    val width: Float,
    val limiterEnabled: Boolean,
    val ceiling: Float
  )

  @Volatile private var settings: EqSettings = EqSettings()
  @Volatile private var plan: Plan = buildPlan(settings, DEFAULT_RATE)

  private var sampleRate = DEFAULT_RATE
  private var channels = 2

  // Per filter, per channel: the two delay registers of a transposed direct form II biquad.
  private var state = DoubleArray(0)
  private var stateFilters = -1

  // Limiter gain envelope (1 = no reduction).
  private var limiterGain = 1f
  private var releaseCoef = releaseCoefFor(DEFAULT_RATE)

  fun configure(sampleRate: Int, channels: Int) {
    this.sampleRate = sampleRate
    this.channels = channels.coerceAtLeast(1)
    releaseCoef = releaseCoefFor(sampleRate)
    plan = buildPlan(settings, sampleRate)
    reset()
  }

  fun update(newSettings: EqSettings) {
    settings = newSettings
    plan = buildPlan(newSettings, sampleRate)
  }

  /** Clears filter memory, e.g. after a seek, so old samples do not ring into new ones. */
  fun reset() {
    state = DoubleArray(0)
    stateFilters = -1
    limiterGain = 1f
  }

  /** Processes [frames] interleaved frames of [buffer] in place. Samples are in -1..1. */
  fun process(buffer: FloatArray, frames: Int) {
    val p = plan
    if (!p.enabled) return
    val ch = channels
    val filters = p.filters
    val nf = filters.size

    if (stateFilters != nf || state.size != nf * ch * 2) {
      // The band layout changed: start the filters from silence (avoids reading stale registers).
      state = DoubleArray(nf * ch * 2)
      stateFilters = nf
    }
    val st = state

    for (frame in 0 until frames) {
      val base = frame * ch
      for (c in 0 until ch) {
        var x = (buffer[base + c] * p.preampGain).toDouble()
        val s = c * 2
        for (k in 0 until nf) {
          val f = filters[k]
          val idx = k * ch * 2 + s
          // Transposed direct form II
          val y = f.b0 * x + st[idx]
          st[idx] = f.b1 * x - f.a1 * y + st[idx + 1]
          st[idx + 1] = f.b2 * x - f.a2 * y
          x = y
        }
        buffer[base + c] = x.toFloat()
      }

      if (ch == 2) {
        var l = buffer[base]
        var r = buffer[base + 1]
        if (p.width != 1f) {
          val mid = (l + r) * 0.5f
          val side = (l - r) * 0.5f * p.width
          l = mid + side
          r = mid - side
        }
        buffer[base] = l * p.leftGain
        buffer[base + 1] = r * p.rightGain
      }

      if (p.limiterEnabled) {
        var peak = 0f
        for (c in 0 until ch) peak = max(peak, abs(buffer[base + c]))
        val target = if (peak > p.ceiling) p.ceiling / peak else 1f
        // Instant attack (no sample ever passes the ceiling), smooth release.
        limiterGain = if (target < limiterGain) target else limiterGain + (target - limiterGain) * releaseCoef
        if (limiterGain < 1f) {
          for (c in 0 until ch) buffer[base + c] *= limiterGain
        }
      }
      // Safety: never hand the sink a sample outside the legal range.
      for (c in 0 until ch) buffer[base + c] = buffer[base + c].coerceIn(-1f, 1f)
    }
  }

  companion object {
    private const val DEFAULT_RATE = 48000
    private const val RELEASE_SECONDS = 0.12
    private const val TONE_Q = 0.707

    private fun releaseCoefFor(rate: Int): Float = (1.0 - exp(-1.0 / (RELEASE_SECONDS * rate))).toFloat()

    private fun dbToGain(db: Float): Float = 10f.pow(db / 20f)

    /** The filters that actually change the sound; flat peaks and shelves are skipped to save CPU. */
    fun activeFilters(s: EqSettings, sampleRate: Int): List<BiquadCoefficients> {
      val rate = sampleRate.toDouble()
      val device = s.device?.bands.orEmpty()
      val out = ArrayList<BiquadCoefficients>(device.size + s.bands.size + 2)
      // The fone correction first, then the user's own curve on top of it.
      (device + s.bands).forEach { b ->
        if (!b.enabled) return@forEach
        if (b.type.usesGain && abs(b.gainDb) < 0.05f) return@forEach
        out += BiquadCoefficients.design(b.type, b.freqHz.toDouble(), b.gainDb.toDouble(), b.q.toDouble(), rate)
      }
      if (abs(s.bassDb) >= 0.05f) {
        out += BiquadCoefficients.design(FilterType.LOW_SHELF, s.bassFreqHz.toDouble(), s.bassDb.toDouble(), TONE_Q, rate)
      }
      if (abs(s.trebleDb) >= 0.05f) {
        out += BiquadCoefficients.design(FilterType.HIGH_SHELF, s.trebleFreqHz.toDouble(), s.trebleDb.toDouble(), TONE_Q, rate)
      }
      return out
    }

    private fun buildPlan(s: EqSettings, sampleRate: Int): Plan = Plan(
      enabled = s.enabled,
      filters = activeFilters(s, sampleRate).toTypedArray(),
      preampGain = dbToGain(totalPreampDb(s)),
      leftGain = min(1f, 1f - s.balance),
      rightGain = min(1f, 1f + s.balance),
      width = s.stereoWidth,
      limiterEnabled = s.limiterEnabled,
      ceiling = dbToGain(s.limiterCeilingDb)
    )

    /**
     * The real frequency response of [s] at [freqHz] (filters + preamp), in dB, as the user will hear
     * it before the limiter. Drawn on the equalizer graph.
     */
    fun responseDb(s: EqSettings, freqHz: Double, sampleRate: Int = DEFAULT_RATE): Double {
      val rate = sampleRate.toDouble()
      var db = totalPreampDb(s).toDouble()
      activeFilters(s, sampleRate).forEach { db += it.magnitudeDb(freqHz, rate) }
      return db
    }

    /** The user's preamp plus the headroom the fone correction asks for. */
    fun totalPreampDb(s: EqSettings): Float = s.preampDb + (s.device?.preampDb ?: 0f)
  }
}
