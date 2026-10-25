package com.example.ui.screens

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Curiosity
import com.example.data.model.Playlist
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.ui.components.CuriosityDetailDialog
import com.example.ui.components.TarkasSectionTitle
import com.example.ui.components.TrackActionsMenu
import kotlin.math.roundToInt

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
  isShuffle: Boolean,
  repeatMode: RepeatMode,
  favoriteIds: List<String>,
  playlists: List<Playlist>,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleFavorite: (Track) -> Unit,
  onPlayNext: (Track) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (String, Track) -> Unit,
  onCreatePlaylist: (String, Track) -> Unit,
  onOpenQueue: () -> Unit = {},
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
      // Read the real duration; fall back to 3 min only if the file has no metadata.
      var realDurationMs = 0L
      try {
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(context, it)
        realDurationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        retriever.release()
      } catch (_: Exception) {}
      onAddLocalTrack(displayName, "Biblioteca Local", it.toString(), if (realDurationMs > 0) realDurationMs else 180000L)
    }
  }

  // The Now Playing page takes its tint from the artwork, like the Apple Music player sheet.
  val trackColor = Color(currentTrack?.colorHex ?: 0xFF46C9D3)
  val pageTop = lerp(trackColor, Color.Black, 0.5f)
  val pageBottom = lerp(trackColor, Color.Black, 0.82f)
  val onPage = Color.White
  val onPageMuted = Color.White.copy(alpha = 0.72f)
  val trackFill = Color.White.copy(alpha = 0.28f)

  val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
  var volume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val pageHeight = maxHeight

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 40.dp)
    ) {

      // 1. Now Playing page (fills the screen; everything else is reached by scrolling down)
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .height(pageHeight)
            .background(Brush.verticalGradient(listOf(pageTop, pageBottom)))
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .testTag("music_full_player_card"),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Spacer(modifier = Modifier.height(10.dp))
          Box(
            modifier = Modifier
              .width(40.dp)
              .height(5.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.4f))
          )

          Spacer(modifier = Modifier.weight(1f))

          Box(
            modifier = Modifier
              .fillMaxWidth(0.8f)
              .aspectRatio(1f)
              .shadow(24.dp, RoundedCornerShape(10.dp))
              .clip(RoundedCornerShape(10.dp))
              .background(
                Brush.linearGradient(
                  listOf(trackColor, lerp(trackColor, Color.Black, 0.45f))
                )
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.MusicNote,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(72.dp)
            )
          }

          Spacer(modifier = Modifier.weight(1f))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = currentTrack?.title ?: "Selecione uma faixa",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = onPage,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = currentTrack?.artist ?: "Escolha uma música na lista abaixo",
                fontSize = 22.sp,
                color = onPageMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            if (currentTrack != null) {
              val isFav = currentTrack.id in favoriteIds
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
              ) {
                TrackActionsMenu(
                  track = currentTrack,
                  isFavorite = isFav,
                  playlists = playlists,
                  onToggleFavorite = { onToggleFavorite(currentTrack) },
                  onPlayNext = { onPlayNext(currentTrack) },
                  onAddToQueue = { onAddToQueue(currentTrack) },
                  onAddToPlaylist = { id -> onAddToPlaylist(id, currentTrack) },
                  onCreatePlaylist = { name -> onCreatePlaylist(name, currentTrack) },
                  iconTint = onPage,
                  icon = Icons.Default.MoreHoriz
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(18.dp))

          val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
          ScrubBar(
            fraction = progress,
            description = "Progresso da faixa",
            filled = onPage.copy(alpha = 0.85f),
            track = trackFill,
            onChange = { frac -> if (durationMs > 0) onSeekTo((frac * durationMs).toLong()) },
            modifier = Modifier.testTag("music_progress_slider")
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(text = formatClock(currentPositionMs), fontSize = 13.sp, color = onPageMuted)
            Text(text = "-" + formatClock((durationMs - currentPositionMs).coerceAtLeast(0L)), fontSize = 13.sp, color = onPageMuted)
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Transport: rewind, play, fast-forward
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = onPreviousTrack,
              modifier = Modifier.size(72.dp).testTag("music_btn_previous")
            ) {
              Icon(Icons.Default.FastRewind, contentDescription = "Faixa Anterior", modifier = Modifier.size(52.dp), tint = onPage)
            }
            IconButton(
              onClick = onTogglePlayPause,
              modifier = Modifier.size(88.dp).testTag("music_btn_play_pause")
            ) {
              Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                tint = onPage,
                modifier = Modifier.size(76.dp)
              )
            }
            IconButton(
              onClick = onNextTrack,
              modifier = Modifier.size(72.dp).testTag("music_btn_next")
            ) {
              Icon(Icons.Default.FastForward, contentDescription = "Próxima Faixa", modifier = Modifier.size(52.dp), tint = onPage)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Volume
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = onPageMuted, modifier = Modifier.size(22.dp))
            ScrubBar(
              fraction = volume,
              description = "Volume",
              filled = onPage.copy(alpha = 0.85f),
              track = trackFill,
              onChange = { frac ->
                volume = frac
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (frac * maxVolume).roundToInt(), 0)
              },
              modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .testTag("music_volume_slider")
            )
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = onPageMuted, modifier = Modifier.size(22.dp))
          }

          Spacer(modifier = Modifier.height(6.dp))

          // Shuffle, repeat, curiosity and queue (the Apple Music bottom row)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = onToggleShuffle, modifier = Modifier.size(52.dp).testTag("music_btn_shuffle")) {
              Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = if (isShuffle) "Aleatório ligado" else "Aleatório desligado",
                tint = if (isShuffle) onPage else onPageMuted.copy(alpha = 0.6f)
              )
            }
            IconButton(onClick = onCycleRepeat, modifier = Modifier.size(52.dp).testTag("music_btn_repeat")) {
              Icon(
                imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                contentDescription = when (repeatMode) {
                  RepeatMode.OFF -> "Repetir desligado"
                  RepeatMode.ALL -> "Repetir tudo"
                  RepeatMode.ONE -> "Repetir uma faixa"
                },
                tint = if (repeatMode != RepeatMode.OFF) onPage else onPageMuted.copy(alpha = 0.6f)
              )
            }
            IconButton(
              onClick = { showCuriosityDialog = true },
              enabled = activeCuriosity != null,
              modifier = Modifier.size(52.dp).testTag("music_btn_curiosity")
            ) {
              Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = "Curiosidade da faixa",
                tint = if (activeCuriosity != null) onPage else onPageMuted.copy(alpha = 0.3f)
              )
            }
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(52.dp).testTag("music_btn_queue")) {
              Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Fila de reprodução", tint = onPageMuted)
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
        }
      }

      // 2. Audius search (streaming)
      item {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            TarkasSectionTitle("Explorar Audius")
            AudiusTrendingLink(onClick = onLoadAudiusTrending)
          }

          Spacer(modifier = Modifier.height(10.dp))

          TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar músicas e artistas no Audius", fontSize = 17.sp) },
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
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              unfocusedIndicatorColor = Color.Transparent,
              focusedIndicatorColor = Color.Transparent
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
                .padding(top = 8.dp)
                .height(48.dp)
                .testTag("btn_audius_search_submit")
            ) {
              Text("Pesquisar no Audius", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
          }
        }
      }

      // 3. Track list
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          TarkasSectionTitle("Faixas (${tracks.size})")
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .clickable { audioPickerLauncher.launch("audio/*") }
              .padding(horizontal = 10.dp, vertical = 12.dp)
              .testTag("music_btn_open_local"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Abrir do aparelho", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
      }

      items(tracks) { track ->
        val isSelected = currentTrack?.id == track.id
        Column(modifier = Modifier.padding(horizontal = 20.dp).testTag("track_row_${track.id}")) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onPlayTrack(track) }
              .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(Color(track.colorHex), Color(track.colorHex).copy(alpha = 0.6f)))),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isSelected && isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
              )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = track.title,
                fontSize = 17.sp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = "${track.artist} • ${track.album}",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }

            Text(
              text = track.formattedDuration(),
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(start = 8.dp)
            )

            TrackActionsMenu(
              track = track,
              isFavorite = track.id in favoriteIds,
              playlists = playlists,
              onToggleFavorite = { onToggleFavorite(track) },
              onPlayNext = { onPlayNext(track) },
              onAddToQueue = { onAddToQueue(track) },
              onAddToPlaylist = { id -> onAddToPlaylist(id, track) },
              onCreatePlaylist = { name -> onCreatePlaylist(name, track) }
            )
          }
          HorizontalDivider(
            modifier = Modifier.padding(start = 78.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
          )
        }
      }
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

/** "Trending" link next to the Audius title. */
@Composable
private fun AudiusTrendingLink(onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 12.dp)
      .testTag("btn_audius_trending"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Filled.TrendingUp,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text("Em alta", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
  }
}

/** Thin rounded progress bar that can be tapped or dragged (track position and volume). */
@Composable
private fun ScrubBar(
  fraction: Float,
  description: String,
  filled: Color,
  track: Color,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(28.dp)
      .semantics { contentDescription = "$description ${(fraction * 100).roundToInt()}%" }
      .pointerInput(Unit) {
        detectTapGestures { offset -> onChange((offset.x / size.width).coerceIn(0f, 1f)) }
      }
      .pointerInput(Unit) {
        detectHorizontalDragGestures { change, _ ->
          onChange((change.position.x / size.width).coerceIn(0f, 1f))
        }
      },
    contentAlignment = Alignment.CenterStart
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(CircleShape)
        .background(track)
    )
    Box(
      modifier = Modifier
        .fillMaxWidth(fraction.coerceIn(0f, 1f))
        .height(6.dp)
        .clip(CircleShape)
        .background(filled)
    )
  }
}

private fun formatClock(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  return String.format("%d:%02d", totalSec / 60, totalSec % 60)
}
