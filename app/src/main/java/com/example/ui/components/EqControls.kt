package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.FilterType

private const val KNOB_SWEEP = 270f
private const val KNOB_START = 135f

/**
 * A rotary knob like a hardware mixer's: a dark disc with an indicator tick, and an arc of light
 * around it showing the value. [value] runs 0..1; drag up or right to turn it, double-tap to go back
 * to [defaultValue]. A [bipolar] knob draws its arc from the top (centre) instead of from the start.
 */
@Composable
fun EqKnob(
  value: Float,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  size: Dp = 96.dp,
  accent: Color = MaterialTheme.colorScheme.primary,
  bipolar: Boolean = false,
  defaultValue: Float = if (bipolar) 0.5f else 0f,
  enabled: Boolean = true,
  description: String = ""
) {
  val colors = MaterialTheme.colorScheme
  val current by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  var dragValue by remember { mutableFloatStateOf(value) }
  val disc = colors.surfaceVariant
  val rim = colors.onSurface.copy(alpha = 0.18f)
  val tick = colors.onSurface.copy(alpha = if (enabled) 0.85f else 0.3f)

  Box(
    modifier = modifier
      .size(size)
      .semantics { contentDescription = description }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onDoubleTap = { change(defaultValue) })
      }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectVerticalDragGestures(
          onDragStart = { dragValue = current },
          onVerticalDrag = { c, dy ->
            c.consume()
            // A full turn takes about two knob heights of travel.
            dragValue = (dragValue - dy / (this.size.height * 2.2f)).coerceIn(0f, 1f)
            change(dragValue)
          }
        )
      }
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = 4.dp.toPx()
      val arcPad = stroke * 1.6f
      val r = this.size.minDimension / 2f
      val discR = r - arcPad - stroke
      val v = value.coerceIn(0f, 1f)

      // Arc track and value arc with a soft glow
      val arcTopLeft = Offset(arcPad / 2f + stroke / 2f, arcPad / 2f + stroke / 2f)
      val arcSize = Size(this.size.width - arcPad - stroke, this.size.height - arcPad - stroke)
      drawArc(rim, KNOB_START, KNOB_SWEEP, false, arcTopLeft, arcSize, style = Stroke(stroke * 0.5f, cap = StrokeCap.Round))
      val (start, sweep) = if (bipolar) {
        val mid = KNOB_START + KNOB_SWEEP / 2f
        mid to (v - 0.5f) * KNOB_SWEEP
      } else KNOB_START to v * KNOB_SWEEP
      if (enabled && kotlin.math.abs(sweep) > 0.5f) {
        drawArc(accent.copy(alpha = 0.25f), start, sweep, false, arcTopLeft, arcSize, style = Stroke(stroke * 2.4f, cap = StrokeCap.Round))
        drawArc(accent, start, sweep, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
      }

      // Disc and rim
      drawCircle(disc, radius = discR, center = center)
      drawCircle(rim, radius = discR, center = center, style = Stroke(1.5.dp.toPx()))

      // Indicator tick, rotated to the value
      val angle = KNOB_START + v * KNOB_SWEEP + 90f
      rotate(angle, center) {
        val len = discR * 0.22f
        drawLine(
          tick,
          Offset(center.x, center.y - discR + discR * 0.14f),
          Offset(center.x, center.y - discR + discR * 0.14f + len),
          strokeWidth = 3.5.dp.toPx(),
          cap = StrokeCap.Round
        )
      }
    }
  }
}

/** The shape of a filter, drawn as a small line icon (bell, shelves, passes). */
@Composable
fun FilterShapeIcon(type: FilterType, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
  Canvas(modifier = modifier.size(width = 34.dp, height = 18.dp)) { drawFilterShape(type, color) }
}

private fun DrawScope.drawFilterShape(type: FilterType, color: Color) {
  val w = size.width
  val h = size.height
  val base = h * 0.82f
  val top = h * 0.12f
  val p = Path()
  when (type) {
    FilterType.PEAK -> {
      p.moveTo(0f, base)
      p.lineTo(w * 0.28f, base)
      p.cubicTo(w * 0.40f, base, w * 0.42f, top, w * 0.5f, top)
      p.cubicTo(w * 0.58f, top, w * 0.60f, base, w * 0.72f, base)
      p.lineTo(w, base)
    }
    FilterType.LOW_SHELF -> {
      p.moveTo(0f, top)
      p.lineTo(w * 0.3f, top)
      p.cubicTo(w * 0.5f, top, w * 0.5f, base, w * 0.7f, base)
      p.lineTo(w, base)
    }
    FilterType.HIGH_SHELF -> {
      p.moveTo(0f, base)
      p.lineTo(w * 0.3f, base)
      p.cubicTo(w * 0.5f, base, w * 0.5f, top, w * 0.7f, top)
      p.lineTo(w, top)
    }
    FilterType.HIGH_PASS -> {
      p.moveTo(w * 0.12f, h)
      p.cubicTo(w * 0.3f, top, w * 0.4f, top, w * 0.55f, top)
      p.lineTo(w, top)
    }
    FilterType.LOW_PASS -> {
      p.moveTo(0f, top)
      p.lineTo(w * 0.45f, top)
      p.cubicTo(w * 0.6f, top, w * 0.7f, top, w * 0.88f, h)
    }
  }
  drawPath(p, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * A tall fader in a rounded rail, like the preamp and gain faders of a mixing desk: [value] runs
 * from -1 (bottom) to +1 (top); the lit part runs from the centre to the handle. Tap or drag.
 */
@Composable
fun EqRailFader(
  value: Float,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  accent: Color = MaterialTheme.colorScheme.primary,
  enabled: Boolean = true,
  description: String = ""
) {
  val colors = MaterialTheme.colorScheme
  val change by rememberUpdatedState(onChange)
  val rail = colors.onSurface.copy(alpha = 0.22f)
  val handleFill = colors.surfaceVariant
  val handleEdge = colors.onSurface.copy(alpha = 0.3f)
  val ink = colors.onSurface
  Canvas(
    modifier = modifier
      .semantics { contentDescription = description }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures { o -> change(posToValue(o.y, this.size.height.toFloat(), FADER_HANDLE.toPx())) }
      }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectVerticalDragGestures { c, _ ->
          c.consume()
          change(posToValue(c.position.y, this.size.height.toFloat(), FADER_HANDLE.toPx()))
        }
      }
  ) {
    val cx = size.width / 2f
    val handleH = FADER_HANDLE.toPx()
    val handleW = (size.width * 0.62f).coerceAtMost(28.dp.toPx())
    val top = handleH / 2f
    val bottom = size.height - handleH / 2f
    val mid = (top + bottom) / 2f
    val y = bottom - (value.coerceIn(-1f, 1f) + 1f) / 2f * (bottom - top)

    // Ticks at the ends, like a printed scale
    listOf(top, bottom).forEach { ty ->
      drawLine(rail, Offset(cx - 10.dp.toPx(), ty), Offset(cx - 5.dp.toPx(), ty), 1.5f)
      drawLine(rail, Offset(cx + 5.dp.toPx(), ty), Offset(cx + 10.dp.toPx(), ty), 1.5f)
    }
    drawLine(rail, Offset(cx, top), Offset(cx, bottom), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    if (enabled && kotlin.math.abs(y - mid) > 1f) {
      drawLine(accent.copy(alpha = 0.3f), Offset(cx, mid), Offset(cx, y), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
      drawLine(accent, Offset(cx, mid), Offset(cx, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
    val left = cx - handleW / 2f
    drawRoundRect(handleFill, Offset(left, y - handleH / 2f), Size(handleW, handleH), CornerRadius(handleW / 2f))
    drawRoundRect(handleEdge, Offset(left, y - handleH / 2f), Size(handleW, handleH), CornerRadius(handleW / 2f), style = Stroke(1.5.dp.toPx()))
    drawLine(ink.copy(alpha = if (enabled) 0.9f else 0.3f), Offset(cx - handleW * 0.28f, y), Offset(cx + handleW * 0.28f, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
  }
}

/** Height of the fader handle; its travel stops half a handle from each end. */
private val FADER_HANDLE = 56.dp

private fun posToValue(y: Float, h: Float, handle: Float): Float {
  val top = handle / 2f
  val bottom = h - handle / 2f
  return (1f - 2f * ((y - top) / (bottom - top))).coerceIn(-1f, 1f)
}
