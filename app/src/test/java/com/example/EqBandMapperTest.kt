package com.example

import com.example.data.audio.EqBandMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class EqBandMapperTest {

  private val flat = List(15) { 0.toShort() }

  @Test
  fun `a flat curve stays flat on every hardware band`() {
    val out = EqBandMapper.toHardware(flat, listOf(60000, 230000, 910000, 3600000, 14000000), -1500, 1500)
    assertEquals(listOf<Short>(0, 0, 0, 0, 0), out)
  }

  @Test
  fun `a hardware band at a band centre takes exactly that band's level`() {
    val levels = flat.toMutableList().also { it[8] = 600 } // 1 kHz
    val out = EqBandMapper.toHardware(levels, listOf(1000000), -1500, 1500)
    assertEquals(listOf<Short>(600), out)
  }

  @Test
  fun `a frequency between two bands is interpolated`() {
    val levels = flat.toMutableList().also { it[7] = 200; it[8] = 400 } // 630 Hz and 1 kHz
    val mid = EqBandMapper.levelAt(levels, 794f) // geometric middle of 630 and 1000
    assertEquals(300f, mid, 5f)
  }

  @Test
  fun `a boost in the highs reaches the high hardware bands, not the low ones`() {
    val levels = flat.toMutableList().also { for (i in 11..14) it[i] = 800 }
    val out = EqBandMapper.toHardware(levels, listOf(60000, 230000, 910000, 3600000, 14000000), -1500, 1500)
    assertEquals(0, out[0].toInt())
    assertEquals(0, out[1].toInt())
    assertEquals(800, out[4].toInt())
  }

  @Test
  fun `frequencies outside the range use the edge bands`() {
    val levels = List(15) { (it * 10).toShort() }
    assertEquals(0f, EqBandMapper.levelAt(levels, 10f), 0.01f)
    assertEquals(140f, EqBandMapper.levelAt(levels, 20000f), 0.01f)
  }

  @Test
  fun `results are clamped to the hardware range`() {
    val levels = List(15) { 1500.toShort() }
    val out = EqBandMapper.toHardware(levels, listOf(1000000), -1200, 1200)
    assertEquals(listOf<Short>(1200), out)
  }
}
