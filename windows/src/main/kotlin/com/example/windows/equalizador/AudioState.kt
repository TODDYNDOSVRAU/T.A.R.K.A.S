package com.example.windows.equalizador

import com.example.data.audio.loudness.LoudnessNormalization
import com.example.data.audio.loudness.LoudnessStore
import com.example.data.audio.loudness.LoudnessUi
import com.example.data.audio.loudness.NormalizationMode
import com.example.data.audio.loudness.TaggedLoudness
import com.example.data.audio.spatial.HeadTrackStatus
import com.example.data.audio.spatial.SpatialControl
import com.example.data.audio.spatial.SpatialKnob
import com.example.data.audio.spatial.SpatialPreset
import com.example.data.autoeq.AutoEqEntry
import com.example.data.autoeq.AutoEqParser
import com.example.data.autoeq.AutoEqRepository
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqCompare
import com.example.data.model.EqPreset
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * State of the Áudio tab on Windows: the equalizer settings (loaded and saved here, applied through
 * [TarkasState.updateEq]), presets, headphone correction, Tarkas Spatial, loudness between tracks and the output
 * device. Mirrors the app's view-model rules; it takes the core state and never grows it.
 */
class AudioState(
  private val state: TarkasState,
  private val autoEq: AutoEqRepository,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
  private val listOutputs: () -> List<String> = ::javaSoundOutputs,
  private val onSpatialActive: (Boolean) -> Unit = {},
  val crossfadeSeconds: StateFlow<Int> = MutableStateFlow(0),
  val setCrossfade: (Int) -> Unit = {},
  private val saveDelayMs: Long = 400,
) {
  private val settings = state.settings
  private val player = state.player

  // ---- equalizer and presets ----
  private val customPresets = MutableStateFlow(EqPresets.presetsFromJson(settings.get(G, K_PRESETS)))
  private val _presets = MutableStateFlow(EqPresets.BUILT_IN + customPresets.value)
  val presets: StateFlow<List<EqPreset>> = _presets.asStateFlow()
  private val _activePresetId = MutableStateFlow(settings.get(G, K_ACTIVE) ?: EqPresets.FLAT.id)
  val activePresetId: StateFlow<String> = _activePresetId.asStateFlow()
  private val _eqModified = MutableStateFlow(false)
  val eqModified: StateFlow<Boolean> = _eqModified.asStateFlow()
  val eqSettings: StateFlow<EqSettings> = state.eqSettings
  private var saveJob: Job? = null

  init {
    settings.get(G, K_EQ)?.let { text ->
      runCatching { EqPresets.settingsFromJson(JSONObject(text)) }.getOrNull()?.let { loaded -> state.updateEq { loaded } }
    }
    refreshModified(state.eqSettings.value)
    SpatialControl.assetOpener = { name -> AudioState::class.java.classLoader.getResourceAsStream(name) }
    SpatialControl.onActiveChanged = onSpatialActive
  }

  private fun refreshModified(next: EqSettings) {
    val preset = _presets.value.firstOrNull { it.id == _activePresetId.value }
    _eqModified.value = preset == null || EqCompare.edited(next, preset.settings)
  }

  fun updateEq(transform: (EqSettings) -> EqSettings) {
    state.updateEq(transform)
    refreshModified(state.eqSettings.value)
    saveJob?.cancel()
    saveJob = scope.launch {
      delay(saveDelayMs)
      saveEqNow()
    }
  }

  /** Writes the settings now (used by the debounce, tests and shutdown). */
  fun saveEqNow() {
    settings.put(G, K_EQ, EqPresets.settingsToJson(state.eqSettings.value).toString())
  }

  fun setEnabled(on: Boolean) = updateEq { it.copy(enabled = on) }

  fun updateBand(index: Int, f: (EqBand) -> EqBand) = updateEq { s ->
    s.copy(bands = s.bands.mapIndexed { i, b -> if (i == index) f(b) else b })
  }

  fun selectPreset(p: EqPreset) {
    _activePresetId.value = p.id
    settings.put(G, K_ACTIVE, p.id)
    updateEq { it.withCurveOf(p.settings) }
  }

  fun savePresetAs(name: String) {
    val p = EqPreset("custom_${System.currentTimeMillis()}", name.trim(), state.eqSettings.value.userCurve, isCustom = true)
    storeCustom(customPresets.value + p)
    selectPreset(p)
  }

  fun overwritePreset() {
    val id = _activePresetId.value
    val cur = customPresets.value.firstOrNull { it.id == id } ?: return
    storeCustom(customPresets.value.map { if (it.id == cur.id) cur.copy(settings = state.eqSettings.value.userCurve) else it })
    refreshModified(state.eqSettings.value)
  }

  fun renamePreset(id: String, name: String) =
    storeCustom(customPresets.value.map { if (it.id == id) it.copy(name = name.trim()) else it })

  fun deletePreset(id: String) {
    storeCustom(customPresets.value.filterNot { it.id == id })
    if (_activePresetId.value == id) {
      _activePresetId.value = EqPresets.FLAT.id
      settings.put(G, K_ACTIVE, EqPresets.FLAT.id)
    }
    refreshModified(state.eqSettings.value)
  }

  private fun storeCustom(list: List<EqPreset>) {
    customPresets.value = list
    _presets.value = EqPresets.BUILT_IN + list
    settings.put(G, K_PRESETS, EqPresets.presetsToJson(list))
  }

  fun reset() {
    _activePresetId.value = EqPresets.FLAT.id
    settings.put(G, K_ACTIVE, EqPresets.FLAT.id)
    updateEq { s -> if (s.parametric) s.withCurveOf(EqPresets.FLAT.settings).withLayout(true).flat() else s.withCurveOf(EqPresets.FLAT.settings) }
  }

  /** A/B: true plays without the equalizer, false returns; nothing is saved. */
  fun compare(bypass: Boolean) {
    player.setEq(if (bypass) state.eqSettings.value.copy(enabled = false) else state.eqSettings.value)
  }

  // ---- headphone correction ----
  private val _deviceSearch = MutableStateFlow(DeviceSearchState())
  val deviceSearch: StateFlow<DeviceSearchState> = _deviceSearch.asStateFlow()
  private var deviceIndex: List<AutoEqEntry> = emptyList()
  private var searchJob: Job? = null

  fun openDevicePicker() {
    if (deviceIndex.isNotEmpty()) return searchDevices(_deviceSearch.value.query, _deviceSearch.value.source)
    _deviceSearch.update { it.copy(loading = true, error = null) }
    scope.launch {
      try {
        deviceIndex = autoEq.index()
        val preferred = listOf("oratory1990", "crinacle", "Rtings", "Innerfidelity")
        val sources = deviceIndex.groupingBy { it.source }.eachCount().entries
          .sortedWith(compareBy({ preferred.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { -it.value }))
          .map { it.key }
        _deviceSearch.update { it.copy(loading = false, sources = sources, total = deviceIndex.size, onlineOnly = deviceIndex.size - autoEq.bundledCount) }
        searchDevices(_deviceSearch.value.query, _deviceSearch.value.source)
      } catch (e: Exception) {
        _deviceSearch.update { it.copy(loading = false, error = e.message ?: "AutoEq") }
      }
    }
  }

  fun searchDevices(query: String, source: String?) {
    _deviceSearch.update { it.copy(query = query, source = source) }
    searchJob?.cancel()
    searchJob = scope.launch {
      delay(120)
      val index = deviceIndex
      val results = withContext(Dispatchers.Default) { AutoEqParser.search(index, query, source) }
      _deviceSearch.update { it.copy(results = results) }
    }
  }

  fun applyDevice(entry: AutoEqEntry, onDone: () -> Unit) {
    _deviceSearch.update { it.copy(applyingPath = entry.path, error = null) }
    scope.launch {
      try {
        val c = autoEq.correction(entry) ?: throw IllegalStateException("empty")
        updateEq { it.copy(device = c) }
        _deviceSearch.update { it.copy(applyingPath = null) }
        onDone()
      } catch (e: Exception) {
        _deviceSearch.update { it.copy(applyingPath = null, error = entry.name) }
      }
    }
  }

  fun clearDevice() = updateEq { it.copy(device = null) }

  private val _autoCorrection = MutableStateFlow(settings.get(G, K_AUTO_CORR) != "false")
  val autoCorrection: StateFlow<Boolean> = _autoCorrection.asStateFlow()
  fun setAutoCorrection(on: Boolean) {
    _autoCorrection.value = on
    settings.put(G, K_AUTO_CORR, on.toString())
  }

  // ---- output device ----
  private val selectedOutput = MutableStateFlow(settings.get(G, K_OUTPUT)?.takeIf { it.isNotEmpty() })
  private val _outputs = MutableStateFlow(OutputsUi(emptyList(), selectedOutput.value))
  val outputs: StateFlow<OutputsUi> = _outputs.asStateFlow()

  fun refreshOutputs() {
    _outputs.value = OutputsUi(listOutputs(), selectedOutput.value, selectedOutput.value ?: "Windows")
  }

  /** null = the Windows default. Takes effect when the next track opens its line. */
  fun selectOutput(name: String?) {
    selectedOutput.value = name
    player.outputMixer = name
    settings.put(G, K_OUTPUT, name ?: "")
    refreshOutputs()
  }

  // ---- Tarkas Spatial ----
  private val _spatial = MutableStateFlow(loadSpatial())
  val spatial: StateFlow<SpatialUi> = _spatial.asStateFlow()

  private fun loadSpatial(): SpatialUi {
    val preset = SpatialPreset.fromKey(settings.get(G, K_SP_PRESET))
    val tuning = SpatialPreset.entries.associateWith { p ->
      (settings.get(G, "spatial_tuning_${p.key}") ?: "").split(';').mapNotNull { pair ->
        val kv = pair.split('=')
        val knob = kv.getOrNull(0)?.let(SpatialKnob::fromKey) ?: return@mapNotNull null
        val v = kv.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
        knob to knob.clamp(v)
      }.toMap()
    }.filterValues { it.isNotEmpty() }
    return SpatialUi(
      enabled = settings.get(G, K_SP_ON) == "true", preset = preset,
      intensity = (settings.get(G, K_SP_INT)?.toFloatOrNull() ?: 1f).coerceIn(0.2f, 1f),
      status = SpatialControl.status.value, tuning = tuning, headStatus = HeadTrackStatus.OFF,
    )
  }

  private fun reconfigure() {
    val s = _spatial.value
    SpatialControl.configure(s.enabled, s.preset, s.intensity.toDouble(), s.tuning)
  }

  private fun setKnob(p: SpatialPreset, k: SpatialKnob, v: Double) {
    val values = (_spatial.value.tuning[p] ?: emptyMap()) + (k to k.clamp(v))
    _spatial.update { it.copy(tuning = it.tuning + (p to values)) }
    settings.put(G, "spatial_tuning_${p.key}", values.entries.joinToString(";") { "${it.key.key}=${it.value}" })
    SpatialControl.setTuning(p, values)
  }

  val spatialActions = SpatialActions(
    onEnabled = { on -> _spatial.update { it.copy(enabled = on) }; settings.put(G, K_SP_ON, on.toString()); reconfigure() },
    onPreset = { p -> _spatial.update { it.copy(preset = p) }; settings.put(G, K_SP_PRESET, p.key); reconfigure() },
    onIntensity = { v -> _spatial.update { it.copy(intensity = v) }; SpatialControl.setIntensity(v.toDouble()) },
    onIntensityFinished = { v -> settings.put(G, K_SP_INT, v.toString()) },
    onKnob = { p, k, v -> setKnob(p, k, v) },
    onResetTuning = { p ->
      _spatial.update { it.copy(tuning = it.tuning - p) }
      settings.put(G, "spatial_tuning_${p.key}", "")
      SpatialControl.setTuning(p, emptyMap())
    },
  )

  // ---- loudness between tracks ----
  private val store = LoudnessStore({ settings.get(G, it) }, { k, v -> settings.put(G, k, v) })
  private val _loudness = MutableStateFlow(LoudnessUi(mode = store.mode))
  val loudness: StateFlow<LoudnessUi> = _loudness.asStateFlow()
  private var loudTrack: String? = null
  private var loudDurationMs = 0L

  fun setNormalizationMode(mode: NormalizationMode) {
    store.mode = mode
    decideLoudness()
  }

  /** The playing file changed: saves the previous track's measurement when it covers the track, then decides for the new one. */
  fun onTrackChanged(file: java.io.File?, durationMs: Long) {
    val prev = loudTrack
    val (lufs, seconds) = player.chain.takeMeasurement()
    if (prev != null && lufs != null && LoudnessNormalization.isMeasurementComplete(seconds, loudDurationMs)) store.saveMeasured(prev, lufs)
    loudTrack = file?.absolutePath
    loudDurationMs = durationMs
    decideLoudness()
  }

  fun decideLoudness() {
    val id = loudTrack
    val measured = id?.let(store::measured)
    val d = LoudnessNormalization.decide(store.mode, TaggedLoudness(), measured)
    player.chain.setMetering(id != null && measured == null && store.mode != NormalizationMode.OFF)
    player.chain.setLoudnessGain(d.linearGain.toFloat())
    _loudness.value = LoudnessUi(store.mode, d.source, d.loudnessLufs, d.gainDb, hasTrack = id != null)
  }

  // ---- what the screen reads ----
  fun capabilities(playing: Boolean): AudioCapabilities = AudioCapabilities(
    deviceModel = "Windows PC", androidVersion = System.getProperty("os.name") + " " + System.getProperty("os.version"),
    apiLevel = 0, activeRouteName = selectedOutput.value ?: "Windows", activeRouteType = AudioCapabilities.ROUTE_OTHER,
    sampleRateHz = 48_000, channelCount = 2, channelFormat = "stereo", isAudioSessionActive = playing,
    audioSessionId = 0, isHardwareEqualizerAvailable = false, isBassBoostAvailable = false,
    isVirtualizerAvailable = false, effectsCount = 0,
  )

  fun outputCorrection(): OutputCorrectionUi = OutputCorrectionUi(selectedOutput.value ?: "Windows", _autoCorrection.value, null)

  init {
    player.outputMixer = selectedOutput.value
    reconfigure()
    refreshOutputs()
    scope.launch { SpatialControl.status.collect { st -> _spatial.update { it.copy(status = st) } } }
    scope.launch {
      var last: java.io.File? = null
      state.playerState.collect { ps ->
        if (ps.file != last) {
          last = ps.file
          onTrackChanged(ps.file, ps.durationMs)
        } else if (ps.durationMs > 0) loudDurationMs = ps.durationMs
      }
    }
  }

  companion object {
    const val G = TarkasState.GLOBAL
    const val K_EQ = "audio_eq_settings"
    const val K_PRESETS = "audio_eq_presets"
    const val K_ACTIVE = "audio_eq_active_preset"
    const val K_AUTO_CORR = "audio_output_auto_correction"
    const val K_OUTPUT = "audio_output_mixer"
    const val K_SP_ON = "spatial_enabled"
    const val K_SP_PRESET = "spatial_preset"
    const val K_SP_INT = "spatial_intensity"
  }
}

/** Java Sound output devices that play 16-bit stereo, by name, without duplicates. */
fun javaSoundOutputs(): List<String> {
  val info = DataLine.Info(SourceDataLine::class.java, AudioFormat(44_100f, 16, 2, true, false))
  return runCatching {
    AudioSystem.getMixerInfo().filter { AudioSystem.getMixer(it).isLineSupported(info) }.map { it.name }.distinct()
  }.getOrDefault(emptyList())
}
