package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import com.example.data.systemaudio.ActiveAppsState
import com.example.data.systemaudio.EqReach
import com.example.data.systemaudio.GlobalEqStatus
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.data.systemaudio.SystemAudioStatus
import com.example.ui.components.GlassChip
import com.example.ui.components.NamePlaylistDialog
import com.example.ui.components.TarkasSectionTitle
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.glassControl
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val PanelShape = RoundedCornerShape(24.dp)
private const val GRAPH_DB_RANGE = 15f

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
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  var parametric by rememberSaveable { mutableStateOf(false) }
  var selectedBand by rememberSaveable { mutableIntStateOf(8) }
  var showSaveAs by remember { mutableStateOf(false) }
  var showRename by remember { mutableStateOf(false) }
  var showDelete by remember { mutableStateOf(false) }

  val activePreset = presets.firstOrNull { it.id == activePresetId }
  val enabled = eqSettings.enabled
  val bandIndex = selectedBand.coerceIn(0, (eqSettings.bands.size - 1).coerceAtLeast(0))

  // Keep "Apps tocando agora" fresh while this screen is open.
  LaunchedEffect(Unit) {
    while (true) {
      onRefreshActiveApps()
      delay(2500)
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {

    // 1. Master switch and output
    item {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Equalizador", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = colors.onBackground)
          Text(
            text = "Saída: ${capabilities.activeRouteName} · ${String.format("%.1f", capabilities.sampleRateHz / 1000f)} kHz",
            fontSize = 15.sp,
            color = colors.onSurfaceVariant
          )
        }
        Switch(
          checked = enabled,
          onCheckedChange = onSetEnabled,
          colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
          modifier = Modifier.testTag("audio_dsp_switch")
        )
      }
    }

    // 1b. Reach: only the app, every app with ADB, every app with root
    item {
      ReachPanel(reach = eqReach, status = globalEqStatus, rootPresent = rootPresent, onSetReach = onSetEqReach)
    }

    // 2. Presets
    item {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          TarkasSectionTitle("Presets")
          if (eqModified) {
            Spacer(Modifier.width(10.dp))
            Text("editado", fontSize = 15.sp, color = colors.onSurfaceVariant)
          }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(presets, key = { it.id }) { preset ->
            GlassChip(
              label = preset.name,
              selected = preset.id == activePresetId,
              onClick = { onSelectPreset(preset) },
              modifier = Modifier.testTag("profile_chip_${preset.id}")
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          GlassChip(
            label = "Salvar como…",
            icon = Icons.Default.SaveAs,
            onClick = { showSaveAs = true },
            modifier = Modifier.testTag("btn_save_15b_profile")
          )
          if (activePreset?.isCustom == true) {
            if (eqModified) {
              GlassChip(label = "Atualizar", icon = Icons.Default.Save, onClick = onOverwritePreset)
            }
            GlassChip(label = "Renomear", icon = Icons.Default.Edit, onClick = { showRename = true })
            GlassChip(label = "Excluir", icon = Icons.Default.Delete, onClick = { showDelete = true })
          }
          GlassChip(
            label = "Zerar",
            icon = Icons.Default.RestartAlt,
            onClick = onReset,
            modifier = Modifier.testTag("btn_reset_eq_zero")
          )
        }
      }
    }

    // 3. Graph + bands
    item {
      GlassPanel(modifier = Modifier.testTag("equalizer_15_bands_card")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          GlassChip(label = "Gráfico", selected = !parametric, onClick = { parametric = false })
          GlassChip(label = "Paramétrico", selected = parametric, onClick = { parametric = true })
        }
        Spacer(Modifier.height(12.dp))
        EqGraph(
          settings = eqSettings,
          selectedBand = if (parametric) bandIndex else -1,
          spectrumBars = spectrumBars,
          isPlaying = isPlaying,
          onSelectBand = { selectedBand = it; parametric = true },
          onDragBand = { i, freq, gain ->
            selectedBand = i
            onUpdateBand(i) { b -> b.copy(freqHz = freq, gainDb = if (b.type.usesGain) gain else b.gainDb) }
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .testTag("audio_spectrum_graph_card")
        )
        Spacer(Modifier.height(14.dp))
        if (parametric) {
          BandSelector(bands = eqSettings.bands, selected = bandIndex, onSelect = { selectedBand = it })
          Spacer(Modifier.height(14.dp))
          eqSettings.bands.getOrNull(bandIndex)?.let { band ->
            BandEditor(index = bandIndex, band = band, onChange = { t -> onUpdateBand(bandIndex, t) })
          }
        } else {
          GraphicFaders(
            settings = eqSettings,
            enabled = enabled,
            onBandGain = { i, db -> onUpdateBand(i) { it.copy(gainDb = db) } },
            onPreamp = { db -> onUpdateEq { it.copy(preampDb = db) } }
          )
        }
      }
    }

    // 4. Tone
    item {
      GlassPanel {
        PanelTitle("Graves e agudos")
        LabeledSlider(
          label = "Graves",
          valueText = formatDb(eqSettings.bassDb) + " dB",
          value = eqSettings.bassDb,
          range = -EqSettings.TONE_RANGE_DB..EqSettings.TONE_RANGE_DB,
          testTag = "slider_bass",
          onChange = { v -> onUpdateEq { it.copy(bassDb = snap(v)) } }
        )
        LogSlider(
          label = "Frequência dos graves",
          valueText = formatHz(eqSettings.bassFreqHz),
          value = eqSettings.bassFreqHz,
          min = 30f, max = 400f,
          onChange = { v -> onUpdateEq { it.copy(bassFreqHz = v) } }
        )
        Spacer(Modifier.height(8.dp))
        LabeledSlider(
          label = "Agudos",
          valueText = formatDb(eqSettings.trebleDb) + " dB",
          value = eqSettings.trebleDb,
          range = -EqSettings.TONE_RANGE_DB..EqSettings.TONE_RANGE_DB,
          testTag = "slider_treble",
          onChange = { v -> onUpdateEq { it.copy(trebleDb = snap(v)) } }
        )
        LogSlider(
          label = "Frequência dos agudos",
          valueText = formatHz(eqSettings.trebleFreqHz),
          value = eqSettings.trebleFreqHz,
          min = 1500f, max = 16000f,
          onChange = { v -> onUpdateEq { it.copy(trebleFreqHz = v) } }
        )
      }
    }

    // 5. Output: preamp, balance, width, limiter, virtualizer
    item {
      GlassPanel {
        PanelTitle("Saída")
        LabeledSlider(
          label = "Pré-amplificação",
          valueText = formatDb(eqSettings.preampDb) + " dB",
          value = eqSettings.preampDb,
          range = -EqSettings.PREAMP_RANGE_DB..EqSettings.PREAMP_RANGE_DB,
          testTag = "fader_preamp",
          onChange = { v -> onUpdateEq { it.copy(preampDb = snap(v)) } }
        )
        LabeledSlider(
          label = "Balanço",
          valueText = balanceText(eqSettings.balance),
          value = eqSettings.balance,
          range = -1f..1f,
          onChange = { v -> onUpdateEq { it.copy(balance = if (abs(v) < 0.03f) 0f else v) } }
        )
        LabeledSlider(
          label = "Largura estéreo",
          valueText = widthText(eqSettings.stereoWidth),
          value = eqSettings.stereoWidth,
          range = 0f..2f,
          onChange = { v -> onUpdateEq { it.copy(stereoWidth = if (abs(v - 1f) < 0.04f) 1f else v) } }
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Limitador", fontSize = 17.sp, color = colors.onSurface)
            Text(
              "Evita distorção quando a curva soma ganho demais",
              fontSize = 13.sp,
              color = colors.onSurfaceVariant
            )
          }
          Switch(
            checked = eqSettings.limiterEnabled,
            onCheckedChange = { on -> onUpdateEq { it.copy(limiterEnabled = on) } },
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
            modifier = Modifier.testTag("switch_limiter")
          )
        }
        if (eqSettings.limiterEnabled) {
          LabeledSlider(
            label = "Teto do limitador",
            valueText = formatDb(eqSettings.limiterCeilingDb) + " dB",
            value = eqSettings.limiterCeilingDb,
            range = -12f..0f,
            onChange = { v -> onUpdateEq { it.copy(limiterCeilingDb = (v * 2).roundToInt() / 2f) } }
          )
        }
        if (capabilities.isVirtualizerAvailable) {
          LabeledSlider(
            label = "Virtualizador (palco)",
            valueText = "${eqSettings.virtualizerStrength / 10}%",
            value = eqSettings.virtualizerStrength / 1000f,
            range = 0f..1f,
            testTag = "knob_virtualizer",
            onChange = { v -> onUpdateEq { it.copy(virtualizerStrength = (v * 1000).roundToInt()) } }
          )
          Text(
            "Efeito do próprio Android. O resultado muda de aparelho para aparelho.",
            fontSize = 13.sp,
            color = colors.onSurfaceVariant
          )
        }
      }
    }

    // 6. Apps playing now (read from dumpsys audio with the DUMP permission)
    item {
      Column {
        TarkasSectionTitle("Apps tocando agora")
        Spacer(modifier = Modifier.height(10.dp))
        ActiveAppsList(activeApps)
      }
    }

    // 7. System audio level meter (measures other apps' output; does not equalize it)
    item {
      Column {
        TarkasSectionTitle("Medidor de nível")
        Spacer(modifier = Modifier.height(10.dp))
        GlassPanel(modifier = Modifier.testTag("system_audio_capture_card")) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Áudio do sistema (experimental)", fontSize = 17.sp, color = colors.onSurface)
            Text(
              text = if (systemAudioStatus.isCapturing) "Ativo" else "Inativo",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = if (systemAudioStatus.isCapturing) colors.primary else colors.onSurfaceVariant
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Mede o volume do áudio tocado por outros apps, usando a captura de áudio do Android (10 ou mais). " +
              "Apps que bloqueiam a captura não aparecem. Não aplica equalização neles e não identifica a música.",
            fontSize = 15.sp,
            color = colors.onSurfaceVariant,
            lineHeight = 20.sp
          )
          if (systemAudioStatus.isCapturing) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.outline.copy(alpha = 0.4f))
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth(systemAudioStatus.currentRmsEnergy.coerceIn(0.02f, 1f))
                  .height(8.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(colors.primary)
              )
            }
          }
          if (systemAudioStatus.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = systemAudioStatus.errorMessage, fontSize = 15.sp, color = colors.error)
          }
          Spacer(modifier = Modifier.height(12.dp))
          GlassChip(
            label = if (systemAudioStatus.isCapturing) "Reiniciar captura" else "Ativar medidor",
            selected = true,
            enabled = systemAudioStatus.isMediaProjectionSupported,
            onClick = onRequestSystemAudioCapture,
            modifier = Modifier.fillMaxWidth().testTag("btn_request_system_audio_capture")
          )
        }
      }
    }

    // 8. Honest limits of Android audio
    item {
      Text(
        text = if (eqReach == EqReach.APP) {
          "No modo sem root, o equalizador processa só o que o T.A.R.K.A.S. toca. Para os outros apps, escolha ADB ou root em Alcance."
        } else {
          "Nos outros apps valem as bandas, o grave, o agudo, o pré-amplificador e o limitador. Balanço, largura estéreo e " +
            "virtualizador valem só para o que o T.A.R.K.A.S. toca. Alguns apps protegidos (chamadas, DRM) podem recusar o efeito."
        },
        fontSize = 13.sp,
        color = colors.onSurfaceVariant,
        lineHeight = 18.sp
      )
    }
  }

  if (showSaveAs) {
    NamePlaylistDialog(
      title = "Salvar preset",
      confirmLabel = "Salvar",
      initialName = if (activePreset?.isCustom == true) "${activePreset.name} 2" else "Meu preset",
      onConfirm = { name -> onSavePresetAs(name); showSaveAs = false },
      onDismiss = { showSaveAs = false }
    )
  }
  if (showRename && activePreset != null) {
    NamePlaylistDialog(
      title = "Renomear preset",
      confirmLabel = "Salvar",
      initialName = activePreset.name,
      onConfirm = { name -> onRenamePreset(activePreset.id, name); showRename = false },
      onDismiss = { showRename = false }
    )
  }
  if (showDelete && activePreset != null) {
    AlertDialog(
      onDismissRequest = { showDelete = false },
      title = { Text("Excluir \"${activePreset.name}\"?") },
      text = { Text("O som atual continua igual. Só o preset salvo é apagado.") },
      confirmButton = {
        TextButton(onClick = { onDeletePreset(activePreset.id); showDelete = false }) { Text("Excluir") }
      },
      dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } }
    )
  }
}

// ---------------- building blocks ----------------

/** A balloon of the equalizer screen: follows the chosen surface style like every other one. */
@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .glassControl(PanelShape)
      .padding(16.dp),
    content = content
  )
}

@Composable
private fun PanelTitle(text: String) {
  Text(
    text,
    fontSize = 17.sp,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(bottom = 6.dp)
  )
}

@Composable
private fun LabeledSlider(
  label: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit,
  testTag: String? = null
) {
  val colors = MaterialTheme.colorScheme
  Column {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, fontSize = 15.sp, color = colors.onSurface)
      Text(valueText, fontSize = 15.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
    }
    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      onValueChange = onChange,
      valueRange = range,
      colors = SliderDefaults.colors(
        thumbColor = colors.primary,
        activeTrackColor = colors.primary,
        inactiveTrackColor = colors.onSurface.copy(alpha = 0.15f)
      ),
      modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
    )
  }
}

/** Slider on a logarithmic scale, for frequencies and Q, which are heard in ratios, not steps. */
@Composable
private fun LogSlider(
  label: String,
  valueText: String,
  value: Float,
  min: Float,
  max: Float,
  onChange: (Float) -> Unit
) {
  val span = log10(max / min)
  LabeledSlider(
    label = label,
    valueText = valueText,
    value = (log10(value.coerceIn(min, max) / min) / span),
    range = 0f..1f,
    onChange = { t -> onChange(min * 10f.pow(t * span)) }
  )
}

@Composable
private fun BandSelector(bands: List<EqBand>, selected: Int, onSelect: (Int) -> Unit) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    itemsIndexed(bands) { i, band ->
      GlassChip(
        label = formatHz(band.freqHz),
        selected = i == selected,
        onClick = { onSelect(i) },
        modifier = Modifier.testTag("band_chip_$i")
      )
    }
  }
}

@Composable
private fun BandEditor(index: Int, band: EqBand, onChange: ((EqBand) -> EqBand) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Column {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(
        "Banda ${index + 1}",
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = colors.onSurface,
        modifier = Modifier.weight(1f)
      )
      Text("Ativa", fontSize = 15.sp, color = colors.onSurfaceVariant)
      Spacer(Modifier.width(8.dp))
      Switch(
        checked = band.enabled,
        onCheckedChange = { on -> onChange { it.copy(enabled = on) } },
        colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary)
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterType.entries.forEach { type ->
        GlassChip(
          label = type.label,
          selected = band.type == type,
          onClick = { onChange { it.copy(type = type) } },
          modifier = Modifier.testTag("filter_type_${type.key}")
        )
      }
    }
    Spacer(Modifier.height(10.dp))
    LogSlider(
      label = "Frequência",
      valueText = formatHz(band.freqHz) + "Hz",
      value = band.freqHz,
      min = EqBand.MIN_FREQ,
      max = EqBand.MAX_FREQ,
      onChange = { v -> onChange { it.copy(freqHz = v) } }
    )
    if (band.type.usesGain) {
      LabeledSlider(
        label = "Ganho",
        valueText = formatDb(band.gainDb) + " dB",
        value = band.gainDb,
        range = EqBand.MIN_GAIN..EqBand.MAX_GAIN,
        testTag = "slider_band_gain",
        onChange = { v -> onChange { it.copy(gainDb = snap(v)) } }
      )
    }
    LogSlider(
      label = if (band.type.usesGain) "Fator Q (largura)" else "Fator Q (ressonância)",
      valueText = "%.2f".format(band.q),
      value = band.q,
      min = EqBand.MIN_Q,
      max = EqBand.MAX_Q,
      onChange = { v -> onChange { it.copy(q = v) } }
    )
    Text(
      text = if (band.type.usesGain) "Q alto: ajuste estreito, cirúrgico. Q baixo: ajuste largo e suave."
      else "Corta tudo além da frequência. Q perto de 0,7 é o corte mais neutro.",
      fontSize = 13.sp,
      color = colors.onSurfaceVariant
    )
  }
}

/** The graphic mode: one vertical fader per band (gain only), plus the preamp. */
@Composable
private fun GraphicFaders(
  settings: EqSettings,
  enabled: Boolean,
  onBandGain: (Int, Float) -> Unit,
  onPreamp: (Float) -> Unit
) {
  Row(modifier = Modifier.fillMaxWidth()) {
    // Preamp stays in place while the bands scroll
    Box(modifier = Modifier.padding(end = 4.dp)) {
      EqFader(
        value = settings.preampDb / EqSettings.PREAMP_RANGE_DB,
        enabled = enabled,
        name = "Pré",
        readout = formatDb(settings.preampDb),
        widthDp = 52,
        onChange = { v -> onPreamp(snap(v * EqSettings.PREAMP_RANGE_DB)) }
      )
    }
    val scrollState = rememberScrollState()
    // The bands scroll sideways; the edges fade out where more bands wait.
    Row(
      modifier = Modifier
        .weight(1f)
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
          drawContent()
          val fade = 28.dp.toPx()
          val startA = if (scrollState.canScrollBackward) 0f else 1f
          val endA = if (scrollState.canScrollForward) 0f else 1f
          drawRect(
            brush = Brush.horizontalGradient(
              0f to Color.Black.copy(alpha = startA),
              (fade / size.width) to Color.Black,
              (1f - fade / size.width) to Color.Black,
              1f to Color.Black.copy(alpha = endA)
            ),
            blendMode = BlendMode.DstIn
          )
        }
        .horizontalScroll(scrollState),
      horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      settings.bands.forEachIndexed { i, band ->
        EqFader(
          value = band.gainDb / EqBand.MAX_GAIN,
          enabled = enabled && band.type.usesGain,
          name = formatHz(band.freqHz),
          readout = if (band.type.usesGain) formatDb(band.gainDb) else band.type.label.take(5),
          widthDp = 44,
          modifier = Modifier.testTag("vertical_fader_band_$i"),
          onChange = { v -> onBandGain(i, snap(v * EqBand.MAX_GAIN)) }
        )
      }
    }
  }
}

/**
 * A tall fader: a thin rail, a bar from the 0 dB line to the handle, a rounded handle, and the name
 * and dB readout underneath. [value] runs from -1 (bottom) to +1 (top). Colours follow the theme.
 */
@Composable
private fun EqFader(
  value: Float,
  enabled: Boolean,
  name: String,
  readout: String,
  widthDp: Int,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  var heightPx by remember { mutableStateOf(1f) }
  val boost = colors.primary
  val cut = colors.secondary
  val accent = if (value >= 0f) boost else cut
  val rail = colors.onSurface.copy(alpha = 0.18f)
  val thumb = colors.surface
  val thumbEdge = colors.onSurface.copy(alpha = 0.25f)
  val ink = colors.onSurface

  Column(modifier = modifier.width(widthDp.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier = Modifier
        .width(widthDp.dp)
        .height(190.dp)
        .onSizeChanged { heightPx = it.height.toFloat() }
        .semantics { contentDescription = "$name $readout dB" }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          detectTapGestures { offset -> onChange((1f - 2f * (offset.y / heightPx)).coerceIn(-1f, 1f)) }
        }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          detectVerticalDragGestures { change, _ ->
            change.consume()
            onChange((1f - 2f * (change.position.y / heightPx)).coerceIn(-1f, 1f))
          }
        }
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val thumbH = 24.dp.toPx()
        val thumbW = (size.width - 8.dp.toPx()).coerceAtMost(36.dp.toPx())
        val travelTop = thumbH / 2f
        val travelBottom = size.height - thumbH / 2f
        val centerY = (travelTop + travelBottom) / 2f
        val handleY = travelBottom - (value.coerceIn(-1f, 1f) + 1f) / 2f * (travelBottom - travelTop)

        drawLine(rail, Offset(cx, travelTop), Offset(cx, travelBottom), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(rail, Offset(cx - 8.dp.toPx(), centerY), Offset(cx + 8.dp.toPx(), centerY), strokeWidth = 2f)
        if (enabled && abs(handleY - centerY) > 1f) {
          drawLine(
            accent,
            Offset(cx, minOf(handleY, centerY)),
            Offset(cx, maxOf(handleY, centerY)),
            strokeWidth = 5f,
            cap = StrokeCap.Round
          )
        }
        val left = cx - thumbW / 2f
        drawRoundRect(
          color = if (enabled) thumb else thumb.copy(alpha = 0.5f),
          topLeft = Offset(left, handleY - thumbH / 2f),
          size = Size(thumbW, thumbH),
          cornerRadius = CornerRadius(12.dp.toPx())
        )
        drawRoundRect(
          color = thumbEdge,
          topLeft = Offset(left, handleY - thumbH / 2f),
          size = Size(thumbW, thumbH),
          cornerRadius = CornerRadius(12.dp.toPx()),
          style = Stroke(width = 1.5f)
        )
        drawLine(
          ink.copy(alpha = if (enabled) 0.8f else 0.3f),
          Offset(cx - 8.dp.toPx(), handleY),
          Offset(cx + 8.dp.toPx(), handleY),
          strokeWidth = 2.5f,
          cap = StrokeCap.Round
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, textAlign = TextAlign.Center)
    Text(
      readout,
      fontSize = 11.sp,
      color = if (value == 0f) colors.onSurfaceVariant else accent,
      maxLines = 1,
      textAlign = TextAlign.Center
    )
  }
}

/**
 * The real frequency response of the whole chain (bands, tone shelves and preamp), computed from the
 * same filter maths the audio uses, over 20 Hz to 20 kHz on a log scale. The live spectrum sits
 * behind it. Each band is a node: tap to select it, drag to move its frequency and gain.
 */
@Composable
private fun EqGraph(
  settings: EqSettings,
  selectedBand: Int,
  spectrumBars: () -> List<Float>,
  isPlaying: Boolean,
  onSelectBand: (Int) -> Unit,
  onDragBand: (Int, Float, Float) -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  val curve = remember(settings) {
    List(CURVE_POINTS) { i ->
      val f = xToFreq(i / (CURVE_POINTS - 1f))
      EqDsp.responseDb(settings, f.toDouble()).toFloat()
    }
  }
  val current by rememberUpdatedState(settings)
  val accent = colors.primary
  val grid = colors.onSurface.copy(alpha = 0.10f)
  val zero = colors.onSurface.copy(alpha = 0.30f)
  val label = colors.onSurfaceVariant
  val nodeFill = colors.surface
  val dim = if (settings.enabled) 1f else 0.35f

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(colors.onSurface.copy(alpha = 0.04f))
      .pointerInput(Unit) {
        // Only a touch that starts on a node belongs to the graph; any other touch is left to the
        // page, so the screen still scrolls when the finger passes over the graph.
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val w = size.width.toFloat()
          val h = size.height.toFloat()
          val band = nearestBand(current, down.position, w, h) ?: return@awaitEachGesture
          fun moveTo(p: Offset) {
            val freq = xToFreq((p.x / w).coerceIn(0f, 1f))
            val gain = yToDb(p.y, h).coerceIn(EqBand.MIN_GAIN, EqBand.MAX_GAIN)
            onDragBand(band, freq, snap(gain))
          }
          down.consume()
          val start = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
          if (start == null) {
            // Lifted without moving: a tap selects the band.
            onSelectBand(band)
            return@awaitEachGesture
          }
          moveTo(start.position)
          drag(start.id) { change ->
            change.consume()
            moveTo(change.position)
          }
        }
      }
      .semantics { contentDescription = "Curva do equalizador. Arraste um ponto para mudar frequência e ganho." }
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // Grid: 0 dB, ±6 and ±12 dB; decades of frequency
      listOf(-12f, -6f, 6f, 12f).forEach { db -> drawLine(grid, Offset(0f, dbToY(db, h)), Offset(w, dbToY(db, h)), 1f) }
      drawLine(zero, Offset(0f, dbToY(0f, h)), Offset(w, dbToY(0f, h)), 1.5f)
      listOf(100f, 1000f, 10000f).forEach { f ->
        val x = freqToX(f) * w
        drawLine(grid, Offset(x, 0f), Offset(x, h), 1f)
      }

      // Live spectrum behind the curve (flat while nothing plays)
      val bars = spectrumBars()
      if (isPlaying && bars.isNotEmpty()) {
        val bw = w / bars.size
        bars.forEachIndexed { i, e ->
          val bh = (e * h * 0.85f).coerceAtLeast(2f)
          drawRoundRect(
            color = accent.copy(alpha = 0.12f),
            topLeft = Offset(i * bw + bw * 0.15f, h - bh),
            size = Size(bw * 0.7f, bh),
            cornerRadius = CornerRadius(3f)
          )
        }
      }

      // Response curve with a soft fill towards 0 dB
      val path = Path()
      curve.forEachIndexed { i, db ->
        val x = i / (CURVE_POINTS - 1f) * w
        val y = dbToY(db.coerceIn(-GRAPH_DB_RANGE - 3f, GRAPH_DB_RANGE + 3f), h)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      val fill = Path().apply {
        addPath(path)
        lineTo(w, dbToY(0f, h))
        lineTo(0f, dbToY(0f, h))
        close()
      }
      drawPath(fill, accent.copy(alpha = 0.14f * dim))
      drawPath(path, accent.copy(alpha = dim), style = Stroke(width = 3f, cap = StrokeCap.Round))

      // Band nodes
      settings.bands.forEachIndexed { i, band ->
        val pos = nodePosition(settings, i, w, h)
        val isSel = i == selectedBand
        val r = if (isSel) 9.dp.toPx() else 6.dp.toPx()
        val c = if (band.enabled) accent else label
        if (isSel) drawCircle(accent.copy(alpha = 0.22f), radius = r * 1.9f, center = pos)
        drawCircle(nodeFill, radius = r, center = pos)
        drawCircle(c.copy(alpha = dim), radius = r, center = pos, style = Stroke(width = 2.5f))
      }
    }

    // Axis labels
    Row(
      modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      listOf("20", "100", "1k", "10k", "20k").forEach { Text(it, fontSize = 10.sp, color = label) }
    }
    Text(
      "+12 dB",
      fontSize = 10.sp,
      color = label,
      modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 2.dp)
    )
  }
}

// ---------------- maths and formatting ----------------

private const val CURVE_POINTS = 160
private const val MIN_F = 20f
private const val MAX_F = 20000f

private fun freqToX(f: Float): Float = (log10(f / MIN_F) / log10(MAX_F / MIN_F)).coerceIn(0f, 1f)
private fun xToFreq(x: Float): Float = MIN_F * (MAX_F / MIN_F).pow(x)

private fun dbToY(db: Float, h: Float): Float {
  val pad = 14f
  return h / 2f - db / GRAPH_DB_RANGE * (h / 2f - pad)
}

private fun yToDb(y: Float, h: Float): Float {
  val pad = 14f
  return (h / 2f - y) / (h / 2f - pad) * GRAPH_DB_RANGE
}

/** Where band [i] is drawn: at its gain for peaks and shelves, on the curve for passes. */
private fun nodePosition(s: EqSettings, i: Int, w: Float, h: Float): Offset {
  val b = s.bands[i]
  val x = freqToX(b.freqHz) * w
  val db = if (b.type.usesGain) b.gainDb else EqDsp.responseDb(s, b.freqHz.toDouble()).toFloat()
  return Offset(x, dbToY(db.coerceIn(-GRAPH_DB_RANGE, GRAPH_DB_RANGE), h))
}

private fun nearestBand(s: EqSettings, pos: Offset, w: Float, h: Float): Int? {
  var best: Int? = null
  var bestD = Float.MAX_VALUE
  s.bands.indices.forEach { i ->
    val d = (nodePosition(s, i, w, h) - pos).getDistance()
    if (d < bestD) {
      bestD = d
      best = i
    }
  }
  // Only pick a node near the finger (about a thumb's width).
  return if (bestD < 90f) best else null
}

/** Rounds to 0.5 dB and snaps near zero, so returning a control to flat is easy. */
private fun snap(db: Float): Float {
  val r = (db * 2f).roundToInt() / 2f
  return if (abs(r) < 0.25f) 0f else r
}

private fun formatDb(db: Float): String {
  val rounded = (db * 10f).roundToInt() / 10f
  return if (rounded > 0f) "+${"%.1f".format(rounded)}" else "%.1f".format(rounded)
}

private fun formatHz(f: Float): String = when {
  f >= 10000f -> "${(f / 1000f).roundToInt()}k"
  f >= 1000f -> "%.1fk".format(f / 1000f).replace(".0k", "k")
  else -> "${f.roundToInt()}"
}

private fun balanceText(b: Float): String = when {
  abs(b) < 0.01f -> "Centro"
  b < 0 -> "Esquerda ${(abs(b) * 100).roundToInt()}%"
  else -> "Direita ${(b * 100).roundToInt()}%"
}

private fun widthText(w: Float): String = when {
  w < 0.02f -> "Mono"
  abs(w - 1f) < 0.01f -> "Original"
  else -> "${(w * 100).roundToInt()}%"
}

@Composable
private fun ActiveAppsList(state: ActiveAppsState) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.testTag("active_apps_list")) {
    when (state) {
      is ActiveAppsState.PermissionMissing -> {
        Text(
          text = "Para ver quais apps estão tocando, libere a permissão DUMP uma vez. " +
            "O Android só a concede por computador, com este comando:",
          fontSize = 15.sp,
          lineHeight = 20.sp,
          color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        CommandBox(DUMP_COMMAND)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "A permissão deixa o app ler o estado do áudio. É com ela que o modo ADB encontra os outros apps para equalizar.",
          fontSize = 13.sp,
          lineHeight = 18.sp,
          color = colors.onSurfaceVariant
        )
      }

      is ActiveAppsState.Failed -> {
        Text(text = state.message, fontSize = 15.sp, color = colors.error)
      }

      is ActiveAppsState.Ready -> {
        if (state.apps.isEmpty()) {
          Text(text = "Nenhum outro app está tocando agora.", fontSize = 15.sp, color = colors.onSurfaceVariant)
        } else {
          HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))
          state.apps.forEach { app ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors.primary))
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(app.label, fontSize = 17.sp, color = colors.onSurface)
                Text(text = usageLabel(app.usage), fontSize = 15.sp, color = colors.onSurfaceVariant)
              }
            }
            HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))
          }
        }
      }
    }
  }
}

private const val DUMP_COMMAND = "adb shell pm grant com.aistudio.tarkas.app android.permission.DUMP"

/** A command to type on a computer; a tap copies it. */
@Composable
private fun CommandBox(command: String) {
  val clipboard = LocalClipboardManager.current
  var copied by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .glassControl(RoundedCornerShape(16.dp))
      .clickable {
        clipboard.setText(AnnotatedString(command))
        copied = true
      }
      .padding(12.dp)
  ) {
    Text(command, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
    Text(
      if (copied) "Copiado" else "Toque para copiar",
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(top = 4.dp)
    )
  }
}

/** How far the equalizer reaches, with what each mode needs and what it is doing now. */
@Composable
private fun ReachPanel(reach: EqReach, status: GlobalEqStatus, rootPresent: Boolean, onSetReach: (EqReach) -> Unit) {
  val colors = MaterialTheme.colorScheme
  GlassPanel(modifier = Modifier.testTag("eq_reach_panel")) {
    PanelTitle("Alcance do equalizador")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      EqReach.entries.forEach { r ->
        GlassChip(
          label = r.label,
          selected = r == reach,
          onClick = { onSetReach(r) },
          modifier = Modifier.weight(1f).testTag("eq_reach_${r.key}")
        )
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(reach.description, fontSize = 15.sp, lineHeight = 20.sp, color = colors.onSurfaceVariant)

    val line: String? = when (reach) {
      EqReach.APP -> null
      EqReach.ADB -> when {
        status.needsDump -> null
        status.apps.isEmpty() -> "Ligado. Nenhum outro app está tocando agora."
        else -> "Equalizando agora: ${status.apps.joinToString(", ")}."
      }
      EqReach.ROOT -> when {
        !rootPresent -> "Este aparelho não tem root (o comando su não existe). Use o modo ADB."
        status.rootUnavailable -> "O root ainda não foi liberado. Permita o T.A.R.K.A.S. no Magisk (ou no seu gerenciador de root) e escolha Root de novo."
        status.apps.isEmpty() -> "Ligado com root" + (if (status.globalOutput) ", incluindo a saída geral." else ".") +
          " Nenhum outro app está tocando agora."
        else -> "Equalizando agora: ${status.apps.joinToString(", ")}" +
          (if (status.globalOutput) ", e a saída geral." else ".")
      }
    }
    line?.let {
      Spacer(Modifier.height(10.dp))
      Text(it, fontSize = 15.sp, lineHeight = 20.sp, color = colors.primary)
    }
    if (reach == EqReach.ADB && status.needsDump) {
      Spacer(Modifier.height(10.dp))
      Text(
        "Falta a permissão DUMP. Ligue a depuração USB, conecte o celular ao computador e rode uma vez:",
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = colors.onSurface
      )
      Spacer(Modifier.height(8.dp))
      CommandBox(DUMP_COMMAND)
      Spacer(Modifier.height(6.dp))
      Text(
        "Até lá, só os players que avisam a própria sessão de áudio são equalizados.",
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = colors.onSurfaceVariant
      )
    }
  }
}

private fun usageLabel(usage: String?): String = when (usage) {
  "USAGE_MEDIA" -> "Música ou vídeo"
  "USAGE_GAME" -> "Jogo"
  "USAGE_VOICE_COMMUNICATION" -> "Chamada de voz"
  "USAGE_ASSISTANT" -> "Assistente"
  "USAGE_NOTIFICATION", "USAGE_ASSISTANCE_SONIFICATION" -> "Som do sistema"
  else -> "Tocando agora"
}
