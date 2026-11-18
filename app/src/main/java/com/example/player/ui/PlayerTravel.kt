package com.example.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * The numbers behind the cover that travels from the mini player (or the song that was tapped) to
 * its place in the full player, and back. Pure functions of the opening progress `p` (0 closed, 1
 * open), so the same value that grows the player drives the cover, the fade of everything around it
 * and the way the tab bar leaves, and they can be tested without a screen.
 */
object PlayerTravel {
  /** Cover scale while paused, while the progress bar is being dragged, and while playing. */
  const val PausedScale = 0.86f
  const val ScrubbingScale = 0.95f

  /** The cover steps back when paused, comes forward a little under the finger on the bar, fills when playing. */
  fun coverScale(isPlaying: Boolean, hasTrack: Boolean, scrubbing: Boolean): Float = when {
    !hasTrack -> 1f
    scrubbing -> ScrubbingScale
    isPlaying -> 1f
    else -> PausedScale
  }

  /**
   * How much of the page around the cover is visible at progress [p]: nothing for the first 15%, then a
   * smoothstep to fully visible at 70%, so it fades in as one calm movement while it rides the sheet up,
   * and is complete before the sheet lands.
   */
  fun reveal(p: Float): Float {
    val t = ((p - 0.15f) / 0.55f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }

  /** How much of the element the player grows out of is still visible: gone almost at once. */
  fun originFade(p: Float): Float = kotlin.math.exp(-300f * p.coerceAtLeast(0f))

  /** How far the tab bar has left: 95% of the way out with 15% of the opening done. */
  fun tabBarOut(p: Float): Float = 1f - kotlin.math.exp(-20f * p.coerceAtLeast(0f))

  /** Closing progress below which the mini player starts to appear. */
  const val MiniArrivalStart = 0.10f

  /**
   * How visible the mini player is while the player closes into it, from the same progress [p] that
   * shrinks the player: 0 until the shrink is within 10% of the mini player, then rising smoothly to
   * exactly 1 when the shrink ends (p = 0). It never reaches 1 before the shrink is done, and has no
   * clock of its own, so the two are one movement.
   */
  fun miniIn(p: Float): Float {
    val t = ((MiniArrivalStart - p) / MiniArrivalStart).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }

  /** The rectangle of the travelling cover at [p], linear between where it starts and where it rests. */
  fun rectAt(from: Rect, to: Rect, p: Float): Rect {
    val q = p.coerceIn(0f, 1f)
    return Rect(
      Offset(from.left + (to.left - from.left) * q, from.top + (to.top - from.top) * q),
      androidx.compose.ui.geometry.Size(
        from.width + (to.width - from.width) * q,
        from.height + (to.height - from.height) * q
      )
    )
  }

  /** Scale to apply to a cover laid out at [to]'s size so that it shows at [rectAt]'s size. */
  fun scaleAt(from: Rect, to: Rect, p: Float): Float {
    if (to.width <= 0f) return 1f
    return rectAt(from, to, p).width / to.width
  }

  /**
   * Corner radius, in the cover's own pixels, that shows as [fromRadius] at the start and
   * [toRadius] at rest. The cover is scaled as a whole, so the radius is divided by the scale to
   * look the same size to the eye all the way.
   */
  fun cornerAt(fromRadius: Float, toRadius: Float, p: Float, scale: Float): Float {
    val q = p.coerceIn(0f, 1f)
    val shown = fromRadius + (toRadius - fromRadius) * q
    return if (scale > 0f) shown / scale else shown
  }

  /**
   * Where the cover starts for an element that opens the player. A row or a bar (wider than tall) has
   * its cover as a square at the leading edge, as tall as the element; a card has it as a square along
   * the top, as wide as the card.
   */
  fun coverIn(origin: Rect): Rect {
    val side = if (origin.width > origin.height * 1.5f) origin.height else minOf(origin.width, origin.height)
    return Rect(origin.topLeft, androidx.compose.ui.geometry.Size(side, side))
  }
}
