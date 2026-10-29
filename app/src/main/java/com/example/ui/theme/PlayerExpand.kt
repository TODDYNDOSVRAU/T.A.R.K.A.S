package com.example.ui.theme

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp

/**
 * Where the full player grows from and shrinks back to. Elements that open the player (the mini
 * player, a song card or row) record their bounds when pressed; the player then expands from that
 * shape to fill the screen, and collapses into the mini player when it closes.
 */
object PlayerOrigin {
  private var pressed: Rect? = null
  private var pressedAt = 0L

  /** Bounds of the mini player, the shape the player collapses back into. */
  var mini: Rect? = null

  /**
   * The colour at the top of the full player, written by the player as it reads the artwork. The
   * growing container is filled with it so the opening has no seam; null until a player has shown.
   */
  var tint: Color? by mutableStateOf(null)

  internal fun press(bounds: Rect?) {
    pressed = bounds
    pressedAt = SystemClock.uptimeMillis()
  }

  /** The element pressed just before the player opened, if the press was recent. */
  internal fun takeOpening(): Rect? {
    val recent = SystemClock.uptimeMillis() - pressedAt < 1500
    return (if (recent) pressed else null).also { pressed = null } ?: mini
  }
}

/**
 * Marks an element that opens the player: when it is pressed, its bounds become the shape the
 * player expands from. It only observes the press, so clicks and scrolling work as before.
 */
fun Modifier.opensPlayer(): Modifier = composed {
  val bounds = remember { arrayOfNulls<Rect>(1) }
  this
    .onGloballyPositioned { bounds[0] = it.boundsInWindow() }
    .pointerInput(Unit) {
      awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        PlayerOrigin.press(bounds[0])
      }
    }
}

/**
 * A spatial spring (damping 0.88): it has the momentum of a real surface, and its overshoot stays
 * below 0.5%, which is under a pixel at the corners, so it never visibly bounces.
 */
private val ExpandSpring = spring<Float>(
  dampingRatio = Motion.ExpandDamping,
  stiffness = Motion.ExpandStiffness,
  visibilityThreshold = 0.001f
)

/**
 * Container transform for the full player. The page is clipped to a rounded rectangle that grows
 * from the pressed element's bounds to the whole screen, filled with the player's tint (or
 * [containerColor] before any player has shown) while the
 * player fades and settles in; on close it shrinks into the mini player. Driven by the screen
 * transition itself, so AnimatedContent keeps the page until the shape has landed.
 */
@Composable
fun Modifier.expandFromOrigin(
  scope: AnimatedVisibilityScope,
  containerColor: Color,
  reduced: Boolean
): Modifier {
  if (reduced) return this
  val origin = remember { PlayerOrigin.takeOpening() }
  val progress = scope.transition.animateFloat(transitionSpec = { ExpandSpring }, label = "playerExpand") {
    if (it == EnterExitState.Visible) 1f else 0f
  }
  val closing = scope.transition.targetState == EnterExitState.PostExit
  val self = remember { arrayOf(Offset.Zero) }
  val cornerStart = 22.dp

  return this
    .onGloballyPositioned { self[0] = it.positionInWindow() }
    .graphicsLayer {
      // Clamped, so the spring's sub-pixel overshoot never grows the shape past the screen.
      val p = progress.value.coerceIn(0f, 1f)
      val full = Rect(Offset.Zero, size)
      val from = ((if (closing) PlayerOrigin.mini ?: origin else origin)?.translate(-self[0]))
        ?: Rect(Offset(size.width * 0.5f, size.height), Offset(size.width * 0.5f, size.height))
      val r = lerp(from, full, p)
      val corner = (cornerStart.toPx() * (1f - p)).coerceAtLeast(0f)
      shape = GenericShape { _, _ -> addRoundRect(RoundRect(r, CornerRadius(corner))) }
      clip = true
    }
    .drawBehind { drawRect(PlayerOrigin.tint ?: containerColor) }
    .graphicsLayer {
      // The page itself fades in once the shape has grown a little, and settles from slightly
      // smaller, anchored where it came from.
      val p = progress.value.coerceIn(0f, 1f)
      alpha = ((p - 0.12f) / 0.45f).coerceIn(0f, 1f)
      val s = 0.92f + 0.08f * p
      scaleX = s
      scaleY = s
      val from = (if (closing) PlayerOrigin.mini ?: origin else origin)
      if (from != null && size.width > 0f && size.height > 0f) {
        val c = from.center - self[0]
        transformOrigin = TransformOrigin((c.x / size.width).coerceIn(0f, 1f), (c.y / size.height).coerceIn(0f, 1f))
      }
    }
}
