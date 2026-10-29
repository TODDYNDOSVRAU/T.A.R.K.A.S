package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.audio.spatial.SpatialPreset
import com.example.data.audio.spatial.SpatialStatus
import com.example.data.autoeq.AutoEqEntry
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqSettings
import com.example.data.systemaudio.ActiveAppsState
import com.example.data.systemaudio.EqReach
import com.example.data.systemaudio.GlobalEqStatus
import com.example.data.systemaudio.SystemAudioStatus
import com.example.ui.screens.audio.EqAdvancedPage
import com.example.ui.screens.audio.EqConsolePage
import com.example.ui.screens.audio.PresetActions
import com.example.ui.screens.audio.PresetPanel
import com.example.ui.theme.AppText
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.subPageTransition
import com.example.ui.viewmodel.DeviceSearchState
import kotlinx.coroutines.launch

/** What the Tarkas Spatial panel shows. */
data class SpatialUi(
  val enabled: Boolean = false,
  val preset: SpatialPreset = SpatialPreset.STANDARD,
  val intensity: Float = 1f,
  val status: SpatialStatus = SpatialStatus.OFF
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
  val onIntensityFinished: (Float) -> Unit = {}
)

/** The two pages of the tab: the console everyone uses, and the technical controls. */
private enum class AudioPage { MAIN, ADVANCED }

/**
 * The Áudio tab: hosts the console and the advanced sub-page, the preset sheet and the dialogs that
 * name and delete presets. The pages themselves live in [com.example.ui.screens.audio].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
  capabilities: AudioCapabilities,
  eqSettings: EqSettings,
  presets: List<EqPreset>,
  activePresetId: String,
  eqModified: Boolean,
  systemAudioStatus: SystemAudioStatus,
  isPlaying: Boolean = false,
  spectrumBars: () -> List<Float> = { emptyList() },
  activeApps: ActiveAppsState = ActiveAppsState.PermissionMissing,
  onRefreshActiveApps: () -> Unit = {},
  eqReach: EqReach = EqReach.APP,
  globalEqStatus: GlobalEqStatus = GlobalEqStatus(),
  rootPresent: Boolean = false,
  onSetEqReach: (EqReach) -> Unit = {},
  deviceSearch: DeviceSearchState = DeviceSearchState(),
  onOpenDevicePicker: () -> Unit = {},
  onSearchDevices: (String, String?) -> Unit = { _, _ -> },
  onApplyDevice: (AutoEqEntry, () -> Unit) -> Unit = { _, _ -> },
  onClearDevice: () -> Unit = {},
  onSetEnabled: (Boolean) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onSelectPreset: (EqPreset) -> Unit,
  onSavePresetAs: (String) -> Unit,
  onOverwritePreset: () -> Unit,
  onRenamePreset: (String, String) -> Unit,
  onDeletePreset: (String) -> Unit,
  onReset: () -> Unit,
  onRequestSystemAudioCapture: () -> Unit,
  onStopSystemAudioCapture: () -> Unit = {},
  spectrumAllowed: Boolean = true,
  onAllowSpectrum: () -> Unit = {},
  spatial: SpatialUi = SpatialUi(),
  spatialActions: SpatialActions? = null,
  modifier: Modifier = Modifier
) {
  var page by rememberSaveable { mutableStateOf(AudioPage.MAIN) }
  var showPresets by rememberSaveable { mutableStateOf(false) }
  var showSaveAs by remember { mutableStateOf(false) }
  var showRename by remember { mutableStateOf(false) }
  var showDelete by remember { mutableStateOf(false) }
  val activePreset = presets.firstOrNull { it.id == activePresetId }
  val reduced = rememberReducedMotion()

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  // Read when the sheet closes, which can be long after this composition (a fone's curve downloads
  // first), so it must see the search as it is then, not as it was when the callback was made.
  val latestSearch by rememberUpdatedState(deviceSearch)
  val latestSearchDevices by rememberUpdatedState(onSearchDevices)
  // Closing the sheet also clears its search, so it opens on the full list next time.
  val closePresets: () -> Unit = {
    scope.launch { sheetState.hide() }.invokeOnCompletion {
      showPresets = false
      if (latestSearch.query.isNotEmpty()) latestSearchDevices("", latestSearch.source)
    }
  }

  // Back leaves the sub-page before it leaves the tab. (The sheet handles back itself.)
  BackHandler(enabled = !showPresets && page != AudioPage.MAIN) { page = AudioPage.MAIN }

  Box(modifier = modifier.fillMaxSize()) {
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
            onOpen = { showPresets = true; onOpenDevicePicker() },
            onSaveAs = { showSaveAs = true },
            onOverwrite = onOverwritePreset,
            onRename = { showRename = true },
            onDelete = { showDelete = true },
            onReset = onReset
          ),
          onOpenAdvanced = { page = AudioPage.ADVANCED },
          spatial = spatial,
          spatialActions = spatialActions
        )
        AudioPage.ADVANCED -> EqAdvancedPage(
          capabilities = capabilities,
          eqSettings = eqSettings,
          systemAudioStatus = systemAudioStatus,
          activeApps = activeApps,
          onRefreshActiveApps = onRefreshActiveApps,
          eqReach = eqReach,
          globalEqStatus = globalEqStatus,
          rootPresent = rootPresent,
          onSetEqReach = onSetEqReach,
          onUpdateEq = onUpdateEq,
          onRequestSystemAudioCapture = onRequestSystemAudioCapture,
          onStopSystemAudioCapture = onStopSystemAudioCapture,
          onBack = { page = AudioPage.MAIN }
        )
      }
    }
  }

  if (showPresets) {
    ModalBottomSheet(
      onDismissRequest = closePresets,
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      PresetPanel(
        presets = presets,
        activePresetId = activePresetId,
        device = eqSettings.device,
        deviceSearch = deviceSearch,
        onSearchDevices = onSearchDevices,
        onRetryDevices = onOpenDevicePicker,
        onPickPreset = { onSelectPreset(it); closePresets() },
        onPickDevice = { entry -> onApplyDevice(entry) { closePresets() } },
        onClearDevice = onClearDevice
      )
    }
  }

  if (showSaveAs) {
    PresetNameDialog(
      title = "Salvar predefinição",
      initialName = if (activePreset?.isCustom == true) "${activePreset.name} 2" else "Minha predefinição",
      onConfirm = { name -> onSavePresetAs(name); showSaveAs = false },
      onDismiss = { showSaveAs = false }
    )
  }
  if (showRename && activePreset != null) {
    PresetNameDialog(
      title = "Renomear predefinição",
      initialName = activePreset.name,
      onConfirm = { name -> onRenamePreset(activePreset.id, name); showRename = false },
      onDismiss = { showRename = false }
    )
  }
  if (showDelete && activePreset != null) {
    AlertDialog(
      onDismissRequest = { showDelete = false },
      title = { Text("Excluir \"${activePreset.name}\"?", style = AppText.Title3) },
      text = { Text("O som atual continua igual. Só a predefinição salva é apagada.", style = AppText.Body) },
      confirmButton = {
        TextButton(onClick = { onDeletePreset(activePreset.id); showDelete = false }) {
          Text("Excluir", color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } }
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
  var name by rememberSaveable { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = AppText.Title3) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        placeholder = { Text("Nome da predefinição") },
        singleLine = true,
        modifier = Modifier.testTag("preset_name_input")
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("Salvar") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
  )
}
