package com.example.ui.screens.audio

import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqSettings
import com.example.ui.screens.SpatialActions
import com.example.ui.screens.SpatialUi
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space

private const val HOLD_MS = 400L

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
 * The equalizer tab is the console and nothing else: the output line, then [EqPowerConsole]. Presets,
 * the import and export of curves and the advanced settings are reached from the preset pill and its
 * overflow menu; Tarkas Spatial opens from that menu in a sheet.
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
  val bandIndex = selectedBand.coerceIn(0, (eqSettings.paramBands.size - 1).coerceAtLeast(0))
  // Each mode keeps its own scroll, so switching back finds the strip where it was.
  val graphicView = remember { StripView(ScrollState(0)) }
  val paramView = remember { StripView(ScrollState(0)) }

  Column(modifier = Modifier.fillMaxSize()) {
    Text(
      text = stringResource(R.string.audio_output_line, capabilities.activeRouteName, formatSampleRate(capabilities.sampleRateHz)),
      style = AppText.Footnote,
      color = colors.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = Space.page).padding(bottom = Space.s)
    )
    EqPowerConsole(
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
      presetActions = presetActions,
      onOpenAdvanced = onOpenAdvanced,
      onOpenSpatial = if (spatialActions != null) ({ showSpatial = true }) else null,
      onCompare = onCompare,
      selectedBand = bandIndex,
      onSelectBand = { selectedBand = it },
      graphicView = graphicView,
      paramView = paramView,
      modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = LocalBarInset.current)
    )
  }

  if (showSpatial && spatialActions != null) {
    ModalBottomSheet(
      onDismissRequest = { showSpatial = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = colors.surface
    ) {
      Column(modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        SpatialPanel(spatial, spatialActions)
        Spacer(Modifier.height(Space.l))
      }
    }
  }
}

/**
 * Under the curve: how much the level is lowered so boosts never distort, a warning when a fone's
 * correction is on while the sound leaves through the loudspeaker, and a hold-to-compare button that
 * plays the music without the equalizer while the finger stays down, or after a tap until the next tap (A/B).
 */
@Composable
internal fun CurveStatusRow(settings: EqSettings, capabilities: AudioCapabilities, onCompare: (Boolean) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val headroom = EqDsp.totalPreampDb(settings)
  val onSpeaker = capabilities.activeRouteType == "ALTO-FALANTE INTEGRADO"
  var comparing by remember { mutableStateOf(false) }
  var latched by remember { mutableStateOf(false) }
  val latestCompare by rememberUpdatedState(onCompare)
  // Leaving the page mid-hold must not leave the equalizer off.
  DisposableEffect(Unit) { onDispose { if (comparing) latestCompare(false) } }
  val compareDescription = stringResource(if (comparing) R.string.audio_compare_listening_desc else R.string.audio_compare_desc)
  val compareState = stringResource(if (comparing) R.string.audio_compare_state_off else R.string.audio_compare_state_on)

  Row(
    modifier = Modifier.fillMaxWidth().padding(start = Space.page, end = Space.page, top = Space.s),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = if (headroom < -0.05f) stringResource(R.string.audio_headroom_lowered, formatDb(-headroom).removePrefix("+")) else stringResource(R.string.audio_headroom_none),
        style = AppText.Footnote.copy(fontFeatureSettings = "tnum"),
        // The level is being lowered: say so in the accent, not in the neutral grey of "nothing happening".
        color = if (headroom < -0.05f) colors.primary else colors.onSurfaceVariant
      )
      if (settings.device != null && onSpeaker) {
        Text(
          text = stringResource(R.string.audio_speaker_warning),
          style = AppText.Footnote,
          color = colors.primary
        )
      }
    }
    Spacer(Modifier.width(Space.s))
    val fill = if (comparing) colors.primary else colors.surfaceContainer
    Box(
      modifier = Modifier
        .heightIn(min = Size.touch)
        .clip(Shapes.pill)
        .background(fill)
        .pointerInput(settings.enabled) {
          if (!settings.enabled) return@pointerInput
          // Hold: without EQ until the finger lifts. Tap (under 400 ms): stays without EQ until the next tap.
          detectTapGestures(onPress = {
            val wasLatched = latched
            val start = System.currentTimeMillis()
            comparing = true
            latestCompare(true)
            tryAwaitRelease()
            val held = System.currentTimeMillis() - start >= HOLD_MS
            if (wasLatched || held) {
              latched = false
              comparing = false
              latestCompare(false)
            } else {
              latched = true
            }
          })
        }
        .semantics {
          role = Role.Button
          contentDescription = compareDescription
          stateDescription = compareState
          // A screen reader cannot hold: a double tap switches the comparison on and off.
          onClick {
            comparing = !comparing
            latched = comparing
            latestCompare(comparing)
            true
          }
        }
        .padding(horizontal = Space.m)
        .testTag("eq_compare"),
      contentAlignment = Alignment.Center
    ) {
      Text(
        if (comparing) stringResource(if (latched) R.string.audio_compare_latched else R.string.audio_compare_no_eq) else stringResource(R.string.audio_compare_button),
        style = AppText.Subhead,
        color = when {
          !settings.enabled -> colors.onSurface.copy(alpha = 0.38f)
          comparing -> colors.onPrimary
          else -> colors.onSurface
        },
        maxLines = 1
      )
    }
  }
}
