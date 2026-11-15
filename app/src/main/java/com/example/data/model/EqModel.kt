package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/** Shape of one equalizer filter. */
enum class FilterType(val key: String, val label: String) {
  PEAK("peak", "Pico"),
  LOW_SHELF("low_shelf", "Prateleira grave"),
  HIGH_SHELF("high_shelf", "Prateleira aguda"),
  HIGH_PASS("high_pass", "Passa-alta"),
  LOW_PASS("low_pass", "Passa-baixa");

  /** Passes cut everything past their frequency, so their gain control does nothing. */
  val usesGain: Boolean get() = this == PEAK || this == LOW_SHELF || this == HIGH_SHELF

  companion object {
    fun fromKey(key: String?): FilterType = entries.firstOrNull { it.key == key } ?: PEAK
  }
}

/** One band of the parametric equalizer. */
data class EqBand(
  val freqHz: Float,
  val gainDb: Float = 0f,
  val q: Float = DEFAULT_Q,
  val type: FilterType = FilterType.PEAK,
  val enabled: Boolean = true
) {
  companion object {
    /** About 2/3 of an octave wide: neighbouring graphic bands meet without gaps or big overlaps. */
    const val DEFAULT_Q = 2.0f
    const val MIN_FREQ = 20f
    const val MAX_FREQ = 20000f
    const val MIN_GAIN = -15f
    const val MAX_GAIN = 15f
    const val MIN_Q = 0.1f
    const val MAX_Q = 12f
  }
}

/**
 * Correction curve for a specific headphone or earphone, measured by a reviewer (oratory1990,
 * crinacle, Rtings…) and fitted by the AutoEq project. It is its own stage, applied before the
 * user's curve, so presets and faders keep working on top of a corrected fone.
 */
data class DeviceCorrection(
  val name: String,
  /** Who measured it, e.g. "oratory1990". */
  val source: String,
  /** Measurement rig or form factor, e.g. "over-ear" or "711 in-ear". */
  val rig: String = "",
  val bands: List<EqBand>,
  val preampDb: Float = 0f
)

/** The whole state of the audio processing chain. Everything here is applied by our own DSP. */
data class EqSettings(
  val enabled: Boolean = true,
  val bands: List<EqBand> = DEFAULT_BANDS,
  val preampDb: Float = 0f,
  // Tone controls: a low shelf and a high shelf with their own corner frequency
  val bassDb: Float = 0f,
  val bassFreqHz: Float = 100f,
  val trebleDb: Float = 0f,
  val trebleFreqHz: Float = 8000f,
  /** -1 = only left, 0 = centre, +1 = only right */
  val balance: Float = 0f,
  /** 0 = mono, 1 = original, 2 = twice as wide (mid/side) */
  val stereoWidth: Float = 1f,
  val limiterEnabled: Boolean = true,
  val limiterCeilingDb: Float = 0f,
  /** Lowers the level by the curve's loudest boost, so boosting never pushes the music into the limiter. */
  val autoPreamp: Boolean = true,
  /** Android's hardware virtualizer, 0..1000. Only works where the device offers it. */
  val virtualizerStrength: Int = 0,
  /** Correction for the fone in use, applied before everything else. Null = none chosen. */
  val device: DeviceCorrection? = null,
  /** True when the bands are parametric (any number, with Q and type), false for the 15 graphic faders. */
  val parametric: Boolean = false,
  /**
   * What the other layout had when it was left, so switching back finds it. Not part of the sound
   * and never compared. Null = that layout's flat default.
   */
  val otherBands: List<EqBand>? = null
) {
  /** The same settings in the other layout: its remembered bands become the ones heard. */
  fun withLayout(parametricOn: Boolean): EqSettings =
    if (parametricOn == parametric) this
    else copy(parametric = parametricOn, bands = otherBands ?: defaultBands(parametricOn), otherBands = bands)

  /** Another curve in the layout it came with, remembering this one's other layout unless it is the same layout. */
  fun withCurveOf(p: EqSettings): EqSettings =
    p.copy(enabled = enabled, device = device, otherBands = if (p.parametric == parametric) otherBands else bands)

  /** The layout in use back to flat: the faders at zero, or the parametric back to one band. */
  fun flat(): EqSettings = copy(bands = defaultBands(parametric))

  /** The bands of the given layout, whether or not it is the one in use. */
  fun bandsOf(parametricLayout: Boolean): List<EqBand> =
    if (parametricLayout == parametric) bands else otherBands ?: defaultBands(parametricLayout)

  /**
   * The user's own curve, without the fone correction: what presets store and compare. The other
   * layout's memory is left out, so it never makes a preset look edited.
   */
  val userCurve: EqSettings
    get() = copy(enabled = true, device = null, otherBands = null)

  companion object {
    val DEFAULT_PARAM_BANDS: List<EqBand> = listOf(EqBand(freqHz = 1000f, q = 1.4f))

    fun defaultBands(parametric: Boolean): List<EqBand> = if (parametric) DEFAULT_PARAM_BANDS else DEFAULT_BANDS

    val DEFAULT_FREQUENCIES = listOf(
      25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f,
      1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f
    )
    val DEFAULT_BANDS: List<EqBand> = DEFAULT_FREQUENCIES.map { EqBand(freqHz = it) }

    const val PREAMP_RANGE_DB = 15f
    const val TONE_RANGE_DB = 12f

    fun withGains(gainsDb: List<Float>): EqSettings =
      EqSettings(bands = DEFAULT_FREQUENCIES.mapIndexed { i, f -> EqBand(freqHz = f, gainDb = gainsDb.getOrElse(i) { 0f }) })
  }
}

data class EqPreset(
  val id: String,
  val name: String,
  val settings: EqSettings,
  val isCustom: Boolean = false
)

/** Built-in presets and the JSON format used to store settings and user presets. */
object EqPresets {

  /** Converts a preset of the old 15-band hardware equalizer (millibels) into the new settings. */
  fun fromLegacy(profile: AudioProfile): EqPreset = EqPreset(
    id = profile.id,
    name = profile.name,
    isCustom = profile.isCustom,
    settings = EqSettings.withGains(profile.bandLevelsMb.map { it / 100f }).copy(
      // The old bass boost (0..1000) becomes the bass shelf, up to +9 dB.
      bassDb = profile.bassBoostStrength / 1000f * 9f,
      virtualizerStrength = profile.virtualizerStrength.toInt()
    )
  )

  private fun EqPreset.withoutVirtualizer() = copy(settings = settings.copy(virtualizerStrength = 0))

  val FLAT: EqPreset = fromLegacy(AudioProfile.FLAT).copy(name = "Flat")

  val BUILT_IN: List<EqPreset> = listOf(
    FLAT,
    // A preset changes the curve only; it never switches the hardware virtualizer on behind the user's back.
    fromLegacy(AudioProfile.PROG_ROCK).withoutVirtualizer(),
    fromLegacy(AudioProfile.ACOUSTIC_CLARITY).withoutVirtualizer(),
    fromLegacy(AudioProfile.VOCAL_PRESENCE).withoutVirtualizer(),
    fromLegacy(AudioProfile.BASS_PUNCH).withoutVirtualizer(),
    EqPreset(
      "preset_pop", "Pop",
      EqSettings.withGains(listOf(-1f, 0f, 1.5f, 2.5f, 2f, 0.5f, -0.5f, -1f, -0.5f, 0.5f, 1.5f, 2.5f, 3f, 3f, 2.5f))
    ),
    EqPreset(
      "preset_jazz", "Jazz",
      EqSettings.withGains(listOf(2f, 2.5f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 1.5f, 2f, 2f, 2.5f, 2.5f, 2f))
    ),
    EqPreset(
      "preset_classical", "Classical",
      EqSettings.withGains(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -1f, -2f, -2.5f, -3f, -4f))
        .copy(trebleDb = 1f)
    ),
    EqPreset(
      "preset_electronic", "Electronic",
      EqSettings.withGains(listOf(4.5f, 4f, 3f, 1f, 0f, -1.5f, -1f, 0f, 0.5f, 1f, 2f, 3f, 3.5f, 4f, 4f))
    ),
    EqPreset(
      "preset_loudness", "Loudness",
      EqSettings(bassDb = 6f, bassFreqHz = 90f, trebleDb = 4f, trebleFreqHz = 9000f, preampDb = -3f)
    ),
    EqPreset(
      "preset_treble", "More treble",
      EqSettings(trebleDb = 6f, trebleFreqHz = 6000f, preampDb = -2f)
    ),
    speechPreset()
  )

  /** Voice-forward curve whose first band becomes a high-pass at 80 Hz to remove rumble. */
  private fun speechPreset(): EqPreset {
    val base = EqSettings.withGains(listOf(0f, 0f, 0f, 0f, -1f, -1f, 0f, 1.5f, 3f, 3.5f, 3f, 2f, 0f, -1f, -2f))
    val bands = base.bands.mapIndexed { i, b ->
      if (i == 0) b.copy(type = FilterType.HIGH_PASS, freqHz = 80f, q = 0.7f) else b
    }
    return EqPreset("preset_speech", "Voice and podcast", base.copy(bands = bands))
  }

  // ---- JSON ----

  fun bandToJson(b: EqBand): JSONObject = JSONObject()
    .put("f", b.freqHz.toDouble())
    .put("g", b.gainDb.toDouble())
    .put("q", b.q.toDouble())
    .put("t", b.type.key)
    .put("on", b.enabled)

  /** [maxGainDb]: the user's bands stop at ±15 dB; fone corrections may need up to ±20 dB. */
  fun bandFromJson(o: JSONObject, maxGainDb: Float = EqBand.MAX_GAIN): EqBand = EqBand(
    freqHz = o.finiteDouble("f", 1000.0).toFloat().coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ),
    gainDb = o.finiteDouble("g", 0.0).toFloat().coerceIn(-maxGainDb, maxGainDb),
    q = o.finiteDouble("q", EqBand.DEFAULT_Q.toDouble()).toFloat().coerceIn(EqBand.MIN_Q, EqBand.MAX_Q),
    type = FilterType.fromKey(o.optString("t")),
    enabled = o.optBoolean("on", true)
  )

  fun settingsToJson(s: EqSettings): JSONObject = JSONObject()
    .put("enabled", s.enabled)
    .put("bands", JSONArray().apply { s.bandsOf(false).forEach { put(bandToJson(it)) } })
    .put("preamp", s.preampDb.toDouble())
    .put("bass", s.bassDb.toDouble())
    .put("bassF", s.bassFreqHz.toDouble())
    .put("treble", s.trebleDb.toDouble())
    .put("trebleF", s.trebleFreqHz.toDouble())
    .put("balance", s.balance.toDouble())
    .put("width", s.stereoWidth.toDouble())
    .put("limiter", s.limiterEnabled)
    .put("ceiling", s.limiterCeilingDb.toDouble())
    .put("autoPre", s.autoPreamp)
    .put("virt", s.virtualizerStrength)
    .apply { s.device?.let { put("device", deviceToJson(it)) } }
    .put("parametric", s.parametric)
    .put("paramBands", JSONArray().apply { s.bandsOf(true).forEach { put(bandToJson(it)) } })

  fun deviceToJson(d: DeviceCorrection): JSONObject = JSONObject()
    .put("name", d.name)
    .put("source", d.source)
    .put("rig", d.rig)
    .put("preamp", d.preampDb.toDouble())
    .put("bands", JSONArray().apply { d.bands.forEach { put(bandToJson(it)) } })

  fun deviceFromJson(o: JSONObject?): DeviceCorrection? {
    if (o == null) return null
    val arr = o.optJSONArray("bands") ?: return null
    return DeviceCorrection(
      name = o.optString("name"),
      source = o.optString("source"),
      rig = o.optString("rig"),
      preampDb = o.finiteDouble("preamp", 0.0).toFloat().coerceIn(-30f, 10f),
      bands = (0 until arr.length()).map { bandFromJson(arr.getJSONObject(it), maxGainDb = DEVICE_MAX_GAIN_DB) }
    )
  }

  /** The same limit the AutoEq parser accepts. */
  private const val DEVICE_MAX_GAIN_DB = 20f

  fun settingsFromJson(o: JSONObject): EqSettings {
    val d = EqSettings()
    val bandsJson = o.optJSONArray("bands")
    val bands = if (bandsJson != null && bandsJson.length() > 0) {
      (0 until bandsJson.length()).map { bandFromJson(bandsJson.getJSONObject(it)) }
    } else d.bands
    val paramJson = o.optJSONArray("paramBands")
    val paramBands = if (paramJson != null && paramJson.length() > 0) {
      (0 until paramJson.length()).map { bandFromJson(paramJson.getJSONObject(it)) }
    } else EqSettings.DEFAULT_PARAM_BANDS
    val parametric = o.optBoolean("parametric", false)
    return EqSettings(
      parametric = parametric,
      bands = if (parametric) paramBands else bands,
      otherBands = (if (parametric) bands else paramBands).takeUnless { it == EqSettings.defaultBands(!parametric) },
      enabled = o.optBoolean("enabled", true),
      preampDb = o.finiteDouble("preamp", 0.0).toFloat().coerceIn(-EqSettings.PREAMP_RANGE_DB, EqSettings.PREAMP_RANGE_DB),
      bassDb = o.finiteDouble("bass", 0.0).toFloat().coerceIn(-EqSettings.TONE_RANGE_DB, EqSettings.TONE_RANGE_DB),
      bassFreqHz = o.finiteDouble("bassF", d.bassFreqHz.toDouble()).toFloat(),
      trebleDb = o.finiteDouble("treble", 0.0).toFloat().coerceIn(-EqSettings.TONE_RANGE_DB, EqSettings.TONE_RANGE_DB),
      trebleFreqHz = o.finiteDouble("trebleF", d.trebleFreqHz.toDouble()).toFloat(),
      balance = o.finiteDouble("balance", 0.0).toFloat().coerceIn(-1f, 1f),
      stereoWidth = o.finiteDouble("width", 1.0).toFloat().coerceIn(0f, 2f),
      limiterEnabled = o.optBoolean("limiter", true),
      limiterCeilingDb = o.finiteDouble("ceiling", 0.0).toFloat().coerceIn(-12f, 0f),
      autoPreamp = o.optBoolean("autoPre", true),
      virtualizerStrength = o.optInt("virt", 0).coerceIn(0, 1000),
      device = deviceFromJson(o.optJSONObject("device"))
    )
  }

  fun presetsToJson(presets: List<EqPreset>): String = JSONArray().apply {
    presets.forEach { p ->
      put(JSONObject().put("id", p.id).put("name", p.name).put("settings", settingsToJson(p.settings)))
    }
  }.toString()

  fun presetsFromJson(json: String?): List<EqPreset> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
      val arr = JSONArray(json)
      (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        EqPreset(
          id = o.getString("id"),
          name = o.getString("name"),
          settings = settingsFromJson(o.getJSONObject("settings")),
          isCustom = true
        )
      }
    } catch (e: Exception) {
      emptyList()
    }
  }
}

/** A number from saved or imported JSON; "NaN", "Infinity" or text fall back to [default], so they never reach a filter. */
private fun org.json.JSONObject.finiteDouble(key: String, default: Double): Double =
  optDouble(key, default).takeIf { it.isFinite() } ?: default
