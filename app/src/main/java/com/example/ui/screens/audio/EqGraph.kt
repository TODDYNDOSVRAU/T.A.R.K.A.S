package com.example.ui.screens.audio

import com.example.ui.theme.Size as ThemeSize
import com.example.ui.theme.EqSize
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqSettings
import com.example.ui.theme.Grid
import com.example.ui.theme.rememberReducedMotion

/** Points along the frequency axis. Enough for a smooth curve at phone width, few enough to redo per frame. */
private const val CURVE_POINTS = 128

/** The curve card: 168 dp of plot and a 24 dp axis row, 192 dp in all. */
internal val CURVE_PLOT_HEIGHT = Grid.u192 - Grid.u24

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

/**
 * The real frequency response of the whole chain (bands, tone shelves and preamp), computed from the
 * same filter maths the audio uses, over 20 Hz to 20 kHz on a log scale. The live spectrum sits behind
 * it; each band shows as a dot, for reading only. The plot is inset 12 dp on each side, so the x of a
 * frequency here is the x of the same frequency in the band area under the card (graphic faders sit
 * exactly under their dots). A 24 dp axis row under the plot prints 20 / 200 / 2k / 20k.
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
  val nodeFill = colors.surfaceContainer
  val dim = if (settings.enabled) 1f else 0.38f

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
  val curveDescription = stringResource(R.string.audio_curve_card_description, summary)
  val shape = Grid.r24
  Column(
    modifier = modifier
      .height(Grid.u192)
      .clip(shape)
      .background(colors.surfaceContainer)
      // Drawn inside the box: the outline never changes the size.
      .border(ThemeSize.hairline, colors.onSurface.copy(alpha = 0.08f), shape)
      .semantics { contentDescription = curveDescription }
  ) {
    Box(modifier = Modifier.fillMaxWidth().height(CURVE_PLOT_HEIGHT)) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        // The plot is inset 12 dp each side: the same x as the faders under the card.
        inset(horizontal = Grid.inset.toPx(), vertical = 0f) {
          val w = size.width
          val h = size.height

          // Grid: 0 dB, ±6 and ±12 dB; the 200 Hz and 2 kHz marks of the axis row
          listOf(-12f, -6f, 6f, 12f).forEach { db -> drawLine(grid, Offset(0f, dbToY(db, h)), Offset(w, dbToY(db, h)), 1f) }
          drawLine(zero, Offset(0f, dbToY(0f, h)), Offset(w, dbToY(0f, h)), ThemeSize.hairline.toPx())
          listOf(200f, 2000f).forEach { f ->
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
              style = Stroke(width = ThemeSize.hairline.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
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
            drawPath(bp, bandTints[bi].copy(alpha = 0.7f * dim), style = Stroke(width = ThemeSize.hairline.toPx(), cap = StrokeCap.Round))
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
          drawPath(path, accent.copy(alpha = dim), style = Stroke(width = Grid.u6.toPx() / 3f, cap = StrokeCap.Round))

          // Band nodes: the selected one filled with the accent
          settings.bands.forEachIndexed { i, band ->
            val pos = nodePosition(curveKey, i, w, h)
            val isSel = i == selectedBand
            val r = if (isSel) EqSize.nodeSelected.toPx() else Grid.u6.toPx()
            val c = (if (band.enabled) bandTints.getOrElse(i) { accent } else label).copy(alpha = dim)
            if (isSel) {
              drawCircle(c, radius = r, center = pos)
            } else {
              drawCircle(nodeFill, radius = r, center = pos)
              drawCircle(c, radius = r, center = pos, style = Stroke(width = EqSize.lineFine.toPx()))
            }
          }
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
        modifier = Modifier.align(Alignment.CenterStart).offset(x = Grid.inset)
      )
    }
    // The axis row: 20 / 200 / 2k / 20k, each centred on its x in the plot, except the two ends: the
    // first starts at the 12 dp inset and the last ends at the right inset, so neither leaves the card.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(Grid.u24)) {
      val plot = maxWidth - Grid.inset * 2
      val ticks = listOf("20" to 20f, "200" to 200f, "2k" to 2000f, "20k" to 20000f)
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
