package com.example.ui.screens

import com.example.ui.theme.opensPlayer
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
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.pressClickable
import com.example.ui.theme.glassControl
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.ui.components.TarkasMark
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TarkasSectionTitle
import com.example.ui.components.TrackActionsMenu
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.ui.viewmodel.TarkasNavTab
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator

@Composable
fun HomeScreen(
  currentTrack: Track?,
  tracks: List<Track>,
  isPlaying: Boolean,
  favoriteIds: List<String>,
  playlists: List<Playlist>,
  onPlayTrack: (Track) -> Unit,
  onTogglePlayPause: () -> Unit,
  onNextTrack: () -> Unit,
  onPreviousTrack: () -> Unit,
  onNavigateToTab: (TarkasNavTab) -> Unit,
  onToggleFavorite: (Track) -> Unit,
  onPlayNext: (Track) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (String, Track) -> Unit,
  onCreatePlaylist: (String, Track) -> Unit,
  audiusResults: List<Track> = emptyList(),
  audiusSearching: Boolean = false,
  onSearchAudiusOnline: (String) -> Unit = {},
  onPlayAudiusResult: (Track) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var searchQuery by remember { mutableStateOf("") }
  var selectedCategory by remember { mutableStateOf("Todas") }

  // The same search bar also searches Audius (the ViewModel waits for a pause in typing).
  LaunchedEffect(searchQuery) { onSearchAudiusOnline(searchQuery) }

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
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + LocalBarInset.current),
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
            // Same capsule of glass as the tab bar and mini player.
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
              unfocusedContainerColor = Color.Transparent,
              focusedContainerColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              focusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
              .weight(1f)
              .glassControl(RoundedCornerShape(28.dp))
              .testTag("home_search_input")
          )
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
                  .pressClickable(pressedScale = 0.95f) { selectedCategory = station.name }
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

    // 3. Destaques: album cards without a container, art + two text lines
    if (searchQuery.isBlank() && tracks.isNotEmpty()) {
      item {
        Column {
          TarkasSectionTitle("Destaques")
          Spacer(modifier = Modifier.height(14.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(tracks.take(8)) { track ->
              val isCurrent = currentTrack?.id == track.id
              Column(
                modifier = Modifier
                  .width(220.dp)
                  .opensPlayer()
                  .pressClickable(pressedScale = 0.97f) { onPlayTrack(track) }
                  .testTag("featured_track_${track.id}")
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .glassControl(RoundedCornerShape(20.dp))
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
                  TrackArtwork(
                    track = track,
                    shape = RoundedCornerShape(12.dp),
                    iconSize = 44.dp,
                    playing = isCurrent && isPlaying,
                    gradientEndAlpha = 0.55f,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.45f)
                  )
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

    // 4. Músicas: flat rows with dividers, like the Apple Music library
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
      ) {
        TarkasSectionTitle(
          if (searchQuery.isNotBlank()) "Na sua biblioteca"
          else if (selectedCategory == "Todas") "Músicas" else "Estação $selectedCategory"
        )
        Text(
          text = "${filteredTracks.size} faixas",
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }

    if (filteredTracks.isEmpty() && searchQuery.isNotBlank()) {
      item {
        Text(
          text = "Nada na sua biblioteca para \"$searchQuery\".",
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else if (filteredTracks.isEmpty()) {
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

    @Composable
    fun SongRow(track: Track, testTag: String, onClick: () -> Unit, modifier: Modifier = Modifier) = TrackRow(
      track = track,
      isSelected = currentTrack?.id == track.id,
      isPlaying = isPlaying,
      isFavorite = track.id in favoriteIds,
      playlists = playlists,
      onClick = onClick,
      onToggleFavorite = { onToggleFavorite(track) },
      onPlayNext = { onPlayNext(track) },
      onAddToQueue = { onAddToQueue(track) },
      onAddToPlaylist = { id -> onAddToPlaylist(id, track) },
      onCreatePlaylist = { name -> onCreatePlaylist(name, track) },
      modifier = modifier.testTag(testTag)
    )

    // Rows glide to their new place when a station filters the list.
    items(filteredTracks, key = { it.id }) { track ->
      SongRow(track, "lib_track_${track.id}", onClick = { onPlayTrack(track) }, modifier = Modifier.animateItem())
    }

    // 5. While searching: the same query on Audius, below the library. Its songs play in the app.
    if (searchQuery.isNotBlank()) {
      item(key = "audius_header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          TarkasSectionTitle("No Audius", modifier = Modifier.weight(1f))
          if (audiusSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
      }
      items(audiusResults, key = { "audius_result_${it.id}" }) { track ->
        SongRow(track, "audius_result_${track.id}", onClick = { onPlayAudiusResult(track) })
      }
      if (audiusResults.isEmpty() && !audiusSearching) {
        item(key = "audius_empty") {
          Text(
            "Nada encontrado no Audius.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

/** A song row: cover, title, artist and album, and the ⋮ menu. */
@Composable
private fun TrackRow(
  track: Track,
  isSelected: Boolean,
  isPlaying: Boolean,
  isFavorite: Boolean,
  playlists: List<Playlist>,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onPlayNext: () -> Unit,
  onAddToQueue: () -> Unit,
  onAddToPlaylist: (String) -> Unit,
  onCreatePlaylist: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(modifier = modifier) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .opensPlayer()
        .pressClickable(pressedScale = 0.97f, onClick = onClick)
        .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TrackArtwork(
        track = track,
        shape = RoundedCornerShape(6.dp),
        iconSize = 26.dp,
        playing = isSelected && isPlaying,
        modifier = Modifier.size(64.dp)
      )

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
        isFavorite = isFavorite,
        playlists = playlists,
        onToggleFavorite = onToggleFavorite,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onAddToPlaylist = onAddToPlaylist,
        onCreatePlaylist = onCreatePlaylist
      )
    }
    HorizontalDivider(
      modifier = Modifier.padding(start = 78.dp, top = 4.dp),
      color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
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
