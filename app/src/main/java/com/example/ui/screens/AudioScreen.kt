package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioCapabilities
import com.example.data.model.AudioProfile
import com.example.data.systemaudio.SystemAudioStatus
import com.example.ui.theme.AppleMusicCyan
import com.example.ui.theme.AppleMusicPink
import com.example.ui.theme.SpaceGrotesk
import com.example.ui.theme.TarkasSuccess

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

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(top = 10.dp, bottom = 48.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {

    // 1. Real-time Spectrum Response Graph Card with Dynamic Music Background
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(24.dp))
          .testTag("audio_spectrum_graph_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "ESPECTRO DE FREQUÊNCIA (15 BANDAS)",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
              )
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (activeProfile.bandLevelsMb.all { it == 0.toShort() }) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else AppleMusicPink.copy(alpha = 0.15f)
            ) {
              Text(
                text = if (activeProfile.bandLevelsMb.all { it == 0.toShort() }) "LINEAR (0 dB)" else "CURVA MODIFICADA",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeProfile.bandLevelsMb.all { it == 0.toShort() }) MaterialTheme.colorScheme.primary else AppleMusicPink,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Real-time Canvas Graph with dynamic music visualizer in the background
          SpectrumResponseCanvasWithVisualizer(
            bandLevelsMb = activeProfile.bandLevelsMb,
            spectrumBars = spectrumBars,
            isPlaying = isPlaying,
            modifier = Modifier
              .fillMaxWidth()
              .height(160.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFF07070A))
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("25 Hz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
            Text("250 Hz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("1 kHz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("4 kHz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("16 kHz", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBD00FF))
          }
        }
      }
    }

    // 2. Profiles Carousel & Reset (0 dB)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Perfis de Equalização",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
          Text(
            text = "Inicia em 0 dB. Escolha um perfil ou ajuste as bandas.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        OutlinedButton(
          onClick = onResetAllBandsToZero,
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("btn_reset_eq_zero")
        ) {
          Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Zerar (0 dB)", fontSize = 11.sp)
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
      ) {
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
                Text(profile.name, fontSize = 12.sp)
              }
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.testTag("profile_chip_${profile.id}")
          )
        }
      }
    }

    // 3. 15 Vertical Band Faders listed Horizontally (20% smaller, 2D swipe & drag navigation)
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(22.dp))
          .testTag("equalizer_15_bands_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C10)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Equalizer,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "EQUALIZADOR 15 BANDAS (25 Hz — 16 kHz)",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color(0xFF00E5FF)
              )
            }

            OutlinedButton(
              onClick = {
                newProfileName = "Meu Perfil 15B"
                showSaveProfileDialog = true
              },
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
              modifier = Modifier.testTag("btn_save_15b_profile")
            ) {
              Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Salvar", fontSize = 11.sp)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // 2D Gesture Deck: Horizontal drag scrolls between bands; Vertical drag adjusts the nearest band's gain!
          val scrollState = rememberScrollState()
          val coroutineScope = rememberCoroutineScope()

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .pointerInput(isEffectEnabled) {
                if (!isEffectEnabled) return@pointerInput
                detectDragGestures { change, dragAmount ->
                  change.consume()
                  if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) * 1.15f) {
                    coroutineScope.launch {
                      scrollState.scrollBy(-dragAmount.x)
                    }
                  } else {
                    val bandItemWidthPx = 41.dp.toPx() // 35.dp width + 6.dp spacing
                    val absX = change.position.x + scrollState.value.toFloat()
                    val targetBand = (absX / bandItemWidthPx).toInt().coerceIn(0, 14)
                    val currentMb = activeProfile.bandLevelsMb.getOrElse(targetBand) { 0.toShort() }
                    val deltaMb = (-dragAmount.y / 128.dp.toPx() * 3000f).toInt()
                    val newMb = (currentMb + deltaMb).coerceIn(-1500, 1500).toShort()
                    onUpdateBandLevel(targetBand, newMb)
                  }
                }
              }
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              AudioProfile.BAND_FREQUENCIES.forEachIndexed { index, freqLabel ->
                val levelMb = activeProfile.bandLevelsMb.getOrElse(index) { 0.toShort() }
                val dbVal = levelMb.toFloat() / 100f

                VerticalBandFader(
                  bandIndex = index,
                  frequencyLabel = freqLabel,
                  gainMb = levelMb,
                  gainDb = dbVal,
                  enabled = isEffectEnabled,
                  onLevelChange = { newLevel ->
                    onUpdateBandLevel(index, newLevel)
                  }
                )
              }
            }
          }
        }
      }
    }

    // 4. System Audio Extraction (Spotify / YouTube Music)
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp))
          .testTag("system_audio_capture_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Radio,
                contentDescription = null,
                tint = AppleMusicPink,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "EXTRAÇÃO DE ÁUDIO DO SISTEMA",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = AppleMusicPink,
                letterSpacing = 0.5.sp
              )
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (systemAudioStatus.isCapturing) TarkasSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            ) {
              Text(
                text = if (systemAudioStatus.isCapturing) "MONITORANDO" else "INATIVO",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (systemAudioStatus.isCapturing) TarkasSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "Permite monitorar o fluxo de áudio reproduzido por aplicativos externos (como Spotify e YouTube Music) para sincronizar o espectro do equalizador e pesquisar curiosidades da música em reprodução.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
          )

          Spacer(modifier = Modifier.height(12.dp))

          if (systemAudioStatus.detectedTrackTitle != null) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Equalizer,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "Detectado: ${systemAudioStatus.detectedTrackTitle}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "${systemAudioStatus.detectedArtist ?: ""} • ${systemAudioStatus.detectedApp ?: ""}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          Button(
            onClick = onRequestSystemAudioCapture,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("btn_request_system_audio_capture"),
            colors = ButtonDefaults.buttonColors(containerColor = if (systemAudioStatus.isCapturing) TarkasSuccess else MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              imageVector = if (systemAudioStatus.isCapturing) Icons.Default.Tune else Icons.Default.Radio,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (systemAudioStatus.isCapturing) "Conectar Spotify / YouTube Music" else "Ativar Captura do Sistema",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
          }
        }
      }
    }

    // 5. Dynamics (Bass Boost & Virtualizer)
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.Waves, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("PROCESSAMENTO DINÂMICO", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("DSP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.width(6.dp))
              Switch(
                checked = isEffectEnabled,
                onCheckedChange = onToggleEffectsEnabled,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Bass Boost
          val bassPct = (activeProfile.bassBoostStrength.toFloat() / 10f).toInt()
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bass Boost", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("$bassPct%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          }
          Slider(
            value = activeProfile.bassBoostStrength.toFloat(),
            onValueChange = { onUpdateBassBoost(it.toInt().toShort()) },
            valueRange = 0f..1000f,
            enabled = isEffectEnabled
          )

          Spacer(modifier = Modifier.height(8.dp))

          // Virtualizer
          val virtPct = (activeProfile.virtualizerStrength.toFloat() / 10f).toInt()
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Virtualizador de Palco", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text("$virtPct%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          }
          Slider(
            value = activeProfile.virtualizerStrength.toFloat(),
            onValueChange = { onUpdateVirtualizer(it.toInt().toShort()) },
            valueRange = 0f..1000f,
            enabled = isEffectEnabled
          )
        }
      }
    }
  }

  // Save Custom Profile Dialog
  if (showSaveProfileDialog) {
    AlertDialog(
      onDismissRequest = { showSaveProfileDialog = false },
      title = { Text("Salvar Perfil de 15 Bandas", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text("Defina um nome para esta curva de equalização:", fontSize = 13.sp)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = newProfileName,
            onValueChange = { newProfileName = it },
            singleLine = true,
            label = { Text("Nome do Perfil") },
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

/**
 * Vertical Fader for a single band with dB readout, center 0dB notch, and frequency badge.
 */
@Composable
fun VerticalBandFader(
  bandIndex: Int,
  frequencyLabel: String,
  gainMb: Short,
  gainDb: Float,
  enabled: Boolean,
  onLevelChange: (Short) -> Unit
) {
  var trackHeightPx by remember { mutableStateOf(1f) }

  Column(
    modifier = Modifier
      .width(35.dp)
      .testTag("vertical_fader_band_$bandIndex"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top dB Gain readout
    Text(
      text = "${if (gainDb > 0) "+" else ""}${String.format("%.1f", gainDb)}",
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = when {
        gainDb > 0f -> Color(0xFFBD00FF)
        gainDb < 0f -> Color(0xFF00E5FF)
        else -> Color(0xFF00FFA3)
      },
      maxLines = 1
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Vertical Interactive Track (20% smaller: 26.dp width x 128.dp height)
    Box(
      modifier = Modifier
        .width(26.dp)
        .height(128.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(Color(0xFF0F1117))
        .border(1.dp, Color(0xFF222838), RoundedCornerShape(10.dp))
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
          detectDragGestures { change, _ ->
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

        // Center 0 dB guide line (Poweramp vibrant green)
        drawLine(
          color = Color(0xFF00FFA3).copy(alpha = 0.55f),
          start = Offset(2f, centerY),
          end = Offset(w - 2f, centerY),
          strokeWidth = 1.5f
        )

        // Gain position calculation: gainMb in [-1500, +1500] -> y in [h, 0]
        val frac = ((gainMb.toFloat() + 1500f) / 3000f).coerceIn(0f, 1f)
        val handleY = h - (frac * h)

        // Fill bar from centerY to handleY
        if (gainMb != 0.toShort()) {
          val top = if (handleY < centerY) handleY else centerY
          val bottom = if (handleY > centerY) handleY else centerY
          val fillColor = if (gainMb > 0) Color(0xFFBD00FF) else Color(0xFF00E5FF)

          drawRoundRect(
            color = fillColor.copy(alpha = 0.5f),
            topLeft = Offset(w * 0.32f, top),
            size = Size(w * 0.36f, bottom - top),
            cornerRadius = CornerRadius(3f, 3f)
          )
        }

        // Fader thumb handle (Poweramp metallic style)
        val thumbAccent = when {
          gainMb > 0 -> Color(0xFFBD00FF)
          gainMb < 0 -> Color(0xFF00E5FF)
          else -> Color(0xFF00FFA3)
        }

        drawRoundRect(
          color = Color(0xFF161B26),
          topLeft = Offset(2f, handleY - 8f),
          size = Size(w - 4f, 16f),
          cornerRadius = CornerRadius(5f, 5f)
        )
        drawRoundRect(
          color = thumbAccent,
          topLeft = Offset(3f, handleY - 1.5f),
          size = Size(w - 6f, 3f),
          cornerRadius = CornerRadius(1.5f, 1.5f)
        )
      }
    }

    Spacer(modifier = Modifier.height(5.dp))

    // Frequency label badge at bottom
    Surface(
      shape = RoundedCornerShape(6.dp),
      color = Color(0xFF141722),
      modifier = Modifier.fillMaxWidth()
    ) {
      Text(
        text = frequencyLabel.replace(" ", ""),
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFE2E8F0),
        modifier = Modifier.padding(vertical = 2.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        maxLines = 1
      )
    }
  }
}

/**
 * Real-time Spectrum Response Canvas:
 * - Dynamic real-time spectrum visualizer dancing in the background with the playing music
 * - Foreground 15-band EQ response curve connecting all 15 nodes smoothly
 */
@Composable
fun SpectrumResponseCanvasWithVisualizer(
  bandLevelsMb: List<Short>,
  spectrumBars: List<Float>,
  isPlaying: Boolean,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val width = size.width
    val height = size.height
    val paddingH = 16f
    val paddingV = 16f
    val plotW = width - paddingH * 2
    val plotH = height - paddingV * 2
    val centerY = paddingV + plotH / 2f

    // 1. Grid reference lines (+12 dB, 0 dB, -12 dB)
    val gridColor = Color(0xFF161A24)
    drawLine(color = gridColor, start = Offset(paddingH, paddingV), end = Offset(width - paddingH, paddingV), strokeWidth = 1f)
    // Signature Poweramp 0 dB center line
    drawLine(color = Color(0xFF00FFA3).copy(alpha = 0.45f), start = Offset(paddingH, centerY), end = Offset(width - paddingH, centerY), strokeWidth = 1.5f)
    drawLine(color = gridColor, start = Offset(paddingH, height - paddingV), end = Offset(width - paddingH, height - paddingV), strokeWidth = 1f)

    // Vertical frequency grid guidelines
    val verticalGridFractions = listOf(0.12f, 0.28f, 0.50f, 0.72f, 0.90f)
    verticalGridFractions.forEach { frac ->
      val gx = paddingH + plotW * frac
      drawLine(color = gridColor, start = Offset(gx, paddingV), end = Offset(gx, height - paddingV), strokeWidth = 1f)
    }

    // 2. BACKGROUND: Dynamic real-time FFT spectrum bars reacting with the music
    val barCount = 15
    val barSpacing = plotW / barCount
    val barWidth = barSpacing * 0.7f

    for (i in 0 until barCount) {
      val rawEnergy = spectrumBars.getOrElse(i) { 0.05f }
      val animatedEnergy = if (isPlaying) rawEnergy else 0.03f
      val barH = (animatedEnergy * (plotH * 0.9f)).coerceAtLeast(3f)
      val barX = paddingH + i * barSpacing + (barSpacing - barWidth) / 2f
      val barY = (height - paddingV) - barH

      drawRoundRect(
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFFBD00FF).copy(alpha = 0.55f),
            Color(0xFF00E5FF).copy(alpha = 0.35f),
            Color.Transparent
          ),
          startY = barY,
          endY = height - paddingV
        ),
        topLeft = Offset(barX, barY),
        size = Size(barWidth, barH),
        cornerRadius = CornerRadius(3f, 3f)
      )
    }

    // 3. FOREGROUND: 15-band EQ Response Curve (Poweramp Neon Cyan)
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
        val cx1 = p0.x + (p1.x - p0.x) / 2f
        val cy1 = p0.y
        val cx2 = p0.x + (p1.x - p0.x) / 2f
        val cy2 = p1.y
        path.cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
      }

      // Fill below curve to center 0 dB line
      val fillPath = Path()
      fillPath.addPath(path)
      fillPath.lineTo(points.last().x, centerY)
      fillPath.lineTo(points.first().x, centerY)
      fillPath.close()

      drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
          colors = listOf(
            Color(0xFFBD00FF).copy(alpha = 0.30f),
            Color(0xFF00E5FF).copy(alpha = 0.15f),
            Color.Transparent
          ),
          startY = paddingV,
          endY = centerY + 30f
        )
      )

      // Poweramp Neon Cyan Glow Line
      drawPath(
        path = path,
        color = Color(0xFF00E5FF),
        style = Stroke(width = 3.2f, cap = StrokeCap.Round)
      )

      // Frequency band nodes
      points.forEachIndexed { idx, pt ->
        val level = bandLevelsMb.getOrElse(idx) { 0.toShort() }
        val nodeColor = when {
          level > 0 -> Color(0xFFBD00FF)
          level < 0 -> Color(0xFF00E5FF)
          else -> Color(0xFF00FFA3)
        }
        drawCircle(
          color = Color(0xFF08080C),
          radius = 5.5f,
          center = pt
        )
        drawCircle(
          color = nodeColor,
          radius = 3.5f,
          center = pt
        )
      }
    }
  }
}
