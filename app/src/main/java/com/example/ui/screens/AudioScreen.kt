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
import androidx.compose.ui.graphics.PathEffect
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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.example.data.autoeq.AutoEqEntry
import com.example.data.model.DeviceCorrection
import com.example.ui.theme.pressClickable
import com.example.ui.components.EqKnob
import com.example.ui.components.EqRailFader
import com.example.ui.components.FilterShapeIcon
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.ui.graphics.compositeOver
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.subPageTransition
import com.example.ui.viewmodel.DeviceSearchState
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val PanelShape = RoundedCornerShape(24.dp)
private const val GRAPH_DB_RANGE = 15f

/** The three pages of the equalizer: everyday controls, technical controls, and the fone list. */
private enum class AudioPage { MAIN, ADVANCED, DEVICES }

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
  modifier: Modifier = Modifier
) {
  var page by rememberSaveable { mutableStateOf(AudioPage.MAIN) }
  var showSaveAs by remember { mutableStateOf(false) }
  var showRename by remember { mutableStateOf(false) }
  var showDelete by remember { mutableStateOf(false) }
  val activePreset = presets.firstOrNull { it.id == activePresetId }
  val reduced = rememberReducedMotion()

  // Back leaves a sub-page first, before it leaves the tab.
  BackHandler(enabled = page != AudioPage.MAIN) { page = AudioPage.MAIN }

  AnimatedContent(
    targetState = page,
    transitionSpec = { subPageTransition(forward = targetState != AudioPage.MAIN, reduced = reduced) },
    label = "audioPage",
    modifier = modifier.fillMaxSize()
  ) { shown ->
    when (shown) {
      AudioPage.MAIN -> EqMainPage(
        capabilities = capabilities,
        eqSettings = eqSettings,
        presets = presets,
        activePresetId = activePresetId,
        activePreset = activePreset,
        eqModified = eqModified,
        isPlaying = isPlaying,
        spectrumBars = spectrumBars,
        onSetEnabled = onSetEnabled,
        onUpdateEq = onUpdateEq,
        onUpdateBand = onUpdateBand,
        onSelectPreset = onSelectPreset,
        onOverwritePreset = onOverwritePreset,
        onReset = onReset,
        onSaveAs = { showSaveAs = true },
        onRename = { showRename = true },
        onDelete = { showDelete = true },
        onOpenDevices = { page = AudioPage.DEVICES; onOpenDevicePicker() },
        onClearDevice = onClearDevice,
        onOpenAdvanced = { page = AudioPage.ADVANCED }
      )
      AudioPage.ADVANCED -> EqAdvancedPage(
        capabilities = capabilities,
        eqSettings = eqSettings,
        systemAudioStatus = systemAudioStatus,
        isPlaying = isPlaying,
        spectrumBars = spectrumBars,
        activeApps = activeApps,
        onRefreshActiveApps = onRefreshActiveApps,
        eqReach = eqReach,
        globalEqStatus = globalEqStatus,
        rootPresent = rootPresent,
        onSetEqReach = onSetEqReach,
        onUpdateEq = onUpdateEq,
        onUpdateBand = onUpdateBand,
        onRequestSystemAudioCapture = onRequestSystemAudioCapture,
        onBack = { page = AudioPage.MAIN }
      )
      AudioPage.DEVICES -> DevicePickerPage(
        state = deviceSearch,
        current = eqSettings.device,
        onSearch = onSearchDevices,
        onRetry = onOpenDevicePicker,
        onPick = { entry -> onApplyDevice(entry) { page = AudioPage.MAIN } },
        onBack = { page = AudioPage.MAIN }
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

// ---------------- main page: what most people touch ----------------

@Composable
private fun EqMainPage(
  capabilities: AudioCapabilities,
  eqSettings: EqSettings,
  presets: List<EqPreset>,
  activePresetId: String,
  activePreset: EqPreset?,
  eqModified: Boolean,
  isPlaying: Boolean,
  spectrumBars: () -> List<Float>,
  onSetEnabled: (Boolean) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onSelectPreset: (EqPreset) -> Unit,
  onOverwritePreset: () -> Unit,
  onReset: () -> Unit,
  onSaveAs: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onOpenDevices: () -> Unit,
  onClearDevice: () -> Unit,
  onOpenAdvanced: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val enabled = eqSettings.enabled

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    // Master switch and output
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

    // The curve, as heard: fone correction + style + faders
    item {
      EqGraph(
        settings = eqSettings,
        selectedBand = -1,
        spectrumBars = spectrumBars,
        isPlaying = isPlaying,
        onSelectBand = {},
        onDragBand = { i, freq, gain ->
          onUpdateBand(i) { b -> b.copy(freqHz = freq, gainDb = if (b.type.usesGain) gain else b.gainDb) }
        },
        modifier = Modifier.fillMaxWidth().height(210.dp).testTag("audio_spectrum_graph_card")
      )
    }

    // Fone correction
    item {
      DeviceCard(device = eqSettings.device, onOpen = onOpenDevices, onClear = onClearDevice)
    }

    // Styles (presets)
    item {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          TarkasSectionTitle("Estilo")
          AnimatedVisibility(visible = eqModified, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut()) {
            Text("  editado", fontSize = 15.sp, color = colors.onSurfaceVariant)
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
          GlassChip(label = "Salvar como…", icon = Icons.Default.SaveAs, onClick = onSaveAs, modifier = Modifier.testTag("btn_save_15b_profile"))
          if (activePreset?.isCustom == true) {
            if (eqModified) GlassChip(label = "Atualizar", icon = Icons.Default.Save, onClick = onOverwritePreset)
            GlassChip(label = "Renomear", icon = Icons.Default.Edit, onClick = onRename)
            GlassChip(label = "Excluir", icon = Icons.Default.Delete, onClick = onDelete)
          }
          GlassChip(label = "Zerar", icon = Icons.Default.RestartAlt, onClick = onReset, modifier = Modifier.testTag("btn_reset_eq_zero"))
        }
      }
    }

    // Bands
    item {
      GlassPanel(modifier = Modifier.testTag("equalizer_15_bands_card")) {
        PanelTitle("Bandas")
        GraphicFaders(
          settings = eqSettings,
          enabled = enabled,
          onBandGain = { i, db -> onUpdateBand(i) { it.copy(gainDb = db) } },
          onPreamp = { db -> onUpdateEq { it.copy(preampDb = db) } }
        )
      }
    }

    // Quick tone
    item {
      // Two knobs, centred at 0 dB: turn up or right to boost, double-tap to reset.
      GlassPanel {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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

    // The way into the technical controls
    item {
      NavRow(
        icon = Icons.Default.Tune,
        title = "Ajustes avançados",
        subtitle = "Paramétrico, alcance (ADB e root), saída, limitador e medidores",
        onClick = onOpenAdvanced,
        modifier = Modifier.testTag("eq_open_advanced")
      )
    }
  }
}

/** The fone in use, or the invitation to choose one. Tapping opens the list. */
@Composable
private fun DeviceCard(device: DeviceCorrection?, onOpen: () -> Unit, onClear: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .glassControl(PanelShape, selected = false)
      .pressClickable(pressedScale = 0.98f, onClick = onOpen)
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag("eq_device_card"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.primary.copy(alpha = if (device != null) 1f else 0.16f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        Icons.Default.Headphones,
        contentDescription = null,
        tint = if (device != null) colors.onPrimary else colors.primary,
        modifier = Modifier.size(24.dp)
      )
    }
    Spacer(Modifier.width(14.dp))
    AnimatedContent(
      targetState = device,
      transitionSpec = { (fadeIn(tween(220, 60)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(100)) + slideOutVertically { -it / 3 }) },
      contentKey = { it?.name to it?.source },
      label = "device",
      modifier = Modifier.weight(1f)
    ) { d ->
      Column {
        Text(
          d?.name ?: "Escolher meu fone",
          fontSize = 17.sp,
          fontWeight = FontWeight.SemiBold,
          color = colors.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          if (d != null) "Correção de ${d.source} · ${d.bands.size} filtros" else "Corrige o som do seu fone com medições de oratory1990, crinacle e outros",
          fontSize = 13.sp,
          lineHeight = 17.sp,
          color = colors.onSurfaceVariant,
          maxLines = 2
        )
      }
    }
    if (device != null) {
      IconButton(onClick = onClear) {
        Icon(Icons.Default.Close, contentDescription = "Remover correção do fone", tint = colors.onSurfaceVariant)
      }
    } else {
      Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
  }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .glassControl(PanelShape)
      .pressClickable(pressedScale = 0.98f, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
      Text(subtitle, fontSize = 13.sp, lineHeight = 17.sp, color = colors.onSurfaceVariant)
    }
    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant)
  }
}

/** Back arrow and title at the top of a sub-page. */
@Composable
private fun SubPageHeader(title: String, onBack: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
    IconButton(onClick = onBack, modifier = Modifier.testTag("eq_subpage_back")) {
      Icon(Icons.Default.ChevronLeft, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
    }
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
  }
}

// ---------------- advanced page: technical controls ----------------

@Composable
private fun EqAdvancedPage(
  capabilities: AudioCapabilities,
  eqSettings: EqSettings,
  systemAudioStatus: SystemAudioStatus,
  isPlaying: Boolean,
  spectrumBars: () -> List<Float>,
  activeApps: ActiveAppsState,
  onRefreshActiveApps: () -> Unit,
  eqReach: EqReach,
  globalEqStatus: GlobalEqStatus,
  rootPresent: Boolean,
  onSetEqReach: (EqReach) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onRequestSystemAudioCapture: () -> Unit,
  onBack: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  var selectedBand by rememberSaveable { mutableIntStateOf(8) }
  val bandIndex = selectedBand.coerceIn(0, (eqSettings.bands.size - 1).coerceAtLeast(0))

  // Keep "Apps tocando agora" fresh while this page is open.
  LaunchedEffect(Unit) {
    while (true) {
      onRefreshActiveApps()
      delay(2500)
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 0.dp, bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    item { SubPageHeader("Ajustes avançados", onBack) }

    // Reach: only the app, every app with ADB, every app with root
    item { ReachPanel(reach = eqReach, status = globalEqStatus, rootPresent = rootPresent, onSetReach = onSetEqReach) }

    // Parametric editor: a strip of band cards (gain fader, filter shape, Q and frequency knobs),
    // the preamp fixed on the left, and the curve underneath showing the selected band.
    item {
      Column {
        PanelTitle("Paramétrico")
        ParametricStrip(
          settings = eqSettings,
          selected = bandIndex,
          onSelect = { selectedBand = it },
          onUpdateBand = onUpdateBand,
          onUpdateEq = onUpdateEq
        )
        Spacer(Modifier.height(12.dp))
        EqGraph(
          settings = eqSettings,
          selectedBand = bandIndex,
          spectrumBars = spectrumBars,
          isPlaying = isPlaying,
          onSelectBand = { selectedBand = it },
          onDragBand = { i, freq, gain ->
            selectedBand = i
            onUpdateBand(i) { b -> b.copy(freqHz = freq, gainDb = if (b.type.usesGain) gain else b.gainDb) }
          },
          modifier = Modifier.fillMaxWidth().height(140.dp)
        )
      }
    }

    // Tone corners
    item {
      GlassPanel {
        PanelTitle("Frequências de graves e agudos")
        LogSlider(
          label = "Frequência dos graves",
          valueText = formatHz(eqSettings.bassFreqHz),
          value = eqSettings.bassFreqHz,
          min = 30f, max = 400f,
          onChange = { v -> onUpdateEq { it.copy(bassFreqHz = v) } }
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

    // Output: preamp, balance, width, limiter, virtualizer
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
        eqSettings.device?.let { d ->
          Text(
            "A correção do fone já reduz ${formatDb(-d.preampDb)} dB para não distorcer.",
            fontSize = 13.sp,
            color = colors.onSurfaceVariant
          )
          Spacer(Modifier.height(6.dp))
        }
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
            Text("Evita distorção quando a curva soma ganho demais", fontSize = 13.sp, color = colors.onSurfaceVariant)
          }
          Switch(
            checked = eqSettings.limiterEnabled,
            onCheckedChange = { on -> onUpdateEq { it.copy(limiterEnabled = on) } },
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = colors.onPrimary),
            modifier = Modifier.testTag("switch_limiter")
          )
        }
        AnimatedVisibility(visible = eqSettings.limiterEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
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
          Text("Efeito do próprio Android. O resultado muda de aparelho para aparelho.", fontSize = 13.sp, color = colors.onSurfaceVariant)
        }
      }
    }

    // Apps playing now (read from dumpsys audio with the DUMP permission)
    item {
      Column {
        TarkasSectionTitle("Apps tocando agora")
        Spacer(modifier = Modifier.height(10.dp))
        ActiveAppsList(activeApps)
      }
    }

    // System audio level meter (measures other apps' output; does not equalize it)
    item {
      Column {
        TarkasSectionTitle("Medidor de nível")
        Spacer(modifier = Modifier.height(10.dp))
        GlassPanel(modifier = Modifier.testTag("system_audio_capture_card")) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
            val level by animateFloatAsState(systemAudioStatus.currentRmsEnergy.coerceIn(0.02f, 1f), spring(stiffness = 900f), label = "level")
            Box(
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.outline.copy(alpha = 0.4f))
            ) {
              Box(modifier = Modifier.fillMaxWidth(level).height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.primary))
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

    // Honest limits of Android audio
    item {
      Text(
        text = if (eqReach == EqReach.APP) {
          "No modo sem root, o equalizador processa só o que o T.A.R.K.A.S. toca. Para os outros apps, escolha ADB ou root em Alcance."
        } else {
          "Nos outros apps valem a correção do fone, as bandas, o grave, o agudo, o pré-amplificador e o limitador. Balanço, " +
            "largura estéreo e virtualizador valem só para o que o T.A.R.K.A.S. toca. Alguns apps protegidos (chamadas, DRM) podem recusar o efeito."
        },
        fontSize = 13.sp,
        color = colors.onSurfaceVariant,
        lineHeight = 18.sp
      )
    }
  }
}

// ---------------- parametric strip ----------------

/** One colour per band, so a card, its node on the curve and its place in the strip read as one. */
private val BandColors = listOf(
  Color(0xFF1E4FD0), Color(0xFF0E7C86), Color(0xFF6A3FD1), Color(0xFFB0368F),
  Color(0xFFC8641C), Color(0xFF2E8B57), Color(0xFF2F7FA8), Color(0xFFAD3A34)
)

private fun bandColor(i: Int): Color = BandColors[i % BandColors.size]

@Composable
private fun ParametricStrip(
  settings: EqSettings,
  selected: Int,
  onSelect: (Int) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit
) {
  val listState = rememberLazyListState()
  // Selecting a band on the curve brings its card into view.
  LaunchedEffect(selected) {
    if (selected in settings.bands.indices) listState.animateScrollToItem(selected)
  }
  Row(modifier = Modifier.fillMaxWidth().height(440.dp)) {
    // Preamp, fixed while the bands scroll
    Column(
      modifier = Modifier.width(64.dp).fillMaxHeight().glassControl(RoundedCornerShape(32.dp)).padding(vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      EqRailFader(
        value = settings.preampDb / EqSettings.PREAMP_RANGE_DB,
        onChange = { v -> onUpdateEq { it.copy(preampDb = snap(v * EqSettings.PREAMP_RANGE_DB)) } },
        enabled = settings.enabled,
        description = "Pré-amplificação",
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("fader_preamp_strip")
      )
      Spacer(Modifier.height(8.dp))
      Text("Pré", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
      Text(formatDb(settings.preampDb), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.width(8.dp))
    LazyRow(
      state = listState,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f).fillMaxHeight()
    ) {
      itemsIndexed(settings.bands) { i, band ->
        BandCard(
          index = i,
          band = band,
          selected = i == selected,
          color = bandColor(i),
          eqEnabled = settings.enabled,
          onSelect = { onSelect(i) },
          onChange = { t -> onSelect(i); onUpdateBand(i, t) },
          onDelete = {
            onUpdateEq { s -> s.copy(bands = s.bands.filterIndexed { j, _ -> j != i }) }
            onSelect((i - 1).coerceAtLeast(0))
          }
        )
      }
      item {
        Column(
          modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          GlassChip(
            label = "Adicionar banda",
            icon = Icons.Default.Add,
            onClick = {
              onUpdateEq { s -> s.copy(bands = s.bands + EqBand(freqHz = 1000f, q = 1.4f)) }
              onSelect(settings.bands.size)
            },
            modifier = Modifier.testTag("eq_add_band")
          )
        }
      }
    }
  }
}

/** A band as a coloured card: gain fader on the left, filter shape, Q knob and frequency knob. */
@Composable
private fun BandCard(
  index: Int,
  band: EqBand,
  selected: Boolean,
  color: Color,
  eqEnabled: Boolean,
  onSelect: () -> Unit,
  onChange: ((EqBand) -> EqBand) -> Unit,
  onDelete: () -> Unit
) {
  val surface = MaterialTheme.colorScheme.surface
  val fill by androidx.compose.animation.animateColorAsState(
    targetValue = if (selected) color else color.copy(alpha = 0.32f).compositeOver(surface),
    animationSpec = tween(260),
    label = "bandFill"
  )
  val lift by animateFloatAsState(if (selected) 1f else 0.97f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "bandLift")
  val live = eqEnabled && band.enabled
  val accent = Color(0xFF7EE3EA)
  val onCard = Color.White

  Column(
    modifier = Modifier
      .width(212.dp)
      .fillMaxHeight()
      .graphicsLayer { scaleX = lift; scaleY = lift }
      .clip(RoundedCornerShape(32.dp))
      .background(fill)
      .pointerInput(Unit) { detectTapGestures { onSelect() } }
      .padding(12.dp)
      .testTag("band_card_$index")
  ) {
    // Band number and its switches
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
      Text("Banda ${index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = onCard, modifier = Modifier.weight(1f).padding(start = 6.dp))
      IconButton(onClick = { onChange { it.copy(enabled = !it.enabled) } }, modifier = Modifier.size(34.dp)) {
        Icon(
          Icons.Default.PowerSettingsNew,
          contentDescription = if (band.enabled) "Desligar banda" else "Ligar banda",
          tint = if (band.enabled) accent else onCard.copy(alpha = 0.45f),
          modifier = Modifier.size(20.dp)
        )
      }
      IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
        Icon(Icons.Default.Delete, contentDescription = "Excluir banda", tint = onCard.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
      }
    }
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
      // Gain
      Column(modifier = Modifier.width(64.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        EqRailFader(
          value = band.gainDb / EqBand.MAX_GAIN,
          onChange = { v -> onChange { it.copy(gainDb = snap(v * EqBand.MAX_GAIN)) } },
          enabled = live && band.type.usesGain,
          accent = accent,
          description = "Ganho da banda ${index + 1}",
          modifier = Modifier.weight(1f).fillMaxWidth()
        )
        ValuePill("Ganho", if (band.type.usesGain) formatDb(band.gainDb) else "—")
      }
      // Shape, Q, frequency
      Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
      ) {
        // Tapping the shape steps through the filter types.
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .pressClickable(pressedScale = 0.92f) {
              onChange { b -> b.copy(type = FilterType.entries[(b.type.ordinal + 1) % FilterType.entries.size]) }
            }
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag("band_type_$index"),
          contentAlignment = Alignment.Center
        ) {
          AnimatedContent(targetState = band.type, label = "filterShape") { t -> FilterShapeIcon(t, color = onCard) }
        }
        Text(band.type.label, fontSize = 11.sp, color = onCard.copy(alpha = 0.8f), maxLines = 1)
        EqKnob(
          value = qToKnob(band.q),
          onChange = { v -> onChange { it.copy(q = knobToQ(v)) } },
          size = 84.dp,
          accent = accent,
          defaultValue = qToKnob(EqBand.DEFAULT_Q),
          enabled = live,
          description = "Q da banda ${index + 1}"
        )
        ValuePill("Q", "%.2f".format(band.q))
        EqKnob(
          value = freqToX(band.freqHz),
          onChange = { v -> onChange { it.copy(freqHz = xToFreq(v)) } },
          size = 84.dp,
          accent = accent,
          defaultValue = freqToX(1000f),
          enabled = live,
          description = "Frequência da banda ${index + 1}"
        )
        ValuePill("Freq", formatHz(band.freqHz))
      }
    }
  }
}

@Composable
private fun ToneKnob(label: String, db: Float, enabled: Boolean, testTag: String, onChange: (Float) -> Unit) {
  val range = EqSettings.TONE_RANGE_DB
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    EqKnob(
      value = (db + range) / (2f * range),
      onChange = { v -> onChange(snap(v * 2f * range - range)) },
      size = 104.dp,
      bipolar = true,
      enabled = enabled,
      description = "$label ${formatDb(db)} dB",
      modifier = Modifier.testTag(testTag)
    )
    Spacer(Modifier.height(6.dp))
    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    Text(formatDb(db) + " dB", fontSize = 13.sp, color = if (db == 0f) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
  }
}

/** A small dark label under a control: name and value, like the readouts of the reference design. */
@Composable
private fun ValuePill(name: String, value: String) {
  Column(
    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 3.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    Text(value, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
  }
}

private fun qToKnob(q: Float): Float = (log10(q.coerceIn(EqBand.MIN_Q, EqBand.MAX_Q) / EqBand.MIN_Q) / log10(EqBand.MAX_Q / EqBand.MIN_Q))
private fun knobToQ(v: Float): Float = EqBand.MIN_Q * (EqBand.MAX_Q / EqBand.MIN_Q).pow(v.coerceIn(0f, 1f))

// ---------------- fone list (AutoEq) ----------------

@Composable
private fun DevicePickerPage(
  state: DeviceSearchState,
  current: DeviceCorrection?,
  onSearch: (String, String?) -> Unit,
  onRetry: () -> Unit,
  onPick: (AutoEqEntry) -> Unit,
  onBack: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    contentPadding = PaddingValues(bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    item { SubPageHeader("Meu fone", onBack) }
    item {
      TextField(
        value = state.query,
        onValueChange = { onSearch(it, state.source) },
        placeholder = { Text("Modelo do fone, ex.: HD 650, Galaxy Buds", fontSize = 16.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
          if (state.query.isNotEmpty()) {
            IconButton(onClick = { onSearch("", state.source) }) { Icon(Icons.Default.Close, contentDescription = "Limpar", modifier = Modifier.size(18.dp)) }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
          unfocusedContainerColor = Color.Transparent,
          focusedContainerColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          focusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().glassControl(RoundedCornerShape(28.dp)).testTag("device_search_input")
      )
    }
    if (state.sources.isNotEmpty()) {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
          item { GlassChip(label = "Todas as medições", selected = state.source == null, onClick = { onSearch(state.query, null) }) }
          items(state.sources.take(10)) { s ->
            GlassChip(label = s, selected = state.source == s, onClick = { onSearch(state.query, s) })
          }
        }
      }
    }
    item {
      AnimatedContent(
        targetState = when {
          state.loading -> "loading"
          state.error != null && state.total == 0 -> "error"
          else -> "count"
        },
        label = "deviceStatus"
      ) { status ->
        when (status) {
          "loading" -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Baixando a lista de fones…", fontSize = 15.sp, color = colors.onSurfaceVariant)
          }
          "error" -> Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(state.error ?: "", fontSize = 15.sp, color = colors.error)
            Spacer(Modifier.height(8.dp))
            GlassChip(label = "Tentar de novo", onClick = onRetry)
          }
          else -> Text(
            if (state.query.isBlank()) "${state.total} fones medidos. Digite o modelo do seu."
            else "${state.results.size} resultado(s)",
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
          )
        }
      }
    }
    if (state.error != null && state.total > 0) {
      item { Text(state.error, fontSize = 13.sp, color = colors.error) }
    }
    items(state.results, key = { it.path }) { entry ->
      val selected = current != null && current.name == entry.name && current.source == entry.source
      DeviceRow(
        entry = entry,
        selected = selected,
        applying = state.applyingPath == entry.path,
        onClick = { onPick(entry) },
        modifier = Modifier.animateItem()
      )
    }
    item {
      Text(
        "Curvas do projeto AutoEq (Jaakko Pasanen, licença MIT), calculadas a partir de medições de oratory1990, " +
          "crinacle, Rtings e outros, rumo ao alvo Harman. Precisa de internet só para baixar.",
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp)
      )
    }
  }
}

@Composable
private fun DeviceRow(entry: AutoEqEntry, selected: Boolean, applying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .pressClickable(pressedScale = 0.98f, onClick = onClick)
      .padding(vertical = 10.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(entry.name, fontSize = 17.sp, color = if (selected) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("${entry.source} · ${entry.form}", fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1)
    }
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
      when {
        applying -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        selected -> Icon(Icons.Default.Check, contentDescription = "Em uso", tint = colors.primary)
      }
    }
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
  val target = remember(settings) {
    FloatArray(CURVE_POINTS) { i ->
      val f = xToFreq(i / (CURVE_POINTS - 1f))
      EqDsp.responseDb(settings, f.toDouble()).toFloat()
    }
  }
  // The fone correction alone, drawn as a dashed guide under the curve.
  val deviceCurve = remember(settings.device) {
    settings.device?.let { d ->
      val alone = EqSettings(bands = emptyList(), device = d)
      FloatArray(CURVE_POINTS) { i -> EqDsp.responseDb(alone, xToFreq(i / (CURVE_POINTS - 1f)).toDouble()).toFloat() }
    }
  }

  // Motion: when the curve jumps (a style, a fone, reset) it morphs into the new shape on a spring
  // that settles with a small overshoot. While a finger drags a node the changes arrive every frame,
  // and the curve follows them directly instead of lagging behind.
  val reduced = rememberReducedMotion()
  val shown = remember { target.copyOf() }
  val from = remember { target.copyOf() }
  val morph = remember { Animatable(1f) }
  val lastChange = remember { longArrayOf(0L) }
  LaunchedEffect(target) {
    val now = android.os.SystemClock.uptimeMillis()
    val continuous = now - lastChange[0] < 160
    lastChange[0] = now
    shown.copyInto(from)
    if (continuous || reduced) {
      morph.snapTo(1f)
    } else {
      morph.snapTo(0f)
      morph.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 240f))
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

      // Fone correction alone: a thin dashed guide
      deviceCurve?.let { dc ->
        val guide = Path()
        dc.forEachIndexed { i, db ->
          val x = i / (CURVE_POINTS - 1f) * w
          val y = dbToY(db.coerceIn(-GRAPH_DB_RANGE - 3f, GRAPH_DB_RANGE + 3f), h)
          if (i == 0) guide.moveTo(x, y) else guide.lineTo(x, y)
        }
        drawPath(
          guide,
          label.copy(alpha = 0.55f * dim),
          style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        )
      }

      // Response curve with a soft fill towards 0 dB
      val p = morph.value
      for (i in shown.indices) shown[i] = from[i] + (target[i] - from[i]) * p
      val path = Path()
      shown.forEachIndexed { i, db ->
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
