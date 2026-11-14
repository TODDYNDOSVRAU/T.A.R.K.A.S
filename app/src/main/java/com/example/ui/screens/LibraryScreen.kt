package com.example.ui.screens

import com.example.ui.screens.library.rememberComputed
import com.example.ui.screens.library.LibraryActions
import com.example.ui.screens.library.LibraryState
import androidx.compose.runtime.setValue
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import com.example.ui.components.TarkasAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.annotation.StringRes
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.library.AutoList
import com.example.data.library.AutoLists
import com.example.data.library.LibraryGroups
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.playback.LibrarySearch
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterChip
import com.example.ui.components.IconAction
import com.example.ui.components.NamePlaylistDialog
import com.example.ui.components.SearchField
import com.example.ui.components.TrackActionsMenu
import com.example.ui.screens.library.FolderPicker
import com.example.ui.screens.library.trackSubtitle
import com.example.ui.screens.library.trailingEdge
import com.example.ui.theme.AppText
import com.example.ui.theme.Space
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.sectionTransition
import com.example.ui.theme.subPageTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.screens.library.AutoListDetail
import com.example.ui.screens.library.AutoListsOverview
import com.example.ui.screens.library.EmptyLibrary
import com.example.ui.screens.library.GroupDetail
import com.example.ui.screens.library.GroupKeySeparator
import com.example.ui.screens.library.GroupsList
import com.example.ui.screens.library.Loading
import com.example.ui.screens.library.MusicPermissionPrompt
import com.example.ui.screens.library.NoMatch
import com.example.ui.screens.library.PlaylistDetail
import com.example.ui.screens.library.PlaylistsList
import com.example.ui.screens.library.SongOrderRow
import com.example.ui.screens.library.TrackList
import com.example.ui.screens.library.labelRes

private enum class LibrarySection(@StringRes val labelRes: Int) {
  SONGS(R.string.library_section_songs),
  ALBUMS(R.string.library_section_albums),
  ARTISTS(R.string.library_section_artists),
  GENRES(R.string.library_section_genres),
  PLAYLISTS(R.string.library_section_playlists),
  FAVORITES(R.string.library_section_favorites),
  AUTO(R.string.library_section_auto),
  QUEUE(R.string.library_section_queue)
}

/** The permission that reads the device's music on this Android version. */
private val MusicPermission: String get() = com.example.data.library.MusicPermission.name

@Composable
fun LibraryScreen(
  state: LibraryState,
  actions: LibraryActions,
  modifier: Modifier = Modifier
) {
  val playlists = state.playlists
  val favoriteTracks = state.favoriteTracks
  val savedTracks = state.savedTracks
  val allTracks = state.allTracks
  val deviceTracks = state.deviceTracks
  val deviceScanning = state.deviceScanning
  val hasMusicPermission = state.hasMusicPermission
  val songsPerFolder = state.songsPerFolder
  val folderRules = state.folderRules
  val queue = state.queue
  val currentTrack = state.currentTrack
  val isPlaying = state.isPlaying
  val upcoming = state.upcoming
  val initialSongOrder = state.initialSongOrder
  val playCounts = state.playCounts
  val openQueue = state.openQueue
  val onScanDevice = actions.onScanDevice
  val onSetFolderIncluded = actions.onSetFolderIncluded
  val onPlayFromList = actions.onPlayFromList
  val onShuffleList = actions.onShuffleList
  val onCreatePlaylist = actions.onCreatePlaylist
  val onAddLocalTrack = actions.onAddLocalTrack
  val onRenamePlaylist = actions.onRenamePlaylist
  val onDeletePlaylist = actions.onDeletePlaylist
  val onRemoveFromPlaylist = actions.onRemoveFromPlaylist
  val onToggleFavorite = actions.onToggleFavorite
  val onPlayNext = actions.onPlayNext
  val onAddToQueue = actions.onAddToQueue
  val onAddToPlaylist = actions.onAddToPlaylist
  val onCreatePlaylistWith = actions.onCreatePlaylistWith
  val onPlayQueueItem = actions.onPlayQueueItem
  val onRemoveQueueItem = actions.onRemoveQueueItem
  val onClearQueue = actions.onClearQueue
  val onSaveQueueAsPlaylist = actions.onSaveQueueAsPlaylist
  val onSongOrderChange = actions.onSongOrderChange
  val onNeedPlayCounts = actions.onNeedPlayCounts
  val onLoadPlayStats = actions.onLoadPlayStats
  val onMoveQueueItem = actions.onMoveQueueItem
  val onPlayUpcoming = actions.onPlayUpcoming
  val onQueueOpened = actions.onQueueOpened
  var section by rememberSaveable { mutableStateOf(LibrarySection.SONGS) }
  var songOrder by remember { mutableStateOf(initialSongOrder) }
  // Sorted off the main thread; null until the first result, and the previous order stays while the next is made.
  val sortedSongs = rememberComputed(deviceTracks, songOrder, playCounts) {
    com.example.data.playback.LibrarySort.sorted(deviceTracks, songOrder, playCounts)
  }
  LaunchedEffect(songOrder) { if (songOrder == com.example.data.playback.SongOrder.MOST_PLAYED) onNeedPlayCounts() }
  val favoriteIds = remember(favoriteTracks) { favoriteTracks.map { it.id }.toSet() }
  // Landscape leaves little height once the bars are on: the search and the order chips share a line.
  val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
  // Search inside Músicas and Favoritos: the sorted list is filtered, so the chosen order holds in the
  // result. The folded text of a song is made the first time a search needs it, not on every keystroke.
  var songQuery by rememberSaveable { mutableStateOf("") }
  var favoriteQuery by rememberSaveable { mutableStateOf("") }
  val songHaystacks = remember(deviceTracks) { HashMap<String, String>() }
  val shownSongs = remember(sortedSongs, songQuery) {
    LibrarySearch.filter(sortedSongs.orEmpty(), songQuery) { songHaystacks.getOrPut(it.id) { LibrarySearch.haystack(it) } }
  }
  val shownFavorites = remember(favoriteTracks, favoriteQuery) { LibrarySearch.filter(favoriteTracks, favoriteQuery) }
  var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
  // The automatic lists are built from the songs and their counters, read again when the section or one
  // of its lists opens and when the song that plays changes (a play may have just been counted).
  var openAutoName by rememberSaveable { mutableStateOf<String?>(null) }
  val openAuto = AutoList.entries.firstOrNull { it.name == openAutoName }
  var playStats by remember { mutableStateOf<Map<String, com.example.data.playback.TrackStats>>(emptyMap()) }
  LaunchedEffect(currentTrack?.id, section, openAuto) {
    if (section == LibrarySection.AUTO || openAuto != null) playStats = onLoadPlayStats()
  }
  val autoLists = remember(allTracks, playStats) { AutoLists.all(allTracks, playStats) }
  // Albums, artists and genres of the device's songs. An opened group is kept as a stack of keys (an
  // artist's album opens over the artist, and back returns to it); the keys are joined into one string
  // so the stack survives a rotation.
  val groups = rememberComputed(deviceTracks) {
    Triple(LibraryGroups.albums(deviceTracks), LibraryGroups.artists(deviceTracks), LibraryGroups.genres(deviceTracks))
  }
  val albumGroups = groups?.first.orEmpty()
  val artistGroups = groups?.second.orEmpty()
  val genreGroups = groups?.third.orEmpty()
  val groupsByKey = remember(albumGroups, artistGroups, genreGroups) {
    (albumGroups + artistGroups + genreGroups).associateBy { it.key }
  }
  var openGroupKeys by rememberSaveable { mutableStateOf("") }
  val openGroup = openGroupKeys.split(GroupKeySeparator).lastOrNull { it.isNotEmpty() }?.let { groupsByKey[it] }
  fun pushGroup(key: String) { openGroupKeys = if (openGroupKeys.isEmpty()) key else openGroupKeys + GroupKeySeparator + key }
  fun popGroup() { openGroupKeys = openGroupKeys.substringBeforeLast(GroupKeySeparator, missingDelimiterValue = "") }
  // Dialogs are saved by id, so a rotation keeps them open on the same playlist.
  var showNewPlaylist by rememberSaveable { mutableStateOf(false) }
  var showFolders by rememberSaveable { mutableStateOf(false) }
  var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
  var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

  // The player's queue button lands here on Fila, whatever section was open before.
  LaunchedEffect(openQueue) {
    if (openQueue) {
      openPlaylistId = null
      openAutoName = null
      openGroupKeys = ""
      section = LibrarySection.QUEUE
      onQueueOpened()
    }
  }

  // Imports an audio file from the device into the local library (system file picker). Reading the
  // name and the duration touches the provider and decodes the file header, so it runs off the main
  // thread.
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val audioFallbackName = stringResource(R.string.library_audio_fallback_name)
  val localLibraryName = stringResource(R.string.library_local_library)
  val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri != null) {
      val appContext = context.applicationContext
      scope.launch {
        val (name, durationMs) = withContext(Dispatchers.IO) { readAudioFile(appContext, uri, audioFallbackName) }
        // Falls back to 3 min only when the file has no duration in its metadata.
        onAddLocalTrack(name, localLibraryName, uri.toString(), if (durationMs > 0) durationMs else 180_000L)
      }
    }
  }

  // The device's music: asked for when the user opens it, read again each time Biblioteca opens, and
  // checked again on every resume, since it can be granted or revoked in the system settings.
  fun musicGranted() = com.example.data.library.MusicPermission.granted(context)
  var musicAllowed by remember { mutableStateOf(hasMusicPermission || musicGranted()) }
  // After a denial with "don't ask again" the system answers at once without a dialog, so the only
  // way left is the app's page in the system settings.
  var musicBlocked by rememberSaveable { mutableStateOf(false) }
  val musicPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    musicAllowed = granted
    if (granted) {
      musicBlocked = false
      onScanDevice()
    } else {
      musicBlocked = context.findActivity()?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, MusicPermission) } ?: false
    }
  }
  LaunchedEffect(Unit) { if (musicAllowed) onScanDevice() }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    val granted = musicGranted()
    if (granted && !musicAllowed) onScanDevice()
    musicAllowed = granted
    if (granted) musicBlocked = false
  }
  val openAppSettings = {
    runCatching {
      context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }
    Unit
  }

  // Tracks of the current catalog win over the saved snapshot (fresher data).
  val byId = savedTracks + allTracks.associateBy { it.id }
  val openPlaylist = playlists.firstOrNull { it.id == openPlaylistId }

  val reduced = rememberReducedMotion()
  // Back closes an open playlist before it leaves the tab.
  BackHandler(enabled = openPlaylist != null || openAuto != null || openGroup != null) {
    if (openPlaylist != null || openAuto != null) { openPlaylistId = null; openAutoName = null } else popGroup()
  }
  // The track menu of the rows in the group pages, the same as in Músicas.
  val rowMenu: @Composable RowScope.(Track) -> Unit = { track ->
    TrackActionsMenu(
      track = track,
      isFavorite = track.id in favoriteIds,
      playlists = playlists,
      onToggleFavorite = { onToggleFavorite(track) },
      onPlayNext = { onPlayNext(track) },
      onAddToQueue = { onAddToQueue(track) },
      onAddToPlaylist = { id -> onAddToPlaylist(id, track) },
      onCreatePlaylist = { name -> onCreatePlaylistWith(name, track) },
      modifier = Modifier.trailingEdge()
    )
  }

  // An open playlist pushes in over the sections like a sub-page; the sections slide between them.
  AnimatedContent(
    targetState = openPlaylist?.id ?: openAuto?.let { "auto:${it.name}" } ?: openGroup?.key,
    transitionSpec = { subPageTransition(forward = targetState != null, reduced = reduced) },
    label = "playlistOpen",
    modifier = modifier.fillMaxSize()
  ) { shownId ->
    val shownPlaylist = playlists.firstOrNull { it.id == shownId }
    val shownAuto = AutoList.entries.firstOrNull { "auto:${it.name}" == shownId }
    val shownGroup = shownId?.let { groupsByKey[it] }
    if (shownAuto != null) {
      val tracks = autoLists.getValue(shownAuto)
      AutoListDetail(
        list = shownAuto,
        tracks = tracks,
        currentTrack = currentTrack,
        isPlaying = isPlaying,
        onBack = { openAutoName = null },
        onPlay = { track -> onPlayFromList(track, tracks) },
        onShuffle = { onShuffleList(tracks) }
      )
    } else if (shownPlaylist == null && shownGroup != null) {
      val group = shownGroup
      GroupDetail(
        group = group,
        currentTrack = currentTrack,
        isPlaying = isPlaying,
        onBack = { popGroup() },
        onPlay = { track -> onPlayFromList(track, group.tracks) },
        onShuffle = { onShuffleList(group.tracks) },
        onOpenGroup = { key -> pushGroup(key) },
        trailing = rowMenu
      )
    } else if (shownPlaylist != null) {
      val tracks = shownPlaylist.trackIds.mapNotNull { byId[it] }
      PlaylistDetail(
        playlist = shownPlaylist,
        tracks = tracks,
        currentTrack = currentTrack,
        isPlaying = isPlaying,
        onBack = { openPlaylistId = null },
        onPlay = { track -> onPlayFromList(track, tracks) },
        onShuffle = { onShuffleList(tracks) },
        onRename = { renamingId = shownPlaylist.id },
        onDelete = { deletingId = shownPlaylist.id },
        onRemoveTrack = { trackId -> onRemoveFromPlaylist(shownPlaylist.id, trackId) }
      )
    } else {
      Column(modifier = Modifier.fillMaxSize()) {
        // Scrolls sideways, so the last chip is never cut by the screen edge at large font sizes.
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.page)
            .selectableGroup(),
          horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
          LibrarySection.entries.forEach { s ->
            FilterChip(
              label = if (s == LibrarySection.QUEUE && queue.isNotEmpty()) {
                stringResource(R.string.library_section_queue_count, queue.size)
              } else stringResource(s.labelRes),
              selected = section == s,
              onClick = { section = s },
              modifier = Modifier.testTag("library_chip_${s.name.lowercase()}")
            )
          }
        }

        AnimatedContent(
          targetState = section,
          transitionSpec = { sectionTransition(initialState.ordinal, targetState.ordinal, reduced) },
          label = "librarySection",
          modifier = Modifier.weight(1f)
        ) { shownSection ->
          when (shownSection) {
            LibrarySection.SONGS -> TrackList(
              tracks = if (musicAllowed) shownSongs else emptyList(),
              currentTrack = currentTrack,
              isPlaying = isPlaying,
              onPlay = { track -> onPlayFromList(track, shownSongs) },
              onShuffle = { onShuffleList(shownSongs) },
              header = {
                if (musicAllowed && (deviceTracks.isNotEmpty() || folderRules.isFiltering)) {
                  if (compact) {
                    item(key = "song_search") {
                      Row(
                        modifier = Modifier.padding(top = Space.s, bottom = Space.xs),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        SearchField(
                          value = songQuery,
                          onValueChange = { songQuery = it },
                          placeholder = stringResource(R.string.library_search_songs_hint),
                          modifier = Modifier
                            .weight(1f)
                            .padding(start = Space.page)
                            .testTag("library_search_songs")
                        )
                        SongOrderRow(
                          songOrder,
                          onChange = { songOrder = it; onSongOrderChange(it) },
                          foldersActive = folderRules.isFiltering,
                          onFolders = { showFolders = true },
                          modifier = Modifier.weight(1f)
                        )
                      }
                    }
                  } else {
                    item(key = "song_search") {
                      SearchField(
                        value = songQuery,
                        onValueChange = { songQuery = it },
                        placeholder = stringResource(R.string.library_search_songs_hint),
                        modifier = Modifier
                          .padding(horizontal = Space.page)
                          .padding(top = Space.s, bottom = Space.xs)
                          .testTag("library_search_songs")
                      )
                    }
                    item(key = "song_order") {
                      SongOrderRow(songOrder, onChange = { songOrder = it; onSongOrderChange(it) }, foldersActive = folderRules.isFiltering, onFolders = { showFolders = true })
                    }
                  }
                }
              },
              trailing = { track ->
                TrackActionsMenu(
                  track = track,
                  isFavorite = track.id in favoriteIds,
                  playlists = playlists,
                  onToggleFavorite = { onToggleFavorite(track) },
                  onPlayNext = { onPlayNext(track) },
                  onAddToQueue = { onAddToQueue(track) },
                  onAddToPlaylist = { id -> onAddToPlaylist(id, track) },
                  onCreatePlaylist = { name -> onCreatePlaylistWith(name, track) },
                  modifier = Modifier.trailingEdge()
                )
              },
              index = { com.example.data.playback.LibrarySort.section(it, songOrder) },
              subtitle = { t ->
                val n = playCounts[t.id] ?: 0
                if (songOrder == com.example.data.playback.SongOrder.MOST_PLAYED && n > 0) {
                  "${pluralStringResource(R.plurals.library_play_count, n, n)} · ${trackSubtitle(t)}"
                } else trackSubtitle(t)
              },
              empty = {
                when {
                  !musicAllowed -> MusicPermissionPrompt(
                    blocked = musicBlocked,
                    onAllow = { musicPermissionLauncher.launch(MusicPermission) },
                    onOpenSettings = openAppSettings
                  )
                  deviceScanning || (sortedSongs == null && deviceTracks.isNotEmpty()) -> Loading(stringResource(R.string.library_loading_songs))
                  deviceTracks.isEmpty() && songsPerFolder.isNotEmpty() -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.library_folders_all_out_title),
                    message = stringResource(R.string.library_folders_all_out_message),
                    modifier = Modifier.padding(horizontal = Space.page),
                    actionLabel = stringResource(R.string.library_folders_chip),
                    onAction = { showFolders = true }
                  )
                  deviceTracks.isNotEmpty() -> NoMatch(
                    message = stringResource(R.string.library_search_empty_songs, songQuery.trim()),
                    onClear = { songQuery = "" }
                  )
                  else -> EmptyLibrary(
                    onRefresh = onScanDevice,
                    onImport = { audioPickerLauncher.launch(arrayOf("audio/*")) }
                  )
                }
              }
            )

            LibrarySection.ALBUMS -> GroupsList(
              groups = albumGroups,
              kind = "albums",
              hintRes = R.string.library_search_albums_hint,
              emptyRes = R.string.library_albums_empty,
              ready = musicAllowed && !deviceScanning && groups != null,
              onOpen = { pushGroup(it.key) }
            )

            LibrarySection.ARTISTS -> GroupsList(
              groups = artistGroups,
              kind = "artists",
              hintRes = R.string.library_search_artists_hint,
              emptyRes = R.string.library_artists_empty,
              ready = musicAllowed && !deviceScanning && groups != null,
              onOpen = { pushGroup(it.key) }
            )

            LibrarySection.GENRES -> GroupsList(
              groups = genreGroups,
              kind = "genres",
              hintRes = R.string.library_search_genres_hint,
              emptyRes = R.string.library_genres_empty,
              ready = musicAllowed && !deviceScanning && groups != null,
              onOpen = { pushGroup(it.key) }
            )

            LibrarySection.PLAYLISTS -> PlaylistsList(
              playlists = playlists,
              byId = byId,
              onOpen = { openPlaylistId = it.id },
              onNew = { showNewPlaylist = true },
              onImportAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) }
            )

            LibrarySection.FAVORITES -> TrackList(
              tracks = shownFavorites,
              currentTrack = currentTrack,
              isPlaying = isPlaying,
              onPlay = { track -> onPlayFromList(track, shownFavorites) },
              onShuffle = { onShuffleList(shownFavorites) },
              header = {
                if (favoriteTracks.isNotEmpty()) item(key = "favorite_search") {
                  SearchField(
                    value = favoriteQuery,
                    onValueChange = { favoriteQuery = it },
                    placeholder = stringResource(R.string.library_search_favorites_hint),
                    modifier = Modifier
                      .padding(horizontal = Space.page)
                      .padding(top = Space.s, bottom = Space.xs)
                      .testTag("library_search_favorites")
                  )
                }
              },
              empty = {
                if (favoriteTracks.isNotEmpty()) {
                  NoMatch(
                    message = stringResource(R.string.library_search_empty_favorites, favoriteQuery.trim()),
                    onClear = { favoriteQuery = "" }
                  )
                } else {
                  EmptyState(
                    icon = Icons.Rounded.FavoriteBorder,
                    title = stringResource(R.string.library_favorites_empty_title),
                    message = stringResource(R.string.library_favorites_empty_message),
                    modifier = Modifier.padding(horizontal = Space.page)
                  )
                }
              },
              trailing = { track ->
                IconAction(
                  icon = Icons.Rounded.Favorite,
                  contentDescription = stringResource(R.string.library_favorite_remove, track.title),
                  onClick = { onToggleFavorite(track) },
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.trailingEdge()
                )
              }
            )

            LibrarySection.AUTO -> AutoListsOverview(
              lists = autoLists,
              byId = byId,
              onOpen = { openAutoName = it.name }
            )

            LibrarySection.QUEUE -> QueueList(
              queue = queue,
              upcoming = upcoming,
              onPlay = onPlayQueueItem,
              onRemove = onRemoveQueueItem,
              onMove = onMoveQueueItem,
              onClear = onClearQueue,
              onSaveAsPlaylist = onSaveQueueAsPlaylist,
              onPlayUpcoming = onPlayUpcoming
            )
          }
        }
      }
    }
  }

  if (showFolders) {
    FolderPicker(
      songsPerFolder = songsPerFolder,
      rules = folderRules,
      onSetIncluded = onSetFolderIncluded,
      onDismiss = { showFolders = false }
    )
  }

  if (showNewPlaylist) {
    NamePlaylistDialog(
      title = stringResource(R.string.library_new_playlist_title),
      confirmLabel = stringResource(R.string.library_create),
      initialName = "",
      onConfirm = { name -> onCreatePlaylist(name); showNewPlaylist = false },
      onDismiss = { showNewPlaylist = false }
    )
  }
  playlists.firstOrNull { it.id == renamingId }?.let { pl ->
    NamePlaylistDialog(
      title = stringResource(R.string.library_rename_playlist_title),
      confirmLabel = stringResource(R.string.library_save),
      initialName = pl.name,
      onConfirm = { name -> onRenamePlaylist(pl.id, name); renamingId = null },
      onDismiss = { renamingId = null }
    )
  }
  playlists.firstOrNull { it.id == deletingId }?.let { pl ->
    TarkasAlertDialog(
      onDismissRequest = { deletingId = null },
      title = { Text(stringResource(R.string.library_delete_title, pl.name), style = AppText.Title3) },
      text = {
        Text(
          if (pl.trackIds.isEmpty()) stringResource(R.string.library_delete_body_empty)
          else pluralStringResource(R.plurals.library_delete_body, pl.trackIds.size, pl.trackIds.size),
          style = AppText.Subhead
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDeletePlaylist(pl.id)
            deletingId = null
            openPlaylistId = null
          },
          modifier = Modifier.testTag("library_confirm_delete")
        ) { Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.library_cancel)) } }
    )
  }
}

/** Name and duration of a picked audio file. Blocking: call it on [Dispatchers.IO]. */
private fun readAudioFile(context: Context, uri: Uri, fallbackName: String): Pair<String, Long> {
  runCatching {
    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  var displayName = fallbackName
  runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
      val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
      if (nameIndex != -1 && cursor.moveToFirst()) {
        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { displayName = it }
      }
    }
  }
  var durationMs = 0L
  val retriever = MediaMetadataRetriever()
  try {
    retriever.setDataSource(context, uri)
    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
  } catch (_: Exception) {
  } finally {
    // Always released: the retriever holds a native decoder and the file open until then.
    runCatching { retriever.release() }
  }
  return displayName to durationMs
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
