package com.example.windows.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import com.example.data.model.Track

/** What a track-bound block shows: the track and its cover. A new cover for the same track updates in place. */
data class Shown(val track: Track?, val art: ImageBitmap?)

/** Timings of the track-change motion (150-300 ms, ease-out). */
object SwapMotion {
  const val InMs = 220
  const val OutMs = 150
  /** The block moves a quarter of its height, a subtle shift. */
  const val Shift = 4
}

private val EaseOut = CubicBezierEasing(0f, 0f, 0.2f, 1f)

private class DirHolder { var id: String? = null; var idx = -1; var dir = 1 }

/** +1 when the track changed to a later one in the list, -1 to an earlier one; kept until the next change. */
@Composable
fun trackDirection(ui: PlayerUi, id: String?): Int {
  val h = remember { DirHolder() }
  if (h.id != id) {
    val idx = ui.list.indexOfFirst { it.id == id }
    h.dir = TrackMotion.direction(h.idx, idx, ui.list.size).let { if (it == 0) h.dir else it }
    h.id = id; h.idx = idx
  }
  return h.dir
}

/**
 * Swaps [shown] with a vertical slide and fade: the next track rises in from below, the previous one from above;
 * the old one leaves the opposite way. The size is fixed by the caller, so nothing jumps.
 */
@Composable
fun TrackSwap(shown: Shown, dir: Int, modifier: Modifier = Modifier, content: @Composable (Shown) -> Unit) {
  AnimatedContent(
    targetState = shown,
    modifier = modifier,
    contentKey = { it.track?.id },
    transitionSpec = {
      (slideInVertically(tween(SwapMotion.InMs, easing = EaseOut)) { dir * it / SwapMotion.Shift } + fadeIn(tween(SwapMotion.InMs, easing = EaseOut)))
        .togetherWith(slideOutVertically(tween(SwapMotion.OutMs, easing = EaseOut)) { -dir * it / SwapMotion.Shift } + fadeOut(tween(SwapMotion.OutMs)))
        .using(SizeTransform(clip = true) { _, _ -> snap() })
    },
    label = "trackSwap",
  ) { s -> content(s) }
}
