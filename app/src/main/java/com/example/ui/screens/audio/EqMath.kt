package com.example.ui.screens.audio

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
 * The one locale every number on this screen is printed with. Mixing the device locale ("1,5") with
 * Locale.US ("1.5") put a comma in one readout and a dot in the next; numbers here always use a dot,
 * as audio gear and the AutoEq data do.
 */
private val NumberLocale: Locale = Locale.US

internal const val MIN_F = 20f
internal const val MAX_F = 20000f

/** The graph's vertical range: ±15 dB fits every band at full gain. */
internal const val GRAPH_DB_RANGE = 15f

/** Room above and below the curve so a band at full gain is not cut by the graph's edge. */
private const val GRAPH_PAD_PX = 14f

/** Position 0..1 of [f] on the 20 Hz – 20 kHz log axis. */
internal fun freqToX(f: Float): Float = (log10(f / MIN_F) / log10(MAX_F / MIN_F)).coerceIn(0f, 1f)

/** Frequency at position [x] (0..1) of the log axis. */
internal fun xToFreq(x: Float): Float = MIN_F * (MAX_F / MIN_F).pow(x)

/** Height in a graph [h] pixels tall where [db] is drawn; 0 dB is the middle. */
internal fun dbToY(db: Float, h: Float): Float = h / 2f - db / GRAPH_DB_RANGE * (h / 2f - GRAPH_PAD_PX)

/** Q is heard as a ratio, so its knob turns on a log scale: 0..1 over MIN_Q..MAX_Q. */
internal fun qToKnob(q: Float): Float =
  log10(q.coerceIn(EqBand.MIN_Q, EqBand.MAX_Q) / EqBand.MIN_Q) / log10(EqBand.MAX_Q / EqBand.MIN_Q)

internal fun knobToQ(v: Float): Float = EqBand.MIN_Q * (EqBand.MAX_Q / EqBand.MIN_Q).pow(v.coerceIn(0f, 1f))

/** Rounds to 0.5 dB and snaps near zero, so returning a control to flat is easy. */
internal fun snap(db: Float): Float {
  val r = (db * 2f).roundToInt() / 2f
  return if (abs(r) < 0.25f) 0f else r
}

/** "+3.5", "0.0", "-2.0": a sign on boosts, so a glance tells up from down. */
internal fun formatDb(db: Float): String {
  val rounded = (db * 10f).roundToInt() / 10f
  val text = String.format(NumberLocale, "%.1f", rounded)
  return if (rounded > 0f) "+$text" else text
}

/** "63", "1.6k", "16k": the short form printed under a band. */
internal fun formatHz(f: Float): String = when {
  f >= 10000f -> "${(f / 1000f).roundToInt()}k"
  f >= 1000f -> String.format(NumberLocale, "%.1fk", f / 1000f).replace(".0k", "k")
  else -> "${f.roundToInt()}"
}

/** The same frequency for a screen reader, with its unit spoken. */
internal fun spokenHz(f: Float): String = when {
  f >= 1000f -> String.format(NumberLocale, "%.1f kHz", f / 1000f).replace(".0 kHz", " kHz")
  else -> "${f.roundToInt()} Hz"
}

internal fun formatQ(q: Float): String = String.format(NumberLocale, "%.2f", q)

/** "48.0 kHz" for the output line. */
internal fun formatSampleRate(hz: Int): String = String.format(NumberLocale, "%.1f kHz", hz / 1000f)

internal fun balanceText(b: Float): String = when {
  abs(b) < 0.01f -> "Centro"
  b < 0 -> "Esquerda ${(abs(b) * 100).roundToInt()}%"
  else -> "Direita ${(b * 100).roundToInt()}%"
}

internal fun widthText(w: Float): String = when {
  w < 0.02f -> "Mono"
  abs(w - 1f) < 0.01f -> "Original"
  else -> "${(w * 100).roundToInt()}%"
}

internal fun usageLabel(usage: String?): String = when (usage) {
  "USAGE_MEDIA" -> "Música ou vídeo"
  "USAGE_GAME" -> "Jogo"
  "USAGE_VOICE_COMMUNICATION" -> "Chamada de voz"
  "USAGE_ASSISTANT" -> "Assistente"
  "USAGE_NOTIFICATION", "USAGE_ASSISTANCE_SONIFICATION" -> "Som do sistema"
  else -> "Tocando agora"
}
