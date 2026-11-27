package com.example.windows.theme

import androidx.compose.material3.ColorScheme
import com.example.ui.theme.TarkasAmoledColorScheme
import com.example.ui.theme.TarkasDarkColorScheme
import com.example.ui.theme.TarkasLightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Every surface slot of the three schemes is a T.A.L. neutral. A slot left unset falls back to Material's violet
 * baseline (`#E6E0E9` light, `#36343B` dark): that is the lilac text field and segmented chip in Ajustes.
 */
class ThemeSlotsTest {
  private fun neutral(c: Color) = abs(c.red - c.green) <= 2f / 255 && abs(c.green - c.blue) <= 2f / 255

  private fun surfaces(s: ColorScheme) = mapOf(
    "background" to s.background, "surface" to s.surface, "surfaceVariant" to s.surfaceVariant,
    "surfaceTint" to s.surfaceTint, "surfaceBright" to s.surfaceBright, "surfaceDim" to s.surfaceDim,
    "surfaceContainer" to s.surfaceContainer, "surfaceContainerHigh" to s.surfaceContainerHigh,
    "surfaceContainerHighest" to s.surfaceContainerHighest, "surfaceContainerLow" to s.surfaceContainerLow,
    "surfaceContainerLowest" to s.surfaceContainerLowest, "inverseSurface" to s.inverseSurface,
    "inverseOnSurface" to s.inverseOnSurface, "secondaryContainer" to s.secondaryContainer,
    "outline" to s.outline, "outlineVariant" to s.outlineVariant, "scrim" to s.scrim,
    "onSurface" to s.onSurface, "onSurfaceVariant" to s.onSurfaceVariant,
  )

  @Test fun everySurfaceSlotIsNeutralInEveryScheme() {
    for ((name, scheme) in listOf("dark" to TarkasDarkColorScheme, "amoled" to TarkasAmoledColorScheme, "light" to TarkasLightColorScheme)) {
      val bad = surfaces(scheme).filterValues { !neutral(it) }
      assertTrue("$name: tinted slots $bad", bad.isEmpty())
    }
  }

  @Test fun containersStepInOrder() {
    // Dark: lowest is the darkest, highest the lightest. Light: the other way round.
    fun lum(c: Color) = c.red * 0.2126f + c.green * 0.7152f + c.blue * 0.0722f
    for ((name, s) in listOf("dark" to TarkasDarkColorScheme, "amoled" to TarkasAmoledColorScheme)) {
      val steps = listOf(s.surfaceContainerLowest, s.surfaceContainerLow, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceContainerHighest).map(::lum)
      assertTrue("$name $steps", steps.zipWithNext().all { (a, b) -> a <= b })
    }
    val l = TarkasLightColorScheme
    val steps = listOf(l.surfaceContainerLowest, l.surfaceContainerLow, l.surfaceContainer, l.surfaceContainerHigh, l.surfaceContainerHighest).map(::lum)
    assertTrue("light $steps", steps.zipWithNext().all { (a, b) -> a >= b })
  }
}
