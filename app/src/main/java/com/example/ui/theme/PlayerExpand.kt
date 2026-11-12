package com.example.ui.theme

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
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

  /** Bounds of the cover inside the mini player, where the travelling cover starts and lands. */
  var miniCover: Rect? = null

  /**
   * The opening progress of the player on screen (0 closed, 1 open), the same value that grows the
   * container, so the cover, the page around it and the bars all follow one number. Null when the
   * player is not growing out of anything (reduced motion), which reads as fully open.
   */
  var progress: State<Float>? by mutableStateOf(null)
    internal set

  /**
   * Window y of the top of the shape the sheet grows from (or shrinks into). The page around the
   * cover rides this edge up as the sheet opens, instead of sitting still behind a growing window.
   */
  var sheetFromTop: Float = 0f
    internal set

  /** True while the player is closing, so the cover travels towards the mini player. */
  var closing: Boolean = false
    internal set

  /** The player's page, to measure its parts in the page's own space. */
  var pageCoords: androidx.compose.ui.layout.LayoutCoordinates? = null

  /** Window position of the page's top-left corner, to bring the mini player's rectangles into page space. */
  var pageOrigin: Offset = Offset.Zero

  /** Where each part of the player rests, in page space (written by MusicScreen). */
  val rest = HashMap<String, Rect>()

  /** The mini player's title, play and next (window space): where those parts of the player come from and go back to. */
  var miniTitle: Rect? = null
  var miniPlay: Rect? = null
  var miniNext: Rect? = null

  /** True when the player opens out of the mini player, so its parts come from the mini's parts. */
  var fromMini: Boolean = true
    internal set

  /** Where the cover starts as the player opens: the cover of what was tapped. */
  var openingCover: Rect? = null
    internal set

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
 * A jelly spring (Motion.ExpandDamping): it overshoots about 8%. The sheet is clamped to the screen
 * (below), so the overshoot shows only in the travelling cover, which swings a little past its place.
 */
private val ExpandSpring = tween<Float>(
  durationMillis = Motion.ExpandMillis,
  easing = Motion.PlayerEasing
)

/** Closing into the mini player: no overshoot, faster than the opening (see Motion.CollapseStiffness). */
private val CollapseSpring = tween<Float>(
  durationMillis = Motion.CollapseMillis,
  easing = Motion.PlayerEasing
)

/**
 * Container transform for the full player: the mini player (or the tapped element) expands to fill the
 * screen, and everything in the player grows with it, the cover landing on its place; on close it
 * shrinks back into the mini player. The container is filled with the player's tint (or
 * [containerColor] before any player has shown). Driven by the visibility transition of the player,
 * which keeps it composed until it has shrunk.
 */
@Composable
fun Modifier.expandFromOrigin(
  scope: AnimatedVisibilityScope,
  containerColor: Color,
  reduced: Boolean
): Modifier {
  if (reduced) return this
  val origin = remember { PlayerOrigin.takeOpening() }
  val progress = scope.transition.animateFloat(
    transitionSpec = { if (targetState == EnterExitState.Visible) ExpandSpring else CollapseSpring },
    label = "playerExpand"
  ) {
    if (it == EnterExitState.Visible) 1f else 0f
  }
  // Present only while the player is composed (opening, open, closing); the mini player reads it to
  // know it is arriving from the player, and falls back to its own entrance once it is gone.
  DisposableEffect(progress) {
    onDispose { if (PlayerOrigin.progress === progress) PlayerOrigin.progress = null }
  }
  val closing = scope.transition.targetState == EnterExitState.PostExit
  val self = remember { arrayOf(Offset.Zero) }
  val cornerStart = Radius.l
  SideEffect {
    PlayerOrigin.progress = progress
    PlayerOrigin.closing = closing
    PlayerOrigin.fromMini = origin == null || origin == PlayerOrigin.mini
    PlayerOrigin.openingCover = when {
      origin == null -> null
      origin == PlayerOrigin.mini && PlayerOrigin.miniCover != null -> PlayerOrigin.miniCover
      else -> PlayerTravel.coverIn(origin)
    }
  }

  return this
    .onGloballyPositioned {
      self[0] = it.positionInWindow()
      PlayerOrigin.pageOrigin = self[0]
    }
    // Outer layer: the container, a rounded rectangle that grows from the mini player (or the tapped
    // element) to the whole screen and shrinks back into it.
    .graphicsLayer {
      // Clamped, so the spring's sub-pixel overshoot never grows the shape past the screen.
      val p = progress.value.coerceIn(0f, 1f)
      val full = Rect(Offset.Zero, size)
      val from = ((if (closing) PlayerOrigin.mini ?: origin else origin)?.translate(-self[0]))
        ?: Rect(Offset(size.width * 0.5f, size.height), Offset(size.width * 0.5f, size.height))
      shape = SheetShape(lerp(from, full, p), (cornerStart.toPx() * (1f - p)).coerceAtLeast(0f))
      clip = true
      // Closing: the container dissolves into the mini player over its last stretch instead of being
      // removed the moment the spring ends.
      alpha = if (closing) {
        val t = (p / 0.18f).coerceIn(0f, 1f)
        t * t * (3f - 2f * t)
      } else 1f
    }
    .drawBehind { drawRect(PlayerOrigin.tint ?: containerColor) }
    // The parts of the page (cover, title, buttons) travel on their own between the mini player's
    // elements and their places (MusicScreen.playerPart), inside this container.
    .onGloballyPositioned { PlayerOrigin.pageCoords = it }
}

/**
 * The container's clip: a rounded rectangle outline, which the GPU clips directly. A path-based shape
 * (GenericShape) is rebuilt and clipped as a path on every frame of a full-screen layer.
 */
private data class SheetShape(val rect: Rect, val corner: Float) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    Outline.Rounded(RoundRect(rect, CornerRadius(corner)))
}

