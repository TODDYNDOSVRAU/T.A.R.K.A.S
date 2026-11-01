package com.example.ui.screens.audio

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import com.example.data.audio.dsp.EqDsp
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqSettings
import com.example.ui.components.EqKnob
import com.example.ui.components.FilterChip
import com.example.ui.components.GroupDivider
import com.example.ui.components.InsetGroup
import com.example.ui.components.ListRow
import com.example.ui.components.SwitchRow
import com.example.ui.components.TextAction
import com.example.ui.screens.SpatialActions
import com.example.ui.screens.SpatialUi
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Space

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
 * The console: the output line, the on/off switches, the response curve across the full width, the
 * band strip, the preset, bass and treble, Tarkas Spatial and the way into the advanced settings.
 */
private val ConsoleTabs = listOf("Curva", "Predefinições", "Espacial")

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
  val enabled = eqSettings.enabled
  val mode = if (eqSettings.parametric) EqMode.PARAMETRIC else EqMode.GRAPHIC
  var selectedBand by rememberSaveable { mutableIntStateOf(0) }
  var tab by rememberSaveable { mutableIntStateOf(0) }
  val bandIndex = selectedBand.coerceIn(0, (eqSettings.paramBands.size - 1).coerceAtLeast(0))
  // Each mode keeps its own scroll, so switching back finds the strip where it was.
  val graphicView = remember { StripView(ScrollState(0)) }
  val paramView = remember { StripView(ScrollState(0)) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    item {
      Text(
        text = "Saída: ${capabilities.activeRouteName} · ${formatSampleRate(capabilities.sampleRateHz)}",
        style = AppText.Footnote,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.page).padding(bottom = Space.m)
      )
    }

    item {
      InsetGroup {
        SwitchRow(
          title = "Equalizador",
          subtitle = bandSummary(eqSettings, mode),
          checked = enabled,
          onCheckedChange = onSetEnabled,
          modifier = Modifier.testTag("audio_dsp_switch")
        )
        GroupDivider()
        SwitchRow(
          title = "Limitador",
          subtitle = "Evita distorção quando a curva soma ganho demais",
          checked = eqSettings.limiterEnabled,
          onCheckedChange = { on -> onUpdateEq { it.copy(limiterEnabled = on) } },
          modifier = Modifier.testTag("switch_limiter")
        )
      }
    }

    // The page is split in three so it fits a screen: the curve, the presets and tone, the spatial scene.
    item {
      Row(
        modifier = Modifier
          .padding(start = Space.page - Space.xs, end = Space.page, top = Space.l)
          .semantics { selectableGroup() }
          .testTag("audio_tabs"),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
      ) {
        ConsoleTabs.forEachIndexed { i, label ->
          FilterChip(
            label = label,
            selected = tab == i,
            onClick = { tab = i },
            modifier = Modifier.testTag("audio_tab_$i")
          )
        }
      }
    }

    if (tab == 0) {
    // The curve as heard, with the strip's window on it
    item {
      EqGraph(
        settings = eqSettings,
        selectedBand = if (mode == EqMode.PARAMETRIC) bandIndex else -1,
        spectrumBars = spectrumBars,
        isPlaying = isPlaying,
        window = if (mode == EqMode.GRAPHIC) graphicView else paramView,
        onSelectBand = { selectedBand = it },
        onUpdateBand = onUpdateBand,
        modifier = Modifier
          .padding(start = Space.page, end = Space.page, top = Space.xl)
          .fillMaxWidth()
          .height(200.dp)
          .testTag("audio_spectrum_graph_card")
      )
    }
    item { CurveStatusRow(eqSettings, capabilities, onCompare = onCompare) }
    // The spectrum behind the curve needs the microphone permission, asked only from here.
    if (!spectrumAllowed) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = Space.page, end = Space.page - Space.s, top = Space.s),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "O Android só entrega o espectro ao vivo com a permissão do microfone. O app não grava nada.",
            style = AppText.Footnote,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
          )
          Spacer(Modifier.width(Space.s))
          TextAction("Ver espectro", onAllowSpectrum, modifier = Modifier.testTag("audio_allow_spectrum"))
        }
      }
    }

    // Graphic or parametric, then the bands
    item {
      Row(
        modifier = Modifier
          .padding(start = Space.page - Space.xs, end = Space.page, top = Space.l)
          .semantics { selectableGroup() }
          .testTag("eq_mode_toggle"),
        horizontalArrangement = Arrangement.spacedBy(Space.xs)
      ) {
        FilterChip(
          label = "Gráfico",
          selected = mode == EqMode.GRAPHIC,
          onClick = { if (mode != EqMode.GRAPHIC) onUpdateEq { it.copy(parametric = false) } }
        )
        FilterChip(
          label = "Paramétrico",
          selected = mode == EqMode.PARAMETRIC,
          onClick = { if (mode != EqMode.PARAMETRIC) onUpdateEq { it.copy(parametric = true) } }
        )
      }
    }
    item {
      BandStrip(
        settings = eqSettings,
        mode = mode,
        selectedBand = bandIndex,
        graphicView = graphicView,
        paramView = paramView,
        onSelectBand = { selectedBand = it },
        onUpdateBand = onUpdateBand,
        onUpdateEq = onUpdateEq,
        modifier = Modifier.padding(top = Space.s)
      )
    }
    }

    if (tab == 1) {
    item { PresetSection(activePreset, eqSettings, eqModified, presetActions) }

    item {
      Section("Graves e agudos") {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          ToneKnob(
            label = "Graves",
            db = eqSettings.bassDb,
            enabled = enabled,
            testTag = "knob_bass",
            onChange = { db -> onUpdateEq { it.copy(bassDb = db) } }
          )
          ToneKnob(
            label = "Agudos",
            db = eqSettings.trebleDb,
            enabled = enabled,
            testTag = "knob_treble",
            onChange = { db -> onUpdateEq { it.copy(trebleDb = db) } }
          )
        }
      }
    }

    }

    if (tab == 2 && spatialActions != null) {
      item { Section(null) { SpatialPanel(spatial, spatialActions) } }
    }

    item {
      Section(null) {
        InsetGroup {
          ListRow(
            title = "Ajustes avançados",
            subtitle = "Alcance (ADB e root), saída, limitador e medidores",
            icon = Icons.Rounded.Tune,
            onClick = onOpenAdvanced,
            modifier = Modifier.testTag("eq_open_advanced")
          )
        }
      }
    }
  }
}

/** "15 bandas · gráfico": what the switch row says under "Equalizador". */
private fun bandSummary(s: EqSettings, mode: EqMode): String {
  val n = s.activeBands.size
  val bands = if (n == 1) "1 banda" else "$n bandas"
  val kind = if (mode == EqMode.GRAPHIC) "gráfico" else "paramétrico"
  return if (s.device != null) "$bands · $kind · com correção do fone" else "$bands · $kind"
}

/** The preset in use and what can be done with it. */
@Composable
private fun PresetSection(preset: EqPreset?, settings: EqSettings, modified: Boolean, actions: PresetActions) {
  val isCustom = preset?.isCustom == true
  Section("Predefinição") {
    InsetGroup {
      ListRow(
        title = (preset?.name ?: "Personalizada") + if (modified) " (editada)" else "",
        subtitle = settings.device?.let { "Fone: ${it.name}" } ?: "Estilos, as suas e fones medidos",
        onClick = actions.onOpen,
        modifier = Modifier.testTag("eq_preset_button")
      )
      GroupDivider()
      ListRow(title = "Salvar como nova…", onClick = actions.onSaveAs, chevron = false, modifier = Modifier.testTag("eq_save_as"))
      if (isCustom && modified) {
        GroupDivider()
        ListRow(title = "Atualizar \"${preset!!.name}\"", onClick = actions.onOverwrite, chevron = false)
      }
      if (isCustom) {
        GroupDivider()
        ListRow(title = "Renomear", onClick = actions.onRename, chevron = false)
        GroupDivider()
        ListRow(title = "Excluir predefinição", onClick = actions.onDelete, chevron = false, destructive = true)
      }
      GroupDivider()
      ListRow(title = "Restaurar padrões", onClick = actions.onReset, chevron = false, modifier = Modifier.testTag("eq_reset"))
    }
  }
}

/** A bass or treble knob with its name and value. */
@Composable
private fun ToneKnob(label: String, db: Float, enabled: Boolean, testTag: String, onChange: (Float) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val range = EqSettings.TONE_RANGE_DB
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    EqKnob(
      value = (db + range) / (2f * range),
      onChange = { v -> onChange(snap(v * 2f * range - range)) },
      label = label,
      stateText = "${formatDb(db)} dB",
      size = 88.dp,
      bipolar = true,
      enabled = enabled,
      // 1 dB per adjust gesture across ±12 dB
      steps = (2 * range).toInt() - 1,
      modifier = Modifier.testTag(testTag)
    )
    Spacer(Modifier.height(Space.s))
    Text(label, style = AppText.Subhead, color = colors.onSurface)
    Text(
      formatDb(db) + " dB",
      style = AppText.Footnote.copy(fontFeatureSettings = "tnum"),
      color = if (db == 0f) colors.onSurfaceVariant else colors.primary
    )
  }
}

/**
 * Under the curve: how much the level is lowered so boosts never distort, a warning when a fone's
 * correction is on while the sound leaves through the loudspeaker, and a hold-to-compare button that
 * plays the music without the equalizer for as long as the finger stays down (A/B).
 */
@Composable
private fun CurveStatusRow(settings: EqSettings, capabilities: AudioCapabilities, onCompare: (Boolean) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val headroom = EqDsp.totalPreampDb(settings)
  val onSpeaker = capabilities.activeRouteType == "ALTO-FALANTE INTEGRADO"
  var comparing by remember { mutableStateOf(false) }
  val latestCompare by rememberUpdatedState(onCompare)
  // Leaving the page mid-hold must not leave the equalizer off.
  DisposableEffect(Unit) { onDispose { if (comparing) latestCompare(false) } }

  Row(
    modifier = Modifier.fillMaxWidth().padding(start = Space.page, end = Space.page, top = Space.s),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = if (headroom < -0.05f) "Nível baixado ${formatDb(-headroom).removePrefix("+")} dB para não distorcer" else "Sem redução de nível",
        style = AppText.Footnote.copy(fontFeatureSettings = "tnum"),
        color = colors.onSurfaceVariant
      )
      if (settings.device != null && onSpeaker) {
        Text(
          text = "A correção do fone está ligada, mas o som sai pelo alto-falante.",
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
          detectTapGestures(onPress = {
            comparing = true
            latestCompare(true)
            tryAwaitRelease()
            comparing = false
            latestCompare(false)
          })
        }
        .semantics {
          role = Role.Button
          contentDescription = if (comparing) "Ouvindo sem equalizador" else "Comparar sem equalizador"
          stateDescription = if (comparing) "Sem equalizador" else "Com equalizador"
          // A screen reader cannot hold: a double tap switches the comparison on and off.
          onClick {
            comparing = !comparing
            latestCompare(comparing)
            true
          }
        }
        .padding(horizontal = Space.m)
        .testTag("eq_compare"),
      contentAlignment = Alignment.Center
    ) {
      Text(
        if (comparing) "Sem EQ" else "Segure: sem EQ",
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
