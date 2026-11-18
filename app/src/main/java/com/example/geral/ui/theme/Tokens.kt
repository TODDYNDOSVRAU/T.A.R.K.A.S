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
  /** The player's transport buttons: touch target and glyph. */
  val control = 72.dp
  val controlGlyph = 44.dp
  /** The big play button and its glyph. */
  val playButton = 88.dp
  val playGlyph = 64.dp
  /** The grab handle at the top of the player. */
  val handleWidth = 36.dp
  val handleHeight = 5.dp
  val hairline = 1.dp
}

/**
 * The T.A.L. grid (tal skill, references/ui.md section 6). One chain of sizes in 2x steps; every height,
 * width, gap and radius of a screen on the grid is one of these. Screens move here one at a time; the
 * older [Space], [Radius] and [Size] stay until the last one has moved.
 */
object Grid {
  val u6 = 6.dp
  val u12 = 12.dp
  val u24 = 24.dp
  /** The touch target, and the height of every row, button and pill. */
  val u48 = 48.dp
  val u96 = 96.dp
  val u192 = 192.dp
  val u384 = 384.dp
  /** The one side margin of a page. */
  val margin = u24
  /** From a surface's edge to its content. */
  val inset = u12
  /** Icons, inside a 48 dp target. */
  val icon = u24
  /** Hairlines are drawn over the surface and take no room in the layout. */
  val hairline = 1.dp
  val r6 = RoundedCornerShape(u6)
  val r12 = RoundedCornerShape(u12)
  val r24 = RoundedCornerShape(u24)
}
