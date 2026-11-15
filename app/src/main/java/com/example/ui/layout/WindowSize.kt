package com.example.ui.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * The window width classes, defined once (the Material breakpoints). Every screen that adapts to a large
 * window asks this class, so no screen picks its own number.
 */
enum class WidthClass {
  /** Under 600 dp: a phone held upright. */
  COMPACT,
  /** 600 to under 840 dp: a large phone or small tablet upright, or a phone on its side. */
  MEDIUM,
  /** 840 dp and over: a tablet, a foldable open, a desktop window. */
  EXPANDED;

  companion object {
    const val MEDIUM_MIN_DP = 600
    const val EXPANDED_MIN_DP = 840

    fun of(widthDp: Int): WidthClass = when {
      widthDp >= EXPANDED_MIN_DP -> EXPANDED
      widthDp >= MEDIUM_MIN_DP -> MEDIUM
      else -> COMPACT
    }
  }
}

/** The widest a column of text or rows reads well; wider windows centre it instead of stretching it. */
const val ReadingColumnMaxDp = 640

/** The width class of the current window. */
@Composable
fun rememberWidthClass(): WidthClass = WidthClass.of(LocalConfiguration.current.screenWidthDp)

/** Two panes (cover and controls side by side) when the window is wide, whatever its orientation. */
val WidthClass.twoPane: Boolean get() = this != WidthClass.COMPACT

/**
 * Limits a list or form to [ReadingColumnMaxDp] and centres it. On a phone the window is narrower than
 * the limit, so nothing changes.
 */
fun Modifier.readingColumn(): Modifier =
  this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ReadingColumnMaxDp.dp).fillMaxWidth()
