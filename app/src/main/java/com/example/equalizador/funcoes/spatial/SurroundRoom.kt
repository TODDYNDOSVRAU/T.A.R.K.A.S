package com.example.data.audio.spatial

import com.example.data.audio.spatial.SpatialEngine.Companion.BINS
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import com.example.data.audio.spatial.SpatialEngine.Companion.WIN
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The virtual room of the 5.1 virtualizer: first reflections of a rectangular room (image sources up to
 * the 2nd order) added to each speaker's direct sound, inside the same filter.
 *
 * When [exactPhysics] is true ([RoomParams.STUDIO]), reflection paths retain exact sub-sample delays
 * and apply both ISO 9613-1 atmospheric absorption over the extra path and frequency-dependent studio
 * wall absorption. [RoomParams.APPROVED] preserves exact prototype parameters for regression tests.
 */
data class RoomParams(
  val level: Double = 0.0,
  /** Metres: length (x), width (y), height (z). */
  val size: Triple<Double, Double, Double> = Triple(6.0, 5.0, 2.8),
  /** Metres from the listener to the back wall; centred on the width. */
  val listenerBack: Double = 2.7,
  val earHeight: Double = 1.2,
  /** Metres from the listener to each speaker. */
  val speakerDist: Double = 2.0,
  /** Fraction of the energy each wall absorbs per reflection. */
  val absorption: Double = 0.3,
  /** Enables ISO 9613-1 air absorption, wall HF damping, and exact sub-sample delays. */
  val exactPhysics: Boolean = false
) {
  val enabled: Boolean get() = level > 0.0

  companion object {
    const val APPROVED_LEVEL = 0.35
    val OFF = RoomParams()
    /** Legacy prototype room preset (tested byte-for-byte in SurroundRoomTest / MeasuredHrtfTest). */
    val APPROVED = RoomParams(level = APPROVED_LEVEL, exactPhysics = false)
    /** Full studio physics room: ISO 9613-1 air absorption + porous wall HF absorption + fractional delays. */
    val STUDIO = RoomParams(level = APPROVED_LEVEL, exactPhysics = true)
  }
}

/** One reflection: direction, delay (rounded samples & exact), gain relative to direct, bounce count, extra distance in m. */
class RoomPath(
  val azimuth: Double,
  val elevation: Double,
  val delay: Int,
  val gain: Double,
  val exactDelaySamples: Double = delay.toDouble(),
  val bounces: Int = 1,
  val extraDistanceM: Double = 0.0
)

object SurroundRoom {
  /** The longest filter whose frame-by-frame convolution is exact in the overlap-add: NFFT - WIN + 1. */
  const val FILTER_LEN = NFFT - WIN + 1
  const val FADE_IN = 16
  const val FADE_OUT = 128
  /** Samples after the direct sound (16 ms at 48 kHz) a reflection may arrive. */
  const val MAX_DELAY = 768
  const val MAX_ORDER = 2
  /** Hz: below this the reflections are removed (zero phase, 12 dB/octave). */
  const val HIGH_PASS = 250.0
  private const val SPEED_OF_SOUND = AcousticPhysics.SOUND_SPEED

  /** Reflections of a speaker at ([azimuth], [elevation]) degrees; listener at the origin, x forward. */
  fun paths(azimuth: Double, elevation: Double, p: RoomParams, sampleRate: Int): List<RoomPath> {
    val az = Math.toRadians(azimuth)
    val el = Math.toRadians(elevation)
    val src = doubleArrayOf(
      cos(el) * cos(az) * p.speakerDist,
      cos(el) * sin(az) * p.speakerDist,
      sin(el) * p.speakerDist
    )
    val (lx, ly, lz) = p.size
    val lo = doubleArrayOf(-p.listenerBack, -ly / 2, -p.earHeight)
    val hi = doubleArrayOf(lx - p.listenerBack, ly / 2, lz - p.earHeight)
    for (i in 0..2) {
      require(lo[i] < src[i] && src[i] < hi[i]) { "speaker outside the room: enlarge the room or reduce speakerDist" }
    }
    val r = sqrt(1.0 - p.absorption)
    val out = ArrayList<RoomPath>()
    val img = DoubleArray(3)
    for (kx in -MAX_ORDER..MAX_ORDER) for (ky in -MAX_ORDER..MAX_ORDER) for (kz in -MAX_ORDER..MAX_ORDER) {
      val order = abs(kx) + abs(ky) + abs(kz)
      if (order < 1 || order > MAX_ORDER) continue
      val ks = intArrayOf(kx, ky, kz)
      for (i in 0..2) {
        val k = ks[i]
        val mirrored = if (Math.floorMod(k, 2) == 0) src[i] else lo[i] + hi[i] - src[i]
        img[i] = mirrored + k * (hi[i] - lo[i])
      }
      val dist = sqrt(img[0] * img[0] + img[1] * img[1] + img[2] * img[2])
      val extraDist = dist - p.speakerDist
      val exactDelay = extraDist / SPEED_OF_SOUND * sampleRate
      val delay = Math.rint(exactDelay).toInt()
      if (delay > MAX_DELAY) continue
      val baseGain = if (p.exactPhysics) {
        // Wall frequency roll-off is applied per bin in addReflections; store 1/r * level here.
        p.level * p.speakerDist / dist
      } else {
        p.level * Math.pow(r, order.toDouble()) * p.speakerDist / dist
      }
      out += RoomPath(
        Math.toDegrees(atan2(img[1], img[0])),
        Math.toDegrees(asin(img[2] / dist)),
        delay,
        baseGain,
        exactDelaySamples = exactDelay,
        bounces = order,
        extraDistanceM = extraDist
      )
    }
    return out
  }

  /**
   * Adds the reflections of [paths] to the direct response ([hl], [hr]: interleaved re/im, 2 per bin), in place.
   * When [room.exactPhysics] is true, applies ISO 9613-1 air absorption, wall absorption per frequency, and fractional delay.
   */
  fun addReflections(
    hl: DoubleArray, hr: DoubleArray,
    paths: List<RoomPath>, hrtf: HrtfSource, sampleRate: Int,
    room: RoomParams = RoomParams.OFF
  ) {
    val highPass = DoubleArray(BINS) {
      val f = it * sampleRate.toDouble() / NFFT
      f * f / (f * f + HIGH_PASS * HIGH_PASS)
    }
    for (path in paths) {
      val (rl, rr) = hrtf.response(path.azimuth, path.elevation)
      val delSamples = if (room.exactPhysics) path.exactDelaySamples else path.delay.toDouble()
      for (k in 0 until BINS) {
        val f = k * sampleRate.toDouble() / NFFT
        val phase = -2 * PI * f * delSamples / sampleRate
        val magScale = if (room.exactPhysics) {
          path.gain * highPass[k] *
            AcousticPhysics.wallReflectionGain(f, path.bounces, room.absorption) *
            AcousticPhysics.airTransmissionGain(f, path.extraDistanceM)
        } else {
          path.gain * highPass[k]
        }
        val zr = magScale * cos(phase)
        val zi = magScale * sin(phase)
        hl[2 * k] += zr * rl[2 * k] - zi * rl[2 * k + 1]
        hl[2 * k + 1] += zr * rl[2 * k + 1] + zi * rl[2 * k]
        hr[2 * k] += zr * rr[2 * k] - zi * rr[2 * k + 1]
        hr[2 * k + 1] += zr * rr[2 * k + 1] + zi * rr[2 * k]
      }
    }
  }

  /**
   * Cuts the impulse response of [h] (interleaved re/im, 2 per bin) to [FILTER_LEN] samples with soft edges and
   * returns to the frequency domain, in place.
   */
  fun fit(h: DoubleArray, fft: Fft = Fft(NFFT)) {
    val re = DoubleArray(NFFT)
    val im = DoubleArray(NFFT)
    for (k in 0 until BINS) {
      re[k] = h[2 * k]; im[k] = h[2 * k + 1]
    }
    im[0] = 0.0; im[BINS - 1] = 0.0
    for (k in 1 until BINS - 1) {
      re[NFFT - k] = re[k]; im[NFFT - k] = -im[k]
    }
    fft.inverse(re, im)
    val scale = 1.0 / NFFT
    for (n in 0 until NFFT) {
      val w = when {
        n >= FILTER_LEN -> 0.0
        n < FADE_IN -> 0.5 - 0.5 * cos(PI * (n + 0.5) / FADE_IN)
        n >= FILTER_LEN - FADE_OUT -> 0.5 + 0.5 * cos(PI * (n - (FILTER_LEN - FADE_OUT) + 0.5) / FADE_OUT)
        else -> 1.0
      }
      re[n] *= scale * w
      im[n] = 0.0
    }
    fft.forward(re, im)
    for (k in 0 until BINS) {
      h[2 * k] = re[k]; h[2 * k + 1] = im[k]
    }
  }
}
