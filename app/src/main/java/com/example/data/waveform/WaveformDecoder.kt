package com.example.data.waveform

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/** How a decode ended. */
sealed interface DecodeResult {
  class Done(val amplitudes: ByteArray) : DecodeResult
  /** The file cannot be shown as a wave: protected, no audio track, a codec the device lacks, too long. */
  data object Unsupported : DecodeResult
  /** Asked to stop; nothing is saved. */
  data object Cancelled : DecodeResult
}

/**
 * Reads a track with the platform's own decoder and measures it with [PeakAccumulator]. Runs on the
 * caller's thread until the end of the file; [isCancelled] is asked between buffers.
 */
object WaveformDecoder {
  /** Longer than this is not worth minutes of decoding for a hundred bars (podcasts, mixes). */
  const val MAX_DURATION_MS = 20 * 60_000L

  private const val TIMEOUT_US = 10_000L

  fun decode(context: Context, uri: Uri, durationMs: Long, isCancelled: () -> Boolean): DecodeResult {
    if (durationMs > MAX_DURATION_MS) return DecodeResult.Unsupported
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    return try {
      extractor.setDataSource(context, uri, null)
      val index = (0 until extractor.trackCount).firstOrNull {
        extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
      } ?: return DecodeResult.Unsupported
      val format = extractor.getTrackFormat(index)
      extractor.selectTrack(index)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return DecodeResult.Unsupported
      val decoder = MediaCodec.createDecoderByType(mime)
      codec = decoder
      decoder.configure(format, null, null, 0)
      decoder.start()

      var channels = format.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 2)
      val sampleRate = format.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, 44_100)
      val headerUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else durationMs * 1000
      val acc = PeakAccumulator(estimatedFrames = headerUs * sampleRate / 1_000_000L)
      val info = MediaCodec.BufferInfo()
      var scratch = ShortArray(0)
      var inputDone = false
      var outputDone = false
      while (!outputDone) {
        if (isCancelled()) return DecodeResult.Cancelled
        if (!inputDone) {
          val i = decoder.dequeueInputBuffer(TIMEOUT_US)
          if (i >= 0) {
            val buf = decoder.getInputBuffer(i)!!
            val n = extractor.readSampleData(buf, 0)
            if (n < 0) {
              decoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              decoder.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }
        val o = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
        when {
          o >= 0 -> {
            if (info.size > 0) {
              val out = decoder.getOutputBuffer(o)!!
              out.position(info.offset)
              out.limit(info.offset + info.size)
              val shorts = out.order(ByteOrder.nativeOrder()).asShortBuffer()
              val count = shorts.remaining()
              if (scratch.size < count) scratch = ShortArray(count)
              shorts.get(scratch, 0, count)
              acc.add(scratch, count, channels)
            }
            decoder.releaseOutputBuffer(o, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
          }
          o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            channels = decoder.outputFormat.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channels)
          }
        }
      }
      if (acc.frames == 0L) DecodeResult.Unsupported else DecodeResult.Done(acc.result())
    } catch (e: Exception) {
      // DRM, a missing codec, a file removed meanwhile, a permission taken back: the track just has no wave.
      android.util.Log.w("WaveformDecoder", "Could not read $uri", e)
      DecodeResult.Unsupported
    } finally {
      try { codec?.stop() } catch (_: Exception) {}
      try { codec?.release() } catch (_: Exception) {}
      extractor.release()
    }
  }

  private fun MediaFormat.intOrDefault(key: String, default: Int) =
    if (containsKey(key)) getInteger(key) else default
}
