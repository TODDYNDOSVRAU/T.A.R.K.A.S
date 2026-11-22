package com.example.data.audio.dsp

import com.example.data.audio.spatial.Fft
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Uniformly partitioned overlap-add stereo FFT Convolver for `.irs` / `.wav` impulse responses
 * (Viper4Android IRS, headphone/room impulse responses, analog gear captures).
 *
 * - Block size: 512 samples (~10.67 ms at 48 kHz), FFT size: 1024.
 * - Max IR length: 4096 taps (~85 ms at 48 kHz = 8 partitions), keeping CPU budget tiny on mobile.
 * - Zero heap allocations during [processFrame]; IR loading & FFT partitioning happen off-thread
 *   and swap atomically via `@Volatile`.
 * - Strict 0.00 dB flat bypass when disabled or no IR is loaded.
 */
class ConvolverDsp {

  data class ImpulseResponse(
    val sampleRate: Int,
    val left: FloatArray,
    val right: FloatArray,
    val name: String = ""
  )

  private class PartitionedIr(
    val sampleRate: Int,
    val numParts: Int,
    val irLRe: Array<DoubleArray>,
    val irLIm: Array<DoubleArray>,
    val irRRe: Array<DoubleArray>,
    val irRIm: Array<DoubleArray>
  )

  @Volatile private var enabled: Boolean = false
  @Volatile private var mix: Float = 1f
  @Volatile private var loadedPath: String? = null
  @Volatile private var rawIr: ImpulseResponse? = null
  @Volatile private var activeIr: PartitionedIr? = null

  private val loader = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
    Thread(r, "tarkas-convolver-io").apply {
      isDaemon = true
      priority = Thread.NORM_PRIORITY - 1
    }
  }
  private val loadGeneration = java.util.concurrent.atomic.AtomicInteger(0)

  private var sampleRate: Int = 48_000
  private val fft = Fft(FFT_SIZE)

  // Real-time audio-thread scratch & ring buffers (pre-allocated once)
  private val inL = DoubleArray(BLOCK_SIZE)
  private val inR = DoubleArray(BLOCK_SIZE)
  private val outL = DoubleArray(BLOCK_SIZE)
  private val outR = DoubleArray(BLOCK_SIZE)
  private val overlapL = DoubleArray(BLOCK_SIZE)
  private val overlapR = DoubleArray(BLOCK_SIZE)
  private var blockPos = 0

  // Frequency-domain delay line (FDL) for partitioned convolution
  private val fdlLRe = Array(MAX_PARTITIONS) { DoubleArray(FFT_SIZE) }
  private val fdlLIm = Array(MAX_PARTITIONS) { DoubleArray(FFT_SIZE) }
  private val fdlRRe = Array(MAX_PARTITIONS) { DoubleArray(FFT_SIZE) }
  private val fdlRIm = Array(MAX_PARTITIONS) { DoubleArray(FFT_SIZE) }
  private var fdlHead = 0

  private val accRe = DoubleArray(FFT_SIZE)
  private val accIm = DoubleArray(FFT_SIZE)

  val isProcessing: Boolean get() = enabled && mix > 0.005f && activeIr != null

  fun configure(sampleRate: Int) {
    val sr = sampleRate.coerceAtLeast(8_000)
    if (this.sampleRate != sr) {
      this.sampleRate = sr
      val ir = rawIr
      if (ir != null) {
        val gen = loadGeneration.incrementAndGet()
        loader.execute {
          val built = buildPartitions(ir, sr)
          if (loadGeneration.get() == gen) activeIr = built
        }
      }
      reset()
    }
  }

  /**
   * Called from the audio thread: NEVER performs disk I/O or FFT partitioning synchronously.
   * When [path] changes, dispatches loading to [loader] and swaps [activeIr] atomically.
   */
  fun update(enabled: Boolean, mix: Float, path: String?) {
    this.enabled = enabled
    this.mix = mix.coerceIn(0f, 1f)
    if (path != loadedPath) {
      loadedPath = path
      val gen = loadGeneration.incrementAndGet()
      if (path.isNullOrBlank()) {
        rawIr = null
        activeIr = null
      } else {
        val sr = sampleRate
        loader.execute {
          val file = File(path)
          val parsed = if (file.exists() && file.isFile) {
            runCatching { parseWav(file.readBytes(), file.name) }.getOrNull()
          } else null
          val built = parsed?.let { buildPartitions(it, sr) }
          if (loadGeneration.get() == gen) {
            rawIr = parsed
            activeIr = built
          }
        }
      }
    }
  }

  /** Directly loads an already-parsed [ImpulseResponse] (useful for JVM unit tests). */
  fun loadImpulse(ir: ImpulseResponse?) {
    rawIr = ir
    activeIr = ir?.let { buildPartitions(it, sampleRate) }
    reset()
  }

  fun loadFromFile(file: File): Boolean {
    if (!file.exists() || !file.isFile) {
      rawIr = null
      activeIr = null
      return false
    }
    val parsed = runCatching { parseWav(file.readBytes(), file.name) }.getOrNull()
    rawIr = parsed
    activeIr = parsed?.let { buildPartitions(it, sampleRate) }
    return parsed != null
  }

  fun reset() {
    inL.fill(0.0); inR.fill(0.0)
    outL.fill(0.0); outR.fill(0.0)
    overlapL.fill(0.0); overlapR.fill(0.0)
    blockPos = 0
    fdlHead = 0
    for (p in 0 until MAX_PARTITIONS) {
      fdlLRe[p].fill(0.0); fdlLIm[p].fill(0.0)
      fdlRRe[p].fill(0.0); fdlRIm[p].fill(0.0)
    }
  }

  /** Processes one interleaved frame at [base] in place. Zero heap allocations. */
  fun processFrame(buffer: FloatArray, base: Int, ch: Int) {
    val ir = activeIr
    val m = mix
    if (!enabled || m <= 0.005f || ir == null || ch <= 0) return

    val dryL = buffer[base].toDouble()
    val dryR = if (ch >= 2) buffer[base + 1].toDouble() else dryL

    inL[blockPos] = dryL
    inR[blockPos] = dryR

    val wetL = outL[blockPos]
    val wetR = outR[blockPos]

    buffer[base] = (dryL * (1.0 - m) + wetL * m).toFloat()
    if (ch >= 2) {
      buffer[base + 1] = (dryR * (1.0 - m) + wetR * m).toFloat()
    }

    blockPos++
    if (blockPos >= BLOCK_SIZE) {
      processBlock(ir)
      blockPos = 0
    }
  }

  private fun processBlock(ir: PartitionedIr) {
    val head = fdlHead
    val curLRe = fdlLRe[head]
    val curLIm = fdlLIm[head]
    val curRRe = fdlRRe[head]
    val curRIm = fdlRIm[head]

    for (i in 0 until BLOCK_SIZE) {
      curLRe[i] = inL[i]; curLIm[i] = 0.0
      curRRe[i] = inR[i]; curRIm[i] = 0.0
    }
    for (i in BLOCK_SIZE until FFT_SIZE) {
      curLRe[i] = 0.0; curLIm[i] = 0.0
      curRRe[i] = 0.0; curRIm[i] = 0.0
    }
    fft.forward(curLRe, curLIm)
    fft.forward(curRRe, curRIm)

    val parts = ir.numParts
    val invN = 1.0 / FFT_SIZE

    // Left channel
    accRe.fill(0.0); accIm.fill(0.0)
    for (p in 0 until parts) {
      val slot = (head - p + MAX_PARTITIONS) % MAX_PARTITIONS
      val xRe = fdlLRe[slot]; val xIm = fdlLIm[slot]
      val hRe = ir.irLRe[p]; val hIm = ir.irLIm[p]
      for (k in 0 until FFT_SIZE) {
        accRe[k] += xRe[k] * hRe[k] - xIm[k] * hIm[k]
        accIm[k] += xRe[k] * hIm[k] + xIm[k] * hRe[k]
      }
    }
    fft.inverse(accRe, accIm)
    for (i in 0 until BLOCK_SIZE) {
      outL[i] = accRe[i] * invN + overlapL[i]
      overlapL[i] = accRe[i + BLOCK_SIZE] * invN
    }

    // Right channel
    accRe.fill(0.0); accIm.fill(0.0)
    for (p in 0 until parts) {
      val slot = (head - p + MAX_PARTITIONS) % MAX_PARTITIONS
      val xRe = fdlRRe[slot]; val xIm = fdlRIm[slot]
      val hRe = ir.irRRe[p]; val hIm = ir.irRIm[p]
      for (k in 0 until FFT_SIZE) {
        accRe[k] += xRe[k] * hRe[k] - xIm[k] * hIm[k]
        accIm[k] += xRe[k] * hIm[k] + xIm[k] * hRe[k]
      }
    }
    fft.inverse(accRe, accIm)
    for (i in 0 until BLOCK_SIZE) {
      outR[i] = accRe[i] * invN + overlapR[i]
      overlapR[i] = accRe[i + BLOCK_SIZE] * invN
    }

    fdlHead = (head + 1) % MAX_PARTITIONS
  }

  private fun buildPartitions(ir: ImpulseResponse, targetRate: Int): PartitionedIr {
    val resampledL = resample(ir.left, ir.sampleRate, targetRate)
    val resampledR = resample(ir.right, ir.sampleRate, targetRate)
    val len = min(max(resampledL.size, resampledR.size), MAX_TAPS).coerceAtLeast(1)

    // Energy normalization to prevent sudden level jumps on hot IRs
    var energy = 0.0
    for (i in 0 until len) {
      val l = if (i < resampledL.size) resampledL[i].toDouble() else 0.0
      val r = if (i < resampledR.size) resampledR[i].toDouble() else 0.0
      energy += 0.5 * (l * l + r * r)
    }
    val norm = if (energy > 1.0) 1.0 / sqrt(energy) else 1.0

    val numParts = ((len + BLOCK_SIZE - 1) / BLOCK_SIZE).coerceIn(1, MAX_PARTITIONS)
    val irLRe = Array(numParts) { DoubleArray(FFT_SIZE) }
    val irLIm = Array(numParts) { DoubleArray(FFT_SIZE) }
    val irRRe = Array(numParts) { DoubleArray(FFT_SIZE) }
    val irRIm = Array(numParts) { DoubleArray(FFT_SIZE) }

    val builderFft = Fft(FFT_SIZE)
    for (p in 0 until numParts) {
      val start = p * BLOCK_SIZE
      for (i in 0 until BLOCK_SIZE) {
        val idx = start + i
        irLRe[p][i] = if (idx < resampledL.size) resampledL[idx] * norm else 0.0
        irRRe[p][i] = if (idx < resampledR.size) resampledR[idx] * norm else 0.0
      }
      builderFft.forward(irLRe[p], irLIm[p])
      builderFft.forward(irRRe[p], irRIm[p])
    }
    return PartitionedIr(targetRate, numParts, irLRe, irLIm, irRRe, irRIm)
  }

  companion object {
    const val BLOCK_SIZE = 512
    const val FFT_SIZE = BLOCK_SIZE * 2
    const val MAX_PARTITIONS = 8
    const val MAX_TAPS = BLOCK_SIZE * MAX_PARTITIONS

    /** Parses a standard RIFF WAVE (`.wav` / `.irs`) file: PCM 16/24/32-bit or IEEE float 32-bit. */
    fun parseWav(bytes: ByteArray, name: String = ""): ImpulseResponse? {
      if (bytes.size < 44) return null
      val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
      if (bytes[0] != 'R'.code.toByte() || bytes[1] != 'I'.code.toByte() ||
        bytes[2] != 'F'.code.toByte() || bytes[3] != 'F'.code.toByte()
      ) return null
      if (bytes[8] != 'W'.code.toByte() || bytes[9] != 'A'.code.toByte() ||
        bytes[10] != 'V'.code.toByte() || bytes[11] != 'E'.code.toByte()
      ) return null

      var audioFormat = 0
      var numChannels = 0
      var sampleRate = 0
      var bitsPerSample = 0
      var dataOffset = -1
      var dataSize = 0

      var pos = 12
      while (pos + 8 <= bytes.size) {
        val id = String(bytes, pos, 4, Charsets.US_ASCII)
        val size = bb.getInt(pos + 4)
        val chunkStart = pos + 8
        if (size < 0 || chunkStart + size > bytes.size) break
        when (id) {
          "fmt " -> if (size >= 16) {
            audioFormat = bb.getShort(chunkStart).toInt() and 0xFFFF
            numChannels = bb.getShort(chunkStart + 2).toInt() and 0xFFFF
            sampleRate = bb.getInt(chunkStart + 4)
            bitsPerSample = bb.getShort(chunkStart + 14).toInt() and 0xFFFF
          }
          "data" -> {
            dataOffset = chunkStart
            dataSize = size
          }
        }
        pos = chunkStart + size + (size and 1)
      }

      if (dataOffset < 0 || numChannels !in 1..2 || sampleRate < 8_000) return null
      val bytesPerSample = bitsPerSample / 8
      if (bytesPerSample !in 2..4) return null
      val frameBytes = bytesPerSample * numChannels
      val totalFrames = (dataSize / frameBytes).coerceAtMost(MAX_TAPS * 4)
      if (totalFrames <= 0) return null

      val left = FloatArray(totalFrames)
      val right = FloatArray(totalFrames)

      var off = dataOffset
      for (f in 0 until totalFrames) {
        val l = readSample(bytes, bb, off, audioFormat, bitsPerSample)
        off += bytesPerSample
        val r = if (numChannels == 2) {
          val v = readSample(bytes, bb, off, audioFormat, bitsPerSample)
          off += bytesPerSample
          v
        } else l
        left[f] = l
        right[f] = r
      }
      return ImpulseResponse(sampleRate, left, right, name)
    }

    private fun readSample(bytes: ByteArray, bb: ByteBuffer, off: Int, fmt: Int, bits: Int): Float = when {
      fmt == 3 && bits == 32 -> bb.getFloat(off).let { if (it.isFinite()) it else 0f }
      fmt == 1 && bits == 16 -> bb.getShort(off) / 32768f
      fmt == 1 && bits == 24 -> {
        val b0 = bytes[off].toInt() and 0xFF
        val b1 = bytes[off + 1].toInt() and 0xFF
        val b2 = bytes[off + 2].toInt() // sign-extended
        ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
      }
      fmt == 1 && bits == 32 -> bb.getInt(off) / 2147483648f
      else -> 0f
    }

    private fun resample(src: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
      if (srcRate == dstRate || src.isEmpty()) return src
      val ratio = srcRate.toDouble() / dstRate.toDouble()
      val dstLen = (src.size / ratio).toInt().coerceIn(1, MAX_TAPS)
      val out = FloatArray(dstLen)
      for (i in 0 until dstLen) {
        val pos = i * ratio
        val idx = floor(pos).toInt().coerceIn(0, src.size - 1)
        val next = min(idx + 1, src.size - 1)
        val frac = (pos - idx).toFloat()
        out[i] = src[idx] * (1f - frac) + src[next] * frac
      }
      return out
    }
  }
}
