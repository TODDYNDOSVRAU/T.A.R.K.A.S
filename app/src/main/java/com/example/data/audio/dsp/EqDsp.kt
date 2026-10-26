package com.example.data.audio.dsp

import com.example.data.model.EqBand
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
 * Every band keeps its own filter slot, even at 0 dB (where it is an exact pass-through), so moving a
 * fader through zero never restarts the filters. Coefficients and gains glide to new values over a few
 * milliseconds, and switching the equalizer on or off crossfades, so nothing clicks.
 *
 * Threading: [update] is called from the UI thread and only swaps an immutable [Plan]; [process]
 * runs on the audio thread and reads the current plan once per buffer.
 */
class EqDsp {

  /** Everything the audio thread needs for one buffer, computed ahead of time. */
  private class Plan(
    val sampleRate: Int,
    val enabled: Boolean,
    /** b0, b1, b2, a1, a2 per slot. */
    val coefs: DoubleArray,
    val preampGain: Float,
    val leftGain: Float,
    val rightGain: Float,
    val width: Float,
    val limiterEnabled: Boolean,
    val ceiling: Float
  ) {
    val slots: Int get() = coefs.size / 5
  }

  @Volatile private var settings: EqSettings = EqSettings()
  @Volatile private var sampleRate = DEFAULT_RATE
  @Volatile private var plan: Plan = buildPlan(settings, DEFAULT_RATE)

  private var channels = 2

  // Per slot, per channel: the two delay registers of a transposed direct form II biquad.
  private var state = DoubleArray(0)
  // The coefficients heard now, gliding towards the plan's.
  private var current = DoubleArray(0)
  private var currentSlots = -1

  // Gains heard now, gliding towards the plan's.
  private var preamp = 1f
  private var left = 1f
  private var right = 1f
  private var width = 1f
  /** 0 = bypassed, 1 = fully processed; glides when the equalizer is switched. */
  private var mix = 1f

  private var smoothCoef = smoothCoefFor(DEFAULT_RATE)

  /** The input of the current frame, for the on/off crossfade. */
  private var dryFrame = FloatArray(2)

  // Lookahead limiter: the signal is delayed so the gain can come down before a peak gets out.
  private var lookahead = lookaheadFor(DEFAULT_RATE)
  private var delayLine = FloatArray(0)
  private var delayPos = 0
  private var limiterGain = 1f
  private var attackStep = 0f
  private var attackTarget = 1f
  private var holdLeft = 0
  private var releaseCoef = releaseCoefFor(DEFAULT_RATE)

  fun configure(sampleRate: Int, channels: Int) {
    this.channels = channels.coerceAtLeast(1)
    smoothCoef = smoothCoefFor(sampleRate)
    releaseCoef = releaseCoefFor(sampleRate)
    lookahead = lookaheadFor(sampleRate)
    this.sampleRate = sampleRate
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
    currentSlots = -1
    delayLine = FloatArray(0)
    delayPos = 0
    limiterGain = 1f
    attackStep = 0f
    attackTarget = 1f
    holdLeft = 0
    val p = plan
    preamp = p.preampGain
    left = p.leftGain
    right = p.rightGain
    width = p.width
    mix = if (p.enabled) 1f else 0f
  }

  /** Processes [frames] interleaved frames of [buffer] in place. Samples are in -1..1. */
  fun process(buffer: FloatArray, frames: Int) {
    var p = plan
    if (p.sampleRate != sampleRate) {
      // A plan built for the previous rate (an update raced with configure): rebuild for this one.
      p = buildPlan(settings, sampleRate)
      plan = p
    }
    if (!p.enabled && mix <= 0f) return
    val ch = channels
    val nf = p.slots
    val target = p.coefs

    if (currentSlots != nf || state.size != nf * ch * 2) {
      // A band was added or removed: the layout changed, so start from the plan as it is.
      state = DoubleArray(nf * ch * 2)
      current = target.copyOf()
      currentSlots = nf
    }
    if (dryFrame.size != ch) dryFrame = FloatArray(ch)
    if (delayLine.size != lookahead * ch) {
      delayLine = FloatArray(lookahead * ch)
      delayPos = 0
    }
    val st = state
    val cur = current
    val k = smoothCoef
    val kf = k.toFloat()
    val mixTarget = if (p.enabled) 1f else 0f
    var gliding = !cur.contentEquals(target)

    for (frame in 0 until frames) {
      if (gliding) {
        var moving = false
        for (i in cur.indices) {
          val d = target[i] - cur[i]
          if (abs(d) > 1e-9) {
            cur[i] += d * k
            moving = true
          } else {
            cur[i] = target[i]
          }
        }
        gliding = moving
      }
      preamp = glide(preamp, p.preampGain, kf)
      left = glide(left, p.leftGain, kf)
      right = glide(right, p.rightGain, kf)
      width = glide(width, p.width, kf)
      mix = glide(mix, mixTarget, kf)

      val base = frame * ch
      for (c in 0 until ch) {
        val dry = buffer[base + c]
        dryFrame[c] = dry
        var x = (dry * preamp).toDouble()
        val s = c * 2
        for (f in 0 until nf) {
          val o = f * 5
          val idx = f * ch * 2 + s
          // Transposed direct form II
          val y = cur[o] * x + st[idx]
          st[idx] = cur[o + 1] * x - cur[o + 3] * y + st[idx + 1]
          st[idx + 1] = cur[o + 2] * x - cur[o + 4] * y
          x = y
        }
        buffer[base + c] = x.toFloat()
      }

      if (ch == 2) {
        var l = buffer[base]
        var r = buffer[base + 1]
        if (width != 1f) {
          val mid = (l + r) * 0.5f
          val side = (l - r) * 0.5f * width
          l = mid + side
          r = mid - side
        }
        buffer[base] = l * left
        buffer[base + 1] = r * right
      }

      if (mix < 1f) {
        // Crossfade with the untouched signal while the equalizer is being switched on or off.
        for (c in 0 until ch) buffer[base + c] = buffer[base + c] * mix + dryFrame[c] * (1f - mix)
      }

      if (p.limiterEnabled) limit(buffer, base, ch, p.ceiling)
      // Safety: never hand the sink a sample outside the legal range.
      for (c in 0 until ch) buffer[base + c] = buffer[base + c].coerceIn(-1f, 1f)
    }
  }

  /** One step of a smooth approach to [target], landing on it exactly once inaudibly close. */
  private fun glide(value: Float, target: Float, k: Float): Float {
    val next = value + (target - value) * k
    return if (abs(target - next) < 1e-5f) target else next
  }

  /**
   * A peak limiter with [lookahead]: the gain ramps down over the lookahead so it reaches the needed
   * reduction exactly when the peak leaves the delay line, holds for as long, then releases smoothly.
   * Unlike an instant-attack limiter it does not change the gain sample by sample at every peak, which
   * is what made loud presets sound distorted.
   */
  private fun limit(buffer: FloatArray, base: Int, ch: Int, ceiling: Float) {
    var peak = 0f
    for (c in 0 until ch) peak = max(peak, abs(buffer[base + c]))
    if (peak > ceiling) {
      val needed = ceiling / peak
      if (needed < limiterGain) {
        // Reach [needed] within the lookahead, i.e. by the time this peak comes out.
        attackStep = max(attackStep, (limiterGain - needed) / lookahead)
        attackTarget = min(attackTarget, needed)
      }
      holdLeft = lookahead * 2
    }
    if (limiterGain > attackTarget) {
      limiterGain = max(attackTarget, limiterGain - attackStep)
    } else if (holdLeft > 0) {
      holdLeft--
    } else {
      attackStep = 0f
      attackTarget = 1f
      limiterGain += (1f - limiterGain) * releaseCoef
    }

    // Out goes the sample from [lookahead] frames ago, at the gain computed for it now.
    val pos = delayPos * ch
    for (c in 0 until ch) {
      val delayed = delayLine[pos + c]
      delayLine[pos + c] = buffer[base + c]
      buffer[base + c] = delayed * limiterGain
    }
    delayPos = (delayPos + 1) % lookahead
  }

  companion object {
    private const val DEFAULT_RATE = 48000
    private const val RELEASE_SECONDS = 0.12
    private const val LOOKAHEAD_SECONDS = 0.002
    /** How long a change of coefficients or gain takes to settle (about 63% after this). */
    private const val SMOOTH_SECONDS = 0.008
    private const val TONE_Q = 0.707
    /** Below this, the automatic preamp leaves the level alone. */
    private const val AUTO_PREAMP_MIN_DB = 0.05

    private fun releaseCoefFor(rate: Int): Float = (1.0 - exp(-1.0 / (RELEASE_SECONDS * rate))).toFloat()
    private fun smoothCoefFor(rate: Int): Double = 1.0 - exp(-1.0 / (SMOOTH_SECONDS * rate))
    private fun lookaheadFor(rate: Int): Int = (LOOKAHEAD_SECONDS * rate).toInt().coerceAtLeast(1)

    private fun dbToGain(db: Float): Float = 10f.pow(db / 20f)

    /** Frequencies where the automatic preamp looks for the loudest boost: 1/6 octave, 20 Hz to 20 kHz. */
    private val PROBE_FREQS: DoubleArray = DoubleArray(61) { 20.0 * 2.0.pow(it / 6.0) }.filter { it <= 20000.0 }.toDoubleArray()

    private fun bandsOf(s: EqSettings): List<EqBand> = s.device?.bands.orEmpty() + s.activeBands

    /** The filters that actually change the sound; flat peaks and shelves are skipped. Used for the curve. */
    fun activeFilters(s: EqSettings, sampleRate: Int): List<BiquadCoefficients> {
      val rate = sampleRate.toDouble()
      val out = ArrayList<BiquadCoefficients>()
      bandsOf(s).forEach { b ->
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

    /**
     * One filter per band (disabled or flat bands are exact pass-throughs) plus the two tone shelves,
     * so the layout only changes when a band is added or removed.
     */
    private fun slotCoefficients(s: EqSettings, sampleRate: Int): DoubleArray {
      val rate = sampleRate.toDouble()
      val list = ArrayList<BiquadCoefficients>()
      bandsOf(s).forEach { b ->
        list += if (!b.enabled || (b.type.usesGain && abs(b.gainDb) < 0.05f)) BiquadCoefficients.IDENTITY
        else BiquadCoefficients.design(b.type, b.freqHz.toDouble(), b.gainDb.toDouble(), b.q.toDouble(), rate)
      }
      list += if (abs(s.bassDb) < 0.05f) BiquadCoefficients.IDENTITY
      else BiquadCoefficients.design(FilterType.LOW_SHELF, s.bassFreqHz.toDouble(), s.bassDb.toDouble(), TONE_Q, rate)
      list += if (abs(s.trebleDb) < 0.05f) BiquadCoefficients.IDENTITY
      else BiquadCoefficients.design(FilterType.HIGH_SHELF, s.trebleFreqHz.toDouble(), s.trebleDb.toDouble(), TONE_Q, rate)
      val out = DoubleArray(list.size * 5)
      list.forEachIndexed { i, c ->
        out[i * 5] = c.b0; out[i * 5 + 1] = c.b1; out[i * 5 + 2] = c.b2; out[i * 5 + 3] = c.a1; out[i * 5 + 4] = c.a2
      }
      return out
    }

    private fun buildPlan(s: EqSettings, sampleRate: Int): Plan = Plan(
      sampleRate = sampleRate,
      enabled = s.enabled,
      coefs = slotCoefficients(s, sampleRate),
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
      val (filters, preamp) = cached(s, sampleRate)
      var db = preamp.toDouble()
      filters.forEach { db += it.magnitudeDb(freqHz, sampleRate.toDouble()) }
      return db
    }

    /** The user's preamp, the headroom the fone correction asks for and, if on, the automatic preamp. */
    fun totalPreampDb(s: EqSettings): Float = cached(s, DEFAULT_RATE).second

    /**
     * How much the automatic preamp lowers the level: the loudest boost of the curve, so the curve never
     * goes above 0 dB and the limiter has nothing to do with ordinary music. 0 when nothing is boosted.
     */
    fun autoPreampDb(s: EqSettings): Float {
      if (!s.autoPreamp) return 0f
      val filters = activeFilters(s, DEFAULT_RATE)
      if (filters.isEmpty()) return 0f
      val manual = s.preampDb + (s.device?.preampDb ?: 0f)
      var loudest = Double.NEGATIVE_INFINITY
      for (f in PROBE_FREQS) {
        var db = manual.toDouble()
        filters.forEach { db += it.magnitudeDb(f, DEFAULT_RATE.toDouble()) }
        if (db > loudest) loudest = db
      }
      return if (loudest > AUTO_PREAMP_MIN_DB) (-loudest).toFloat() else 0f
    }

    // The graph asks for the response at many points with the same settings: design the filters once.
    private class Cache(val settings: EqSettings, val rate: Int, val filters: List<BiquadCoefficients>, val preamp: Float)
    @Volatile private var cache: Cache? = null

    private fun cached(s: EqSettings, sampleRate: Int): Pair<List<BiquadCoefficients>, Float> {
      cache?.let { if (it.rate == sampleRate && it.settings == s) return it.filters to it.preamp }
      val preamp = s.preampDb + (s.device?.preampDb ?: 0f) + autoPreampDb(s)
      val c = Cache(s, sampleRate, activeFilters(s, sampleRate), preamp)
      cache = c
      return c.filters to c.preamp
    }
  }
}
