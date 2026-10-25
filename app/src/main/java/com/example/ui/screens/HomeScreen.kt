package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.model.VerificationStatus
import com.example.ui.components.CuriosityDetailDialog
import com.example.ui.components.TarkasMark
import com.example.ui.components.TarkasSectionTitle
import com.example.ui.components.TrackActionsMenu
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.ui.viewmodel.TarkasNavTab

@Composable
fun HomeScreen(
  currentTrack: Track?,
  tracks: List<Track>,
  isPlaying: Boolean,
  waveformEnergy: Float,
  activeCuriosity: Curiosity?,
  allCuriosities: List<Curiosity>,
  favoriteIds: List<String>,
  playlists: List<Playlist>,
  onPlayTrack: (Track) -> Unit,
  onTogglePlayPause: () -> Unit,
  onNextTrack: () -> Unit,
  onPreviousTrack: () -> Unit,
  onNavigateToTab: (TarkasNavTab) -> Unit,
  onSearchAudius: (String) -> Unit,
  onToggleFavorite: (Track) -> Unit,
  onPlayNext: (Track) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (String, Track) -> Unit,
  onCreatePlaylist: (String, Track) -> Unit,
  onAddLocalTrack: (String, String, String, Long) -> Unit = { _, _, _, _ -> },
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("Todas") }
  var selectedCuriosityForDialog by remember { mutableStateOf<Curiosity?>(null) }
  var showActiveCuriosityDialog by remember { mutableStateOf(false) }

  // SAF File Picker for local audio files
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

  // Filter tracks by category and search query
  val filteredTracks = tracks.filter { track ->
    val matchesCategory = when (selectedCategory) {
      "Audius" -> track.album.contains("Audius", ignoreCase = true)
      "Locais" -> track.isLocalFile
      "Clássicos" -> !track.isLocalFile && !track.album.contains("Audius", ignoreCase = true)
      else -> true
    }
    val matchesSearch = if (searchQuery.isBlank()) {
      true
    } else {
      track.title.contains(searchQuery, ignoreCase = true) ||
          track.artist.contains(searchQuery, ignoreCase = true) ||
          track.album.contains(searchQuery, ignoreCase = true)
    }
    matchesCategory && matchesSearch
  }

  fun captionFor(station: String): String {
    val count = tracks.count { track ->
      when (station) {
        "Audius" -> track.album.contains("Audius", ignoreCase = true)
        "Locais" -> track.isLocalFile
        "Clássicos" -> !track.isLocalFile && !track.album.contains("Audius", ignoreCase = true)
        else -> true
      }
    }
    return if (count == 1) "1 faixa" else "$count faixas"
  }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
    verticalArrangement = Arrangement.spacedBy(28.dp)
  ) {

    // 1. Search pill + import, then category chips
    item {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Artistas, músicas, álbuns", fontSize = 17.sp) },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Buscar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
              )
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Limpar busca",
                    modifier = Modifier.size(18.dp)
                  )
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
              .weight(1f)
              .testTag("home_search_input")
          )

          IconButton(
            onClick = { audioPickerLauncher.launch("audio/*") },
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .testTag("home_add_local_audio")
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Importar áudio do aparelho",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }

      }
    }

    // 2. "Estações por categoria": square gradient cards with a faint watermark, like "Stations by Genre"
    if (searchQuery.isBlank()) {
      item {
        Column {
          TarkasSectionTitle("Estações por categoria")
          Spacer(modifier = Modifier.height(14.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(stationCategories) { station ->
              val selected = selectedCategory == station.name
              Column(
                modifier = Modifier
                  .width(160.dp)
                  .clickable { selectedCategory = station.name }
                  .semantics { this.selected = selected }
                  .testTag("station_${station.name}")
              ) {
                Box(
                  modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(station.top, station.bottom)))
                    .then(
                      if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(10.dp))
                      else Modifier
                    )
                ) {
                  Text(
                    text = station.name,
                    modifier = Modifier
                      .align(Alignment.BottomStart)
                      .offset(x = (-6).dp, y = 18.dp),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    color = Color.Black.copy(alpha = 0.10f)
                  )
                  Text(
                    text = station.name,
                    modifier = Modifier
                      .align(Alignment.TopStart)
                      .padding(start = 12.dp, top = 12.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                  TarkasMark(
                    modifier = Modifier
                      .align(Alignment.TopEnd)
                      .padding(top = 14.dp, end = 12.dp)
                      .size(width = 28.dp, height = 17.dp),
                    symbolColor = Color.White,
                    accentColor = Color.White
                  )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = if (station.name == "Todas") "Todas as músicas" else "Estação ${station.name}",
                  fontSize = 17.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                Text(
                  text = captionFor(station.name),
                  fontSize = 15.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }

    // 3. Curiosity of the current track (only when there is a real source)
    if (activeCuriosity != null && activeCuriosity.status != VerificationStatus.UNVERIFIED) {
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showActiveCuriosityDialog = true }
            .testTag("home_curiosity_pill")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Curiosidade",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = activeCuriosity.shortTrivia,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }
            Text(
              text = "Ver",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(start = 8.dp)
            )
          }
        }
      }
    }

    // 4. Destaques: album cards without a container, art + two text lines
    if (searchQuery.isBlank() && tracks.isNotEmpty()) {
      item {
        Column {
          TarkasSectionTitle("Destaques", onClick = { onNavigateToTab(TarkasNavTab.MUSIC) })
          Spacer(modifier = Modifier.height(14.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(tracks.take(8)) { track ->
              val isCurrent = currentTrack?.id == track.id
              Column(
                modifier = Modifier
                  .width(220.dp)
                  .clickable { onPlayTrack(track) }
                  .testTag("featured_track_${track.id}")
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = track.era,
                      fontSize = 17.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    TarkasMark(
                      modifier = Modifier.size(width = 28.dp, height = 17.dp),
                      symbolColor = MaterialTheme.colorScheme.onSurface
                    )
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = track.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.height(10.dp))
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .aspectRatio(1.45f)
                      .clip(RoundedCornerShape(6.dp))
                      .background(Brush.verticalGradient(listOf(Color(track.colorHex), Color(track.colorHex).copy(alpha = 0.55f)))),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = if (isCurrent && isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(44.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = track.title,
                  fontSize = 17.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                Text(
                  text = track.artist,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
    }

    // 5. Curiosidades musicais (curated, with sources)
    if (searchQuery.isBlank() && allCuriosities.isNotEmpty()) {
      item {
        Column {
          TarkasSectionTitle("Curiosidades Musicais")
          Spacer(modifier = Modifier.height(14.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(allCuriosities) { cur ->
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                  .width(240.dp)
                  .clickable { selectedCuriosityForDialog = cur }
                  .testTag("curiosity_card_${cur.trackId}")
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Text(
                    text = cur.artist.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = cur.trackTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = cur.shortTrivia,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                  )
                }
              }
            }
          }
        }
      }
    }

    // 6. Músicas: flat rows with dividers, like the Apple Music library
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        TarkasSectionTitle(
          if (searchQuery.isNotBlank()) "Resultados da busca"
          else if (selectedCategory == "Todas") "Músicas" else "Estação $selectedCategory"
        )
        Text(
          text = "${filteredTracks.size} faixas",
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    if (filteredTracks.isEmpty()) {
      item {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (searchQuery.isNotBlank()) "Nenhuma música encontrada para \"$searchQuery\"" else "Nenhuma música nesta categoria",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    items(filteredTracks) { track ->
      val isSelected = currentTrack?.id == track.id
      Column(modifier = Modifier.testTag("lib_track_${track.id}")) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayTrack(track) }
            .padding(vertical = 4.dp),
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
          modifier = Modifier.padding(start = 78.dp, top = 4.dp),
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
      }
    }
  }

  // Curiosity Detail Dialog when clicked (shown only on demand)
  if (showActiveCuriosityDialog && activeCuriosity != null) {
    CuriosityDetailDialog(
      curiosity = activeCuriosity,
      onDismiss = { showActiveCuriosityDialog = false }
    )
  }

  selectedCuriosityForDialog?.let { cur ->
    CuriosityDetailDialog(
      curiosity = cur,
      onDismiss = { selectedCuriosityForDialog = null }
    )
  }
}

private class Station(val name: String, val top: Color, val bottom: Color)

// Palette of the T.A.R.K.A.S. symbol: ice, mid cyan, teal and a steel blue.
private val stationCategories = listOf(
  Station("Todas", Color(0xFF7EE3EA), Color(0xFF3FB4C0)),
  Station("Clássicos", Color(0xFF2F8FA8), Color(0xFF1E6A82)),
  Station("Audius", Color(0xFF0E7C86), Color(0xFF0A5A62)),
  Station("Locais", Color(0xFF5B7C99), Color(0xFF3F5A74))
)
