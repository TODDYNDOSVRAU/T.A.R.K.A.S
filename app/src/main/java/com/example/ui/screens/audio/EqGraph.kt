package com.example.ui.screens.audio

import androidx.compose.ui.platform.LocalContext
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.data.model.EqBand
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqSettings
import com.example.ui.theme.AppText
import com.example.ui.theme.Shapes
import com.example.ui.theme.Space
import com.example.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * What the band strip shows right now, shared with the graph above it: the strip's scroll and the
 * width of its window, so the graph can draw that window and drag it.
 */
@Stable
internal class StripView(val scroll: ScrollState) {
  var viewport by mutableIntStateOf(0)
  val total: Int get() = scroll.maxValue + viewport
}

/** Points along the frequency axis. Enough for a smooth curve at phone width, few enough to redo per frame. */
private const val CURVE_POINTS = 128

/**
 * The settings with every field that does not shape the frequency response set to its default, so
 * moving balance, width, the limiter or the virtualizer does not redo the curve.
 */
private fun EqSettings.responseKey(): EqSettings = copy(
  balance = 0f,
  stereoWidth = 1f,
  limiterEnabled = true,
  limiterCeilingDb = -0.5f,
  virtualizerStrength = 0
)

/**
 * The real frequency response of the whole chain (bands, tone shelves and preamp), computed from the
 * same filter maths the audio uses, over 20 Hz to 20 kHz on a log scale. The live spectrum sits behind
 * it; each band shows as a dot, for reading only: the dots are never touched, they move when a control moves. With a [window] the part of the strip in view is
 * outlined, and dragging sideways slides the strip.
 */
@Composable
internal fun EqGraph(
  settings: EqSettings,
  selectedBand: Int,
  spectrumBars: () -> List<Float>,
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
  window: StripView? = null
) {
  val colors = MaterialTheme.colorScheme
  val scope = rememberCoroutineScope()
  val curveKey = settings.responseKey()
  // Computed only when something that shapes the response changes. EqDsp designs the filters once per
  // settings value, so each point costs a handful of multiplications.
  // The shape as set: the level the app lowers on its own (fone headroom, automatic preamp) is left
  // out, so each band's point sits on the curve under the finger; that reduction is printed below.
  val target = remember(curveKey) {
    val automatic = EqDsp.totalPreampDb(curveKey) - curveKey.preampDb
    FloatArray(CURVE_POINTS) { i -> EqDsp.responseDb(curveKey, xToFreq(i / (CURVE_POINTS - 1f)).toDouble()).toFloat() - automatic }
  }
  // The fone correction alone, drawn as a dashed guide under the curve.
  val deviceCurve = remember(settings.device) {
    settings.device?.let { d ->
      val alone = EqSettings(bands = emptyList(), device = d, autoPreamp = false)
      FloatArray(CURVE_POINTS) { i -> EqDsp.responseDb(alone, xToFreq(i / (CURVE_POINTS - 1f)).toDouble()).toFloat() - d.preampDb }
    }
  }

  // When the curve jumps (a preset, a fone, reset) it morphs into the new shape, critically damped.
  // While a finger drags a control the changes arrive every frame and the curve follows them directly.
  val reduced = rememberReducedMotion()
  val shown = remember { target.copyOf() }
  val from = remember { target.copyOf() }
  val morph = remember { Animatable(1f) }
  val lastChange = remember { longArrayOf(0L) }
  LaunchedEffect(target) {
    val now = android.os.SystemClock.uptimeMillis()
    val continuous = now - lastChange[0] < 160
    lastChange[0] = now
    shown.copyInto(from)
    if (continuous || reduced) {
      morph.snapTo(1f)
    } else {
      morph.snapTo(0f)
      morph.animateTo(1f, spring(dampingRatio = 1f, stiffness = 300f))
    }
  }

  val accent = colors.primary
  // In parametric mode each band has its colour, and its own curve is drawn under the total.
  val parametric = settings.parametric
  val bandTints = settings.activeBands.indices.map { bandColor(it) }
  val bandCurves = remember(curveKey) {
    if (!curveKey.parametric) emptyList() else curveKey.paramBands.map { b ->
      if (!b.enabled || (b.type.usesGain && kotlin.math.abs(b.gainDb) < 0.05f)) null
      else {
        val c = com.example.data.audio.dsp.BiquadCoefficients.design(b.type, b.freqHz.toDouble(), b.gainDb.toDouble(), b.q.toDouble(), 48000.0)
        FloatArray(CURVE_POINTS) { i -> c.magnitudeDb(xToFreq(i / (CURVE_POINTS - 1f)).toDouble(), 48000.0).toFloat() }
      }
    }
  }
  val grid = colors.onSurface.copy(alpha = 0.08f)
  val zero = colors.onSurface.copy(alpha = 0.24f)
  val label = colors.onSurfaceVariant
  val nodeFill = colors.surface
  val dim = if (settings.enabled) 1f else 0.38f
  val windowEdge = colors.onSurface.copy(alpha = 0.4f)
  val outsideShade = colors.background.copy(alpha = 0.5f)

  // What the curve does, in words: its highest boost and deepest cut, so a screen reader gets the shape.
  val res = LocalContext.current.resources
  val summary = remember(settings, res) {
    val freqs = (0..40).map { 20.0 * Math.pow(1000.0, it / 40.0) }
    val points = freqs.map { it to EqDsp.responseDb(settings, it) }
    val high = points.maxByOrNull { it.second }!!
    val low = points.minByOrNull { it.second }!!
    when {
      high.second < 0.25 && low.second > -0.25 -> res.getString(R.string.audio_curve_flat)
      else -> buildString {
        if (high.second >= 0.25) append(res.getString(R.string.audio_curve_peak_gain, formatDb(high.second.toFloat()), spokenHz(high.first.toFloat())))
        if (low.second <= -0.25) append(res.getString(R.string.audio_curve_peak_cut, formatDb(low.second.toFloat()), spokenHz(low.first.toFloat())))
      }.trim()
    }
  }
  val curveDescription = stringResource(R.string.audio_curve_description, summary)
  Box(
    modifier = modifier
      .clip(Shapes.m)
      .background(colors.surface)
      .then(if (window != null) Modifier.overviewGestures(window, scope) else Modifier)
      .semantics { contentDescription = curveDescription }
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // Grid: 0 dB, ±6 and ±12 dB; decades of frequency
      listOf(-12f, -6f, 6f, 12f).forEach { db -> drawLine(grid, Offset(0f, dbToY(db, h)), Offset(w, dbToY(db, h)), 1f) }
      drawLine(zero, Offset(0f, dbToY(0f, h)), Offset(w, dbToY(0f, h)), 1.dp.toPx())
      listOf(100f, 1000f, 10000f).forEach { f ->
        val x = freqToX(f) * w
        drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
      }

      // Live spectrum behind the curve (nothing while nothing plays)
      val bars = spectrumBars()
      if (isPlaying && bars.isNotEmpty()) {
        val bw = w / bars.size
        bars.forEachIndexed { i, e ->
          val bh = (e * h * 0.85f).coerceAtLeast(2f)
          drawRect(
            color = accent.copy(alpha = 0.10f),
            topLeft = Offset(i * bw + bw * 0.15f, h - bh),
            size = Size(bw * 0.7f, bh)
          )
        }
      }

      deviceCurve?.let { dc ->
        val guide = Path()
        dc.forEachIndexed { i, db ->
          val x = i / (CURVE_POINTS - 1f) * w
          val y = dbToY(db.coerceIn(-GRAPH_DB_RANGE - 3f, GRAPH_DB_RANGE + 3f), h)
          if (i == 0) guide.moveTo(x, y) else guide.lineTo(x, y)
        }
        drawPath(
          guide,
          label.copy(alpha = 0.6f * dim),
          style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        )
      }

      // Each parametric band's own curve, in its colour, under the total
      bandCurves.forEachIndexed { bi, curve ->
        if (curve == null) return@forEachIndexed
        val bp = Path()
        curve.forEachIndexed { i, db ->
          val x = i / (CURVE_POINTS - 1f) * w
          val y = dbToY(db.coerceIn(-GRAPH_DB_RANGE - 3f, GRAPH_DB_RANGE + 3f), h)
          if (i == 0) bp.moveTo(x, y) else bp.lineTo(x, y)
        }
        drawPath(bp, bandTints[bi].copy(alpha = 0.7f * dim), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
      }

      // Response curve with a light fill towards 0 dB
      val p = morph.value
      for (i in shown.indices) shown[i] = from[i] + (target[i] - from[i]) * p
      val path = Path()
      shown.forEachIndexed { i, db ->
        val x = i / (CURVE_POINTS - 1f) * w
        val y = dbToY(db.coerceIn(-GRAPH_DB_RANGE - 3f, GRAPH_DB_RANGE + 3f), h)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      val fill = Path().apply {
        addPath(path)
        lineTo(w, dbToY(0f, h))
        lineTo(0f, dbToY(0f, h))
        close()
      }
      drawPath(fill, accent.copy(alpha = 0.10f * dim))
      drawPath(path, accent.copy(alpha = dim), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

      // Band nodes: the selected one filled with the accent
      settings.activeBands.forEachIndexed { i, band ->
        val pos = nodePosition(curveKey, i, w, h)
        val isSel = i == selectedBand
        val r = if (isSel) 6.dp.toPx() else 4.dp.toPx()
        val c = (if (band.enabled) bandTints.getOrElse(i) { accent } else label).copy(alpha = dim)
        if (isSel) {
          drawCircle(c, radius = r, center = pos)
        } else {
          drawCircle(nodeFill, radius = r, center = pos)
          drawCircle(c, radius = r, center = pos, style = Stroke(width = 1.5.dp.toPx()))
        }
      }

      // The strip's window: the part of the bands in view, with the rest veiled
      window?.let { v ->
        val total = v.total
        if (total > 0 && v.scroll.maxValue > 0) {
          val left = v.scroll.value.toFloat() / total * w
          val ww = v.viewport.toFloat() / total * w
          drawRect(outsideShade, Offset.Zero, Size(left, h))
          drawRect(outsideShade, Offset(left + ww, 0f), Size(w - left - ww, h))
          drawRoundRect(
            windowEdge,
            Offset(left, 0f),
            Size(ww, h),
            CornerRadius(com.example.ui.theme.Radius.xs.toPx()),
            style = Stroke(1.dp.toPx())
          )
        }
      }
    }

    Row(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .padding(horizontal = Space.s, vertical = Space.xxs),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      listOf("20", "100", "1k", "10k", "20k").forEach { Text(it, style = AppText.Caption2, color = label) }
    }
    Text(
      "+${GRAPH_DB_RANGE.toInt()} dB",
      style = AppText.Caption2,
      color = label,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(start = Space.s, top = Space.xxs)
    )
    Text(
      "0",
      style = AppText.Caption2,
      color = label,
      modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = Space.s)
    )
  }
}

/**
 * Gestures of the graph: drag sideways to slide the strip's window (and with it the strip), tap to
 * jump the window there. Vertical drags are left alone, so the page still scrolls over the graph.
 */
private fun Modifier.overviewGestures(view: StripView, scope: CoroutineScope): Modifier = pointerInput(view) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val w = size.width.toFloat()
    fun stripPerPx() = view.total / w
    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
      change.consume()
      view.scroll.dispatchRawDelta(over * stripPerPx())
    }
    if (slop == null) {
      val up = currentEvent.changes.firstOrNull { it.id == down.id }
      if (up != null && !up.pressed && !up.isConsumed) {
        val target = (down.position.x * stripPerPx() - view.viewport / 2f).roundToInt().coerceIn(0, view.scroll.maxValue)
        scope.launch { view.scroll.animateScrollTo(target, spring(dampingRatio = 1f, stiffness = 320f)) }
      }
      return@awaitEachGesture
    }
    horizontalDrag(slop.id) { change ->
      val dx = change.positionChange().x
      change.consume()
      view.scroll.dispatchRawDelta(dx * stripPerPx())
    }
  }
}

/** Where band [i] of [s] (a [responseKey]) is drawn: at its gain for peaks and shelves, on the curve for passes. */
private fun nodePosition(s: EqSettings, i: Int, w: Float, h: Float): Offset {
  val b = s.activeBands[i]
  val x = freqToX(b.freqHz) * w
  val db = if (b.type.usesGain) b.gainDb
    else EqDsp.responseDb(s, b.freqHz.toDouble()).toFloat() - (EqDsp.totalPreampDb(s) - s.preampDb)
  return Offset(x, dbToY(db.coerceIn(-GRAPH_DB_RANGE, GRAPH_DB_RANGE), h))
}
