package com.example.windows.audio

import com.example.data.audio.dsp.EqDsp
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Decodes a real file through the shipped decoders, no sound card. Set -Dtarkas.sample=<path>; skipped otherwise. */
class RealFileDecodeTest {
  @Test fun decodesWholeFileThroughTheEq() {
    val path = System.getProperty("tarkas.sample")
    assumeTrue(path != null && File(path).isFile)
    val p = PcmPipeline(EqDsp())
    val src = p.open(File(path!!))
    val out = ByteArray(4096 * src.format.frameSize)
    var frames = 0L
    var peak = 0
    while (true) {
      val n = p.render(src, 4096, out); if (n < 0) break
      frames += n / src.format.frameSize
      for (i in 0 until n / 2) peak = maxOf(peak, kotlin.math.abs(((out[2 * i].toInt() and 0xFF) or (out[2 * i + 1].toInt() shl 8)).toShort().toInt()))
    }
    src.close()
    println("decoded ${src.format}: $frames frames (${frames * 1000 / src.format.sampleRate.toLong()} ms), expected ${src.totalFrames}, peak $peak")
    assertTrue(frames > src.format.sampleRate && peak > 0)
  }
}
