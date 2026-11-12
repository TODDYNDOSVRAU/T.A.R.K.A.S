package com.example.ui.screens.audio

import androidx.compose.ui.res.pluralStringResource
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.LocalActivity
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.audio.dsp.EqDsp
import com.example.data.audio.loudness.LoudnessSource
import com.example.data.audio.loudness.LoudnessUi
import com.example.data.audio.loudness.NormalizationMode
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqSettings
import com.example.data.systemaudio.ActiveAppsState
import com.example.data.systemaudio.EqReach
import com.example.data.systemaudio.GlobalEqStatus
import com.example.data.systemaudio.SystemAudioStatus
import com.example.ui.components.GroupDivider
import com.example.ui.components.GroupFooter
import com.example.ui.components.GroupHeader
import com.example.ui.components.InfoRow
import com.example.ui.components.InsetGroup
import com.example.ui.components.ListRow
import com.example.ui.components.RadioRow
import com.example.ui.components.SubPageHeader
import com.example.ui.components.SwitchRow
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Shapes
import com.example.ui.theme.Space
import com.example.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DUMP_COMMAND = "adb shell pm grant com.aistudio.tarkas.app android.permission.DUMP"

/** How often "Apps tocando agora" is read again while the page is on screen. */
private const val ACTIVE_APPS_REFRESH_MS = 2500L

/**
 * The technical controls behind "Ajustes avançados": how far the equalizer reaches, the tone
 * corners, the output stage, the apps playing now and the system level meter.
 */
@Composable
internal fun EqAdvancedPage(
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
  loudness: LoudnessUi,
  onSetNormalizationMode: (NormalizationMode) -> Unit,
  crossfadeSeconds: Int = 0,
  onSetCrossfade: (Int) -> Unit = {},
  spatialOn: Boolean = false,
  outputCorrection: com.example.ui.screens.OutputCorrectionUi = com.example.ui.screens.OutputCorrectionUi(),
  onSetAutoOutputCorrection: (Boolean) -> Unit = {},
  onBack: () -> Unit
) {
  val reduced = rememberReducedMotion()

  // The level meter runs only while this page is open: leaving it ends the capture and its service.
  // A rotation also disposes the page, but the page comes straight back; stopping then would throw
  // away the screen-capture consent the user just gave, so a configuration change is not a leave.
  val activity = LocalActivity.current
  val stopMeter by rememberUpdatedState(onStopSystemAudioCapture)
  DisposableEffect(Unit) {
    onDispose { if (activity?.isChangingConfigurations != true) stopMeter() }
  }

  // Keep "Apps tocando agora" fresh, but only while the page is actually on screen: reading it runs
  // dumpsys, which must not keep going with the app in the background.
  val lifecycleOwner = LocalLifecycleOwner.current
  val refresh by rememberUpdatedState(onRefreshActiveApps)
  LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
      while (true) {
        refresh()
        delay(ACTIVE_APPS_REFRESH_MS)
      }
    }
  }

  val colors = MaterialTheme.colorScheme
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    item {
      SubPageHeader(
        title = stringResource(R.string.audio_menu_advanced),
        backLabel = stringResource(R.string.audio_adv_back),
        onBack = onBack,
        modifier = Modifier.testTag("eq_subpage_back")
      )
    }

    item { GroupLabel(stringResource(R.string.audio_adv_group_sound)) }

    item {
      Section(stringResource(R.string.audio_adv_section_correction)) {
        InsetGroup {
          SwitchRow(
            title = stringResource(R.string.audio_adv_auto_correction),
            subtitle = stringResource(R.string.audio_adv_output_now, outputCorrection.outputName),
            checked = outputCorrection.auto,
            onCheckedChange = onSetAutoOutputCorrection,
            modifier = Modifier.testTag("switch_auto_output_correction")
          )
        }
        GroupFooter(
          if (outputCorrection.auto) {
            stringResource(R.string.audio_adv_correction_auto_footer)
          } else {
            stringResource(R.string.audio_adv_correction_manual_footer)
          }
        )
      }
    }

    item {
      Section(stringResource(R.string.audio_adv_section_tone)) {
        InsetGroup {
          LogSliderRow(
            label = stringResource(R.string.audio_adv_bass_freq),
            valueText = spokenHz(eqSettings.bassFreqHz),
            value = eqSettings.bassFreqHz,
            min = 30f, max = 400f,
            onChange = { v -> onUpdateEq { it.copy(bassFreqHz = v) } }
          )
          GroupDivider()
          LogSliderRow(
            label = stringResource(R.string.audio_adv_treble_freq),
            valueText = spokenHz(eqSettings.trebleFreqHz),
            value = eqSettings.trebleFreqHz,
            min = 1500f, max = 16000f,
            onChange = { v -> onUpdateEq { it.copy(trebleFreqHz = v) } }
          )
        }
        GroupFooter(stringResource(R.string.audio_adv_tone_footer))
      }
    }

    item {
      Section(stringResource(R.string.audio_adv_section_output)) {
        InsetGroup {
          SliderRow(
            label = stringResource(R.string.audio_preamp_long),
            valueText = formatDb(eqSettings.preampDb) + " dB",
            value = eqSettings.preampDb,
            range = -EqSettings.PREAMP_RANGE_DB..EqSettings.PREAMP_RANGE_DB,
            testTag = "fader_preamp",
            onChange = { v -> onUpdateEq { it.copy(preampDb = snap(v)) } }
          )
          GroupDivider()
          val auto = EqDsp.autoPreampDb(eqSettings)
          SwitchRow(
            title = stringResource(R.string.audio_adv_auto_preamp),
            subtitle = if (eqSettings.autoPreamp && auto < 0f) stringResource(R.string.audio_adv_auto_preamp_lowers, formatDb(-auto))
            else stringResource(R.string.audio_adv_auto_preamp_idle),
            checked = eqSettings.autoPreamp,
            onCheckedChange = { on -> onUpdateEq { it.copy(autoPreamp = on) } },
            modifier = Modifier.testTag("switch_auto_preamp")
          )
          GroupDivider()
          SliderRow(
            label = stringResource(R.string.audio_adv_balance),
            valueText = balanceText(eqSettings.balance),
            value = eqSettings.balance,
            range = -1f..1f,
            onChange = { v -> onUpdateEq { it.copy(balance = if (abs(v) < 0.03f) 0f else v) } }
          )
          GroupDivider()
          SliderRow(
            label = stringResource(R.string.audio_adv_stereo_width),
            valueText = widthText(eqSettings.stereoWidth),
            value = eqSettings.stereoWidth,
            range = 0f..2f,
            onChange = { v -> onUpdateEq { it.copy(stereoWidth = if (abs(v - 1f) < 0.04f) 1f else v) } }
          )
          GroupDivider()
          SwitchRow(
            title = stringResource(R.string.audio_switch_limiter),
            subtitle = stringResource(R.string.audio_adv_limiter_sub),
            checked = eqSettings.limiterEnabled,
            onCheckedChange = { on -> onUpdateEq { it.copy(limiterEnabled = on) } },
            modifier = Modifier.testTag("switch_limiter_advanced")
          )
          AnimatedVisibility(
            visible = eqSettings.limiterEnabled,
            enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut()
          ) {
            Column {
              GroupDivider()
              SliderRow(
                label = stringResource(R.string.audio_adv_limiter_ceiling),
                valueText = formatDb(eqSettings.limiterCeilingDb) + " dB",
                value = eqSettings.limiterCeilingDb,
                range = -12f..0f,
                onChange = { v -> onUpdateEq { it.copy(limiterCeilingDb = (v * 2).roundToInt() / 2f) } }
              )
            }
          }
          if (capabilities.isVirtualizerAvailable) {
            GroupDivider()
            SliderRow(
              label = stringResource(R.string.audio_adv_virtualizer),
              valueText = "${eqSettings.virtualizerStrength / 10}%",
              value = eqSettings.virtualizerStrength / 1000f,
              range = 0f..1f,
              testTag = "knob_virtualizer",
              onChange = { v -> onUpdateEq { it.copy(virtualizerStrength = (v * 1000).roundToInt()) } }
            )
          }
        }
        eqSettings.device?.let { d ->
          GroupFooter(stringResource(R.string.audio_adv_device_reduces, formatDb(-d.preampDb)))
        }
        capabilities.trackFormat?.let { format ->
          val max = capabilities.outputMaxRateHz
          GroupFooter(
            if (capabilities.outputBelowTrackRate && max != null) {
              stringResource(
                R.string.audio_adv_track_format_downsample,
                format, capabilities.activeRouteName, com.example.data.audio.TrackFormat.khz(max)
              )
            } else {
              stringResource(R.string.audio_adv_track_format, format)
            }
          )
        }
        if (capabilities.isVirtualizerAvailable) {
          GroupFooter(stringResource(R.string.audio_adv_virtualizer_footer))
        }
      }
    }

    item { GroupLabel(stringResource(R.string.audio_adv_group_playback)) }

    item { LoudnessSection(loudness, onSetNormalizationMode) }

    item {
      Section(stringResource(R.string.audio_adv_section_transition)) {
        InsetGroup {
          SwitchRow(
            title = stringResource(R.string.audio_adv_crossfade),
            subtitle = if (crossfadeSeconds > 0) stringResource(R.string.audio_adv_crossfade_on, crossfadeSeconds)
            else stringResource(R.string.audio_adv_crossfade_off),
            checked = crossfadeSeconds > 0,
            onCheckedChange = { on -> onSetCrossfade(if (on) com.example.data.playback.CrossfadePlanner.DEFAULT_SECONDS else 0) },
            modifier = Modifier.testTag("switch_crossfade")
          )
          AnimatedVisibility(
            visible = crossfadeSeconds > 0,
            enter = if (reduced) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (reduced) ExitTransition.None else shrinkVertically() + fadeOut()
          ) {
            Column {
              GroupDivider()
              SliderRow(
                label = stringResource(R.string.audio_adv_duration),
                valueText = "$crossfadeSeconds s",
                value = crossfadeSeconds.toFloat(),
                range = 1f..12f,
                onChange = { v -> onSetCrossfade(v.roundToInt().coerceIn(1, 12)) }
              )
            }
          }
        }
        GroupFooter(
          when {
            crossfadeSeconds > 0 && spatialOn ->
              stringResource(R.string.audio_adv_crossfade_spatial)
            else ->
              stringResource(R.string.audio_adv_crossfade_footer)
          }
        )
      }
    }

    item { GroupLabel(stringResource(R.string.audio_adv_group_engineering)) }

    item { ReachSection(eqReach, globalEqStatus, rootPresent, onSetEqReach) }

    item { Section(stringResource(R.string.audio_adv_section_apps)) { ActiveAppsGroup(activeApps) } }

    item {
      Section(stringResource(R.string.audio_adv_section_meter)) {
        InsetGroup(modifier = Modifier.testTag("system_audio_capture_card")) {
          InfoRow(
            title = stringResource(R.string.audio_adv_system_audio),
            value = stringResource(if (systemAudioStatus.isCapturing) R.string.audio_adv_meter_active else R.string.audio_adv_meter_inactive)
          )
          if (systemAudioStatus.isCapturing) {
            val level by animateFloatAsState(
              targetValue = systemAudioStatus.currentRmsEnergy.coerceIn(0.02f, 1f),
              animationSpec = if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 900f),
              label = "level"
            )
            Box(
              modifier = Modifier
                .padding(horizontal = Space.l)
                .padding(bottom = Space.m)
                .fillMaxWidth()
                .height(6.dp)
                .clip(Shapes.pill)
                .background(colors.surfaceContainerHigh)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth(level)
                  .height(6.dp)
                  .clip(Shapes.pill)
                  .background(colors.primary)
              )
            }
          }
          GroupDivider()
          ListRow(
            title = stringResource(if (systemAudioStatus.isCapturing) R.string.audio_adv_meter_stop else R.string.audio_adv_meter_start),
            onClick = if (systemAudioStatus.isCapturing) onStopSystemAudioCapture else onRequestSystemAudioCapture,
            chevron = false,
            enabled = systemAudioStatus.isMediaProjectionSupported,
            modifier = Modifier.testTag("btn_request_system_audio_capture")
          )
        }
        systemAudioStatus.errorMessage?.let { msg ->
          Text(
            msg,
            style = AppText.Footnote,
            color = colors.error,
            modifier = Modifier.padding(start = Space.page + Space.l, end = Space.page + Space.l, top = Space.s)
          )
        }
        GroupFooter(
          stringResource(R.string.audio_adv_meter_footer)
        )
      }
    }

    // Honest limits of Android audio
    item {
      GroupFooter(
        if (eqReach == EqReach.APP) {
          stringResource(R.string.audio_adv_limits_app)
        } else {
          stringResource(R.string.audio_adv_limits_other)
        },
        modifier = Modifier.padding(top = Space.xl)
      )
    }
  }
}

/**
 * Volume entre faixas: the normalization mode, then what it does to the playing track (where its
 * loudness came from and the change applied), so the effect can be checked, not only trusted.
 */
@Composable
private fun LoudnessSection(loudness: LoudnessUi, onSetMode: (NormalizationMode) -> Unit) {
  Section(stringResource(R.string.audio_adv_section_loudness)) {
    InsetGroup(modifier = Modifier.testTag("loudness_panel")) {
      RadioRow(
        title = stringResource(R.string.audio_adv_loudness_off),
        selected = loudness.mode == NormalizationMode.OFF,
        onClick = { onSetMode(NormalizationMode.OFF) }
      )
      GroupDivider()
      RadioRow(
        title = stringResource(R.string.audio_adv_loudness_auto),
        subtitle = stringResource(R.string.audio_adv_loudness_auto_sub),
        selected = loudness.mode == NormalizationMode.AUTO,
        onClick = { onSetMode(NormalizationMode.AUTO) }
      )
      GroupDivider()
      RadioRow(
        title = stringResource(R.string.audio_adv_loudness_track),
        subtitle = stringResource(R.string.audio_adv_loudness_track_sub),
        selected = loudness.mode == NormalizationMode.TRACK,
        onClick = { onSetMode(NormalizationMode.TRACK) }
      )
      GroupDivider()
      RadioRow(
        title = stringResource(R.string.audio_adv_loudness_album),
        subtitle = stringResource(R.string.audio_adv_loudness_album_sub),
        selected = loudness.mode == NormalizationMode.ALBUM,
        onClick = { onSetMode(NormalizationMode.ALBUM) }
      )
      if (loudness.mode != NormalizationMode.OFF && loudness.hasTrack) {
        GroupDivider()
        InfoRow(title = stringResource(R.string.audio_adv_loudness_current), value = loudnessReadout(loudness))
        if (loudness.loudnessLufs != null) {
          GroupDivider()
          InfoRow(
            title = stringResource(R.string.audio_adv_loudness_applied),
            // 0 dB: the track is already at or below the target (the footer says it never raises).
            value = if (loudness.gainDb < -0.05) formatDb(loudness.gainDb.toFloat()) + " dB" else "0 dB"
          )
        }
      }
    }
    GroupFooter(
      stringResource(R.string.audio_adv_loudness_footer)
    )
  }
}

/** "−9.8 LUFS · tag do arquivo", or what is missing. */
@Composable
private fun loudnessReadout(l: LoudnessUi): String {
  val lufs = l.loudnessLufs ?: return stringResource(R.string.audio_adv_loudness_measuring)
  val source = when (l.source) {
    LoudnessSource.TAG_TRACK -> stringResource(R.string.audio_adv_loudness_src_track)
    LoudnessSource.TAG_ALBUM -> stringResource(R.string.audio_adv_loudness_src_album)
    LoudnessSource.MEASURED -> stringResource(R.string.audio_adv_loudness_src_measured)
    LoudnessSource.NONE -> ""
  }
  return formatDb(lufs.toFloat()) + " LUFS · " + source
}

/** Names a group of sections: Som, Reprodução, Engenharia (the layers of the product). */
@Composable
private fun GroupLabel(text: String) {
  Text(
    text,
    style = AppText.Title3,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.padding(start = Space.page, end = Space.page, top = Space.l, bottom = Space.xs).semantics { heading() }
  )
}

/** A titled block of the page: the group header, then its content, with the gap to the next one. */
@Composable
internal fun Section(title: String?, content: @Composable () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(top = Space.xl)) {
    if (title != null) GroupHeader(title)
    content()
  }
}

// EqReach is a data enum with Portuguese text; the UI maps each entry to its string resource.
@Composable
private fun reachLabel(r: EqReach): String = stringResource(
  when (r) {
    EqReach.APP -> R.string.audio_adv_reach_app
    EqReach.ADB -> R.string.audio_adv_reach_adb
    EqReach.ROOT -> R.string.audio_adv_reach_root
  }
)

@Composable
private fun reachDescription(r: EqReach): String = stringResource(
  when (r) {
    EqReach.APP -> R.string.audio_adv_reach_app_desc
    EqReach.ADB -> R.string.audio_adv_reach_adb_desc
    EqReach.ROOT -> R.string.audio_adv_reach_root_desc
  }
)

/** How far the equalizer reaches, with what each mode needs and what it is doing now. */
@Composable
private fun ReachSection(reach: EqReach, status: GlobalEqStatus, rootPresent: Boolean, onSetReach: (EqReach) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Section(stringResource(R.string.audio_adv_section_reach)) {
    InsetGroup(modifier = Modifier.testTag("eq_reach_panel")) {
      EqReach.entries.forEachIndexed { i, r ->
        if (i > 0) GroupDivider()
        RadioRow(
          title = reachLabel(r),
          selected = r == reach,
          onClick = { onSetReach(r) },
          modifier = Modifier.testTag("eq_reach_${r.key}")
        )
      }
    }
    GroupFooter(reachDescription(reach))

    val line: String? = when (reach) {
      EqReach.APP -> null
      EqReach.ADB -> when {
        status.needsDump -> null
        status.apps.isEmpty() -> stringResource(R.string.audio_adv_reach_adb_idle)
        else -> stringResource(R.string.audio_adv_reach_now, status.apps.joinToString(", "))
      }
      EqReach.ROOT -> when {
        !rootPresent -> stringResource(R.string.audio_adv_reach_no_root)
        status.rootUnavailable -> stringResource(R.string.audio_adv_reach_root_denied)
        status.apps.isEmpty() -> stringResource(
          if (status.globalOutput) R.string.audio_adv_reach_root_idle_global else R.string.audio_adv_reach_root_idle
        )
        else -> stringResource(
          if (status.globalOutput) R.string.audio_adv_reach_now_global else R.string.audio_adv_reach_now,
          status.apps.joinToString(", ")
        )
      }
    }
    val conflict = status.appsWithoutControl.takeIf { it > 0 && reach != EqReach.APP }?.let { n ->
      " " + pluralStringResource(R.plurals.audio_adv_reach_conflict, n, n)
    }.orEmpty()
    (line?.plus(conflict))?.let {
      Text(
        it,
        style = AppText.Footnote,
        color = colors.primary,
        modifier = Modifier.padding(start = Space.page + Space.l, end = Space.page + Space.l, top = Space.s)
      )
    }
    if (reach == EqReach.ADB && status.needsDump) {
      Spacer(Modifier.height(Space.l))
      GroupHeader(stringResource(R.string.audio_adv_dump_missing))
      InsetGroup { CommandRow(DUMP_COMMAND) }
      GroupFooter(
        stringResource(R.string.audio_adv_dump_steps)
      )
    }
  }
}

/** The apps playing now, read from dumpsys audio with the DUMP permission. */
@Composable
private fun ActiveAppsGroup(state: ActiveAppsState) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.testTag("active_apps_list")) {
    when (state) {
      is ActiveAppsState.PermissionMissing -> {
        InsetGroup { CommandRow(DUMP_COMMAND) }
        GroupFooter(
          stringResource(R.string.audio_adv_dump_apps)
        )
      }

      is ActiveAppsState.Failed -> Text(
        state.message,
        style = AppText.Footnote,
        color = colors.error,
        modifier = Modifier.padding(horizontal = Space.page + Space.l)
      )

      is ActiveAppsState.Ready -> {
        if (state.apps.isEmpty()) {
          Text(
            stringResource(R.string.audio_adv_no_other_apps),
            style = AppText.Footnote,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.page + Space.l)
          )
        } else {
          InsetGroup {
            state.apps.forEachIndexed { i, app ->
              if (i > 0) GroupDivider()
              InfoRow(title = app.label, value = usageLabel(app.usage))
            }
          }
        }
      }
    }
  }
}
