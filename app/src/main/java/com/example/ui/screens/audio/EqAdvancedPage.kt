package com.example.ui.screens.audio

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
        title = "Ajustes avançados",
        backLabel = "Áudio",
        onBack = onBack,
        modifier = Modifier.testTag("eq_subpage_back")
      )
    }

    item { ReachSection(eqReach, globalEqStatus, rootPresent, onSetEqReach) }

    item {
      Section("Correção do fone") {
        InsetGroup {
          SwitchRow(
            title = "Trocar a correção com o fone",
            subtitle = "Saída agora: ${outputCorrection.outputName}",
            checked = outputCorrection.auto,
            onCheckedChange = onSetAutoOutputCorrection,
            modifier = Modifier.testTag("switch_auto_output_correction")
          )
        }
        GroupFooter(
          if (outputCorrection.auto) {
            "A correção escolhida com um fone conectado fica guardada para ele e volta sozinha quando ele reconecta. " +
              "No alto-falante, nenhuma correção, a menos que você escolha uma com ele tocando. " +
              "Fones com fio são tratados como um só, porque o conector não informa o modelo."
          } else {
            "A correção escolhida fica a mesma em qualquer saída."
          }
        )
      }
    }

    item {
      Section("Graves e agudos") {
        InsetGroup {
          LogSliderRow(
            label = "Frequência dos graves",
            valueText = spokenHz(eqSettings.bassFreqHz),
            value = eqSettings.bassFreqHz,
            min = 30f, max = 400f,
            onChange = { v -> onUpdateEq { it.copy(bassFreqHz = v) } }
          )
          GroupDivider()
          LogSliderRow(
            label = "Frequência dos agudos",
            valueText = spokenHz(eqSettings.trebleFreqHz),
            value = eqSettings.trebleFreqHz,
            min = 1500f, max = 16000f,
            onChange = { v -> onUpdateEq { it.copy(trebleFreqHz = v) } }
          )
        }
        GroupFooter("Onde os botões de graves e agudos começam a agir.")
      }
    }

    item {
      Section("Saída") {
        InsetGroup {
          SliderRow(
            label = "Pré-amplificação",
            valueText = formatDb(eqSettings.preampDb) + " dB",
            value = eqSettings.preampDb,
            range = -EqSettings.PREAMP_RANGE_DB..EqSettings.PREAMP_RANGE_DB,
            testTag = "fader_preamp",
            onChange = { v -> onUpdateEq { it.copy(preampDb = snap(v)) } }
          )
          GroupDivider()
          val auto = EqDsp.autoPreampDb(eqSettings)
          SwitchRow(
            title = "Pré-amp automático",
            subtitle = if (eqSettings.autoPreamp && auto < 0f) "Baixa ${formatDb(-auto)} dB para os reforços não distorcerem"
            else "Baixa o volume o quanto a curva reforça, para não distorcer",
            checked = eqSettings.autoPreamp,
            onCheckedChange = { on -> onUpdateEq { it.copy(autoPreamp = on) } },
            modifier = Modifier.testTag("switch_auto_preamp")
          )
          GroupDivider()
          SliderRow(
            label = "Balanço",
            valueText = balanceText(eqSettings.balance),
            value = eqSettings.balance,
            range = -1f..1f,
            onChange = { v -> onUpdateEq { it.copy(balance = if (abs(v) < 0.03f) 0f else v) } }
          )
          GroupDivider()
          SliderRow(
            label = "Largura estéreo",
            valueText = widthText(eqSettings.stereoWidth),
            value = eqSettings.stereoWidth,
            range = 0f..2f,
            onChange = { v -> onUpdateEq { it.copy(stereoWidth = if (abs(v - 1f) < 0.04f) 1f else v) } }
          )
          GroupDivider()
          SwitchRow(
            title = "Limitador",
            subtitle = "Evita distorção quando a curva soma ganho demais",
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
                label = "Teto do limitador",
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
              label = "Virtualizador (palco)",
              valueText = "${eqSettings.virtualizerStrength / 10}%",
              value = eqSettings.virtualizerStrength / 1000f,
              range = 0f..1f,
              testTag = "knob_virtualizer",
              onChange = { v -> onUpdateEq { it.copy(virtualizerStrength = (v * 1000).roundToInt()) } }
            )
          }
        }
        eqSettings.device?.let { d ->
          GroupFooter("A correção do fone já reduz ${formatDb(-d.preampDb)} dB para não distorcer.")
        }
        capabilities.trackFormat?.let { format ->
          val max = capabilities.outputMaxRateHz
          GroupFooter(
            if (capabilities.outputBelowTrackRate && max != null) {
              "Faixa tocando: $format. A saída ${capabilities.activeRouteName} vai até ${com.example.data.audio.TrackFormat.khz(max)}, " +
                "então o Android reduz a taxa antes de tocar."
            } else {
              "Faixa tocando: $format."
            }
          )
        }
        if (capabilities.isVirtualizerAvailable) {
          GroupFooter("O virtualizador é um efeito do próprio Android. O resultado muda de aparelho para aparelho.")
        }
      }
    }

    item { LoudnessSection(loudness, onSetNormalizationMode) }

    item { Section("Apps tocando agora") { ActiveAppsGroup(activeApps) } }

    item {
      Section("Medidor de nível") {
        InsetGroup(modifier = Modifier.testTag("system_audio_capture_card")) {
          InfoRow(title = "Áudio do sistema", value = if (systemAudioStatus.isCapturing) "Ativo" else "Inativo")
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
            title = if (systemAudioStatus.isCapturing) "Desligar medidor" else "Ativar medidor",
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
          "Experimental. Mede o volume do áudio tocado por outros apps, usando a captura de áudio do Android (10 ou mais). " +
            "Apps que bloqueiam a captura não aparecem. Não aplica equalização neles e não identifica a música. " +
            "Desliga sozinho quando você sai desta página."
        )
      }
    }

    // Honest limits of Android audio
    item {
      GroupFooter(
        if (eqReach == EqReach.APP) {
          "No modo sem root, o equalizador processa só o que o T.A.R.K.A.S. toca. Para os outros apps, escolha ADB ou root em Alcance."
        } else {
          "Nos outros apps valem a correção do fone, as bandas, o grave, o agudo, o pré-amplificador, o limitador e, no Android 9 ou mais novo, o balanço. " +
            "Largura estéreo e virtualizador valem só para o que o T.A.R.K.A.S. toca. Alguns apps protegidos (chamadas, DRM) podem recusar o efeito. " +
            "Para seguir a curva até nos graves, o som dos outros apps passa a sair cerca de 85 ms atrasado: em vídeos, a voz pode ficar um pouco fora de sincronia."
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
  Section("Volume entre faixas") {
    InsetGroup(modifier = Modifier.testTag("loudness_panel")) {
      RadioRow(
        title = "Desligado",
        selected = loudness.mode == NormalizationMode.OFF,
        onClick = { onSetMode(NormalizationMode.OFF) }
      )
      GroupDivider()
      RadioRow(
        title = "Automático",
        subtitle = "Por álbum quando um álbum toca em ordem; por faixa no resto",
        selected = loudness.mode == NormalizationMode.AUTO,
        onClick = { onSetMode(NormalizationMode.AUTO) }
      )
      GroupDivider()
      RadioRow(
        title = "Por faixa",
        subtitle = "Todas as músicas no mesmo volume",
        selected = loudness.mode == NormalizationMode.TRACK,
        onClick = { onSetMode(NormalizationMode.TRACK) }
      )
      GroupDivider()
      RadioRow(
        title = "Por álbum",
        subtitle = "Mantém a diferença entre as faixas de um álbum, quando o arquivo traz o valor do álbum",
        selected = loudness.mode == NormalizationMode.ALBUM,
        onClick = { onSetMode(NormalizationMode.ALBUM) }
      )
      if (loudness.mode != NormalizationMode.OFF && loudness.hasTrack) {
        GroupDivider()
        InfoRow(title = "Faixa atual", value = loudnessReadout(loudness))
        if (loudness.loudnessLufs != null) {
          GroupDivider()
          InfoRow(
            title = "Ajuste aplicado",
            // 0 dB: the track is already at or below the target (the footer says it never raises).
            value = if (loudness.gainDb < -0.05) formatDb(loudness.gainDb.toFloat()) + " dB" else "0 dB"
          )
        }
      }
    }
    GroupFooter(
      "Iguala as músicas em −14 LUFS. Só abaixa o volume, nunca aumenta, para não distorcer. " +
        "Usa o valor ReplayGain do arquivo, se houver. Sem ele, mede a música enquanto toca e aplica a partir da próxima vez. " +
        "A medição só vale se a música tocar quase inteira."
    )
  }
}

/** "−9.8 LUFS · tag do arquivo", or what is missing. */
private fun loudnessReadout(l: LoudnessUi): String {
  val lufs = l.loudnessLufs ?: return "Medindo nesta reprodução"
  val source = when (l.source) {
    LoudnessSource.TAG_TRACK -> "tag da faixa"
    LoudnessSource.TAG_ALBUM -> "tag do álbum"
    LoudnessSource.MEASURED -> "medido"
    LoudnessSource.NONE -> ""
  }
  return formatDb(lufs.toFloat()) + " LUFS · " + source
}

/** A titled block of the page: the group header, then its content, with the gap to the next one. */
@Composable
internal fun Section(title: String?, content: @Composable () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(top = Space.xl)) {
    if (title != null) GroupHeader(title)
    content()
  }
}

/** How far the equalizer reaches, with what each mode needs and what it is doing now. */
@Composable
private fun ReachSection(reach: EqReach, status: GlobalEqStatus, rootPresent: Boolean, onSetReach: (EqReach) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Section("Alcance do equalizador") {
    InsetGroup(modifier = Modifier.testTag("eq_reach_panel")) {
      EqReach.entries.forEachIndexed { i, r ->
        if (i > 0) GroupDivider()
        RadioRow(
          title = r.label,
          selected = r == reach,
          onClick = { onSetReach(r) },
          modifier = Modifier.testTag("eq_reach_${r.key}")
        )
      }
    }
    GroupFooter(reach.description)

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
    val conflict = status.appsWithoutControl.takeIf { it > 0 && reach != EqReach.APP }?.let { n ->
      " Em $n ${if (n == 1) "app" else "apps"}, outro app de equalizador tem prioridade e esta curva não é aplicada. Desligue o outro para usar a do T.A.R.K.A.S."
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
      GroupHeader("Falta a permissão DUMP")
      InsetGroup { CommandRow(DUMP_COMMAND) }
      GroupFooter(
        "Ligue a depuração USB, conecte o celular ao computador e rode o comando uma vez. " +
          "Até lá, só os players que avisam a própria sessão de áudio são equalizados."
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
          "Para ver quais apps estão tocando, libere a permissão DUMP uma vez. O Android só a concede por computador, " +
            "com este comando. É com ela que o modo ADB encontra os outros apps para equalizar."
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
            "Nenhum outro app está tocando agora.",
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
