package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioCapabilities
import com.example.data.model.AudioProfile
import com.example.data.systemaudio.ActiveAppsState
import com.example.data.systemaudio.SystemAudioStatus
import com.example.ui.components.TarkasSectionTitle
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.roundToInt

// Colours of the equalizer deck: always a dark panel, like a hardware unit, in both themes.
private val DeckPanel = Color(0xFF0A0D10)
private val DeckButton = Color(0xFF1A1F24)
private val DeckThumb = Color(0xFF242A30)
private val DeckGuide = Color(0xFF3A434B)
private val DeckText = Color(0xFFE9EBED)
private val DeckMuted = Color(0xFF8B949C)
private val DeckBoost = Color(0xFF7EE3EA)
private val DeckCut = Color(0xFF2F9FB0)

private const val BAND_RANGE_MB = 1500f
private const val PREAMP_RANGE_DB = 12f

@Composable
fun AudioScreen(
  capabilities: AudioCapabilities,
  activeProfile: AudioProfile,
  allProfiles: List<AudioProfile>,
  isEffectEnabled: Boolean,
  systemAudioStatus: SystemAudioStatus,
  isPlaying: Boolean = false,
  spectrumBars: List<Float> = emptyList(),
  preampDb: Float = 0f,
  onPreampChange: (Float) -> Unit = {},
  activeApps: ActiveAppsState = ActiveAppsState.PermissionMissing,
  onRefreshActiveApps: () -> Unit = {},
  onSelectProfile: (AudioProfile) -> Unit,
  onSaveCustomProfile: (String) -> Unit,
  onUpdateBandLevel: (Int, Short) -> Unit,
  onResetAllBandsToZero: () -> Unit,
  onUpdateBassBoost: (Short) -> Unit,
  onUpdateVirtualizer: (Short) -> Unit,
  onToggleEffectsEnabled: (Boolean) -> Unit,
  onRequestSystemAudioCapture: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showSaveProfileDialog by remember { mutableStateOf(false) }
  var newProfileName by remember { mutableStateOf("") }
  var showPresetMenu by remember { mutableStateOf(false) }

  val colors = MaterialTheme.colorScheme

  // Keep "Apps tocando agora" fresh while this screen is open.
  LaunchedEffect(Unit) {
    while (true) {
      onRefreshActiveApps()
      delay(2500)
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {

    // 1. Output route, master switch and what this device supports
    item {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Equalizador",
              fontWeight = FontWeight.Bold,
              fontSize = 22.sp,
              color = colors.onBackground
            )
            Text(
              text = "${capabilities.activeRouteName} • ${String.format("%.1f", capabilities.sampleRateHz / 1000f)} kHz",
              fontSize = 15.sp,
              color = colors.onSurfaceVariant
            )
          }
          Switch(
            checked = isEffectEnabled,
            onCheckedChange = onToggleEffectsEnabled,
            colors = SwitchDefaults.colors(
              checkedTrackColor = colors.primary,
              checkedThumbColor = colors.onPrimary
            ),
            modifier = Modifier.testTag("audio_dsp_switch")
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          CapabilityPill("Equalizador", capabilities.isHardwareEqualizerAvailable)
          CapabilityPill("Bass Boost", capabilities.isBassBoostAvailable)
          CapabilityPill("Virtualizador", capabilities.isVirtualizerAvailable)
        }
      }
    }

    // 2. The deck: preamp + 15 band faders, spectrum strip, buttons and knobs
    item {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(DeckPanel)
          .padding(vertical = 14.dp)
          .testTag("equalizer_15_bands_card")
      ) {
        Row(modifier = Modifier.fillMaxWidth()) {
          // Preamp stays in place while the bands scroll
          Box(modifier = Modifier.padding(start = 10.dp, end = 4.dp)) {
            DeckFader(
              value = (preampDb / PREAMP_RANGE_DB).coerceIn(-1f, 1f),
              enabled = isEffectEnabled,
              name = "Preamp",
              readout = formatDb(preampDb),
              widthDp = 56,
              modifier = Modifier.testTag("fader_preamp"),
              onChange = { v -> onPreampChange(v * PREAMP_RANGE_DB) }
            )
          }

          val scrollState = rememberScrollState()
          Row(
            modifier = Modifier
              .weight(1f)
              .horizontalScroll(scrollState)
              .padding(end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            AudioProfile.BAND_FREQUENCIES.forEachIndexed { index, freqLabel ->
              val levelMb = activeProfile.bandLevelsMb.getOrElse(index) { 0.toShort() }
              DeckFader(
                value = (levelMb / BAND_RANGE_MB).coerceIn(-1f, 1f),
                enabled = isEffectEnabled,
                name = freqLabel.replace(" Hz", "").replace(" kHz", "k").replace(" ", ""),
                readout = formatDb(levelMb / 100f),
                widthDp = 46,
                modifier = Modifier.testTag("vertical_fader_band_$index"),
                onChange = { v -> onUpdateBandLevel(index, (v * BAND_RANGE_MB).roundToInt().toShort()) }
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Spectrum strip with the response curve on top
        Box(
          modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF12161A))
            .testTag("audio_spectrum_graph_card")
        ) {
          SpectrumResponseCanvasWithVisualizer(
            bandLevelsMb = activeProfile.bandLevelsMb,
            spectrumBars = spectrumBars,
            isPlaying = isPlaying,
            curveColor = DeckBoost,
            cutColor = DeckCut,
            zeroColor = DeckMuted.copy(alpha = 0.6f),
            gridColor = DeckGuide.copy(alpha = 0.6f),
            nodeBackground = Color(0xFF12161A),
            modifier = Modifier
              .fillMaxWidth()
              .height(96.dp)
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // EQ on/off, preset picker, save, reset
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          DeckPill(
            text = "EQ",
            highlighted = isEffectEnabled,
            onClick = { onToggleEffectsEnabled(!isEffectEnabled) },
            modifier = Modifier.testTag("btn_eq_toggle")
          )
          Box(modifier = Modifier.weight(1f)) {
            DeckPill(
              text = activeProfile.name,
              icon = Icons.Default.Headphones,
              onClick = { showPresetMenu = true },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_preset_picker")
            )
            DropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
              allProfiles.forEach { profile ->
                DropdownMenuItem(
                  text = {
                    Text(
                      profile.name,
                      color = if (profile.id == activeProfile.id) colors.primary else colors.onSurface
                    )
                  },
                  leadingIcon = if (profile.isCustom) {
                    { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp)) }
                  } else null,
                  onClick = {
                    onSelectProfile(profile)
                    showPresetMenu = false
                  },
                  modifier = Modifier.testTag("profile_chip_${profile.id}")
                )
              }
            }
          }
          DeckPill(
            text = "Salvar",
            icon = Icons.Default.Save,
            onClick = {
              newProfileName = "Meu Perfil"
              showSaveProfileDialog = true
            },
            modifier = Modifier.testTag("btn_save_15b_profile")
          )
          DeckPill(
            text = "Zerar",
            icon = Icons.Default.RestartAlt,
            onClick = onResetAllBandsToZero,
            modifier = Modifier.testTag("btn_reset_eq_zero")
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bass Boost and Virtualizer knobs
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          DeckKnob(
            label = "Graves",
            fraction = activeProfile.bassBoostStrength / 1000f,
            enabled = isEffectEnabled && capabilities.isBassBoostAvailable,
            accent = DeckBoost,
            testTag = "knob_bass",
            onChange = { f -> onUpdateBassBoost((f * 1000f).roundToInt().toShort()) }
          )
          DeckKnob(
            label = "Palco",
            fraction = activeProfile.virtualizerStrength / 1000f,
            enabled = isEffectEnabled && capabilities.isVirtualizerAvailable,
            accent = DeckCut,
            testTag = "knob_virtualizer",
            onChange = { f -> onUpdateVirtualizer((f * 1000f).roundToInt().toShort()) }
          )
        }
      }
    }

    item {
      Text(
        text = activeProfile.description,
        fontSize = 15.sp,
        color = colors.onSurfaceVariant,
        lineHeight = 20.sp
      )
    }

    // 3. Apps playing now (read from dumpsys audio with the DUMP permission)
    item {
      Column {
        TarkasSectionTitle("Apps tocando agora")
        Spacer(modifier = Modifier.height(10.dp))
        ActiveAppsList(activeApps)
      }
    }

    // 4. System audio level meter (measures other apps' output; does not equalize it)
    item {
      Column {
        TarkasSectionTitle("Medidor de nível")
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = colors.surfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("system_audio_capture_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Áudio do sistema (experimental)",
                fontSize = 17.sp,
                color = colors.onSurface
              )
              Text(
                text = if (systemAudioStatus.isCapturing) "Ativo" else "Inativo",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (systemAudioStatus.isCapturing) colors.primary else colors.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Mede o volume do áudio tocado por outros apps, usando a captura de áudio do Android (10 ou mais). " +
                "Apps que bloqueiam a captura não aparecem. Não aplica equalização neles e não identifica a música.",
              fontSize = 15.sp,
              color = colors.onSurfaceVariant,
              lineHeight = 20.sp
            )

            if (systemAudioStatus.isCapturing) {
              Spacer(modifier = Modifier.height(10.dp))
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(8.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(colors.outline.copy(alpha = 0.4f))
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth(systemAudioStatus.currentRmsEnergy.coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.primary)
                )
              }
            }

            if (systemAudioStatus.errorMessage != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = systemAudioStatus.errorMessage,
                fontSize = 15.sp,
                color = colors.error
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
              onClick = onRequestSystemAudioCapture,
              enabled = systemAudioStatus.isMediaProjectionSupported,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("btn_request_system_audio_capture"),
              colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = if (systemAudioStatus.isCapturing) "Reiniciar captura" else "Ativar medidor",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
              )
            }
          }
        }
      }
    }

    // 5. Honest limits of Android audio
    item {
      Text(
        text = capabilities.systemDisclaimer,
        fontSize = 13.sp,
        color = colors.onSurfaceVariant,
        lineHeight = 18.sp
      )
    }
  }

  // Save Custom Profile Dialog
  if (showSaveProfileDialog) {
    AlertDialog(
      onDismissRequest = { showSaveProfileDialog = false },
      title = { Text("Salvar perfil", fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text("Dê um nome a esta curva de equalização:", fontSize = 15.sp)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = newProfileName,
            onValueChange = { newProfileName = it },
            singleLine = true,
            label = { Text("Nome do perfil") },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newProfileName.isNotBlank()) {
              onSaveCustomProfile(newProfileName.trim())
              showSaveProfileDialog = false
            }
          }
        ) {
          Text("Salvar")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSaveProfileDialog = false }) { Text("Cancelar") }
      }
    )
  }
}

private fun formatDb(db: Float): String {
  val rounded = (db * 10f).roundToInt() / 10f
  return if (rounded > 0f) "+${"%.1f".format(rounded)}" else "%.1f".format(rounded)
}

@Composable
private fun ActiveAppsList(state: ActiveAppsState) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = Modifier.testTag("active_apps_list")) {
    when (state) {
      is ActiveAppsState.PermissionMissing -> {
        Text(
          text = "Para ver quais apps estão tocando, libere a permissão DUMP uma vez. " +
            "O Android só a concede por computador, com este comando:",
          fontSize = 15.sp,
          lineHeight = 20.sp,
          color = colors.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "adb shell pm grant com.aistudio.tarkas.app android.permission.DUMP",
          fontSize = 13.sp,
          lineHeight = 18.sp,
          color = colors.onSurface,
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceVariant)
            .padding(12.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "A permissão só deixa o app ler o estado do áudio. Ela não captura nem muda o som de outros apps.",
          fontSize = 13.sp,
          lineHeight = 18.sp,
          color = colors.onSurfaceVariant
        )
      }

      is ActiveAppsState.Failed -> {
        Text(text = state.message, fontSize = 15.sp, color = colors.error)
      }

      is ActiveAppsState.Ready -> {
        if (state.apps.isEmpty()) {
          Text(
            text = "Nenhum outro app está tocando agora.",
            fontSize = 15.sp,
            color = colors.onSurfaceVariant
          )
        } else {
          HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))
          state.apps.forEach { app ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(colors.primary)
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(app.label, fontSize = 17.sp, color = colors.onSurface)
                Text(
                  text = usageLabel(app.usage),
                  fontSize = 15.sp,
                  color = colors.onSurfaceVariant
                )
              }
            }
            HorizontalDivider(color = colors.outline.copy(alpha = 0.5f))
          }
        }
      }
    }
  }
}

private fun usageLabel(usage: String?): String = when (usage) {
  "USAGE_MEDIA" -> "Música ou vídeo"
  "USAGE_GAME" -> "Jogo"
  "USAGE_VOICE_COMMUNICATION" -> "Chamada de voz"
  "USAGE_ASSISTANT" -> "Assistente"
  "USAGE_NOTIFICATION", "USAGE_ASSISTANCE_SONIFICATION" -> "Som do sistema"
  null -> "Tocando agora"
  else -> "Tocando agora"
}

@Composable
private fun CapabilityPill(label: String, available: Boolean) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (available) colors.primary.copy(alpha = 0.14f) else colors.outline.copy(alpha = 0.3f)
  ) {
    Text(
      text = if (available) label else "$label indisponível",
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      color = if (available) colors.primary else colors.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
    )
  }
}

/** A rounded dark button of the equalizer deck. */
@Composable
private fun DeckPill(
  text: String,
  modifier: Modifier = Modifier,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  highlighted: Boolean = false,
  onClick: () -> Unit
) {
  Row(
    modifier = modifier
      .heightIn(min = 44.dp)
      .clip(RoundedCornerShape(22.dp))
      .background(DeckButton)
      .then(if (highlighted) Modifier.border(1.5.dp, DeckBoost, RoundedCornerShape(22.dp)) else Modifier)
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = DeckText, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
    }
    Text(
      text = text,
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      color = if (highlighted) DeckBoost else DeckText,
      maxLines = 1
    )
  }
}

/**
 * A tall fader: a thin rail, a glowing bar from the 0 dB line to the handle, a wide rounded
 * handle, and the name and dB readout underneath. [value] runs from -1 (bottom) to +1 (top).
 */
@Composable
private fun DeckFader(
  value: Float,
  enabled: Boolean,
  name: String,
  readout: String,
  widthDp: Int,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  var heightPx by remember { mutableStateOf(1f) }
  val accent = if (value >= 0f) DeckBoost else DeckCut

  Column(
    modifier = modifier.width(widthDp.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier
        .width(widthDp.dp)
        .height(210.dp)
        .onSizeChanged { heightPx = it.height.toFloat() }
        .semantics { contentDescription = "$name $readout dB" }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          detectTapGestures { offset ->
            onChange((1f - 2f * (offset.y / heightPx)).coerceIn(-1f, 1f))
          }
        }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          detectVerticalDragGestures { change, _ ->
            change.consume()
            onChange((1f - 2f * (change.position.y / heightPx)).coerceIn(-1f, 1f))
          }
        }
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val thumbH = 26.dp.toPx()
        val thumbW = (size.width - 6.dp.toPx()).coerceAtMost(40.dp.toPx())
        // The handle's centre travels between half a handle from the top and from the bottom.
        val travelTop = thumbH / 2f
        val travelBottom = size.height - thumbH / 2f
        val centerY = (travelTop + travelBottom) / 2f
        val handleY = travelBottom - (value + 1f) / 2f * (travelBottom - travelTop)

        // Dotted guides at the top and bottom of the travel
        val dots = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
        drawLine(DeckGuide, Offset(2f, travelTop), Offset(size.width - 2f, travelTop), strokeWidth = 2f, pathEffect = dots)
        drawLine(DeckGuide, Offset(2f, travelBottom), Offset(size.width - 2f, travelBottom), strokeWidth = 2f, pathEffect = dots)

        // Rail
        drawLine(DeckGuide, Offset(cx, travelTop), Offset(cx, travelBottom), strokeWidth = 3f, cap = StrokeCap.Round)

        // Glowing bar from the 0 dB line to the handle
        if (enabled && kotlin.math.abs(handleY - centerY) > 1f) {
          val top = minOf(handleY, centerY)
          val bottom = maxOf(handleY, centerY)
          drawLine(accent.copy(alpha = 0.25f), Offset(cx, top), Offset(cx, bottom), strokeWidth = 12f, cap = StrokeCap.Round)
          drawLine(accent, Offset(cx, top), Offset(cx, bottom), strokeWidth = 5f, cap = StrokeCap.Round)
        }

        // Handle
        val left = cx - thumbW / 2f
        drawRoundRect(
          color = if (enabled) DeckThumb else DeckThumb.copy(alpha = 0.5f),
          topLeft = Offset(left, handleY - thumbH / 2f),
          size = Size(thumbW, thumbH),
          cornerRadius = CornerRadius(10.dp.toPx())
        )
        drawRoundRect(
          color = DeckGuide,
          topLeft = Offset(left, handleY - thumbH / 2f),
          size = Size(thumbW, thumbH),
          cornerRadius = CornerRadius(10.dp.toPx()),
          style = Stroke(width = 1.5f)
        )
        drawLine(
          if (enabled) DeckText.copy(alpha = 0.8f) else DeckMuted,
          Offset(cx - 9.dp.toPx(), handleY),
          Offset(cx + 9.dp.toPx(), handleY),
          strokeWidth = 2.5f,
          cap = StrokeCap.Round
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = name,
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      color = DeckText,
      maxLines = 1,
      textAlign = TextAlign.Center
    )
    Text(
      text = readout,
      fontSize = 11.sp,
      color = if (value == 0f) DeckMuted else accent,
      maxLines = 1,
      textAlign = TextAlign.Center
    )
  }
}

/**
 * A round knob: drag around it to turn. The arc shows the value between 7 o'clock and 5 o'clock.
 */
@Composable
private fun DeckKnob(
  label: String,
  fraction: Float,
  enabled: Boolean,
  accent: Color,
  testTag: String,
  onChange: (Float) -> Unit
) {
  val startAngle = 135f
  val sweep = 270f

  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(horizontalAlignment = Alignment.End) {
      Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DeckText)
      Text("${(fraction * 100).roundToInt()}%", fontSize = 13.sp, color = if (enabled) accent else DeckMuted)
    }
    Spacer(modifier = Modifier.width(10.dp))
    Box(
      modifier = Modifier
        .size(96.dp)
        .semantics { contentDescription = "$label ${(fraction * 100).roundToInt()}%" }
        .testTag(testTag)
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          fun fractionAt(p: Offset): Float {
            val dx = p.x - size.width / 2f
            val dy = p.y - size.height / 2f
            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (deg < 0f) deg += 360f
            // Angle measured from the start of the arc; the gap at the bottom snaps to the near end.
            var rel = deg - startAngle
            if (rel < 0f) rel += 360f
            val f = when {
              rel <= sweep -> rel / sweep
              rel - sweep < (360f - sweep) / 2f -> 1f
              else -> 0f
            }
            return f.coerceIn(0f, 1f)
          }
          detectDragGestures(
            onDragStart = { onChange(fractionAt(it)) },
            onDrag = { change, _ ->
              change.consume()
              onChange(fractionAt(change.position))
            }
          )
        }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          detectTapGestures { offset ->
            val dx = offset.x - size.width / 2f
            val dy = offset.y - size.height / 2f
            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            if (deg < 0f) deg += 360f
            var rel = deg - startAngle
            if (rel < 0f) rel += 360f
            if (rel <= sweep) onChange((rel / sweep).coerceIn(0f, 1f))
          }
        }
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 6.dp.toPx()
        val arcInset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        // Rail and value arc
        drawArc(
          color = DeckGuide,
          startAngle = startAngle,
          sweepAngle = sweep,
          useCenter = false,
          topLeft = Offset(arcInset, arcInset),
          size = arcSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        if (enabled && fraction > 0f) {
          drawArc(
            color = accent,
            startAngle = startAngle,
            sweepAngle = sweep * fraction,
            useCenter = false,
            topLeft = Offset(arcInset, arcInset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
          )
        }
        // Dial body and its pointer
        val bodyRadius = size.minDimension / 2f - stroke * 1.9f
        drawCircle(
          brush = Brush.verticalGradient(listOf(Color(0xFF2A3036), Color(0xFF14181C))),
          radius = bodyRadius,
          center = center
        )
        val angle = Math.toRadians((startAngle + sweep * fraction).toDouble())
        val inner = bodyRadius * 0.45f
        val outer = bodyRadius * 0.85f
        drawLine(
          color = if (enabled) DeckText else DeckMuted,
          start = Offset(center.x + (inner * Math.cos(angle)).toFloat(), center.y + (inner * Math.sin(angle)).toFloat()),
          end = Offset(center.x + (outer * Math.cos(angle)).toFloat(), center.y + (outer * Math.sin(angle)).toFloat()),
          strokeWidth = 3.dp.toPx(),
          cap = StrokeCap.Round
        )
      }
    }
  }
}

/**
 * Spectrum canvas:
 * - background: real FFT bars (flat while nothing plays)
 * - foreground: the 15-band EQ response curve
 */
@Composable
fun SpectrumResponseCanvasWithVisualizer(
  bandLevelsMb: List<Short>,
  spectrumBars: List<Float>,
  isPlaying: Boolean,
  curveColor: Color,
  cutColor: Color,
  zeroColor: Color,
  gridColor: Color,
  nodeBackground: Color,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val width = size.width
    val height = size.height
    val paddingH = 12f
    val paddingV = 12f
    val plotW = width - paddingH * 2
    val plotH = height - paddingV * 2
    val centerY = paddingV + plotH / 2f

    // Grid: top, bottom, 0 dB and vertical guides
    drawLine(color = gridColor, start = Offset(paddingH, paddingV), end = Offset(width - paddingH, paddingV), strokeWidth = 1f)
    drawLine(color = zeroColor, start = Offset(paddingH, centerY), end = Offset(width - paddingH, centerY), strokeWidth = 1.5f)
    drawLine(color = gridColor, start = Offset(paddingH, height - paddingV), end = Offset(width - paddingH, height - paddingV), strokeWidth = 1f)
    listOf(0.12f, 0.28f, 0.50f, 0.72f, 0.90f).forEach { frac ->
      val gx = paddingH + plotW * frac
      drawLine(color = gridColor, start = Offset(gx, paddingV), end = Offset(gx, height - paddingV), strokeWidth = 1f)
    }

    // Background FFT bars
    val barCount = 15
    val barSpacing = plotW / barCount
    val barWidth = barSpacing * 0.7f
    for (i in 0 until barCount) {
      val rawEnergy = spectrumBars.getOrElse(i) { 0.05f }
      val energy = if (isPlaying) rawEnergy else 0.03f
      val barH = (energy * (plotH * 0.9f)).coerceAtLeast(3f)
      val barX = paddingH + i * barSpacing + (barSpacing - barWidth) / 2f
      val barY = (height - paddingV) - barH
      drawRoundRect(
        brush = Brush.verticalGradient(
          colors = listOf(curveColor.copy(alpha = 0.5f), curveColor.copy(alpha = 0.12f)),
          startY = barY,
          endY = height - paddingV
        ),
        topLeft = Offset(barX, barY),
        size = Size(barWidth, barH),
        cornerRadius = CornerRadius(3f, 3f)
      )
    }

    // Foreground EQ response curve
    val pointCount = bandLevelsMb.size.coerceAtLeast(1)
    val points = mutableListOf<Offset>()
    for (i in 0 until pointCount) {
      val x = paddingH + (plotW / (pointCount - 1).coerceAtLeast(1)) * i
      val levelMb = bandLevelsMb.getOrElse(i) { 0.toShort() }.toFloat()
      val fraction = (levelMb / 1500f).coerceIn(-1f, 1f)
      val y = centerY - (fraction * (plotH / 2f) * 0.9f)
      points.add(Offset(x, y))
    }

    if (points.isNotEmpty()) {
      val path = Path()
      path.moveTo(points[0].x, points[0].y)
      for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val midX = p0.x + (p1.x - p0.x) / 2f
        path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
      }

      val fillPath = Path()
      fillPath.addPath(path)
      fillPath.lineTo(points.last().x, centerY)
      fillPath.lineTo(points.first().x, centerY)
      fillPath.close()
      drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
          colors = listOf(curveColor.copy(alpha = 0.28f), Color.Transparent),
          startY = paddingV,
          endY = centerY + 30f
        )
      )

      drawPath(path = path, color = curveColor, style = Stroke(width = 3.2f, cap = StrokeCap.Round))

      points.forEachIndexed { idx, pt ->
        val level = bandLevelsMb.getOrElse(idx) { 0.toShort() }
        val nodeColor = when {
          level > 0 -> curveColor
          level < 0 -> cutColor
          else -> zeroColor
        }
        drawCircle(color = nodeBackground, radius = 5.5f, center = pt)
        drawCircle(color = nodeColor, radius = 3.5f, center = pt)
      }
    }
  }
}
