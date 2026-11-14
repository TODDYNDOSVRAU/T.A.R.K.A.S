package com.example.ui.screens.audio

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * One tone per parametric band, from the identity's accent only: Ice on dark, Deep Teal on light, with the
 * intermediate tones made by opacity over the surface, as the identity asks. Neighbouring bands alternate
 * between a strong and a weaker tone so a band stands out from the ones beside it; what names a band is its
 * number and frequency, which every card and point also carry. Each set keeps at least 3:1 against its own
 * background (non-text contrast, WCAG AA), which is why the weakest tone is lower on dark than on light.
 */
internal object BandColors {
  private val ICE = Color(0xFF7EE3EA)
  private val DEEP_TEAL = Color(0xFF0D6C74)
  private val BLACK = Color(0xFF080808)
  private val WHITE = Color(0xFFF2F2F0)

  private val ALPHA_ON_DARK = floatArrayOf(1.00f, 0.55f, 0.85f, 0.62f, 0.93f, 0.58f, 0.76f, 0.68f)
  private val ALPHA_ON_LIGHT = floatArrayOf(1.00f, 0.76f, 0.92f, 0.80f, 0.96f, 0.78f, 0.88f, 0.84f)

  val ON_DARK: List<Color> = ALPHA_ON_DARK.map { ICE.copy(alpha = it).compositeOver(BLACK) }
  val ON_LIGHT: List<Color> = ALPHA_ON_LIGHT.map { DEEP_TEAL.copy(alpha = it).compositeOver(WHITE) }

  fun of(index: Int, onDark: Boolean): Color {
    val set = if (onDark) ON_DARK else ON_LIGHT
    return set[Math.floorMod(index, set.size)]
  }
}

/** The colour of band [index] for the current theme. */
@Composable
internal fun bandColor(index: Int): Color =
  BandColors.of(index, onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f)
