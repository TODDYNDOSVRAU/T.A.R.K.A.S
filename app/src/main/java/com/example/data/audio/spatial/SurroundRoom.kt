package com.example.data.audio.spatial

import com.example.data.audio.spatial.SpatialEngine.Companion.BINS
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import com.example.data.audio.spatial.SpatialEngine.Companion.WIN
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The virtual room of the 5.1 virtualizer: first reflections of a rectangular room (image sources up to
 * the 2nd order) added to each speaker's direct sound, inside the same filter. Port of `room_paths` and
 * `fit_filter` in tools/spatial_proto/tarkas_spatial/virtualize.py.
 *
 * [level] 0 means no room (anechoic); 1 is the physical level of the reflections. The owner approved 0.35
 * after listening A/B ([APPROVED_LEVEL]) and it is always on in the app ([SurroundFilters.production]); a
 * [RoomParams] built without arguments is the anechoic one. Only the content of the filters changes: latency and the cost
 * per block are those of the dry virtualizer. All of this runs when the filters are built, never on the
 * audio thread.
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
  val absorption: Double = 0.3
) {
  val enabled: Boolean get() = level > 0.0

  companion object {
    const val APPROVED_LEVEL = 0.35
    val OFF = RoomParams()
    /** The room as approved by the owner. */
    val APPROVED = RoomParams(level = APPROVED_LEVEL)
  }
}

/** One reflection: direction as the listener hears it, extra delay in samples, gain relative to the direct sound. */
class RoomPath(val azimuth: Double, val elevation: Double, val delay: Int, val gain: Double)

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
  private const val SPEED_OF_SOUND = 343.0

  /** Reflections of a speaker at ([azimuth], [elevation]) degrees; listener at the origin, x forward. */
  fun paths(azimuth: Double, elevation: Double, p: RoomParams, sampleRate: Int): List<RoomPath> {
    val az = Math.toRadians(azimuth)
    val el = Math.toRadians(elevation)
    val src = doubleArrayOf(
      cos(el) * cos(az) * p.speakerDist,
      cos(el) * kotlin.math.sin(az) * p.speakerDist,
      kotlin.math.sin(el) * p.speakerDist
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
      // Python's round() goes to the even number on a tie; Math.rint does the same.
      val delay = Math.rint((dist - p.speakerDist) / SPEED_OF_SOUND * sampleRate).toInt()
      if (delay > MAX_DELAY) continue
      out += RoomPath(
        Math.toDegrees(atan2(img[1], img[0])),
        Math.toDegrees(asin(img[2] / dist)),
        delay,
        p.level * Math.pow(r, order.toDouble()) * p.speakerDist / dist
      )
    }
    return out
  }

  /**
   * Adds the reflections of [paths] to the direct response ([hl], [hr]: interleaved re/im, 2 per bin), in place.
   * Each reflection goes through the HRTF of its own direction, delayed and high-passed.
   */
  fun addReflections(hl: DoubleArray, hr: DoubleArray, paths: List<RoomPath>, hrtf: HrtfSource, sampleRate: Int) {
    val highPass = DoubleArray(BINS) {
      val f = it * sampleRate.toDouble() / NFFT
      f * f / (f * f + HIGH_PASS * HIGH_PASS)
    }
    for (path in paths) {
      val (rl, rr) = hrtf.response(path.azimuth, path.elevation)
      for (k in 0 until BINS) {
        val f = k * sampleRate.toDouble() / NFFT
        val phase = -2 * PI * f * path.delay / sampleRate
        val zr = path.gain * highPass[k] * cos(phase)
        val zi = path.gain * highPass[k] * kotlin.math.sin(phase)
        hl[2 * k] += zr * rl[2 * k] - zi * rl[2 * k + 1]
        hl[2 * k + 1] += zr * rl[2 * k + 1] + zi * rl[2 * k]
        hr[2 * k] += zr * rr[2 * k] - zi * rr[2 * k + 1]
        hr[2 * k + 1] += zr * rr[2 * k + 1] + zi * rr[2 * k]
      }
    }
  }

  /**
   * Cuts the impulse response of [h] (interleaved re/im, 2 per bin) to [FILTER_LEN] samples with soft edges and
   * returns to the frequency domain, in place. Without it the HRTF's fractional interaural delay spreads energy
   * over the whole frame, which the STFT folds back as error; after it the frame-by-frame convolution is exact.
   */
  fun fit(h: DoubleArray, fft: Fft = Fft(NFFT)) {
    val re = DoubleArray(NFFT)
    val im = DoubleArray(NFFT)
    for (k in 0 until BINS) {
      re[k] = h[2 * k]; im[k] = h[2 * k + 1]
    }
    // A real signal's DC and Nyquist bins are real; a real inverse transform drops their imaginary part.
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
