package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

/** Shape of one equalizer filter. */
enum class FilterType(val key: String, val label: String) {
  PEAK("peak", "Pico"),
  LOW_SHELF("low_shelf", "Prateleira grave"),
  HIGH_SHELF("high_shelf", "Prateleira aguda"),
  HIGH_PASS("high_pass", "Passa-alta"),
  LOW_PASS("low_pass", "Passa-baixa"),
  NOTCH("notch", "Rejeita-faixa"),
  BAND_PASS("band_pass", "Passa-faixa"),
  TILT_SHELF("tilt_shelf", "Inclinação");

  /** Passes and notch cut or isolate around their frequency, so their gain control does nothing. */
  val usesGain: Boolean get() = this == PEAK || this == LOW_SHELF || this == HIGH_SHELF || this == TILT_SHELF
  /** High-pass and low-pass support variable Butterworth cascade slopes (6..48 dB/oct). */
  val usesSlope: Boolean get() = this == HIGH_PASS || this == LOW_PASS

  companion object {
    fun fromKey(key: String?): FilterType = entries.firstOrNull { it.key == key } ?: PEAK
  }
}

/** Roll-off steepness for High-Pass and Low-Pass filters (Butterworth cascade order). */
enum class FilterSlope(val dbPerOct: Int, val order: Int) {
  DB_6(6, 1),
  DB_12(12, 2),
  DB_18(18, 3),
  DB_24(24, 4),
  DB_36(36, 6),
  DB_48(48, 8);

  companion object {
    fun fromDb(db: Int): FilterSlope = entries.firstOrNull { it.dbPerOct == db } ?: DB_12
  }
}

/** Which stereo or Mid/Side component a parametric band processes. */
enum class BandChannel(val key: String) {
  STEREO("both"),
  LEFT("left"),
  RIGHT("right"),
  MID("mid"),
  SIDE("side");

  companion object {
    fun fromKey(key: String?): BandChannel = entries.firstOrNull { it.key == key } ?: STEREO
  }
}

/** Which signal tap(s) the internal 64-band FFT RTA displays behind the EQ curve. */
enum class RtaMode(val key: String) {
  POST("post"),
  PRE("pre"),
  BOTH("both"),
  OFF("off");

  companion object {
    fun fromKey(key: String?): RtaMode = entries.firstOrNull { it.key == key } ?: POST
  }
}

/** Standard Bauer Stereophonic-to-Binaural (BS2B) crossfeed presets plus a custom mode. */
enum class CrossfeedPreset(val key: String, val cutoffHz: Float, val feedDb: Float) {
  OFF("off", 700f, 4.5f),
  BS2B_DEFAULT("bs2b", 700f, 4.5f),
  BS2B_CMOY("cmoy", 700f, 6.0f),
  BS2B_JMEIER("jmeier", 650f, 9.5f),
  CUSTOM("custom", 700f, 4.5f);

  companion object {
    fun fromKey(key: String?): CrossfeedPreset = entries.firstOrNull { it.key == key } ?: OFF
  }
}

/**
 * Purist binaural crossfeed (~0 ms latency): blends low frequencies across ears to reduce headphone
 * listening fatigue without adding room reverb.
 */
data class CrossfeedSettings(
  val preset: CrossfeedPreset = CrossfeedPreset.OFF,
  val cutoffHz: Float = 700f,
  val feedDb: Float = 4.5f
) {
  val enabled: Boolean get() = preset != CrossfeedPreset.OFF
  val effectiveCutoffHz: Float get() = if (preset == CrossfeedPreset.CUSTOM) cutoffHz.coerceIn(MIN_CUTOFF_HZ, MAX_CUTOFF_HZ) else preset.cutoffHz
  val effectiveFeedDb: Float get() = if (preset == CrossfeedPreset.CUSTOM) feedDb.coerceIn(MIN_FEED_DB, MAX_FEED_DB) else preset.feedDb

  companion object {
    const val MIN_CUTOFF_HZ = 300f
    const val MAX_CUTOFF_HZ = 2000f
    const val MIN_FEED_DB = 1.0f
    const val MAX_FEED_DB = 15.0f
  }
}

/** One band of the parametric equalizer. */
data class EqBand(
  val freqHz: Float,
  val gainDb: Float = 0f,
  val q: Float = DEFAULT_Q,
  val type: FilterType = FilterType.PEAK,
  val enabled: Boolean = true,
  val channel: BandChannel = BandChannel.STEREO,
  val slope: FilterSlope = FilterSlope.DB_12,
  /** Dynamic EQ maximum excursion in dB (-15..+15); 0 = static filter (zero dynamic CPU cost). */
  val dynamicRangeDb: Float = 0f,
  /** Dynamic EQ trigger threshold in dBFS (-48..0). */
  val dynamicThresholdDb: Float = -18f
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
  /** Limiter release time in milliseconds (20..500 ms). */
  val limiterReleaseMs: Float = 120f,
  /** Optional smooth tanh soft-clipper before final clamp to avoid harsh digital clipping. */
  val limiterSoftClip: Boolean = false,
  /** ISO 226:2003 equal-loudness contour compensation for low-volume listening. */
  val equalLoudnessEnabled: Boolean = false,
  val equalLoudnessLevelPhon: Float = 65f,
  /** 3-band Linkwitz-Riley multiband compander amount (0 = off, 1 = full). */
  val companderAmount: Float = 0f,
  /** 2x oversampled Tube/Tape harmonic warmth (0 = off, 1 = full). */
  val tubeWarmth: Float = 0f,
  /** Psychoacoustic sub-bass harmonic generator (0 = off, 1 = full). */
  val subBassHarmonic: Float = 0f,
  /** Partitioned FFT impulse response convolver (.irs / .wav). */
  val convolverEnabled: Boolean = false,
  val convolverPath: String? = null,
  val convolverMix: Float = 1f,
  /** Lowers the level by the curve's loudest boost, so boosting never pushes the music into the limiter. */
  val autoPreamp: Boolean = true,
  /** Android's hardware virtualizer, 0..1000. Only works where the device offers it. */
  val virtualizerStrength: Int = 0,
  /** Purist binaural crossfeed (BS2B / Jan Meier / Chu Moy / Custom), ~0 ms latency. */
  val crossfeed: CrossfeedSettings = CrossfeedSettings(),
  /** Internal 64-band FFT RTA spectrum tap behind the EQ curve (no microphone permission required). */
  val rtaMode: RtaMode = RtaMode.POST,
  /** Correction for the fone in use, applied before everything else. Null = none chosen. */
  val device: DeviceCorrection? = null,
  /** True when the bands are parametric (any number, with Q and type), false for the 10 graphic faders. */
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

    /** The old 15-band hardware centres: saved curves and the built-in presets are written on these. */
    val LEGACY_FREQUENCIES = listOf(
      25f, 40f, 63f, 100f, 160f, 250f, 400f, 630f,
      1000f, 1600f, 2500f, 4000f, 6300f, 10000f, 16000f
    )

    /** The 10 graphic faders, one octave apart from 31 Hz to 16 kHz; the first and last sit on the graph edges. */
    val DEFAULT_FREQUENCIES = listOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val DEFAULT_BANDS: List<EqBand> = DEFAULT_FREQUENCIES.map { EqBand(freqHz = it) }

    const val PREAMP_RANGE_DB = 15f
    const val TONE_RANGE_DB = 12f

    /** Gains on the 15 legacy centres, resampled onto the 10 faders. */
    fun withGains(legacyGainsDb: List<Float>): EqSettings {
      val gains = resampleGains(LEGACY_FREQUENCIES, List(LEGACY_FREQUENCIES.size) { legacyGainsDb.getOrElse(it) { 0f } }, DEFAULT_FREQUENCIES)
      return EqSettings(bands = DEFAULT_FREQUENCIES.mapIndexed { i, f -> EqBand(freqHz = f, gainDb = gains[i]) })
    }

    /**
     * [fromGains], given at [fromFreqs], interpolated on a log-frequency axis at each of [toFreqs] and rounded
     * to 0.1 dB. Outside the source range the nearest end is held.
     */
    fun resampleGains(fromFreqs: List<Float>, fromGains: List<Float>, toFreqs: List<Float>): List<Float> =
      toFreqs.map { f ->
        val g = when {
          f <= fromFreqs.first() -> fromGains.first()
          f >= fromFreqs.last() -> fromGains.last()
          else -> {
            val i = fromFreqs.indexOfLast { it <= f }
            val lo = Math.log(fromFreqs[i].toDouble())
            val t = ((Math.log(f.toDouble()) - lo) / (Math.log(fromFreqs[i + 1].toDouble()) - lo)).toFloat()
            fromGains[i] + (fromGains[i + 1] - fromGains[i]) * t
          }
        }
        Math.round(g * 10f) / 10f
      }

    /** A saved graphic curve on the old 15 centres is converted to the 10 faders; any other curve is kept as saved. */
    fun graphicFromSaved(saved: List<EqBand>): List<EqBand> =
      if (saved.size == LEGACY_FREQUENCIES.size) withGains(saved.map { it.gainDb }).bands else saved
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
    .apply { if (b.channel != BandChannel.STEREO) put("ch", b.channel.key) }
    .apply { if (b.slope != FilterSlope.DB_12) put("sl", b.slope.dbPerOct) }
    .apply {
      if (kotlin.math.abs(b.dynamicRangeDb) >= 0.05f) {
        put("dr", b.dynamicRangeDb.toDouble())
        put("dt", b.dynamicThresholdDb.toDouble())
      }
    }

  /** [maxGainDb]: the user's bands stop at ±15 dB; fone corrections may need up to ±20 dB. */
  fun bandFromJson(o: JSONObject, maxGainDb: Float = EqBand.MAX_GAIN): EqBand = EqBand(
    freqHz = o.finiteDouble("f", 1000.0).toFloat().coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ),
    gainDb = o.finiteDouble("g", 0.0).toFloat().coerceIn(-maxGainDb, maxGainDb),
    q = o.finiteDouble("q", EqBand.DEFAULT_Q.toDouble()).toFloat().coerceIn(EqBand.MIN_Q, EqBand.MAX_Q),
    type = FilterType.fromKey(o.optString("t")),
    enabled = o.optBoolean("on", true),
    channel = BandChannel.fromKey(o.optString("ch", BandChannel.STEREO.key)),
    slope = FilterSlope.fromDb(o.optInt("sl", FilterSlope.DB_12.dbPerOct)),
    dynamicRangeDb = o.finiteDouble("dr", 0.0).toFloat().coerceIn(-EqBand.MAX_GAIN, EqBand.MAX_GAIN),
    dynamicThresholdDb = o.finiteDouble("dt", -18.0).toFloat().coerceIn(-48f, 0f)
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
    .apply { if (kotlin.math.abs(s.limiterReleaseMs - 120f) >= 0.5f) put("limRel", s.limiterReleaseMs.toDouble()) }
    .apply { if (s.limiterSoftClip) put("limSoft", true) }
    .apply {
      if (s.equalLoudnessEnabled) {
        put("eqLoud", true)
        put("eqPhon", s.equalLoudnessLevelPhon.toDouble())
      }
    }
    .apply { if (s.companderAmount > 0.005f) put("comp", s.companderAmount.toDouble()) }
    .apply { if (s.tubeWarmth > 0.005f) put("tube", s.tubeWarmth.toDouble()) }
    .apply { if (s.subBassHarmonic > 0.005f) put("subH", s.subBassHarmonic.toDouble()) }
    .apply {
      if (s.convolverEnabled || !s.convolverPath.isNullOrBlank()) {
        put("convOn", s.convolverEnabled)
        s.convolverPath?.let { put("convPath", it) }
        put("convMix", s.convolverMix.toDouble())
      }
    }
    .put("autoPre", s.autoPreamp)
    .put("virt", s.virtualizerStrength)
    .apply {
      if (s.crossfeed.enabled) {
        put(
          "xf",
          JSONObject()
            .put("p", s.crossfeed.preset.key)
            .put("fc", s.crossfeed.cutoffHz.toDouble())
            .put("fd", s.crossfeed.feedDb.toDouble())
        )
      }
    }
    .apply { if (s.rtaMode != RtaMode.POST) put("rta", s.rtaMode.key) }
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
    val bands = EqSettings.graphicFromSaved(if (bandsJson != null && bandsJson.length() > 0) {
      (0 until bandsJson.length()).map { bandFromJson(bandsJson.getJSONObject(it)) }
    } else d.bands)
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
      limiterReleaseMs = o.finiteDouble("limRel", 120.0).toFloat().coerceIn(20f, 500f),
      limiterSoftClip = o.optBoolean("limSoft", false),
      equalLoudnessEnabled = o.optBoolean("eqLoud", false),
      equalLoudnessLevelPhon = o.finiteDouble("eqPhon", 65.0).toFloat().coerceIn(40f, 85f),
      companderAmount = o.finiteDouble("comp", 0.0).toFloat().coerceIn(0f, 1f),
      tubeWarmth = o.finiteDouble("tube", 0.0).toFloat().coerceIn(0f, 1f),
      subBassHarmonic = o.finiteDouble("subH", 0.0).toFloat().coerceIn(0f, 1f),
      convolverEnabled = o.optBoolean("convOn", false),
      convolverPath = o.optString("convPath", "").takeIf { it.isNotBlank() },
      convolverMix = o.finiteDouble("convMix", 1.0).toFloat().coerceIn(0f, 1f),
      autoPreamp = o.optBoolean("autoPre", true),
      virtualizerStrength = o.optInt("virt", 0).coerceIn(0, 1000),
      crossfeed = o.optJSONObject("xf")?.let { xf ->
        CrossfeedSettings(
          preset = CrossfeedPreset.fromKey(xf.optString("p")),
          cutoffHz = xf.finiteDouble("fc", 700.0).toFloat().coerceIn(CrossfeedSettings.MIN_CUTOFF_HZ, CrossfeedSettings.MAX_CUTOFF_HZ),
          feedDb = xf.finiteDouble("fd", 4.5).toFloat().coerceIn(CrossfeedSettings.MIN_FEED_DB, CrossfeedSettings.MAX_FEED_DB)
        )
      } ?: CrossfeedSettings(),
      rtaMode = RtaMode.fromKey(o.optString("rta", RtaMode.POST.key)),
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
