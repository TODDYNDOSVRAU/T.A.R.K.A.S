package com.example.windows.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Sharpness of a cover drawn at the window sizes and scales the owner cares about (1920x1080 and 3840x2160,
 * 100/125/150%). A fine checkerboard stands for cover detail; the error is the mean distance from the ideal
 * (a straight area average of the source), so lower is sharper. High must not be worse than Low anywhere.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SharpnessTest {
  private fun source(n: Int): org.jetbrains.skia.Image {
    val bmp = Bitmap().apply { allocPixels(ImageInfo.makeN32(n, n, ColorAlphaType.PREMUL)) }
    val px = IntArray(n * n) { i -> if ((i % n / 3 + i / n / 3) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
    val bytes = java.nio.ByteBuffer.allocate(n * n * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    px.forEach { bytes.putInt(it) }
    bmp.installPixels(bytes.array())
    return org.jetbrains.skia.Image.makeFromBitmap(bmp)
  }

  /** Mean absolute error of the rendered cover against the source averaged to the same size (the ideal). */
  private fun error(filter: FilterQuality, scale: Float, sizeDp: Int): Double {
    val px = (sizeDp * scale).toInt()
    val src = source(1000)
    val bmp = src.toComposeImageBitmap()
    val scene = ImageComposeScene(px, px, Density(scale)) {
      Image(bmp, null, Modifier.size(sizeDp.dp), contentScale = ContentScale.Crop, filterQuality = filter)
    }
    val out = scene.render().toComposeImageBitmap()
    scene.close()
    val got = IntArray(px * px).also { out.readPixels(it, 0, 0, px, px) }
    // Ideal: box average of the 1000x1000 source over each destination pixel.
    val ref = Bitmap().apply { allocPixels(ImageInfo.makeN32(px, px, ColorAlphaType.PREMUL)) }
    val c = Canvas(ref)
    c.drawImageRect(src, org.jetbrains.skia.Rect.makeWH(1000f, 1000f), org.jetbrains.skia.Rect.makeWH(px.toFloat(), px.toFloat()),
      org.jetbrains.skia.SamplingMode.MITCHELL, null, true)
    val want = ref.readPixels(ImageInfo.makeN32(px, px, ColorAlphaType.PREMUL))!!
    var sum = 0L
    for (i in 0 until px * px) {
      val g = (got[i] and 0xFF); val w = want[i * 4].toInt() and 0xFF
      sum += abs(g - w)
    }
    return sum.toDouble() / (px * px)
  }

  @Test fun highIsNeverWorseThanLowAcrossScales() {
    val rows = ArrayList<String>()
    // Cover sizes in the app: 48 dp row/transport, 192 dp card, 384 dp player. Scales 1.0/1.25/1.5 at 1080p,
    // and the same at 4K, where dp are twice as many pixels at the same percentage.
    for ((res, mult) in listOf("1920x1080" to 1f, "3840x2160" to 2f)) for (pct in listOf(1f, 1.25f, 1.5f)) for (dp in listOf(48, 192, 384)) {
      val s = pct * mult
      val low = error(FilterQuality.Low, s, dp); val high = error(FilterQuality.High, s, dp)
      rows += "%s %3d%% %3d dp  low %.2f  high %.2f".format(res, (pct * 100).toInt(), dp, low, high)
      assertTrue("High worse than Low: ${rows.last()}", high <= low + 0.5)
    }
    println(rows.joinToString("\n"))
    java.io.File("build/sharpness.txt").writeText(rows.joinToString("\n"))
  }
}
