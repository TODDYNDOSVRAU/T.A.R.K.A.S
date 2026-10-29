package com.example.ui.screens.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
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
private fun spatialStatusText(status: SpatialStatus): String = when (status) {
  SpatialStatus.OFF -> "Abre a música ao redor da cabeça"
  SpatialStatus.PREPARING -> "Preparando…"
  SpatialStatus.ACTIVE -> "Ativo"
  SpatialStatus.NOT_STEREO -> "Esta faixa não é estéreo"
}

/**
 * Tarkas Spatial, stereo turned into a binaural scene for headphones: a switch, then (when on) the
 * scene as a list of choices and its intensity.
 */
@Composable
internal fun SpatialPanel(ui: SpatialUi, actions: SpatialActions, modifier: Modifier = Modifier) {
  val reduced = rememberReducedMotion()
  Column(modifier = modifier) {
    InsetGroup {
      SwitchRow(
        title = "Áudio espacial",
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
            GroupDivider()
            RadioRow(
              title = p.label,
              selected = ui.preset == p,
              onClick = { actions.onPreset(p) },
              modifier = Modifier.testTag("spatial_preset_${p.key}")
            )
          }
          GroupDivider()
          SliderRow(
            label = "Intensidade",
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
      "Funciona em qualquer saída, melhor nos fones. Músicas mono ou multicanal tocam como estão." +
        (ui.othersNote?.let { "\n" + it } ?: "")
    )
  }
}
