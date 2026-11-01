package com.example.ui.screens.audio

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
 * it; each band shows as a dot, for reading only. With a [window] the part of the strip in view is
 * outlined, and dragging sideways slides the strip.
 */
@Composable
internal fun EqGraph(
  settings: EqSettings,
  selectedBand: Int,
  spectrumBars: () -> List<Float>,
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
  window: StripView? = null,
  onSelectBand: (Int) -> Unit = {},
  onUpdateBand: ((Int, (EqBand) -> EqBand) -> Unit)? = null
) {
  val colors = MaterialTheme.colorScheme
  val scope = rememberCoroutineScope()
  val haptics = LocalHapticFeedback.current
  val curveKey = settings.responseKey()
  val latestSettings by rememberUpdatedState(settings)
  val latestSelect by rememberUpdatedState(onSelectBand)
  val latestUpdate by rememberUpdatedState(onUpdateBand)
  var grabbed by remember { mutableIntStateOf(-1) }
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
  val grid = colors.onSurface.copy(alpha = 0.08f)
  val zero = colors.onSurface.copy(alpha = 0.24f)
  val label = colors.onSurfaceVariant
  val nodeFill = colors.surface
  val dim = if (settings.enabled) 1f else 0.38f
  val windowEdge = colors.onSurface.copy(alpha = 0.4f)
  val outsideShade = colors.background.copy(alpha = 0.5f)

  Box(
    modifier = modifier
      .clip(Shapes.m)
      .background(colors.surface)
      .then(
        if (onUpdateBand != null && settings.enabled) {
          Modifier.curveGestures(
            settings = { latestSettings },
            window = window,
            scope = scope,
            onGrab = { i ->
              grabbed = i
              if (i >= 0) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                latestSelect(i)
              }
            },
            onTick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
            onEdit = { i, t -> latestUpdate?.invoke(i, t) }
          )
        } else if (window != null) {
          Modifier.overviewGestures(window, scope)
        } else {
          Modifier
        }
      )
      .semantics {
        contentDescription = "Curva do equalizador. Arraste um ponto para mudar a banda, toque duas vezes nele para voltar a 0 dB. " +
          "Arraste fora dos pontos para rolar as bandas."
      }
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
        val isSel = i == selectedBand || i == grabbed
        // The grabbed point grows under the finger so it stays visible past the fingertip.
        val r = when {
          i == grabbed -> 9.dp.toPx()
          isSel -> 6.dp.toPx()
          else -> 4.dp.toPx()
        }
        if (i == grabbed) {
          // A guide down to the frequency axis; the values are printed at the top of the graph.
          drawLine(accent.copy(alpha = 0.35f), Offset(pos.x, 0f), Offset(pos.x, h), 1.dp.toPx())
        }
        val c = (if (band.enabled) accent else label).copy(alpha = dim)
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
            CornerRadius(8.dp.toPx()),
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
      "+12 dB",
      style = AppText.Caption2,
      color = label,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(start = Space.s, top = Space.xxs)
    )
    settings.activeBands.getOrNull(grabbed)?.let { b ->
      Text(
        spokenHz(b.freqHz) + if (b.type.usesGain) " · ${formatDb(b.gainDb)} dB" else "",
        style = AppText.Caption1.copy(fontFeatureSettings = "tnum"),
        color = accent,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = Space.xxs)
      )
    }
  }
}

/** How close, in dp, a finger must land to a band's point to grab it instead of scrolling. */
private const val GRAB_RADIUS_DP = 24f
private const val DOUBLE_TAP_MS = 300L

/**
 * Direct editing on the curve. A finger that lands on a band's point grabs it: graphic bands move up
 * and down (gain), parametric bands move anywhere (frequency and gain). A double tap on a point
 * returns it to 0 dB. A finger anywhere else keeps the overview gestures (slide the strip's window).
 * Crossing 0 dB gives a haptic tick.
 */
private fun Modifier.curveGestures(
  settings: () -> EqSettings,
  window: StripView?,
  scope: CoroutineScope,
  onGrab: (Int) -> Unit,
  onTick: () -> Unit,
  onEdit: (Int, (EqBand) -> EqBand) -> Unit
): Modifier = pointerInput(window) {
  var lastTapBand = -1
  var lastTapAt = 0L
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val s = settings()
    val key = s.responseKey()
    val radius = GRAB_RADIUS_DP.dp.toPx()
    val hit = s.activeBands.indices
      .map { it to (nodePosition(key, it, w, h) - down.position).getDistance() }
      .filter { it.second <= radius }
      .minByOrNull { it.second }?.first

    if (hit == null) {
      // Not on a point: the overview gesture, sideways only, so the page still scrolls over the graph.
      if (window == null) return@awaitEachGesture
      fun stripPerPx() = window.total / w
      val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
        change.consume()
        window.scroll.dispatchRawDelta(over * stripPerPx())
      }
      if (slop == null) {
        val up = currentEvent.changes.firstOrNull { it.id == down.id }
        if (up != null && !up.pressed && !up.isConsumed) {
          val target = (down.position.x * stripPerPx() - window.viewport / 2f).roundToInt().coerceIn(0, window.scroll.maxValue)
          scope.launch { window.scroll.animateScrollTo(target, spring(dampingRatio = 1f, stiffness = 320f)) }
        }
        return@awaitEachGesture
      }
      horizontalDrag(slop.id) { change ->
        val dx = change.positionChange().x
        change.consume()
        window.scroll.dispatchRawDelta(dx * stripPerPx())
      }
      return@awaitEachGesture
    }

    // On a point: it is ours from the first touch, so the page does not scroll under the finger.
    down.consume()
    onGrab(hit)
    val band = s.activeBands[hit]
    val parametric = s.parametric
    // The grab offset is kept, so the point never jumps to the fingertip.
    val start = nodePosition(key, hit, w, h)
    val grabDx = start.x - down.position.x
    val grabDy = start.y - down.position.y
    var lastGain = band.gainDb
    var moved = false
    while (true) {
      val event = awaitPointerEvent()
      val change = event.changes.firstOrNull { it.id == down.id } ?: break
      if (!change.pressed) break
      if (!moved && (change.position - down.position).getDistance() < viewConfiguration.touchSlop) continue
      moved = true
      change.consume()
      val gain = if (band.type.usesGain) {
        snap(yToDb(change.position.y + grabDy, h).coerceIn(-EqBand.MAX_GAIN, EqBand.MAX_GAIN))
      } else band.gainDb
      if ((lastGain > 0f && gain <= 0f) || (lastGain < 0f && gain >= 0f)) onTick()
      lastGain = gain
      if (parametric) {
        val f = xToFreq(((change.position.x + grabDx) / w).coerceIn(0f, 1f))
        onEdit(hit) { it.copy(freqHz = f, gainDb = gain) }
      } else {
        onEdit(hit) { it.copy(gainDb = gain) }
      }
    }
    onGrab(-1)
    if (!moved) {
      val now = android.os.SystemClock.uptimeMillis()
      if (hit == lastTapBand && now - lastTapAt < DOUBLE_TAP_MS) {
        if (band.gainDb != 0f) onTick()
        onEdit(hit) { it.copy(gainDb = 0f) }
        lastTapBand = -1
      } else {
        lastTapBand = hit
        lastTapAt = now
      }
    }
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
