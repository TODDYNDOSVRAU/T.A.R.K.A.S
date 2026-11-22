package com.example.data.audio.dsp

import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class PeqEntrega2Test {

  private val rate = 48000

  @Test
  fun dynamicEqAttenuatesLoudSignalAboveThresholdAndLeavesQuietSignalUntouched() {
    val dsp = EqDsp()
    val settings = EqSettings(
      enabled = true,
      parametric = true,
      autoPreamp = false,
      limiterEnabled = false,
      bands = listOf(
        EqBand(
          freqHz = 4000f,
          gainDb = 0f,
          q = 2.0f,
          type = FilterType.PEAK,
          dynamicRangeDb = -8f,
          dynamicThresholdDb = -20f
        )
      )
    )
    dsp.update(settings)
    dsp.configure(rate, 2)

    val block = 480 // 10 ms blocks (realistic ExoPlayer buffer cadence)
    val blocks = 40 // 400 ms total

    // 1. Quiet 4 kHz sine at -32 dBFS (well below -20 dBFS threshold) -> ~0 dB change
    val quietAmp = 0.025f // ~ -32 dBFS
    var pOut = 0.0; var pRef = 0.0
    for (b in 0 until blocks) {
      val buf = FloatArray(block * 2)
      for (i in 0 until block) {
        val t = (b * block + i).toDouble()
        val s = (quietAmp * sin(2.0 * PI * 4000.0 * t / rate)).toFloat()
        buf[i * 2] = s; buf[i * 2 + 1] = s
      }
      val ref = buf.copyOf()
      dsp.process(buf, block)
      if (b >= blocks / 2) {
        for (i in 0 until block) {
          pOut += buf[i * 2] * buf[i * 2]
          pRef += ref[i * 2] * ref[i * 2]
        }
      }
    }
    val quietDb = 10.0 * log10(pOut / pRef)
    assertEquals("Quiet signal below threshold should remain flat (~0 dB)", 0.0, quietDb, 0.25)

    // 2. Loud 4 kHz sine at -6 dBFS (14 dB above -20 dBFS threshold) -> ~ -6 to -8 dB attenuation
    dsp.reset()
    val loudAmp = 0.5f // -6 dBFS
    pOut = 0.0; pRef = 0.0
    for (b in 0 until blocks) {
      val buf = FloatArray(block * 2)
      for (i in 0 until block) {
        val t = (b * block + i).toDouble()
        val s = (loudAmp * sin(2.0 * PI * 4000.0 * t / rate)).toFloat()
        buf[i * 2] = s; buf[i * 2 + 1] = s
      }
      val ref = buf.copyOf()
      dsp.process(buf, block)
      if (b >= blocks / 2) {
        for (i in 0 until block) {
          pOut += buf[i * 2] * buf[i * 2]
          pRef += ref[i * 2] * ref[i * 2]
        }
      }
    }
    val loudDb = 10.0 * log10(pOut / pRef)
    assertTrue("Loud signal above threshold should be attenuated strongly (< -4.5 dB), got $loudDb dB", loudDb < -4.5)
  }
}
