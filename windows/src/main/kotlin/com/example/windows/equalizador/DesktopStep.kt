package com.example.windows.equalizador

import androidx.compose.foundation.focusable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable

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

/**
 * Desktop input for a fader or knob: the mouse wheel steps it (up turns it up), and with focus the
 * arrow keys do (Up/Right up, Down/Left down). Double-click reset is the controls' own double tap.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.desktopStep(
  enabled: Boolean, value: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit
): Modifier {
  val current by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  if (!enabled) return this
  return this
    .onPointerEvent(PointerEventType.Scroll) { e ->
      val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
      if (dy != 0f) {
        change(steppedValue(current, if (dy < 0f) 1 else -1, step, min, max))
        e.changes.forEach { it.consume() }
      }
    }
    .focusable()
    .onKeyEvent { k ->
      if (k.type != KeyEventType.KeyDown) return@onKeyEvent false
      val dir = when (k.key) { Key.DirectionUp, Key.DirectionRight -> 1; Key.DirectionDown, Key.DirectionLeft -> -1; else -> 0 }
      if (dir == 0) false else { change(steppedValue(current, dir, step, min, max)); true }
    }
}
