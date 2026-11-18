package com.example.ui.screens.audio

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.example.R
import com.example.data.audio.spatial.HeadTrackStatus
import com.example.data.audio.spatial.SpatialControl
import com.example.data.audio.spatial.SpatialKnob
import com.example.data.audio.spatial.SpatialPreset
import com.example.data.audio.spatial.SpatialStatus
import com.example.ui.components.GridSwitch
import com.example.ui.components.GroupDivider
import com.example.ui.components.SwitchRow
import com.example.ui.components.TextAction
import com.example.ui.screens.SpatialActions
import com.example.ui.screens.SpatialUi
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GridText
import com.example.ui.theme.Grid
import com.example.ui.theme.Motion
import com.example.ui.theme.rememberReducedMotion
import java.util.Locale
import kotlin.math.roundToInt

/** What the status line says, for each state of the engine. */
@Composable
private fun spatialStatusText(status: SpatialStatus): String = when (status) {
  SpatialStatus.OFF -> stringResource(R.string.audio_spatial_status_off)
  SpatialStatus.PREPARING -> stringResource(R.string.audio_spatial_status_preparing)
  SpatialStatus.ACTIVE -> stringResource(R.string.audio_spatial_status_active)
  SpatialStatus.NOT_STEREO -> stringResource(R.string.audio_spatial_status_not_stereo)
  SpatialStatus.FAILED -> stringResource(R.string.audio_spatial_status_failed)
}

@Composable
private fun headStatusText(wanted: Boolean, status: HeadTrackStatus): String = stringResource(
  when {
    !wanted -> R.string.audio_spatial_head_off
    status == HeadTrackStatus.NO_SENSOR -> R.string.audio_spatial_head_no_sensor
    status == HeadTrackStatus.TRACKING -> R.string.audio_spatial_head_tracking
    else -> R.string.audio_spatial_head_waiting
  }
)

/** The caption of the stage: the first word in the main colour, the rest muted, like the mock. */
@Composable
private fun StageCaption(text: String, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val cut = text.indexOf(" · ")
  val styled: AnnotatedString = buildAnnotatedString {
    if (cut < 0) append(text) else {
      withStyle(SpanStyle(color = colors.onSurface)) { append(text.substring(0, cut)) }
      append(text.substring(cut))
    }
  }
  Text(
    styled, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis,
    style = GridText.Label.copy(fontWeight = FontWeight.Medium),
    color = colors.onSurfaceVariant
  )
}

/** Four equal segments (48 dp high, inner inset 6): Subtle, Standard, Wide, Cinema 5.1. */
@Composable
private fun PresetSegments(ui: SpatialUi, actions: SpatialActions) {
  val colors = MaterialTheme.colorScheme
  val pill = Grid.r24
  val label = stringResource(R.string.audio_spatial_mode)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(Grid.u48)
      .clip(pill)
      .background(colors.surfaceContainer)
      .selectableGroup()
      .semantics { contentDescription = label }
  ) {
    val last = SpatialPreset.entries.size - 1
    SpatialPreset.entries.forEachIndexed { position, p ->
      val selected = ui.preset == p
      // The touch target is the full 48 dp; the highlight is inset 6 inside it.
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxHeight()
          .selectable(selected = selected, role = Role.RadioButton, onClick = { actions.onPreset(p) })
          .testTag("spatial_preset_${p.key}")
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(
              start = if (position == 0) Grid.u6 else Grid.u6 / 2,
              end = if (position == last) Grid.u6 else Grid.u6 / 2,
              top = Grid.u6,
              bottom = Grid.u6
            )
            .clip(pill)
            .background(if (selected) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainer),
          contentAlignment = Alignment.Center
        ) {
          Text(
            presetTitle(p), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = GridText.Small.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = if (selected) colors.primary else colors.onSurfaceVariant
          )
        }
      }
    }
  }
}

/**
 * Tarkas Spatial sheet: title and caption, the 3D stage, the mode segments, the on/off row and the
 * intensity; the hand tuning, head tracking, status and notes sit behind one expandable row.
 */
@Composable
internal fun SpatialPanel(ui: SpatialUi, actions: SpatialActions, modifier: Modifier = Modifier) {
  val reduced = rememberReducedMotion()
  val colors = MaterialTheme.colorScheme
  var moreOpen by rememberSaveable { mutableStateOf(false) }
  val caption = spatialStageCaption(ui.enabled, ui.preset)
  Column(modifier = modifier.fillMaxWidth().padding(horizontal = Grid.margin)) {
    Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = Grid.u48),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Grid.u12)
    ) {
      Text(
        stringResource(R.string.audio_spatial_title), modifier = Modifier.weight(1f), maxLines = 1,
        style = GridText.Title, color = colors.onSurface
      )
      StageCaption(caption)
    }
    Box(modifier = Modifier.padding(top = Grid.u12)) {
      SpatialStage(enabled = ui.enabled, preset = ui.preset, intensity = ui.intensity)
    }
    Box(modifier = Modifier.padding(top = Grid.u12)) { PresetSegments(ui, actions) }
    Row(
      modifier = Modifier
        .padding(top = Grid.u12)
        .fillMaxWidth()
        .height(Grid.u48)
        .toggleable(value = ui.enabled, role = Role.Switch, onValueChange = actions.onEnabled)
        .testTag("switch_spatial"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        stringResource(R.string.audio_spatial_on), modifier = Modifier.weight(1f),
        style = GridText.Body, color = colors.onSurface
      )
      // The row is the control; the switch only draws the state.
      GridSwitch(checked = ui.enabled)
    }
    SliderRow(
      label = stringResource(R.string.audio_spatial_intensity),
      valueText = "${(ui.intensity * 100).roundToInt()}%",
      value = ui.intensity,
      range = 0.2f..1f,
      onChange = actions.onIntensity,
      onChangeFinished = actions.onIntensityFinished,
      defaultValue = 1f,
      enabled = ui.enabled,
      side = 0.dp,
      testTag = "spatial_intensity"
    )
    val turn by animateFloatAsState(if (moreOpen) 180f else 0f, if (reduced) snap() else Motion.gel(), label = "more_arrow")
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(Grid.u48)
        .clickable(role = Role.Button, onClick = { moreOpen = !moreOpen })
        .testTag("spatial_tune_${ui.preset.key}"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        stringResource(R.string.audio_spatial_more), modifier = Modifier.weight(1f),
        style = GridText.Body, color = colors.onSurface
      )
      Box(modifier = Modifier.size(Grid.u48), contentAlignment = Alignment.Center) {
        Icon(
          Icons.Rounded.ExpandMore, contentDescription = null,
          modifier = Modifier.size(Grid.u24).rotate(turn), tint = colors.onSurfaceVariant
        )
      }
    }
    AnimatedVisibility(
      visible = moreOpen,
      enter = if (reduced) EnterTransition.None else expandVertically(Motion.gel()) + fadeIn(),
      exit = if (reduced) ExitTransition.None else shrinkVertically(Motion.gelExit()) + fadeOut()
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().clip(Grid.r12).background(colors.surface)) {
          Text(
            spatialStatusText(ui.status),
            modifier = Modifier.fillMaxWidth().padding(Grid.u12),
            style = GridText.Label,
            color = colors.onSurfaceVariant
          )
          if (ui.preset.virtual51) {
            Text(
              stringResource(R.string.audio_spatial_cinema_hint),
              modifier = Modifier.padding(horizontal = Grid.u12, vertical = Grid.u12),
              style = GridText.Small, color = colors.onSurfaceVariant
            )
          } else {
            SpatialTuning(ui.preset, ui, actions)
          }
          GroupDivider()
          SwitchRow(
            title = stringResource(R.string.audio_spatial_head_title),
            subtitle = headStatusText(ui.headTracking, ui.headStatus),
            checked = ui.headTracking,
            onCheckedChange = actions.onHeadTracking,
            modifier = Modifier.testTag("switch_spatial_head")
          )
          if (ui.headStatus == HeadTrackStatus.TRACKING) {
            GroupDivider()
            TextAction(stringResource(R.string.audio_spatial_head_recenter), onClick = actions.onRecenterHead)
          }
        }
        Text(
          stringResource(R.string.audio_spatial_footer) + (ui.othersNote?.let { "\n" + it } ?: ""),
          modifier = Modifier.padding(vertical = Grid.u12),
          style = GridText.Small, color = colors.onSurfaceVariant
        )
      }
    }
  }
}

/**
 * The hand settings of one mode, for listening tests: every slider changes the sound as it moves, the
 * values are kept per mode, and "Copiar valores" puts them on the clipboard as text.
 */
@Composable
private fun SpatialTuning(preset: SpatialPreset, ui: SpatialUi, actions: SpatialActions) {
  val context = LocalContext.current
  val values = ui.tuning[preset].orEmpty()
  val shown = SpatialControl.paramsFor(preset, values)
  val knobTitles = SpatialKnob.entries.associateWith { knobTitle(it) }
  val copyHeader = stringResource(R.string.audio_spatial_copy_header, presetTitle(preset), "%.2f".format(Locale.US, ui.intensity))
  Column(modifier = Modifier.testTag("spatial_tuning_${preset.key}")) {
    SpatialKnob.entries.forEach { knob ->
      GroupDivider()
      val v = knob.read(shown)
      SliderRow(
        label = knobTitles.getValue(knob),
        valueText = formatNumber(v.toFloat(), knob.decimals) + knob.unit,
        value = v.toFloat(),
        range = knob.min.toFloat()..knob.max.toFloat(),
        defaultValue = knob.read(preset.params).toFloat(),
        onChange = { actions.onKnob(preset, knob, it.toDouble()) },
        testTag = "spatial_knob_${knob.key}"
      )
    }
    GroupDivider()
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Grid.inset),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      TextAction(stringResource(R.string.audio_spatial_reset_mode), onClick = { actions.onResetTuning(preset) }, enabled = values.isNotEmpty())
      TextAction(stringResource(R.string.audio_spatial_copy_values), onClick = {
        val text = SpatialKnob.entries.joinToString("\n", prefix = copyHeader) {
          "${knobTitles.getValue(it)}: ${String.format(Locale.US, "%.${it.decimals}f", it.read(shown))}${it.unit}"
        }
        context.getSystemService(ClipboardManager::class.java)
          ?.setPrimaryClip(ClipData.newPlainText("Tarkas Spatial", text))
      })
    }
  }
}

/** The mode's name; [SpatialPreset] stays free of Android resources, the UI maps each entry. */
@Composable
private fun presetTitle(p: SpatialPreset): String = stringResource(
  when (p) {
    SpatialPreset.SUBTLE -> R.string.audio_spatial_preset_sutil
    SpatialPreset.STANDARD -> R.string.audio_spatial_preset_padrao
    SpatialPreset.WIDE -> R.string.audio_spatial_preset_amplo
    SpatialPreset.CINEMA -> R.string.audio_spatial_preset_cinema
  }
)

/** The slider's name; same idea as [presetTitle]. */
@Composable
private fun knobTitle(k: SpatialKnob): String = stringResource(
  when (k) {
    SpatialKnob.ROOM -> R.string.audio_spatial_knob_room
    SpatialKnob.EARLY -> R.string.audio_spatial_knob_early
    SpatialKnob.TAIL -> R.string.audio_spatial_knob_tail
    SpatialKnob.DECAY -> R.string.audio_spatial_knob_decay
    SpatialKnob.PREDELAY -> R.string.audio_spatial_knob_predelay
    SpatialKnob.DIFFUSION -> R.string.audio_spatial_knob_diffusion
    SpatialKnob.VOICE -> R.string.audio_spatial_knob_voice
    SpatialKnob.WIDTH -> R.string.audio_spatial_knob_width
    SpatialKnob.HEIGHT -> R.string.audio_spatial_knob_height
    SpatialKnob.AMBIENCE -> R.string.audio_spatial_knob_ambience
  }
)
