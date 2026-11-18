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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import com.example.ui.theme.Grid
import kotlinx.coroutines.flow.MutableStateFlow
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
    Crossfade(src.art, animationSpec = tween(300)) { art ->
      if (art != null) Image(art, null, Modifier.fillMaxSize().blur(Grid.u96), contentScale = ContentScale.Crop)
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
