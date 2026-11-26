package com.example.windows.shell

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The current cover becomes the window background (owner, 2026-11-18, docs/WINDOWS-VISUAL-REF.md).
 * The player sets [set] when the track changes; the shell draws it blurred under a veil that keeps text contrast.
 */
object ShellBackdrop {
  class Source(val art: ImageBitmap?, val tint: Color?)
  private val _source = MutableStateFlow(Source(null, null))
  val source: StateFlow<Source> = _source.asStateFlow()
  /** [art]: the track's real cover, or null. [tint]: its dominant colour (or `Track.colorHex`), or null for none. */
  fun set(art: ImageBitmap?, tint: Color?) { _source.value = Source(art, tint) }
  fun clear() = set(null, null)

  /** Long side of the baked backdrop, in pixels. Scaled up with bilinear filtering it reads as one soft wash. */
  const val SOFT_PX = 96
  /** Blur sigma at [SOFT_PX]: 5% of the side, the same share a 96 dp blur had over a 1152 dp window (about 83 px of 1728). */
  const val SOFT_SIGMA = 4.8f

  /**
   * The cover downscaled to [SOFT_PX] and blurred once (Skia, CPU, under 1 ms). Called off the UI thread per cover.
   * Edges clamp, like `Modifier.blur`'s rectangle treatment, so the wash has no dark rim.
   */
  fun soften(art: ImageBitmap): ImageBitmap {
    val src = org.jetbrains.skia.Image.makeFromBitmap(art.asSkiaBitmap())
    val scale = SOFT_PX.toFloat() / maxOf(src.width, src.height)
    val w = maxOf(1, (src.width * scale).toInt()); val h = maxOf(1, (src.height * scale).toInt())
    val small = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
    small.canvas.drawImageRect(src, org.jetbrains.skia.Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
      org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()), org.jetbrains.skia.SamplingMode.LINEAR, null, true)
    val out = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
    val paint = org.jetbrains.skia.Paint().apply {
      imageFilter = org.jetbrains.skia.ImageFilter.makeBlur(SOFT_SIGMA, SOFT_SIGMA, org.jetbrains.skia.FilterTileMode.CLAMP)
    }
    out.canvas.drawImage(small.makeImageSnapshot(), 0f, 0f, paint)
    return out.makeImageSnapshot().toComposeImageBitmap()
  }

  /** Veil over the cover: the theme background at this alpha. Dark themes keep more of the cover than light ones. */
  fun veilAlpha(background: Color): Float = if (background.luminance() < 0.5f) VEIL_DARK else VEIL_LIGHT
  const val VEIL_DARK = 0.80f
  const val VEIL_LIGHT = 0.88f
  /** Alpha of the glass surfaces (sidebar, side pane, transport) when a backdrop is shown. */
  const val GLASS = 0.55f

  /**
   * Worst-case text contrast over the veil: [fg] on the veil blended over pure white (dark theme) or pure black
   * (light theme), the brightest/darkest a cover can be. Used by the test that keeps contrast ≥ 4.5:1.
   */
  fun worstContrast(fg: Color, background: Color): Double {
    val a = veilAlpha(background)
    val extreme = if (background.luminance() < 0.5f) Color.White else Color.Black
    fun mix(x: Float, y: Float) = x * a + y * (1 - a)
    val bg = Color(mix(background.red, extreme.red), mix(background.green, extreme.green), mix(background.blue, extreme.blue))
    val l1 = fg.luminance().toDouble(); val l2 = bg.luminance().toDouble()
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
  }
}

/** Blurred cover + tint + veil, filling its box. Nothing is drawn when no track has art or tint. */
@Composable
fun Backdrop(modifier: Modifier = Modifier) {
  val c = MaterialTheme.colorScheme
  val src by ShellBackdrop.source.collectAsState()
  val tint by animateColorAsState(src.tint ?: c.background, tween(300))
  Box(modifier.fillMaxSize().background(c.background)) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.6f), c.background))))
    // The blur is baked once per cover into a small bitmap (soften); drawing it is a plain scaled texture.
    // Before 2.1.6 a live blur(96.dp) over the whole window ran again on every repaint (owner report: laggy, heavy).
    val soft by produceState<ImageBitmap?>(null, src.art) {
      val art = src.art
      value = if (art == null) null else withContext(Dispatchers.Default) { ShellBackdrop.soften(art) }
    }
    Crossfade(soft, animationSpec = tween(300)) { art ->
      if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
    }
    Box(Modifier.fillMaxSize().background(c.background.copy(alpha = ShellBackdrop.veilAlpha(c.background))))
  }
}

/** Glass surface colour: translucent when a backdrop is shown, the plain surface otherwise. */
@Composable
fun glass(): Color {
  val c = MaterialTheme.colorScheme
  val src by ShellBackdrop.source.collectAsState()
  return if (src.art != null || src.tint != null) c.surface.copy(alpha = ShellBackdrop.GLASS) else c.surface
}
