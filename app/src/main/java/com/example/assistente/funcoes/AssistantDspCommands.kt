package com.example.data.assistant

import com.example.data.model.CrossfeedPreset
import com.example.data.model.CrossfeedSettings
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import kotlin.math.abs
import kotlin.math.ln

/** Deterministic local DSP & playback commands understood by the Assistant in PT and EN. */
sealed interface DspCommand {
  data class Crossfeed(val preset: CrossfeedPreset) : DspCommand
  data class EqualLoudness(val enabled: Boolean) : DspCommand
  data class TubeWarmth(val amount: Float) : DspCommand
  data class SubBass(val amount: Float) : DspCommand
  data class Compander(val amount: Float) : DspCommand
  data class Limiter(val enabled: Boolean? = null, val softClip: Boolean? = null) : DspCommand
  data class PeqBandAdjust(val freqHz: Float, val deltaDb: Float) : DspCommand
  data object AbLoop : DspCommand
}

private val TURN_OFF = setOf("desligar", "desliga", "desativar", "desativa", "tirar", "off", "disable", "zero")
private val TURN_ON = setOf("ligar", "liga", "ativar", "ativa", "on", "enable", "usar", "colocar")

/** Parses natural-language PT/EN DSP commands into a [DspCommand], or null if unrecognized. */
fun parseDspCommand(input: String): DspCommand? {
  val norm = normalizeForSearch(input)
  val words = commandWords(input).toSet()

  // A-B Loop
  if (norm.contains("loop ab") || norm.contains("loop a-b") || norm.contains("a-b loop") ||
    norm.contains("ab loop") || norm.contains("marcar ponto a") || norm.contains("marcar ponto b") ||
    norm.contains("limpar loop") || norm.contains("clear loop")
  ) {
    return DspCommand.AbLoop
  }

  val asksOff = words.any { it in TURN_OFF }
  val asksOn = words.any { it in TURN_ON }
  val pctMatch = Regex("""(\d{1,3})\s*%""").find(norm)
  val pctValue = pctMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)?.div(100f)

  // Crossfeed (BS2B / Chu Moy / Jan Meier)
  if (norm.contains("crossfeed") || norm.contains("bs2b") || norm.contains("chu moy") || norm.contains("jan meier")) {
    val preset = when {
      asksOff -> CrossfeedPreset.OFF
      norm.contains("chu") || norm.contains("cmoy") -> CrossfeedPreset.BS2B_CMOY
      norm.contains("meier") || norm.contains("jmeier") -> CrossfeedPreset.BS2B_JMEIER
      else -> CrossfeedPreset.BS2B_DEFAULT
    }
    return DspCommand.Crossfeed(preset)
  }

  // Equal-Loudness ISO 226
  if (norm.contains("equal loudness") || norm.contains("equal-loudness") || norm.contains("iso 226") || norm.contains("iso226")) {
    return DspCommand.EqualLoudness(enabled = !asksOff)
  }

  // Soft-clip / Limiter
  if (norm.contains("soft clip") || norm.contains("soft-clip") || norm.contains("softclip")) {
    return DspCommand.Limiter(softClip = !asksOff)
  }
  if ((norm.contains("limitador") || norm.contains("limiter")) && (asksOff || asksOn)) {
    return DspCommand.Limiter(enabled = !asksOff)
  }

  // Tube / Tape Warmth
  if (norm.contains("valvul") || norm.contains("tube") || norm.contains("tape") || norm.contains("fita")) {
    val amt = when {
      asksOff -> 0f
      pctValue != null -> pctValue
      else -> 0.5f
    }
    return DspCommand.TubeWarmth(amt)
  }

  // Sub-Bass Harmonics
  if (norm.contains("sub-grave") || norm.contains("subgrave") || norm.contains("sub bass") || norm.contains("sub-bass") || norm.contains("subbass")) {
    val amt = when {
      asksOff -> 0f
      pctValue != null -> pctValue
      else -> 0.5f
    }
    return DspCommand.SubBass(amt)
  }

  // Multiband Compander
  if (norm.contains("compander") || norm.contains("multibanda") || norm.contains("multiband")) {
    val amt = when {
      asksOff -> 0f
      pctValue != null -> pctValue
      else -> 0.5f
    }
    return DspCommand.Compander(amt)
  }

  // Direct PEQ band adjustment: e.g. "subir 60 hz em 3 db", "cortar 4k 2 db", "+3 db em 100 hz", "-2.5 db at 3000 hz"
  val freqMatch = Regex("""(\d+(?:[.,]\d+)?)\s*(khz|k|hz)\b""").find(norm)
  val dbMatch = Regex("""([+-]?\d+(?:[.,]\d+)?)\s*db\b""").find(norm)
  if (freqMatch != null && dbMatch != null) {
    val rawF = freqMatch.groupValues[1].replace(',', '.').toFloatOrNull() ?: return null
    val unit = freqMatch.groupValues[2]
    val freqHz = (if (unit.startsWith("k")) rawF * 1000f else rawF).coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ)
    val rawDb = dbMatch.groupValues[1].replace(',', '.').toFloatOrNull() ?: return null
    val isCut = words.any { it in setOf("cortar", "corta", "reduzir", "reduz", "diminuir", "diminui", "abaixar", "abaixa", "menos", "cut", "lower", "reduce") }
    val signedDb = when {
      dbMatch.groupValues[1].startsWith("-") || dbMatch.groupValues[1].startsWith("+") -> rawDb
      isCut -> -abs(rawDb)
      else -> abs(rawDb)
    }.coerceIn(-12f, 12f)
    return DspCommand.PeqBandAdjust(freqHz, signedDb)
  }

  return null
}

/**
 * Purely applies an EQ/DSP [DspCommand] onto [current], returning the updated [EqSettings]
 * (or null for non-EQ commands such as [DspCommand.AbLoop]).
 */
fun applyDspCommand(current: EqSettings, cmd: DspCommand): EqSettings? = when (cmd) {
  is DspCommand.Crossfeed -> current.copy(
    crossfeed = CrossfeedSettings(
      preset = cmd.preset,
      cutoffHz = cmd.preset.cutoffHz,
      feedDb = cmd.preset.feedDb
    )
  )
  is DspCommand.EqualLoudness -> current.copy(equalLoudnessEnabled = cmd.enabled)
  is DspCommand.TubeWarmth -> current.copy(tubeWarmth = cmd.amount.coerceIn(0f, 1f))
  is DspCommand.SubBass -> current.copy(subBassHarmonic = cmd.amount.coerceIn(0f, 1f))
  is DspCommand.Compander -> current.copy(companderAmount = cmd.amount.coerceIn(0f, 1f))
  is DspCommand.Limiter -> current.copy(
    limiterEnabled = cmd.enabled ?: current.limiterEnabled,
    limiterSoftClip = cmd.softClip ?: current.limiterSoftClip
  )
  is DspCommand.PeqBandAdjust -> {
    val bands = current.bands.toMutableList()
    if (bands.isEmpty()) {
      bands += EqBand(freqHz = cmd.freqHz, gainDb = cmd.deltaDb.coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN))
    } else {
      // Find closest band in log-frequency space
      val targetLog = ln(cmd.freqHz.toDouble())
      val closestIdx = bands.indices.minByOrNull { abs(ln(bands[it].freqHz.toDouble()) - targetLog) } ?: 0
      val b = bands[closestIdx]
      bands[closestIdx] = b.copy(
        enabled = true,
        gainDb = (b.gainDb + cmd.deltaDb).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)
      )
    }
    current.copy(enabled = true, bands = bands)
  }
  DspCommand.AbLoop -> null
}
