package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Spacing on a 4 dp grid. Screens use these instead of numbers of their own, so margins and gaps line
 * up from one screen to the next.
 */
object Space {
  val xxs = 2.dp
  val xs = 4.dp
  val s = 8.dp
  val m = 12.dp
  val l = 16.dp
  /** The page's side margin. */
  val page = 20.dp
  val xl = 24.dp
  val xxl = 32.dp
  /** Between two sections of a page. */
  val section = 36.dp
}

/** Corner radii. Concentric shapes step down by the padding between them. */
object Radius {
  /** Small artwork in a row, small chips. */
  val xs = 6.dp
  /** Artwork in rows, text fields. */
  val s = 10.dp
  /** Grouped list sections, cards. */
  val m = 14.dp
  /** Large artwork, sheets. */
  val l = 20.dp
  /** Floating capsules. */
  val xl = 28.dp
}

object Shapes {
  val xs = RoundedCornerShape(Radius.xs)
  val s = RoundedCornerShape(Radius.s)
  val m = RoundedCornerShape(Radius.m)
  val l = RoundedCornerShape(Radius.l)
  val xl = RoundedCornerShape(Radius.xl)
  val pill = RoundedCornerShape(percent = 50)
}

/** Sizes that recur. */
object Size {
  /** The smallest touch target (Material and Apple agree within a few dp). */
  val touch = 48.dp
  /** A one-line row. */
  val row = 52.dp
  /** Artwork in a track row. */
  val rowArtwork = 48.dp
  val icon = 24.dp
  val iconSmall = 20.dp
}
