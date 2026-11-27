package com.example.windows.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner rules (2026-11-18, D7 2026-11-27): marble and cover keep text contrast at AA on their worst pixel. */
class BackdropTest {
  private val black = Color(0xFF080808); private val white = Color(0xFFF2F2F0)
  private val grayDark = Color(0xFF8C8C8C); private val grayLight = Color(0xFF6B6B6B)

  @Test fun darkThemeTextReadableOverWhiteCover() {
    assertTrue(ShellBackdrop.worstContrast(white, black) >= 4.5)
    assertTrue(ShellBackdrop.worstContrast(grayDark, black) >= 3.0) // secondary text: large/UI threshold
  }

  @Test fun lightThemeTextReadableOverBlackCover() {
    assertTrue(ShellBackdrop.worstContrast(black, white) >= 4.5)
    assertTrue(ShellBackdrop.worstContrast(grayLight, white) >= 3.0)
  }

  private val paper = Color(0xFFE9E9E7); private val grayPaper = Color(0xFF616160)
  private fun lum(argb: Int) = Color(argb).luminance()
  private fun baked(dark: Boolean): IntArray {
    val m = ShellBackdrop.marble(dark); val px = IntArray(m.width * m.height); m.readPixels(px); return px
  }

  /** D7: the marble alone keeps primary and secondary text at AA (4.5:1) on its most extreme pixel, blurred or not. */
  @Test fun darkMarbleKeepsAaOnItsBrightestPixel() {
    for (px in listOf(ShellBackdrop.marblePixels(true), baked(true))) {
      val top = Color(px.maxBy(::lum))
      assertTrue("white ${ShellBackdrop.contrast(white, top)}", ShellBackdrop.contrast(white, top) >= 4.5)
      assertTrue("gray ${ShellBackdrop.contrast(grayDark, top)}", ShellBackdrop.contrast(grayDark, top) >= 4.5)
      // A pure white cover on top keeps the same thresholds as before the marble.
      assertTrue(ShellBackdrop.worstContrast(white, black, top) >= 4.5)
      assertTrue("gray+cover ${ShellBackdrop.worstContrast(grayDark, black, top)}", ShellBackdrop.worstContrast(grayDark, black, top) >= 3.0)
    }
  }

  @Test fun lightMarbleKeepsAaOnItsDarkestPixel() {
    for (px in listOf(ShellBackdrop.marblePixels(false), baked(false))) {
      val low = Color(px.minBy(::lum))
      assertTrue(ShellBackdrop.contrast(black, low) >= 4.5)
      assertTrue("gray ${ShellBackdrop.contrast(grayPaper, low)}", ShellBackdrop.contrast(grayPaper, low) >= 4.5)
      assertTrue(ShellBackdrop.worstContrast(black, paper, low) >= 4.5)
      assertTrue("gray+cover ${ShellBackdrop.worstContrast(grayPaper, paper, low)}", ShellBackdrop.worstContrast(grayPaper, paper, low) >= 3.0)
    }
  }

  /** It is a marble, not a flat fill: luminance varies, and the hue leans green/teal (G and B above R). */
  @Test fun marbleHasVeinsAndATealHue() {
    for (dark in listOf(true, false)) {
      val px = ShellBackdrop.marblePixels(dark)
      val ls = px.map(::lum)
      val spread = ls.max() - ls.min()
      assertTrue("spread $spread dark=$dark", spread > if (dark) 0.008f else 0.05f)
      val r = px.sumOf { it shr 16 and 0xFF }; val g = px.sumOf { it shr 8 and 0xFF }; val b = px.sumOf { it and 0xFF }
      assertTrue("rgb $r $g $b dark=$dark", g > r && b > r)
    }
  }

  @Test fun marbleIsDeterministicAndSixteenByTen() {
    org.junit.Assert.assertArrayEquals(ShellBackdrop.marblePixels(true), ShellBackdrop.marblePixels(true))
    val m = ShellBackdrop.marble(true)
    org.junit.Assert.assertEquals(192, m.width); org.junit.Assert.assertEquals(120, m.height)
    org.junit.Assert.assertSame(m, ShellBackdrop.marble(true))   // baked once per process
  }

  /** 2.1.6 lag fix:the backdrop is baked once, small and already soft, instead of a live full-window blur. */
  @Test fun softenBakesASmallBlurredCopyKeepingAspect() {
    val w = 640; val h = 320
    val bmp = org.jetbrains.skia.Bitmap().apply { allocN32Pixels(w, h) }
    val cv = org.jetbrains.skia.Canvas(bmp)
    val black = org.jetbrains.skia.Paint().apply { color = 0xFF000000.toInt() }
    val white = org.jetbrains.skia.Paint().apply { color = 0xFFFFFFFF.toInt() }
    cv.drawRect(org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()), black)
    // Hard 32 px checkerboard: the worst case for a blur.
    for (y in 0 until h step 32) for (x in 0 until w step 32) if ((x / 32 + y / 32) % 2 == 0)
      cv.drawRect(org.jetbrains.skia.Rect.makeXYWH(x.toFloat(), y.toFloat(), 32f, 32f), white)
    val out = ShellBackdrop.soften(bmp.asComposeImageBitmap())
    org.junit.Assert.assertEquals(ShellBackdrop.SOFT_PX, out.width)
    org.junit.Assert.assertEquals(ShellBackdrop.SOFT_PX / 2, out.height)
    val px = IntArray(out.width * out.height); out.readPixels(px)
    var maxStep = 0
    for (y in 0 until out.height) for (x in 1 until out.width) {
      val a = px[y * out.width + x] and 0xFF; val b = px[y * out.width + x - 1] and 0xFF
      maxStep = maxOf(maxStep, kotlin.math.abs(a - b))
    }
    // Neighbouring pixels differ by less than 1/8 of full scale: no hard edge survives.
    assertTrue("max step $maxStep", maxStep < 32)
  }
}
