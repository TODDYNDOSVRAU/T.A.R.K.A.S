package com.example.windows.equalizador

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.data.autoeq.AutoEqRepository
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.player.PlayerHost
import com.example.windows.shell.PanelContent
import com.example.windows.state.TarkasState
import java.io.File

/** Builds the Áudio state once and registers the screen in the shell slot of the Áudio tab. */
object AudioHost {
  @Volatile private var s: AudioState? = null

  fun audio(state: TarkasState): AudioState = s ?: synchronized(this) {
    s ?: run {
      val controller = PlayerHost.controller(state)
      val dir = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "TARKAS").also { it.mkdirs() }
      AudioState(
        state = state,
        autoEq = AutoEqRepository(dir),
        onSpatialActive = { controller.spatialActive = it },
        crossfadeSeconds = kotlinx.coroutines.flow.MutableStateFlow(controller.ui.value.crossfadeSeconds),
        setCrossfade = controller::setCrossfade,
      ).also { s = it }
    }
  }

  fun shutdown() { s?.saveEqNow() }

  fun register(slots: MutableMap<TarkasNavTab, PanelContent>) {
    slots[TarkasNavTab.AUDIO] = PanelContent { st -> AudioPanel(audio(st), st) }
  }
}

@Composable
fun AudioPanel(audio: AudioState, state: TarkasState) {
  val eq by audio.eqSettings.collectAsState()
  val presets by audio.presets.collectAsState()
  val activeId by audio.activePresetId.collectAsState()
  val modified by audio.eqModified.collectAsState()
  val search by audio.deviceSearch.collectAsState()
  val spatial by audio.spatial.collectAsState()
  val loudness by audio.loudness.collectAsState()
  val outputs by audio.outputs.collectAsState()
  val playing by state.isPlaying.collectAsState()
  val reduce by state.reduceMotion.collectAsState()
  val controller = remember { PlayerHost.controller(state) }
  val ui by controller.ui.collectAsState()
  CompositionLocalProvider(LocalReducedMotion provides reduce) {
    AudioScreen(
      capabilities = audio.capabilities(playing),
      eqSettings = eq,
      presets = presets,
      activePresetId = activeId,
      eqModified = modified,
      isPlaying = playing,
      deviceSearch = search,
      onOpenDevicePicker = audio::openDevicePicker,
      onSearchDevices = audio::searchDevices,
      onApplyDevice = audio::applyDevice,
      onClearDevice = audio::clearDevice,
      outputCorrection = audio.outputCorrection(),
      onSetAutoOutputCorrection = audio::setAutoCorrection,
      crossfadeSeconds = ui.crossfadeSeconds,
      onSetCrossfade = audio.setCrossfade,
      onSetEnabled = audio::setEnabled,
      onUpdateEq = audio::updateEq,
      onUpdateBand = audio::updateBand,
      onSelectPreset = audio::selectPreset,
      onSavePresetAs = audio::savePresetAs,
      onOverwritePreset = audio::overwritePreset,
      onRenamePreset = audio::renamePreset,
      onDeletePreset = audio::deletePreset,
      onReset = audio::reset,
      spatial = spatial,
      spatialActions = audio.spatialActions,
      onCompare = audio::compare,
      loudness = loudness,
      onSetNormalizationMode = audio::setNormalizationMode,
      outputs = outputs,
      onSelectOutput = audio::selectOutput,
    )
  }
}
