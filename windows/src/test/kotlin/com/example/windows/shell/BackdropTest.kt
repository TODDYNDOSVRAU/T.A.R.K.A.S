package com.example.windows.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.junit.Assert.assertTrue
import org.junit.Test

/** Owner rule (2026-11-18): the cover backdrop sits under a veil that keeps text contrast ≥ 4.5:1 on any cover. */
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

  /** 2.1.6 lag fix: the backdrop is baked once, small and already soft, instead of a live full-window blur. */
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
