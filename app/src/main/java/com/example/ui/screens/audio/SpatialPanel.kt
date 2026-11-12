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
import androidx.compose.animation.core.tween
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
import com.example.data.audio.spatial.SpatialControl
import com.example.data.audio.spatial.SpatialKnob
import com.example.ui.components.IconAction
import com.example.ui.components.TextAction
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
}

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
        enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn(),
        exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut()
      ) {
        Column {
          SpatialPreset.entries.forEach { p ->
            val open = tuningOpen == p
            GroupDivider()
            RadioRow(
              title = p.label,
              subtitle = if (ui.tuning[p].orEmpty().isNotEmpty()) stringResource(R.string.audio_spatial_tuned) else null,
              selected = ui.preset == p,
              onClick = { actions.onPreset(p) },
              modifier = Modifier.testTag("spatial_preset_${p.key}"),
              accessory = {
                val turn by animateFloatAsState(if (open) 180f else 0f, tween(if (reduced) 0 else 200), label = "tuning_arrow")
                IconAction(
                  icon = Icons.Rounded.ExpandMore,
                  contentDescription = stringResource(if (open) R.string.audio_spatial_tune_close else R.string.audio_spatial_tune_open, p.label),
                  onClick = {
                    // The sliders change what is playing, so the mode they belong to must be the one playing.
                    if (!open && ui.preset != p) actions.onPreset(p)
                    tuningOpen = if (open) null else p
                  },
                  modifier = Modifier.rotate(turn).testTag("spatial_tune_${p.key}")
                )
              }
            )
            AnimatedVisibility(
              visible = open,
              enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn(),
              exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut()
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
  val copyHeader = stringResource(R.string.audio_spatial_copy_header, preset.label, "%.2f".format(Locale.US, ui.intensity))
  Column(modifier = Modifier.testTag("spatial_tuning_${preset.key}")) {
    SpatialKnob.entries.forEach { knob ->
      GroupDivider()
      val v = knob.read(shown)
      SliderRow(
        label = knob.label,
        valueText = String.format(Locale.US, "%.${knob.decimals}f", v) + knob.unit,
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
          "${it.label}: ${String.format(Locale.US, "%.${it.decimals}f", it.read(shown))}${it.unit}"
        }
        context.getSystemService(ClipboardManager::class.java)
          ?.setPrimaryClip(ClipData.newPlainText("Tarkas Spatial", text))
      })
    }
  }
}
