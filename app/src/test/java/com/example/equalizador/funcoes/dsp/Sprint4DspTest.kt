package com.example.data.audio.dsp

import com.example.data.model.EqSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class Sprint4DspTest {

  private fun sineBuffer(freqHz: Double, rate: Int, frames: Int, amp: Float = 0.5f): FloatArray {
    val buf = FloatArray(frames * 2)
    for (i in 0 until frames) {
      val v = (amp * sin(2.0 * PI * freqHz * i / rate)).toFloat()
      buf[i * 2] = v
      buf[i * 2 + 1] = v
    }
    return buf
  }

  private fun rms(buf: FloatArray, skipFrames: Int = 256): Double {
    var sum = 0.0
    var n = 0
    for (i in skipFrames * 2 until buf.size step 2) {
      sum += buf[i] * buf[i]
      n++
    }
    return sqrt(sum / n.coerceAtLeast(1))
  }

  @Test
  fun equalLoudness_flatAt80PhonAndBoostsBassAtQuietLevels() {
    assertFalse(EqualLoudnessDsp.gainsFor(false, 50f).isActive)
    assertFalse(EqualLoudnessDsp.gainsFor(true, 80f).isActive)

    val quiet = EqualLoudnessDsp.gainsFor(true, 55f)
    assertTrue(quiet.isActive)
    assertTrue("Expected positive bass boost at 55 phon", quiet.bassDb > 5.0)
    assertTrue("Expected positive treble boost at 55 phon", quiet.trebleDb > 1.0)
    assertEquals(2, EqualLoudnessDsp.biquads(true, 55f, 48_000).size)
  }

  @Test
  fun multibandCompander_strictBypassAtZeroAndCompressesWhenActive() {
    val comp = MultibandCompanderDsp()
    comp.configure(48_000, 2)
    comp.update(0f)
    assertFalse(comp.isProcessing)

    val orig = sineBuffer(1000.0, 48_000, 2048, amp = 0.8f)
    val bypass = orig.copyOf()
    for (f in 0 until 2048) comp.processFrame(bypass, f * 2, 2)
    for (i in orig.indices) assertEquals(orig[i], bypass[i], 0f)

    comp.update(0.8f)
    assertTrue(comp.isProcessing)
    val hot = orig.copyOf()
    for (f in 0 until 2048) comp.processFrame(hot, f * 2, 2)
    assertTrue("Hot signal should be attenuated by compander", rms(hot) < rms(orig))
  }

  @Test
  fun analogHarmonics_strictBypassAtZeroAndAddsWarmthOrSubHarmonics() {
    val harm = AnalogHarmonicsDsp()
    harm.configure(48_000, 2)
    harm.update(0f, 0f)
    assertFalse(harm.isProcessing)

    val orig = sineBuffer(60.0, 48_000, 2048, amp = 0.4f)
    val bypass = orig.copyOf()
    for (f in 0 until 2048) harm.processFrame(bypass, f * 2, 2)
    for (i in orig.indices) assertEquals(orig[i], bypass[i], 0f)

    harm.update(0.7f, 0.5f)
    assertTrue(harm.isProcessing)
    val wet = orig.copyOf()
    for (f in 0 until 2048) harm.processFrame(wet, f * 2, 2)
    var maxDiff = 0f
    for (i in orig.indices) maxDiff = maxOf(maxDiff, abs(wet[i] - orig[i]))
    assertTrue("Tube + sub-bass harmonics should modify waveform", maxDiff > 0.01f)
    assertTrue("Output must remain finite", wet.all { it.isFinite() })
  }

  @Test
  fun convolver_parses16BitStereoWavAndConvolvesDiracDeltaAccurately() {
    // Build a minimal 16-bit stereo WAV containing a unit impulse at sample 0
    val frames = 16
    val dataBytes = frames * 2 * 2
    val wav = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    wav.put("RIFF".toByteArray(Charsets.US_ASCII))
    wav.putInt(36 + dataBytes)
    wav.put("WAVE".toByteArray(Charsets.US_ASCII))
    wav.put("fmt ".toByteArray(Charsets.US_ASCII))
    wav.putInt(16)
    wav.putShort(1) // PCM
    wav.putShort(2) // Stereo
    wav.putInt(48_000)
    wav.putInt(48_000 * 4)
    wav.putShort(4)
    wav.putShort(16)
    wav.put("data".toByteArray(Charsets.US_ASCII))
    wav.putInt(dataBytes)
    // Sample 0 = 0.5f on L and R
    wav.putShort(16384)
    wav.putShort(16384)
    for (i in 1 until frames) {
      wav.putShort(0)
      wav.putShort(0)
    }

    val ir = ConvolverDsp.parseWav(wav.array(), "dirac.wav")
    assertNotNull(ir)
    assertEquals(48_000, ir!!.sampleRate)
    assertEquals(frames, ir.left.size)
    assertEquals(0.5f, ir.left[0], 1e-3f)

    val conv = ConvolverDsp()
    conv.configure(48_000)
    conv.loadImpulse(ir)
    conv.update(enabled = true, mix = 1f, path = null)
    // Re-load impulse directly (update(path=null) only clears when loadedPath changes)
    conv.loadImpulse(ir)
    assertTrue(conv.isProcessing)

    val sig = sineBuffer(1000.0, 48_000, 2048, amp = 0.6f)
    val origRms = rms(sig, skipFrames = 1024)
    for (f in 0 until 2048) conv.processFrame(sig, f * 2, 2)
    val wetRms = rms(sig, skipFrames = 1024)
    // Dirac impulse of amplitude 0.5 should scale RMS by ~0.5
    assertEquals(origRms * 0.5, wetRms, 0.02)
  }

  @Test
  fun eqDsp_softClipPreventsHardClipWhileStayingLinearBelowKnee() {
    val dsp = EqDsp()
    dsp.configure(48_000, 2)
    dsp.update(EqSettings(enabled = true, limiterEnabled = false, limiterSoftClip = true, limiterCeilingDb = 0f, autoPreamp = false))

    val quiet = sineBuffer(1000.0, 48_000, 512, amp = 0.5f)
    val quietOrig = quiet.copyOf()
    dsp.process(quiet, 512)
    assertEquals(rms(quietOrig), rms(quiet), 1e-3)

    val hot = sineBuffer(1000.0, 48_000, 512, amp = 1.6f)
    dsp.process(hot, 512)
    assertTrue("Soft-clip must keep all samples strictly within [-1, 1]", hot.all { it in -1f..1f })
  }
}
