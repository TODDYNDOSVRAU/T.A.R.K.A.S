package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.ui.components.EmptyState
import com.example.ui.components.SearchField
import com.example.ui.components.SectionHeader
import com.example.ui.components.TrackActionsMenu
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackRow
import com.example.ui.components.highlightClickable
import com.example.ui.screens.library.TrackDivider
import com.example.ui.screens.library.itemMotion
import com.example.ui.screens.library.trackSubtitle
import com.example.ui.screens.library.trailingEdge
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.theme.opensPlayer
import com.example.ui.theme.pressClickable
import com.example.ui.theme.listEntrance
import com.example.ui.viewmodel.TrendingState
import kotlinx.coroutines.delay

/** How long typing must pause before the query goes out to the three services. */
private const val SearchDebounceMs = 350L

/** Width of a tile in the trending carousel: two and a bit fit a phone, so the next one peeks in. */
private val TileWidth = 160.dp

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
  // The query the services were last asked about. Saved with the query, so coming back to Início
  // (closing the player, rotating) does not send the same three searches again.
  var searchedQuery by rememberSaveable { mutableStateOf("") }
  var showDemos by rememberSaveable { mutableStateOf(false) }

  val searchAudius by rememberUpdatedState(onSearchAudiusOnline)
  val searchYouTube by rememberUpdatedState(onSearchYouTubeOnline)
  val searchSpotify by rememberUpdatedState(onSearchSpotifyOnline)
  LaunchedEffect(searchQuery) {
    if (searchQuery == searchedQuery) return@LaunchedEffect
    // Clearing the field empties the results at once; a new query waits for a pause in typing.
    if (searchQuery.isNotBlank()) delay(SearchDebounceMs)
    searchedQuery = searchQuery
    searchAudius(searchQuery)
    searchYouTube(searchQuery)
    searchSpotify(searchQuery)
  }

  // The user's own music; the synthesized demo tracks sit in their own section at the bottom.
  val myTracks = tracks.filter { !it.isDemo }
  val demoTracks = tracks.filter { it.isDemo }
  val searchMatches = if (searchQuery.isBlank()) emptyList() else tracks.filter { track ->
    track.title.contains(searchQuery, ignoreCase = true) ||
      track.artist.contains(searchQuery, ignoreCase = true) ||
      track.album.contains(searchQuery, ignoreCase = true)
  }

  /** A song with its actions sheet; [showSource] adds the service to the subtitle. */
  fun LazyListScope.songs(
    list: List<Track>,
    keyPrefix: String,
    tagPrefix: String,
    showSource: Boolean,
    stagger: Boolean,
    onClick: (Track) -> Unit
  ) {
    itemsIndexed(list, key = { _, t -> "${keyPrefix}_${t.id}" }) { index, track ->
      Column(modifier = itemMotion(index, stagger)) {
        if (index > 0) TrackDivider()
        TrackRow(
          track = track,
          subtitle = trackSubtitle(track, showSource),
          onClick = { onClick(track) },
          isCurrent = currentTrack?.id == track.id,
          isPlaying = isPlaying,
          modifier = Modifier.opensPlayer().testTag("${tagPrefix}_${track.id}"),
          trailing = {
            TrackActionsMenu(
              track = track,
              isFavorite = track.id in favoriteIds,
              playlists = playlists,
              onToggleFavorite = { onToggleFavorite(track) },
              onPlayNext = { onPlayNext(track) },
              onAddToQueue = { onAddToQueue(track) },
              onAddToPlaylist = { id -> onAddToPlaylist(id, track) },
              onCreatePlaylist = { name -> onCreatePlaylist(name, track) },
              modifier = Modifier.trailingEdge()
            )
          }
        )
      }
    }
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(top = Space.xs, bottom = LocalBarInset.current + Space.l)
  ) {
    item(key = "search") {
      SearchField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = "Artistas, músicas, álbuns",
        modifier = Modifier
          .padding(horizontal = Space.page)
          .testTag("home_search_input")
      )
    }

    if (searchQuery.isBlank()) {
      item(key = "trending") {
        Column {
          SectionHeader("Em alta no Audius", modifier = Modifier.padding(top = Space.xl, bottom = Space.m))
          when {
            trending.tracks.isEmpty() && trending.loading -> TrendingSkeleton()
            trending.tracks.isNotEmpty() -> TrendingRow(
              tracks = trending.tracks,
              currentTrack = currentTrack,
              isPlaying = isPlaying,
              onPlay = onPlayTrending
            )
            trending.offline -> EmptyState(
              icon = Icons.Rounded.CloudOff,
              title = "Sem conexão com o Audius",
              message = "Confira a internet e tente de novo. Suas músicas do aparelho continuam tocando.",
              actionLabel = "Tentar de novo",
              onAction = onRetryTrending,
              modifier = Modifier.padding(horizontal = Space.page).testTag("trending_offline")
            )
            !trending.loading -> Note("O Audius não mandou nada em alta agora.")
          }
        }
      }

      item(key = "mine_header") {
        SectionHeader("Suas músicas", modifier = Modifier.padding(top = Space.section, bottom = Space.s))
      }
      if (myTracks.isEmpty()) {
        item(key = "mine_empty") {
          EmptyState(
            icon = Icons.Rounded.LibraryMusic,
            title = "Nenhuma música sua ainda",
            message = "Busque uma música acima ou importe um arquivo do aparelho na Biblioteca.",
            actionLabel = "Abrir Biblioteca",
            onAction = onOpenLibrary,
            modifier = Modifier.padding(horizontal = Space.page).testTag("home_empty")
          )
        }
      }
      songs(myTracks, "mine", "lib_track", showSource = true, stagger = true, onClick = onPlayTrack)

      // The short synthesized tracks, folded away under one row.
      if (demoTracks.isNotEmpty()) {
        item(key = "demo_header") {
          DemoToggle(
            count = demoTracks.size,
            expanded = showDemos,
            onToggle = { showDemos = !showDemos },
            modifier = Modifier.padding(top = Space.xl)
          )
        }
        if (showDemos) {
          songs(demoTracks, "demo", "demo_track", showSource = false, stagger = false, onClick = onPlayTrack)
        }
      }
    } else {
      // While searching: the library first, then the same query on each service. All of it plays here.
      item(key = "search_lib_header") { ResultsHeader("Na sua biblioteca", searching = false) }
      if (searchMatches.isEmpty()) {
        item(key = "search_lib_empty") { Note("Nada na sua biblioteca para “$searchQuery”.") }
      }
      songs(searchMatches, "lib", "lib_track", showSource = true, stagger = false, onClick = onPlayTrack)

      item(key = "audius_header") { ResultsHeader("Audius", searching = audiusSearching) }
      songs(audiusResults, "audius_result", "audius_result", showSource = false, stagger = true, onClick = onPlayAudiusResult)
      if (audiusResults.isEmpty() && !audiusSearching) {
        item(key = "audius_empty") {
          if (audiusOffline) {
            EmptyState(
              icon = Icons.Rounded.CloudOff,
              title = "Sem conexão com o Audius",
              message = "Confira a internet e tente de novo.",
              actionLabel = "Tentar de novo",
              onAction = { onSearchAudiusOnline(searchQuery) },
              modifier = Modifier.padding(horizontal = Space.page).testTag("audius_offline")
            )
          } else {
            Note("Nada encontrado no Audius.")
          }
        }
      }

      item(key = "youtube_header") { ResultsHeader("YouTube Music", searching = youtubeSearching) }
      songs(youtubeResults.take(10), "youtube_result", "youtube_result", showSource = false, stagger = true, onClick = onPlayYouTubeResult)
      if (youtubeResults.isEmpty() && !youtubeSearching) {
        item(key = "youtube_empty") {
          if (youtubeOffline) {
            EmptyState(
              icon = Icons.Rounded.CloudOff,
              title = "Sem conexão com o YouTube Music",
              message = "Confira a internet e tente de novo.",
              actionLabel = "Tentar de novo",
              onAction = { onSearchYouTubeOnline(searchQuery) },
              modifier = Modifier.padding(horizontal = Space.page).testTag("youtube_empty")
            )
          } else {
            Note("Nada encontrado no YouTube Music.", Modifier.testTag("youtube_empty"))
          }
        }
      }

      item(key = "spotify_header") { ResultsHeader("Spotify", searching = spotifySearching) }
      songs(spotifyResults.take(10), "spotify_result", "spotify_result", showSource = false, stagger = true, onClick = onPlaySpotifyResult)
      if (spotifyResults.isEmpty() && !spotifySearching) {
        item(key = "spotify_empty") { Note("Nada encontrado no Spotify.", Modifier.testTag("spotify_empty")) }
      }
    }
  }
}

/** A section title over search results, with a small spinner while that service is still answering. */
@Composable
private fun ResultsHeader(title: String, searching: Boolean) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = Space.xl, bottom = Space.s, end = Space.page),
    verticalAlignment = Alignment.CenterVertically
  ) {
    SectionHeader(title, modifier = Modifier.weight(1f))
    if (searching) {
      CircularProgressIndicator(
        modifier = Modifier
          .size(Size.iconSmall)
          .semantics { stateDescription = "Buscando" },
        strokeWidth = 2.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

/** One line of secondary text inside a section: "nothing found" and the like. */
@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = AppText.Subhead,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(horizontal = Space.page, vertical = Space.s)
  )
}

/** The row that folds the demo tracks in and out, announced with its state. */
@Composable
private fun DemoToggle(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = Size.row)
      .highlightClickable(role = Role.Button, onClickLabel = if (expanded) "Esconder" else "Mostrar", onClick = onToggle)
      .semantics { stateDescription = if (expanded) "Aberto" else "Fechado" }
      .padding(horizontal = Space.page, vertical = Space.s)
      .testTag("home_demo_toggle"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text("Faixas de demonstração", style = AppText.Body, color = MaterialTheme.colorScheme.onSurface)
      Text(
        "$count trechos curtos gerados pelo app, para testar o som e o equalizador",
        style = AppText.Subhead,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Spacer(Modifier.width(Space.m))
    Icon(
      imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

/**
 * The trending carousel: square covers with the title, artist and genre under them, no card around
 * them. It starts at the page margin, snaps tile by tile and lets the next tile peek in.
 */
@Composable
private fun TrendingRow(
  tracks: List<Track>,
  currentTrack: Track?,
  isPlaying: Boolean,
  onPlay: (Track) -> Unit
) {
  val state = rememberLazyListState()
  LazyRow(
    state = state,
    flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
    contentPadding = PaddingValues(horizontal = Space.page),
    horizontalArrangement = Arrangement.spacedBy(Space.m)
  ) {
    itemsIndexed(tracks.take(12), key = { _, t -> "trending_${t.id}" }) { index, track ->
      val isCurrent = currentTrack?.id == track.id
      Column(
        modifier = Modifier
          .width(TileWidth)
          .listEntrance(index)
          .opensPlayer()
          .pressClickable(pressedScale = 0.97f, onClickLabel = "Tocar ${track.title}") { onPlay(track) }
          .testTag("trending_track_${track.id}")
      ) {
        TrackArtwork(
          track = track,
          shape = Shapes.s,
          iconSize = 40.dp,
          playing = isCurrent && isPlaying,
          modifier = Modifier.size(TileWidth)
        )
        Spacer(Modifier.height(Space.s))
        Text(
          text = track.title,
          style = AppText.Subhead.copy(fontWeight = FontWeight.SemiBold),
          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = track.artist,
          style = AppText.Footnote,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (track.genre.isNotBlank()) {
          Text(
            text = track.genre,
            style = AppText.Caption1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
  }
}

/** Tiles of the same size as the carousel's while trending loads; still, with nothing sweeping. */
@Composable
private fun TrendingSkeleton() {
  val fill = MaterialTheme.colorScheme.surfaceContainer
  Row(
    horizontalArrangement = Arrangement.spacedBy(Space.m),
    // Laid out at full size and cut by the screen edge, as the real carousel is.
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentWidth(Alignment.Start, unbounded = true)
      .padding(horizontal = Space.page)
      .semantics { stateDescription = "Carregando" }
      .testTag("trending_loading")
  ) {
    repeat(3) {
      Column(modifier = Modifier.width(TileWidth)) {
        Box(Modifier.size(TileWidth).clip(Shapes.s).background(fill))
        Spacer(Modifier.height(Space.s))
        Box(Modifier.width(110.dp).height(Space.m).clip(Shapes.xs).background(fill))
        Spacer(Modifier.height(Space.xs))
        Box(Modifier.width(80.dp).height(Space.m).clip(Shapes.xs).background(fill))
      }
    }
  }
}
