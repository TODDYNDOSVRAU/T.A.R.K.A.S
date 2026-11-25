package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Classes de largura: COMPACT < 600, MEDIUM 600-839, EXPANDED >= 840 dp. */
enum class WidthClass {
  COMPACT, MEDIUM, EXPANDED;

  companion object {
    fun of(widthDp: Float): WidthClass = when {
      widthDp < 600f -> COMPACT
      widthDp < 840f -> MEDIUM
      else -> EXPANDED
    }
    fun of(width: Dp): WidthClass = of(width.value)
  }
}

const val ReadingColumnMaxDp = 1536

/** Altura reservada pelo dock flutuante; telas somam `Grid.u12` a ela no fundo. */
val LocalBarInset = compositionLocalOf { 0.dp }

/** Card estrutural: squircle 18 dp, superfície, fio de 1 dp a 8%. */
@Composable
fun Modifier.studioCard(radius: Dp = 18.dp): Modifier {
  val colors = MaterialTheme.colorScheme
  val shape = SquircleShape(radius)
  return this.clip(shape)
    .background(colors.surfaceContainer)
    .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), shape)
}
