package com.example.windows.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.example.ui.theme.TarkasTheme
import com.example.windows.biblioteca.contrastRatio
import com.example.windows.shell.ShellBackdrop
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The text of the corner pill over the worst silk pixel it can meet (the brightest dark silk, the darkest light silk, plus the
 * white / black cover), at the fill each glass style really draws: the surface at the shell's glass alpha times the style's scrim
 * ([scrimShare]). Writes the numbers to build/glass-render/pill-worst-silk.txt; the artist line and the times are `onSurfaceVariant`.
 */
@OptIn(ExperimentalTestApi::class)
class PillWorstSilkContrastTest {
  private fun srgb(lin: Double): Double = if (lin <= 0.0031308) 12.92 * lin else 1.055 * lin.pow(1 / 2.4) - 0.055

  private fun measure(): Pair<List<String>, List<String>> {
    val lines = ArrayList<String>(); val failures = ArrayList<String>()
    for (mode in listOf("dark", "light")) runDesktopComposeUiTest(200, 100) {
      setContent {
        TarkasTheme(themeMode = mode) {
          val c = MaterialTheme.colorScheme
          val dark = c.background.luminance() < 0.5f
          val silkL = if (dark) ShellBackdrop.MARBLE_MAX_L_DARK.toDouble() else ShellBackdrop.MARBLE_MIN_L_LIGHT.toDouble()
          val g = srgb(silkL).toFloat()
          val share = ShellBackdrop.coverShare(c.background)
          val mixed = g * (1 - share) + (if (dark) 1f else 0f) * share
          val under = Color(mixed, mixed, mixed)
          for ((name, scrim) in listOf("regular" to GlassSpec.Regular.scrim, "clear" to GlassSpec.Clear.scrim, "none" to 1f)) {
            val fill = c.surface.copy(alpha = ShellBackdrop.GLASS * scrim).compositeOver(under)
            val title = contrastRatio(c.onSurface, fill)
            val old = contrastRatio(c.onSurfaceVariant, fill)
            val second = contrastRatio(PillText.secondary(c).compositeOver(fill), fill)
            val primary = contrastRatio(c.primary, fill)
            lines += "$name $mode (alpha %.3f): title %.2f, secondary %.2f (onSurfaceVariant was %.2f), primary %.2f".format(ShellBackdrop.GLASS * scrim, title, second, old, primary)
            if (second < 4.5 || title < 4.5) failures += lines.last()
          }
        }
      }
      waitForIdle()
    }
    return lines to failures
  }

  @Test fun theSecondaryTextOfThePillOverTheWorstSilk() {
    val (lines, failures) = measure()
    File("build/glass-render").apply { mkdirs() }.let { File(it, "pill-worst-silk.txt").writeText(lines.joinToString(System.lineSeparator())) }
    lines.forEach { println("PILL-WORST-SILK $it") }
    assertTrue("AA 4.5 over the worst silk: $failures", failures.isEmpty())
  }
}
