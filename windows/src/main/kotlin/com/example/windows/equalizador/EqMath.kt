package com.example.windows.equalizador

import com.example.windows.res.Res
import com.example.windows.res.tr
import com.example.windows.res.audio_balance_center
import com.example.windows.res.audio_balance_left
import com.example.windows.res.audio_balance_right
import com.example.windows.res.audio_filter_high_pass
import com.example.windows.res.audio_filter_high_shelf
import com.example.windows.res.audio_filter_low_pass
import com.example.windows.res.audio_filter_low_shelf
import com.example.windows.res.audio_filter_peak
import com.example.windows.res.audio_usage_assistant
import com.example.windows.res.audio_usage_game
import com.example.windows.res.audio_usage_media
import com.example.windows.res.audio_usage_playing
import com.example.windows.res.audio_usage_system
import com.example.windows.res.audio_usage_voice
import com.example.windows.res.audio_width_mono
import com.example.windows.res.audio_width_original
import com.example.data.model.FilterType
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.example.data.model.EqBand
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Pure maths and formatting of the equalizer screen: the log frequency axis, the dB axis of the
 * graph, the knob scales and the text of every readout. Kept apart from the composables so they can
 * be read (and tested) without any UI.
 */

/**
 * Every number on this screen is printed in the language of the interface ("44,1 kHz" in Portuguese,
 * "44.1 kHz" in English), read at the moment of printing so a change of language shows at once. Text typed
 * into a number field accepts both separators, and text meant for the clipboard stays in Locale.US.
 */
internal fun numberLocale(): Locale = Locale.getDefault()

/** The decimal separator of [numberLocale], for trimming "1.0k" to "1k" in either language. */
private fun decimalMark(): String = java.text.DecimalFormatSymbols.getInstance(numberLocale()).decimalSeparator.toString()

/** [v] with [decimals] places, in the interface language. */
internal fun formatNumber(v: Float, decimals: Int): String = String.format(numberLocale(), "%.${decimals}f", v)

internal const val MIN_F = 31f
internal const val MAX_F = 16000f

/** The graph's vertical range: ±15 dB fits every band at full gain. */
internal const val GRAPH_DB_RANGE = 15f

/** Room above and below the curve so a band at full gain is not cut by the graph's edge. */
private const val GRAPH_PAD_PX = 14f

/** Position 0..1 of [f] on the 31 Hz – 16 kHz log axis: the first and last bands sit on its edges. */
internal fun freqToX(f: Float): Float = (log10(f / MIN_F) / log10(MAX_F / MIN_F)).coerceIn(0f, 1f)

/** Frequency at position [x] (0..1) of the log axis. */
internal fun xToFreq(x: Float): Float = MIN_F * (MAX_F / MIN_F).pow(x)

/** Height in a graph [h] pixels tall where [db] is drawn; 0 dB is the middle. */
internal fun dbToY(db: Float, h: Float): Float = h / 2f - db / GRAPH_DB_RANGE * (h / 2f - GRAPH_PAD_PX)

/** The gain drawn at height [y] of a graph [h] pixels tall: the inverse of [dbToY]. */
internal fun yToDb(y: Float, h: Float): Float = (h / 2f - y) / (h / 2f - GRAPH_PAD_PX) * GRAPH_DB_RANGE

/** Q is heard as a ratio, so its knob turns on a log scale: 0..1 over MIN_Q..MAX_Q. */
internal fun qToKnob(q: Float): Float =
  log10(q.coerceIn(EqBand.MIN_Q, EqBand.MAX_Q) / EqBand.MIN_Q) / log10(EqBand.MAX_Q / EqBand.MIN_Q)

internal fun knobToQ(v: Float): Float = EqBand.MIN_Q * (EqBand.MAX_Q / EqBand.MIN_Q).pow(v.coerceIn(0f, 1f))

/** Rounds to 0.5 dB and snaps near zero, so returning a control to flat is easy. */
internal fun snap(db: Float): Float {
  val r = (db * 2f).roundToInt() / 2f
  return if (abs(r) < 0.25f) 0f else r
}

/** "+3.5", "0", "-2": a sign on boosts, and a decimal only when the value is not whole. */
internal fun formatDb(db: Float): String {
  val rounded = (db * 10f).roundToInt() / 10f + 0f // + 0f turns -0.0 into 0.0
  val text = formatNumber(rounded, if (rounded == rounded.roundToInt().toFloat()) 0 else 1)
  return if (rounded > 0f) "+$text" else text
}

/** "63", "1.6k", "16k": the short form printed under a band. */
internal fun formatHz(f: Float): String = when {
  f >= 10000f -> "${(f / 1000f).roundToInt()}k"
  f >= 1000f -> formatNumber(f / 1000f, 1).let { if (it.endsWith("${decimalMark()}0")) it.dropLast(2) else it } + "k"
  else -> "${f.roundToInt()}"
}

/** The same frequency for a screen reader, with its unit spoken. */
internal fun spokenHz(f: Float): String = when {
  f >= 1000f -> formatNumber(f / 1000f, 1).let { if (it.endsWith("${decimalMark()}0")) it.dropLast(2) else it } + " kHz"
  else -> "${f.roundToInt()} Hz"
}

internal fun formatQ(q: Float): String = formatNumber(q, 2).trimEnd('0').trimEnd('.', ',')

/** "48 kHz", "44.1 kHz", "22.05 kHz": a decimal only when the value is not whole. */
internal fun formatSampleRate(hz: Int): String {
  val decimals = when {
    hz % 1000 == 0 -> 0
    hz % 100 == 0 -> 1
    else -> 2
  }
  return formatNumber(hz / 1000f, decimals) + " kHz"
}

/** The filter type's name. [FilterType.label] stays as is in data/model; the UI maps the type to a string. */
@Composable
internal fun filterTypeLabel(type: FilterType): String = stringResource(
  when (type) {
    FilterType.PEAK -> Res.string.audio_filter_peak
    FilterType.LOW_SHELF -> Res.string.audio_filter_low_shelf
    FilterType.HIGH_SHELF -> Res.string.audio_filter_high_shelf
    FilterType.HIGH_PASS -> Res.string.audio_filter_high_pass
    FilterType.LOW_PASS -> Res.string.audio_filter_low_pass
  }
)

@Composable
internal fun balanceText(b: Float): String = when {
  abs(b) < 0.01f -> stringResource(Res.string.audio_balance_center)
  b < 0 -> tr(Res.string.audio_balance_left, (abs(b) * 100).roundToInt())
  else -> tr(Res.string.audio_balance_right, (b * 100).roundToInt())
}

@Composable
internal fun widthText(w: Float): String = when {
  w < 0.02f -> stringResource(Res.string.audio_width_mono)
  abs(w - 1f) < 0.01f -> stringResource(Res.string.audio_width_original)
  else -> "${(w * 100).roundToInt()}%"
}

@Composable
internal fun usageLabel(usage: String?): String = when (usage) {
  "USAGE_MEDIA" -> stringResource(Res.string.audio_usage_media)
  "USAGE_GAME" -> stringResource(Res.string.audio_usage_game)
  "USAGE_VOICE_COMMUNICATION" -> stringResource(Res.string.audio_usage_voice)
  "USAGE_ASSISTANT" -> stringResource(Res.string.audio_usage_assistant)
  "USAGE_NOTIFICATION", "USAGE_ASSISTANCE_SONIFICATION" -> stringResource(Res.string.audio_usage_system)
  else -> stringResource(Res.string.audio_usage_playing)
}
