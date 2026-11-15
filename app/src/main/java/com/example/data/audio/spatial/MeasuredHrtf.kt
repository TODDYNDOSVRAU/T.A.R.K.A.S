package com.example.data.audio.spatial

import com.example.data.audio.spatial.SpatialEngine.Companion.BINS
import com.example.data.audio.spatial.SpatialEngine.Companion.NFFT
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/** Where the virtualizer gets a speaker's (or reflection's) head response from, for an exact direction. */
interface HrtfSource {
  val sampleRate: Int

  /** Interleaved (re, im) per bin, [BINS] bins per ear, left then right; frontal sound arrives at HRTF_DELAY. */
  fun response(azDeg: Double, elDeg: Double): Pair<DoubleArray, DoubleArray>
}

/**
 * The measured head (SADIE II subject D1, Neumann KU100 dummy head; Apache License 2.0, Copyright 2018,
 * University of York): impulse responses of just the directions the virtualizer uses, already aligned
 * (frontal sound at HRTF_DELAY) and diffuse-field normalised by tools/spatial_proto/export_hrtf.py.
 * Nearest direction by the largest dot product of unit vectors, as the prototype's SofaHrtf.
 *
 * File (little-endian): "TKHR", u32 version = 1, u32 sample rate, u32 directions, u32 taps; per direction
 * f32 azimuth, f32 elevation (degrees, positive azimuth to the left), f32[taps] left, f32[taps] right.
 */
class MeasuredHrtf private constructor(
  override val sampleRate: Int,
  private val taps: Int,
  private val units: Array<DoubleArray>,
  private val left: Array<FloatArray>,
  private val right: Array<FloatArray>
) : HrtfSource {
  val directions: Int get() = units.size

  private val fft = Fft(NFFT)

  /** Index of the stored direction nearest to ([azDeg], [elDeg]). */
  fun nearest(azDeg: Double, elDeg: Double): Int {
    val ux = unit(azDeg, elDeg)
    var best = 0
    var bestDot = Double.NEGATIVE_INFINITY
    for (i in units.indices) {
      val u = units[i]
      val dot = u[0] * ux[0] + u[1] * ux[1] + u[2] * ux[2]
      if (dot > bestDot) { bestDot = dot; best = i }
    }
    return best
  }

  /** The stored impulse response of direction [index] for one ear (0 = left). */
  fun impulse(index: Int, ear: Int): FloatArray = if (ear == 0) left[index] else right[index]

  @Synchronized
  override fun response(azDeg: Double, elDeg: Double): Pair<DoubleArray, DoubleArray> {
    val i = nearest(azDeg, elDeg)
    return spectrum(left[i]) to spectrum(right[i])
  }

  private fun spectrum(ir: FloatArray): DoubleArray {
    val re = DoubleArray(NFFT)
    val im = DoubleArray(NFFT)
    for (n in 0 until taps) re[n] = ir[n].toDouble()
    fft.forward(re, im)
    val out = DoubleArray(BINS * 2)
    for (k in 0 until BINS) { out[2 * k] = re[k]; out[2 * k + 1] = im[k] }
    return out
  }

  companion object {
    private const val HEADER = 20
    private const val MAX_DIRECTIONS = 4096

    /** Name of the asset for a sample rate, inside the assets folder. */
    fun assetName(sampleRate: Int) = "hrtf/hrtf_ku100_$sampleRate.bin"

    private fun unit(azDeg: Double, elDeg: Double): DoubleArray {
      val az = Math.toRadians(azDeg); val el = Math.toRadians(elDeg)
      return doubleArrayOf(cos(el) * cos(az), cos(el) * sin(az), sin(el))
    }

    /** Reads a file's bytes. Throws [IllegalArgumentException] saying what is wrong when it is not a valid file for [sampleRate]. */
    fun parse(bytes: ByteArray, sampleRate: Int): MeasuredHrtf {
      require(bytes.size >= HEADER) { "file too short (${bytes.size} bytes)" }
      val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
      require(bytes[0] == 'T'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
        bytes[2] == 'H'.code.toByte() && bytes[3] == 'R'.code.toByte()) { "bad magic" }
      val version = b.getInt(4)
      require(version == 1) { "unsupported version $version" }
      val rate = b.getInt(8)
      require(rate == sampleRate) { "file is for $rate Hz, not $sampleRate Hz" }
      val count = b.getInt(12)
      val taps = b.getInt(16)
      require(count in 1..MAX_DIRECTIONS) { "bad direction count $count" }
      require(taps in 1..NFFT) { "bad tap count $taps" }
      val expected = HEADER.toLong() + count.toLong() * (8 + 8L * taps)
      require(bytes.size.toLong() == expected) { "size ${bytes.size} does not match the header ($expected expected)" }
      var pos = HEADER
      val units = Array(count) { doubleArrayOf() }
      val left = Array(count) { FloatArray(0) }
      val right = Array(count) { FloatArray(0) }
      for (i in 0 until count) {
        val az = b.getFloat(pos).toDouble(); val el = b.getFloat(pos + 4).toDouble()
        require(az.isFinite() && el.isFinite()) { "direction $i is not a number" }
        pos += 8
        units[i] = unit(az, el)
        left[i] = FloatArray(taps) { b.getFloat(pos + 4 * it) }; pos += 4 * taps
        right[i] = FloatArray(taps) { b.getFloat(pos + 4 * it) }; pos += 4 * taps
        require(left[i].all { it.isFinite() } && right[i].all { it.isFinite() }) { "direction $i has a value that is not a number" }
      }
      return MeasuredHrtf(rate, taps, units, left, right)
    }

    /**
     * The measured head for [sampleRate] from [open] (the asset's stream, or null when there is none), or
     * null when there is no usable file; [onProblem] gets what was wrong, for the log. Never throws.
     */
    fun load(sampleRate: Int, open: (String) -> InputStream?, onProblem: (String) -> Unit = {}): MeasuredHrtf? {
      return try {
        val stream = open(assetName(sampleRate)) ?: return null.also { onProblem("no measured HRTF for $sampleRate Hz") }
        parse(stream.use { it.readBytes() }, sampleRate)
      } catch (e: Exception) {
        onProblem("measured HRTF for $sampleRate Hz not used: ${e.message ?: e.javaClass.simpleName}")
        null
      }
    }
  }
}
