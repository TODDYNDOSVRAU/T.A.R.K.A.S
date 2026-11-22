package com.example.data.audio.dsp

import com.example.data.model.BandChannel
import com.example.data.model.CrossfeedSettings
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.data.model.FilterSlope
import com.example.data.model.FilterType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

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

  private class DynamicSpec(
    val slotIndex: Int,
    val type: FilterType,
    val freqHz: Double,
    val baseGainDb: Double,
    val q: Double,
    val rangeDb: Double,
    val thresholdDb: Double,
    val detector: BiquadCoefficients
  )

  /** Everything the audio thread needs for one buffer, computed ahead of time. */
  private class Plan(
    val sampleRate: Int,
    val enabled: Boolean,
    /** b0, b1, b2, a1, a2 per slot. */
    val coefs: DoubleArray,
    /** Per-slot channel mask: 1=L, 2=R, 3=Stereo, 4=Mid, 8=Side. */
    val channelMasks: IntArray,
    /** True when the slot is non-identity or dynamic; settled identity slots are skipped in the hot loop. */
    val slotActive: BooleanArray,
    val hasMidSide: Boolean,
    val dynamicSpecs: List<DynamicSpec>,
    val preampGain: Float,
    val leftGain: Float,
    val rightGain: Float,
    val width: Float,
    val crossfeed: CrossfeedSettings,
    val limiterEnabled: Boolean,
    val ceiling: Float,
    val releaseCoef: Float = releaseCoefFor(DEFAULT_RATE, 120f),
    val limiterSoftClip: Boolean = false,
    val companderAmount: Float = 0f,
    val tubeWarmth: Float = 0f,
    val subBassHarmonic: Float = 0f,
    val convolverEnabled: Boolean = false,
    val convolverMix: Float = 1f,
    val convolverPath: String? = null,
    /** Gain on the untouched signal, so a comparison without the equalizer plays at the same volume; 1 = none. */
    val dryGain: Float = 1f
  ) {
    val slots: Int get() = coefs.size / 5
  }

  /** Internal 64-band FFT RTA tapped directly from this float pipeline (zero permissions required). */
  val rtaAnalyzer = RtaAnalyzer()

  @Volatile private var soloCoefs: BiquadCoefficients? = null
  private var soloState = DoubleArray(4)
  private var soloMix = 0f

  // Dynamic EQ per-band sidechain detector states (allocated only when dynamic bands exist).
  private var dynDetState = DoubleArray(0)
  private var dynEnv = DoubleArray(0)
  private var dynTarget = DoubleArray(0)
  private var dynLastGainDb = DoubleArray(0)
  private var dynRate = -1
  private var dynAtk = 0.0
  private var dynRel = 0.0

  @Volatile private var settings: EqSettings = EqSettings()
  @Volatile private var sampleRate = DEFAULT_RATE
  /** True while the spatial stage (which runs before this equalizer) is on; see [SPATIAL_HEADROOM_DB]. */
  @Volatile private var spatialStage = false
  /** The equalizer state to match in volume while comparing without it, or null when not comparing. */
  @Volatile private var reference: EqSettings? = null
  @Volatile private var plan: Plan = buildPlan(settings, DEFAULT_RATE, false, null)

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
  /** Gain of the untouched signal heard now, gliding towards the plan's. */
  private var dryGain = 1f

  private var smoothCoef = smoothCoefFor(DEFAULT_RATE)
  private val crossfeedDsp = CrossfeedDsp()
  private var lastCrossfeed: CrossfeedSettings? = null
  private val companderDsp = MultibandCompanderDsp()
  private val harmonicsDsp = AnalogHarmonicsDsp()
  val convolverDsp = ConvolverDsp()

  /** Optional native Rust DSP engine (`libtarkas_dsp.so`); null on plain JVM tests (falls back to Kotlin). */
  private val nativeEq: NativeEqDsp? = NativeEqDsp.createOrNull()
  private var lastNativePlan: Plan? = null

  /** True when audio is processed by the compiled Rust `tarkas-dsp` engine. */
  val isUsingNativeRust: Boolean get() = nativeEq != null

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

  // What the limiter and the final clamp did since the last [takeActivity]; written by the audio thread, read by the UI.
  private val lowestGainBits = AtomicInteger(java.lang.Float.floatToIntBits(1f))
  private val clippedSamples = AtomicInteger(0)

  fun configure(sampleRate: Int, channels: Int) {
    this.channels = channels.coerceAtLeast(1)
    smoothCoef = smoothCoefFor(sampleRate)
    releaseCoef = releaseCoefFor(sampleRate)
    lookahead = lookaheadFor(sampleRate)
    this.sampleRate = sampleRate
    rtaAnalyzer.sampleRate = sampleRate
    rtaAnalyzer.clear()
    crossfeedDsp.configure(sampleRate)
    lastCrossfeed = null
    companderDsp.configure(sampleRate, this.channels)
    harmonicsDsp.configure(sampleRate, this.channels)
    convolverDsp.configure(sampleRate)
    nativeEq?.configure(sampleRate, this.channels)
    lastNativePlan = null
    plan = buildPlan(settings, sampleRate, spatialStage, reference)
    reset()
  }

  /**
   * Press-and-hold band audition (Solo): isolates [band]'s centre frequency and Q with a narrow
   * band-pass filter so the listener can hunt resonances and sibilance. Pass null to exit Solo.
   */
  fun setSoloBand(band: EqBand?) {
    soloCoefs = band?.let {
      BiquadCoefficients.design(
        FilterType.BAND_PASS,
        it.freqHz.toDouble(),
        0.0,
        it.q.coerceIn(0.7f, 12f).toDouble(),
        sampleRate.toDouble()
      )
    }
  }

  fun update(newSettings: EqSettings) {
    settings = newSettings
    plan = buildPlan(newSettings, sampleRate, spatialStage, reference)
  }

  /**
   * Tells the equalizer whether the spatial stage is on. With the automatic preamp on, the level then drops
   * by [SPATIAL_HEADROOM_DB] more; the preamp glides to it like to any other change, so switching the
   * effect while music plays does not click.
   */
  fun setSpatialStage(on: Boolean) {
    spatialStage = on
    plan = buildPlan(settings, sampleRate, on, reference)
  }

  /**
   * While comparing without the equalizer, [reference] is the equalizer that was on: the untouched signal then
   * plays at the volume that curve would have given on average ([loudnessMatchDb]), so the comparison judges
   * the tone and not the loudness. Null ends it. The gain glides, like any other change.
   */
  fun setCompareReference(reference: EqSettings?) {
    this.reference = reference
    plan = buildPlan(settings, sampleRate, spatialStage, reference)
  }

  /** Clears filter memory, e.g. after a seek, so old samples do not ring into new ones. */
  fun reset() {
    val p = plan
    val ch = channels
    val nf = p.slots
    if (state.size == nf * ch * 2) state.fill(0.0) else state = DoubleArray(nf * ch * 2)
    if (current.size == p.coefs.size) p.coefs.copyInto(current) else current = p.coefs.copyOf()
    currentSlots = nf
    if (dryFrame.size == ch) dryFrame.fill(0f) else dryFrame = FloatArray(ch)
    if (delayLine.size == lookahead * ch) delayLine.fill(0f) else delayLine = FloatArray(lookahead * ch)
    delayPos = 0
    limiterGain = 1f
    attackStep = 0f
    attackTarget = 1f
    holdLeft = 0
    preamp = p.preampGain
    left = p.leftGain
    right = p.rightGain
    width = p.width
    mix = if (p.enabled) 1f else 0f
    dryGain = p.dryGain
    crossfeedDsp.update(if (p.enabled) p.crossfeed else CrossfeedSettings())
    lastCrossfeed = if (p.enabled) p.crossfeed else CrossfeedSettings()
    crossfeedDsp.reset()
    companderDsp.reset()
    harmonicsDsp.reset()
    convolverDsp.reset()
    nativeEq?.reset()
    if (soloState.size == ch * 2) soloState.fill(0.0) else soloState = DoubleArray(ch * 2)
    soloMix = 0f
    dynDetState.fill(0.0)
    dynEnv.fill(0.0)
    dynLastGainDb.fill(Double.NaN)
  }

  /** Processes [frames] interleaved frames of [buffer] in place. Samples are in -1..1. */
  fun process(buffer: FloatArray, frames: Int) {
    var p = plan
    if (p.sampleRate != sampleRate) {
      // A plan built for the previous rate (an update raced with configure): rebuild for this one.
      p = buildPlan(settings, sampleRate, spatialStage, reference)
      plan = p
    }
    val targetXf = if (p.enabled) p.crossfeed else CrossfeedSettings()
    if (targetXf !== lastCrossfeed) {
      crossfeedDsp.update(targetXf)
      lastCrossfeed = targetXf
    }
    companderDsp.update(if (p.enabled) p.companderAmount else 0f)
    harmonicsDsp.update(if (p.enabled) p.tubeWarmth else 0f, if (p.enabled) p.subBassHarmonic else 0f)
    convolverDsp.update(p.enabled && p.convolverEnabled, p.convolverMix, p.convolverPath)
    val solo = soloCoefs
    val rtaOn = rtaAnalyzer.enabled
    if (!p.enabled && mix <= 0f && dryGain == 1f && p.dryGain == 1f && !crossfeedDsp.isProcessing &&
      !companderDsp.isProcessing && !harmonicsDsp.isProcessing && !convolverDsp.isProcessing &&
      solo == null && soloMix <= 0f
    ) {
      if (rtaOn) {
        val ch = channels
        for (frame in 0 until frames) {
          val base = frame * ch
          val m = if (ch == 2) (buffer[base] + buffer[base + 1]) * 0.5f else buffer[base]
          rtaAnalyzer.pushPre(m)
          rtaAnalyzer.pushPostAndAdvance(m)
        }
      }
      return
    }
    val ch = channels
    val nf = p.slots
    val target: DoubleArray
    if (p.dynamicSpecs.isEmpty()) {
      target = p.coefs
    } else {
      if (dynTarget.size != p.coefs.size) {
        dynTarget = p.coefs.copyOf()
        dynLastGainDb.fill(Double.NaN)
      } else if (dynLastGainDb.isEmpty()) {
        p.coefs.copyInto(dynTarget)
      }
      updateDynamicTargets(buffer, frames, ch, p.dynamicSpecs, p.coefs, dynTarget)
      target = dynTarget
    }

    // Native Rust fast path: executes the ENTIRE DSP chain (Crossfeed, 64-bit TDF-II EQ cascade,
    // M/S, width/balance, Multiband Compander, 2x Tube/Tape + Sub-Bass, 16k-tap FFT Convolver,
    // Lookahead Peak Limiter + Soft-Clipper) in a single zero-GC native call.
    val rust = nativeEq
    if (rust != null && solo == null && soloMix <= 0f) {
      if (rtaOn) {
        for (frame in 0 until frames) {
          val base = frame * ch
          val m = if (ch == 2) (buffer[base] + buffer[base + 1]) * 0.5f else buffer[base]
          rtaAnalyzer.pushPre(m)
        }
      }
      if (p !== lastNativePlan || p.dynamicSpecs.isNotEmpty()) {
        lastNativePlan = p
        rust.updatePlan(
          enabled = p.enabled,
          coefs = target,
          masks = p.channelMasks,
          slotActive = p.slotActive,
          hasMidSide = p.hasMidSide,
          preampGain = p.preampGain,
          leftGain = p.leftGain,
          rightGain = p.rightGain,
          width = p.width,
          dryGain = p.dryGain,
          limiterEnabled = p.limiterEnabled,
          ceiling = p.ceiling,
          releaseCoef = p.releaseCoef,
          softClip = p.limiterSoftClip
        )
        rust.updateFx(
          crossfeedOn = p.enabled && p.crossfeed.enabled,
          crossfeedCutoffHz = p.crossfeed.effectiveCutoffHz,
          crossfeedFeedDb = p.crossfeed.effectiveFeedDb,
          companderAmount = if (p.enabled) p.companderAmount else 0f,
          tubeWarmth = if (p.enabled) p.tubeWarmth else 0f,
          subBassHarmonic = if (p.enabled) p.subBassHarmonic else 0f,
          convolverEnabled = p.enabled && p.convolverEnabled,
          convolverMix = p.convolverMix,
          convolverPath = p.convolverPath
        )
      }
      val packed = rust.process(buffer, frames)
      val lowestGain = java.lang.Float.intBitsToFloat((packed ushr 32).toInt())
      val clips = (packed and 0xFFFF_FFFFL).toInt()
      if (rtaOn) {
        for (frame in 0 until frames) {
          val base = frame * ch
          val m = if (ch == 2) (buffer[base] + buffer[base + 1]) * 0.5f else buffer[base]
          rtaAnalyzer.pushPostAndAdvance(m)
        }
      }
      publishActivity(lowestGain, clips)
      return
    }
    val masks = p.channelMasks
    val active = p.slotActive
    val hasMidSide = p.hasMidSide && ch == 2
    if (soloState.size != ch * 2) soloState = DoubleArray(ch * 2)

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
    var lowestGain = 1f
    var clips = 0

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
      dryGain = glide(dryGain, p.dryGain, kf)

      val base = frame * ch
      for (c in 0 until ch) {
        val raw = buffer[base + c]
        val dry = if (raw.isFinite()) raw else 0f
        dryFrame[c] = dry * dryGain
        buffer[base + c] = dry
      }
      if (rtaOn) {
        val mPre = if (ch == 2) (buffer[base] + buffer[base + 1]) * 0.5f else buffer[base]
        rtaAnalyzer.pushPre(mPre)
      }
      if (ch == 2 && crossfeedDsp.isProcessing) {
        crossfeedDsp.processFrame(buffer, base)
      }
      for (c in 0 until ch) {
        val chBit = if (ch == 2) (1 shl c) else 3
        var x = (buffer[base + c] * preamp).toDouble()
        val s = c * 2
        for (f in 0 until nf) {
          if (!gliding && !active[f]) continue
          val mask = masks[f]
          if (mask >= MASK_MID || (mask and chBit) == 0) continue
          val o = f * 5
          val idx = f * ch * 2 + s
          // Transposed direct form II
          val y = cur[o] * x + st[idx]
          if (!y.isFinite()) {
            st[idx] = 0.0
            st[idx + 1] = 0.0
            x = 0.0
          } else {
            st[idx] = cur[o + 1] * x - cur[o + 3] * y + st[idx + 1]
            st[idx + 1] = cur[o + 2] * x - cur[o + 4] * y
            x = y
          }
        }
        buffer[base + c] = x.toFloat()
      }
      if (hasMidSide) {
        var mid = (buffer[base].toDouble() + buffer[base + 1].toDouble()) * 0.5
        var side = (buffer[base].toDouble() - buffer[base + 1].toDouble()) * 0.5
        for (f in 0 until nf) {
          if (!gliding && !active[f]) continue
          val mask = masks[f]
          if (mask != MASK_MID && mask != MASK_SIDE) continue
          val o = f * 5
          val isMid = mask == MASK_MID
          val idx = f * ch * 2 + if (isMid) 0 else 2
          val x = if (isMid) mid else side
          val y = cur[o] * x + st[idx]
          if (!y.isFinite()) {
            st[idx] = 0.0
            st[idx + 1] = 0.0
            if (isMid) mid = 0.0 else side = 0.0
          } else {
            st[idx] = cur[o + 1] * x - cur[o + 3] * y + st[idx + 1]
            st[idx + 1] = cur[o + 2] * x - cur[o + 4] * y
            if (isMid) mid = y else side = y
          }
        }
        buffer[base] = (mid + side).toFloat()
        buffer[base + 1] = (mid - side).toFloat()
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

      if (companderDsp.isProcessing) {
        companderDsp.processFrame(buffer, base, ch)
      }
      if (harmonicsDsp.isProcessing) {
        harmonicsDsp.processFrame(buffer, base, ch)
      }
      if (convolverDsp.isProcessing) {
        convolverDsp.processFrame(buffer, base, ch)
      }

      if (mix < 1f) {
        // Crossfade with the untouched signal while the equalizer is being switched on or off.
        for (c in 0 until ch) buffer[base + c] = buffer[base + c] * mix + dryFrame[c] * (1f - mix)
      }

      val soloTarget = if (solo != null) 1f else 0f
      if (soloTarget > 0f || soloMix > 0f) {
        soloMix = glide(soloMix, soloTarget, kf)
        if (solo != null && soloMix > 0f) {
          for (c in 0 until ch) {
            val sIdx = c * 2
            val x = dryFrame[c].toDouble()
            val y = solo.b0 * x + soloState[sIdx]
            if (!y.isFinite()) {
              soloState[sIdx] = 0.0
              soloState[sIdx + 1] = 0.0
            } else {
              soloState[sIdx] = solo.b1 * x - solo.a1 * y + soloState[sIdx + 1]
              soloState[sIdx + 1] = solo.b2 * x - solo.a2 * y
              buffer[base + c] = buffer[base + c] * (1f - soloMix) + y.toFloat() * soloMix
            }
          }
        }
      }

      if (p.limiterEnabled) {
        limit(buffer, base, ch, p.ceiling, p.releaseCoef, p.limiterSoftClip)
        if (limiterGain < lowestGain) lowestGain = limiterGain
      } else if (p.limiterSoftClip) {
        applySoftClip(buffer, base, ch, p.ceiling)
      }
      // Safety: never hand the sink a sample outside the legal range. A sample caught here is a clip.
      for (c in 0 until ch) {
        val v = buffer[base + c]
        if (!v.isFinite()) {
          buffer[base + c] = 0f
        } else if (v > 1f || v < -1f) {
          clips++
          buffer[base + c] = v.coerceIn(-1f, 1f)
        }
      }
      if (rtaOn) {
        val mPost = if (ch == 2) (buffer[base] + buffer[base + 1]) * 0.5f else buffer[base]
        rtaAnalyzer.pushPostAndAdvance(mPost)
      }
    }
    publishActivity(lowestGain, clips)
  }

  /** Folds this buffer's lowest limiter gain and clip count into what [takeActivity] will report. */
  private fun publishActivity(lowestGain: Float, clips: Int) {
    if (clips > 0) clippedSamples.addAndGet(clips)
    if (lowestGain >= 1f) return
    while (true) {
      val seen = lowestGainBits.get()
      if (java.lang.Float.intBitsToFloat(seen) <= lowestGain) return
      if (lowestGainBits.compareAndSet(seen, java.lang.Float.floatToIntBits(lowestGain))) return
    }
  }

  /**
   * What the limiter and the clamp did since the last call, then starts over. Called from the UI thread a
   * few times a second; the audio thread only does two atomic updates per buffer.
   */
  fun takeActivity(): LimiterActivity {
    val gain = java.lang.Float.intBitsToFloat(lowestGainBits.getAndSet(java.lang.Float.floatToIntBits(1f)))
    return LimiterActivity(reductionDb = gainToReductionDb(gain), clippedSamples = clippedSamples.getAndSet(0))
  }

  /**
   * Sidechain detector for Dynamic EQ bands: runs once per buffer (~5..10 ms) with zero allocations,
   * updating only the dynamic slots inside [targetCoefs] when the envelope shifts by >= 0.02 dB.
   */
  private fun updateDynamicTargets(
    buffer: FloatArray,
    frames: Int,
    ch: Int,
    specs: List<DynamicSpec>,
    baseCoefs: DoubleArray,
    targetCoefs: DoubleArray
  ) {
    // Copy non-dynamic slots from baseCoefs while preserving already-designed dynamic slots inside the 0.02 dB deadband.
    baseCoefs.copyInto(targetCoefs)
    val n = specs.size
    if (dynDetState.size != n * 2) dynDetState = DoubleArray(n * 2)
    if (dynEnv.size != n) dynEnv = DoubleArray(n)
    if (dynLastGainDb.size != n) {
      dynLastGainDb = DoubleArray(n) { Double.NaN }
    }
    val srInt = sampleRate
    val rate = srInt.toDouble()
    if (dynRate != srInt) {
      dynRate = srInt
      dynAtk = 1.0 - exp(-1.0 / (0.015 * rate))
      dynRel = 1.0 - exp(-1.0 / (0.080 * rate))
    }
    val atk = dynAtk
    val rel = dynRel
    for (idx in 0 until n) {
      val spec = specs[idx]
      val d = spec.detector
      var z1 = dynDetState[idx * 2]
      var z2 = dynDetState[idx * 2 + 1]
      var env = dynEnv[idx]
      for (frame in 0 until frames) {
        val base = frame * ch
        val x = if (ch == 2) (buffer[base].toDouble() + buffer[base + 1].toDouble()) * 0.5 else buffer[base].toDouble()
        val y = d.b0 * x + z1
        z1 = d.b1 * x - d.a1 * y + z2
        z2 = d.b2 * x - d.a2 * y
        val mag = abs(y)
        env += (mag - env) * if (mag > env) atk else rel
      }
      if (!z1.isFinite() || !z2.isFinite()) { z1 = 0.0; z2 = 0.0 }
      if (!env.isFinite()) env = 0.0
      dynDetState[idx * 2] = z1
      dynDetState[idx * 2 + 1] = z2
      dynEnv[idx] = env
      val envDb = 20.0 * log10(env.coerceAtLeast(1e-6))
      val overDb = (envDb - spec.thresholdDb).coerceAtLeast(0.0)
      val frac = (overDb / 12.0).coerceIn(0.0, 1.0)
      val dynGain = (spec.baseGainDb + spec.rangeDb * frac).coerceIn(-24.0, 24.0)
      val prevGain = dynLastGainDb[idx]
      val o = spec.slotIndex * 5
      val usedGain = if (prevGain.isNaN() || abs(dynGain - prevGain) >= 0.02) {
        dynLastGainDb[idx] = dynGain
        dynGain
      } else {
        prevGain
      }
      BiquadCoefficients.designInto(targetCoefs, o, spec.type, spec.freqHz, usedGain, spec.q, rate)
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
  private fun limit(buffer: FloatArray, base: Int, ch: Int, ceiling: Float, relCoef: Float, softClip: Boolean) {
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
      limiterGain += (1f - limiterGain) * relCoef
    }

    // Out goes the sample from [lookahead] frames ago, at the gain computed for it now.
    val pos = delayPos * ch
    for (c in 0 until ch) {
      val delayed = delayLine[pos + c]
      delayLine[pos + c] = buffer[base + c]
      buffer[base + c] = delayed * limiterGain
    }
    delayPos = (delayPos + 1) % lookahead
    if (softClip) applySoftClip(buffer, base, ch, ceiling)
  }

  /** Smooth soft-clipper above 85% of [ceiling]; strictly linear (0.00 dB) below the knee. */
  private fun applySoftClip(buffer: FloatArray, base: Int, ch: Int, ceiling: Float) {
    val ceil = ceiling.coerceAtLeast(0.05f)
    val knee = ceil * 0.85f
    val span = ceil - knee
    for (c in 0 until ch) {
      val x = buffer[base + c]
      val ax = abs(x)
      if (ax > knee) {
        val shaped = knee + span * kotlin.math.tanh(((ax - knee) / span).toDouble()).toFloat()
        buffer[base + c] = if (x >= 0f) shaped else -shaped
      }
    }
  }

  companion object {
    private const val DEFAULT_RATE = 48000
    /**
     * PROVISIONAL display threshold (not a product target; the owner has not set one): the limiter is shown as
     * working only when it lowered the level by at least this many dB, so the settling tail of the release does
     * not flicker the indicator.
     */
    const val LIMITER_SHOWN_DB = 0.1f

    /** Largest volume correction the comparison applies, in dB. */
    const val MATCH_LIMIT_DB = 12f

    /** How far down a limiter gain is, in dB, 0 when it did nothing. */
    fun gainToReductionDb(gain: Float): Float = if (gain >= 1f) 0f else (-20.0 * log10(gain.coerceAtLeast(1e-6f).toDouble())).toFloat()

    private const val RELEASE_SECONDS = 0.12
    private const val LOOKAHEAD_SECONDS = 0.002
    /** How long a change of coefficients or gain takes to settle (about 63% after this). */
    private const val SMOOTH_SECONDS = 0.008
    private const val TONE_Q = 0.707
    /** Below this, the automatic preamp leaves the level alone. */
    private const val AUTO_PREAMP_MIN_DB = 0.05

    /**
     * Extra attenuation of the automatic preamp while the spatial stage is on, in dB. The owner decided it
     * (2026-11-13) from one measurement: on a real 44.1 kHz track the upmix and virtualizer with the room at
     * 0.35 raised the peak by up to 3.33 dB (sample peak) and 2.16 dB (true peak, 4x oversampled); see
     * tools/spatial_proto/peak_headroom.py. A single track, so it is a decision, not a guarantee.
     */
    const val SPATIAL_HEADROOM_DB = 3.5f

    private fun releaseCoefFor(rate: Int, releaseMs: Float = 120f): Float {
      val sec = (releaseMs.coerceIn(20f, 500f) / 1000.0)
      return (1.0 - exp(-1.0 / (sec * rate))).toFloat()
    }
    private fun smoothCoefFor(rate: Int): Double = 1.0 - exp(-1.0 / (SMOOTH_SECONDS * rate))
    private fun lookaheadFor(rate: Int): Int = (LOOKAHEAD_SECONDS * rate).toInt().coerceAtLeast(1)

    private fun dbToGain(db: Float): Float = 10f.pow(db / 20f)

    /** Frequencies where the automatic preamp looks for the loudest boost: 1/6 octave, 20 Hz to 20 kHz. */
    private val PROBE_FREQS: DoubleArray = DoubleArray(61) { 20.0 * 2.0.pow(it / 6.0) }.filter { it <= 20000.0 }.toDoubleArray()

    /** Precomputed 64-bit `(cos(w), sin(w), cos(2w), sin(2w))` tables per sampleRate. */
    private class FreqTrigLut(val freqs: DoubleArray, val rate: Int) {
      val cos1 = DoubleArray(freqs.size)
      val sin1 = DoubleArray(freqs.size)
      val cos2 = DoubleArray(freqs.size)
      val sin2 = DoubleArray(freqs.size)
      init {
        val sr = rate.toDouble()
        for (i in freqs.indices) {
          val w = 2.0 * PI * freqs[i] / sr
          cos1[i] = cos(w)
          sin1[i] = sin(w)
          cos2[i] = cos(2.0 * w)
          sin2[i] = sin(2.0 * w)
        }
      }
    }

    @Volatile private var probeLut: FreqTrigLut = FreqTrigLut(PROBE_FREQS, DEFAULT_RATE)
    @Volatile private var curveLut: FreqTrigLut? = null

    private fun probeLutFor(sampleRate: Int): FreqTrigLut {
      val cur = probeLut
      if (cur.rate == sampleRate) return cur
      return FreqTrigLut(PROBE_FREQS, sampleRate).also { probeLut = it }
    }

    private fun curveLutFor(freqs: DoubleArray, sampleRate: Int): FreqTrigLut {
      val cur = curveLut
      if (cur != null && cur.rate == sampleRate && cur.freqs === freqs) return cur
      return FreqTrigLut(freqs, sampleRate).also { curveLut = it }
    }

    /** Iterates over device correction bands and user bands without allocating a concatenated list. */
    private inline fun forEachBand(s: EqSettings, action: (EqBand) -> Unit) {
      val devBands = s.device?.bands
      if (devBands != null) {
        for (i in devBands.indices) action(devBands[i])
      }
      val userBands = s.bands
      for (i in userBands.indices) action(userBands[i])
    }

    private const val MASK_LEFT = 1
    private const val MASK_RIGHT = 2
    private const val MASK_STEREO = 3
    private const val MASK_MID = 4
    private const val MASK_SIDE = 8

    private fun maskOf(ch: BandChannel): Int = when (ch) {
      BandChannel.STEREO -> MASK_STEREO
      BandChannel.LEFT -> MASK_LEFT
      BandChannel.RIGHT -> MASK_RIGHT
      BandChannel.MID -> MASK_MID
      BandChannel.SIDE -> MASK_SIDE
    }

    /** The filters that actually change the sound; flat peaks and shelves are skipped. Used for the curve. */
    fun activeFilters(s: EqSettings, sampleRate: Int): List<BiquadCoefficients> {
      val rate = sampleRate.toDouble()
      val out = ArrayList<BiquadCoefficients>()
      forEachBand(s) { b ->
        if (!b.enabled) return@forEachBand
        if (b.type.usesGain && abs(b.gainDb) < 0.05f) return@forEachBand
        if (b.type.usesSlope && b.slope != FilterSlope.DB_12) {
          out += BiquadCoefficients.designSlope(b.type, b.freqHz.toDouble(), b.q.toDouble(), b.slope, rate)
        } else {
          out += BiquadCoefficients.design(b.type, b.freqHz.toDouble(), b.gainDb.toDouble(), b.q.toDouble(), rate)
        }
      }
      if (abs(s.bassDb) >= 0.05f) {
        out += BiquadCoefficients.design(FilterType.LOW_SHELF, s.bassFreqHz.toDouble(), s.bassDb.toDouble(), TONE_Q, rate)
      }
      if (abs(s.trebleDb) >= 0.05f) {
        out += BiquadCoefficients.design(FilterType.HIGH_SHELF, s.trebleFreqHz.toDouble(), s.trebleDb.toDouble(), TONE_Q, rate)
      }
      if (s.equalLoudnessEnabled) {
        out += EqualLoudnessDsp.biquads(true, s.equalLoudnessLevelPhon, sampleRate)
      }
      return out
    }

    /**
     * One or more filter slots per band (cascaded Butterworth sections for steeper HP/LP slopes;
     * disabled or flat bands stay as exact pass-throughs) plus the two tone shelves.
     */
    private fun buildSlots(s: EqSettings, sampleRate: Int): Triple<DoubleArray, IntArray, List<DynamicSpec>> {
      val rate = sampleRate.toDouble()
      val coefs = ArrayList<BiquadCoefficients>()
      val masks = ArrayList<Int>()
      val dynamics = ArrayList<DynamicSpec>()
      forEachBand(s) { b ->
        val mask = maskOf(b.channel)
        val slotStart = coefs.size
        val isDynamic = b.enabled && b.type.usesGain && abs(b.dynamicRangeDb) >= 0.05f
        if (!b.enabled || (!isDynamic && b.type.usesGain && abs(b.gainDb) < 0.05f)) {
          val sectionCount = if (b.type.usesSlope && b.slope != FilterSlope.DB_12) (b.slope.order + 1) / 2 else 1
          repeat(sectionCount) {
            coefs += BiquadCoefficients.IDENTITY
            masks += mask
          }
        } else if (b.type.usesSlope && b.slope != FilterSlope.DB_12) {
          val sections = BiquadCoefficients.designSlope(b.type, b.freqHz.toDouble(), b.q.toDouble(), b.slope, rate)
          sections.forEach { sec ->
            coefs += sec
            masks += mask
          }
        } else {
          coefs += BiquadCoefficients.design(b.type, b.freqHz.toDouble(), b.gainDb.toDouble(), b.q.toDouble(), rate)
          masks += mask
          if (isDynamic) {
            dynamics += DynamicSpec(
              slotIndex = slotStart,
              type = b.type,
              freqHz = b.freqHz.toDouble(),
              baseGainDb = b.gainDb.toDouble(),
              q = b.q.toDouble(),
              rangeDb = b.dynamicRangeDb.toDouble(),
              thresholdDb = b.dynamicThresholdDb.toDouble(),
              detector = BiquadCoefficients.design(FilterType.BAND_PASS, b.freqHz.toDouble(), 0.0, b.q.toDouble().coerceIn(0.7, 12.0), rate)
            )
          }
        }
      }
      coefs += if (abs(s.bassDb) < 0.05f) BiquadCoefficients.IDENTITY
      else BiquadCoefficients.design(FilterType.LOW_SHELF, s.bassFreqHz.toDouble(), s.bassDb.toDouble(), TONE_Q, rate)
      masks += MASK_STEREO
      coefs += if (abs(s.trebleDb) < 0.05f) BiquadCoefficients.IDENTITY
      else BiquadCoefficients.design(FilterType.HIGH_SHELF, s.trebleFreqHz.toDouble(), s.trebleDb.toDouble(), TONE_Q, rate)
      masks += MASK_STEREO
      val isoGains = EqualLoudnessDsp.gainsFor(s.equalLoudnessEnabled, s.equalLoudnessLevelPhon)
      coefs += if (abs(isoGains.bassDb) < 0.05) BiquadCoefficients.IDENTITY
      else BiquadCoefficients.design(FilterType.LOW_SHELF, EqualLoudnessDsp.LOW_SHELF_HZ, isoGains.bassDb, EqualLoudnessDsp.SHELF_Q, rate)
      masks += MASK_STEREO
      coefs += if (abs(isoGains.trebleDb) < 0.05) BiquadCoefficients.IDENTITY
      else BiquadCoefficients.design(FilterType.HIGH_SHELF, EqualLoudnessDsp.HIGH_SHELF_HZ, isoGains.trebleDb, EqualLoudnessDsp.SHELF_Q, rate)
      masks += MASK_STEREO
      val outCoefs = DoubleArray(coefs.size * 5)
      coefs.forEachIndexed { i, c ->
        outCoefs[i * 5] = c.b0; outCoefs[i * 5 + 1] = c.b1; outCoefs[i * 5 + 2] = c.b2; outCoefs[i * 5 + 3] = c.a1; outCoefs[i * 5 + 4] = c.a2
      }
      return Triple(outCoefs, masks.toIntArray(), dynamics)
    }

    private fun buildPlan(s: EqSettings, sampleRate: Int, spatialStage: Boolean, reference: EqSettings?): Plan {
      val (coefs, masks, dynamics) = buildSlots(s, sampleRate)
      val hasMidSide = masks.any { it == MASK_MID || it == MASK_SIDE }
      val dynamicSlots = if (dynamics.isEmpty()) emptySet() else dynamics.map { it.slotIndex }.toSet()
      val slotCount = coefs.size / 5
      val slotActive = BooleanArray(slotCount) { f ->
        if (f in dynamicSlots) true
        else {
          val o = f * 5
          abs(coefs[o] - 1.0) > 1e-9 || abs(coefs[o + 1]) > 1e-9 || abs(coefs[o + 2]) > 1e-9 ||
            abs(coefs[o + 3]) > 1e-9 || abs(coefs[o + 4]) > 1e-9
        }
      }
      return Plan(
        sampleRate = sampleRate,
        enabled = s.enabled,
        coefs = coefs,
        channelMasks = masks,
        slotActive = slotActive,
        hasMidSide = hasMidSide,
        dynamicSpecs = dynamics,
        preampGain = dbToGain(totalPreampDb(s, spatialStage, sampleRate)),
        leftGain = min(1f, 1f - s.balance),
        rightGain = min(1f, 1f + s.balance),
        width = s.stereoWidth,
        crossfeed = s.crossfeed,
        limiterEnabled = s.limiterEnabled,
        ceiling = dbToGain(s.limiterCeilingDb),
        releaseCoef = releaseCoefFor(sampleRate, s.limiterReleaseMs),
        limiterSoftClip = s.limiterSoftClip,
        companderAmount = s.companderAmount,
        tubeWarmth = s.tubeWarmth,
        subBassHarmonic = s.subBassHarmonic,
        convolverEnabled = s.convolverEnabled,
        convolverMix = s.convolverMix,
        convolverPath = s.convolverPath,
        dryGain = if (reference == null || s.enabled) 1f else dbToGain(loudnessMatchDb(reference, spatialStage, sampleRate))
      )
    }

    /**
     * The real frequency response of [s] at [freqHz] (filters + preamp), in dB, as the user will hear
     * it before the limiter. Drawn on the equalizer graph.
     */
    fun responseDb(s: EqSettings, freqHz: Double, sampleRate: Int = DEFAULT_RATE): Double {
      val (filters, preamp) = cached(s, sampleRate)
      var db = preamp.toDouble()
      for (i in filters.indices) db += filters[i].magnitudeDb(freqHz, sampleRate.toDouble())
      return db
    }

    /**
     * Evaluates the entire frequency response curve across [freqsHz] into [out] using a precomputed
     * 64-bit trigonometric look-up table (`cos(w), sin(w), cos(2w), sin(2w)`), with zero per-point
     * trig calls and zero heap allocations.
     */
    fun responseCurveInto(
      s: EqSettings,
      freqsHz: DoubleArray,
      out: FloatArray,
      subtractAutomaticPreamp: Boolean = true,
      sampleRate: Int = DEFAULT_RATE
    ) {
      val (filters, totalPre) = cached(s, sampleRate)
      val basePre = if (subtractAutomaticPreamp) s.preampDb.toDouble() else totalPre.toDouble()
      val lut = curveLutFor(freqsHz, sampleRate)
      val c1 = lut.cos1
      val s1 = lut.sin1
      val c2 = lut.cos2
      val s2 = lut.sin2
      val n = minOf(freqsHz.size, out.size)
      for (i in 0 until n) {
        var db = basePre
        val cos1 = c1[i]
        val sin1 = s1[i]
        val cos2 = c2[i]
        val sin2 = s2[i]
        for (f in filters.indices) {
          db += filters[f].magnitudeDbAtTrig(cos1, sin1, cos2, sin2)
        }
        out[i] = db.toFloat()
      }
    }

    /**
     * By how much the equalizer [s] changes the average level, in dB (negative: it lowers it), with the spatial
     * headroom included. Averages the response over 1/6-octave points from 20 Hz to 20 kHz in the power domain,
     * which weighs every octave equally (the spectrum of pink noise, close to music).
     *
     * PROVISIONAL: this weighting is not a product target. A perceptual weighting (K or A) may suit better;
     * only listening on a device can say. Limited to +-[MATCH_LIMIT_DB] so a wild curve cannot make the untouched
     * signal unreasonably loud or quiet.
     */
    fun loudnessMatchDb(s: EqSettings, spatialStage: Boolean = false, sampleRate: Int = DEFAULT_RATE): Float {
      if (!s.enabled) return 0f
      val (filters, preamp) = cached(s, sampleRate)
      val baseDb = preamp.toDouble() + spatialHeadroomDb(s, spatialStage).toDouble()
      val lut = probeLutFor(sampleRate)
      val freqs = lut.freqs
      val c1 = lut.cos1
      val s1 = lut.sin1
      val c2 = lut.cos2
      val s2 = lut.sin2
      val nyquist = sampleRate * 0.49
      var power = 0.0
      var count = 0
      for (i in freqs.indices) {
        if (freqs[i] <= nyquist) {
          var db = baseDb
          val cos1 = c1[i]; val sin1 = s1[i]; val cos2 = c2[i]; val sin2 = s2[i]
          for (f in filters.indices) db += filters[f].magnitudeDbAtTrig(cos1, sin1, cos2, sin2)
          power += 10.0.pow(db / 10.0)
          count++
        }
      }
      if (count == 0) return 0f
      val db = 10.0 * log10(power / count)
      return db.toFloat().coerceIn(-MATCH_LIMIT_DB, MATCH_LIMIT_DB)
    }

    /** The user's preamp, the headroom the fone correction asks for and, if on, the automatic preamp. */
    fun totalPreampDb(s: EqSettings, spatialStage: Boolean = false, sampleRate: Int = DEFAULT_RATE): Float =
      cached(s, sampleRate).second + spatialHeadroomDb(s, spatialStage)

    /** What the spatial stage adds to the automatic preamp: [SPATIAL_HEADROOM_DB] lower, only with the automatic preamp on. */
    private fun spatialHeadroomDb(s: EqSettings, spatialStage: Boolean): Float =
      if (spatialStage && s.autoPreamp) -SPATIAL_HEADROOM_DB else 0f

    /**
     * How much the automatic preamp lowers the level: the loudest boost of the curve, so the curve never
     * goes above 0 dB and the limiter has nothing to do with ordinary music. 0 when nothing is boosted.
     */
    fun autoPreampDb(s: EqSettings, spatialStage: Boolean = false, sampleRate: Int = DEFAULT_RATE): Float =
      curveAutoPreampDb(s, sampleRate) + spatialHeadroomDb(s, spatialStage)

    private fun curveAutoPreampDb(s: EqSettings, sampleRate: Int = DEFAULT_RATE, prebuiltFilters: List<BiquadCoefficients>? = null): Float {
      if (!s.autoPreamp) return 0f
      val filters = prebuiltFilters ?: activeFilters(s, sampleRate)
      if (filters.isEmpty()) return 0f
      val manual = (s.preampDb + (s.device?.preampDb ?: 0f)).toDouble()
      val lut = probeLutFor(sampleRate)
      val freqs = lut.freqs
      val c1 = lut.cos1
      val s1 = lut.sin1
      val c2 = lut.cos2
      val s2 = lut.sin2
      val nyquist = sampleRate * 0.49
      var loudest = Double.NEGATIVE_INFINITY
      for (i in freqs.indices) {
        if (freqs[i] > nyquist) continue
        var db = manual
        val cos1 = c1[i]; val sin1 = s1[i]; val cos2 = c2[i]; val sin2 = s2[i]
        for (f in filters.indices) db += filters[f].magnitudeDbAtTrig(cos1, sin1, cos2, sin2)
        if (db > loudest) loudest = db
      }
      return if (loudest > AUTO_PREAMP_MIN_DB) (-loudest).toFloat() else 0f
    }

    /** Canonical key for frequency-response caching so UI graph and DSP engine share 100% cache hits. */
    fun EqSettings.responseCacheKey(): EqSettings =
      if (balance == 0f && stereoWidth == 1f && limiterEnabled && limiterCeilingDb == 0f &&
        virtualizerStrength == 0 && !crossfeed.enabled && rtaMode == com.example.data.model.RtaMode.OFF
      ) this
      else copy(
        balance = 0f,
        stereoWidth = 1f,
        limiterEnabled = true,
        limiterCeilingDb = 0f,
        virtualizerStrength = 0,
        crossfeed = CrossfeedSettings(),
        rtaMode = com.example.data.model.RtaMode.OFF
      )

    // The graph and buildPlan both ask for the response with the same curve: design the filters once.
    private class Cache(val key: EqSettings, val rate: Int, val filters: List<BiquadCoefficients>, val preamp: Float)
    @Volatile private var cache: Cache? = null

    private fun cached(s: EqSettings, sampleRate: Int): Pair<List<BiquadCoefficients>, Float> {
      val key = s.responseCacheKey()
      cache?.let { if (it.rate == sampleRate && it.key == key) return it.filters to it.preamp }
      val filters = activeFilters(key, sampleRate)
      val preamp = key.preampDb + (key.device?.preampDb ?: 0f) + curveAutoPreampDb(key, sampleRate, filters)
      val c = Cache(key, sampleRate, filters, preamp)
      cache = c
      return c.filters to c.preamp
    }
  }
}

/** What the output stage did over a stretch of audio: the deepest limiter reduction, and clamped samples. */
data class LimiterActivity(val reductionDb: Float = 0f, val clippedSamples: Int = 0) {
  /** The limiter lowered the level enough to show ([EqDsp.LIMITER_SHOWN_DB], provisional). */
  val limiting: Boolean get() = reductionDb >= EqDsp.LIMITER_SHOWN_DB
  val clipped: Boolean get() = clippedSamples > 0
}
