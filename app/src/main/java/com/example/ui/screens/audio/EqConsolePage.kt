package com.example.ui.screens.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.audio.dsp.EqActivity
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqSettings
import com.example.ui.screens.SpatialActions
import com.example.ui.screens.SpatialUi
import com.example.ui.theme.Grid
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Motion
import com.example.ui.theme.rememberReducedMotion

internal const val HOLD_MS = 400L

/** What the preset part of the console can do; grouped so the page takes one parameter for it. */
internal class PresetActions(
  val onOpen: () -> Unit,
  val onSaveAs: () -> Unit,
  val onOverwrite: () -> Unit,
  val onRename: () -> Unit,
  val onDelete: () -> Unit,
  val onReset: () -> Unit
)

/**
 * Whether the music plays without the equalizer for a comparison. It is held by the page, so the power
 * pill (hold) and the path bar button (hold or tap to latch) drive the same state, and leaving the page
 * always ends it. [notify] returns the callback that tells the player.
 */
internal class CompareState(private val notify: () -> (Boolean) -> Unit) {
  var comparing by mutableStateOf(false)
    private set
  var latched by mutableStateOf(false)
    private set

  fun start() {
    if (comparing) return
    comparing = true
    notify()(true)
  }

  fun latch() {
    if (comparing) latched = true
  }

  fun stop() {
    latched = false
    if (!comparing) return
    comparing = false
    notify()(false)
  }

  fun toggle() {
    if (comparing) stop() else {
      start()
      latch()
    }
  }
}

/**
 * The equalizer tab: [EqPowerConsole] and the three sheets it opens (Tarkas Spatial, the settings with
 * bass, treble, preamp and limiter, and, inside the band area, the band editor). Presets, the import and
 * export of curves and the advanced page are reached from the preset row and its overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EqConsolePage(
  capabilities: AudioCapabilities,
  eqSettings: EqSettings,
  activePreset: EqPreset?,
  eqModified: Boolean,
  isPlaying: Boolean,
  spectrumBars: () -> List<Float>,
  spectrumAllowed: Boolean,
  onAllowSpectrum: () -> Unit,
  onSetEnabled: (Boolean) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  presetActions: PresetActions,
  onOpenAdvanced: () -> Unit,
  spatial: SpatialUi,
  spatialActions: SpatialActions?,
  onCompare: (Boolean) -> Unit = {}
) {
  val colors = MaterialTheme.colorScheme
  var selectedBand by rememberSaveable { mutableIntStateOf(0) }
  var showSpatial by rememberSaveable { mutableStateOf(false) }
  var showSettings by rememberSaveable { mutableStateOf(false) }
  val bandIndex = selectedBand.coerceIn(0, (eqSettings.bands.size - 1).coerceAtLeast(0))
  val latestCompare by rememberUpdatedState(onCompare)
  val compare = remember { CompareState { latestCompare } }
  // Leaving the page mid-comparison, or switching the equalizer off, must not leave the music without it.
  DisposableEffect(Unit) { onDispose { compare.stop() } }
  LaunchedEffect(eqSettings.enabled) { if (!eqSettings.enabled) compare.stop() }

  EqPowerConsole(
    capabilities = capabilities,
    eqSettings = eqSettings,
    activePreset = activePreset,
    eqModified = eqModified,
    isPlaying = isPlaying,
    spectrumBars = spectrumBars,
    onSetEnabled = onSetEnabled,
    onUpdateEq = onUpdateEq,
    onUpdateBand = onUpdateBand,
    presetActions = presetActions,
    onOpenAdvanced = onOpenAdvanced,
    onOpenSettings = { showSettings = true },
    onOpenSpatial = if (spatialActions != null) ({ showSpatial = true }) else null,
    spatial = spatial,
    compare = compare,
    selectedBand = bandIndex,
    onSelectBand = { selectedBand = it },
    modifier = Modifier.fillMaxSize().padding(top = Grid.u12, bottom = LocalBarInset.current)
  )

  if (showSpatial && spatialActions != null) {
    ModalBottomSheet(
      sheetMaxWidth = Grid.u384 * 2,
      onDismissRequest = { showSpatial = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = colors.surface
    ) {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        SpatialPanel(spatial, spatialActions)
        Spacer(Modifier.height(Grid.u24))
      }
    }
  }

  if (showSettings) {
    ModalBottomSheet(
      sheetMaxWidth = Grid.u384 * 2,
      onDismissRequest = { showSettings = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = colors.surface
    ) {
      EqSettingsSheet(
        settings = eqSettings,
        spectrumAllowed = spectrumAllowed,
        onAllowSpectrum = onAllowSpectrum,
        onUpdateEq = onUpdateEq,
        onOpenAdvanced = { showSettings = false; onOpenAdvanced() }
      )
    }
  }
}

/**
 * True while the spatial stage is on, so the preamp shown is the one applied: the automatic preamp lowers the
 * level [EqDsp.SPATIAL_HEADROOM_DB] more then. Recomposes when the effect is switched or its state changes.
 */
@Composable
internal fun rememberSpatialStage(): Boolean {
  val status by com.example.data.audio.spatial.SpatialControl.status.collectAsState()
  return status.let { com.example.data.audio.spatial.SpatialControl.active }
}

/**
 * Under the headroom line, what the output stage is doing right now: the limiter lowering the level, a clip
 * (a sample that reached the end of the range), and, while comparing without the equalizer, how much the
 * untouched signal was lowered or raised to play at the same volume. Each line opens and closes with the
 * same spring as the rest of the console, and keeps the words while it closes.
 */
@Composable
internal fun OutputStageNotes(settings: EqSettings, comparing: Boolean) {
  val colors = MaterialTheme.colorScheme
  val reduced = rememberReducedMotion()
  val activity by EqActivity.state.collectAsState()
  val spatial = rememberSpatialStage()
  val match = remember(settings, spatial) { EqDsp.loudnessMatchDb(settings, spatial) }
  // The words of a line that is closing must not turn to "0.0 dB" on the way out.
  val lastReduction = remember { mutableFloatStateOf(0f) }
  val lastMatch = remember { mutableFloatStateOf(0f) }
  LaunchedEffect(activity.reductionDb) { if (activity.limiting) lastReduction.floatValue = activity.reductionDb }
  LaunchedEffect(match) { lastMatch.floatValue = match }
  val enter = if (reduced) EnterTransition.None else expandVertically(Motion.gel()) + fadeIn()
  val exit = if (reduced) ExitTransition.None else shrinkVertically(Motion.gelExit()) + fadeOut()
  val style = EqType.Mono

  AnimatedVisibility(visible = settings.enabled && activity.limiting, enter = enter, exit = exit) {
    Text(
      stringResource(R.string.audio_limiter_working, formatDb(-lastReduction.floatValue).removePrefix("-")),
      style = style,
      color = colors.primary,
      modifier = Modifier.testTag("eq_limiter_note")
    )
  }
  AnimatedVisibility(visible = settings.enabled && activity.clipped, enter = enter, exit = exit) {
    Text(stringResource(R.string.audio_clipping), style = style, color = colors.primary, modifier = Modifier.testTag("eq_clip_note"))
  }
  AnimatedVisibility(visible = comparing && kotlin.math.abs(match) >= EqDsp.LIMITER_SHOWN_DB, enter = enter, exit = exit) {
    Text(
      stringResource(R.string.audio_compare_matched, formatDb(lastMatch.floatValue)),
      style = style,
      color = colors.onSurfaceVariant,
      modifier = Modifier.testTag("eq_match_note")
    )
  }
}
