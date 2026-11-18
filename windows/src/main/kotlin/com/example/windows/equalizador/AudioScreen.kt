package com.example.windows.equalizador

import com.example.windows.res.Res
import com.example.windows.res.tr
import com.example.windows.res.audio_action_cancel
import com.example.windows.res.audio_action_delete
import com.example.windows.res.audio_action_save
import com.example.windows.res.audio_preset_default_name
import com.example.windows.res.audio_preset_delete_body
import com.example.windows.res.audio_preset_delete_title
import com.example.windows.res.audio_preset_name_hint
import com.example.windows.res.audio_preset_rename_title
import com.example.windows.res.audio_preset_save_title
import org.jetbrains.compose.resources.stringResource
import com.example.windows.base.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.components.TarkasAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.audio.loudness.LoudnessUi
import com.example.data.audio.loudness.NormalizationMode
import com.example.data.audio.spatial.SpatialKnob
import com.example.data.audio.spatial.SpatialPreset
import com.example.data.audio.spatial.SpatialStatus
import com.example.data.autoeq.AutoEqEntry
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqSettings
import com.example.windows.equalizador.rememberReducedMotion
import com.example.windows.equalizador.subPageTransition
import com.example.windows.equalizador.DeviceSearchState

/** The output playing now, and whether each output keeps its own fone correction. */
data class OutputCorrectionUi(
  val outputName: String = "",
  val auto: Boolean = true,
  /** A search that finds AutoEq corrections for the fone playing now, while it has none. */
  val suggestionQuery: String? = null
)

/** What the Tarkas Spatial panel shows. */
data class SpatialUi(
  val enabled: Boolean = false,
  val preset: SpatialPreset = SpatialPreset.STANDARD,
  val intensity: Float = 1f,
  val status: SpatialStatus = SpatialStatus.OFF,
  /** What happens to other apps, shown under the panel; null when nothing needs saying. */
  val othersNote: String? = null,
  /** What the listener set by hand on each preset (only the values that differ from the preset). */
  val tuning: Map<SpatialPreset, Map<SpatialKnob, Double>> = emptyMap(),
  /** Head tracking: whether the user wants it, and what Android says about it. */
  val headTracking: Boolean = false,
  val headStatus: com.example.data.audio.spatial.HeadTrackStatus = com.example.data.audio.spatial.HeadTrackStatus.OFF
)

/**
 * What the Tarkas Spatial panel can do. [onIntensity] runs on every frame of a drag, so the sound
 * follows the finger; [onIntensityFinished] runs once with the final value when the finger lifts, the
 * place to persist it.
 */
class SpatialActions(
  val onEnabled: (Boolean) -> Unit,
  val onPreset: (SpatialPreset) -> Unit,
  val onIntensity: (Float) -> Unit,
  val onIntensityFinished: (Float) -> Unit = {},
  val onKnob: (SpatialPreset, SpatialKnob, Double) -> Unit = { _, _, _ -> },
  val onResetTuning: (SpatialPreset) -> Unit = {},
  val onHeadTracking: (Boolean) -> Unit = {},
  val onRecenterHead: () -> Unit = {}
)

/** Output devices of this PC (Java Sound mixers that play stereo) and the one in use (null = Windows default). */
data class OutputsUi(val names: List<String> = emptyList(), val selected: String? = null, val nowPlaying: String = "")

/** The two pages of the tab: the console everyone uses, and the technical controls. */
private enum class AudioPage { MAIN, ADVANCED }

/** What the inline side pane (384 dp, 2x chain) shows. The desktop has no bottom sheets: these were sheets on Android. */
private enum class AudioPane { PRESETS, CORRECTION, SETTINGS, SPATIAL }

/** The side pane: a close row (Esc closes it too) over a scrolling body. */
@Composable
private fun AudioSidePane(onClose: () -> Unit, content: @Composable () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Column(Modifier.width(Grid.u384).fillMaxHeight().background(colors.surface).testTag("audio_side_pane")) {
    Row(Modifier.fillMaxWidth().height(Grid.u48), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onClose, modifier = Modifier.testTag("audio_side_pane_close")) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.audio_action_cancel), tint = colors.onSurfaceVariant)
      }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { content() }
  }
}

/**
 * The Áudio tab: hosts the console and the advanced sub-page, the preset sheet and the dialogs that
 * name and delete presets. The pages themselves live in [com.example.ui.screens.audio].
 */
@Composable
fun AudioScreen(
  capabilities: AudioCapabilities,
  eqSettings: EqSettings,
  presets: List<EqPreset>,
  activePresetId: String,
  eqModified: Boolean,
  isPlaying: Boolean = false,
  spectrumBars: () -> List<Float> = { emptyList() },
  deviceSearch: DeviceSearchState = DeviceSearchState(),
  onOpenDevicePicker: () -> Unit = {},
  onSearchDevices: (String, String?) -> Unit = { _, _ -> },
  onApplyDevice: (AutoEqEntry, () -> Unit) -> Unit = { _, _ -> },
  onClearDevice: () -> Unit = {},
  outputCorrection: OutputCorrectionUi = OutputCorrectionUi(),
  onSetAutoOutputCorrection: (Boolean) -> Unit = {},
  /** A search to open the fone list with (from a suggestion); [onDevicePickerOpened] clears it. */
  crossfadeSeconds: Int = 0,
  onSetCrossfade: (Int) -> Unit = {},
  devicePickerRequest: String? = null,
  onDevicePickerOpened: () -> Unit = {},
  onSetEnabled: (Boolean) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onSelectPreset: (EqPreset) -> Unit,
  onSavePresetAs: (String) -> Unit,
  onOverwritePreset: () -> Unit,
  onRenamePreset: (String, String) -> Unit,
  onDeletePreset: (String) -> Unit,
  onReset: () -> Unit,
  spectrumAllowed: Boolean = true,
  onAllowSpectrum: () -> Unit = {},
  spatial: SpatialUi = SpatialUi(),
  spatialActions: SpatialActions? = null,
  onCompare: (Boolean) -> Unit = {},
  loudness: LoudnessUi = LoudnessUi(),
  onSetNormalizationMode: (NormalizationMode) -> Unit = {},
  outputs: OutputsUi = OutputsUi(),
  onSelectOutput: (String?) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var page by remember { mutableStateOf(AudioPage.MAIN) }
  var pane by remember { mutableStateOf<AudioPane?>(null) }
  var showSaveAs by remember { mutableStateOf(false) }
  var showRename by remember { mutableStateOf(false) }
  var showDelete by remember { mutableStateOf(false) }
  val activePreset = presets.firstOrNull { it.id == activePresetId }
  val reduced = rememberReducedMotion()

  // Read when the pane closes, which can be long after this composition (a fone's curve downloads
  // first), so it must see the search as it is then, not as it was when the callback was made.
  val latestSearch by rememberUpdatedState(deviceSearch)
  val latestSearchDevices by rememberUpdatedState(onSearchDevices)
  // Closing the pane also clears its search, so it opens on the full list next time.
  val closePane: () -> Unit = {
    if (pane == AudioPane.PRESETS || pane == AudioPane.CORRECTION) {
      if (latestSearch.query.isNotEmpty()) latestSearchDevices("", latestSearch.source)
    }
    pane = null
  }

  LaunchedEffect(devicePickerRequest) {
    val query = devicePickerRequest ?: return@LaunchedEffect
    pane = AudioPane.CORRECTION
    onOpenDevicePicker()
    onSearchDevices(query, null)
    onDevicePickerOpened()
  }

  // Esc closes the pane, then leaves the sub-page, then leaves the tab.
  BackHandler(enabled = pane != null) { closePane() }
  BackHandler(enabled = pane == null && page != AudioPage.MAIN) { page = AudioPage.MAIN }

  Row(modifier = modifier.fillMaxSize()) {
   Box(Modifier.weight(1f).fillMaxHeight()) {
    AnimatedContent(
      targetState = page,
      transitionSpec = { subPageTransition(forward = targetState != AudioPage.MAIN, reduced = reduced) },
      label = "audioPage",
      modifier = Modifier.fillMaxSize()
    ) { shown ->
      when (shown) {
        AudioPage.MAIN -> EqConsolePage(
          capabilities = capabilities,
          eqSettings = eqSettings,
          activePreset = activePreset,
          eqModified = eqModified,
          isPlaying = isPlaying,
          spectrumBars = spectrumBars,
          spectrumAllowed = spectrumAllowed,
          onAllowSpectrum = onAllowSpectrum,
          onSetEnabled = onSetEnabled,
          onUpdateEq = onUpdateEq,
          onUpdateBand = onUpdateBand,
          presetActions = PresetActions(
            onOpen = { pane = AudioPane.PRESETS; onOpenDevicePicker() },
            onOpenCorrection = { pane = AudioPane.CORRECTION; onOpenDevicePicker() },
            onSaveAs = { showSaveAs = true },
            onOverwrite = onOverwritePreset,
            onRename = { showRename = true },
            onDelete = { showDelete = true },
            onReset = onReset
          ),
          onOpenAdvanced = { page = AudioPage.ADVANCED },
          onOpenSettings = { pane = AudioPane.SETTINGS },
          onOpenSpatial = { pane = AudioPane.SPATIAL },
          spatial = spatial,
          spatialActions = spatialActions,
          onCompare = onCompare
        )
        AudioPage.ADVANCED -> EqAdvancedPage(
          capabilities = capabilities,
          eqSettings = eqSettings,
          onUpdateEq = onUpdateEq,
          loudness = loudness,
          onSetNormalizationMode = onSetNormalizationMode,
          outputCorrection = outputCorrection,
          onSetAutoOutputCorrection = onSetAutoOutputCorrection,
          crossfadeSeconds = crossfadeSeconds,
          onSetCrossfade = onSetCrossfade,
          outputs = outputs,
          onSelectOutput = onSelectOutput,
          spatialOn = spatial.enabled,
          onBack = { page = AudioPage.MAIN }
        )
      }
    }
   }
   pane?.let { open ->
    AudioSidePane(onClose = closePane) {
      when (open) {
        AudioPane.PRESETS, AudioPane.CORRECTION -> PresetPanel(
          presets = presets,
          activePresetId = activePresetId,
          device = eqSettings.device,
          deviceOutput = outputCorrection.outputName.takeIf { outputCorrection.auto },
          suggestion = outputCorrection.suggestionQuery?.let { it to outputCorrection.outputName },
          deviceSearch = deviceSearch,
          onSearchDevices = onSearchDevices,
          onRetryDevices = onOpenDevicePicker,
          onPickPreset = { onSelectPreset(it); closePane() },
          onPickDevice = { entry -> onApplyDevice(entry) { closePane() } },
          onClearDevice = onClearDevice,
          initialTab = if (open == AudioPane.CORRECTION) PresetTab.CORRECTION else PresetTab.EQ
        )
        AudioPane.SETTINGS -> EqSettingsSheet(
          settings = eqSettings,
          spectrumAllowed = spectrumAllowed,
          onAllowSpectrum = onAllowSpectrum,
          onUpdateEq = onUpdateEq,
          onOpenAdvanced = { pane = null; page = AudioPage.ADVANCED }
        )
        AudioPane.SPATIAL -> if (spatialActions != null) SpatialPanel(spatial, spatialActions)
      }
    }
   }
  }

  if (showSaveAs) {
    PresetNameDialog(
      title = stringResource(Res.string.audio_preset_save_title),
      initialName = if (activePreset?.isCustom == true) "${activePreset.name} 2" else stringResource(Res.string.audio_preset_default_name),
      onConfirm = { name -> onSavePresetAs(name); showSaveAs = false },
      onDismiss = { showSaveAs = false }
    )
  }
  if (showRename && activePreset != null) {
    PresetNameDialog(
      title = stringResource(Res.string.audio_preset_rename_title),
      initialName = activePreset.name,
      onConfirm = { name -> onRenamePreset(activePreset.id, name); showRename = false },
      onDismiss = { showRename = false }
    )
  }
  if (showDelete && activePreset != null) {
    TarkasAlertDialog(
      onDismissRequest = { showDelete = false },
      title = { Text(tr(Res.string.audio_preset_delete_title, activePreset.name), style = GridText.Title) },
      text = { Text(stringResource(Res.string.audio_preset_delete_body), style = GridText.Body) },
      confirmButton = {
        TextButton(onClick = { onDeletePreset(activePreset.id); showDelete = false }) {
          Text(stringResource(Res.string.audio_action_delete), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(Res.string.audio_action_cancel)) } }
    )
  }
}

/**
 * Names a preset. Its own dialog rather than the playlist one, whose field said "Nome da playlist".
 * The name is trimmed, and a blank one cannot be saved.
 */
@Composable
private fun PresetNameDialog(
  title: String,
  initialName: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var name by remember { mutableStateOf(initialName) }
  TarkasAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = GridText.Title) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        placeholder = { Text(stringResource(Res.string.audio_preset_name_hint)) },
        singleLine = true,
        modifier = Modifier.testTag("preset_name_input")
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.audio_action_save)) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.audio_action_cancel)) } }
  )
}
