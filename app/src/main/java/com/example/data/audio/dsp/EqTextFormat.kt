package com.example.data.audio.dsp

import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/**
 * The plain-text curve formats other equalizers read and write, so a curve moves in and out of the app
 * as text:
 * - Equalizer APO / AutoEq "ParametricEQ.txt": `Preamp: -6.1 dB` and `Filter 1: ON PK Fc 105 Hz Gain 6.4 dB Q 0.70`;
 * - "GraphicEQ: 20 -1; 25 2; …": frequency/gain points.
 * Pure functions: no Android types, tested on the JVM.
 */
object EqTextFormat {

  /** What a text became: parametric bands (APO), or the graphic bands (GraphicEQ points laid onto them). */
  sealed interface Result {
    data class Parametric(val bands: List<EqBand>, val preampDb: Float) : Result
    data class Graphic(val gainsDb: List<Float>, val points: Int) : Result
  }

  /** At most this many filters are read from an APO text. */
  const val MAX_FILTERS = 64
  /** A GraphicEQ text needs at least this many points to describe a curve. */
  const val MIN_GRAPHIC_POINTS = 5

  private val preampLine = Regex("""Preamp:\s*(-?[\d.]+)\s*dB""", RegexOption.IGNORE_CASE)
  private val filterLine = Regex(
    """Filter\s*\d*:\s*(ON|OFF)\s+(\w+)\s+Fc\s+([\d.]+)\s*(?:Hz)?(?:\s+Gain\s+(-?[\d.]+)\s*(?:dB)?)?(?:\s+Q\s+([\d.]+))?""",
    RegexOption.IGNORE_CASE
  )
  private val graphicLine = Regex("""GraphicEQ:\s*(.+)""", RegexOption.IGNORE_CASE)

  private val decimalComma = Regex("""(\d),(\d)""")

  /** Texts from a comma-decimal locale ("-6,1 dB") read the same as with a point; "+" signs are dropped. */
  private fun normalize(text: String): String = text.replace(decimalComma, "$1.$2").replace(Regex("""\+(?=[\d.])"""), "")

  fun parse(text: String): Result? = normalize(text).let { parseGraphic(it) ?: parseParametric(it) }

  private fun parseParametric(text: String): Result.Parametric? {
    val bands = filterLine.findAll(text).take(MAX_FILTERS).mapNotNull { m ->
      val type = when (m.groupValues[2].uppercase()) {
        "PK", "PEQ" -> FilterType.PEAK
        "LSC", "LS", "LSQ" -> FilterType.LOW_SHELF
        "HSC", "HS", "HSQ" -> FilterType.HIGH_SHELF
        "HP", "HPQ" -> FilterType.HIGH_PASS
        "LP", "LPQ" -> FilterType.LOW_PASS
        else -> return@mapNotNull null
      }
      EqBand(
        freqHz = (m.groupValues[3].toFloatOrNull() ?: return@mapNotNull null).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ),
        gainDb = (m.groupValues[4].toFloatOrNull() ?: 0f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN),
        q = (m.groupValues[5].toFloatOrNull() ?: 0.7f).coerceIn(EqBand.MIN_Q, EqBand.MAX_Q),
        type = type,
        enabled = m.groupValues[1].equals("ON", ignoreCase = true)
      )
    }.toList()
    if (bands.isEmpty()) return null
    val preamp = preampLine.find(text)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    return Result.Parametric(bands, preamp.coerceIn(-EqSettings.PREAMP_RANGE_DB, EqSettings.PREAMP_RANGE_DB))
  }

  private fun parseGraphic(text: String): Result.Graphic? {
    val body = graphicLine.find(text)?.groupValues?.get(1) ?: return null
    val points = body.split(';').mapNotNull { part ->
      val n = part.trim().split(Regex("\\s+"))
      if (n.size != 2) return@mapNotNull null
      val f = n[0].toFloatOrNull() ?: return@mapNotNull null
      val g = n[1].toFloatOrNull() ?: return@mapNotNull null
      if (!f.isFinite() || f <= 0f || !g.isFinite()) null else f to g
    }.sortedBy { it.first }
    if (points.size < MIN_GRAPHIC_POINTS) return null
    // Each graphic band takes the curve at its frequency, interpolated on a log-frequency axis.
    val gains = EqSettings.DEFAULT_FREQUENCIES.map { f ->
      interpolate(points, f).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)
    }
    return Result.Graphic(gains, points.size)
  }

  private fun interpolate(points: List<Pair<Float, Float>>, f: Float): Float {
    if (f <= points.first().first) return points.first().second
    if (f >= points.last().first) return points.last().second
    val i = points.indexOfFirst { it.first >= f }
    val (f0, g0) = points[i - 1]
    val (f1, g1) = points[i]
    val t = (ln(f) - ln(f0)) / (ln(f1) - ln(f0))
    return g0 + (g1 - g0) * t
  }

  /** The curve in use as an APO / AutoEq text, preamp included. Gains below 0.05 dB are written as they are. */
  fun export(s: EqSettings): String = buildString {
    val bands = s.activeBands.filter { !it.type.usesGain || abs(it.gainDb) >= 0.0005f }
    val preamp = s.preampDb + (s.device?.preampDb ?: 0f)
    append("Preamp: ").append(fmt(preamp)).append(" dB\n")
    var n = 1
    fun line(b: EqBand) {
      val code = when (b.type) {
        FilterType.PEAK -> "PK"
        FilterType.LOW_SHELF -> "LSC"
        FilterType.HIGH_SHELF -> "HSC"
        FilterType.HIGH_PASS -> "HP"
        FilterType.LOW_PASS -> "LP"
      }
      append("Filter ").append(n++).append(": ").append(if (b.enabled) "ON " else "OFF ").append(code)
        .append(" Fc ").append(fmt(b.freqHz)).append(" Hz")
      if (b.type.usesGain) append(" Gain ").append(fmt(b.gainDb)).append(" dB")
      append(" Q ").append(fmt(b.q)).append('\n')
    }
    s.device?.bands?.forEach(::line)
    bands.forEach(::line)
    if (abs(s.bassDb) >= 0.05f) line(EqBand(s.bassFreqHz, s.bassDb, 0.707f, FilterType.LOW_SHELF))
    if (abs(s.trebleDb) >= 0.05f) line(EqBand(s.trebleFreqHz, s.trebleDb, 0.707f, FilterType.HIGH_SHELF))
  }

  private fun fmt(v: Float): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

  /** Settings with [result] applied: parametric bands switch the parametric mode on, GraphicEQ the graphic one. */
  fun apply(base: EqSettings, result: Result): EqSettings = when (result) {
    // Export writes bass/treble as shelf filters, so they come back as bands; keeping them too would apply them twice.
    is Result.Parametric -> base.copy(
      parametric = true, paramBands = result.bands, preampDb = result.preampDb, device = null,
      bassDb = 0f, trebleDb = 0f
    )
    is Result.Graphic -> base.copy(
      parametric = false,
      bands = EqSettings.DEFAULT_FREQUENCIES.mapIndexed { i, f -> EqBand(freqHz = f, gainDb = result.gainsDb[i]) }
    )
  }
}
