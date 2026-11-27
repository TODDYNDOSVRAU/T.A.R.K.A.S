package com.example.windows.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Grid
import com.example.ui.theme.SquircleShape
import com.example.windows.shell.ShellBackdrop

/**
 * A page card over the silk: the surface at [alpha] (the shell's glass by default), the same squircle and hairline as
 * `studioCard()`. The Início draws over the silk; the Biblioteca's three cards let it through the same way instead of
 * hiding it behind opaque panels. [GlassCards.contrast] is the worst case the text on it can meet.
 */
@Composable
fun Modifier.glassCard(radius: Dp = 18.dp, alpha: Float = GlassCards.READING_ALPHA): Modifier {
  val colors = MaterialTheme.colorScheme
  val shape = SquircleShape(radius)
  return this.clip(shape)
    .background(colors.surface.copy(alpha = alpha))
    .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), shape)
}

internal object GlassCards {
  /** The shell's glass alpha: the same translucency as the corner player and the side pane. */
  const val ALPHA = ShellBackdrop.GLASS

  /**
   * The alpha of cards that hold running text and tables. At the shell glass (0.55) the secondary text in the dark theme
   * reads 4.38:1 on the brightest silk pixel, under AA; 0.72 keeps the silk visible and every text colour at 4.5:1 or more
   * (proved by LibraryPageLayoutTest for both themes).
   */
  const val READING_ALPHA = 0.72f

  /**
   * Contrast of [fg] on a card of [surface] at [alpha] over the brightest (dark theme) or darkest (light theme) pixel
   * the backdrop can show, [under]. That pixel is the worst case: every other backdrop pixel reads better.
   */
  fun contrast(fg: Color, surface: Color, under: Color, alpha: Float = READING_ALPHA): Double =
    contrastRatio(fg, surface.copy(alpha = alpha).compositeOver(under))
}
