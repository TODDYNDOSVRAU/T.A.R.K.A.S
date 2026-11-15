package com.example.ui.screens.player

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The T.A.L. grid as the player uses it (ui.md section 6): one size chain (each step 2x or 0.5x), text of
 * 12 / 18 / 24 sp and radii of 6 / 12 / 24. Kept here until the shared grid object in ui/theme replaces it.
 */
internal object PlayerGrid {
  val g6 = 6.dp
  val g12 = 12.dp
  val g24 = 24.dp
  val g48 = 48.dp
  val g96 = 96.dp

  val text12 = 12.sp
  val text18 = 18.sp
  val text24 = 24.sp

  val radius12 = 12.dp
}
