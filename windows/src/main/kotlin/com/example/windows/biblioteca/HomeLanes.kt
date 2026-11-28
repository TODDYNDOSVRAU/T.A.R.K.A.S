package com.example.windows.biblioteca

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Grid
import com.example.windows.shell.CornerInsets

/**
 * Where the Início page sits around the player's corner (bottom left: the 384 dp mini-player and, only on this page,
 * the floating art card above it; the corner takes [CornerInsets.lane] dp from the left edge).
 *
 * One left edge for the whole page: the hero, every carousel, the catalogues and the about card all start at [start]
 * (the 24 dp margin; the corner's lane when it takes the left side; more when the window is wide enough to centre the
 * column). Two edges in one column read as a mistake, so no section gets its own indent.
 * [end] is the page's right padding, mirroring [start] when the column is centred.
 */
internal data class HomeLanes(val start: Dp, val end: Dp)

internal object HomeLayout {
  /** The widest the column grows: 384 x 3. */
  val ColumnMax: Dp = Grid.u384 * 3

  /** Under this width (a breakpoint, not a size) the art card is not drawn, only the mini-player, so nothing steps aside. */
  val CardMinWidth: Dp = 1024.dp

  /**
   * The lanes for a page [page] dp wide with the corner [corner]:
   * - wide (the column plus a lane on each side fits): the column is centred and the corner lives in the free margin;
   * - medium: the whole column starts after the lane, so the corner's card has its own gutter and nothing is hidden;
   * - narrow, or no card on this page: nothing steps aside.
   */
  fun lanes(page: Dp, corner: CornerInsets, margin: Dp = Grid.margin, end: Dp = Grid.margin): HomeLanes {
    if (!corner.card || corner.lane <= 0.dp || page < CardMinWidth) return HomeLanes(margin, end)
    if (page >= ColumnMax + corner.lane * 2) {
      val side = (page - ColumnMax) / 2
      return HomeLanes(side, side)
    }
    return HomeLanes(corner.lane, end)
  }
}
