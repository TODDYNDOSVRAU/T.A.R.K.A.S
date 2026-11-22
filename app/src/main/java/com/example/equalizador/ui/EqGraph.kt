package com.example.ui.screens.audio

import com.example.ui.theme.EqSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.ui.theme.Grid
import com.example.ui.theme.rememberReducedMotion

/** Points along the frequency axis. Enough for a smooth curve at phone width, few enough to redo per frame. */
private const val CURVE_POINTS = 128
private val CURVE_FREQS = DoubleArray(CURVE_POINTS) { i -> xToFreq(i / (CURVE_POINTS - 1f)).toDouble() }
private val GRID_DB = floatArrayOf(-12f, -6f, 6f, 12f)
private val GRID_FREQS = floatArrayOf(250f, 2000f)
private val DEVICE_DASH = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

/** The curve card: 168 dp of plot and a 24 dp axis row, 192 dp in all. */

/**
 * The settings with every field that does not shape the frequency response set to its default, so
 * moving balance, width, the limiter or the virtualizer does not redo the curve.
 */
private fun EqSettings.responseKey(): EqSettings = copy(
  balance = 0f,
  stereoWidth = 1f,
  limiterEnabled = true,
  limiterCeilingDb = 0f,
  virtualizerStrength = 0,
  crossfeed = com.example.data.model.CrossfeedSettings(),
  rtaMode = com.example.data.model.RtaMode.OFF
)

/**
 * The real frequency response of the whole chain (bands, tone shelves and preamp), computed from the
 * same filter maths the audio uses, over 31 Hz to 16 kHz on a log scale. The live 64-band FFT RTA
 * sits behind it; each band shows as a dot. The plot is inset 12 dp on each side, so the x of a
 * frequency here is the x of the same frequency in the band area under the card (graphic faders sit
 * exactly under their dots). A 24 dp axis row under the plot prints 31 / 250 / 2k / 16k.
 */
@Composable
internal fun EqGraph(
  settings: EqSettings,
  selectedBand: Int,
  spectrumBars: () -> List<Float>,
  isPlaying: Boolean,
  modifier: Modifier = Modifier.height(Grid.u192),
  rtaFrame: () -> com.example.data.audio.dsp.RtaFrame = { com.example.data.audio.dsp.RtaFrame.FLAT },
  onSelectBand: ((Int) -> Unit)? = null,
  onUpdateBand: ((Int, (EqBand) -> EqBand) -> Unit)? = null,
  onAddBand: ((Float, Float) -> Unit)? = null
) {
  val colors = MaterialTheme.colorScheme
  val curveKey = settings.responseKey()
  // Double-buffered 128-point response curve evaluated via precomputed 64-bit trig LUT:
  // 0 FloatArray allocations and 0 cos()/sin() calls per drag frame.
  val targetBufs = remember { arrayOf(FloatArray(CURVE_POINTS), FloatArray(CURVE_POINTS)) }
  val targetSlot = remember { intArrayOf(0) }
  val target = remember(curveKey) {
    val slot = targetSlot[0] xor 1
    targetSlot[0] = slot
    val out = targetBufs[slot]
    EqDsp.responseCurveInto(curveKey, CURVE_FREQS, out, subtractAutomaticPreamp = true)
    out
  }
  // The fone correction alone, drawn as a dashed guide under the curve.
  val deviceCurve = remember(settings.device) {
    settings.device?.let { d ->
      val alone = EqSettings(bands = emptyList(), device = d, autoPreamp = false)
      val out = FloatArray(CURVE_POINTS)
      EqDsp.responseCurveInto(alone, CURVE_FREQS, out, subtractAutomaticPreamp = true)
      for (i in out.indices) out[i] -= d.preampDb
      out
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
      morph.animateTo(1f, spring(dampingRatio = 1f, stiffness = 360f))
    }
  }

  val accent = colors.primary
  // In parametric mode each band has its colour, and its own curve is drawn under the total.
  val bg = colors.background
  val bandTints = remember(settings.bands.size, accent, bg) {
    val set = BandColors.tones(accent, bg, onDark = bg.luminance() < 0.5f)
    List(settings.bands.size) { set[Math.floorMod(it, set.size)] }
  }
  // Incremental per-band cache: dragging 1 band only recomputes that band's biquad response,
  // reusing cached curves for all other N-1 bands for rock-solid 120 Hz headroom.
  val bandCurveCache = remember { HashMap<EqBand, FloatArray>() }
  val bandCurves = remember(curveKey) {
    if (!curveKey.parametric) {
      bandCurveCache.clear()
      emptyList()
    } else {
      val activeKeys = HashSet<EqBand>(curveKey.bands.size * 2)
      val result = curveKey.bands.map { b ->
        if (!b.enabled || (b.type.usesGain && kotlin.math.abs(b.gainDb) < 0.05f)) null
        else {
          activeKeys.add(b)
          bandCurveCache.getOrPut(b) {
            val sections = if (b.type.usesSlope && b.slope != com.example.data.model.FilterSlope.DB_12) {
              com.example.data.audio.dsp.BiquadCoefficients.designSlope(b.type, b.freqHz.toDouble(), b.q.toDouble(), b.slope, 48000.0)
            } else {
              listOf(com.example.data.audio.dsp.BiquadCoefficients.design(b.type, b.freqHz.toDouble(), b.gainDb.toDouble(), b.q.toDouble(), 48000.0))
            }
            FloatArray(CURVE_POINTS) { i ->
              val f = xToFreq(i / (CURVE_POINTS - 1f)).toDouble()
              var db = 0.0
              for (s in sections) db += s.magnitudeDb(f, 48000.0)
              db.toFloat()
            }
          }
        }
      }
      if (bandCurveCache.size > activeKeys.size + 8) {
        bandCurveCache.keys.retainAll(activeKeys)
      }
      result
    }
  }
  val grid = colors.onSurface.copy(alpha = 0.08f)
  val zero = colors.onSurface.copy(alpha = 0.24f)
  val label = colors.onSurfaceVariant
  val line = colors.onSurface
  val dim = if (settings.enabled) 1f else 0.38f

  // What the curve does, in words: its highest boost and deepest cut, scanned directly from the
  // already-evaluated [target] array (0 extra trig/pow calls and 0 list/Pair allocations).
  val res = LocalContext.current.resources
  val summary = remember(curveKey, res) {
    val autoShift = EqDsp.totalPreampDb(curveKey) - curveKey.preampDb
    var highDb = Float.NEGATIVE_INFINITY
    var highHz = 1000f
    var lowDb = Float.POSITIVE_INFINITY
    var lowHz = 1000f
    for (i in target.indices) {
      val db = target[i] + autoShift
      val hz = CURVE_FREQS[i].toFloat()
      if (db > highDb) { highDb = db; highHz = hz }
      if (db < lowDb) { lowDb = db; lowHz = hz }
    }
    when {
      highDb < 0.25f && lowDb > -0.25f -> res.getString(R.string.audio_curve_flat)
      else -> buildString {
        if (highDb >= 0.25f) append(res.getString(R.string.audio_curve_peak_gain, formatDb(highDb), spokenHz(highHz)))
        if (lowDb <= -0.25f) append(res.getString(R.string.audio_curve_peak_cut, formatDb(lowDb), spokenHz(lowHz)))
      }.trim()
    }
  }
  val curveDescription = stringResource(R.string.audio_curve_card_description, summary)
  val shape = com.example.ui.theme.SquircleShape(Grid.u24)
  // Reused across spectrum redraws so drawing allocates zero Path objects per frame.
  val guidePath = remember { Path() }
  val bandPath = remember { Path() }
  val curvePath = remember { Path() }
  val fillPath = remember { Path() }
  val rtaLinePath = remember { Path() }
  val rtaFillPath = remember { Path() }
  // Pre-allocated Strokes & gradient colors so 60/120 Hz Canvas frames never allocate them per frame.
  val density = androidx.compose.ui.platform.LocalDensity.current
  val hairlineStroke = remember(density) { Stroke(width = with(density) { Grid.hairline.toPx() }, cap = StrokeCap.Round) }
  val glowStroke = remember(density) { Stroke(width = with(density) { Grid.u6.toPx() }, cap = StrokeCap.Round) }
  val coreStroke = remember(density) { Stroke(width = with(density) { Grid.u6.toPx() / 3f }, cap = StrokeCap.Round) }
  val curveGradColors = remember(line, dim) {
    listOf(line.copy(alpha = 0.14f * dim), line.copy(alpha = 0.02f * dim), line.copy(alpha = 0.12f * dim))
  }
  val curveGradBrushCache = remember(curveGradColors) { arrayOfNulls<Pair<Float, Brush>>(1) }
  val latestSettings = rememberUpdatedState(settings)
  val latestCurveKey = rememberUpdatedState(curveKey)
  val latestSelected = rememberUpdatedState(selectedBand)
  val latestSelect = rememberUpdatedState(onSelectBand)
  val latestUpdate = rememberUpdatedState(onUpdateBand)
  val latestAdd = rememberUpdatedState(onAddBand)
  val haptic = LocalHapticFeedback.current
  val textMeasurer = rememberTextMeasurer()
  val nodeNumberStyle = remember(colors.surface) { EqType.MonoStrong.copy(color = colors.surface) }
  val nodeNumberLayouts = remember(textMeasurer, nodeNumberStyle) {
    Array(32) { idx -> textMeasurer.measure((idx + 1).toString(), style = nodeNumberStyle) }
  }
  val interactiveCurve = settings.parametric && settings.enabled && onUpdateBand != null
  val lastTap = remember { longArrayOf(0L) }
  val lastTapBand = remember { intArrayOf(-2) }
  var draggingBand by remember { mutableIntStateOf(-1) }
  var frozenRta by remember { mutableStateOf<com.example.data.audio.dsp.RtaFrame?>(null) }

  val curveGesture = if (!interactiveCurve) {
    // Even when read-only (graphic mode or EQ off), consume horizontal drags inside the graph card
    // so touching/swiping on the plot never accidentally triggers the parent tab-swipe gesture.
    Modifier.pointerInput(Unit) {
      awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        while (true) {
          val event = awaitPointerEvent()
          val pressed = event.changes.filter { it.pressed }
          if (pressed.isEmpty()) break
          pressed.forEach { p ->
            val d = p.position - p.previousPosition
            if (kotlin.math.abs(d.x) > kotlin.math.abs(d.y)) p.consume()
          }
        }
      }
    }
  } else Modifier.pointerInput(Unit) {
    val insetPx = Grid.inset.toPx()
    val hitRadiusPx = Grid.u24.toPx() * 1.4f
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      val w = (size.width - insetPx * 2f).coerceAtLeast(1f)
      val h = size.height.toFloat().coerceAtLeast(1f)
      val s = latestSettings.value
      val ck = latestCurveKey.value

      // Find nearest parametric band node within hit radius.
      var hitIndex = -1
      var bestDist = hitRadiusPx
      for (i in s.bands.indices) {
        val pos = nodePosition(ck, i, w, h)
        val dx = (down.position.x - insetPx) - pos.x
        val dy = down.position.y - pos.y
        val dist = kotlin.math.hypot(dx, dy)
        if (dist < bestDist) {
          bestDist = dist
          hitIndex = i
        }
      }

      val now = android.os.SystemClock.uptimeMillis()
      val isDoubleTap = (now - lastTap[0]) < 320L && lastTapBand[0] == hitIndex
      lastTap[0] = now
      lastTapBand[0] = hitIndex

      if (isDoubleTap) {
        lastTap[0] = 0L
        // Reset continuous guard so double-tap smoothly morphs the curve & node to 0 dB.
        lastChange[0] = 0L
        if (hitIndex >= 0) {
          latestUpdate.value?.invoke(hitIndex) { it.copy(gainDb = 0f, dynamicRangeDb = 0f) }
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } else {
          val xNorm = ((down.position.x - insetPx) / w).coerceIn(0f, 1f)
          val freq = xToFreq(xNorm).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ)
          val gain = yToDb(down.position.y, h).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)
          latestAdd.value?.invoke(freq, snap(gain))
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        return@awaitEachGesture
      }

      if (hitIndex >= 0) {
        latestSelect.value?.invoke(hitIndex)
      }
      val activeBand = if (hitIndex >= 0) hitIndex else latestSelected.value
      var moved = false
      var lastSign = if (hitIndex >= 0) kotlin.math.sign(s.bands.getOrNull(hitIndex)?.gainDb ?: 0f) else 0f

      try {
        while (true) {
          val event = awaitPointerEvent()
          val pressed = event.changes.filter { it.pressed }
          if (pressed.isEmpty()) break

          if (pressed.size >= 2 && activeBand >= 0) {
            // Two-finger pinch adjusts Q (spreading fingers widens bell / lowers Q).
            val zoom = event.calculateZoom()
            if (zoom != 1f && zoom.isFinite()) {
              moved = true
              draggingBand = activeBand
              pressed.forEach { it.consume() }
              latestUpdate.value?.invoke(activeBand) { b ->
                b.copy(q = (b.q / zoom).coerceIn(EqBand.MIN_Q, EqBand.MAX_Q))
              }
            }
          } else if (hitIndex >= 0) {
            val p = pressed.first()
            val delta = p.position - p.previousPosition
            if (moved || kotlin.math.hypot(delta.x, delta.y) > 2f) {
              moved = true
              draggingBand = hitIndex
              p.consume()
              val xNorm = ((p.position.x - insetPx) / w).coerceIn(0f, 1f)
              val newFreq = xToFreq(xNorm).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ)
              val rawGain = yToDb(p.position.y, h).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)
              val snappedGain = if (kotlin.math.abs(rawGain) < 0.35f) 0f else snap(rawGain)
              val newSign = kotlin.math.sign(snappedGain)
              if (newSign != lastSign && (snappedGain == 0f || (lastSign != 0f && newSign != 0f))) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              }
              lastSign = newSign
              latestUpdate.value?.invoke(hitIndex) { b ->
                if (b.type.usesGain) b.copy(freqHz = newFreq, gainDb = snappedGain)
                else b.copy(freqHz = newFreq)
              }
            }
          } else {
            // Dragging empty space inside the graph: consume horizontal movement so it never switches tabs.
            pressed.forEach { p ->
              val d = p.position - p.previousPosition
              if (kotlin.math.abs(d.x) > kotlin.math.abs(d.y)) p.consume()
            }
          }
        }
      } finally {
        draggingBand = -1
      }
    }
  }

  Column(
    modifier = modifier
      .clip(shape)
      .background(colors.surfaceContainer)
      // Drawn inside the box: the outline never changes the size.
      .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), shape)
      .semantics { contentDescription = curveDescription }
  ) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f).then(curveGesture)) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        // The plot is inset 12 dp each side: the same x as the faders under the card.
        inset(horizontal = Grid.inset.toPx(), vertical = 0f) {
          val w = size.width
          val h = size.height

          // Grid: 0 dB, ±6 and ±12 dB; the 250 Hz and 2 kHz marks of the axis row
          for (db in GRID_DB) drawLine(grid, Offset(0f, dbToY(db, h)), Offset(w, dbToY(db, h)), 1f)
          drawLine(zero, Offset(0f, dbToY(0f, h)), Offset(w, dbToY(0f, h)), Grid.hairline.toPx())
          for (f in GRID_FREQS) {
            val x = freqToX(f) * w
            drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
          }

          // Live 64-band FFT RTA behind the curve (or frozen snapshot when Freeze is active).
          val rta = frozenRta ?: rtaFrame()
          if ((isPlaying || frozenRta != null) && (rta.pre.isNotEmpty() || rta.post.isNotEmpty())) {
            fun drawRta(bins: FloatArray, strokeColor: androidx.compose.ui.graphics.Color, fillColor: androidx.compose.ui.graphics.Color?) {
              if (bins.size < 2) return
              rtaLinePath.rewind()
              val step = w / (bins.size - 1f)
              for (i in bins.indices) {
                val x = i * step
                val y = h - (bins[i].coerceIn(0f, 1f) * h * 0.90f)
                if (i == 0) rtaLinePath.moveTo(x, y) else rtaLinePath.lineTo(x, y)
              }
              if (fillColor != null) {
                rtaFillPath.rewind()
                rtaFillPath.addPath(rtaLinePath)
                rtaFillPath.lineTo(w, h)
                rtaFillPath.lineTo(0f, h)
                rtaFillPath.close()
                drawPath(rtaFillPath, fillColor)
              }
              drawPath(rtaLinePath, strokeColor, style = hairlineStroke)
            }
            if (rta.pre.isNotEmpty()) {
              drawRta(rta.pre, label.copy(alpha = 0.40f), null)
            }
            if (rta.post.isNotEmpty()) {
              drawRta(rta.post, accent.copy(alpha = 0.45f), accent.copy(alpha = 0.12f))
            }
          } else {
            val bars = spectrumBars()
            if (isPlaying && bars.isNotEmpty()) {
              val bw = w / bars.size
              for (i in bars.indices) {
                val bh = (bars[i] * h * 0.85f).coerceAtLeast(2f)
                drawRect(
                  color = accent.copy(alpha = 0.10f),
                  topLeft = Offset(i * bw + bw * 0.15f, h - bh),
                  size = Size(bw * 0.7f, bh)
                )
              }
            }
          }

          deviceCurve?.let { dc ->
            guidePath.rewind()
            for (i in dc.indices) {
              val x = i / (CURVE_POINTS - 1f) * w
              val y = dbToY(dc[i].coerceIn(-GRAPH_DB_RANGE - 3f, GRAPH_DB_RANGE + 3f), h)
              if (i == 0) guidePath.moveTo(x, y) else guidePath.lineTo(x, y)
            }
            drawPath(
              guidePath,
              label.copy(alpha = 0.6f * dim),
              style = Stroke(width = Grid.hairline.toPx(), pathEffect = DEVICE_DASH)
            )
          }

          // Each parametric band's own curve, in its colour, under the total; selected band gets a translucent fill.
          for (bi in bandCurves.indices) {
            val curve = bandCurves[bi] ?: continue
            val isSel = bi == selectedBand
            bandPath.rewind()
            for (i in curve.indices) {
              val x = i / (CURVE_POINTS - 1f) * w
              val y = dbToY(curve[i].coerceIn(-GRAPH_DB_RANGE - 3f, GRAPH_DB_RANGE + 3f), h)
              if (i == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
            }
            if (isSel) {
              fillPath.rewind()
              fillPath.addPath(bandPath)
              fillPath.lineTo(w, dbToY(0f, h))
              fillPath.lineTo(0f, dbToY(0f, h))
              fillPath.close()
              drawPath(fillPath, bandTints[bi].copy(alpha = 0.16f * dim))
            }
            drawPath(
              bandPath,
              bandTints[bi].copy(alpha = (if (isSel) 0.90f else 0.55f) * dim),
              style = Stroke(width = if (isSel) Grid.hairline.toPx() * 1.5f else Grid.hairline.toPx(), cap = StrokeCap.Round)
            )
          }

          // Response curve with a luminous vertical gradient fill towards 0 dB (T.A.L. Studio finish)
          val p = morph.value
          for (i in shown.indices) shown[i] = from[i] + (target[i] - from[i]) * p
          curvePath.rewind()
          for (i in shown.indices) {
            val x = i / (CURVE_POINTS - 1f) * w
            val y = dbToY(shown[i].coerceIn(-GRAPH_DB_RANGE - 3f, GRAPH_DB_RANGE + 3f), h)
            if (i == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)
          }
          fillPath.rewind()
          fillPath.addPath(curvePath)
          fillPath.lineTo(w, dbToY(0f, h))
          fillPath.lineTo(0f, dbToY(0f, h))
          fillPath.close()
          val curveBrush = curveGradBrushCache[0]?.takeIf { it.first == h }?.second
            ?: Brush.verticalGradient(colors = curveGradColors, startY = 0f, endY = h).also {
              curveGradBrushCache[0] = h to it
            }
          drawPath(fillPath, curveBrush)
          // Subtle soft glow under the main curve line + crisp core stroke.
          drawPath(curvePath, line.copy(alpha = 0.18f * dim), style = glowStroke)
          drawPath(curvePath, line.copy(alpha = dim), style = coreStroke)

          // Crosshair guides while dragging a parametric node.
          if (draggingBand in settings.bands.indices) {
            val dragPos = nodePosition(curveKey, draggingBand, w, h)
            drawLine(accent.copy(alpha = 0.35f), Offset(dragPos.x, 0f), Offset(dragPos.x, h), Grid.hairline.toPx())
            drawLine(accent.copy(alpha = 0.35f), Offset(0f, dragPos.y), Offset(w, dragPos.y), Grid.hairline.toPx())
          }

          // Band nodes: in parametric mode numbered nodes with elastic Q halo & specular ring; in graphic mode clean dots.
          settings.bands.forEachIndexed { i, band ->
            val pos = nodePosition(curveKey, i, w, h)
            val isSel = i == selectedBand
            val isDrag = i == draggingBand
            if (settings.parametric) {
              val nodeColor = (if (band.enabled) (if (isSel) accent else bandTints.getOrElse(i) { line }) else label).copy(alpha = dim)
              val r = if (isSel) Grid.u12.toPx() * 0.85f else Grid.u6.toPx() * 1.15f
              if (isSel || isDrag) {
                // Live Q-width elastic halo: wider bell (lower Q) expands the outer ring smoothly.
                val qFactor = (1.6f / band.q.coerceIn(0.3f, 8f)).coerceIn(0.5f, 2.2f)
                val haloR = if (isDrag) r * (1.85f + 0.55f * qFactor) else r * 1.65f
                drawCircle(nodeColor.copy(alpha = if (isDrag) 0.34f else 0.18f), radius = haloR, center = pos)
                if (isDrag) {
                  drawCircle(
                    color = nodeColor.copy(alpha = 0.65f),
                    radius = haloR,
                    center = pos,
                    style = Stroke(width = Grid.hairline.toPx())
                  )
                }
              }
              drawCircle(nodeColor, radius = r, center = pos)
              drawCircle(
                color = colors.onSurface.copy(alpha = if (isSel) 0.85f * dim else 0.35f * dim),
                radius = r,
                center = pos,
                style = Stroke(width = Grid.hairline.toPx())
              )
              if (isSel || settings.bands.size <= 12) {
                val layout = nodeNumberLayouts.getOrNull(i) ?: textMeasurer.measure((i + 1).toString(), style = nodeNumberStyle)
                drawText(
                  textLayoutResult = layout,
                  topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f)
                )
              }
            } else {
              if (isSel) {
                drawCircle((if (band.enabled) accent else label).copy(alpha = dim), radius = EqSize.nodeSelected.toPx(), center = pos)
              } else {
                drawCircle((if (band.enabled) line else label).copy(alpha = 0.55f * dim), radius = EqSize.knobInset.toPx(), center = pos)
              }
            }
          }
        }
      }
      // Floating translucent HUD pill above the finger while dragging a parametric node.
      val dragBand = settings.bands.getOrNull(draggingBand)
      if (settings.parametric && dragBand != null) {
        val hudText = buildString {
          append("#").append(draggingBand + 1)
          append(" · ").append(formatHz(dragBand.freqHz))
          if (dragBand.type.usesGain) append(" · ").append(formatDb(dragBand.gainDb)).append(" dB")
          append(" · Q ").append(formatQ(dragBand.q))
        }
        Box(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = Grid.u6)
            .clip(Grid.r12)
            .background(colors.surface.copy(alpha = 0.88f))
            .border(Grid.hairline, accent.copy(alpha = 0.50f), Grid.r12)
            .padding(horizontal = Grid.u12, vertical = Grid.u6)
        ) {
          Text(hudText, style = EqType.MonoStrong, color = colors.onSurface, maxLines = 1)
        }
      }
      Text(
        "+${GRAPH_DB_RANGE.toInt()}",
        style = EqType.Mono,
        color = label,
        modifier = Modifier.align(Alignment.TopStart).offset(x = Grid.inset, y = Grid.u6 / 2)
      )
      Text(
        "0",
        style = EqType.Mono,
        color = label,
        modifier = Modifier.align(Alignment.CenterStart).offset(x = Grid.inset, y = -Grid.u12)
      )
      // RTA Peak Hz readout + Freeze button in the top-right corner when RTA is active.
      val shownRta = frozenRta ?: rtaFrame()
      if ((isPlaying || frozenRta != null) && (shownRta.pre.isNotEmpty() || shownRta.post.isNotEmpty())) {
        val isFrozen = frozenRta != null
        val badgeText = buildString {
          if (shownRta.peakHz >= 20f) {
            append(formatHz(shownRta.peakHz)).append(" · ")
          }
          append(stringResource(if (isFrozen) R.string.audio_rta_frozen else R.string.audio_rta_freeze))
        }
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = Grid.u6, end = Grid.inset)
            .clip(Grid.r6)
            .background(if (isFrozen) accent.copy(alpha = 0.18f) else colors.surface.copy(alpha = 0.72f))
            .clickable { frozenRta = if (isFrozen) null else rtaFrame() }
            .padding(horizontal = Grid.u6, vertical = Grid.u6 / 2)
            .testTag("rta_freeze_chip")
        ) {
          Text(
            text = badgeText,
            style = EqType.Mono,
            color = if (isFrozen) accent else label,
            maxLines = 1
          )
        }
      }
    }
    // The axis row: 31 / 250 / 2k / 16k, each centred on its x in the plot, except the two ends: the
    // first starts at the 12 dp inset and the last ends at the right inset, so neither leaves the card.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(Grid.u24)) {
      val plot = maxWidth - Grid.inset * 2
      val ticks = listOf("31" to 31f, "250" to 250f, "2k" to 2000f, "16k" to 16000f)
      ticks.forEachIndexed { position, (text, f) ->
        val first = position == 0
        val last = position == ticks.size - 1
        val x = when {
          first -> Grid.inset
          last -> maxWidth - Grid.inset - Grid.u48
          else -> Grid.inset + plot * freqToX(f) - Grid.u24
        }
        Text(
          text,
          style = EqType.Mono,
          color = label,
          textAlign = if (first) TextAlign.Start else if (last) TextAlign.End else TextAlign.Center,
          maxLines = 1,
          modifier = Modifier
            .align(Alignment.CenterStart)
            .offset(x = x)
            .requiredWidth(Grid.u48)
        )
      }
    }
  }
}

/** Where band [i] of [s] (a [responseKey]) is drawn: at its gain for peaks and shelves, on the curve for passes. */
private fun nodePosition(s: EqSettings, i: Int, w: Float, h: Float): Offset {
  val b = s.bands[i]
  val x = freqToX(b.freqHz) * w
  val db = if (b.type.usesGain) b.gainDb
    else EqDsp.responseDb(s, b.freqHz.toDouble()).toFloat() - (EqDsp.totalPreampDb(s) - s.preampDb)
  return Offset(x, dbToY(db.coerceIn(-GRAPH_DB_RANGE, GRAPH_DB_RANGE), h))
}
