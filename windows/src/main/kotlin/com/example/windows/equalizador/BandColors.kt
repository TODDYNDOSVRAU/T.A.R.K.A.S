package com.example.windows.equalizador

import com.example.windows.res.Res
import com.example.windows.res.tr
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.TalBlack
import com.example.ui.theme.TalWhite
import com.example.ui.theme.TarkasIce
import com.example.ui.theme.TarkasTealDeep

/**
 * One tone per parametric band, from the identity's accent only: Ice on dark, Deep Teal on light, with the
 * intermediate tones made by opacity over the surface, as the identity asks. Neighbouring bands alternate
 * between a strong and a weaker tone so a band stands out from the ones beside it; what names a band is its
 * number and frequency, which every card and point also carry. Each set keeps at least 3:1 against its own
 * background (non-text contrast, WCAG AA), which is why the weakest tone is lower on dark than on light.
 */
internal object BandColors {
  val ALPHA_ON_DARK = floatArrayOf(1.00f, 0.55f, 0.85f, 0.62f, 0.93f, 0.58f, 0.76f, 0.68f)
  val ALPHA_ON_LIGHT = floatArrayOf(1.00f, 0.76f, 0.92f, 0.80f, 0.96f, 0.78f, 0.88f, 0.84f)

  val ON_DARK: List<Color> = tones(TarkasIce, TalBlack, onDark = true)
  val ON_LIGHT: List<Color> = tones(TarkasTealDeep, TalWhite, onDark = false)

  /** The band tones made from any [accent] over its [background]: the phone's colours when the user chose them. */
  fun tones(accent: Color, background: Color, onDark: Boolean): List<Color> =
    (if (onDark) ALPHA_ON_DARK else ALPHA_ON_LIGHT).map { accent.copy(alpha = it).compositeOver(background) }

  fun of(index: Int, onDark: Boolean): Color {
    val set = if (onDark) ON_DARK else ON_LIGHT
    return set[Math.floorMod(index, set.size)]
  }
}

/** The colour of band [index] for the current theme. */
@Composable
internal fun bandColor(index: Int): Color {
  val colors = MaterialTheme.colorScheme
  val onDark = colors.background.luminance() < 0.5f
  val alphas = if (onDark) BandColors.ALPHA_ON_DARK else BandColors.ALPHA_ON_LIGHT
  return colors.primary.copy(alpha = alphas[Math.floorMod(index, alphas.size)]).compositeOver(colors.background)
}
