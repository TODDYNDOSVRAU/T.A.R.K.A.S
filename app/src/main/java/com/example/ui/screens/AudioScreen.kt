package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioCapabilities
import com.example.data.model.AudioProfile
import com.example.data.systemaudio.SystemAudioStatus
import com.example.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

@Composable
fun AudioScreen(
  capabilities: AudioCapabilities,
  activeProfile: AudioProfile,
  allProfiles: List<AudioProfile>,
  isEffectEnabled: Boolean,
  systemAudioStatus: SystemAudioStatus,
  isPlaying: Boolean = false,
  spectrumBars: List<Float> = emptyList(),
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

  val colors = MaterialTheme.colorScheme
  val boostColor = colors.primary
  val cutColor = colors.secondary
  val zeroColor = colors.onSurfaceVariant.copy(alpha = 0.55f)
  val gridColor = colors.outline.copy(alpha = 0.45f)

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(28.dp)
  ) {

    // 1. Header: output route + master DSP switch
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
              fontFamily = SpaceGrotesk,
              fontWeight = FontWeight.Bold,
              fontSize = 22.sp,
              color = colors.onBackground
            )
            Text(
              text = "${capabilities.activeRouteName} • ${String.format("%.1f", capabilities.sampleRateHz / 1000f)} kHz",
              fontSize = 12.sp,
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

    // 2. Spectrum and EQ response curve
    item {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("audio_spectrum_graph_card")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Resposta em frequência",
              fontWeight = FontWeight.SemiBold,
              fontSize = 14.sp,
              color = colors.onSurface
            )
            val flat = activeProfile.bandLevelsMb.all { it == 0.toShort() }
            Text(
              text = if (flat) "Linear (0 dB)" else "Curva modificada",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (flat) colors.onSurfaceVariant else colors.primary
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          SpectrumResponseCanvasWithVisualizer(
            bandLevelsMb = activeProfile.bandLevelsMb,
            spectrumBars = spectrumBars,
            isPlaying = isPlaying,
            curveColor = boostColor,
            cutColor = cutColor,
            zeroColor = zeroColor,
            gridColor = gridColor,
            nodeBackground = colors.surfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .height(150.dp)
          )

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            listOf("25 Hz", "250 Hz", "1 kHz", "4 kHz", "16 kHz").forEach {
              Text(it, fontSize = 10.sp, color = colors.onSurfaceVariant)
            }
          }
          if (!isPlaying) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "O espectro aparece quando uma faixa estiver tocando.",
              fontSize = 11.sp,
              color = colors.onSurfaceVariant
            )
          }
        }
      }
    }

    // 3. Profiles
    item {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Perfis",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = colors.onBackground
          )
          TextButton(
            onClick = onResetAllBandsToZero,
            modifier = Modifier.testTag("btn_reset_eq_zero")
          ) {
            Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Zerar (0 dB)", fontSize = 13.sp)
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(allProfiles) { profile ->
            val isSelected = activeProfile.id == profile.id
            FilterChip(
              selected = isSelected,
              onClick = { onSelectProfile(profile) },
              label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (profile.isCustom) {
                    Icon(
                      imageVector = Icons.Default.Bookmark,
                      contentDescription = null,
                      modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                  }
                  Text(
                    profile.name,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                  )
                }
              },
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = isSelected,
                borderColor = Color.Transparent,
                selectedBorderColor = Color.Transparent
              ),
              colors = FilterChipDefaults.filterChipColors(
                containerColor = colors.surfaceVariant,
                labelColor = colors.onSurface,
                selectedContainerColor = colors.primary,
                selectedLabelColor = colors.onPrimary
              ),
              modifier = Modifier.heightIn(min = 40.dp).testTag("profile_chip_${profile.id}")
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = activeProfile.description,
          fontSize = 12.sp,
          color = colors.onSurfaceVariant,
          lineHeight = 16.sp
        )
      }
    }

    // 4. 15 vertical band faders (horizontal drag scrolls, vertical drag adjusts the nearest band)
    item {
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("equalizer_15_bands_card")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "15 bandas • 25 Hz a 16 kHz",
              fontWeight = FontWeight.SemiBold,
              fontSize = 14.sp,
              color = colors.onSurface
            )
            TextButton(
              onClick = {
                newProfileName = "Meu Perfil 15B"
                showSaveProfileDialog = true
              },
              modifier = Modifier.testTag("btn_save_15b_profile")
            ) {
              Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Salvar", fontSize = 13.sp)
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Only a drag that starts on a fader changes that band. Everywhere else the page scrolls
          // vertically and the deck scrolls horizontally.
          val scrollState = rememberScrollState()

          Box(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              AudioProfile.BAND_FREQUENCIES.forEachIndexed { index, freqLabel ->
                val levelMb = activeProfile.bandLevelsMb.getOrElse(index) { 0.toShort() }
                VerticalBandFader(
                  bandIndex = index,
                  frequencyLabel = freqLabel,
                  gainMb = levelMb,
                  gainDb = levelMb.toFloat() / 100f,
                  enabled = isEffectEnabled,
                  boostColor = boostColor,
                  cutColor = cutColor,
                  zeroColor = zeroColor,
                  trackColor = colors.surfaceVariant,
                  borderColor = colors.outline,
                  labelColor = colors.onSurfaceVariant,
                  onLevelChange = { newLevel -> onUpdateBandLevel(index, newLevel) }
                )
              }
            }
          }
        }
      }
    }

    // 5. Dynamics (Bass Boost & Virtualizer)
    item {
      Column {
        Text(
          text = "Dinâmica",
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.Bold,
          fontSize = 22.sp,
          color = colors.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            val bassPct = (activeProfile.bassBoostStrength.toFloat() / 10f).toInt()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Bass Boost", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
              Text("$bassPct%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
            Slider(
              value = activeProfile.bassBoostStrength.toFloat(),
              onValueChange = { onUpdateBassBoost(it.toInt().toShort()) },
              valueRange = 0f..1000f,
              enabled = isEffectEnabled && capabilities.isBassBoostAvailable,
              colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
            )

            Spacer(modifier = Modifier.height(4.dp))

            val virtPct = (activeProfile.virtualizerStrength.toFloat() / 10f).toInt()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Virtualizador de palco", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
              Text("$virtPct%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
            Slider(
              value = activeProfile.virtualizerStrength.toFloat(),
              onValueChange = { onUpdateVirtualizer(it.toInt().toShort()) },
              valueRange = 0f..1000f,
              enabled = isEffectEnabled && capabilities.isVirtualizerAvailable,
              colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary)
            )
          }
        }
      }
    }

    // 6. System audio level meter (measures other apps' output; does not equalize it)
    item {
      Column {
        Text(
          text = "Áudio do sistema",
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.Bold,
          fontSize = 22.sp,
          color = colors.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
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
                text = "Medidor de nível (experimental)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colors.onSurface
              )
              Text(
                text = if (systemAudioStatus.isCapturing) "Ativo" else "Inativo",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (systemAudioStatus.isCapturing) colors.primary else colors.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Mede o volume do áudio tocado por outros apps, usando a captura de áudio do Android (10 ou mais). " +
                "Não aplica equalização neles e não identifica a música.",
              fontSize = 12.sp,
              color = colors.onSurfaceVariant,
              lineHeight = 16.sp
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
                fontSize = 12.sp,
                color = colors.error
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
              onClick = onRequestSystemAudioCapture,
              enabled = systemAudioStatus.isMediaProjectionSupported,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_request_system_audio_capture"),
              colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = if (systemAudioStatus.isCapturing) "Reiniciar captura" else "Ativar medidor",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
            }
          }
        }
      }
    }

    // 7. Honest limits of Android audio
    item {
      Text(
        text = capabilities.systemDisclaimer,
        fontSize = 11.sp,
        color = colors.onSurfaceVariant,
        lineHeight = 15.sp
      )
    }
  }

  // Save Custom Profile Dialog
  if (showSaveProfileDialog) {
    AlertDialog(
      onDismissRequest = { showSaveProfileDialog = false },
      title = { Text("Salvar perfil", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text("Dê um nome a esta curva de equalização:", fontSize = 13.sp)
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

@Composable
private fun CapabilityPill(label: String, available: Boolean) {
  val colors = MaterialTheme.colorScheme
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (available) colors.primary.copy(alpha = 0.14f) else colors.outline.copy(alpha = 0.3f)
  ) {
    Text(
      text = if (available) label else "$label indisponível",
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      color = if (available) colors.primary else colors.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}

/**
 * Vertical fader for a single band: dB readout, 0 dB notch and frequency label.
 * Colours come from the theme, so the fader follows light and dark mode.
 */
@Composable
fun VerticalBandFader(
  bandIndex: Int,
  frequencyLabel: String,
  gainMb: Short,
  gainDb: Float,
  enabled: Boolean,
  boostColor: Color,
  cutColor: Color,
  zeroColor: Color,
  trackColor: Color,
  borderColor: Color,
  labelColor: Color,
  onLevelChange: (Short) -> Unit
) {
  var trackHeightPx by remember { mutableStateOf(1f) }

  Column(
    modifier = Modifier
      .width(35.dp)
      .testTag("vertical_fader_band_$bandIndex"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      text = "${if (gainDb > 0) "+" else ""}${String.format("%.1f", gainDb)}",
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = when {
        gainDb > 0f -> boostColor
        gainDb < 0f -> cutColor
        else -> labelColor
      },
      maxLines = 1
    )

    Spacer(modifier = Modifier.height(4.dp))

    Box(
      modifier = Modifier
        .width(26.dp)
        .height(128.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(trackColor)
        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
        .onSizeChanged { trackHeightPx = it.height.toFloat() }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          detectTapGestures { offset ->
            val fraction = 1f - (offset.y / trackHeightPx).coerceIn(0f, 1f)
            val newMb = ((fraction * 3000f) - 1500f).toInt().toShort()
            onLevelChange(newMb)
          }
        }
        .pointerInput(enabled) {
          if (!enabled) return@pointerInput
          detectVerticalDragGestures { change, _ ->
            change.consume()
            val fraction = 1f - (change.position.y / trackHeightPx).coerceIn(0f, 1f)
            val newMb = ((fraction * 3000f) - 1500f).toInt().toShort()
            onLevelChange(newMb)
          }
        },
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        // 0 dB guide line
        drawLine(
          color = zeroColor,
          start = Offset(2f, centerY),
          end = Offset(w - 2f, centerY),
          strokeWidth = 1.5f
        )

        // gainMb in [-1500, +1500] -> y in [h, 0]
        val frac = ((gainMb.toFloat() + 1500f) / 3000f).coerceIn(0f, 1f)
        val handleY = h - (frac * h)
        val accent = when {
          gainMb > 0 -> boostColor
          gainMb < 0 -> cutColor
          else -> zeroColor
        }

        if (gainMb != 0.toShort()) {
          val top = if (handleY < centerY) handleY else centerY
          val bottom = if (handleY > centerY) handleY else centerY
          drawRoundRect(
            color = accent.copy(alpha = 0.45f),
            topLeft = Offset(w * 0.32f, top),
            size = Size(w * 0.36f, bottom - top),
            cornerRadius = CornerRadius(3f, 3f)
          )
        }

        // Thumb
        drawRoundRect(
          color = accent,
          topLeft = Offset(2f, handleY - 8f),
          size = Size(w - 4f, 16f),
          cornerRadius = CornerRadius(8f, 8f)
        )
      }
    }

    Spacer(modifier = Modifier.height(5.dp))

    Text(
      text = frequencyLabel.replace(" ", ""),
      fontSize = 9.sp,
      fontWeight = FontWeight.SemiBold,
      color = labelColor,
      textAlign = TextAlign.Center,
      maxLines = 1,
      modifier = Modifier.fillMaxWidth()
    )
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
