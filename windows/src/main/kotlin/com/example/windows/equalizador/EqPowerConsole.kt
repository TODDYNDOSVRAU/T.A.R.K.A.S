package com.example.windows.equalizador

import com.example.windows.res.*
import com.example.windows.res.Res
import com.example.windows.res.tr
import com.example.windows.res.trNow
import com.example.windows.res.trPluralNow
import com.example.windows.res.audio_action_rename
import com.example.windows.res.audio_choose_preset
import com.example.windows.res.audio_clipboard_curve
import com.example.windows.res.audio_compare_banner
import com.example.windows.res.audio_compare_desc
import com.example.windows.res.audio_compare_listening_desc
import com.example.windows.res.audio_compare_no_eq
import com.example.windows.res.audio_compare_state_off
import com.example.windows.res.audio_compare_state_on
import com.example.windows.res.audio_correction
import com.example.windows.res.audio_correction_desc
import com.example.windows.res.audio_correction_off
import com.example.windows.res.audio_eq_short
import com.example.windows.res.audio_headroom_label
import com.example.windows.res.audio_headroom_lowered
import com.example.windows.res.audio_headroom_none
import com.example.windows.res.audio_menu_advanced
import com.example.windows.res.audio_menu_copy_curve
import com.example.windows.res.audio_menu_delete_preset
import com.example.windows.res.audio_menu_import_text
import com.example.windows.res.audio_menu_reset
import com.example.windows.res.audio_menu_save_as
import com.example.windows.res.audio_menu_update
import com.example.windows.res.audio_mode_graphic
import com.example.windows.res.audio_mode_parametric
import com.example.windows.res.audio_more_options
import com.example.windows.res.audio_output_line
import com.example.windows.res.audio_preset_custom
import com.example.windows.res.audio_preset_modified_desc
import com.example.windows.res.audio_preset_sub
import com.example.windows.res.audio_preset_sub_modified
import com.example.windows.res.audio_settings_title
import com.example.windows.res.audio_spatial_preset_amplo
import com.example.windows.res.audio_spatial_preset_cinema
import com.example.windows.res.audio_spatial_preset_padrao
import com.example.windows.res.audio_spatial_preset_sutil
import com.example.windows.res.audio_spatial_sub_off
import com.example.windows.res.audio_spatial_sub_on
import com.example.windows.res.audio_spatial_title
import com.example.windows.res.audio_speaker_warning
import com.example.windows.res.audio_switch_equalizer
import com.example.windows.res.audio_toast_curve_copied
import com.example.windows.res.audio_toast_import_failed
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.audio.dsp.EqActivity
import com.example.data.audio.dsp.EqDsp
import com.example.data.audio.dsp.EqTextFormat
import com.example.data.audio.spatial.SpatialPreset
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqPreset
import com.example.data.model.EqSettings
import com.example.ui.theme.Grid
import com.example.ui.theme.LocalBarInset
import kotlin.math.roundToInt

/**
 * The equalizer as one fixed screen, top to bottom: the title with the power pill, the preset row, the
 * curve card, the bands, the dock (view and Tarkas Spatial) and the signal path bar. Bass, treble, the
 * preamp, the limiter and the advanced entry live in the settings sheet. The band area takes the height
 * that is left; below its minimum the page scrolls. In landscape the curve and bands take the width and
 * everything else moves to a column on the right.
 */
@Composable
internal fun EqPowerConsole(
  capabilities: AudioCapabilities,
  eqSettings: EqSettings,
  activePreset: EqPreset?,
  eqModified: Boolean,
  isPlaying: Boolean,
  spectrumBars: () -> List<Float>,
  onSetEnabled: (Boolean) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onUpdateBand: (Int, (EqBand) -> EqBand) -> Unit,
  presetActions: PresetActions,
  onOpenAdvanced: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenSpatial: (() -> Unit)?,
  spatial: SpatialUi,
  compare: CompareState,
  selectedBand: Int,
  onSelectBand: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val mode = if (eqSettings.parametric) EqMode.PARAMETRIC else EqMode.GRAPHIC
  val bandIndex = selectedBand.coerceIn(0, (eqSettings.bands.size - 1).coerceAtLeast(0))

  @Composable
  fun top() = TopRow(capabilities, eqSettings, compare, onSetEnabled)

  @Composable
  fun presets() = PresetRow(eqSettings, activePreset, eqModified, presetActions, onOpenSettings, onOpenAdvanced, onOpenSpatial, onUpdateEq)

  @Composable
  fun curve(m: Modifier) = EqGraph(
    settings = eqSettings,
    selectedBand = if (mode == EqMode.PARAMETRIC) bandIndex else -1,
    spectrumBars = spectrumBars,
    isPlaying = isPlaying,
    modifier = m.testTag("audio_spectrum_graph_card")
  )

  @Composable
  fun bands(m: Modifier) = BandArea(eqSettings, mode, bandIndex, onSelectBand, onUpdateBand, onUpdateEq, m)

  @Composable
  fun dock(m: Modifier) = Dock(mode, spatial, onOpenSpatial, onUpdateEq, m)

  @Composable
  fun path() = PathBar(eqSettings, capabilities, compare)

  BoxWithConstraints(modifier = modifier) {
    val pageHeight = maxHeight
    val pageWidth = maxWidth
    if (maxWidth >= TABLET_MIN_WIDTH && pageHeight >= TABLET_MIN_HEIGHT) {
      Column(
        modifier = Modifier.fillMaxSize().padding(start = Grid.margin, end = Grid.margin, bottom = LocalBarInset.current + Grid.u12),
        verticalArrangement = Arrangement.spacedBy(Grid.u12)
      ) {
        TabletTopBar(
          capabilities, eqSettings, activePreset, eqModified, mode, spatial, compare, presetActions,
          onSetEnabled, onUpdateEq, onOpenSettings, onOpenAdvanced, onOpenSpatial,
          twoRows = pageWidth < TOP_BAR_ONE_ROW_MIN
        )
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
          Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
            if (mode == EqMode.GRAPHIC) {
              Box(Modifier.weight(0.46f).fillMaxWidth()) { curve(Modifier.fillMaxSize()) }
              bands(Modifier.weight(0.54f).fillMaxWidth())
            } else {
              Box(Modifier.weight(1f).fillMaxWidth()) { curve(Modifier.fillMaxSize()) }
              // The tiles take the rows they need at this width (96 dp pieces, 12 dp apart, "+" included)
              // and the curve keeps the rest, instead of a fixed strip that leaves empty rows under them.
              val leftWidth = pageWidth - Grid.margin * 2 - RACK_WIDTH - Grid.u12
              val perRow = ((leftWidth + Grid.u12) / (Grid.u96 + Grid.u12)).toInt().coerceAtLeast(1)
              val tileRows = (eqSettings.bands.size + 1 + perRow - 1) / perRow
              val tilesHeight = (Grid.u48 * tileRows + Grid.u12 * (tileRows - 1) + Grid.u12)
                .coerceIn(Grid.u48 + Grid.u12, Grid.u192 + Grid.u12)
              bands(Modifier.fillMaxWidth().height(tilesHeight))
            }
          }
          TabletToneRack(eqSettings, capabilities, compare, onUpdateEq, onOpenAdvanced)
        }
      }
      return@BoxWithConstraints
    }
    if (maxWidth > maxHeight && maxWidth >= SIDE_MIN_WIDTH) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = Grid.margin),
        horizontalArrangement = Arrangement.spacedBy(Grid.u24)
      ) {
        // The curve and bands take the height there is, and no less than the curve plus the band minimum;
        // below that the column scrolls.
        Box(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
          // A short screen (a phone on its side) gets the curve at half height, so the bands show without scrolling.
          val curveHeight = if (pageHeight < Grid.u192 + Grid.u12 + BANDS_MIN) Grid.u96 else Grid.u192
          Column(
            modifier = Modifier.fillMaxWidth().height(maxOf(pageHeight, curveHeight + Grid.u12 + BANDS_MIN)),
            verticalArrangement = Arrangement.spacedBy(Grid.u12)
          ) {
            curve(Modifier.fillMaxWidth().height(curveHeight))
            bands(Modifier.weight(1f).fillMaxWidth())
          }
        }
        // On a short window the four rows of the control column sit 6 dp apart, so the last one clears the
        // floating bar without scrolling; taller windows keep 12.
        Column(
          modifier = Modifier.width(Grid.u384).fillMaxHeight().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(if (pageHeight < Grid.u384) Grid.u6 else Grid.u12)
        ) {
          top()
          presets()
          dock(Modifier.fillMaxWidth())
          path()
        }
      }
      return@BoxWithConstraints
    }

    // Rows 48 + 48 + 48 + 48 + 48, the curve card 192, and five gaps of 12 around the band area.
    val bandHeight = (maxHeight - FIXED_HEIGHT).coerceIn(BANDS_MIN, BANDS_MAX)
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(start = Grid.margin, end = Grid.margin, bottom = Grid.u12),
      verticalArrangement = Arrangement.spacedBy(Grid.u12)
    ) {
      top()
      presets()
      curve(Modifier.fillMaxWidth().height(Grid.u192))
      bands(Modifier.fillMaxWidth().height(bandHeight))
      dock(Modifier.fillMaxWidth())
      path()
    }
  }
}

// Side by side needs the 384 dp control column and as much again for the curve.
private val SIDE_MIN_WIDTH = Grid.u384 * 2
// The tablet top bar fits one row from here; narrower, it takes two.
private val TOP_BAR_ONE_ROW_MIN = Grid.u384 * 3
// Two value lines, a fader with 120 dp of travel under its 48 dp thumb, and two frequency lines. The band
// area is the block that flexes; where the window is shorter than that, the page scrolls.
private val BANDS_MIN = Grid.u192 + Grid.u48
private val BANDS_MAX = Grid.u192 + Grid.u96
// Four 48 dp rows, the 192 dp curve, five gaps of 12 and 12 below the last row (room above the floating bar).
private val FIXED_HEIGHT = Grid.u48 * 4 + Grid.u192 + Grid.u12 * 6

/** Title and route line on the left; the power pill on the right. Holding the pill plays without the EQ. */
@Composable
private fun TopRow(capabilities: AudioCapabilities, settings: EqSettings, compare: CompareState, onSetEnabled: (Boolean) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(modifier = Modifier.fillMaxWidth().heightIn(min = Grid.u48), verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        stringResource(Res.string.audio_switch_equalizer),
        style = EqType.Title,
        color = colors.onSurface,
        maxLines = 1,
        modifier = Modifier.padding(start = Grid.inset)
      )
      if (compare.comparing) {
        Text(
          stringResource(Res.string.audio_compare_banner),
          style = EqType.Mono,
          color = colors.primary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = Grid.inset).testTag("eq_compare_banner")
        )
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(Grid.u6)
              .clip(CircleShape)
              .background(if (settings.enabled) colors.primary else colors.onSurface.copy(alpha = 0.38f))
          )
          Spacer(Modifier.width(Grid.u6))
          val rate = formatSampleRate(capabilities.sampleRateHz)
          val outputLine = tr(Res.string.audio_output_line, capabilities.activeRouteName, rate)
          Text(
            capabilities.activeRouteName + " · " + rate,
            style = EqType.Mono,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { contentDescription = outputLine }
          )
        }
      }
    }
    PowerPill(settings.enabled, compare, onSetEnabled)
  }
}

/** 96 x 48 pill with a 48 x 24 switch. A tap switches the equalizer; holding it compares against the untouched signal. */
@Composable
private fun PowerPill(on: Boolean, compare: CompareState, onSetEnabled: (Boolean) -> Unit) {
  val colors = MaterialTheme.colorScheme
  var pressed by remember { mutableStateOf(false) }
  var heldCompare by remember { mutableStateOf(false) }
  var longWhileOff by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(150), label = "powerPress")
  val thumb by animateDpAsState(if (on) Grid.u24 else 0.dp, tween(200), label = "powerThumb")
  val track by animateColorAsState(if (on) colors.primary else colors.onSurface.copy(alpha = 0.24f), tween(200), label = "powerTrack")
  val name = stringResource(Res.string.audio_switch_equalizer)
  val hint = stringResource(Res.string.audio_compare_desc)
  Box(
    modifier = Modifier
      .size(width = Grid.u96, height = Grid.u48)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(Grid.r24)
      .background(colors.surfaceContainer)
      .pointerInput(on) {
        detectTapGestures(
          onPress = {
            pressed = true
            longWhileOff = false
            val released = tryAwaitRelease()
            pressed = false
            if (heldCompare) {
              heldCompare = false
              compare.stop()
            }
            // A press past the long-press time while the EQ is off has no tap: a real release still switches it on.
            if (longWhileOff) {
              longWhileOff = false
              if (released) onSetEnabled(true)
            }
          },
          onLongPress = {
            if (on) {
              heldCompare = true
              compare.start()
            } else {
              longWhileOff = true
            }
          },
          onTap = { onSetEnabled(!on) }
        )
      }
      .semantics {
        role = Role.Switch
        contentDescription = "$name. $hint"
        toggleableState = ToggleableState(on)
        onClick { onSetEnabled(!on); true }
      }
      .testTag("audio_dsp_switch"),
    contentAlignment = Alignment.Center
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = Grid.u12),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        stringResource(Res.string.audio_eq_short),
        style = EqType.LabelStrong,
        color = if (on) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.clearAndSetSemantics {}
      )
      // 48 x 24 track; the 18 dp thumb sits 3 dp inside it and travels 24.
      Box(
        modifier = Modifier.size(width = Grid.u48, height = Grid.u24).clip(Grid.r12).background(track),
        contentAlignment = Alignment.CenterStart
      ) {
        Box(
          modifier = Modifier
            .offset { IntOffset((EqSize.knobInset + thumb).roundToPx(), 0) }
            .size(Grid.u24 - Grid.u6)
            .clip(CircleShape)
            .background(if (on) colors.onPrimary else colors.onSurfaceVariant)
        )
      }
    }
  }
}

/** The preset pill takes the width that is left, then Correction 96 and the overflow 48, with 12 between them. */
@Composable
private fun PresetRow(
  settings: EqSettings,
  preset: EqPreset?,
  modified: Boolean,
  actions: PresetActions,
  onOpenSettings: () -> Unit,
  onOpenAdvanced: () -> Unit,
  onOpenSpatial: (() -> Unit)?,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit
) {
  val presetName = preset?.localizedName() ?: stringResource(Res.string.audio_preset_custom)
  val presetDescription = if (modified) tr(Res.string.audio_preset_modified_desc, presetName) else presetName
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = Grid.u48).height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(Grid.u12),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Pill(
      text = presetName,
      sub = stringResource(if (modified) Res.string.audio_preset_sub_modified else Res.string.audio_preset_sub),
      chevron = true,
      description = presetDescription,
      modifier = Modifier.weight(1f),
      onClickLabel = stringResource(Res.string.audio_choose_preset),
      onClick = actions.onOpen,
      tag = "eq_preset_button"
    )
    Pill(
      text = stringResource(Res.string.audio_correction),
      sub = settings.device?.name ?: stringResource(Res.string.audio_correction_off),
      active = settings.device != null,
      description = stringResource(Res.string.audio_correction_desc),
      modifier = Modifier.width(Grid.u96),
      onClickLabel = stringResource(Res.string.audio_correction),
      onClick = actions.onOpenCorrection,
      tag = "eq_correction_button"
    )
    OverflowMenu(settings, preset, modified, actions, onOpenSettings, onOpenAdvanced, onOpenSpatial, onUpdateEq)
  }
}

/**
 * A 48 dp tall pill (taller when the text is larger) with two centred lines: what it is and its state.
 * [chevron] marks a pill that opens a list; [active] lights it. [description] is what a screen reader
 * says instead of the visible text.
 */
@Composable
private fun Pill(
  text: String,
  sub: String,
  description: String,
  modifier: Modifier,
  onClickLabel: String,
  onClick: () -> Unit,
  tag: String,
  chevron: Boolean = false,
  active: Boolean = false
) {
  val colors = MaterialTheme.colorScheme
  val source = remember { MutableInteractionSource() }
  Row(
    modifier = modifier
      .fillMaxHeight()
      .heightIn(min = Grid.u48)
      .pressScale(source)
      .clip(Grid.r24)
      .background(if (active) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer)
      .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
      .semantics { contentDescription = description }
      .padding(horizontal = Grid.u12)
      .testTag(tag),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(
      modifier = Modifier.weight(1f, fill = false).clearAndSetSemantics {},
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(text, style = EqType.LabelStrong, color = if (active) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(sub, style = EqType.Label, color = if (active) colors.primary else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (chevron) {
      Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Grid.icon))
    }
  }
}

/** The view switch (192 wide, 6 inside) and the Tarkas Spatial button that fills the rest. */
@Composable
private fun Dock(mode: EqMode, spatial: SpatialUi, onOpenSpatial: (() -> Unit)?, onUpdateEq: ((EqSettings) -> EqSettings) -> Unit, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  val source = remember { MutableInteractionSource() }
  val enabled = onOpenSpatial != null
  val sub = if (spatial.enabled) {
    tr(Res.string.audio_spatial_sub_on, spatialPresetLabel(spatial.preset), (spatial.intensity * 100f).roundToInt())
  } else stringResource(Res.string.audio_spatial_sub_off)
  // The view switch takes 192 at most; on a narrow column it shrinks so the Spatial button keeps 96.
  BoxWithConstraints(modifier = modifier.heightIn(min = Grid.u48)) {
    val segmentsWidth = (maxWidth - Grid.u12 - Grid.u96).coerceIn(Grid.u96, Grid.u192)
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = Grid.u48).height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    ModeSegments(
      parametric = mode == EqMode.PARAMETRIC,
      width = segmentsWidth,
      onSelect = { parametric -> if (parametric != (mode == EqMode.PARAMETRIC)) onUpdateEq { it.withLayout(parametric) } }
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
        .heightIn(min = Grid.u48)
        .pressScale(source)
        .clip(Grid.r24)
        .background(if (spatial.enabled) colors.primary.copy(alpha = 0.14f) else colors.surfaceContainer)
        .clickable(
          interactionSource = source, indication = null, enabled = enabled, role = Role.Button,
          onClickLabel = stringResource(Res.string.audio_spatial_title), onClick = { onOpenSpatial?.invoke() }
        )
        .padding(horizontal = Grid.u12)
        .testTag("eq_open_spatial"),
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        stringResource(Res.string.audio_spatial_title),
        style = EqType.LabelStrong,
        color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        sub,
        style = EqType.Mono,
        color = if (spatial.enabled) colors.primary else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
  }
}

@Composable
private fun spatialPresetLabel(preset: SpatialPreset): String = stringResource(
  when (preset) {
    SpatialPreset.SUBTLE -> Res.string.audio_spatial_preset_sutil
    SpatialPreset.STANDARD -> Res.string.audio_spatial_preset_padrao
    SpatialPreset.WIDE -> Res.string.audio_spatial_preset_amplo
    SpatialPreset.CINEMA -> Res.string.audio_spatial_preset_cinema
  }
)

/**
 * Graphic or Parametric, both always visible; the chosen one is lit. [width] is 192 at most. Each option
 * is a full-height (48 dp) touch target; the lit highlight is inset 6 inside it.
 */
@Composable
private fun ModeSegments(parametric: Boolean, width: androidx.compose.ui.unit.Dp, onSelect: (Boolean) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .width(width)
      .fillMaxHeight()
      .clip(Grid.r24)
      .background(colors.surfaceContainer)
      .selectableGroup()
      .testTag("eq_mode_toggle")
  ) {
    listOf(false to stringResource(Res.string.audio_mode_graphic), true to stringResource(Res.string.audio_mode_parametric)).forEachIndexed { position, (isParam, name) ->
      val chosen = parametric == isParam
      val fill by animateColorAsState(if (chosen) colors.primary.copy(alpha = 0.14f) else Color.Transparent, tween(150), label = "segment")
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(isParam) })
          .testTag(if (isParam) "eq_mode_parametric" else "eq_mode_graphic")
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(
              start = if (position == 0) Grid.u6 else Grid.u6 / 2,
              end = if (position == 0) Grid.u6 / 2 else Grid.u6,
              top = Grid.u6,
              bottom = Grid.u6
            )
            .clip(Grid.r12)
            .background(fill),
          contentAlignment = Alignment.Center
        ) {
          Text(
            name,
            style = if (chosen) EqType.LabelStrong else EqType.Label,
            color = if (chosen) colors.onSurface else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

/**
 * The headroom bar: an outlined 48 dp pill with the level the equalizer takes off ("Headroom -6 dB"), a
 * bar that fills with it and lights when the limiter works or the output clips, and the compare button
 * at the right end. What the output stage is doing (limiter, clip, matched volume, the loudspeaker
 * warning) is written under the bar, the last thing on the page, so a note never moves a control.
 */
@Composable
internal fun PathBar(settings: EqSettings, capabilities: AudioCapabilities, compare: CompareState, showCompare: Boolean = true) {
  val colors = MaterialTheme.colorScheme
  val headroom = EqDsp.totalPreampDb(settings, rememberSpatialStage())
  val lowered = settings.enabled && headroom < -0.05f
  val onSpeaker = capabilities.activeRouteType == AudioCapabilities.ROUTE_BUILTIN_SPEAKER
  val activity by EqActivity.state.collectAsState()
  val hot = settings.enabled && (activity.limiting || activity.clipped)
  val fill by animateFloatAsState(if (lowered) (-headroom / EqBand.MAX_GAIN).coerceIn(0f, 1f) else 0f, tween(200), label = "headroomFill")
  val fillColor by animateColorAsState(if (hot) colors.primary else colors.onSurface.copy(alpha = 0.75f), tween(200), label = "headroomColor")
  val spoken = if (lowered) tr(Res.string.audio_headroom_lowered, formatDb(-headroom).removePrefix("+")) else stringResource(Res.string.audio_headroom_none)
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(Grid.u48)
        .border(Grid.hairline, colors.onSurface.copy(alpha = 0.24f), Grid.r24)
        .padding(start = Grid.u12),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }.testTag("eq_headroom"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(stringResource(Res.string.audio_headroom_label), style = EqType.Label, color = colors.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.width(Grid.u6))
        Text(
          (if (lowered) formatDb(headroom) else "0") + " dB",
          style = EqType.MonoStrong,
          color = colors.onSurface,
          maxLines = 1
        )
      }
      Spacer(Modifier.width(Grid.u12))
      Box(
        modifier = Modifier
          .weight(1f)
          .height(EqSize.meterBar)
          .clip(Grid.r6)
          .background(colors.onSurface.copy(alpha = 0.12f))
      ) {
        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fill).clip(Grid.r6).background(fillColor))
      }
      Spacer(Modifier.width(Grid.u12))
      if (showCompare) CompareButton(settings.enabled, compare)
    }
    OutputStageNotes(settings, compare.comparing)
    if (settings.device != null && onSpeaker) {
      Text(text = stringResource(Res.string.audio_speaker_warning), style = EqType.Label, color = colors.primary)
    }
  }
}

/** Hold: without EQ until the finger lifts. Tap (under 400 ms): without EQ until the next tap. A screen reader toggles it. */
@Composable
internal fun CompareButton(enabled: Boolean, compare: CompareState) {
  val colors = MaterialTheme.colorScheme
  var pressed by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(150), label = "comparePress")
  val comparing = compare.comparing
  val description = stringResource(if (comparing) Res.string.audio_compare_listening_desc else Res.string.audio_compare_desc)
  val state = stringResource(if (comparing) Res.string.audio_compare_state_off else Res.string.audio_compare_state_on)
  Box(
    modifier = Modifier
      .size(width = Grid.u96, height = Grid.u48)
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .clip(Grid.r24)
      .background(if (comparing) colors.primary else colors.surfaceContainer)
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onPress = {
          val wasLatched = compare.latched
          val start = System.currentTimeMillis()
          pressed = true
          compare.start()
          val released = tryAwaitRelease()
          pressed = false
          if (!released) {
            // The press was cancelled (the finger scrolled away): end what this press started, never latch.
            if (!wasLatched) compare.stop()
          } else {
            val held = System.currentTimeMillis() - start >= HOLD_MS
            if (wasLatched || held) compare.stop() else compare.latch()
          }
        })
      }
      .semantics {
        role = Role.Button
        contentDescription = description
        stateDescription = state
        // A screen reader cannot hold: a double tap switches the comparison on and off. Off with the EQ.
        if (enabled) onClick { compare.toggle(); true } else disabled()
      }
      .padding(horizontal = Grid.u12)
      .testTag("eq_compare"),
    contentAlignment = Alignment.Center
  ) {
    Text(
      stringResource(Res.string.audio_compare_no_eq),
      style = EqType.LabelStrong,
      color = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        comparing -> colors.onPrimary
        else -> colors.onSurface
      },
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

/** The preset's actions, the way into the settings sheet and the advanced page. */
@Composable
private fun OverflowMenu(
  settings: EqSettings,
  preset: EqPreset?,
  modified: Boolean,
  actions: PresetActions,
  onOpenSettings: () -> Unit,
  onOpenAdvanced: () -> Unit,
  onOpenSpatial: (() -> Unit)?,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit
) {
  var open by remember { mutableStateOf(false) }
  val source = remember { MutableInteractionSource() }
  val toaster = com.example.windows.base.LocalToaster.current
  fun toast(text: String) = toaster.show(text)
  val isCustom = preset?.isCustom == true
  Box {
    Box(
      modifier = Modifier
        .size(Grid.u48)
        .pressScale(source)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = stringResource(Res.string.audio_more_options)) { open = true }
        .testTag("eq_more"),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        Icons.Rounded.MoreHoriz,
        contentDescription = stringResource(Res.string.audio_more_options),
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(Grid.icon)
      )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      @Composable
      fun item(text: String, tag: String? = null, onClick: () -> Unit) = DropdownMenuItem(
        text = { Text(text, style = EqType.Body) },
        onClick = { open = false; onClick() },
        modifier = if (tag != null) Modifier.testTag(tag) else Modifier
      )
      item(stringResource(Res.string.audio_menu_save_as), "eq_save_as", actions.onSaveAs)
      if (isCustom && modified) item(tr(Res.string.audio_menu_update, preset!!.name), onClick = actions.onOverwrite)
      if (isCustom) {
        item(stringResource(Res.string.audio_action_rename), onClick = actions.onRename)
        item(stringResource(Res.string.audio_menu_delete_preset), onClick = actions.onDelete)
      }
      item(stringResource(Res.string.audio_menu_copy_curve), "eq_export_text") {
        com.example.windows.base.DesktopClipboard.copy(EqTextFormat.export(settings))
        toast(trNow(Res.string.audio_toast_curve_copied))
      }
      item(stringResource(Res.string.audio_menu_import_text), "eq_import_text") {
        val text = com.example.windows.base.DesktopClipboard.paste().orEmpty()
        when (val result = EqTextFormat.parse(text)) {
          null -> toast(trNow(Res.string.audio_toast_import_failed))
          is EqTextFormat.Result.Parametric -> {
            onUpdateEq { EqTextFormat.apply(it, result) }
            toast(trPluralNow(Res.plurals.audio_toast_imported_filters, result.bands.size, result.bands.size))
          }
          is EqTextFormat.Result.Graphic -> {
            onUpdateEq { EqTextFormat.apply(it, result) }
            toast(trPluralNow(Res.plurals.audio_toast_imported_points, result.points, result.points, result.gainsDb.size))
          }
        }
      }
      item(stringResource(Res.string.audio_menu_reset), "eq_reset", actions.onReset)
      if (onOpenSpatial != null) item(stringResource(Res.string.audio_spatial_title), "eq_open_spatial_menu", onOpenSpatial)
      item(stringResource(Res.string.audio_settings_title), "eq_open_settings", onOpenSettings)
      item(stringResource(Res.string.audio_menu_advanced), "eq_open_advanced", onOpenAdvanced)
    }
  }
}

/** One row on a wide window: route and power, preset 144, Correction 96, menu 48, the view switch 192, Spatial, Compare. */
@Composable
private fun TabletTopBar(
  capabilities: AudioCapabilities,
  settings: EqSettings,
  preset: EqPreset?,
  modified: Boolean,
  mode: EqMode,
  spatial: SpatialUi,
  compare: CompareState,
  actions: PresetActions,
  onSetEnabled: (Boolean) -> Unit,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  onOpenSettings: () -> Unit,
  onOpenAdvanced: () -> Unit,
  onOpenSpatial: (() -> Unit)?,
  twoRows: Boolean
) {
  if (twoRows) {
    // Under 1152 dp the one row (route and power, preset 312, view and Spatial 348, Compare 96) squeezes the
    // title: the route and power take the first row with Compare, the preset and the dock the second.
    Column(modifier = Modifier.fillMaxWidth().testTag("eq_tablet_top_bar"), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
      Row(horizontalArrangement = Arrangement.spacedBy(Grid.u12), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { TopRow(capabilities, settings, compare, onSetEnabled) }
        CompareButton(settings.enabled, compare)
      }
      Row(horizontalArrangement = Arrangement.spacedBy(Grid.u12), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(Grid.u192 + Grid.u96 + Grid.u24)) {
          PresetRow(settings, preset, modified, actions, onOpenSettings, onOpenAdvanced, onOpenSpatial, onUpdateEq)
        }
        Dock(mode, spatial, onOpenSpatial, onUpdateEq, Modifier.weight(1f))
      }
    }
    return
  }
  Row(
    // No intrinsic height: PresetRow measures with constraints (a SubcomposeLayout), which intrinsics cannot ask.
    modifier = Modifier.fillMaxWidth().heightIn(min = Grid.u48).testTag("eq_tablet_top_bar"),
    horizontalArrangement = Arrangement.spacedBy(Grid.u12),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.weight(1f)) { TopRow(capabilities, settings, compare, onSetEnabled) }
    Box(Modifier.width(Grid.u192 + Grid.u96 + Grid.u24)) {
      PresetRow(settings, preset, modified, actions, onOpenSettings, onOpenAdvanced, onOpenSpatial, onUpdateEq)
    }
    Box(Modifier.width(Grid.u192 + Grid.u96 + Grid.u48 + Grid.u12)) {
      Dock(mode, spatial, onOpenSpatial, onUpdateEq, Modifier.fillMaxWidth())
    }
    CompareButton(settings.enabled, compare)
  }
}
