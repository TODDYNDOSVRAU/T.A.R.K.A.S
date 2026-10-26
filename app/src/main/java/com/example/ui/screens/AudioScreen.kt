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
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.key
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.animation.scaleOut
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val PanelShape = RoundedCornerShape(24.dp)
private const val GRAPH_DB_RANGE = 15f

/** The two pages of the equalizer: the console everyone uses, and the technical controls. */
private enum class AudioPage { MAIN, ADVANCED }

/** How the bands are shown on the console: one fader each, or one card each with every parameter. */
private enum class EqMode { GRAPHIC, PARAMETRIC }

/**
 * What the band strip shows right now, shared with the overview graph underneath: the strip's
 * scroll and the width of its window, so the graph can draw that window and drag it.
 */
@androidx.compose.runtime.Stable
private class StripView(val scroll: ScrollState) {
  var viewport by mutableIntStateOf(0)
  val total: Int get() = scroll.maxValue + viewport
}

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
  modifier: Modifier = Modifier
) {
  var page by rememberSaveable { mutableStateOf(AudioPage.MAIN) }
  var showPresets by rememberSaveable { mutableStateOf(false) }
  var showSaveAs by remember { mutableStateOf(false) }
  var showRename by remember { mutableStateOf(false) }
  var showDelete by remember { mutableStateOf(false) }
  val activePreset = presets.firstOrNull { it.id == activePresetId }
  val reduced = rememberReducedMotion()
  // Closing the panel also clears its search, so it opens on the full list next time.
  val closePresets = {
    showPresets = false
    if (deviceSearch.query.isNotEmpty()) onSearchDevices("", deviceSearch.source)
  }

  // Back closes the preset panel, then leaves a sub-page, before it leaves the tab.
  BackHandler(enabled = showPresets) { closePresets() }
  BackHandler(enabled = !showPresets && page != AudioPage.MAIN) { page = AudioPage.MAIN }

  Box(modifier = modifier.fillMaxSize()) {
    AnimatedContent(
      targetState = page,
      transitionSpec = { subPageTransition(forward = targetState != AudioPage.MAIN, reduced = reduced) },
      label = "audioPage",
      modifier = Modifier.fillMaxSize()
    ) { shown ->
      when (shown) {
        AudioPage.MAIN -> EqMainPage(
          capabilities = capabilities,
          eqSettings = eqSettings,
          activePreset = activePreset,
          eqModified = eqModified,
          isPlaying = isPlaying,
          spectrumBars = spectrumBars,
          onSetEnabled = onSetEnabled,
          onUpdateEq = onUpdateEq,
          onUpdateBand = onUpdateBand,
          onOverwritePreset = onOverwritePreset,
          onReset = onReset,
          onSaveAs = { showSaveAs = true },
          onRename = { showRename = true },
          onDelete = { showDelete = true },
          onOpenPresets = { showPresets = true; onOpenDevicePicker() },
          onOpenAdvanced = { page = AudioPage.ADVANCED },
          spectrumAllowed = spectrumAllowed,
          onAllowSpectrum = onAllowSpectrum
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

    // The preset panel floats over the console, growing out of the preset button.
    AnimatedVisibility(
      visible = showPresets,
      enter = fadeIn(tween(160)),
      exit = fadeOut(tween(140)),
      modifier = Modifier.fillMaxSize()
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.55f))
          .pointerInput(Unit) { detectTapGestures { closePresets() } }
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
          onClearDevice = onClearDevice,
          onClose = { closePresets() },
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = 14.dp)
            .padding(top = 56.dp, bottom = 24.dp + LocalBarInset.current)
            .animateEnterExit(
              enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 420f), initialScale = 0.86f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.85f)) +
                slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it / 6 },
              exit = scaleOut(tween(140), targetScale = 0.92f) + fadeOut(tween(120))
            )
        )
      }
    }
  }

  if (showSaveAs) {
    NamePlaylistDialog(
      title = "Salvar predefinição",
      confirmLabel = "Salvar",
      initialName = if (activePreset?.isCustom == true) "${activePreset.name} 2" else "Minha predefinição",
      onConfirm = { name -> onSavePresetAs(name); showSaveAs = false },
      onDismiss = { showSaveAs = false }
    )
  }
  if (showRename && activePreset != null) {
    NamePlaylistDialog(
      title = "Renomear predefinição",
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
      text = { Text("O som atual continua igual. Só a predefinição salva é apagada.") },
      confirmButton = {
        TextButton(onClick = { onDeletePreset(activePreset.id); showDelete = false }) { Text("Excluir") }
      },
      dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } }
    )
  }
}

// ---------------- main page: the console ----------------

/**
 * The console, laid out like a hardware EQ: the band strip on top (faders or parametric cards, with
 * the preamp fixed on the left), the whole curve underneath as an overview whose bright window shows
 * and drags the part of the strip in view, then the switches, the preset, and bass and treble.
 */
@Composable
private fun EqMainPage(
  capabilities: AudioCapabilities,
  eqSettings: EqSettings,
  activePreset: EqPreset?,
  eqModified: Boolean,
  isPlaying: Boolean,
  spectrumBars: () -> List<Float>,
  onSetEnabled: (Boolean) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onOverwritePreset: () -> Unit,
  onReset: () -> Unit,
  onSaveAs: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onOpenPresets: () -> Unit,
  onOpenAdvanced: () -> Unit,
  spectrumAllowed: Boolean,
  onAllowSpectrum: () -> Unit
) {
  val colors = MaterialTheme.colorScheme
  val enabled = eqSettings.enabled
  val mode = if (eqSettings.parametric) EqMode.PARAMETRIC else EqMode.GRAPHIC
  var selectedBand by rememberSaveable { mutableIntStateOf(0) }
  val bandIndex = selectedBand.coerceIn(0, (eqSettings.paramBands.size - 1).coerceAtLeast(0))
  // Each mode keeps its own scroll, so switching back finds the strip where it was.
  val graphicView = remember { StripView(ScrollState(0)) }
  val paramView = remember { StripView(ScrollState(0)) }
  val view = if (mode == EqMode.GRAPHIC) graphicView else paramView
  val reduced = rememberReducedMotion()

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Equalizador", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = colors.onBackground)
        Text(
          text = "Saída: ${capabilities.activeRouteName} · ${String.format("%.1f", capabilities.sampleRateHz / 1000f)} kHz",
          fontSize = 15.sp,
          color = colors.onSurfaceVariant
        )
      }
    }

    // Band strip: the two modes cross-fade, each with its own layout.
    item {
      AnimatedContent(
        targetState = mode,
        transitionSpec = {
          if (reduced) EnterTransition.None togetherWith ExitTransition.None
          else (fadeIn(tween(220, 80)) + scaleIn(tween(260, 60), initialScale = 0.97f)) togetherWith fadeOut(tween(120))
        },
        label = "eqMode",
        modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT).padding(horizontal = 12.dp)
      ) { m ->
        when (m) {
          EqMode.GRAPHIC -> GraphicFaders(
            settings = eqSettings,
            enabled = enabled,
            view = graphicView,
            onBandGain = { i, db -> onUpdateBand(i) { it.copy(gainDb = db) } },
            onPreamp = { db -> onUpdateEq { it.copy(preampDb = db) } }
          )
          EqMode.PARAMETRIC -> ParametricStrip(
            settings = eqSettings,
            selected = bandIndex,
            view = paramView,
            onSelect = { selectedBand = it },
            onUpdateBand = onUpdateBand,
            onUpdateEq = onUpdateEq
          )
        }
      }
    }

    // Overview: the curve as heard, with the strip's window on it.
    item {
      Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        EqGraph(
          settings = eqSettings,
          selectedBand = if (mode == EqMode.PARAMETRIC) bandIndex else -1,
          spectrumBars = spectrumBars,
          isPlaying = isPlaying,
          window = view,
          compact = true,
          modifier = Modifier.fillMaxWidth().height(104.dp).testTag("audio_spectrum_graph_card")
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = consoleCaption(eqSettings, mode),
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
          color = colors.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
        // The spectrum behind the curve needs the microphone permission, asked only from here.
        if (!spectrumAllowed) {
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "O Android só entrega o espectro ao vivo com a permissão do microfone. O app não grava nada.",
              fontSize = 13.sp,
              lineHeight = 18.sp,
              color = colors.onSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            GlassChip(
              label = "Ver espectro",
              selected = true,
              onClick = onAllowSpectrum,
              modifier = Modifier.testTag("audio_allow_spectrum")
            )
          }
        }
      }
    }

    // Switches, preset and tone, in the console's lower deck
    item {
      Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          ConsoleToggle(
            label = "Equ",
            on = enabled,
            description = if (enabled) "Desligar equalizador" else "Ligar equalizador",
            onClick = { onSetEnabled(!enabled) },
            modifier = Modifier.testTag("audio_dsp_switch")
          )
          ConsoleToggle(
            label = "Limit",
            on = eqSettings.limiterEnabled,
            description = if (eqSettings.limiterEnabled) "Desligar limitador" else "Ligar limitador",
            onClick = { onUpdateEq { it.copy(limiterEnabled = !it.limiterEnabled) } },
            modifier = Modifier.testTag("switch_limiter")
          )
          ConsoleToggle(
            label = if (mode == EqMode.GRAPHIC) "Gráf" else "Param",
            on = false,
            icon = if (mode == EqMode.GRAPHIC) Icons.Default.GraphicEq else Icons.Default.Tune,
            description = if (mode == EqMode.GRAPHIC) "Mudar para paramétrico" else "Mudar para gráfico",
            onClick = { onUpdateEq { it.copy(parametric = !it.parametric) } },
            modifier = Modifier.testTag("eq_mode_toggle")
          )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            PresetButton(
              preset = activePreset,
              device = eqSettings.device,
              modified = eqModified,
              onClick = onOpenPresets,
              modifier = Modifier.weight(1f)
            )
            ConsoleMenu(
              isCustom = activePreset?.isCustom == true,
              modified = eqModified,
              onSaveAs = onSaveAs,
              onOverwrite = onOverwritePreset,
              onRename = onRename,
              onDelete = onDelete,
              onReset = onReset,
              onOpenAdvanced = onOpenAdvanced
            )
          }
          Spacer(Modifier.height(10.dp))
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
    }

    // The way into the technical controls
    item {
      NavRow(
        icon = Icons.Default.Tune,
        title = "Ajustes avançados",
        subtitle = "Alcance (ADB e root), saída, limitador, frequências de graves e agudos e medidores",
        onClick = onOpenAdvanced,
        modifier = Modifier.padding(horizontal = 20.dp).testTag("eq_open_advanced")
      )
    }
  }
}

private val STRIP_HEIGHT = 360.dp

/** The small caption under the curve, like a console's status line: mode, bands and stages on. */
private fun consoleCaption(s: EqSettings, mode: EqMode): String = buildList {
  if (!s.enabled) add("EQ DESLIGADO")
  else add(if (mode == EqMode.GRAPHIC) "EQ GRÁFICO" else "EQ PARAMÉTRICO")
  add("${s.activeBands.size} ${if (s.activeBands.size == 1) "BANDA" else "BANDAS"}")
  if (s.device != null) add("FONE")
  if (s.limiterEnabled) add("LIMIT")
}.joinToString(" · ")

/** A square-ish console switch: lit when on, plain when off. */
@Composable
private fun ConsoleToggle(
  label: String,
  on: Boolean,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null
) {
  val colors = MaterialTheme.colorScheme
  val content = com.example.ui.theme.glassContentColor(on)
  Box(
    modifier = modifier
      .size(width = 64.dp, height = 44.dp)
      .glassControl(RoundedCornerShape(22.dp), selected = on)
      .pressClickable(pressedScale = 0.92f, onClick = onClick)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center
  ) {
    if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    else Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = content)
  }
}

/** The preset in use (and the fone under it). Opens the preset panel. */
@Composable
private fun PresetButton(preset: EqPreset?, device: DeviceCorrection?, modified: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = modifier
      .heightIn(min = 48.dp)
      .glassControl(RoundedCornerShape(24.dp))
      .pressClickable(pressedScale = 0.97f, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 6.dp)
      .testTag("eq_preset_button"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    AnimatedContent(
      targetState = (preset?.name ?: "Predefinição") + if (modified) " · editada" else "",
      transitionSpec = { (fadeIn(tween(200, 60)) + slideInVertically { it / 3 }) togetherWith (fadeOut(tween(90)) + slideOutVertically { -it / 3 }) },
      label = "presetName"
    ) { name ->
      Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (device != null) {
      Text(device.name, fontSize = 12.sp, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** The ⋮ menu: everything that acts on the preset, and the way to the technical controls. */
@Composable
private fun ConsoleMenu(
  isCustom: Boolean,
  modified: Boolean,
  onSaveAs: () -> Unit,
  onOverwrite: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onReset: () -> Unit,
  onOpenAdvanced: () -> Unit
) {
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }, modifier = Modifier.testTag("eq_menu")) {
      Icon(Icons.Default.MoreVert, contentDescription = "Mais opções", tint = MaterialTheme.colorScheme.onSurface)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
      val close = { open = false }
      MenuEntry("Salvar como…", Icons.Default.SaveAs, close = close, action = onSaveAs)
      MenuEntry("Atualizar predefinição", Icons.Default.Save, enabled = isCustom && modified, close = close, action = onOverwrite)
      MenuEntry("Renomear", Icons.Default.Edit, enabled = isCustom, close = close, action = onRename)
      MenuEntry("Excluir", Icons.Default.Delete, enabled = isCustom, close = close, action = onDelete)
      MenuEntry("Restaurar padrões", Icons.Default.RestartAlt, close = close, action = onReset)
      HorizontalDivider()
      MenuEntry("Ajustes avançados", Icons.Default.Tune, close = close, action = onOpenAdvanced)
    }
  }
}

@Composable
private fun MenuEntry(label: String, icon: ImageVector, close: () -> Unit, action: () -> Unit, enabled: Boolean = true) {
  DropdownMenuItem(
    text = { Text(label, fontSize = 16.sp) },
    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
    enabled = enabled,
    onClick = { close(); action() }
  )
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
  activeApps: ActiveAppsState,
  onRefreshActiveApps: () -> Unit,
  eqReach: EqReach,
  globalEqStatus: GlobalEqStatus,
  rootPresent: Boolean,
  onSetEqReach: (EqReach) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onRequestSystemAudioCapture: () -> Unit,
  onStopSystemAudioCapture: () -> Unit,
  onBack: () -> Unit
) {
  val colors = MaterialTheme.colorScheme

  // The level meter only runs while this page is open: leaving it ends the capture and its service.
  val stopMeter by androidx.compose.runtime.rememberUpdatedState(onStopSystemAudioCapture)
  androidx.compose.runtime.DisposableEffect(Unit) { onDispose { stopMeter() } }

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
            modifier = Modifier.testTag("switch_limiter_advanced")
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
              "Apps que bloqueiam a captura não aparecem. Não aplica equalização neles e não identifica a música. " +
              "Desliga sozinho quando você sai desta página.",
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
            label = if (systemAudioStatus.isCapturing) "Desligar medidor" else "Ativar medidor",
            selected = !systemAudioStatus.isCapturing,
            enabled = systemAudioStatus.isMediaProjectionSupported,
            onClick = if (systemAudioStatus.isCapturing) onStopSystemAudioCapture else onRequestSystemAudioCapture,
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

private val CARD_WIDTH = 204.dp
private val CARD_GAP = 8.dp

@Composable
private fun ParametricStrip(
  settings: EqSettings,
  selected: Int,
  view: StripView,
  onSelect: (Int) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit
) {
  val density = androidx.compose.ui.platform.LocalDensity.current
  // Selecting a band on the curve brings its card to the middle of the strip.
  LaunchedEffect(selected) {
    if (selected !in settings.paramBands.indices || view.viewport == 0) return@LaunchedEffect
    val step = with(density) { (CARD_WIDTH + CARD_GAP).toPx() }
    val card = with(density) { CARD_WIDTH.toPx() }
    val target = (selected * step - (view.viewport - card) / 2f).roundToInt().coerceIn(0, view.scroll.maxValue)
    if (abs(target - view.scroll.value) > 4) view.scroll.animateScrollTo(target, spring(dampingRatio = 0.86f, stiffness = 300f))
  }
  Row(modifier = Modifier.fillMaxSize()) {
    PreampColumn(settings, onUpdateEq)
    Spacer(Modifier.width(8.dp))
    Row(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .onSizeChanged { view.viewport = it.width }
        .edgeFade(view.scroll)
        .horizontalScroll(view.scroll),
      horizontalArrangement = Arrangement.spacedBy(CARD_GAP)
    ) {
      settings.paramBands.forEachIndexed { i, band ->
        key(i) {
          BandCard(
            index = i,
            band = band,
            selected = i == selected,
            color = bandColor(i),
            eqEnabled = settings.enabled,
            onSelect = { onSelect(i) },
            onChange = { t -> onSelect(i); onUpdateBand(i, t) },
            onDelete = {
              onUpdateEq { s -> s.copy(paramBands = s.paramBands.filterIndexed { j, _ -> j != i }) }
              onSelect((i - 1).coerceAtLeast(0))
            }
          )
        }
      }
      Column(
        modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        GlassChip(
          label = "Adicionar banda",
          icon = Icons.Default.Add,
          onClick = {
            onUpdateEq { s -> s.copy(paramBands = s.paramBands + EqBand(freqHz = 1000f, q = 1.4f)) }
            onSelect(settings.paramBands.size)
          },
          modifier = Modifier.testTag("eq_add_band")
        )
      }
    }
  }
}

/** The preamp, fixed at the left of the strip while the bands scroll. */
@Composable
private fun PreampColumn(settings: EqSettings, onUpdateEq: ((EqSettings) -> EqSettings) -> Unit) {
  Column(
    modifier = Modifier.width(60.dp).fillMaxHeight().glassControl(RoundedCornerShape(30.dp)).padding(vertical = 14.dp),
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
}

/** Fades the strip's edges where more bands wait off screen. */
private fun Modifier.edgeFade(scroll: ScrollState): Modifier = this
  .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
  .drawWithContent {
    drawContent()
    val fade = 24.dp.toPx()
    val startA = if (scroll.canScrollBackward) 0f else 1f
    val endA = if (scroll.canScrollForward) 0f else 1f
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

/** A band as a coloured card: gain fader on the left; filter shape, Q knob and frequency knob on the right. */
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
      .width(CARD_WIDTH)
      .fillMaxHeight()
      .graphicsLayer { scaleX = lift; scaleY = lift }
      .clip(RoundedCornerShape(32.dp))
      .background(fill)
      .pointerInput(Unit) { detectTapGestures { onSelect() } }
      .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp)
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
      Column(modifier = Modifier.width(60.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
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
            .semantics { contentDescription = "Filtro ${band.type.label}. Toque para trocar." }
            .padding(horizontal = 16.dp, vertical = 9.dp)
            .testTag("band_type_$index"),
          contentAlignment = Alignment.Center
        ) {
          AnimatedContent(targetState = band.type, label = "filterShape") { t -> FilterShapeIcon(t, color = onCard) }
        }
        EqKnob(
          value = qToKnob(band.q),
          onChange = { v -> onChange { it.copy(q = knobToQ(v)) } },
          size = 78.dp,
          accent = accent,
          defaultValue = qToKnob(EqBand.DEFAULT_Q),
          enabled = live,
          description = "Q da banda ${index + 1}"
        )
        ValuePill("Q", "%.2f".format(band.q))
        EqKnob(
          value = freqToX(band.freqHz),
          onChange = { v -> onChange { it.copy(freqHz = xToFreq(v)) } },
          size = 78.dp,
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

// ---------------- preset panel: styles, own presets and fones in one list ----------------

private enum class PresetFilter(val label: String) { ALL("Todas"), MINE("Minhas"), BUILT_IN("Embutidas"), FONES("Fones (AutoEq)") }

/**
 * One searchable list of everything that shapes the sound: the user's presets, the built-in ones
 * (each with a thumbnail of its curve) and the 8,850 measured fones of AutoEq. A preset replaces
 * the user's curve; a fone is a correction layer underneath it, so it stays when presets change.
 */
@Composable
private fun PresetPanel(
  presets: List<EqPreset>,
  activePresetId: String,
  device: DeviceCorrection?,
  deviceSearch: DeviceSearchState,
  onSearchDevices: (String, String?) -> Unit,
  onRetryDevices: () -> Unit,
  onPickPreset: (EqPreset) -> Unit,
  onPickDevice: (AutoEqEntry) -> Unit,
  onClearDevice: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  var filter by rememberSaveable { mutableStateOf(PresetFilter.ALL) }
  val query = deviceSearch.query
  val words = query.trim().lowercase()
  val mine = presets.filter { it.isCustom && (words.isEmpty() || words in it.name.lowercase()) }
  val builtIn = presets.filter { !it.isCustom && (words.isEmpty() || words in it.name.lowercase()) }
  val showMine = filter == PresetFilter.ALL || filter == PresetFilter.MINE
  val showBuiltIn = filter == PresetFilter.ALL || filter == PresetFilter.BUILT_IN
  val showFones = filter == PresetFilter.ALL || filter == PresetFilter.FONES
  // With no search, "Todas" lists only a taste of the fones; the Fones filter lists them all.
  val fones = if (filter == PresetFilter.ALL && words.isEmpty()) deviceSearch.results.take(12) else deviceSearch.results

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(28.dp))
      .background(colors.surface)
      .pointerInput(Unit) { detectTapGestures { } } // taps inside don't close the panel
      .padding(top = 12.dp)
      .testTag("eq_preset_panel")
  ) {
    // Search
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
      TextField(
        value = query,
        onValueChange = { onSearchDevices(it, deviceSearch.source) },
        placeholder = { Text("Pesquisar estilo ou fone", fontSize = 16.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { onSearchDevices("", deviceSearch.source) }) { Icon(Icons.Default.Close, contentDescription = "Limpar", modifier = Modifier.size(18.dp)) }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
          unfocusedContainerColor = Color.Transparent,
          focusedContainerColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
          focusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.weight(1f).glassControl(RoundedCornerShape(26.dp)).testTag("device_search_input")
      )
      IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fechar", tint = colors.onSurface) }
    }

    // Filters
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
      items(PresetFilter.entries) { f ->
        GlassChip(label = f.label, selected = filter == f, onClick = { filter = f }, modifier = Modifier.testTag("preset_filter_${f.name}"))
      }
    }
    AnimatedVisibility(visible = filter == PresetFilter.FONES && deviceSearch.sources.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
        item { GlassChip(label = "Todas as medições", selected = deviceSearch.source == null, onClick = { onSearchDevices(query, null) }) }
        items(deviceSearch.sources.take(10)) { s ->
          GlassChip(label = s, selected = deviceSearch.source == s, onClick = { onSearchDevices(query, s) })
        }
      }
    }

    // The fone in use, removable here
    if (device != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(colors.primary.copy(alpha = 0.14f))
          .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.Headphones, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(device.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text("Correção de ${device.source} · ${device.bands.size} filtros", fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
        }
        TextButton(onClick = onClearDevice) { Text("Remover") }
      }
      Spacer(Modifier.height(4.dp))
    }

    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
      contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp)
    ) {
      if (showMine && mine.isNotEmpty()) {
        item(key = "h_mine") { PanelSection("Minhas") }
        items(mine, key = { "p_" + it.id }) { p ->
          PresetRow(p.name, "Minha predefinição", p.settings, selected = p.id == activePresetId, onClick = { onPickPreset(p) }, modifier = Modifier.animateItem())
        }
      }
      if (showBuiltIn && builtIn.isNotEmpty()) {
        item(key = "h_builtin") { PanelSection("Embutidas") }
        items(builtIn, key = { "p_" + it.id }) { p ->
          PresetRow(p.name, "Embutida", p.settings, selected = p.id == activePresetId, onClick = { onPickPreset(p) }, modifier = Modifier.animateItem())
        }
      }
      if (showFones) {
        item(key = "h_fones") {
          Column {
            PanelSection("Fones · AutoEq")
            when {
              deviceSearch.loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Baixando a lista de fones…", fontSize = 14.sp, color = colors.onSurfaceVariant)
              }
              deviceSearch.error != null && deviceSearch.total == 0 -> Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(deviceSearch.error, fontSize = 14.sp, color = colors.error)
                Spacer(Modifier.height(8.dp))
                GlassChip(label = "Tentar de novo", onClick = onRetryDevices)
              }
              else -> {
                if (deviceSearch.error != null) Text(deviceSearch.error, fontSize = 13.sp, color = colors.error, modifier = Modifier.padding(horizontal = 12.dp))
                Text(
                  if (words.isEmpty()) "${deviceSearch.total} fones medidos. Pesquise o modelo do seu." else "${deviceSearch.results.size} fone(s)",
                  fontSize = 13.sp,
                  color = colors.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
              }
            }
          }
        }
        items(fones, key = { "d_" + it.path }) { entry ->
          val selected = device != null && device.name == entry.name && device.source == entry.source
          FoneRow(entry, selected, applying = deviceSearch.applyingPath == entry.path, onClick = { onPickDevice(entry) }, modifier = Modifier.animateItem())
        }
        item(key = "credit") {
          Text(
            "Curvas do projeto AutoEq (Jaakko Pasanen, licença MIT), a partir de medições de oratory1990, crinacle, Rtings e outros. " +
              "Precisa de internet só para baixar.",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun PanelSection(title: String) {
  Text(
    title,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
  )
}

/** A preset with a thumbnail of the curve it makes. */
@Composable
private fun PresetRow(name: String, subtitle: String, settings: EqSettings, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(if (selected) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
      .pressClickable(pressedScale = 0.98f, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    CurveThumb(settings, modifier = Modifier.size(width = 72.dp, height = 34.dp))
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (selected) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
    }
    if (selected) Icon(Icons.Default.Check, contentDescription = "Em uso", tint = colors.primary)
  }
}

@Composable
private fun FoneRow(entry: AutoEqEntry, selected: Boolean, applying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(if (selected) colors.primary.copy(alpha = 0.10f) else Color.Transparent)
      .pressClickable(pressedScale = 0.98f, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier.size(width = 72.dp, height = 34.dp).clip(RoundedCornerShape(10.dp)).background(colors.onSurface.copy(alpha = 0.06f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.Headphones, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(entry.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (selected) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("${entry.source} · ${entry.form}", fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1)
    }
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
      when {
        applying -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        selected -> Icon(Icons.Default.Check, contentDescription = "Em uso", tint = colors.primary)
      }
    }
  }
}

/** A tiny drawing of a preset's curve, coloured from bass (warm) to treble (cool). */
@Composable
private fun CurveThumb(settings: EqSettings, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val points = remember(settings) {
    val user = settings.copy(enabled = true, device = null)
    FloatArray(THUMB_POINTS) { i -> EqDsp.responseDb(user, xToFreq(i / (THUMB_POINTS - 1f)).toDouble()).toFloat() }
  }
  val bg = colors.onSurface.copy(alpha = 0.06f)
  val zero = colors.onSurface.copy(alpha = 0.18f)
  val brush = Brush.horizontalGradient(listOf(Color(0xFFFFB45C), colors.primary, Color(0xFF8C7CFF)))
  Canvas(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(bg)) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val range = 12f
    drawLine(zero, Offset(0f, mid), Offset(w, mid), 1f)
    val path = Path()
    points.forEachIndexed { i, db ->
      val x = i / (THUMB_POINTS - 1f) * w
      val y = mid - (db.coerceIn(-range, range) / range) * (mid - 3.dp.toPx())
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, brush, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
  }
}

private const val THUMB_POINTS = 48

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

/** The graphic mode: one tall fader per band (gain only), with the preamp fixed on the left. */
@Composable
private fun GraphicFaders(
  settings: EqSettings,
  enabled: Boolean,
  view: StripView,
  onBandGain: (Int, Float) -> Unit,
  onPreamp: (Float) -> Unit
) {
  Row(modifier = Modifier.fillMaxSize()) {
    EqFader(
      value = settings.preampDb / EqSettings.PREAMP_RANGE_DB,
      enabled = enabled,
      name = "Pré",
      readout = formatDb(settings.preampDb),
      widthDp = 52,
      modifier = Modifier.fillMaxHeight().glassControl(RoundedCornerShape(26.dp)).testTag("fader_preamp"),
      onChange = { v -> onPreamp(snap(v * EqSettings.PREAMP_RANGE_DB)) }
    )
    Spacer(Modifier.width(6.dp))
    Row(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .onSizeChanged { view.viewport = it.width }
        .edgeFade(view.scroll)
        .horizontalScroll(view.scroll),
      horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      settings.bands.forEachIndexed { i, band ->
        EqFader(
          value = band.gainDb / EqBand.MAX_GAIN,
          enabled = enabled && band.type.usesGain,
          name = formatHz(band.freqHz),
          readout = if (band.type.usesGain) formatDb(band.gainDb) else band.type.label.take(5),
          widthDp = 42,
          modifier = Modifier.fillMaxHeight().testTag("vertical_fader_band_$i"),
          onChange = { v -> onBandGain(i, snap(v * EqBand.MAX_GAIN)) }
        )
      }
    }
  }
}

/**
 * A tall fader like a mixing desk's: a rail with printed ticks, a lit bar from the bottom up to the
 * handle, and the name and dB readout underneath. While a finger holds it, the rail lights up and the
 * readout grows, so the value can be read past the thumb. [value] runs from -1 (bottom) to +1 (top).
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
  var held by remember { mutableStateOf(false) }
  val glow by animateFloatAsState(if (held) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 600f), label = "faderHeld")
  val accent = colors.primary
  val rail = colors.onSurface.copy(alpha = 0.18f)
  val tick = colors.onSurface.copy(alpha = 0.28f)
  val thumb = colors.surfaceVariant
  val thumbEdge = colors.onSurface.copy(alpha = 0.3f)
  val ink = colors.onSurface
  val thumbH = 48.dp

  Column(modifier = modifier.width(widthDp.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      modifier = Modifier
        .width(widthDp.dp)
        .weight(1f)
        .onSizeChanged { heightPx = it.height.toFloat() }
        .semantics { contentDescription = "$name $readout dB" }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          val handle = thumbH.toPx()
          fun toValue(y: Float) = (1f - 2f * ((y - handle / 2f) / (heightPx - handle))).coerceIn(-1f, 1f)
          detectTapGestures(
            onPress = {
              held = true
              tryAwaitRelease()
              held = false
            },
            onTap = { offset -> onChange(toValue(offset.y)) }
          )
        }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          val handle = thumbH.toPx()
          fun toValue(y: Float) = (1f - 2f * ((y - handle / 2f) / (heightPx - handle))).coerceIn(-1f, 1f)
          detectVerticalDragGestures(
            onDragStart = { held = true },
            onDragEnd = { held = false },
            onDragCancel = { held = false }
          ) { change, _ ->
            change.consume()
            onChange(toValue(change.position.y))
          }
        }
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val handleH = thumbH.toPx()
        val handleW = (size.width - 12.dp.toPx()).coerceAtMost(28.dp.toPx())
        val top = handleH / 2f
        val bottom = size.height - handleH / 2f
        val mid = (top + bottom) / 2f
        val y = bottom - (value.coerceIn(-1f, 1f) + 1f) / 2f * (bottom - top)

        // Printed scale: ends and 0 dB
        listOf(top, mid, bottom).forEach { ty ->
          val len = if (ty == mid) 9.dp.toPx() else 6.dp.toPx()
          drawLine(tick, Offset(cx - 5.dp.toPx() - len, ty), Offset(cx - 5.dp.toPx(), ty), 1.5f)
          drawLine(tick, Offset(cx + 5.dp.toPx(), ty), Offset(cx + 5.dp.toPx() + len, ty), 1.5f)
        }
        drawLine(rail.copy(alpha = 0.18f + 0.5f * glow), Offset(cx, top), Offset(cx, bottom), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        if (enabled) {
          val lit = if (glow > 0f) androidx.compose.ui.graphics.lerp(accent, Color.White, glow * 0.6f) else accent
          drawLine(lit.copy(alpha = 0.28f), Offset(cx, bottom), Offset(cx, y), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
          drawLine(lit, Offset(cx, bottom), Offset(cx, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        val left = cx - handleW / 2f
        val corner = CornerRadius(handleW / 2f)
        drawRoundRect(if (enabled) thumb else thumb.copy(alpha = 0.5f), Offset(left, y - handleH / 2f), Size(handleW, handleH), corner)
        drawRoundRect(
          thumbEdge.copy(alpha = 0.3f + 0.6f * glow),
          Offset(left, y - handleH / 2f),
          Size(handleW, handleH),
          corner,
          style = Stroke((1.5f + 1.5f * glow).dp.toPx())
        )
        drawLine(
          ink.copy(alpha = if (enabled) 0.9f else 0.3f),
          Offset(cx - handleW * 0.28f, y),
          Offset(cx + handleW * 0.28f, y),
          strokeWidth = 3.dp.toPx(),
          cap = StrokeCap.Round
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, textAlign = TextAlign.Center)
    Text(
      readout,
      fontSize = (11f + 4f * glow).sp,
      fontWeight = if (held) FontWeight.Bold else FontWeight.Normal,
      color = if (value == 0f && !held) colors.onSurfaceVariant else accent,
      maxLines = 1,
      textAlign = TextAlign.Center
    )
  }
}

/**
 * The real frequency response of the whole chain (bands, tone shelves and preamp), computed from the
 * same filter maths the audio uses, over 20 Hz to 20 kHz on a log scale. The live spectrum sits
 * behind it. Each band shows as a dot, for reading only.
 */
@Composable
private fun EqGraph(
  settings: EqSettings,
  selectedBand: Int,
  spectrumBars: () -> List<Float>,
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
  window: StripView? = null,
  compact: Boolean = false
) {
  val colors = MaterialTheme.colorScheme
  val scope = androidx.compose.runtime.rememberCoroutineScope()
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
  val accent = colors.primary
  val grid = colors.onSurface.copy(alpha = 0.10f)
  val zero = colors.onSurface.copy(alpha = 0.30f)
  val label = colors.onSurfaceVariant
  val nodeFill = colors.surface
  val dim = if (settings.enabled) 1f else 0.35f
  // The window is the bright part and the rest is dimmed. On the dark themes that means a light
  // window over a darkened curve; on the light theme a clear window over a faint grey (a black
  // veil there turned the whole graph a heavy grey).
  val darkPage = colors.background.luminance() < 0.5f
  // (Drawn over the curve, so on the light page the window stays clear and only its edge shows.)
  val windowFill = if (darkPage) colors.onSurface.copy(alpha = 0.10f) else Color.Transparent
  val windowEdge = colors.onSurface.copy(alpha = 0.45f)
  val outsideShade = if (darkPage) Color.Black.copy(alpha = 0.32f) else colors.onSurface.copy(alpha = 0.07f)

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(16.dp))
      .background(colors.onSurface.copy(alpha = 0.04f))
      .then(if (window != null) Modifier.overviewGestures(window, scope) else Modifier)
      .semantics { contentDescription = "Curva do equalizador. Arraste para o lado para rolar as bandas." }
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
      settings.activeBands.forEachIndexed { i, band ->
        val pos = nodePosition(settings, i, w, h)
        val isSel = i == selectedBand
        val r = if (isSel) 9.dp.toPx() else 6.dp.toPx()
        val c = if (band.enabled) accent else label
        if (isSel) drawCircle(accent.copy(alpha = 0.22f), radius = r * 1.9f, center = pos)
        drawCircle(nodeFill, radius = r, center = pos)
        drawCircle(c.copy(alpha = dim), radius = r, center = pos, style = Stroke(width = 2.5f))
      }

      // The strip's window: the part of the bands in view, bright, with the rest dimmed
      window?.let { v ->
        val total = v.total
        if (total > 0 && v.scroll.maxValue > 0) {
          val left = v.scroll.value.toFloat() / total * w
          val ww = v.viewport.toFloat() / total * w
          val shade = outsideShade
          drawRect(shade, Offset.Zero, Size(left, h))
          drawRect(shade, Offset(left + ww, 0f), Size(w - left - ww, h))
          val corner = CornerRadius(12.dp.toPx())
          drawRoundRect(windowFill, Offset(left, 0f), Size(ww, h), corner)
          drawRoundRect(windowEdge, Offset(left, 0f), Size(ww, h), corner, style = Stroke(1.5.dp.toPx()))
          // Grip, like the dot a thumb lands on
          drawCircle(windowEdge, radius = 5.dp.toPx(), center = Offset(left + ww - 14.dp.toPx(), h - 14.dp.toPx()))
        }
      }
    }

    // Axis labels
    Row(
      modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      listOf("20", "100", "1k", "10k", "20k").forEach { Text(it, fontSize = 10.sp, color = label) }
    }
    if (!compact) {
      Text(
        "+12 dB",
        fontSize = 10.sp,
        color = label,
        modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 2.dp)
      )
    }
  }
}

/**
 * Gestures of the overview graph: drag sideways to slide the strip's window (and with it the strip),
 * tap to jump the window there. The band nodes only show the curve; they never take a touch.
 * Vertical drags are left alone, so the page still scrolls over the graph.
 */
private fun Modifier.overviewGestures(
  view: StripView,
  scope: kotlinx.coroutines.CoroutineScope
): Modifier = pointerInput(view) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    fun stripPerPx() = view.total / w
    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
      change.consume()
      view.scroll.dispatchRawDelta(over * stripPerPx())
    }
    if (slop == null) {
      val up = currentEvent.changes.firstOrNull { it.id == down.id }
      if (up != null && !up.pressed && !up.isConsumed) {
        val ww = view.viewport.toFloat()
        val target = (down.position.x * stripPerPx() - ww / 2f).roundToInt().coerceIn(0, view.scroll.maxValue)
        scope.launch { view.scroll.animateScrollTo(target, spring(dampingRatio = 0.86f, stiffness = 320f)) }
      }
      return@awaitEachGesture
    }
    horizontalDrag(slop.id) { change ->
      val dx = change.positionChange().x
      change.consume()
      view.scroll.dispatchRawDelta(dx * stripPerPx())
    }
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
  val b = s.activeBands[i]
  val x = freqToX(b.freqHz) * w
  val db = if (b.type.usesGain) b.gainDb else EqDsp.responseDb(s, b.freqHz.toDouble()).toFloat()
  return Offset(x, dbToY(db.coerceIn(-GRAPH_DB_RANGE, GRAPH_DB_RANGE), h))
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
