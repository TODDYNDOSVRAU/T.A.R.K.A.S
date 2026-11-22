package com.example.ui.components

import com.example.ui.theme.Grid
import com.example.ui.theme.Space
import com.example.ui.theme.EqSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.AppMotion
import com.example.ui.theme.Motion
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.ui.theme.LocalSurfaceStyle
import com.example.ui.theme.SurfaceStyle
import com.example.ui.theme.drawLiquidGlassPillThumb
import com.example.ui.theme.liquidGlassSupported
import com.example.ui.theme.liquidLensPill
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
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
/**
 * Maps absolute EQ gain intensity (0..1, same for boost and cut) from green through amber to red,
 * as requested by the owner for the graphic faders.
 */
fun eqIntensityColor(normalizedGain: Float): Color {
  val t = abs(normalizedGain).coerceIn(0f, 1f)
  val green = Color(0xFF22C55E)
  val amber = Color(0xFFF59E0B)
  val red = Color(0xFFEF4444)
  return if (t <= 0.5f) lerp(green, amber, t * 2f) else lerp(amber, red, (t - 0.5f) * 2f)
}

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
  tint: androidx.compose.ui.graphics.Color? = null,
  /** The thumb's size. The graphic bands use a thin 12 dp thumb so 10 faders fit under their frequencies. */
  handleWidth: Dp = FADER_HANDLE_W,
  handleHeight: Dp = FADER_HANDLE_H,
  /** The dotted marks beside the track; the graphic bands leave them out (a dashed zero line replaces them). */
  marks: Boolean = true,
  /** The track's width: a fine line by default, a bold one under the graphic bands' thin thumb. */
  trackWidth: Dp = EqSize.lineFine,
  /** A solid thumb in this colour, with no dash across it; null draws the default raised thumb. */
  handleColor: androidx.compose.ui.graphics.Color? = null
) {
  val colors = MaterialTheme.colorScheme
  val isLiquid = LocalSurfaceStyle.current == SurfaceStyle.LIQUID && liquidGlassSupported
  val isDark = colors.background.luminance() < 0.5f
  val reduced = AppMotion.reduced
  val scope = rememberCoroutineScope()
  val current by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  val held by rememberUpdatedState(onHeldChange)
  var dragValue by remember { mutableFloatStateOf(value) }
  val isDragging = remember { booleanArrayOf(false) }
  val shownAnim = remember { Animatable(value.coerceIn(-1f, 1f)) }
  val thumbScale = remember { Animatable(1f) }
  val rubberY = remember { Animatable(0f) }

  LaunchedEffect(value, reduced) {
    val target = value.coerceIn(-1f, 1f)
    if (isDragging[0] || reduced) {
      shownAnim.snapTo(target)
    } else {
      shownAnim.animateTo(target, Motion.fluid(visibilityThreshold = 0.002f))
    }
  }

  val haptics = LocalHapticFeedback.current
  val track = colors.onSurface.copy(alpha = 0.12f)
  val tick = colors.onSurface.copy(alpha = 0.24f)
  val accent = if (enabled) tint ?: colors.primary else colors.onSurface.copy(alpha = 0.24f)
  // surfaceContainerHighest is not set by the theme (it falls back to Material's lavender); a tone from the base colours instead.
  val handle = colors.onSurface.copy(alpha = if (enabled) 0.16f else 0.06f).compositeOver(colors.surface)
  val dash = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f)

  val faderBg = colors.background
  androidx.compose.foundation.layout.BoxWithConstraints(
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
          val handleH = handleHeight.toPx()
          val top = handleH / 2f
          val bottom = size.height - handleH / 2f
          val handleY = bottom - (current.coerceIn(-1f, 1f) + 1f) / 2f * (bottom - top)
          if (abs(down.position.y - handleY) > maxOf(FADER_GRAB.toPx(), handleH / 2f)) return@awaitEachGesture
          // Every move is consumed as it arrives, so the page never starts scrolling under a held
          // handle; the down itself is left alone so a double tap still reaches the tap detector.
          dragValue = current
          var rawDrag = current
          isDragging[0] = true
          held(true)
          if (!reduced) {
            scope.launch { thumbScale.animateTo(1.08f, Motion.criticalSpatial(stiffness = 900f)) }
          }
          try {
            verticalDrag(down.id) { c ->
              val dy = c.positionChange().y
              c.consume()
              val travel = (bottom - top).coerceAtLeast(1f)
              val before = dragValue
              rawDrag -= 2f * dy / travel
              dragValue = rawDrag.coerceIn(-1f, 1f)
              scope.launch { shownAnim.snapTo(dragValue) }
              if (!reduced) {
                val overPx = when {
                  rawDrag > 1f -> -(rawDrag - 1f) * (travel / 2f)
                  rawDrag < -1f -> (-1f - rawDrag) * (travel / 2f)
                  else -> 0f
                }
                val rb = Motion.rubberBand(overPx, limit = 10.dp.toPx(), coefficient = 0.45f)
                scope.launch { rubberY.snapTo(rb) }
              }
              // A tick when the handle crosses or lands on the centre, and at the two ends: the detent
              // a desk fader has, felt without looking.
              val crossed = (before > 0f && dragValue <= 0f) || (before < 0f && dragValue >= 0f)
              val hitEnd = abs(dragValue) == 1f && abs(before) < 1f
              if (crossed || hitEnd) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
              if (dragValue != before) change(dragValue)
            }
          } finally {
            isDragging[0] = false
            held(false)
            if (!reduced) {
              scope.launch { thumbScale.animateTo(1f, Motion.fluidFast(visibilityThreshold = 0.002f)) }
              if (rubberY.value != 0f) {
                scope.launch { rubberY.animateTo(0f, Motion.fluidFast(visibilityThreshold = 0.5f)) }
              }
            }
          }
        }
      }
      .graphicsLayer { translationY = rubberY.value },
    contentAlignment = androidx.compose.ui.Alignment.TopCenter
  ) {
    val totalH = maxHeight
    val effHandleW = handleWidth.coerceAtMost(maxWidth)
    Canvas(
      modifier = Modifier.fillMaxSize()
    ) {
      val cx = size.width / 2f
      val handleH = handleHeight.toPx()
      val handleW = handleWidth.toPx().coerceAtMost(size.width)
      val top = handleH / 2f
      val bottom = size.height - handleH / 2f
      val mid = (top + bottom) / 2f
      val shownVal = shownAnim.value.coerceIn(-1f, 1f)
      val y = bottom - (shownVal + 1f) / 2f * (bottom - top)
      val trackW = trackWidth.toPx()

      // Dotted marks at +max, 0 dB and -max on both sides of the track, like the rule of a desk.
      val dot = Grid.hairline.toPx()
      val gap = Grid.u6.toPx()
      if (marks) for (markY in floatArrayOf(top, mid, bottom)) {
        drawCircle(tick, dot, Offset(cx - gap, markY))
        drawCircle(tick, dot, Offset(cx + gap, markY))
      }

      drawLine(track, Offset(cx, top), Offset(cx, bottom), strokeWidth = trackW, cap = StrokeCap.Round)
      if (abs(y - mid) > 0.5f) {
        drawLine(accent, Offset(cx, mid), Offset(cx, y), strokeWidth = EqSize.lineBold.toPx(), cap = StrokeCap.Round)
      }
      if (!(isLiquid && enabled)) {
        val thumbTopLeft = Offset(cx - handleW / 2f, y - handleH / 2f)
        val thumbSize = Size(handleW, handleH)
        drawRoundRect(
          color = handleColor ?: handle,
          topLeft = thumbTopLeft,
          size = thumbSize,
          cornerRadius = CornerRadius(handleW / 2f)
        )
        val dashW = handleW * 0.46f
        if (handleColor == null) drawLine(dash, Offset(cx - dashW / 2f, y), Offset(cx + dashW / 2f, y), strokeWidth = EqSize.lineFine.toPx(), cap = StrokeCap.Round)
      }
    }
    if (isLiquid && enabled) {
      val travelDp = (totalH - handleHeight).coerceAtLeast(0.dp)
      // Lightweight CanvasBackdrop draws the fader's own track and coloured gain strip in the pill's
      // local coordinates — eliminating 10 offscreen RenderNodes while preserving 100% AGSL refraction.
      val pillBackdrop = rememberCanvasBackdrop {
        drawRect(faderBg)
        val cx = size.width / 2f
        val trW = trackWidth.toPx()
        val boldW = EqSize.lineBold.toPx()
        drawLine(track, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = trW, cap = StrokeCap.Round)
        val sv = shownAnim.value.coerceIn(-1f, 1f)
        if (abs(sv) > 0.01f) {
          val cy = size.height / 2f
          val endY = if (sv > 0f) size.height else 0f
          drawLine(accent, Offset(cx, cy), Offset(cx, endY), strokeWidth = boldW, cap = StrokeCap.Round)
        }
      }
      Box(
        modifier = Modifier
          .graphicsLayer {
            val shownVal = shownAnim.value.coerceIn(-1f, 1f)
            translationY = travelDp.toPx() * (1f - (shownVal + 1f) / 2f)
            val s = thumbScale.value
            scaleX = s
            scaleY = s
          }
          .size(width = effHandleW, height = handleHeight)
          .liquidLensPill(
            backdrop = pillBackdrop,
            isDark = isDark,
            trackAccent = if (abs(value) > 0.02f) accent else null
          )
      )
    }
  }
}

/** How far above or below the handle's centre a finger still grabs it: a 48 dp touch target. */
private val FADER_GRAB = Grid.u24

/** The handle's size; its travel stops half a handle from each end. */
private val FADER_HANDLE_H = Grid.u48
private val FADER_HANDLE_W = Grid.u24

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
  size: Dp = Grid.u48,
  bipolar: Boolean = false,
  defaultValue: Float = if (bipolar) 0.5f else 0f,
  enabled: Boolean = true,
  steps: Int = 0,
  /** The colour of the arc; the theme accent when null. A band passes its own. */
  tint: androidx.compose.ui.graphics.Color? = null
) {
  val colors = MaterialTheme.colorScheme
  val isLiquid = LocalSurfaceStyle.current == SurfaceStyle.LIQUID && liquidGlassSupported
  val isDark = colors.background.luminance() < 0.5f
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
      val stroke = EqSize.lineBold.toPx()
      val inset = stroke / 2f
      val arcTopLeft = Offset(inset, inset)
      val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
      val discR = this.size.minDimension / 2f - stroke - EqSize.knobInset.toPx()
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
          strokeWidth = EqSize.lineBold.toPx(),
          cap = StrokeCap.Round
        )
      }
    }
  }
}

/** The shape of a filter, drawn as a small line icon (bell, shelves, passes). */
@Composable
fun FilterShapeIcon(type: FilterType, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
  Canvas(modifier = modifier.size(width = Grid.u24, height = Grid.u12)) { drawFilterShape(type, color) }
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
    FilterType.NOTCH -> {
      p.moveTo(0f, top)
      p.lineTo(w * 0.30f, top)
      p.cubicTo(w * 0.42f, top, w * 0.44f, base, w * 0.5f, base)
      p.cubicTo(w * 0.56f, base, w * 0.58f, top, w * 0.70f, top)
      p.lineTo(w, top)
    }
    FilterType.BAND_PASS -> {
      p.moveTo(w * 0.08f, h)
      p.cubicTo(w * 0.28f, base, w * 0.38f, top, w * 0.5f, top)
      p.cubicTo(w * 0.62f, top, w * 0.72f, base, w * 0.92f, h)
    }
    FilterType.TILT_SHELF -> {
      p.moveTo(0f, base)
      p.lineTo(w * 0.20f, base)
      p.cubicTo(w * 0.42f, base, w * 0.58f, top, w * 0.80f, top)
      p.lineTo(w, top)
    }
  }
  drawPath(p, color, style = Stroke(width = EqSize.lineFine.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
