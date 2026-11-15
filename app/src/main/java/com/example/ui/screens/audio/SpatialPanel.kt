package com.example.ui.screens.audio

import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import com.example.data.audio.spatial.HeadTrackStatus
import com.example.data.audio.spatial.SpatialControl
import com.example.data.audio.spatial.SpatialKnob
import com.example.ui.components.IconAction
import com.example.ui.components.TextAction
import com.example.ui.theme.Motion
import com.example.ui.theme.Space
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.data.audio.spatial.SpatialPreset
import com.example.data.audio.spatial.SpatialStatus
import com.example.ui.components.GroupDivider
import com.example.ui.components.GroupFooter
import com.example.ui.components.InsetGroup
import com.example.ui.components.RadioRow
import com.example.ui.components.SwitchRow
import com.example.ui.screens.SpatialActions
import com.example.ui.screens.SpatialUi
import com.example.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt

/** What the switch row says under its title, for each state of the engine. */
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

/**
 * Tarkas Spatial, stereo turned into a binaural scene for headphones: a switch, then (when on) the
 * scene as a list of choices and its intensity.
 */
@Composable
internal fun SpatialPanel(ui: SpatialUi, actions: SpatialActions, modifier: Modifier = Modifier) {
  val reduced = rememberReducedMotion()
  var tuningOpen by rememberSaveable { mutableStateOf<SpatialPreset?>(null) }
  Column(modifier = modifier) {
    InsetGroup {
      SwitchRow(
        title = stringResource(R.string.audio_spatial_title),
        subtitle = spatialStatusText(ui.status),
        checked = ui.enabled,
        onCheckedChange = actions.onEnabled,
        modifier = Modifier.testTag("switch_spatial")
      )
      AnimatedVisibility(
        visible = ui.enabled,
        enter = if (reduced) EnterTransition.None else expandVertically(Motion.gel()) + fadeIn(),
        exit = if (reduced) ExitTransition.None else shrinkVertically(Motion.gelExit()) + fadeOut()
      ) {
        Column {
          SpatialPreset.entries.forEach { p ->
            val open = tuningOpen == p
            GroupDivider()
            RadioRow(
              title = presetTitle(p),
              subtitle = when {
                p.virtual51 -> stringResource(R.string.audio_spatial_cinema_hint)
                ui.tuning[p].orEmpty().isNotEmpty() -> stringResource(R.string.audio_spatial_tuned)
                else -> null
              },
              selected = ui.preset == p,
              onClick = { actions.onPreset(p) },
              modifier = Modifier.testTag("spatial_preset_${p.key}"),
              // The Cinema preset has no room or azimuth settings to tune: no button for it.
              accessory = if (p.virtual51) null else ({
                val turn by animateFloatAsState(if (open) 180f else 0f, if (reduced) snap() else Motion.gel(), label = "tuning_arrow")
                IconAction(
                  icon = Icons.Rounded.ExpandMore,
                  contentDescription = stringResource(if (open) R.string.audio_spatial_tune_close else R.string.audio_spatial_tune_open, presetTitle(p)),
                  onClick = {
                    // The sliders change what is playing, so the mode they belong to must be the one playing.
                    if (!open && ui.preset != p) actions.onPreset(p)
                    tuningOpen = if (open) null else p
                  },
                  modifier = Modifier.rotate(turn).testTag("spatial_tune_${p.key}")
                )
              })
            )
            AnimatedVisibility(
              visible = open,
              enter = if (reduced) EnterTransition.None else expandVertically(Motion.gel()) + fadeIn(),
              exit = if (reduced) ExitTransition.None else shrinkVertically(Motion.gelExit()) + fadeOut()
            ) {
              SpatialTuning(p, ui, actions)
            }
          }
          GroupDivider()
          SliderRow(
            label = stringResource(R.string.audio_spatial_intensity),
            valueText = "${(ui.intensity * 100).roundToInt()}%",
            value = ui.intensity,
            range = 0.2f..1f,
            onChange = actions.onIntensity,
            onChangeFinished = actions.onIntensityFinished,
            testTag = "spatial_intensity"
          )
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
      }
    }
    GroupFooter(
      stringResource(R.string.audio_spatial_footer) +
        (ui.othersNote?.let { "\n" + it } ?: "")
    )
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
        onChange = { actions.onKnob(preset, knob, it.toDouble()) },
        testTag = "spatial_knob_${knob.key}"
      )
    }
    GroupDivider()
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s),
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
