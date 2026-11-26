package com.example.windows.equalizador

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import com.example.ui.theme.Motion
import kotlinx.coroutines.launch

/** Shift turns a step into a fine step: a fifth of it. */
internal const val FINE_DIVISOR = 5f

/** One step of a fader or knob: [direction] is +1 (up) or -1 (down); the result stays inside [min]..[max]. */
internal fun steppedValue(current: Float, direction: Int, step: Float, min: Float, max: Float): Float {
  val next = current + direction * step
  // Snap to the step grid so repeated wheel clicks do not accumulate float error.
  val snapped = Math.round((next - min) / step) * step + min
  return snapped.coerceIn(min, max)
}

/** The step a [steps]-stop control walks between [min] and [max]; [fallback] when it has no stops. */
internal fun stepSize(min: Float, max: Float, steps: Int, fallback: Float): Float =
  if (steps > 0) (max - min) / (steps + 1) else fallback

/** True when a step in [direction] has nowhere to go: the value already sits on that end. */
internal fun atLimit(current: Float, direction: Int, min: Float, max: Float): Boolean =
  if (direction > 0) current >= max else current <= min

/**
 * Desktop input for a fader or knob: the mouse wheel steps it (up turns it up, Shift = fine), and with focus the
 * arrow keys do (Up/Right up, Down/Left down, Shift = fine). A step past either end gives with
 * [Motion.rubberBand] and springs back with [Motion.gel]. Double-click reset is the double tap of the control itself.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.desktopStep(
  enabled: Boolean, value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit
): Modifier {
  val current by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  val scope = rememberCoroutineScope()
  val nudge = remember { Animatable(0f) }
  if (!enabled) return this
  fun apply(direction: Int, fine: Boolean) {
    if (atLimit(current, direction, min, max)) {
      scope.launch {
        nudge.snapTo(Motion.rubberBand(NUDGE_PX, NUDGE_PX * 4f) * -direction)
        nudge.animateTo(0f, Motion.gel())
      }
    } else {
      change(steppedValue(current, direction, if (fine) step / FINE_DIVISOR else step, min, max))
    }
  }
  return this
    .graphicsLayer { translationY = nudge.value }
    .onPointerEvent(PointerEventType.Scroll) { e ->
      val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
      if (dy != 0f) {
        apply(if (dy < 0f) 1 else -1, e.keyboardModifiers.isShiftPressed)
        e.changes.forEach { it.consume() }
      }
    }
    .focusable()
    .onKeyEvent { k ->
      if (k.type != KeyEventType.KeyDown) return@onKeyEvent false
      val dir = when (k.key) { Key.DirectionUp, Key.DirectionRight -> 1; Key.DirectionDown, Key.DirectionLeft -> -1; else -> 0 }
      if (dir == 0) false else { apply(dir, k.isShiftPressed); true }
    }
}

private const val NUDGE_PX = 12f
