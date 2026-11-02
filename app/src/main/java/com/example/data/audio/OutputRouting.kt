package com.example.data.audio

import com.example.data.autoeq.AutoEqEntry
import com.example.data.autoeq.AutoEqParser
import com.example.data.model.DeviceCorrection
import com.example.data.model.EqPresets
import org.json.JSONObject

/** Where the music comes out. */
enum class OutputKind { SPEAKER, WIRED, BLUETOOTH, USB, OTHER }

/**
 * The output the music goes to now. [key] tells one output from another across connections:
 * Bluetooth and USB fones by their address or name, a wired fone as one output (the jack has no name),
 * the loudspeaker as itself.
 */
data class OutputDevice(val key: String, val kind: OutputKind, val name: String) {
  val isExternal: Boolean get() = kind != OutputKind.SPEAKER

  companion object {
    val SPEAKER = OutputDevice("speaker", OutputKind.SPEAKER, "Alto-falante do aparelho")

    fun keyFor(kind: OutputKind, address: String?, productName: String?): String {
      val id = address?.trim()?.takeIf { it.isNotEmpty() } ?: productName?.trim()?.lowercase().orEmpty()
      return when (kind) {
        OutputKind.SPEAKER -> "speaker"
        OutputKind.WIRED -> "wired"
        OutputKind.BLUETOOTH -> "bt:$id"
        OutputKind.USB -> "usb:$id"
        OutputKind.OTHER -> "other:$id"
      }
    }
  }
}

/**
 * Which fone correction goes with which output. A correction chosen while an output plays is bound to
 * that output, and comes back by itself when the output does; "none" can be bound too. Pure, so the
 * rules are checked without a device.
 */
object OutputRouting {

  /** Holds the correction that was in use before outputs had bindings, for the first fone seen. */
  const val CARRY_OVER = "*"

  /**
   * @param correction what to use on [output]
   * @param bindings the bindings after the decision (changed only by the carry-over)
   */
  data class Decision(val correction: DeviceCorrection?, val bindings: Map<String, DeviceCorrection?>)

  /**
   * - a bound output gets its own correction (or none, when "none" was bound);
   * - the loudspeaker, unbound, gets none: a fone's correction is wrong there;
   * - an unbound fone gets the correction carried over from before bindings existed, if their names
   *   share a word ([sameFone]); it waits for that fone otherwise. Anything else gets none.
   */
  fun decide(output: OutputDevice, bindings: Map<String, DeviceCorrection?>): Decision {
    if (output.key in bindings) return Decision(bindings[output.key], bindings)
    if (!output.isExternal) return Decision(null, bindings)
    val carried = bindings[CARRY_OVER]
    if (carried != null && sameFone(carried.name, output.name)) {
      return Decision(carried, bindings - CARRY_OVER + (output.key to carried))
    }
    return Decision(null, bindings)
  }

  /** Words that do not tell one fone from another. */
  private val COMMON_WORDS = setOf(
    "usb", "type", "typec", "headset", "headphones", "headphone", "earphones", "earphone", "earbuds",
    "audio", "bluetooth", "wireless", "pro", "plus", "max", "mini", "the", "and", "with", "fone", "anc"
  )

  /** The words of a name that can tell a fone apart: letters and digits, 3 or more, not common ones. */
  fun distinctiveWords(name: String): Set<String> =
    name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in COMMON_WORDS }.toSet()

  /** Whether a correction's name and an output's name share a distinctive word ("Sony WH-1000XM4" / "WH-1000XM4"). */
  fun sameFone(correctionName: String, outputName: String): Boolean =
    distinctiveWords(correctionName).any { w -> w in distinctiveWords(outputName) } ||
      AutoEqParser.searchKey(outputName).let { k -> k.length >= 4 && k in AutoEqParser.searchKey(correctionName) }

  /** The bindings the first time the feature runs: the correction in use waits for the first fone. */
  fun firstRun(current: DeviceCorrection?): Map<String, DeviceCorrection?> =
    if (current != null) mapOf(CARRY_OVER to current) else emptyMap()

  fun toJson(bindings: Map<String, DeviceCorrection?>): String = JSONObject().apply {
    bindings.forEach { (k, v) -> put(k, v?.let(EqPresets::deviceToJson) ?: JSONObject.NULL) }
  }.toString()

  fun fromJson(text: String?): Map<String, DeviceCorrection?>? {
    if (text.isNullOrBlank()) return null
    return runCatching {
      val o = JSONObject(text)
      o.keys().asSequence().associateWith { k -> if (o.isNull(k)) null else EqPresets.deviceFromJson(o.optJSONObject(k)) }
    }.getOrNull()
  }

  /** Names that say nothing about which fone it is. */
  private val GENERIC = setOf(
    "headset", "headphones", "headphone", "earphones", "earbuds", "usbaudio", "usbheadset",
    "usbaudiodevice", "bluetooth", "bluetoothaudio", "audio", "fone", "speaker", "soundbar"
  )

  /**
   * What AutoEq has for a fone named [query]: one entry per model (the best known reviewer's
   * measurement), the best match first. [exact] is false when only a word of the name matched, so the
   * user should pick the model.
   */
  data class Suggestion(val query: String, val entries: List<AutoEqEntry>, val exact: Boolean)

  /**
   * Suggestions for a fone that reports [productName]. The whole name is tried first; a name the user
   * gave the fone ("Buds2 de roger") rarely matches, so then each distinctive word is tried, the one
   * with fewest models first. Null when nothing matches or the name is too generic to be sure.
   */
  fun suggestions(entries: List<AutoEqEntry>, productName: String, limit: Int = 8): Suggestion? {
    // Bluetooth LE fones often report "LE-<name>".
    val name = productName.trim().removePrefix("LE-").removePrefix("LE_").trim()
    val key = AutoEqParser.searchKey(name)
    if (key.length < 4 || key in GENERIC) return null
    models(entries, name, limit).takeIf { it.isNotEmpty() }?.let { return Suggestion(name, it, exact = true) }
    return distinctiveWords(name)
      .filter { it.length >= 4 && it.any(Char::isDigit) || it.length >= 5 }
      .map { w -> Suggestion(w, models(entries, w, limit), exact = false) }
      .filter { it.entries.isNotEmpty() }
      .minByOrNull { it.entries.size }
  }

  private fun models(entries: List<AutoEqEntry>, query: String, limit: Int): List<AutoEqEntry> {
    val preferred = listOf("oratory1990", "crinacle", "Rtings", "Innerfidelity")
    fun rank(e: AutoEqEntry) = preferred.indexOf(e.source).let { if (it < 0) preferred.size else it }
    return AutoEqParser.search(entries, query, limit = 200)
      .groupBy { AutoEqParser.searchKey(it.name) }
      .values
      .map { same -> same.minBy(::rank) }
      .take(limit)
  }
}
