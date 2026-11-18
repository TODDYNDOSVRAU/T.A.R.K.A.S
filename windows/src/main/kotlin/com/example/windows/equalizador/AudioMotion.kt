package com.example.windows.equalizador

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** The motion tokens of the Áudio tab (same springs as the app's `Motion`: gel 0.62/348, exit critical). */
object AudioMotion {
  const val GelDamping = 0.62f
  const val GelStiffness = 348f
  const val DefaultSpatialStiffness = 380f

  fun <T> gel(visibilityThreshold: T? = null) = spring(GelDamping, GelStiffness, visibilityThreshold)
  fun <T> gelExit() = spring<T>(dampingRatio = 1f, stiffness = GelStiffness)
  fun <T> criticalSpatial(stiffness: Float = DefaultSpatialStiffness) = spring<T>(dampingRatio = 1f, stiffness = stiffness)
}

/** True when motion should be skipped; provided by the panel from `TarkasState.reduceMotion`. */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean = LocalReducedMotion.current

/** Space the floating bars take at the bottom; the desktop has none, so it stays 0. */
val LocalBarInset = compositionLocalOf { 0.dp }

/** A page opened inside the tab: pushes in on the gel spring, leaves critically damped. */
fun subPageTransition(forward: Boolean, reduced: Boolean): ContentTransform {
  if (reduced) return EnterTransition.None togetherWith ExitTransition.None
  val dir = if (forward) 1 else -1
  return (slideInHorizontally(AudioMotion.gel()) { w -> dir * w / 3 } + fadeIn(tween(200, delayMillis = 30))) togetherWith
    (slideOutHorizontally(AudioMotion.gelExit()) { w -> -dir * w / 8 } + fadeOut(tween(150)))
}

/**
 * The effect chain the player runs on decoded float frames (interleaved). The audio thread calls
 * [configure] on a new stream, [process] per block (in place, same frame count) and [reset] on seek.
 */
interface EffectChain {
  fun configure(rate: Int, channels: Int)
  fun reset()
  fun process(floats: FloatArray, frames: Int)
}
