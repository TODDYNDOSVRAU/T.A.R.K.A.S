package com.example.windows.equalizador

import com.example.windows.res.Res
import com.example.windows.res.tr
import com.example.windows.res.trNow
import com.example.windows.res.audio_curve_card_description
import com.example.windows.res.audio_curve_flat
import com.example.windows.res.audio_curve_peak_cut
import com.example.windows.res.audio_curve_peak_gain
import org.jetbrains.compose.resources.stringResource
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqSettings
import com.example.ui.theme.Grid
import com.example.windows.equalizador.rememberReducedMotion
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode
/** Points along the frequency axis. Enough for a smooth curve at phone width, few enough to redo per frame. */
internal const val CURVE_POINTS = 128

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
  virtualizerStrength = 0
)

/** Horizontal inset of the curve plot and of the graphic faders: they share it so a fader sits under its dot. */
internal val PLOT_INSET = Grid.u24

/**
 * The real frequency response of the whole chain (bands, tone shelves and preamp), computed from the
 * same filter maths the audio uses, over 31 Hz to 16 kHz on a log scale. The live spectrum sits behind
 * it; each band shows as a dot, for reading only. The plot is inset 24 dp on each side (PLOT_INSET), so the x of a
 * frequency here is the x of the same frequency in the band area under the card (graphic faders sit
 * exactly under their dots). A 24 dp axis row under the plot prints 31 / 250 / 2k / 16k.
 */
@Composable
internal fun EqGraph(
  settings: EqSettings,
  selectedBand: Int,
  spectrumBars: () -> List<Float>,
  isPlaying: Boolean,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
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
  // Reused every frame: the fill's vertex buffer and its paint (Zero-Allocation in the Canvas).
  val strip = remember { FloatArray(FILL_STRIP_FLOATS) }
  val fillPaint = remember { Paint().apply { isAntiAlias = true } }
  LaunchedEffect(target) {
    val now = System.nanoTime() / 1_000_000L
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
  val bandTints = settings.bands.indices.map { bandColor(it) }
  val bandCurves = remember(curveKey) {
    if (!curveKey.parametric) emptyList() else curveKey.bands.map { b ->
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
  val line = colors.onSurface
  val dim = if (settings.enabled) 1f else 0.38f

  // What the curve does, in words: its highest boost and deepest cut, so a screen reader gets the shape.
  val flatText = stringResource(Res.string.audio_curve_flat)
  val summary = remember(settings) {
    val freqs = (0..40).map { 20.0 * Math.pow(1000.0, it / 40.0) }
    val points = freqs.map { it to EqDsp.responseDb(settings, it) }
    val high = points.maxByOrNull { it.second }!!
    val low = points.minByOrNull { it.second }!!
    when {
      high.second < 0.25 && low.second > -0.25 -> flatText
      else -> buildString {
        if (high.second >= 0.25) append(trNow(Res.string.audio_curve_peak_gain, formatDb(high.second.toFloat()), spokenHz(high.first.toFloat())))
        if (low.second <= -0.25) append(trNow(Res.string.audio_curve_peak_cut, formatDb(low.second.toFloat()), spokenHz(low.first.toFloat())))
      }.trim()
    }
  }
  val curveDescription = tr(Res.string.audio_curve_card_description, summary)
  val measurer = rememberTextMeasurer()
  val shape = Grid.r12
  Column(
    modifier = modifier
      .height(Grid.u192)
      .clip(shape)
      .background(colors.surfaceContainer)
      // Drawn inside the box: the outline never changes the size.
      .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), shape)
      .semantics { contentDescription = curveDescription }
  ) {
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        // The plot is inset 24 dp each side: the same x as the faders under the card.
        inset(horizontal = PLOT_INSET.toPx(), vertical = 0f) {
          val w = size.width
          val h = size.height

          // The curves are clipped to the plot rectangle (a plain scissor). A curve clamped to +-(range + 3) dB
          // still lies outside [0, h]; unclipped it ran over the axis row and met the card's rounded clip, which
          // the GPU rasterises in coverage blocks (the checkerboard seen in the 2026-11-27 video).
          clipRect(0f, 0f, w, h) {

          // Grid: 0 dB, ±6 and ±12 dB; the 250 Hz and 2 kHz marks of the axis row
          listOf(-12f, -6f, 6f, 12f).forEach { db -> drawLine(grid, Offset(0f, dbToY(db, h)), Offset(w, dbToY(db, h)), 1f) }
          drawLine(zero, Offset(0f, dbToY(0f, h)), Offset(w, dbToY(0f, h)), Grid.hairline.toPx())
          listOf(250f, 2000f).forEach { f ->
            val x = freqToX(f) * w
            drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
          }
          // Each gain line says what it is, just above it at the left edge: +12 +6 0 -6 -12 dB.
          val labelStyle = EqType.Mono.copy(color = label)
          // A short plot (the curve at half height) keeps the labels that do not touch another: 0 first, then
          // the ends, then +-6, so the zero line is always named.
          val drawnY = ArrayList<Float>(5)
          listOf(0f, 12f, -12f, 6f, -6f).forEach { db ->
            val text = measurer.measure(formatDb(db), labelStyle, maxLines = 1)
            val y = dbToY(db, h)
            if (drawnY.none { kotlin.math.abs(it - y) < text.size.height + 2f }) {
              drawText(text, topLeft = Offset(Grid.u6.toPx(), y - text.size.height - Grid.hairline.toPx()))
              drawnY += y
            }
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
              style = Stroke(width = Grid.hairline.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
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
            drawPath(bp, bandTints[bi].copy(alpha = 0.7f * dim), style = Stroke(width = Grid.hairline.toPx(), cap = StrokeCap.Round))
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
          // The fill is a triangle strip, not a Path: it never goes through the path-mask rasteriser (see fillStrip).
          fillStrip(shown, w, h, strip)
          drawIntoCanvas { canvas ->
            fillPaint.color = line.copy(alpha = 0.06f * dim).toArgb()
            canvas.nativeCanvas.drawVertices(VertexMode.TRIANGLE_STRIP, strip, null, null, null, BlendMode.SRC_OVER, fillPaint)
          }
          // The curve is drawn in the text colour; the accent is kept for the band that is selected.
          drawPath(path, line.copy(alpha = dim), style = Stroke(width = Grid.u6.toPx() / 3f, cap = StrokeCap.Round))
          }

          // Band nodes: small neutral dots, the selected one larger and in the accent (their y is already
          // limited to +-range, so they stay inside the plot; drawn outside the clip so a band at the very
          // ends of the axis is not cut in half)
          settings.bands.forEachIndexed { i, band ->
            val pos = nodePosition(curveKey, i, w, h)
            val isSel = i == selectedBand
            if (isSel) {
              drawCircle((if (band.enabled) accent else label).copy(alpha = dim), radius = EqSize.nodeSelected.toPx(), center = pos)
            } else {
              drawCircle((if (band.enabled) line else label).copy(alpha = 0.55f * dim), radius = EqSize.knobInset.toPx(), center = pos)
            }
          }
        }
      }
    }
    // The axis row: 31 / 250 / 2k / 16k, each centred on its x in the plot, except the two ends: the
    // first starts at the 12 dp inset and the last ends at the right inset, so neither leaves the card.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(Grid.u24)) {
      val plot = maxWidth - PLOT_INSET * 2
      val ticks = listOf("31" to 31f, "250" to 250f, "2k" to 2000f, "16k" to 16000f)
      ticks.forEachIndexed { position, (text, f) ->
        val first = position == 0
        val last = position == ticks.size - 1
        val x = when {
          first -> PLOT_INSET - Grid.u12
          last -> maxWidth - PLOT_INSET + Grid.u12 - Grid.u48
          else -> PLOT_INSET + plot * freqToX(f) - Grid.u24
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

/** Most vertices the fill strip can hold: one pair per curve point plus one pair per 0 dB crossing. */
internal const val FILL_STRIP_VERTICES = 2 * (2 * CURVE_POINTS - 1)
internal const val FILL_STRIP_FLOATS = FILL_STRIP_VERTICES * 2

/**
 * Writes the fill under the curve [db] (towards 0 dB) as a TRIANGLE_STRIP into [out] (x, y pairs) and returns the
 * number of vertices that carry geometry; the rest of [out] repeats the last vertex (zero-area triangles), because
 * Skia reads the whole array. Each curve point gives a pair (curve, 0 dB). Where the curve crosses 0 dB between two
 * points, a pair of coincident vertices on the zero line is inserted at the crossing, so the strip turns into one
 * triangle above and one below instead of a self-overlapping bow tie: every pixel is covered once, so the fill's
 * low alpha is never blended twice. The y of the curve is limited to +-(range + 3) dB like the stroke; the caller
 * clips to the plot.
 */
internal fun fillStrip(db: FloatArray, w: Float, h: Float, out: FloatArray): Int {
  val zeroY = dbToY(0f, h)
  var n = 0
  var prevDb = 0f
  var prevX = 0f
  for (i in db.indices) {
    val x = i / (db.size - 1f) * w
    val d = db[i]
    if (i > 0 && prevDb * d < 0f) {
      val xc = prevX + (x - prevX) * (prevDb / (prevDb - d))
      n = putVertex(out, n, xc, zeroY)
      n = putVertex(out, n, xc, zeroY)
    }
    n = putVertex(out, n, x, dbToY(d.coerceIn(-GRAPH_DB_RANGE - 3f, GRAPH_DB_RANGE + 3f), h))
    n = putVertex(out, n, x, zeroY)
    prevDb = d
    prevX = x
  }
  val used = n
  val lastX = out[2 * (used - 1)]
  val lastY = out[2 * (used - 1) + 1]
  while (n < FILL_STRIP_VERTICES) n = putVertex(out, n, lastX, lastY)
  return used
}

private fun putVertex(out: FloatArray, n: Int, x: Float, y: Float): Int {
  out[2 * n] = x
  out[2 * n + 1] = y
  return n + 1
}

/** Where band [i] of [s] (a [responseKey]) is drawn: at its gain for peaks and shelves, on the curve for passes. */
private fun nodePosition(s: EqSettings, i: Int, w: Float, h: Float): Offset {
  val b = s.bands[i]
  val x = freqToX(b.freqHz) * w
  val db = if (b.type.usesGain) b.gainDb
    else EqDsp.responseDb(s, b.freqHz.toDouble()).toFloat() - (EqDsp.totalPreampDb(s) - s.preampDb)
  return Offset(x, dbToY(db.coerceIn(-GRAPH_DB_RANGE, GRAPH_DB_RANGE), h))
}
