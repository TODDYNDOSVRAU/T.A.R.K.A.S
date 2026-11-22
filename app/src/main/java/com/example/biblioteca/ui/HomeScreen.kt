package com.example.ui.screens

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.ui.layout.WidthClass
import com.example.ui.layout.readingColumn
import com.example.ui.layout.rememberWidthClass
import com.example.ui.theme.SquircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.ui.components.EmptyState
import com.example.ui.components.SearchField
import com.example.ui.components.SectionHeader
import com.example.ui.components.topScrollFade
import com.example.ui.components.TrackActionsMenu
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackRow
import com.example.ui.components.highlightClickable
import com.example.ui.screens.library.TrackDivider
import com.example.ui.screens.library.itemMotion
import com.example.ui.screens.library.trackSubtitle
import com.example.ui.screens.library.trailingEdge
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.opensPlayer
import com.example.ui.theme.pressClickable
import com.example.ui.theme.listEntrance
import com.example.ui.viewmodel.OnlineSearchState
import com.example.ui.viewmodel.TrendingState
import kotlinx.coroutines.delay

/** How long typing must pause before the query goes out to the three services. */
private const val SearchDebounceMs = 350L

/** Width of a tile in the trending carousel: two and a bit fit a phone, so the next one peeks in. */
private val TileWidth = Grid.u96 + Grid.u48

/** "Tocadas recentemente" shows this many songs: a shortcut; the full list is in Biblioteca. */
private const val RecentLimit = 5

private fun clock(ms: Long): String {
  val totalSec = (ms / 1000).coerceAtLeast(0)
  return String.format(java.util.Locale.ROOT, "%d:%02d", totalSec / 60, totalSec % 60)
}

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
  onDownloadOffline: (Track) -> Unit = {},
  trending: TrendingState,
  onRetryTrending: () -> Unit,
  onPlayTrending: (Track) -> Unit,
  onOpenLibrary: () -> Unit,
  audiusResults: List<Track> = emptyList(),
  audiusSearching: Boolean = false,
  audiusOffline: Boolean = false,
  onSearchAudiusOnline: (String) -> Unit = {},
  onPlayAudiusResult: (Track) -> Unit = {},
  jamendo: OnlineSearchState = OnlineSearchState(),
  onSearchJamendoOnline: (String) -> Unit = {},
  onPlayJamendoResult: (Track) -> Unit = {},
  jamendoPopular: TrendingState = TrendingState(),
  /** The user's own music above the online rows (default) or below them; set in Ajustes. */
  musicFirst: Boolean = true,
  /** Plays and last plays per track id, for "Tocadas recentemente". */
  onLoadPlayStats: suspend () -> Map<String, com.example.data.playback.TrackStats> = { emptyMap() },
  /** Where the saved track stopped, for "Continuar ouvindo"; null when there is nothing to continue. */
  onLoadResume: suspend () -> com.example.data.playback.ResumeInfo? = { null },
  onResume: () -> Unit = {},
  onRetryJamendoPopular: () -> Unit = {},
  onPlayJamendoPopular: (Track) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  // The query the services were last asked about. Saved with the query, so coming back to Início
  // (closing the player, rotating) does not send the same searches again.
  var searchedQuery by rememberSaveable { mutableStateOf("") }
  var showDemos by rememberSaveable { mutableStateOf(false) }

  var playStats by remember { mutableStateOf<Map<String, com.example.data.playback.TrackStats>>(emptyMap()) }
  // Read again when the song that plays changes: a play may have just been counted.
  LaunchedEffect(currentTrack?.id) { playStats = onLoadPlayStats() }

  var resume by remember { mutableStateOf<com.example.data.playback.ResumeInfo?>(null) }
  // Read again when the song or the play state changes: pausing is what saves the position.
  LaunchedEffect(currentTrack?.id, isPlaying) { resume = onLoadResume() }

  val searchAudius by rememberUpdatedState(onSearchAudiusOnline)
  val searchJamendo by rememberUpdatedState(onSearchJamendoOnline)
  LaunchedEffect(searchQuery) {
    if (searchQuery == searchedQuery) return@LaunchedEffect
    // Clearing the field empties the results at once; a new query waits for a pause in typing.
    if (searchQuery.isNotBlank()) delay(SearchDebounceMs)
    searchedQuery = searchQuery
    searchAudius(searchQuery)
    searchJamendo(searchQuery)
  }

  // The user's own music; the synthesized demo tracks sit in their own section at the bottom.
  // Memoized with remember(tracks) so scrolling or unrelated state changes never re-filter the catalogue.
  val (myTracks, demoTracks) = remember(tracks) {
    tracks.partition { !it.isDemo }
  }
  val favoriteSet = remember(favoriteIds) { favoriteIds.toHashSet() }
  val recent = remember(myTracks, playStats) {
    com.example.data.library.AutoLists.build(com.example.data.library.AutoList.RECENTLY_PLAYED, myTracks, playStats, RecentLimit)
  }
  // Accents and case do not matter: "acao" finds "Ação".
  val searchMatches = if (searchQuery.isBlank()) emptyList() else androidx.compose.runtime.remember(searchQuery, tracks) { tracks.filter { track ->
    com.example.data.TextMatch.contains(track.title, searchQuery) ||
      com.example.data.TextMatch.contains(track.artist, searchQuery) ||
      com.example.data.TextMatch.contains(track.album, searchQuery)
  } }

  /** A song with its actions sheet; [showSource] adds the service to the subtitle. */
  fun LazyListScope.songs(
    list: List<Track>,
    keyPrefix: String,
    tagPrefix: String,
    showSource: Boolean,
    stagger: Boolean,
    onClick: (Track) -> Unit
  ) {
    itemsIndexed(
      items = list,
      key = { _, t -> "${keyPrefix}_${t.id}" },
      contentType = { _, _ -> "home_song_row" }
    ) { index, track ->
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
              isFavorite = track.id in favoriteSet,
              playlists = playlists,
              onToggleFavorite = { onToggleFavorite(track) },
              onPlayNext = { onPlayNext(track) },
              onAddToQueue = { onAddToQueue(track) },
              onAddToPlaylist = { id -> onAddToPlaylist(id, track) },
              onCreatePlaylist = { name -> onCreatePlaylist(name, track) },
              onDownloadOffline = { onDownloadOffline(track) },
              modifier = Modifier.trailingEdge()
            )
          }
        )
      }
    }
  }

  val listState = androidx.compose.foundation.lazy.rememberLazyListState()
  val rightListState = androidx.compose.foundation.lazy.rememberLazyListState()
  val cfg = LocalConfiguration.current
  val isTabletConsole = rememberWidthClass() == WidthClass.EXPANDED &&
    cfg.screenWidthDp > cfg.screenHeightDp && cfg.screenHeightDp >= 480

  if (isTabletConsole) {
    val colors = MaterialTheme.colorScheme
    val cardShape = SquircleShape(18.dp)
    val barInset = LocalBarInset.current
    val resumeTrack = currentTrack?.takeIf { t -> !isPlaying && resume?.trackId == t.id && !t.isDemo }
    val resumeAt = resume

    Column(
      modifier = modifier
        .fillMaxSize()
        .padding(start = Grid.margin, end = Grid.margin, bottom = barInset + Grid.u12),
      verticalArrangement = Arrangement.spacedBy(Grid.u12)
    ) {
      SearchField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = stringResource(R.string.home_search_placeholder),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("home_search_input")
      )

      if (searchQuery.isBlank()) {
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Grid.u12)
        ) {
          // Left Main Pane (~58%): "Suas músicas" + Demo tracks inside a Studio Console Squircle card.
          LazyColumn(
            state = listState,
            modifier = Modifier
              .weight(0.58f)
              .fillMaxHeight()
              .clip(cardShape)
              .background(colors.surfaceContainer)
              .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape),
            contentPadding = PaddingValues(top = Grid.u6, bottom = Grid.u12)
          ) {
            item(key = "mine_header") {
              SectionHeader(stringResource(R.string.home_mine_header), modifier = Modifier.padding(top = Grid.u6, bottom = Grid.u6))
            }
            if (myTracks.isEmpty()) {
              item(key = "mine_empty") {
                EmptyState(
                  icon = Icons.Rounded.LibraryMusic,
                  title = stringResource(R.string.home_mine_empty_title),
                  message = stringResource(R.string.home_mine_empty_message),
                  actionLabel = stringResource(R.string.home_mine_empty_action),
                  onAction = onOpenLibrary,
                  modifier = Modifier.padding(horizontal = Grid.margin).testTag("home_empty")
                )
              }
            }
            songs(myTracks, "mine", "lib_track", showSource = true, stagger = true, onClick = onPlayTrack)
            if (demoTracks.isNotEmpty()) {
              item(key = "demo_header") {
                DemoToggle(
                  count = demoTracks.size,
                  expanded = showDemos,
                  onToggle = { showDemos = !showDemos },
                  modifier = Modifier.padding(top = Grid.u12)
                )
              }
              if (showDemos) {
                songs(demoTracks, "demo", "demo_track", showSource = false, stagger = false, onClick = onPlayTrack)
              }
            }
          }

          // Right Studio Rack (~42%): 2 Squircle blocks (Block 1 = Continuar ouvindo / Tocadas recentemente; Block 2 = Catálogos Online).
          Column(
            modifier = Modifier.weight(0.42f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Grid.u12)
          ) {
            if ((resumeTrack != null && resumeAt != null) || recent.isNotEmpty()) {
              val resumeListState = androidx.compose.foundation.lazy.rememberLazyListState()
              LaunchedEffect(resumeTrack?.id) {
                if (resumeTrack != null) resumeListState.scrollToItem(0)
              }
              LazyColumn(
                state = resumeListState,
                modifier = Modifier
                  .weight(0.48f)
                  .fillMaxWidth()
                  .clip(cardShape)
                  .background(colors.surfaceContainer)
                  .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape),
                contentPadding = PaddingValues(top = Grid.u6, bottom = Grid.u12)
              ) {
                if (resumeTrack != null && resumeAt != null) {
                  item(key = "resume_header") {
                    SectionHeader(stringResource(R.string.home_resume_header), modifier = Modifier.padding(top = Grid.u6, bottom = Grid.u6))
                  }
                  songs(listOf(resumeTrack), "resume", "resume_track", showSource = true, stagger = false, onClick = { onResume() })
                  item(key = "resume_position") {
                    Text(
                      text = resumeAt.durationMs?.let { stringResource(R.string.home_resume_position_of, clock(resumeAt.positionMs), clock(it)) }
                        ?: stringResource(R.string.home_resume_position, clock(resumeAt.positionMs)),
                      style = GridText.Small,
                      color = colors.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u6).testTag("resume_position")
                    )
                  }
                }
                if (recent.isNotEmpty()) {
                  item(key = "recent_header") {
                    SectionHeader(stringResource(R.string.home_recent_header), modifier = Modifier.padding(top = Grid.u6, bottom = Grid.u6))
                  }
                  songs(recent, "recent", "recent_track", showSource = true, stagger = false, onClick = onPlayTrack)
                }
              }
            }
            LazyColumn(
              state = rightListState,
              modifier = Modifier
                .weight(0.52f)
                .fillMaxWidth()
                .clip(cardShape)
                .background(colors.surfaceContainer)
                .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape),
              contentPadding = PaddingValues(bottom = Grid.u12)
            ) {
              item(key = "trending") {
                CatalogSection(
                  title = stringResource(R.string.home_trending_title),
                  service = "Audius",
                  state = trending,
                  onRetry = onRetryTrending,
                  onPlay = onPlayTrending,
                  tagPrefix = "trending",
                  currentTrack = currentTrack,
                  isPlaying = isPlaying
                )
              }
              item(key = "jamendo_popular") {
                CatalogSection(
                  title = stringResource(R.string.home_jamendo_popular_title),
                  service = "Jamendo",
                  state = jamendoPopular,
                  onRetry = onRetryJamendoPopular,
                  onPlay = onPlayJamendoPopular,
                  tagPrefix = "jamendo_popular",
                  currentTrack = currentTrack,
                  isPlaying = isPlaying
                )
              }
            }
          }
        }
      } else {
        // Two-pane search: Left = Local Library matches; Right = Online Audius + Jamendo matches.
        Row(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(Grid.u12)
        ) {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .weight(0.5f)
              .fillMaxHeight()
              .clip(cardShape)
              .background(colors.surfaceContainer)
              .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape),
            contentPadding = PaddingValues(bottom = Grid.u12)
          ) {
            item(key = "search_lib_header") { ResultsHeader(stringResource(R.string.home_search_library_header), searching = false) }
            if (searchMatches.isEmpty()) {
              item(key = "search_lib_empty") { Note(stringResource(R.string.home_search_library_empty, searchQuery)) }
            }
            songs(searchMatches, "lib", "lib_track", showSource = true, stagger = false, onClick = onPlayTrack)
          }
          LazyColumn(
            state = rightListState,
            modifier = Modifier
              .weight(0.5f)
              .fillMaxHeight()
              .clip(cardShape)
              .background(colors.surfaceContainer)
              .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape),
            contentPadding = PaddingValues(bottom = Grid.u12)
          ) {
            item(key = "audius_header") { ResultsHeader("Audius", searching = audiusSearching) }
            songs(audiusResults, "audius_result", "audius_result", showSource = false, stagger = true, onClick = onPlayAudiusResult)
            if (audiusResults.isEmpty() && !audiusSearching) {
              item(key = "audius_empty") {
                if (audiusOffline) {
                  EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.home_offline_title, "Audius"),
                    message = stringResource(R.string.home_offline_message),
                    actionLabel = stringResource(R.string.home_retry),
                    onAction = { onSearchAudiusOnline(searchQuery) },
                    modifier = Modifier.padding(horizontal = Grid.margin).testTag("audius_offline")
                  )
                } else {
                  Note(stringResource(R.string.home_nothing_found, "Audius"))
                }
              }
            }
            item(key = "jamendo_header") { ResultsHeader("Jamendo", searching = jamendo.searching) }
            songs(jamendo.results, "jamendo_result", "jamendo_result", showSource = false, stagger = true, onClick = onPlayJamendoResult)
            if (jamendo.results.isEmpty() && !jamendo.searching) {
              item(key = "jamendo_empty") {
                when {
                  jamendo.notConfigured -> Note(stringResource(R.string.home_jamendo_missing_key), Modifier.testTag("jamendo_not_configured"))
                  jamendo.offline -> EmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(R.string.home_offline_title, "Jamendo"),
                    message = stringResource(R.string.home_offline_message),
                    actionLabel = stringResource(R.string.home_retry),
                    onAction = { onSearchJamendoOnline(searchQuery) },
                    modifier = Modifier.padding(horizontal = Grid.margin).testTag("jamendo_offline")
                  )
                  else -> Note(stringResource(R.string.home_nothing_found, "Jamendo"), Modifier.testTag("jamendo_empty"))
                }
              }
            }
          }
        }
      }
    }
    return
  }

  val colors = MaterialTheme.colorScheme
  val cardShape = SquircleShape(18.dp)
  val cardTopShape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
  val cardBottomShape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
  val isTabletPortrait = rememberWidthClass() != WidthClass.COMPACT

  fun LazyListScope.cardSongs(
    list: List<Track>,
    keyPrefix: String,
    tagPrefix: String,
    showSource: Boolean,
    stagger: Boolean,
    roundLastBottom: Boolean,
    onClick: (Track) -> Unit
  ) {
    itemsIndexed(
      items = list,
      key = { _, t -> "${keyPrefix}_${t.id}" },
      contentType = { _, _ -> "home_card_song_row" }
    ) { index, track ->
      val isLast = index == list.lastIndex && roundLastBottom
      val rowShape = if (isLast) cardBottomShape else androidx.compose.ui.graphics.RectangleShape
      Column(
        modifier = itemMotion(index, stagger)
          .padding(horizontal = Grid.margin)
          .clip(rowShape)
          .background(colors.surfaceContainer)
          .then(if (isLast) Modifier.padding(bottom = Grid.u6) else Modifier)
      ) {
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
              isFavorite = track.id in favoriteSet,
              playlists = playlists,
              onToggleFavorite = { onToggleFavorite(track) },
              onPlayNext = { onPlayNext(track) },
              onAddToQueue = { onAddToQueue(track) },
              onAddToPlaylist = { id -> onAddToPlaylist(id, track) },
              onCreatePlaylist = { name -> onCreatePlaylist(name, track) },
              onDownloadOffline = { onDownloadOffline(track) },
              modifier = Modifier.trailingEdge()
            )
          }
        )
      }
    }
  }

  LazyColumn(
    state = listState,
    modifier = modifier.fillMaxSize().topScrollFade(listState),
    contentPadding = PaddingValues(top = Grid.u6, bottom = LocalBarInset.current + Grid.u24)
  ) {
    item(key = "search") {
      SearchField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        placeholder = stringResource(R.string.home_search_placeholder),
        modifier = Modifier
          .padding(horizontal = Grid.margin)
          .testTag("home_search_input")
      )
    }

    fun LazyListScope.onlineSections() {
      item(key = "trending") {
        Box(
          modifier = Modifier
            .padding(start = Grid.margin, end = Grid.margin, top = Grid.u12)
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surfaceContainer)
            .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape)
            .padding(bottom = Grid.u12)
        ) {
          CatalogSection(
            title = stringResource(R.string.home_trending_title),
            service = "Audius",
            state = trending,
            onRetry = onRetryTrending,
            onPlay = onPlayTrending,
            tagPrefix = "trending",
            currentTrack = currentTrack,
            isPlaying = isPlaying
          )
        }
      }
      item(key = "jamendo_popular") {
        Box(
          modifier = Modifier
            .padding(start = Grid.margin, end = Grid.margin, top = Grid.u12)
            .fillMaxWidth()
            .clip(cardShape)
            .background(colors.surfaceContainer)
            .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape)
            .padding(bottom = Grid.u12)
        ) {
          CatalogSection(
            title = stringResource(R.string.home_jamendo_popular_title),
            service = "Jamendo",
            state = jamendoPopular,
            onRetry = onRetryJamendoPopular,
            onPlay = onPlayJamendoPopular,
            tagPrefix = "jamendo_popular",
            currentTrack = currentTrack,
            isPlaying = isPlaying
          )
        }
      }
    }

    fun LazyListScope.mySections() {
      // Only for the track that is loaded and paused: tapping it continues exactly where it stopped.
      val resumeTrack = currentTrack?.takeIf { t -> !isPlaying && resume?.trackId == t.id && !t.isDemo }
      val resumeAt = resume
      val hasResume = resumeTrack != null && resumeAt != null
      val hasRecent = recent.isNotEmpty()

      if (isTabletPortrait && hasResume && hasRecent) {
        // Tablet Portrait 2-column top deck: Continuar ouvindo (left) | Tocadas recentemente (right).
        item(key = "portrait_top_deck") {
          Row(
            modifier = Modifier
              .padding(start = Grid.margin, end = Grid.margin, top = Grid.u12)
              .fillMaxWidth()
              .height(androidx.compose.foundation.layout.IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(Grid.u12)
          ) {
            Column(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(cardShape)
                .background(colors.surfaceContainer)
                .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape)
                .padding(vertical = Grid.u6)
            ) {
              SectionHeader(stringResource(R.string.home_resume_header), modifier = Modifier.padding(top = Grid.u6, bottom = Grid.u6))
              TrackRow(
                track = resumeTrack!!,
                subtitle = trackSubtitle(resumeTrack, true),
                onClick = { onResume() },
                isCurrent = currentTrack?.id == resumeTrack.id,
                isPlaying = isPlaying,
                modifier = Modifier.opensPlayer().testTag("resume_track_${resumeTrack.id}"),
                trailing = {
                  TrackActionsMenu(
                    track = resumeTrack,
                    isFavorite = resumeTrack.id in favoriteSet,
                    playlists = playlists,
                    onToggleFavorite = { onToggleFavorite(resumeTrack) },
                    onPlayNext = { onPlayNext(resumeTrack) },
                    onAddToQueue = { onAddToQueue(resumeTrack) },
                    onAddToPlaylist = { id -> onAddToPlaylist(id, resumeTrack) },
                    onCreatePlaylist = { name -> onCreatePlaylist(name, resumeTrack) },
                    onDownloadOffline = { onDownloadOffline(resumeTrack) },
                    modifier = Modifier.trailingEdge()
                  )
                }
              )
              Text(
                text = resumeAt!!.durationMs?.let { stringResource(R.string.home_resume_position_of, clock(resumeAt.positionMs), clock(it)) }
                  ?: stringResource(R.string.home_resume_position, clock(resumeAt.positionMs)),
                style = GridText.Small,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u6).testTag("resume_position")
              )
            }
            Column(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(cardShape)
                .background(colors.surfaceContainer)
                .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape)
                .padding(vertical = Grid.u6)
            ) {
              SectionHeader(stringResource(R.string.home_recent_header), modifier = Modifier.padding(top = Grid.u6, bottom = Grid.u6))
              recent.take(3).forEachIndexed { idx, track ->
                if (idx > 0) TrackDivider()
                TrackRow(
                  track = track,
                  subtitle = trackSubtitle(track, true),
                  onClick = { onPlayTrack(track) },
                  isCurrent = currentTrack?.id == track.id,
                  isPlaying = isPlaying,
                  modifier = Modifier.opensPlayer().testTag("recent_track_${track.id}"),
                  trailing = {
                    TrackActionsMenu(
                      track = track,
                      isFavorite = track.id in favoriteSet,
                      playlists = playlists,
                      onToggleFavorite = { onToggleFavorite(track) },
                      onPlayNext = { onPlayNext(track) },
                      onAddToQueue = { onAddToQueue(track) },
                      onAddToPlaylist = { id -> onAddToPlaylist(id, track) },
                      onCreatePlaylist = { name -> onCreatePlaylist(name, track) },
                      onDownloadOffline = { onDownloadOffline(track) },
                      modifier = Modifier.trailingEdge()
                    )
                  }
                )
              }
            }
          }
        }
      } else {
        if (hasResume) {
          item(key = "resume_card") {
            Column(
              modifier = Modifier
                .padding(start = Grid.margin, end = Grid.margin, top = Grid.u12)
                .fillMaxWidth()
                .clip(cardShape)
                .background(colors.surfaceContainer)
                .border(Grid.hairline, colors.onSurface.copy(alpha = 0.08f), cardShape)
                .padding(vertical = Grid.u6)
            ) {
              SectionHeader(stringResource(R.string.home_resume_header), modifier = Modifier.padding(top = Grid.u6, bottom = Grid.u6))
              TrackRow(
                track = resumeTrack!!,
                subtitle = trackSubtitle(resumeTrack, true),
                onClick = { onResume() },
                isCurrent = currentTrack?.id == resumeTrack.id,
                isPlaying = isPlaying,
                modifier = Modifier.opensPlayer().testTag("resume_track_${resumeTrack.id}"),
                trailing = {
                  TrackActionsMenu(
                    track = resumeTrack,
                    isFavorite = resumeTrack.id in favoriteSet,
                    playlists = playlists,
                    onToggleFavorite = { onToggleFavorite(resumeTrack) },
                    onPlayNext = { onPlayNext(resumeTrack) },
                    onAddToQueue = { onAddToQueue(resumeTrack) },
                    onAddToPlaylist = { id -> onAddToPlaylist(id, resumeTrack) },
                    onCreatePlaylist = { name -> onCreatePlaylist(name, resumeTrack) },
                    onDownloadOffline = { onDownloadOffline(resumeTrack) },
                    modifier = Modifier.trailingEdge()
                  )
                }
              )
              Text(
                text = resumeAt!!.durationMs?.let { stringResource(R.string.home_resume_position_of, clock(resumeAt.positionMs), clock(it)) }
                  ?: stringResource(R.string.home_resume_position, clock(resumeAt.positionMs)),
                style = GridText.Small,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u6).testTag("resume_position")
              )
            }
          }
        }

        if (hasRecent) {
          item(key = "recent_header") {
            Box(
              modifier = Modifier
                .padding(start = Grid.margin, end = Grid.margin, top = Grid.u12)
                .fillMaxWidth()
                .clip(cardTopShape)
                .background(colors.surfaceContainer)
            ) {
              SectionHeader(stringResource(R.string.home_recent_header), modifier = Modifier.padding(top = Grid.u12, bottom = Grid.u6))
            }
          }
          cardSongs(recent, "recent", "recent_track", showSource = true, stagger = false, roundLastBottom = true, onClick = onPlayTrack)
        }
      }

      val mineHasBottomContent = myTracks.isNotEmpty() || demoTracks.isNotEmpty()
      item(key = "mine_header") {
        Box(
          modifier = Modifier
            .padding(start = Grid.margin, end = Grid.margin, top = Grid.u12)
            .fillMaxWidth()
            .clip(if (mineHasBottomContent) cardTopShape else cardShape)
            .background(colors.surfaceContainer)
        ) {
          SectionHeader(stringResource(R.string.home_mine_header), modifier = Modifier.padding(top = Grid.u12, bottom = Grid.u6))
        }
      }
      if (myTracks.isEmpty()) {
        item(key = "mine_empty") {
          Box(
            modifier = Modifier
              .padding(horizontal = Grid.margin)
              .fillMaxWidth()
              .clip(if (demoTracks.isEmpty()) cardBottomShape else androidx.compose.ui.graphics.RectangleShape)
              .background(colors.surfaceContainer)
              .padding(bottom = Grid.u12)
          ) {
            EmptyState(
              icon = Icons.Rounded.LibraryMusic,
              title = stringResource(R.string.home_mine_empty_title),
              message = stringResource(R.string.home_mine_empty_message),
              actionLabel = stringResource(R.string.home_mine_empty_action),
              onAction = onOpenLibrary,
              modifier = Modifier.padding(horizontal = Grid.margin).testTag("home_empty")
            )
          }
        }
      }
      cardSongs(myTracks, "mine", "lib_track", showSource = true, stagger = true, roundLastBottom = demoTracks.isEmpty(), onClick = onPlayTrack)

      // The short synthesized tracks, folded away under one row inside the same virtualised card.
      if (demoTracks.isNotEmpty()) {
        item(key = "demo_header") {
          Box(
            modifier = Modifier
              .padding(horizontal = Grid.margin)
              .fillMaxWidth()
              .clip(if (showDemos) androidx.compose.ui.graphics.RectangleShape else cardBottomShape)
              .background(colors.surfaceContainer)
              .padding(bottom = if (showDemos) 0.dp else Grid.u6)
          ) {
            DemoToggle(
              count = demoTracks.size,
              expanded = showDemos,
              onToggle = { showDemos = !showDemos },
              modifier = Modifier.padding(top = Grid.u6)
            )
          }
        }
        if (showDemos) {
          cardSongs(demoTracks, "demo", "demo_track", showSource = false, stagger = false, roundLastBottom = true, onClick = onPlayTrack)
        }
      }
    }

    if (searchQuery.isBlank()) {
      if (musicFirst) { mySections(); onlineSections() } else { onlineSections(); mySections() }
    } else {
      // While searching: the library first, then the same query on each service. All of it plays here.
      item(key = "search_lib_header") { ResultsHeader(stringResource(R.string.home_search_library_header), searching = false) }
      if (searchMatches.isEmpty()) {
        item(key = "search_lib_empty") { Note(stringResource(R.string.home_search_library_empty, searchQuery)) }
      }
      songs(searchMatches, "lib", "lib_track", showSource = true, stagger = false, onClick = onPlayTrack)

      item(key = "audius_header") { ResultsHeader("Audius", searching = audiusSearching) }
      songs(audiusResults, "audius_result", "audius_result", showSource = false, stagger = true, onClick = onPlayAudiusResult)
      if (audiusResults.isEmpty() && !audiusSearching) {
        item(key = "audius_empty") {
          if (audiusOffline) {
            EmptyState(
              icon = Icons.Rounded.CloudOff,
              title = stringResource(R.string.home_offline_title, "Audius"),
              message = stringResource(R.string.home_offline_message),
              actionLabel = stringResource(R.string.home_retry),
              onAction = { onSearchAudiusOnline(searchQuery) },
              modifier = Modifier.padding(horizontal = Grid.margin).testTag("audius_offline")
            )
          } else {
            Note(stringResource(R.string.home_nothing_found, "Audius"))
          }
        }
      }

      item(key = "jamendo_header") { ResultsHeader("Jamendo", searching = jamendo.searching) }
      songs(jamendo.results, "jamendo_result", "jamendo_result", showSource = false, stagger = true, onClick = onPlayJamendoResult)
      if (jamendo.results.isEmpty() && !jamendo.searching) {
        item(key = "jamendo_empty") {
          when {
            jamendo.notConfigured -> Note(stringResource(R.string.home_jamendo_missing_key), Modifier.testTag("jamendo_not_configured"))
            jamendo.offline -> EmptyState(
              icon = Icons.Rounded.CloudOff,
              title = stringResource(R.string.home_offline_title, "Jamendo"),
              message = stringResource(R.string.home_offline_message),
              actionLabel = stringResource(R.string.home_retry),
              onAction = { onSearchJamendoOnline(searchQuery) },
              modifier = Modifier.padding(horizontal = Grid.margin).testTag("jamendo_offline")
            )
            else -> Note(stringResource(R.string.home_nothing_found, "Jamendo"), Modifier.testTag("jamendo_empty"))
          }
        }
      }
    }
  }
}

/**
 * A catalogue's row of Início ("Em alta no Audius", "Popular no Jamendo"): the covers, a skeleton
 * while they load, and what went wrong otherwise. Nothing is shown for a catalogue the build has no
 * key for, so Início never opens on an error the user cannot fix.
 */
@Composable
private fun CatalogSection(
  title: String,
  service: String,
  state: TrendingState,
  onRetry: () -> Unit,
  onPlay: (Track) -> Unit,
  tagPrefix: String,
  currentTrack: Track?,
  isPlaying: Boolean
) {
  if (state.notConfigured) return
  Column {
    SectionHeader(title, modifier = Modifier.padding(top = Grid.u24, bottom = Grid.u12))
    when {
      state.tracks.isEmpty() && state.loading -> TrendingSkeleton()
      state.tracks.isNotEmpty() -> TrendingRow(
        tracks = state.tracks,
        currentTrack = currentTrack,
        isPlaying = isPlaying,
        onPlay = onPlay,
        tagPrefix = tagPrefix
      )
      state.offline -> EmptyState(
        icon = Icons.Rounded.CloudOff,
        title = stringResource(R.string.home_offline_title, service),
        message = stringResource(R.string.home_offline_catalog_message),
        actionLabel = stringResource(R.string.home_retry),
        onAction = onRetry,
        modifier = Modifier.padding(horizontal = Grid.margin).testTag("${tagPrefix}_offline")
      )
      !state.loading -> Note(stringResource(R.string.home_catalog_empty, service))
    }
  }
}

/** How long a search runs before its spinner shows. */
private const val SPINNER_DELAY_MS = 1500L

/** A section title over search results, with a small spinner while that service is still answering. */
@Composable
private fun ResultsHeader(title: String, searching: Boolean) {
  val searchingDescription = stringResource(R.string.home_searching)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = Grid.u24, bottom = Grid.u6, end = Grid.margin),
    verticalAlignment = Alignment.CenterVertically
  ) {
    SectionHeader(title, modifier = Modifier.weight(1f))
    // A quick answer never flashes a spinner: it shows only once the search takes a while.
    var slow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(searching) {
      slow = false
      if (searching) {
        kotlinx.coroutines.delay(SPINNER_DELAY_MS)
        slow = true
      }
    }
    if (searching && slow) {
      CircularProgressIndicator(
        modifier = Modifier
          .size(Grid.icon)
          .semantics { stateDescription = searchingDescription },
        strokeWidth = Grid.hairline * 2,
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
    style = GridText.Small,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(horizontal = Grid.margin, vertical = Grid.u6)
  )
}

/** The row that folds the demo tracks in and out, announced with its state. */
@Composable
private fun DemoToggle(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
  val toggleState = stringResource(if (expanded) R.string.home_demo_state_open else R.string.home_demo_state_closed)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = Grid.u48)
      .highlightClickable(role = Role.Button, onClickLabel = stringResource(if (expanded) R.string.home_demo_hide else R.string.home_demo_show), onClick = onToggle)
      .semantics { stateDescription = toggleState }
      .padding(horizontal = Grid.margin, vertical = Grid.u6)
      .testTag("home_demo_toggle"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(stringResource(R.string.home_demo_title), style = GridText.Body, color = MaterialTheme.colorScheme.onSurface)
      Text(
        pluralStringResource(R.plurals.home_demo_summary, count, count),
        style = GridText.Small,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Spacer(Modifier.width(Grid.u12))
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
  onPlay: (Track) -> Unit,
  tagPrefix: String = "trending"
) {
  val state = rememberLazyListState()
  LazyRow(
    state = state,
    flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
    contentPadding = PaddingValues(horizontal = Grid.margin),
    horizontalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    itemsIndexed(tracks.take(12), key = { _, t -> "${tagPrefix}_${t.id}" }) { index, track ->
      val isCurrent = currentTrack?.id == track.id
      val playLabel = stringResource(R.string.home_play_track, track.title)
      Column(
        modifier = Modifier
          .width(TileWidth)
          .listEntrance(index)
          .opensPlayer()
          .pressClickable(pressedScale = 0.97f, onClickLabel = playLabel) { onPlay(track) }
          .testTag("${tagPrefix}_track_${track.id}")
      ) {
        TrackArtwork(
          track = track,
          shape = Grid.r12,
          iconSize = Grid.u48,
          playing = isCurrent && isPlaying,
          modifier = Modifier.size(TileWidth)
        )
        Spacer(Modifier.height(Grid.u6))
        Text(
          text = track.title,
          style = GridText.Small.copy(fontWeight = FontWeight.SemiBold),
          color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = track.artist,
          style = GridText.Small,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (track.genre.isNotBlank()) {
          Text(
            text = track.genre,
            style = GridText.Small,
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
  val loadingDescription = stringResource(R.string.home_loading)
  val fill = MaterialTheme.colorScheme.surfaceContainer
  Row(
    horizontalArrangement = Arrangement.spacedBy(Grid.u12),
    // Laid out at full size and cut by the screen edge, as the real carousel is.
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentWidth(Alignment.Start, unbounded = true)
      .padding(horizontal = Grid.margin)
      .semantics { stateDescription = loadingDescription }
      .testTag("trending_loading")
  ) {
    repeat(3) {
      Column(modifier = Modifier.width(TileWidth)) {
        Box(Modifier.size(TileWidth).clip(Grid.r12).background(fill))
        Spacer(Modifier.height(Grid.u6))
        Box(Modifier.width(Grid.u96).height(Grid.u12).clip(Grid.r6).background(fill))
        Spacer(Modifier.height(Grid.u6))
        Box(Modifier.width(Grid.u48 + Grid.u24).height(Grid.u12).clip(Grid.r6).background(fill))
      }
    }
  }
}
