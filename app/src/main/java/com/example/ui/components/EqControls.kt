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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.FilterType
import kotlin.math.abs

/*
 * The equalizer's own controls: a vertical fader and a rotary knob. Both are drawn flat (a quiet
 * track, the accent only on the part that is not neutral, a plain handle) and both are real
 * adjustable controls for a screen reader: they say what they set, read out their value with its
 * unit, and take the swipe-up / swipe-down adjust gestures of TalkBack.
 */

private const val KNOB_SWEEP = 270f
private const val KNOB_START = 135f

/**
 * The fader follows the finger 1:1: the handle moves as far as the finger does. Fine values come from
 * the numeric field behind the readout, not from a slowed-down drag. A knob has no handle to follow, so
 * it sweeps its range over this many control heights.
 */
private const val KNOB_TRAVEL = 1.6f

/**
 * A vertical fader: [value] runs from -1 (bottom) to +1 (top), 0 is the centre detent. The lit part
 * runs from the centre to the handle, so a cut and a boost read differently at a glance. A drag moves
 * relative to where it started, so touching the handle never makes it jump; a double tap returns to
 * the centre. Crossing the centre or reaching an end gives a haptic tick.
 *
 * [label] names what it sets ("Banda 1 kHz"); [stateText] is the value as heard ("+3.0 dB").
 * [steps] is how many stops a screen reader's adjust gesture walks between the ends.
 * [onHeldChange] reports a finger on the fader, so the caller can enlarge its readout.
 */
@Composable
fun EqFader(
  value: Float,
  onChange: (Float) -> Unit,
  label: String,
  stateText: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  steps: Int = 29,
  onHeldChange: (Boolean) -> Unit = {},
  /** The colour of the filled part; the theme accent when null. A band passes its own. */
  tint: androidx.compose.ui.graphics.Color? = null
) {
  val colors = MaterialTheme.colorScheme
  val current by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  val held by rememberUpdatedState(onHeldChange)
  var dragValue by remember { mutableFloatStateOf(value) }
  val haptics = LocalHapticFeedback.current
  val track = colors.onSurface.copy(alpha = 0.12f)
  val tick = colors.onSurface.copy(alpha = 0.24f)
  val accent = if (enabled) tint ?: colors.primary else colors.onSurface.copy(alpha = 0.24f)
  val handle = if (enabled) colors.surfaceContainerHighest else colors.surfaceContainerHigh
  val dash = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)

  Canvas(
    modifier = modifier
      .semantics {
        contentDescription = label
        stateDescription = stateText
        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(-1f, 1f), -1f..1f, steps)
        if (enabled) {
          setProgress { target ->
            change(target.coerceIn(-1f, 1f))
            true
          }
        } else {
          disabled()
        }
      }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        // A single tap changes nothing, so a finger that lands on a fader while scrolling never moves
        // it. A double tap goes back to 0 dB.
        detectTapGestures(
          onDoubleTap = {
            if (current != 0f) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            change(0f)
          }
        )
      }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        // Only a finger that lands on the handle moves it, as with a slider's thumb. Anywhere else on
        // the track the drag is left to the page, so scrolling over the strip never changes a band.
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val handleH = FADER_HANDLE_H.toPx()
          val top = handleH / 2f
          val bottom = size.height - handleH / 2f
          val handleY = bottom - (current.coerceIn(-1f, 1f) + 1f) / 2f * (bottom - top)
          if (abs(down.position.y - handleY) > FADER_GRAB.toPx()) return@awaitEachGesture
          // Every move is consumed as it arrives, so the page never starts scrolling under a held
          // handle; the down itself is left alone so a double tap still reaches the tap detector.
          dragValue = current
          held(true)
          try {
            verticalDrag(down.id) { c ->
              val dy = c.positionChange().y
              c.consume()
              val travel = bottom - top
              val before = dragValue
              dragValue = (dragValue - 2f * dy / travel).coerceIn(-1f, 1f)
              // A tick when the handle crosses or lands on the centre, and at the two ends: the detent
              // a desk fader has, felt without looking.
              val crossed = (before > 0f && dragValue <= 0f) || (before < 0f && dragValue >= 0f)
              val hitEnd = abs(dragValue) == 1f && abs(before) < 1f
              if (crossed || hitEnd) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
              if (dragValue != before) change(dragValue)
            }
          } finally {
            held(false)
          }
        }
      }
  ) {
    val cx = size.width / 2f
    val handleH = FADER_HANDLE_H.toPx()
    val handleW = FADER_HANDLE_W.toPx().coerceAtMost(size.width)
    val top = handleH / 2f
    val bottom = size.height - handleH / 2f
    val mid = (top + bottom) / 2f
    val y = bottom - (value.coerceIn(-1f, 1f) + 1f) / 2f * (bottom - top)
    val trackW = 2.dp.toPx()

    // Dotted marks at +max, 0 dB and -max on both sides of the track, like the rule of a desk.
    val dot = 1.dp.toPx()
    val gap = 7.dp.toPx()
    for (markY in floatArrayOf(top, mid, bottom)) {
      drawCircle(tick, dot, Offset(cx - gap, markY))
      drawCircle(tick, dot, Offset(cx + gap, markY))
    }

    drawLine(track, Offset(cx, top), Offset(cx, bottom), strokeWidth = trackW, cap = StrokeCap.Round)
    if (abs(y - mid) > 0.5f) {
      drawLine(accent, Offset(cx, mid), Offset(cx, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
    // The thumb: a tall pill with a light dash across it.
    drawRoundRect(
      color = handle,
      topLeft = Offset(cx - handleW / 2f, y - handleH / 2f),
      size = Size(handleW, handleH),
      cornerRadius = CornerRadius(handleW / 2f)
    )
    val dashW = handleW * 0.46f
    drawLine(dash, Offset(cx - dashW / 2f, y), Offset(cx + dashW / 2f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
  }
}

/** How far above or below the handle's centre a finger still grabs it: a 48 dp touch target. */
private val FADER_GRAB = 24.dp

/** The handle's size; its travel stops half a handle from each end. */
private val FADER_HANDLE_H = 38.dp
private val FADER_HANDLE_W = 24.dp

/**
 * A rotary knob: a quiet disc with an indicator, and an arc showing the value. [value] runs 0..1;
 * drag up to turn it up, double-tap to go back to [defaultValue]. A [bipolar] knob draws its arc
 * from the top (centre) instead of from the start, for controls whose neutral is the middle.
 *
 * [label] names what it sets; [stateText] is the value with its unit, read by screen readers.
 */
@Composable
fun EqKnob(
  value: Float,
  onChange: (Float) -> Unit,
  label: String,
  stateText: String,
  modifier: Modifier = Modifier,
  size: Dp = 64.dp,
  bipolar: Boolean = false,
  defaultValue: Float = if (bipolar) 0.5f else 0f,
  enabled: Boolean = true,
  steps: Int = 0,
  /** The colour of the arc; the theme accent when null. A band passes its own. */
  tint: androidx.compose.ui.graphics.Color? = null
) {
  val colors = MaterialTheme.colorScheme
  val current by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  val reset by rememberUpdatedState(defaultValue)
  var dragValue by remember { mutableFloatStateOf(value) }
  val track = colors.onSurface.copy(alpha = 0.12f)
  val disc = colors.surfaceContainerHigh
  val accent = if (enabled) tint ?: colors.primary else colors.onSurface.copy(alpha = 0.24f)
  val tick = colors.onSurface.copy(alpha = if (enabled) 0.9f else 0.38f)

  Box(
    modifier = modifier
      .size(size)
      .semantics {
        contentDescription = label
        stateDescription = stateText
        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f, steps)
        if (enabled) {
          setProgress { target ->
            change(target.coerceIn(0f, 1f))
            true
          }
        } else {
          disabled()
        }
      }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onDoubleTap = { change(reset) })
      }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectVerticalDragGestures(onDragStart = { dragValue = current }) { c, dy ->
          c.consume()
          dragValue = (dragValue - dy / (this.size.height * KNOB_TRAVEL)).coerceIn(0f, 1f)
          change(dragValue)
        }
      }
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = 4.dp.toPx()
      val inset = stroke / 2f
      val arcTopLeft = Offset(inset, inset)
      val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
      val discR = this.size.minDimension / 2f - stroke - 3.dp.toPx()
      val v = value.coerceIn(0f, 1f)

      drawArc(track, KNOB_START, KNOB_SWEEP, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
      val (start, sweep) = if (bipolar) {
        (KNOB_START + KNOB_SWEEP / 2f) to (v - 0.5f) * KNOB_SWEEP
      } else {
        KNOB_START to v * KNOB_SWEEP
      }
      if (abs(sweep) > 0.5f) {
        drawArc(accent, start, sweep, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
      }

      drawCircle(disc, radius = discR, center = center)
      rotate(KNOB_START + v * KNOB_SWEEP + 90f, center) {
        val from = center.y - discR + discR * 0.18f
        drawLine(
          tick,
          Offset(center.x, from),
          Offset(center.x, from + discR * 0.3f),
          strokeWidth = 2.5.dp.toPx(),
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
