package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Curiosity
import com.example.data.model.Track
import com.example.ui.components.CuriosityDetailDialog
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.AppleMusicPink
import com.example.ui.theme.SpaceGrotesk

@Composable
fun MusicScreen(
  currentTrack: Track?,
  tracks: List<Track>,
  isPlaying: Boolean,
  currentPositionMs: Long,
  durationMs: Long,
  waveformEnergy: Float,
  activeCuriosity: Curiosity?,
  isSearchingAudius: Boolean,
  onPlayTrack: (Track) -> Unit,
  onTogglePlayPause: () -> Unit,
  onNextTrack: () -> Unit,
  onPreviousTrack: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onAddLocalTrack: (String, String, String, Long) -> Unit,
  onSearchAudius: (String) -> Unit,
  onLoadAudiusTrending: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var showCuriosityDialog by remember { mutableStateOf(false) }

  // SAF File Picker for local audio
  val audioPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri?.let {
      var displayName = "Áudio Local"
      try {
        context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIndex != -1 && cursor.moveToFirst()) {
            displayName = cursor.getString(nameIndex)
          }
        }
      } catch (_: Exception) {}
      onAddLocalTrack(displayName, "Biblioteca Local", it.toString(), 180000L)
    }
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp),
    contentPadding = PaddingValues(vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {

    // 1. Audius Search Bar (Search and Play Streaming API)
    item {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Explorar Audius",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
          )

          OutlinedButton(
            onClick = onLoadAudiusTrending,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.testTag("btn_audius_trending")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.TrendingUp,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint = AppleMusicPink
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Trending", fontSize = 11.sp, color = AppleMusicPink)
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Buscar músicas e artistas no Audius...", fontSize = 13.sp) },
          leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          },
          trailingIcon = {
            if (isSearchingAudius) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (searchQuery.isNotBlank()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Limpar")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("audius_search_input")
        )

        AnimatedVisibility(visible = searchQuery.isNotBlank()) {
          Button(
            onClick = { onSearchAudius(searchQuery.trim()) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 6.dp)
              .testTag("btn_audius_search_submit")
          ) {
            Text("Pesquisar no Audius", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          }
        }
      }
    }

    // 2. Apple Music Hero Player Card
    item {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(26.dp))
          .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
          .testTag("music_full_player_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {

          // Ambient Artwork
          val trackColor = Color(currentTrack?.colorHex ?: 0xFFFA243C)
          Box(
            modifier = Modifier
              .size(150.dp)
              .clip(RoundedCornerShape(22.dp))
              .background(
                Brush.radialGradient(
                  colors = listOf(
                    trackColor,
                    trackColor.copy(alpha = 0.8f),
                    Color(0xFF0F0F12)
                  )
                )
              )
              .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.MusicNote,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(64.dp)
            )
          }

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = currentTrack?.title ?: "Selecione uma faixa",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          Text(
            text = "${currentTrack?.artist ?: ""} • ${currentTrack?.album ?: ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Real-time audio waveform visualizer
          WaveformVisualizer(
            energy = if (isPlaying) waveformEnergy else 0.1f,
            barCount = 28,
            modifier = Modifier
              .fillMaxWidth()
              .height(34.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
              .padding(horizontal = 8.dp, vertical = 4.dp),
            barColor = MaterialTheme.colorScheme.primary
          )

          Spacer(modifier = Modifier.height(6.dp))

          // Scrubber
          val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
          Slider(
            value = progress,
            onValueChange = { frac ->
              val target = (frac * durationMs).toLong()
              onSeekTo(target)
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("music_progress_slider"),
            colors = SliderDefaults.colors(
              thumbColor = MaterialTheme.colorScheme.primary,
              activeTrackColor = MaterialTheme.colorScheme.primary,
              inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            val currSec = currentPositionMs / 1000
            val durSec = durationMs / 1000
            Text(
              text = String.format("%02d:%02d", currSec / 60, currSec % 60),
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = String.format("%02d:%02d", durSec / 60, durSec % 60),
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Transport controls
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = { onSeekTo((currentPositionMs - 5000L).coerceAtLeast(0L)) },
              modifier = Modifier.size(44.dp)
            ) {
              Icon(imageVector = Icons.Default.Replay5, contentDescription = "Voltar 5s", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(
              onClick = onPreviousTrack,
              modifier = Modifier.size(50.dp).testTag("music_btn_previous")
            ) {
              Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Faixa Anterior", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
            }

            Button(
              onClick = onTogglePlayPause,
              modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .testTag("music_btn_play_pause"),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              contentPadding = PaddingValues(0.dp)
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
              )
            }

            IconButton(
              onClick = onNextTrack,
              modifier = Modifier.size(50.dp).testTag("music_btn_next")
            ) {
              Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Próxima Faixa", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
            }

            IconButton(
              onClick = { onSeekTo((currentPositionMs + 5000L).coerceAtMost(durationMs)) },
              modifier = Modifier.size(44.dp)
            ) {
              Icon(imageVector = Icons.Default.Forward5, contentDescription = "Avançar 5s", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          // Curiosity preview button (clicking it opens the dialog)
          if (activeCuriosity != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surface,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showCuriosityDialog = true }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Curiosidade: ${activeCuriosity.shortTrivia.take(50)}...",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
                Text("Ler", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      }
    }

    // 3. Local Audio SAF Picker Action
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Faixas Disponíveis (${tracks.size})",
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = MaterialTheme.colorScheme.onBackground
        )

        OutlinedButton(
          onClick = { audioPickerLauncher.launch("audio/*") },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("music_btn_open_local")
        ) {
          Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Abrir do Aparelho", fontSize = 11.sp)
        }
      }
    }

    // 4. Track List
    items(tracks) { track ->
      val isSelected = currentTrack?.id == track.id
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onPlayTrack(track) }
          .testTag("track_row_${track.id}")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(Color(track.colorHex)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isSelected && isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(22.dp)
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = track.title,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              fontSize = 14.sp,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${track.artist} • ${track.album}",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          Text(
            text = track.formattedDuration(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
          )
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(40.dp))
    }
  }

  // Curiosity Detail Dialog when triggered
  if (showCuriosityDialog && activeCuriosity != null) {
    CuriosityDetailDialog(
      curiosity = activeCuriosity,
      onDismiss = { showCuriosityDialog = false }
    )
  }
}
