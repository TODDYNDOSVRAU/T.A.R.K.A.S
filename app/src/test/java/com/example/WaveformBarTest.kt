package com.example

import com.example.ui.components.WaveformBar
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformBarTest {
  private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

  @Test fun emptySourceOrNoBarsGivesNothing() {
    assertEquals(0, WaveformBar.resample(ByteArray(0), 10).size)
    assertEquals(0, WaveformBar.resample(bytes(1, 2), 0).size)
  }

  @Test fun sameCountKeepsValuesScaledTo01() {
    val out = WaveformBar.resample(bytes(0, 255, 51), 3)
    assertArrayEquals(floatArrayOf(0f, 1f, 0.2f), out, 1e-6f)
  }

  @Test fun fewerBarsTakeThePeakOfEachGroup() {
    // 6 points into 3 bars: groups (10,200) (30,20) (255,0).
    val out = WaveformBar.resample(bytes(10, 200, 30, 20, 255, 0), 3)
    assertArrayEquals(floatArrayOf(200 / 255f, 30 / 255f, 1f), out, 1e-6f)
  }

  @Test fun aLoudPointIsNeverLostWhenShrinking() {
    val src = ByteArray(100) { 5 }
    src[37] = 255.toByte()
    val out = WaveformBar.resample(src, 7)
    assertEquals(1f, out.max(), 0f)
  }

  @Test fun moreBarsInterpolateLinearlyAndKeepTheEnds() {
    val out = WaveformBar.resample(bytes(0, 255), 5)
    assertEquals(0f, out.first(), 0f)
    assertEquals(1f, out.last(), 0f)
    assertArrayEquals(floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), out, 1e-6f)
  }

  @Test fun singleBarIsDefined() {
    val out = WaveformBar.resample(bytes(100, 200), 1)
    assertEquals(1, out.size)
    assertEquals(200 / 255f, out[0], 1e-6f)
    assertEquals(1, WaveformBar.resample(bytes(100), 1).size)
  }

  @Test fun everyValueStaysInUnitRange() {
    val src = ByteArray(100) { (it * 7).toByte() }
    for (bars in listOf(1, 10, 99, 100, 101, 250)) {
      val out = WaveformBar.resample(src, bars)
      assertEquals(bars, out.size)
      assertTrue(out.all { it in 0f..1f })
    }
  }

  @Test fun barCountFitsTheWidthAndIsAtLeastOne() {
    // 360 px wide, 9 px bars with 6 px gap: (360+6)/15 = 24.4 -> 24 bars.
    assertEquals(24, WaveformBar.barCount(360f, 9f, 6f))
    assertEquals(1, WaveformBar.barCount(3f, 9f, 6f))
    assertEquals(1, WaveformBar.barCount(0f, 9f, 6f))
  }
}
