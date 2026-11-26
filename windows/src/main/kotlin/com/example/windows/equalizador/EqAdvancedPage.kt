package com.example.windows.equalizador

import com.example.windows.res.Res
import com.example.windows.res.tr
import com.example.windows.res.audio_adv_auto_correction
import com.example.windows.res.audio_adv_auto_preamp
import com.example.windows.res.audio_adv_auto_preamp_idle
import com.example.windows.res.audio_adv_auto_preamp_lowers
import com.example.windows.res.audio_adv_auto_preamp_lowers_spatial
import com.example.windows.res.audio_adv_back
import com.example.windows.res.audio_adv_balance
import com.example.windows.res.audio_adv_bass_freq
import com.example.windows.res.audio_adv_correction_auto_footer
import com.example.windows.res.audio_adv_correction_manual_footer
import com.example.windows.res.audio_adv_crossfade
import com.example.windows.res.audio_adv_crossfade_footer
import com.example.windows.res.audio_adv_crossfade_off
import com.example.windows.res.audio_adv_crossfade_on
import com.example.windows.res.audio_adv_crossfade_spatial
import com.example.windows.res.audio_adv_device_reduces
import com.example.windows.res.audio_adv_dump_apps
import com.example.windows.res.audio_adv_dump_missing
import com.example.windows.res.audio_adv_dump_steps
import com.example.windows.res.audio_adv_duration
import com.example.windows.res.audio_adv_group_engineering
import com.example.windows.res.audio_adv_group_playback
import com.example.windows.res.audio_adv_group_sound
import com.example.windows.res.audio_adv_limiter_ceiling
import com.example.windows.res.audio_adv_limiter_sub
import com.example.windows.res.audio_adv_limits_app
import com.example.windows.res.audio_adv_limits_other
import com.example.windows.res.audio_adv_loudness_album
import com.example.windows.res.audio_adv_loudness_album_sub
import com.example.windows.res.audio_adv_loudness_applied
import com.example.windows.res.audio_adv_loudness_auto
import com.example.windows.res.audio_adv_loudness_auto_sub
import com.example.windows.res.audio_adv_loudness_current
import com.example.windows.res.audio_adv_loudness_footer
import com.example.windows.res.audio_adv_loudness_measuring
import com.example.windows.res.audio_adv_loudness_off
import com.example.windows.res.audio_adv_loudness_src_album
import com.example.windows.res.audio_adv_loudness_src_measured
import com.example.windows.res.audio_adv_loudness_src_track
import com.example.windows.res.audio_adv_loudness_track
import com.example.windows.res.audio_adv_loudness_track_sub
import com.example.windows.res.audio_adv_meter_active
import com.example.windows.res.audio_adv_meter_footer
import com.example.windows.res.audio_adv_meter_inactive
import com.example.windows.res.audio_adv_meter_start
import com.example.windows.res.audio_adv_meter_stop
import com.example.windows.res.audio_adv_no_other_apps
import com.example.windows.res.audio_adv_output_now
import com.example.windows.res.audio_adv_reach_adb
import com.example.windows.res.audio_adv_reach_adb_desc
import com.example.windows.res.audio_adv_reach_adb_idle
import com.example.windows.res.audio_adv_reach_app
import com.example.windows.res.audio_adv_reach_app_desc
import com.example.windows.res.audio_adv_reach_no_root
import com.example.windows.res.audio_adv_reach_now
import com.example.windows.res.audio_adv_reach_now_global
import com.example.windows.res.audio_adv_reach_root
import com.example.windows.res.audio_adv_reach_root_denied
import com.example.windows.res.audio_adv_reach_root_desc
import com.example.windows.res.audio_adv_reach_root_idle
import com.example.windows.res.audio_adv_reach_root_idle_global
import com.example.windows.res.audio_adv_section_apps
import com.example.windows.res.audio_adv_section_correction
import com.example.windows.res.audio_adv_section_loudness
import com.example.windows.res.audio_adv_section_meter
import com.example.windows.res.audio_adv_section_output
import com.example.windows.res.audio_adv_section_reach
import com.example.windows.res.audio_adv_section_tone
import com.example.windows.res.audio_adv_section_transition
import com.example.windows.res.audio_adv_stereo_width
import com.example.windows.res.audio_adv_system_audio
import com.example.windows.res.audio_adv_tone_footer
import com.example.windows.res.audio_adv_track_format
import com.example.windows.res.audio_adv_track_format_downsample
import com.example.windows.res.audio_adv_treble_freq
import com.example.windows.res.audio_adv_virtualizer
import com.example.windows.res.audio_adv_virtualizer_footer
import com.example.windows.res.audio_menu_advanced
import com.example.windows.res.audio_preamp_long
import com.example.windows.res.audio_switch_limiter
import com.example.ui.theme.Grid
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.audio.dsp.EqDsp
import com.example.data.audio.loudness.LoudnessSource
import com.example.data.audio.loudness.LoudnessUi
import com.example.data.audio.loudness.NormalizationMode
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqSettings
import com.example.ui.components.GroupDivider
import com.example.ui.components.GroupFooter
import com.example.ui.components.GroupHeader
import com.example.ui.components.InfoRow
import com.example.ui.components.InsetGroup
import com.example.ui.components.ListRow
import com.example.ui.components.RadioRow
import com.example.ui.components.SubPageHeader
import com.example.ui.components.SwitchRow
import com.example.windows.equalizador.LocalBarInset
import com.example.windows.equalizador.AudioMotion
import com.example.windows.equalizador.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Widest the advanced page grows: two phone columns of 384 dp (docs/WINDOWS-NOVA-UI.md). */
internal val ADVANCED_MAX_WIDTH = Grid.u384 * 2

/**
 * The technical controls behind "Ajustes avançados": how far the equalizer reaches, the tone
 * corners, the output stage, the apps playing now and the system level meter.
 */
@Composable
internal fun EqAdvancedPage(
  capabilities: AudioCapabilities,
  eqSettings: EqSettings,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit,
  loudness: LoudnessUi,
  onSetNormalizationMode: (NormalizationMode) -> Unit,
  crossfadeSeconds: Int = 0,
  onSetCrossfade: (Int) -> Unit = {},
  spatialOn: Boolean = false,
  outputCorrection: OutputCorrectionUi = OutputCorrectionUi(),
  onSetAutoOutputCorrection: (Boolean) -> Unit = {},
  outputs: OutputsUi = OutputsUi(),
  onSelectOutput: (String?) -> Unit = {},
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val reduced = rememberReducedMotion()

  val colors = MaterialTheme.colorScheme
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Grid.u24)
  ) {
    item {
      SubPageHeader(
        title = stringResource(Res.string.audio_menu_advanced),
        backLabel = stringResource(Res.string.audio_adv_back),
        onBack = onBack,
        modifier = Modifier.testTag("eq_subpage_back")
      )
    }

    item { GroupLabel(stringResource(Res.string.audio_adv_group_sound)) }

    item {
      Section(stringResource(Res.string.audio_adv_section_correction)) {
        InsetGroup {
          SwitchRow(
            title = stringResource(Res.string.audio_adv_auto_correction),
            subtitle = tr(Res.string.audio_adv_output_now, outputCorrection.outputName),
            checked = outputCorrection.auto,
            onCheckedChange = onSetAutoOutputCorrection,
            modifier = Modifier.testTag("switch_auto_output_correction")
          )
        }
        GroupFooter(
          if (outputCorrection.auto) {
            stringResource(Res.string.audio_adv_correction_auto_footer)
          } else {
            stringResource(Res.string.audio_adv_correction_manual_footer)
          }
        )
      }
    }

    item {
      Section(stringResource(Res.string.audio_adv_section_tone)) {
        InsetGroup {
          LogSliderRow(
            label = stringResource(Res.string.audio_adv_bass_freq),
            valueText = spokenHz(eqSettings.bassFreqHz),
            value = eqSettings.bassFreqHz,
            min = 30f, max = 400f,
            defaultValue = 100f,
            onChange = { v -> onUpdateEq { it.copy(bassFreqHz = v) } }
          )
          GroupDivider()
          LogSliderRow(
            label = stringResource(Res.string.audio_adv_treble_freq),
            valueText = spokenHz(eqSettings.trebleFreqHz),
            value = eqSettings.trebleFreqHz,
            min = 1500f, max = 16000f,
            defaultValue = 8000f,
            onChange = { v -> onUpdateEq { it.copy(trebleFreqHz = v) } }
          )
        }
        GroupFooter(stringResource(Res.string.audio_adv_tone_footer))
      }
    }

    item {
      Section(stringResource(Res.string.audio_adv_section_output)) {
        InsetGroup {
          SliderRow(
            label = stringResource(Res.string.audio_preamp_long),
            valueText = formatDb(eqSettings.preampDb) + " dB",
            value = eqSettings.preampDb,
            range = -EqSettings.PREAMP_RANGE_DB..EqSettings.PREAMP_RANGE_DB,
            defaultValue = 0f,
            testTag = "fader_preamp",
            onChange = { v -> onUpdateEq { it.copy(preampDb = snap(v)) } }
          )
          GroupDivider()
          val spatialStage = rememberSpatialStage()
          val auto = EqDsp.autoPreampDb(eqSettings, spatialStage)
          SwitchRow(
            title = stringResource(Res.string.audio_adv_auto_preamp),
            subtitle = if (eqSettings.autoPreamp && auto < 0f) {
              stringResource(if (spatialStage) Res.string.audio_adv_auto_preamp_lowers_spatial else Res.string.audio_adv_auto_preamp_lowers, formatDb(-auto))
            }
            else stringResource(Res.string.audio_adv_auto_preamp_idle),
            checked = eqSettings.autoPreamp,
            onCheckedChange = { on -> onUpdateEq { it.copy(autoPreamp = on) } },
            modifier = Modifier.testTag("switch_auto_preamp")
          )
          GroupDivider()
          SliderRow(
            label = stringResource(Res.string.audio_adv_balance),
            valueText = balanceText(eqSettings.balance),
            value = eqSettings.balance,
            range = -1f..1f,
            defaultValue = 0f,
            onChange = { v -> onUpdateEq { it.copy(balance = if (abs(v) < 0.03f) 0f else v) } }
          )
          GroupDivider()
          SliderRow(
            label = stringResource(Res.string.audio_adv_stereo_width),
            valueText = widthText(eqSettings.stereoWidth),
            value = eqSettings.stereoWidth,
            range = 0f..2f,
            defaultValue = 1f,
            onChange = { v -> onUpdateEq { it.copy(stereoWidth = if (abs(v - 1f) < 0.04f) 1f else v) } }
          )
          GroupDivider()
          SwitchRow(
            title = stringResource(Res.string.audio_switch_limiter),
            subtitle = stringResource(Res.string.audio_adv_limiter_sub),
            checked = eqSettings.limiterEnabled,
            onCheckedChange = { on -> onUpdateEq { it.copy(limiterEnabled = on) } },
            modifier = Modifier.testTag("switch_limiter_advanced")
          )
          AnimatedVisibility(
            visible = eqSettings.limiterEnabled,
            enter = if (reduced) EnterTransition.None else expandVertically(AudioMotion.gel()) + fadeIn(),
            exit = if (reduced) ExitTransition.None else shrinkVertically(AudioMotion.gelExit()) + fadeOut()
          ) {
            Column {
              GroupDivider()
              SliderRow(
                label = stringResource(Res.string.audio_adv_limiter_ceiling),
                valueText = formatDb(eqSettings.limiterCeilingDb) + " dB",
                value = eqSettings.limiterCeilingDb,
                range = -12f..0f,
                defaultValue = 0f,
                onChange = { v -> onUpdateEq { it.copy(limiterCeilingDb = (v * 2).roundToInt() / 2f) } }
              )
            }
          }
          if (capabilities.isVirtualizerAvailable) {
            GroupDivider()
            SliderRow(
              label = stringResource(Res.string.audio_adv_virtualizer),
              valueText = "${eqSettings.virtualizerStrength / 10}%",
              value = eqSettings.virtualizerStrength / 1000f,
              range = 0f..1f,
              defaultValue = 0f,
              testTag = "knob_virtualizer",
              onChange = { v -> onUpdateEq { it.copy(virtualizerStrength = (v * 1000).roundToInt()) } }
            )
          }
        }
        eqSettings.device?.let { d ->
          GroupFooter(tr(Res.string.audio_adv_device_reduces, formatDb(-d.preampDb)))
        }
        capabilities.trackFormat?.let { format ->
          val max = capabilities.outputMaxRateHz
          GroupFooter(
            if (capabilities.outputBelowTrackRate && max != null) {
              tr(Res.string.audio_adv_track_format_downsample,
                format, capabilities.activeRouteName, (if (max % 1000 == 0) "${max / 1000} kHz" else String.format(java.util.Locale.ROOT, "%.1f kHz", max / 1000.0))
              )
            } else {
              tr(Res.string.audio_adv_track_format, format)
            }
          )
        }
        if (capabilities.isVirtualizerAvailable) {
          GroupFooter(stringResource(Res.string.audio_adv_virtualizer_footer))
        }
      }
    }

    item { GroupLabel(stringResource(Res.string.audio_adv_group_playback)) }

    item { LoudnessSection(loudness, onSetNormalizationMode) }

    item {
      Section(stringResource(Res.string.audio_adv_section_transition)) {
        InsetGroup {
          SwitchRow(
            title = stringResource(Res.string.audio_adv_crossfade),
            subtitle = if (crossfadeSeconds > 0) tr(Res.string.audio_adv_crossfade_on, crossfadeSeconds)
            else stringResource(Res.string.audio_adv_crossfade_off),
            checked = crossfadeSeconds > 0,
            onCheckedChange = { on -> onSetCrossfade(if (on) com.example.data.playback.CrossfadePlanner.DEFAULT_SECONDS else 0) },
            modifier = Modifier.testTag("switch_crossfade")
          )
          AnimatedVisibility(
            visible = crossfadeSeconds > 0,
            enter = if (reduced) EnterTransition.None else expandVertically(AudioMotion.gel()) + fadeIn(),
            exit = if (reduced) ExitTransition.None else shrinkVertically(AudioMotion.gelExit()) + fadeOut()
          ) {
            Column {
              GroupDivider()
              SliderRow(
                label = stringResource(Res.string.audio_adv_duration),
                valueText = "$crossfadeSeconds s",
                value = crossfadeSeconds.toFloat(),
                range = 1f..12f,
                defaultValue = com.example.data.playback.CrossfadePlanner.DEFAULT_SECONDS.toFloat(),
                onChange = { v -> onSetCrossfade(v.roundToInt().coerceIn(1, 12)) }
              )
            }
          }
        }
        GroupFooter(
          when {
            crossfadeSeconds > 0 && spatialOn ->
              stringResource(Res.string.audio_adv_crossfade_spatial)
            else ->
              stringResource(Res.string.audio_adv_crossfade_footer)
          }
        )
      }
    }

    item { GroupLabel(stringResource(Res.string.audio_adv_group_engineering)) }

    item {
      Section(stringResource(Res.string.audio_adv_section_output)) {
        InsetGroup(modifier = Modifier.testTag("output_device_panel")) {
          RadioRow(
            title = outputs.nowPlaying.ifBlank { "Windows" },
            selected = outputs.selected == null,
            onClick = { onSelectOutput(null) },
            modifier = Modifier.testTag("output_default")
          )
          outputs.names.forEach { name ->
            GroupDivider()
            RadioRow(title = name, selected = outputs.selected == name, onClick = { onSelectOutput(name) })
          }
          GroupDivider()
          InfoRow(
            title = tr(Res.string.audio_adv_output_now, outputs.selected ?: outputs.nowPlaying.ifBlank { "Windows" }),
            value = ""
          )
        }
      }
    }
  }
}

/**
 * Volume entre faixas: the normalization mode, then what it does to the playing track (where its
 * loudness came from and the change applied), so the effect can be checked, not only trusted.
 */
@Composable
private fun LoudnessSection(loudness: LoudnessUi, onSetMode: (NormalizationMode) -> Unit) {
  Section(stringResource(Res.string.audio_adv_section_loudness)) {
    InsetGroup(modifier = Modifier.testTag("loudness_panel")) {
      RadioRow(
        title = stringResource(Res.string.audio_adv_loudness_off),
        selected = loudness.mode == NormalizationMode.OFF,
        onClick = { onSetMode(NormalizationMode.OFF) }
      )
      GroupDivider()
      RadioRow(
        title = stringResource(Res.string.audio_adv_loudness_auto),
        subtitle = stringResource(Res.string.audio_adv_loudness_auto_sub),
        selected = loudness.mode == NormalizationMode.AUTO,
        onClick = { onSetMode(NormalizationMode.AUTO) }
      )
      GroupDivider()
      RadioRow(
        title = stringResource(Res.string.audio_adv_loudness_track),
        subtitle = stringResource(Res.string.audio_adv_loudness_track_sub),
        selected = loudness.mode == NormalizationMode.TRACK,
        onClick = { onSetMode(NormalizationMode.TRACK) }
      )
      GroupDivider()
      RadioRow(
        title = stringResource(Res.string.audio_adv_loudness_album),
        subtitle = stringResource(Res.string.audio_adv_loudness_album_sub),
        selected = loudness.mode == NormalizationMode.ALBUM,
        onClick = { onSetMode(NormalizationMode.ALBUM) }
      )
      if (loudness.mode != NormalizationMode.OFF && loudness.hasTrack) {
        GroupDivider()
        InfoRow(title = stringResource(Res.string.audio_adv_loudness_current), value = loudnessReadout(loudness))
        if (loudness.loudnessLufs != null) {
          GroupDivider()
          InfoRow(
            title = stringResource(Res.string.audio_adv_loudness_applied),
            // 0 dB: the track is already at or below the target (the footer says it never raises).
            value = if (loudness.gainDb < -0.05) formatDb(loudness.gainDb.toFloat()) + " dB" else "0 dB"
          )
        }
      }
    }
    GroupFooter(
      stringResource(Res.string.audio_adv_loudness_footer)
    )
  }
}

/** "−9.8 LUFS · tag do arquivo", or what is missing. */
@Composable
private fun loudnessReadout(l: LoudnessUi): String {
  val lufs = l.loudnessLufs ?: return stringResource(Res.string.audio_adv_loudness_measuring)
  val source = when (l.source) {
    LoudnessSource.TAG_TRACK -> stringResource(Res.string.audio_adv_loudness_src_track)
    LoudnessSource.TAG_ALBUM -> stringResource(Res.string.audio_adv_loudness_src_album)
    LoudnessSource.MEASURED -> stringResource(Res.string.audio_adv_loudness_src_measured)
    LoudnessSource.NONE -> ""
  }
  return formatDb(lufs.toFloat()) + " LUFS · " + source
}

/** Names a group of sections: Som, Reprodução, Engenharia (the layers of the product). */
@Composable
private fun GroupLabel(text: String) {
  Text(
    text,
    style = EqType.BodyStrong,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(start = Grid.margin, end = Grid.margin, top = Grid.u24, bottom = Grid.u12).semantics { heading() }
  )
}

/** A titled block of the page: the group header, then its content, with the gap to the next one. */
@Composable
internal fun Section(title: String?, content: @Composable () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(top = Grid.u24)) {
    if (title != null) GroupHeader(title)
    content()
  }
}
