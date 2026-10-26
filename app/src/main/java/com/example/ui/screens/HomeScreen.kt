package com.example.ui.screens

import androidx.compose.runtime.saveable.rememberSaveable
import com.example.ui.theme.opensPlayer
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.ui.components.EmptyState
import com.example.ui.components.TarkasMark
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TarkasSectionTitle
import com.example.ui.components.TrackActionsMenu
import com.example.ui.viewmodel.TrendingState
import com.example.ui.theme.staggeredEntrance
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush

@Composable
fun HomeScreen(
  currentTrack: Track?,
  tracks: List<Track>,
  isPlaying: Boolean,
  favoriteIds: List<String>,
  playlists: List<Playlist>,
  onPlayTrack: (Track) -> Unit,
  onToggleFavorite: (Track) -> Unit,
  onPlayNext: (Track) -> Unit,
  onAddToQueue: (Track) -> Unit,
  onAddToPlaylist: (String, Track) -> Unit,
  onCreatePlaylist: (String, Track) -> Unit,
  trending: TrendingState,
  onRetryTrending: () -> Unit,
  onPlayTrending: (Track) -> Unit,
  onOpenLibrary: () -> Unit,
  audiusResults: List<Track> = emptyList(),
  audiusSearching: Boolean = false,
  audiusOffline: Boolean = false,
  onSearchAudiusOnline: (String) -> Unit = {},
  onPlayAudiusResult: (Track) -> Unit = {},
  youtubeResults: List<Track> = emptyList(),
  youtubeSearching: Boolean = false,
  youtubeOffline: Boolean = false,
  onSearchYouTubeOnline: (String) -> Unit = {},
  onPlayYouTubeResult: (Track) -> Unit = {},
  spotifyResults: List<Track> = emptyList(),
  spotifySearching: Boolean = false,
  onSearchSpotifyOnline: (String) -> Unit = {},
  onPlaySpotifyResult: (Track) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var showDemos by rememberSaveable { mutableStateOf(false) }

  // The same search bar also searches Audius (the ViewModel waits for a pause in typing).
  LaunchedEffect(searchQuery) {
    onSearchAudiusOnline(searchQuery)
    onSearchYouTubeOnline(searchQuery)
    onSearchSpotifyOnline(searchQuery)
  }

  // The user's own music; the synthesized demo tracks sit in their own section at the bottom.
  val myTracks = tracks.filter { !it.isDemo }
  val demoTracks = tracks.filter { it.isDemo }
  val searchMatches = if (searchQuery.isBlank()) emptyList() else tracks.filter { track ->
    track.title.contains(searchQuery, ignoreCase = true) ||
      track.artist.contains(searchQuery, ignoreCase = true) ||
      track.album.contains(searchQuery, ignoreCase = true)
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

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp),
    contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + LocalBarInset.current),
    verticalArrangement = Arrangement.spacedBy(28.dp)
  ) {

    // 1. Search pill
    item(key = "search") {
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
          .fillMaxWidth()
          .glassControl(RoundedCornerShape(28.dp))
          .testTag("home_search_input")
      )
    }

    if (searchQuery.isBlank()) {
      // 2. Em alta no Audius: cards with the cover, like Apple Music's featured row
      item(key = "trending") {
        Column {
          TarkasSectionTitle("Em alta no Audius")
          Spacer(modifier = Modifier.height(14.dp))
          when {
            // Cards waiting for the network: placeholders of the same size with a soft shimmer.
            trending.tracks.isEmpty() && trending.loading -> TrendingSkeleton()
            trending.tracks.isNotEmpty() -> TrendingRow(
              tracks = trending.tracks,
              currentTrack = currentTrack,
              isPlaying = isPlaying,
              onPlay = onPlayTrending
            )
            trending.offline -> EmptyState(
              icon = Icons.Default.CloudOff,
              title = "Sem conexão com o Audius",
              message = "Confira a internet e tente de novo. Suas músicas do aparelho continuam tocando.",
              actionLabel = "Tentar de novo",
              onAction = onRetryTrending,
              modifier = Modifier.testTag("trending_offline")
            )
            !trending.loading -> Text(
              "O Audius não mandou nada em alta agora.",
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // 3. Suas músicas
      item(key = "mine_header") {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom
        ) {
          TarkasSectionTitle("Suas músicas")
          if (myTracks.isNotEmpty()) {
            Text(
              text = if (myTracks.size == 1) "1 faixa" else "${myTracks.size} faixas",
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
      if (myTracks.isEmpty()) {
        item(key = "mine_empty") {
          EmptyState(
            icon = Icons.Default.LibraryMusic,
            title = "Nenhuma música sua ainda",
            message = "Busque uma música acima ou adicione um arquivo do aparelho pela Biblioteca.",
            actionLabel = "Abrir Biblioteca",
            onAction = onOpenLibrary,
            modifier = Modifier.testTag("home_empty")
          )
        }
      }
      itemsIndexed(myTracks, key = { _, t -> t.id }) { index, track ->
        SongRow(track, "lib_track_${track.id}", onClick = { onPlayTrack(track) }, modifier = Modifier.animateItem().staggeredEntrance(index))
      }

      // 4. Demonstração: the short synthesized tracks, folded away
      if (demoTracks.isNotEmpty()) {
        item(key = "demo_header") {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .pressClickable(pressedScale = 0.98f) { showDemos = !showDemos }
              .padding(vertical = 4.dp)
              .testTag("home_demo_toggle"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Faixas de demonstração", fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
              Text(
                "${demoTracks.size} trechos curtos gerados pelo app, para testar o som e o equalizador",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Icon(
              imageVector = if (showDemos) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = if (showDemos) "Esconder" else "Mostrar",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        if (showDemos) {
          items(demoTracks, key = { "demo_${it.id}" }) { track ->
            SongRow(track, "demo_track_${track.id}", onClick = { onPlayTrack(track) }, modifier = Modifier.animateItem())
          }
        }
      }
    } else {
      // While searching: the library first, then the same query on Audius. Its songs play in the app.
      item(key = "search_lib_header") { TarkasSectionTitle("Na sua biblioteca") }
      if (searchMatches.isEmpty()) {
        item(key = "search_lib_empty") {
          Text(
            text = "Nada na sua biblioteca para \"$searchQuery\".",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      items(searchMatches, key = { it.id }) { track ->
        SongRow(track, "lib_track_${track.id}", onClick = { onPlayTrack(track) }, modifier = Modifier.animateItem())
      }

      item(key = "audius_header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          TarkasSectionTitle("No Audius", modifier = Modifier.weight(1f))
          if (audiusSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
      }
      itemsIndexed(audiusResults, key = { _, t -> "audius_result_${t.id}" }) { index, track ->
        SongRow(track, "audius_result_${track.id}", onClick = { onPlayAudiusResult(track) }, modifier = Modifier.animateItem().staggeredEntrance(index))
      }
      if (audiusResults.isEmpty() && !audiusSearching) {
        item(key = "audius_empty") {
          if (audiusOffline) {
            EmptyState(
              icon = Icons.Default.CloudOff,
              title = "Sem conexão com o Audius",
              message = "Confira a internet e tente de novo.",
              actionLabel = "Tentar de novo",
              onAction = { onSearchAudiusOnline(searchQuery) },
              modifier = Modifier.testTag("audius_offline")
            )
          } else {
            Text(
              "Nada encontrado no Audius.",
              fontSize = 15.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      item(key = "youtube_header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          TarkasSectionTitle("No YouTube Music", modifier = Modifier.weight(1f))
          if (youtubeSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
      }
      itemsIndexed(youtubeResults.take(10), key = { _, t -> "youtube_result_${t.id}" }) { index, track ->
        SongRow(track, "youtube_result_${track.id}", onClick = { onPlayYouTubeResult(track) }, modifier = Modifier.animateItem().staggeredEntrance(index))
      }
      if (youtubeResults.isEmpty() && !youtubeSearching) {
        item(key = "youtube_empty") {
          Text(
            if (youtubeOffline) "Sem conexão com o YouTube Music." else "Nada encontrado no YouTube Music.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("youtube_empty")
          )
        }
      }

      item(key = "spotify_header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
          TarkasSectionTitle("No Spotify", modifier = Modifier.weight(1f))
          if (spotifySearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
      }
      itemsIndexed(spotifyResults.take(10), key = { _, t -> "spotify_result_${t.id}" }) { index, track ->
        SongRow(track, "spotify_result_${track.id}", onClick = { onPlaySpotifyResult(track) }, modifier = Modifier.animateItem().staggeredEntrance(index))
      }
      if (spotifyResults.isEmpty() && !spotifySearching) {
        item(key = "spotify_empty") {
          Text(
            "Nada encontrado no Spotify.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("spotify_empty")
          )
        }
      }
    }
  }
}

/** Under a streamed result: which service it comes from. */
@Composable
private fun SourceBadge(track: Track) {
  val spotify = track.webSource == "spotify"
  Row(
    modifier = Modifier.padding(top = 3.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(14.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(if (spotify) Color(0xFF1DB954) else Color(0xFFE5322D)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(10.dp)
      )
    }
    Spacer(Modifier.width(6.dp))
    Text(if (spotify) "Spotify" else "YouTube Music", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
  }
}

/** The trending cards: cover, genre, title and artist, in a row that scrolls sideways. */
@Composable
private fun TrendingRow(
  tracks: List<Track>,
  currentTrack: Track?,
  isPlaying: Boolean,
  onPlay: (Track) -> Unit
) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    itemsIndexed(tracks.take(12), key = { _, t -> "trending_${t.id}" }) { index, track ->
      val isCurrent = currentTrack?.id == track.id
      Column(
        modifier = Modifier
          .width(220.dp)
          .staggeredEntrance(index)
          .opensPlayer()
          .pressClickable(pressedScale = 0.97f) { onPlay(track) }
          .testTag("trending_track_${track.id}")
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
              text = track.genre,
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f)
            )
            TarkasMark(
              modifier = Modifier.size(width = 28.dp, height = 17.dp),
              symbolColor = MaterialTheme.colorScheme.onSurface
            )
          }
          Spacer(modifier = Modifier.height(10.dp))
          TrackArtwork(
            track = track,
            shape = RoundedCornerShape(12.dp),
            iconSize = 44.dp,
            playing = isCurrent && isPlaying,
            gradientEndAlpha = 0.55f,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
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

/**
 * Three card-shaped placeholders while trending loads. A faint light sweeps across them (8% at
 * most, 1.4 s a pass); with reduced motion they stay still.
 */
@Composable
private fun TrendingSkeleton() {
  val reduced = com.example.ui.theme.rememberReducedMotion()
  val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
  val light = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
  val sweep = if (reduced) null else androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer").animateFloat(
    initialValue = -1f,
    targetValue = 2f,
    animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1400)),
    label = "shimmerX"
  )
  Row(
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.testTag("trending_loading")
  ) {
    repeat(3) {
      Column(modifier = Modifier.width(220.dp)) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.86f)
            .clip(RoundedCornerShape(20.dp))
            .drawBehind {
              drawRect(base)
              sweep?.let { s ->
                val x = size.width * s.value
                drawRect(
                  Brush.linearGradient(
                    listOf(Color.Transparent, light, Color.Transparent),
                    start = androidx.compose.ui.geometry.Offset(x - size.width * 0.5f, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height)
                  )
                )
              }
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(Modifier.width(150.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(base))
        Spacer(modifier = Modifier.height(6.dp))
        Box(Modifier.width(100.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(base))
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
          text = if (track.isWeb) track.artist.ifBlank { track.album } else "${track.artist} • ${track.album}",
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (track.isWeb) SourceBadge(track)
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
