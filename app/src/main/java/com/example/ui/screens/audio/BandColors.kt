package com.example.ui.screens.audio

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * One colour per parametric band, so a band is recognised by colour across its card, its point and its
 * own curve on the graph, as in hardware-style equalizers. The first is the app accent. This is the one
 * place the interface leaves the T.A.L. palette, on purpose: eight hues are needed to tell bands apart.
 * Each set keeps at least 3:1 against its own background (non-text contrast, WCAG AA): the bright set is
 * for dark surfaces and the deep set for light ones.
 */
internal object BandColors {
  val ON_DARK: List<Color> = listOf(
    Color(0xFF7EE3EA), Color(0xFFF2A65A), Color(0xFFB794F6), Color(0xFF86E3A0),
    Color(0xFFF27C7C), Color(0xFFF2D45A), Color(0xFF7FA8F5), Color(0xFFE58AD0)
  )
  val ON_LIGHT: List<Color> = listOf(
    Color(0xFF0D6C74), Color(0xFFB35F0C), Color(0xFF6B3FC4), Color(0xFF1E7A3C),
    Color(0xFFB3322F), Color(0xFF7A5F00), Color(0xFF2B5BC4), Color(0xFFA32B8A)
  )

  fun of(index: Int, onDark: Boolean): Color {
    val set = if (onDark) ON_DARK else ON_LIGHT
    return set[Math.floorMod(index, set.size)]
  }
}

/** The colour of band [index] for the current theme. */
@Composable
internal fun bandColor(index: Int): Color =
  BandColors.of(index, onDark = MaterialTheme.colorScheme.background.luminance() < 0.5f)
