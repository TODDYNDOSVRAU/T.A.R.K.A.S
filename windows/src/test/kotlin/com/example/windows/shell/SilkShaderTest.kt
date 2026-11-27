package com.example.windows.shell

import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * The owner's "seda líquida" shader, ported to SkSL: it compiles here, it moves with time, it is the same picture for
 * the same time, it keeps the approved look (bright threads, bronze at the top left, deep black between the folds), it
 * lifts under the pointer, and the limited version keeps text contrast. Evidence PNGs go to build/silk-render.
 */
class SilkShaderTest {
  companion object {
    const val W = 640; const val H = 400
    val dir = File("build/silk-render")
    @BeforeClass @JvmStatic fun clean() { dir.mkdirs(); dir.listFiles()?.forEach { it.delete() } }
  }

  private fun lum(argb: Int): Double {
    fun ch(v: Int): Double { val c = v / 255.0; return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * ch(argb shr 16 and 0xFF) + 0.7152 * ch(argb shr 8 and 0xFF) + 0.0722 * ch(argb and 0xFF)
  }

  private fun png(name: String, px: IntArray, w: Int = W, h: Int = H) {
    val bytes = ByteArray(w * h * 4)
    for (i in px.indices) { val p = px[i]
      bytes[i * 4] = (p and 0xFF).toByte(); bytes[i * 4 + 1] = (p shr 8 and 0xFF).toByte()
      bytes[i * 4 + 2] = (p shr 16 and 0xFF).toByte(); bytes[i * 4 + 3] = 0xFF.toByte()
    }
    val bmp = Bitmap().apply { allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.OPAQUE)); installPixels(bytes) }
    File(dir, "$name.png").writeBytes(Image.makeFromBitmap(bmp).encodeToData(EncodedImageFormat.PNG)!!.bytes)
  }

  private fun diffShare(a: IntArray, b: IntArray): Double = a.indices.count { a[it] != b[it] }.toDouble() / a.size

  @Test fun compiles() { assertNotNull("SkSL compile", ShellSilk.effect) }

  @Test fun movesWithTimeAndIsStableForTheSameTime() {
    val t0 = ShellSilk.renderPixels(W, H, 0f, dark = true)
    val t0b = ShellSilk.renderPixels(W, H, 0f, dark = true)
    val t2 = ShellSilk.renderPixels(W, H, 2f, dark = true)
    val t10 = ShellSilk.renderPixels(W, H, 10f, dark = true)
    assertArrayEquals("same time, same picture", t0, t0b)
    val d2 = diffShare(t0, t2); val d10 = diffShare(t0, t10)
    println("silk animation: pixels changed 0->2 s ${"%.1f".format(d2 * 100)}%, 0->10 s ${"%.1f".format(d10 * 100)}%")
    assertTrue("moves in 2 s ($d2)", d2 > 0.30)
    assertTrue("moves more in 10 s ($d10)", d10 > d2)
    png("dark-t00", t0); png("dark-t02", t2); png("dark-t10", t10)
  }

  @Test fun rawLookHasThreadsBronzeAndBlack() {
    val raw = ShellSilk.renderPixels(W, H, 3f, dark = true, limit = false)
    png("raw-dark", raw)
    val ls = raw.map(::lum).sorted()
    val max = ls.last(); val p10 = ls[ls.size / 10]
    println("silk raw: L p10=${"%.4f".format(p10)} median=${"%.4f".format(ls[ls.size / 2])} max=${"%.4f".format(max)}")
    assertTrue("bright threads ($max)", max > 0.20)
    assertTrue("deep black between folds ($p10)", p10 < 0.01)
    // Bronze: red over blue, more in the top-left quarter than in the bottom-right one.
    fun warm(x0: Int, y0: Int): Double {
      var s = 0.0; var n = 0
      for (y in y0 until y0 + H / 3) for (x in x0 until x0 + W / 3) { val p = raw[y * W + x]; s += (p shr 16 and 0xFF) - (p and 0xFF); n++ }
      return s / n
    }
    val tl = warm(0, 0); val br = warm(W - W / 3, H - H / 3)
    println("silk raw: warmth (R-B) top-left=${"%.2f".format(tl)} bottom-right=${"%.2f".format(br)}")
    assertTrue("bronze at the top left ($tl vs $br)", tl > br + 1.0)
  }

  @Test fun darkLimitKeepsPrimaryTextAa() {
    var worstL = 0.0
    for (t in listOf(0f, 3f, 7f, 13f, 29f)) worstL = maxOf(worstL, ShellSilk.renderPixels(W, H, t, dark = true).maxOf(::lum))
    png("limited-dark", ShellSilk.renderPixels(W, H, 3f, dark = true))
    val under = srgbOfLum(worstL)
    val white = ShellBackdrop.worstContrast(Color(0xFFF2F2F0), Color(0xFF080808), under)
    val gray = ShellBackdrop.worstContrast(Color(0xFF8C8C8C), Color(0xFF080808), under)
    println("silk dark limited: max L=${"%.4f".format(worstL)} white=${"%.2f".format(white)}:1 gray=${"%.2f".format(gray)}:1")
    assertTrue("max L $worstL", worstL <= ShellSilk.MAX_L_DARK + 0.002)
    assertTrue("primary text $white", white >= 4.5)
    // The dark marble's contract (BackdropTest): gray 4.5:1 on the silk alone, 3:1 under the worst cover.
    val grayAlone = ShellBackdrop.contrast(Color(0xFF8C8C8C), under)
    println("silk dark limited: gray alone=${"%.2f".format(grayAlone)}:1")
    assertTrue("gray alone $grayAlone", grayAlone >= 4.5)
    assertTrue("gray $gray", gray >= 3.0)
  }

  @Test fun lightFloorKeepsTextAa() {
    var worstL = 1.0
    val px = ShellSilk.renderPixels(W, H, 3f, dark = false)
    png("limited-light", px)
    for (t in listOf(0f, 3f, 7f, 13f, 29f)) worstL = minOf(worstL, ShellSilk.renderPixels(W, H, t, dark = false).minOf(::lum))
    val under = srgbOfLum(worstL)
    val black = ShellBackdrop.worstContrast(Color(0xFF080808), Color(0xFFF2F2F0), under)
    val gray = ShellBackdrop.worstContrast(Color(0xFF616160), Color(0xFFF2F2F0), under)
    println("silk light: min L=${"%.4f".format(worstL)} black=${"%.2f".format(black)}:1 gray=${"%.2f".format(gray)}:1")
    assertTrue("min L $worstL", worstL >= ShellSilk.MIN_L_LIGHT - 0.003)
    assertTrue("primary $black", black >= 4.5)
    assertTrue("gray $gray", gray >= 3.0)   // same contract as the light marble (BackdropTest), cover included
  }

  @Test fun thePointerLiftsTheSurfaceWhereItIs() {
    val off = ShellSilk.renderPixels(W, H, 3f, dark = true, limit = false)
    // Pointer at the centre (y up), fully lifted.
    val on = ShellSilk.renderPixels(W, H, 3f, dark = true, limit = false, mouseX = 0.5f, mouseY = 0.5f, hover = 1f)
    png("raw-dark-hover", on)
    // Mean absolute change of the green channel (0..255) in a square: the lift is a bump centred on the pointer.
    fun changed(x0: Int, y0: Int, s: Int): Double {
      var sum = 0.0; for (y in y0 until y0 + s) for (x in x0 until x0 + s) {
        sum += kotlin.math.abs((off[y * W + x] shr 8 and 0xFF) - (on[y * W + x] shr 8 and 0xFF))
      }
      return sum / (s * s)
    }
    val centre = changed(W / 2 - 40, H / 2 - 40, 80); val corner = changed(0, 0, 80)
    println("silk hover: mean change centre=${"%.2f".format(centre)} corner=${"%.2f".format(corner)} (of 255)")
    assertTrue("centre moves ($centre)", centre > 2.0)
    assertTrue("corner less ($corner)", corner < centre / 2)
  }

  @Test fun pointerSmoothingSettles() {
    val p = ShellSilk.Pointer
    p.reset(); p.at(1f, 0f)
    var t = 0f; while (p.step(1f / 60f) && t < 5f) t += 1f / 60f
    println("silk pointer: settles in ${"%.2f".format(t)} s")
    assertTrue(t in 0.3f..3f)
    assertTrue(p.x > 0.99f && p.y > 0.99f && p.hover > 0.99f)   // y flipped up
    p.left(); repeat(240) { p.step(1f / 60f) }
    assertTrue(p.hover < 0.01f)
    p.reset()
  }

  @Test fun cpuCostAtWindowSize() {
    // CPU raster is the slowest path (the app draws on the GPU); this is an upper bound, logged only.
    for ((w, h) in listOf(1280 to 800, 640 to 400)) {
      ShellSilk.renderPixels(w, h, 0f, dark = true)
      val n = 5; val t0 = System.nanoTime()
      repeat(n) { ShellSilk.renderPixels(w, h, it.toFloat(), dark = true) }
      println("silk cpu raster ${w}x$h: ${"%.1f".format((System.nanoTime() - t0) / 1e6 / n)} ms/frame")
    }
    png("dark-1280", ShellSilk.renderPixels(1280, 800, 3f, dark = true), 1280, 800)
    png("light-1280", ShellSilk.renderPixels(1280, 800, 3f, dark = false), 1280, 800)
    png("raw-dark-1280", ShellSilk.renderPixels(1280, 800, 3f, dark = true, limit = false), 1280, 800)
  }

  /** A neutral grey whose relative luminance is [l]. */
  private fun srgbOfLum(l: Double): Color {
    val c = if (l <= 0.0031308) l * 12.92 else 1.055 * l.pow(1 / 2.4) - 0.055
    return Color(c.toFloat(), c.toFloat(), c.toFloat())
  }
}
