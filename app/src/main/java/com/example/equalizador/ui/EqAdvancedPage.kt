package com.example.ui.screens.audio

import com.example.ui.theme.Grid
import com.example.ui.theme.EqSize
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
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Motion
import com.example.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
  onSetTargetLufs: (Double) -> Unit = {},
  audioHealth: com.example.data.audio.AudioHealth = com.example.data.audio.AudioHealth(),
  crossfadeSeconds: Int = 0,
  onSetCrossfade: (Int) -> Unit = {},
  spatialOn: Boolean = false,
  outputCorrection: com.example.ui.screens.OutputCorrectionUi = com.example.ui.screens.OutputCorrectionUi(),
  onSetAutoOutputCorrection: (Boolean) -> Unit = {},
  onExportOutputProfiles: () -> String = { "{}" },
  onImportOutputProfiles: (String) -> Int = { -1 },
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
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Grid.u24)
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
      val ctx = androidx.compose.ui.platform.LocalContext.current
      val scope = androidx.compose.runtime.rememberCoroutineScope()
      val exportedMsg = stringResource(R.string.audio_adv_profiles_exported)
      val importedFmt = stringResource(R.string.audio_adv_profiles_imported)
      val invalidProfilesMsg = stringResource(R.string.audio_adv_profiles_invalid)
      val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
      ) { uri ->
        if (uri != null) {
          val appCtx = ctx.applicationContext
          val json = onExportOutputProfiles()
          scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
              runCatching {
                appCtx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                true
              }.getOrDefault(false)
            }
            if (ok) android.widget.Toast.makeText(appCtx, exportedMsg, android.widget.Toast.LENGTH_SHORT).show()
          }
        }
      }
      val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
      ) { uri ->
        if (uri != null) {
          val appCtx = ctx.applicationContext
          scope.launch {
            val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
              runCatching { appCtx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            val count = if (text != null) onImportOutputProfiles(text) else -1
            val msg = if (count >= 0) String.format(importedFmt, count) else invalidProfilesMsg
            android.widget.Toast.makeText(appCtx, msg, android.widget.Toast.LENGTH_SHORT).show()
          }
        }
      }
      Section(stringResource(R.string.audio_adv_section_correction)) {
        InsetGroup {
          SwitchRow(
            title = stringResource(R.string.audio_adv_auto_correction),
            subtitle = stringResource(R.string.audio_adv_output_now, outputCorrection.outputName),
            checked = outputCorrection.auto,
            onCheckedChange = onSetAutoOutputCorrection,
            modifier = Modifier.testTag("switch_auto_output_correction")
          )
          GroupDivider()
          ListRow(
            title = stringResource(R.string.audio_adv_profiles_export),
            onClick = { exportLauncher.launch("tarkas-output-profiles.json") },
            modifier = Modifier.testTag("btn_export_output_profiles")
          )
          GroupDivider()
          ListRow(
            title = stringResource(R.string.audio_adv_profiles_import),
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.testTag("btn_import_output_profiles")
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
            defaultValue = 100f,
            onChange = { v -> onUpdateEq { it.copy(bassFreqHz = v) } }
          )
          GroupDivider()
          LogSliderRow(
            label = stringResource(R.string.audio_adv_treble_freq),
            valueText = spokenHz(eqSettings.trebleFreqHz),
            value = eqSettings.trebleFreqHz,
            min = 1500f, max = 16000f,
            defaultValue = 8000f,
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
            defaultValue = 0f,
            testTag = "fader_preamp",
            onChange = { v -> onUpdateEq { it.copy(preampDb = snap(v)) } }
          )
          GroupDivider()
          val spatialStage = rememberSpatialStage()
          val auto = EqDsp.autoPreampDb(eqSettings, spatialStage)
          SwitchRow(
            title = stringResource(R.string.audio_adv_auto_preamp),
            subtitle = if (eqSettings.autoPreamp && auto < 0f) {
              stringResource(if (spatialStage) R.string.audio_adv_auto_preamp_lowers_spatial else R.string.audio_adv_auto_preamp_lowers, formatDb(-auto))
            }
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
            defaultValue = 0f,
            onChange = { v -> onUpdateEq { it.copy(balance = if (abs(v) < 0.03f) 0f else v) } }
          )
          GroupDivider()
          SliderRow(
            label = stringResource(R.string.audio_adv_stereo_width),
            valueText = widthText(eqSettings.stereoWidth),
            value = eqSettings.stereoWidth,
            range = 0f..2f,
            defaultValue = 1f,
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
            enter = if (reduced) EnterTransition.None else expandVertically(Motion.gel()) + fadeIn(),
            exit = if (reduced) ExitTransition.None else shrinkVertically(Motion.gelExit()) + fadeOut()
          ) {
            Column {
              GroupDivider()
              SliderRow(
                label = stringResource(R.string.audio_adv_limiter_ceiling),
                valueText = formatDb(eqSettings.limiterCeilingDb) + " dB",
                value = eqSettings.limiterCeilingDb,
                range = -12f..0f,
                defaultValue = 0f,
                onChange = { v -> onUpdateEq { it.copy(limiterCeilingDb = (v * 2).roundToInt() / 2f) } }
              )
              GroupDivider()
              SliderRow(
                label = stringResource(R.string.audio_adv_limiter_release),
                valueText = "${eqSettings.limiterReleaseMs.roundToInt()} ms",
                value = eqSettings.limiterReleaseMs,
                range = 20f..500f,
                defaultValue = 120f,
                testTag = "slider_limiter_release",
                onChange = { v -> onUpdateEq { it.copy(limiterReleaseMs = (v / 5f).roundToInt() * 5f) } }
              )
              GroupDivider()
              SwitchRow(
                title = stringResource(R.string.audio_adv_limiter_soft_clip),
                subtitle = stringResource(R.string.audio_adv_limiter_soft_clip_sub),
                checked = eqSettings.limiterSoftClip,
                onCheckedChange = { on -> onUpdateEq { it.copy(limiterSoftClip = on) } },
                modifier = Modifier.testTag("switch_limiter_soft_clip")
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
              defaultValue = 0f,
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

    item {
      AdvancedDspSection(eqSettings = eqSettings, reduced = reduced, onUpdateEq = onUpdateEq)
    }

    item {
      ConvolverSection(eqSettings = eqSettings, reduced = reduced, onUpdateEq = onUpdateEq)
    }

    item { LoudnessSection(loudness, onSetNormalizationMode, onSetTargetLufs) }

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
            enter = if (reduced) EnterTransition.None else expandVertically(Motion.gel()) + fadeIn(),
            exit = if (reduced) ExitTransition.None else shrinkVertically(Motion.gelExit()) + fadeOut()
          ) {
            Column {
              GroupDivider()
              SliderRow(
                label = stringResource(R.string.audio_adv_duration),
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
                .padding(horizontal = Grid.inset)
                .padding(bottom = Grid.inset)
                .fillMaxWidth()
                .height(EqSize.meterBar)
                .clip(Grid.r6)
                .background(colors.surfaceContainerHigh)
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth(level)
                  .height(EqSize.meterBar)
                  .clip(Grid.r6)
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
            style = EqType.Label,
            color = colors.error,
            modifier = Modifier.padding(start = Grid.margin + Grid.inset, end = Grid.margin + Grid.inset, top = Grid.u12)
          )
        }
        GroupFooter(
          stringResource(R.string.audio_adv_meter_footer)
        )
      }
    }

    // Live Engineering Pipeline & Telemetry (Hi-Res Route, Underruns, Thermal)
    item {
      LiveEngineeringSection(capabilities = capabilities, health = audioHealth)
    }

    // Honest limits of Android audio
    item {
      GroupFooter(
        if (eqReach == EqReach.APP) {
          stringResource(R.string.audio_adv_limits_app)
        } else {
          stringResource(R.string.audio_adv_limits_other)
        },
        modifier = Modifier.padding(top = Grid.u24)
      )
    }
  }
}

/**
 * Volume entre faixas: the normalization mode, then what it does to the playing track (where its
 * loudness came from and the change applied), so the effect can be checked, not only trusted.
 */
@Composable
private fun LoudnessSection(
  loudness: LoudnessUi,
  onSetMode: (NormalizationMode) -> Unit,
  onSetTargetLufs: (Double) -> Unit = {}
) {
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
      if (loudness.mode != NormalizationMode.OFF) {
        val nextTarget = when (loudness.targetLufs.roundToInt()) {
          -14 -> -16.0
          -16 -> -18.0
          else -> -14.0
        }
        val targetLabel = when (loudness.targetLufs.roundToInt()) {
          -14 -> "-14 LUFS (Streaming)"
          -16 -> "-16 LUFS (Apple)"
          -18 -> "-18 LUFS (EBU / RG)"
          else -> "${loudness.targetLufs.roundToInt()} LUFS"
        }
        GroupDivider()
        ListRow(
          title = stringResource(R.string.audio_adv_loudness_target),
          value = targetLabel,
          onClick = { onSetTargetLufs(nextTarget) },
          modifier = Modifier.testTag("loudness_target_row")
        )
      }
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

/** Live Engineering Panel: active audio route & native sample rate, track format, underrun counters, and thermal status. */
@Composable
private fun LiveEngineeringSection(
  capabilities: AudioCapabilities,
  health: com.example.data.audio.AudioHealth
) {
  val routeValue = buildString {
    append(capabilities.activeRouteName)
    if (capabilities.sampleRateHz > 0) append(" · ").append(capabilities.sampleRateHz / 1000).append(" kHz")
    capabilities.outputMaxRateHz?.takeIf { it > capabilities.sampleRateHz }?.let { maxHz ->
      append(" (máx ").append(maxHz / 1000).append(" kHz)")
    }
  }
  Section(stringResource(R.string.audio_adv_eng_pipeline)) {
    InsetGroup(modifier = Modifier.testTag("live_engineering_panel")) {
      InfoRow(
        title = stringResource(R.string.audio_adv_eng_route),
        value = routeValue
      )
      GroupDivider()
      InfoRow(
        title = stringResource(R.string.audio_adv_eng_track),
        value = capabilities.trackFormat ?: "—"
      )
      GroupDivider()
      InfoRow(
        title = stringResource(R.string.audio_adv_eng_health),
        value = "${health.underruns} · ${health.sinkErrors} · ${health.codecErrors}"
      )
      GroupDivider()
      InfoRow(
        title = stringResource(R.string.audio_adv_eng_thermal),
        value = com.example.data.diagnostics.ThermalMonitor.label()
      )
    }
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

// EqReach is a data enum without text; the UI maps each entry to its string resource.
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
        style = EqType.Label,
        color = colors.primary,
        modifier = Modifier.padding(start = Grid.margin + Grid.inset, end = Grid.margin + Grid.inset, top = Grid.u12)
      )
    }
    if (reach == EqReach.ADB && status.needsDump) {
      Spacer(Modifier.height(Grid.u12))
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
        style = EqType.Label,
        color = colors.error,
        modifier = Modifier.padding(horizontal = Grid.margin + Grid.inset)
      )

      is ActiveAppsState.Ready -> {
        if (state.apps.isEmpty()) {
          Text(
            stringResource(R.string.audio_adv_no_other_apps),
            style = EqType.Label,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Grid.margin + Grid.inset)
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


@Composable
private fun AdvancedDspSection(
  eqSettings: EqSettings,
  reduced: Boolean,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit
) {
  Section(stringResource(R.string.audio_adv_section_dsp)) {
    InsetGroup {
      SwitchRow(
        title = stringResource(R.string.audio_adv_equal_loudness),
        subtitle = stringResource(R.string.audio_adv_equal_loudness_sub),
        checked = eqSettings.equalLoudnessEnabled,
        onCheckedChange = { on -> onUpdateEq { it.copy(equalLoudnessEnabled = on) } },
        modifier = Modifier.testTag("switch_equal_loudness")
      )
      AnimatedVisibility(
        visible = eqSettings.equalLoudnessEnabled,
        enter = if (reduced) EnterTransition.None else expandVertically(Motion.gel()) + fadeIn(),
        exit = if (reduced) ExitTransition.None else shrinkVertically(Motion.gelExit()) + fadeOut()
      ) {
        Column {
          GroupDivider()
          SliderRow(
            label = stringResource(R.string.audio_adv_equal_loudness_level),
            valueText = "${eqSettings.equalLoudnessLevelPhon.roundToInt()} phon",
            value = eqSettings.equalLoudnessLevelPhon,
            range = com.example.data.audio.dsp.EqualLoudnessDsp.MIN_PHON..com.example.data.audio.dsp.EqualLoudnessDsp.MAX_PHON,
            defaultValue = 65f,
            testTag = "slider_equal_loudness_phon",
            onChange = { v -> onUpdateEq { it.copy(equalLoudnessLevelPhon = v.roundToInt().toFloat()) } }
          )
        }
      }
      GroupDivider()
      SliderRow(
        label = stringResource(R.string.audio_adv_compander),
        valueText = "${(eqSettings.companderAmount * 100f).roundToInt()}%",
        value = eqSettings.companderAmount,
        range = 0f..1f,
        defaultValue = 0f,
        testTag = "slider_compander",
        onChange = { v -> onUpdateEq { it.copy(companderAmount = (v * 20f).roundToInt() / 20f) } }
      )
      GroupDivider()
      SliderRow(
        label = stringResource(R.string.audio_adv_tube_warmth),
        valueText = "${(eqSettings.tubeWarmth * 100f).roundToInt()}%",
        value = eqSettings.tubeWarmth,
        range = 0f..1f,
        defaultValue = 0f,
        testTag = "slider_tube_warmth",
        onChange = { v -> onUpdateEq { it.copy(tubeWarmth = (v * 20f).roundToInt() / 20f) } }
      )
      GroupDivider()
      SliderRow(
        label = stringResource(R.string.audio_adv_sub_bass),
        valueText = "${(eqSettings.subBassHarmonic * 100f).roundToInt()}%",
        value = eqSettings.subBassHarmonic,
        range = 0f..1f,
        defaultValue = 0f,
        testTag = "slider_sub_bass",
        onChange = { v -> onUpdateEq { it.copy(subBassHarmonic = (v * 20f).roundToInt() / 20f) } }
      )
    }
    GroupFooter(stringResource(R.string.audio_adv_dsp_footer))
  }
}

@Composable
private fun ConvolverSection(
  eqSettings: EqSettings,
  reduced: Boolean,
  onUpdateEq: ((EqSettings) -> EqSettings) -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val loadedFmt = stringResource(R.string.audio_adv_convolver_loaded)
  val invalidMsg = stringResource(R.string.audio_adv_convolver_invalid)
  val irPicker = androidx.activity.compose.rememberLauncherForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      val appContext = context.applicationContext
      scope.launch {
        val savedPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          runCatching {
            var name = "impulse.wav"
            appContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
              val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
              if (idx != -1 && c.moveToFirst()) c.getString(idx)?.takeIf { it.isNotBlank() }?.let { name = it }
            }
            val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            if (com.example.data.audio.dsp.ConvolverDsp.parseWav(bytes, name) == null) return@runCatching null
            val dir = java.io.File(appContext.filesDir, "convolver").apply { mkdirs() }
            val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "impulse.wav" }
            val dest = java.io.File(dir, safe)
            dest.writeBytes(bytes)
            dest.absolutePath to name
          }.getOrNull()
        }
        if (savedPath != null) {
          onUpdateEq { it.copy(convolverPath = savedPath.first, convolverEnabled = true) }
          android.widget.Toast.makeText(appContext, String.format(loadedFmt, savedPath.second), android.widget.Toast.LENGTH_SHORT).show()
        } else {
          android.widget.Toast.makeText(appContext, invalidMsg, android.widget.Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  val loadedName = eqSettings.convolverPath?.let { java.io.File(it).name }
  Section(stringResource(R.string.audio_adv_section_convolver)) {
    InsetGroup {
      SwitchRow(
        title = stringResource(R.string.audio_adv_convolver_on),
        subtitle = loadedName ?: stringResource(R.string.audio_adv_convolver_none),
        checked = eqSettings.convolverEnabled && loadedName != null,
        onCheckedChange = { on ->
          if (loadedName != null) onUpdateEq { it.copy(convolverEnabled = on) }
          else irPicker.launch(arrayOf("audio/wav", "audio/x-wav", "application/octet-stream", "*/*"))
        },
        modifier = Modifier.testTag("switch_convolver")
      )
      GroupDivider()
      ListRow(
        title = stringResource(R.string.audio_adv_convolver_load),
        onClick = { irPicker.launch(arrayOf("audio/wav", "audio/x-wav", "application/octet-stream", "*/*")) },
        modifier = Modifier.testTag("btn_convolver_load")
      )
      AnimatedVisibility(
        visible = loadedName != null,
        enter = if (reduced) EnterTransition.None else expandVertically(Motion.gel()) + fadeIn(),
        exit = if (reduced) ExitTransition.None else shrinkVertically(Motion.gelExit()) + fadeOut()
      ) {
        Column {
          GroupDivider()
          SliderRow(
            label = stringResource(R.string.audio_adv_convolver_mix),
            valueText = "${(eqSettings.convolverMix * 100f).roundToInt()}%",
            value = eqSettings.convolverMix,
            range = 0f..1f,
            defaultValue = 1f,
            testTag = "slider_convolver_mix",
            onChange = { v -> onUpdateEq { it.copy(convolverMix = (v * 20f).roundToInt() / 20f) } }
          )
          GroupDivider()
          ListRow(
            title = stringResource(R.string.audio_adv_convolver_clear),
            chevron = false,
            onClick = { onUpdateEq { it.copy(convolverEnabled = false, convolverPath = null) } },
            modifier = Modifier.testTag("btn_convolver_clear")
          )
        }
      }
    }
  }
}
