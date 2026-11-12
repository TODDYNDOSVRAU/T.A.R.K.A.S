package com.example.ui.screens.audio

import com.example.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import com.example.data.audio.dsp.EqTextFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqSettings
import com.example.ui.components.EqKnob
import com.example.ui.components.TextAction
import com.example.ui.theme.AppText
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space

/**
 * The equalizer as one fixed screen, laid out like a hardware console: the faders take all the height
 * there is, the response strip sits under them, and the controls (power pills, preset, bass and treble)
 * under that. Nothing scrolls unless the screen is too short for the controls. Everything else the
 * page does (presets, spatial, advanced) is reached from the preset pill and the overflow menu.
 */
@Composable
internal fun EqPowerConsole(
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
  onOpenSpatial: (() -> Unit)?,
  onCompare: (Boolean) -> Unit,
  selectedBand: Int,
  onSelectBand: (Int) -> Unit,
  graphicView: StripView,
  paramView: StripView,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  val mode = if (eqSettings.parametric) EqMode.PARAMETRIC else EqMode.GRAPHIC
  val bandIndex = selectedBand.coerceIn(0, (eqSettings.paramBands.size - 1).coerceAtLeast(0))

  BoxWithConstraints(modifier = modifier) {
    // What sits under the faders: response strip, status line, the control row, and the gaps.
    // maxHeight already ends above the floating mini player and tab bar.
    val below = RESPONSE_HEIGHT + STATUS_HEIGHT + CONTROLS_HEIGHT + Space.s * 3
    val stripHeight = (maxHeight - below).coerceAtLeast(MIN_STRIP_HEIGHT)

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      BandStrip(
        settings = eqSettings,
        mode = mode,
        selectedBand = bandIndex,
        graphicView = graphicView,
        paramView = paramView,
        onSelectBand = onSelectBand,
        onUpdateBand = onUpdateBand,
        onUpdateEq = onUpdateEq,
        modifier = Modifier.height(stripHeight)
      )

      EqGraph(
        settings = eqSettings,
        selectedBand = if (mode == EqMode.PARAMETRIC) bandIndex else -1,
        spectrumBars = spectrumBars,
        isPlaying = isPlaying,
        window = if (mode == EqMode.GRAPHIC) graphicView else paramView,
        modifier = Modifier
          .padding(horizontal = Space.page, vertical = Space.s)
          .fillMaxWidth()
          .height(RESPONSE_HEIGHT)
          .testTag("audio_spectrum_graph_card")
      )

      Box(modifier = Modifier.heightIn(min = STATUS_HEIGHT)) {
        CurveStatusRow(eqSettings, capabilities, onCompare = onCompare)
      }
      if (!spectrumAllowed) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(start = Space.page, end = Space.page - Space.s),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.audio_spectrum_permission),
            style = AppText.Footnote,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
          )
          Spacer(Modifier.width(Space.s))
          TextAction(stringResource(R.string.audio_spectrum_show), onAllowSpectrum, modifier = Modifier.testTag("audio_allow_spectrum"))
        }
      }

      ControlRow(
        eqSettings = eqSettings,
        mode = mode,
        activePreset = activePreset,
        eqModified = eqModified,
        onSetEnabled = onSetEnabled,
        onUpdateEq = onUpdateEq,
        presetActions = presetActions,
        onOpenAdvanced = onOpenAdvanced,
        onOpenSpatial = onOpenSpatial,
        modifier = Modifier.padding(horizontal = Space.page).padding(top = Space.s)
      )
      Spacer(Modifier.height(Space.s))
    }
  }
}

private val RESPONSE_HEIGHT = 80.dp
private val STATUS_HEIGHT = 52.dp
private val CONTROLS_HEIGHT = 176.dp
private val LEFT_COLUMN_WIDTH = 132.dp
private val MIN_STRIP_HEIGHT = 260.dp

/**
 * Left: Equ, Limit and the graphic/parametric switch as pills. Middle: the preset pill and an overflow
 * menu. Right: bass and treble knobs with their values.
 */
@Composable
private fun ControlRow(
  eqSettings: EqSettings,
  mode: EqMode,
  activePreset: EqPreset?,
  eqModified: Boolean,
  onSetEnabled: (Boolean) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  presetActions: PresetActions,
  onOpenAdvanced: () -> Unit,
  onOpenSpatial: (() -> Unit)?,
  modifier: Modifier = Modifier
) {
  Row(modifier = modifier.fillMaxWidth().height(CONTROLS_HEIGHT), verticalAlignment = Alignment.Top) {
    Column(modifier = Modifier.width(LEFT_COLUMN_WIDTH), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
      PowerSwitch(
        label = stringResource(R.string.audio_switch_equalizer),
        on = eqSettings.enabled,
        onToggle = { onSetEnabled(!eqSettings.enabled) },
        modifier = Modifier.testTag("audio_dsp_switch")
      )
      PowerSwitch(
        label = stringResource(R.string.audio_switch_limiter),
        on = eqSettings.limiterEnabled,
        onToggle = { onUpdateEq { it.copy(limiterEnabled = !it.limiterEnabled) } },
        modifier = Modifier.testTag("switch_limiter")
      )
      ModeSegments(
        parametric = mode == EqMode.PARAMETRIC,
        onSelect = { parametric -> if (parametric != (mode == EqMode.PARAMETRIC)) onUpdateEq { it.copy(parametric = parametric) } }
      )
    }

    Spacer(Modifier.width(Space.m))

    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = Size.touch)
            .clip(Shapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.audio_choose_preset), onClick = presetActions.onOpen)
            .padding(horizontal = Space.m)
            .testTag("eq_preset_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = (activePreset?.name ?: stringResource(R.string.audio_preset_custom)) + if (eqModified) " •" else "",
            style = AppText.Subhead.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        OverflowMenu(eqSettings, activePreset, eqModified, presetActions, onOpenAdvanced, onOpenSpatial, onUpdateEq)
      }
      Spacer(Modifier.height(Space.s))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        ToneControl(stringResource(R.string.audio_tone_bass), eqSettings.bassDb, eqSettings.enabled, "knob_bass") { db -> onUpdateEq { it.copy(bassDb = db) } }
        ToneControl(stringResource(R.string.audio_tone_treble), eqSettings.trebleDb, eqSettings.enabled, "knob_treble") { db -> onUpdateEq { it.copy(trebleDb = db) } }
      }
    }
  }
}

/** A knob with its name above it and its value beside it, as the tone controls of a console. */
@Composable
private fun ToneControl(label: String, db: Float, enabled: Boolean, testTag: String, onChange: (Float) -> Unit) {
  val colors = MaterialTheme.colorScheme
  val range = EqSettings.TONE_RANGE_DB
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(label, style = AppText.Caption1.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface, maxLines = 1)
      Spacer(Modifier.width(Space.xs))
      Text(
        formatDb(db),
        style = AppText.Caption2.copy(fontFeatureSettings = "tnum"),
        color = if (db == 0f) colors.onSurfaceVariant else colors.primary,
        maxLines = 1
      )
    }
    Spacer(Modifier.height(Space.xs))
    EqKnob(
      value = (db + range) / (2f * range),
      onChange = { v -> onChange(snap(v * 2f * range - range)) },
      label = label,
      stateText = "${formatDb(db)} dB",
      size = 60.dp,
      bipolar = true,
      enabled = enabled,
      steps = (2 * range).toInt() - 1,
      modifier = Modifier.testTag(testTag)
    )
  }
}

/** An on/off switch with its full name: the switch shows the state, the label stays readable. */
@Composable
private fun PowerSwitch(label: String, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = Size.touch)
      .clip(Shapes.pill)
      .background(colors.surfaceContainerHigh)
      .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
      .padding(start = Space.m, end = Space.s),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      label,
      style = AppText.Caption1.copy(fontWeight = FontWeight.SemiBold),
      color = colors.onSurface,
      maxLines = 1,
      modifier = Modifier.weight(1f)
    )
    // The row is the control (one touch target, one announcement); the switch only draws the state.
    Switch(checked = on, onCheckedChange = null)
  }
}

/** Gráfico or Paramétrico, both always visible; the chosen one is lit. */
@Composable
private fun ModeSegments(parametric: Boolean, onSelect: (Boolean) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(Shapes.pill)
      .background(colors.surfaceContainerHigh)
      .padding(Space.xxs)
      .testTag("eq_mode_toggle")
  ) {
    listOf(false to stringResource(R.string.audio_mode_graphic), true to stringResource(R.string.audio_mode_parametric)).forEach { (isParam, name) ->
      val chosen = parametric == isParam
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = Size.touch - 4.dp)
          .clip(Shapes.pill)
          .background(if (chosen) colors.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
          .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(isParam) })
          .testTag(if (isParam) "eq_mode_parametric" else "eq_mode_graphic"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          name,
          style = AppText.Caption1.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal),
          color = if (chosen) colors.primary else colors.onSurfaceVariant,
          maxLines = 1
        )
      }
    }
  }
}

/** The preset's actions and the way into the advanced settings. */
@Composable
private fun OverflowMenu(
  settings: EqSettings,
  preset: EqPreset?,
  modified: Boolean,
  actions: PresetActions,
  onOpenAdvanced: () -> Unit,
  onOpenSpatial: (() -> Unit)?,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit
) {
  var open by remember { mutableStateOf(false) }
  val context = LocalContext.current
  // Resolved when the menu item is chosen, from the same context, so the toast follows the app language.
  fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
  val clipboard = { context.getSystemService(ClipboardManager::class.java) }
  val isCustom = preset?.isCustom == true
  Box {
    Box(
      modifier = Modifier
        .size(Size.touch)
        .clip(Shapes.pill)
        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.audio_more_options)) { open = true }
        .testTag("eq_more"),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.audio_more_options), tint = MaterialTheme.colorScheme.onSurface)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      @Composable
      fun item(text: String, tag: String? = null, onClick: () -> Unit) = DropdownMenuItem(
        text = { Text(text, style = AppText.Body) },
        onClick = { open = false; onClick() },
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier
      )
      item(stringResource(R.string.audio_menu_save_as), "eq_save_as", actions.onSaveAs)
      if (isCustom && modified) item(stringResource(R.string.audio_menu_update, preset!!.name), onClick = actions.onOverwrite)
      if (isCustom) {
        item(stringResource(R.string.audio_action_rename), onClick = actions.onRename)
        item(stringResource(R.string.audio_menu_delete_preset), onClick = actions.onDelete)
      }
      item(stringResource(R.string.audio_menu_copy_curve), "eq_export_text") {
        clipboard()?.setPrimaryClip(ClipData.newPlainText("Curva", EqTextFormat.export(settings)))
        toast(context.getString(R.string.audio_toast_curve_copied))
      }
      item(stringResource(R.string.audio_menu_import_text), "eq_import_text") {
        val text = clipboard()?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        when (val result = EqTextFormat.parse(text)) {
          null -> toast(context.getString(R.string.audio_toast_import_failed))
          is EqTextFormat.Result.Parametric -> {
            onUpdateEq { EqTextFormat.apply(it, result) }
            toast(context.resources.getQuantityString(R.plurals.audio_toast_imported_filters, result.bands.size, result.bands.size))
          }
          is EqTextFormat.Result.Graphic -> {
            onUpdateEq { EqTextFormat.apply(it, result) }
            toast(context.resources.getQuantityString(R.plurals.audio_toast_imported_points, result.points, result.points, result.gainsDb.size))
          }
        }
      }
      item(stringResource(R.string.audio_menu_reset), "eq_reset", actions.onReset)
      if (onOpenSpatial != null) item(stringResource(R.string.audio_spatial_title), "eq_open_spatial", onOpenSpatial)
      item(stringResource(R.string.audio_menu_advanced), "eq_open_advanced", onOpenAdvanced)
    }
  }
}
