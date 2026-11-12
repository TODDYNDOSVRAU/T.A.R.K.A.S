package com.example

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.ui.screens.audio.BandColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BandColorsTest {

  private fun contrast(a: Color, b: Color): Float {
    val l1 = maxOf(a.luminance(), b.luminance())
    val l2 = minOf(a.luminance(), b.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
  }

  @Test fun darkSetReadsOnAbsoluteBlack() {
    val bg = Color(0xFF080808)
    BandColors.ON_DARK.forEachIndexed { i, c -> assertTrue("band $i: ${contrast(c, bg)}", contrast(c, bg) >= 3f) }
  }

  @Test fun lightSetReadsOnPrecisionWhite() {
    val bg = Color(0xFFF2F2F0)
    BandColors.ON_LIGHT.forEachIndexed { i, c -> assertTrue("band $i: ${contrast(c, bg)}", contrast(c, bg) >= 3f) }
  }

  @Test fun setsMatchAndHaveNoRepeats() {
    assertEquals(BandColors.ON_DARK.size, BandColors.ON_LIGHT.size)
    assertEquals(BandColors.ON_DARK.size, BandColors.ON_DARK.toSet().size)
    assertEquals(BandColors.ON_LIGHT.size, BandColors.ON_LIGHT.toSet().size)
  }

  @Test fun firstBandIsTheAccentAndColoursWrapAround() {
    assertEquals(Color(0xFF7EE3EA), BandColors.of(0, onDark = true))
    assertEquals(Color(0xFF0D6C74), BandColors.of(0, onDark = false))
    assertEquals(BandColors.of(0, true), BandColors.of(BandColors.ON_DARK.size, true))
    assertEquals(BandColors.of(7, true), BandColors.of(-1, true))
  }
}
