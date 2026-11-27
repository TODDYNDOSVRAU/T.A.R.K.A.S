package com.example.windows.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.Grid
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs
import kotlin.math.max

/** What a [TiltCard] hands its content: a layer of depth that moves against the lean, for parallax. */
interface TiltScope {
  /** Shifts a layer by `ry·depth·1.2` and `-rx·depth·1.2` density-independent pixels (D4 depth layers); 0 is the card's plane. */
  fun Modifier.depth(depth: Float): Modifier
}

/**
 * A surface that leans toward the pointer with the spring of the queue stack (D4/D5): `k 150 / c 12.5`, 8° on X and 11° on Y
 * at the edge, or the smaller pair a contained use passes ([maxRx], [maxRy]; a grid cover uses 4° and 6°).
 *
 * - Drawn only by `graphicsLayer` (`rotationX/Y`, `cameraDistance = 16·density`); frames come from `withFrameNanos`
 *   and only while a spring moves, none once both are at rest.
 * - The pointer is watched from an outer box that does not rotate and never consumes, so a click, a double click or a
 *   drag inside [content] works as without the card.
 * - [reduced] fixes the pose at 0 and draws no reflection or shadow shift.
 * - With [keyboard], the card takes focus: the arrows lean it and Esc brings it back to neutral.
 * - The shadow falls against the lean and a faint reflection follows the perspective, never a strong glare.
 */
@Composable
fun TiltCard(
  modifier: Modifier = Modifier,
  reduced: Boolean = false,
  maxRx: Float = StackTuning.MaxRx,
  maxRy: Float = StackTuning.MaxRy,
  shape: Shape = Grid.r24,
  shadow: Boolean = true,
  keyboard: Boolean = false,
  engine: TiltEngine = remember(maxRx, maxRy) { TiltEngine(maxRx, maxRy) },
  content: @Composable TiltScope.() -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  val tick = remember { mutableIntStateOf(0) }
  val wake = remember { Channel<Unit>(Channel.CONFLATED) }
  var focused by remember { mutableStateOf(false) }
  engine.reduced = reduced

  LaunchedEffect(engine) {
    while (true) {
      wake.receive()
      var last = withFrameNanos { it }
      while (true) {
        val now = withFrameNanos { it }
        val rest = engine.frame((now - last) / 1_000_000_000f)
        last = now
        tick.intValue++
        if (rest) break
      }
    }
  }

  val scope = remember(engine) {
    object : TiltScope {
      override fun Modifier.depth(depth: Float): Modifier = graphicsLayer {
        tick.intValue
        translationX = engine.ry.x * depth * 1.2f * density
        translationY = -engine.rx.x * depth * 1.2f * density
      }
    }
  }

  val keys: Modifier = if (keyboard) {
    Modifier
      .onFocusChanged { focused = it.isFocused; if (!it.isFocused) { engine.leave(); wake.trySend(Unit) } }
      .focusable()
      .onKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) false
        else when (e.key) {
          Key.DirectionLeft -> { engine.lean(-1f, 0f); wake.trySend(Unit); true }
          Key.DirectionRight -> { engine.lean(1f, 0f); wake.trySend(Unit); true }
          Key.DirectionUp -> { engine.lean(0f, -1f); wake.trySend(Unit); true }
          Key.DirectionDown -> { engine.lean(0f, 1f); wake.trySend(Unit); true }
          Key.Escape -> { engine.leave(); wake.trySend(Unit); true }
          else -> false
        }
      }
  } else Modifier

  Box(
    modifier
      .then(keys)
      .pointerInput(engine) {
        awaitPointerEventScope {
          while (true) {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull() ?: continue
            when (ev.type) {
              PointerEventType.Enter, PointerEventType.Move -> {
                engine.aim(ch.position.x, ch.position.y, size.width.toFloat(), size.height.toFloat())
                wake.trySend(Unit)
              }
              PointerEventType.Exit -> { engine.leave(); wake.trySend(Unit) }
              else -> {}
            }
          }
        }
      }
      .testTag("tilt_card")
      .then(if (shadow) Modifier.drawBehind { tick.intValue; drawContraryShadow(engine, Grid.u12.toPx(), Grid.u6.toPx(), Grid.u24.toPx()) } else Modifier),
  ) {
    Box(
      Modifier.fillMaxSize()
        .graphicsLayer {
          tick.intValue
          rotationX = engine.rx.x
          rotationY = engine.ry.x
          cameraDistance = 16f * density
        }
        .clip(shape)
        .background(cs.surfaceContainer)
        .drawWithContent {
          drawContent()
          tick.intValue
          val m = max(abs(engine.rx.x) / max(engine.maxRx, 0.001f), abs(engine.ry.x) / max(engine.maxRy, 0.001f)).coerceIn(0f, 1f)
          if (m > 0.01f) {
            val a = 0.12f * m
            val shift = engine.ry.x / max(engine.maxRy, 0.001f) * size.width * 0.5f
            drawRect(
              Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = a), Color.White.copy(alpha = 0f)),
                start = Offset(size.width * 0.15f + shift, 0f),
                end = Offset(size.width * 0.85f + shift, size.height),
              ),
            )
          }
        }
        .border(Grid.hairline, if (focused) cs.primary else cs.outlineVariant, shape),
    ) {
      scope.content()
    }
  }
}

/** Soft shadow made of three rounded rectangles, shifted against the lean; it never moves under reduced motion. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawContraryShadow(engine: TiltEngine, reach: Float, shift: Float, radius: Float) {
  val nx = engine.ry.x / max(engine.maxRy, 0.001f)
  val ny = -engine.rx.x / max(engine.maxRx, 0.001f)
  val ox = -nx * shift
  val oy = shift * 0.5f - ny * shift
  for (i in 0 until 3) {
    val grow = reach * (i + 1) / 3f
    drawRoundRect(
      Color.Black.copy(alpha = 0.10f - i * 0.025f),
      topLeft = Offset(ox - grow * 0.5f, oy - grow * 0.5f + grow * 0.35f),
      size = androidx.compose.ui.geometry.Size(size.width + grow, size.height + grow),
      cornerRadius = CornerRadius(radius + grow * 0.5f),
    )
  }
}
