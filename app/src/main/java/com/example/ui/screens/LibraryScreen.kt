package com.example.ui.screens

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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.annotation.StringRes
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.playback.LibrarySearch
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterChip
import com.example.ui.components.IconAction
import com.example.ui.components.NamePlaylistDialog
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SearchField
import com.example.ui.components.SubPageHeader
import com.example.ui.components.TextAction
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackRow
import com.example.ui.components.highlightClickable
import com.example.ui.screens.library.PlayShuffleRow
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
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.sectionTransition
import com.example.ui.theme.listEntrance
import com.example.ui.theme.subPageTransition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LibrarySection(@StringRes val labelRes: Int) {
  SONGS(R.string.library_section_songs),
  PLAYLISTS(R.string.library_section_playlists),
  FAVORITES(R.string.library_section_favorites),
  QUEUE(R.string.library_section_queue)
}

/** The name of a song order in the current language. */
@StringRes
private fun com.example.data.playback.SongOrder.labelRes(): Int = when (this) {
  com.example.data.playback.SongOrder.TITLE -> R.string.library_order_title
  com.example.data.playback.SongOrder.ARTIST -> R.string.library_order_artist
  com.example.data.playback.SongOrder.ALBUM -> R.string.library_order_album
  com.example.data.playback.SongOrder.RECENT -> R.string.library_order_recent
  com.example.data.playback.SongOrder.MOST_PLAYED -> R.string.library_order_most_played
}

/** Artwork of a playlist row. */
private val PlaylistArtworkSize = 56.dp

/** The permission that reads the device's music on this Android version. */
private val MusicPermission: String =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
  else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun LibraryScreen(
  playlists: List<Playlist>,
  favoriteTracks: List<Track>,
  savedTracks: Map<String, Track>,
  allTracks: List<Track>,
  deviceTracks: List<Track> = emptyList(),
  deviceScanning: Boolean = false,
  hasMusicPermission: Boolean = false,
  onScanDevice: () -> Unit = {},
  queue: List<Track>,
  currentTrack: Track?,
  isPlaying: Boolean,
  onPlayFromList: (Track, List<Track>) -> Unit,
  onShuffleList: (List<Track>) -> Unit,
  onCreatePlaylist: (String) -> Unit,
  onAddLocalTrack: (String, String, String, Long) -> Unit,
  onRenamePlaylist: (String, String) -> Unit,
  onDeletePlaylist: (String) -> Unit,
  onRemoveFromPlaylist: (String, String) -> Unit,
  onToggleFavorite: (Track) -> Unit,
  onPlayQueueItem: (Int) -> Unit,
  onRemoveQueueItem: (Int) -> Unit,
  onClearQueue: () -> Unit,
  upcoming: List<Track> = emptyList(),
  initialSongOrder: com.example.data.playback.SongOrder = com.example.data.playback.SongOrder.TITLE,
  onSongOrderChange: (com.example.data.playback.SongOrder) -> Unit = {},
  /** Plays counted on this device per track id, for "Mais tocadas"; [onNeedPlayCounts] loads them. */
  playCounts: Map<String, Int> = emptyMap(),
  onNeedPlayCounts: () -> Unit = {},
  onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
  onPlayUpcoming: (Track) -> Unit = {},
  openQueue: Boolean = false,
  onQueueOpened: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var section by rememberSaveable { mutableStateOf(LibrarySection.SONGS) }
  var songOrder by remember { mutableStateOf(initialSongOrder) }
  // Sorted off the main thread would need a loading state; collation keys make this fast enough.
  val sortedSongs = remember(deviceTracks, songOrder, playCounts) {
    com.example.data.playback.LibrarySort.sorted(deviceTracks, songOrder, playCounts)
  }
  LaunchedEffect(songOrder) { if (songOrder == com.example.data.playback.SongOrder.MOST_PLAYED) onNeedPlayCounts() }
  // Search inside Músicas and Favoritos: the sorted list is filtered, so the chosen order holds in the
  // result. The folded text of a song is made the first time a search needs it, not on every keystroke.
  var songQuery by rememberSaveable { mutableStateOf("") }
  var favoriteQuery by rememberSaveable { mutableStateOf("") }
  val songHaystacks = remember(deviceTracks) { HashMap<String, String>() }
  val shownSongs = remember(sortedSongs, songQuery) {
    LibrarySearch.filter(sortedSongs, songQuery) { songHaystacks.getOrPut(it.id) { LibrarySearch.haystack(it) } }
  }
  val shownFavorites = remember(favoriteTracks, favoriteQuery) { LibrarySearch.filter(favoriteTracks, favoriteQuery) }
  var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
  // Dialogs are saved by id, so a rotation keeps them open on the same playlist.
  var showNewPlaylist by rememberSaveable { mutableStateOf(false) }
  var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
  var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

  // The player's queue button lands here on Fila, whatever section was open before.
  LaunchedEffect(openQueue) {
    if (openQueue) {
      openPlaylistId = null
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
  val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri != null) {
      val appContext = context.applicationContext
      scope.launch {
        val (name, durationMs) = withContext(Dispatchers.IO) { readAudioFile(appContext, uri, audioFallbackName) }
        // Falls back to 3 min only when the file has no duration in its metadata.
        onAddLocalTrack(name, "Biblioteca Local", uri.toString(), if (durationMs > 0) durationMs else 180_000L)
        // "Biblioteca Local" is kept as is: the repository stores and compares that literal.
      }
    }
  }

  // The device's music: asked for when the user opens it, read again each time Biblioteca opens, and
  // checked again on every resume, since it can be granted or revoked in the system settings.
  fun musicGranted() = ContextCompat.checkSelfPermission(context, MusicPermission) == PackageManager.PERMISSION_GRANTED
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
  BackHandler(enabled = openPlaylist != null) { openPlaylistId = null }

  // An open playlist pushes in over the sections like a sub-page; the sections slide between them.
  AnimatedContent(
    targetState = openPlaylist?.id,
    transitionSpec = { subPageTransition(forward = targetState != null, reduced = reduced) },
    label = "playlistOpen",
    modifier = modifier.fillMaxSize()
  ) { shownId ->
    val shownPlaylist = playlists.firstOrNull { it.id == shownId }
    if (shownPlaylist != null) {
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
                if (musicAllowed && deviceTracks.isNotEmpty()) {
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
                    SongOrderRow(songOrder, onChange = { songOrder = it; onSongOrderChange(it) })
                  }
                }
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
                  deviceScanning -> Loading(stringResource(R.string.library_loading_songs))
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

            LibrarySection.QUEUE -> QueueList(
              queue = queue,
              upcoming = upcoming,
              onPlay = onPlayQueueItem,
              onRemove = onRemoveQueueItem,
              onMove = onMoveQueueItem,
              onClear = onClearQueue,
              onPlayUpcoming = onPlayUpcoming
            )
          }
        }
      }
    }
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
    AlertDialog(
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

/** Asks for the device's music in plain words, with the one action that can still grant it. */
@Composable
private fun MusicPermissionPrompt(blocked: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page, vertical = Space.xl),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(
      stringResource(R.string.library_permission_title),
      style = AppText.Title3,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.size(Space.s))
    Text(
      stringResource(if (blocked) R.string.library_permission_blocked else R.string.library_permission_request),
      style = AppText.Subhead,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(Modifier.size(Space.l))
    if (blocked) {
      PrimaryButton(stringResource(R.string.library_permission_open_settings), onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().testTag("library_open_settings"))
      // The detection cannot tell "don't ask again" from a dismissed dialog, so asking stays possible.
      TextAction(stringResource(R.string.library_permission_ask_again), onClick = onAllow)
    } else {
      PrimaryButton(stringResource(R.string.library_permission_allow), onClick = onAllow, modifier = Modifier.fillMaxWidth().testTag("library_allow_music"))
    }
  }
}

/** A search that found nothing: says so, and the one thing to do next. */
@Composable
private fun NoMatch(message: String, onClear: () -> Unit) {
  EmptyState(
    icon = Icons.Rounded.SearchOff,
    title = stringResource(R.string.library_search_empty_title),
    message = message,
    modifier = Modifier.padding(horizontal = Space.page),
    actionLabel = stringResource(R.string.library_search_clear),
    onAction = onClear
  )
}

/** The device has no music the app can see: refresh the scan, or import one file by hand. */
@Composable
private fun EmptyLibrary(onRefresh: () -> Unit, onImport: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    EmptyState(
      icon = Icons.Rounded.LibraryMusic,
      title = stringResource(R.string.library_empty_songs_title),
      message = stringResource(R.string.library_empty_songs_message),
      modifier = Modifier.padding(horizontal = Space.page),
      actionLabel = stringResource(R.string.library_refresh),
      onAction = onRefresh
    )
    TextAction(
      stringResource(R.string.library_import_file),
      onClick = onImport,
      modifier = Modifier.testTag("library_empty_import")
    )
  }
}

/** A spinner with what is being waited for. */
@Composable
private fun Loading(text: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page, vertical = Space.xl),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(Size.iconSmall),
      strokeWidth = 2.dp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.width(Space.m))
    Text(text, style = AppText.Subhead, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** One line of secondary text inside a list. */
@Composable
private fun Note(text: String) {
  Text(
    text,
    style = AppText.Subhead,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Space.page, vertical = Space.l)
  )
}

/**
 * A list of songs with Play and Shuffle above it. [header] goes before them (a sub-page title),
 * [empty] replaces the list when there are no songs, [trailing] adds a button to each row.
 */
@Composable
private fun TrackList(
  tracks: List<Track>,
  currentTrack: Track?,
  isPlaying: Boolean,
  onPlay: (Track) -> Unit,
  onShuffle: () -> Unit,
  empty: @Composable () -> Unit,
  header: LazyListScope.() -> Unit = {},
  /** The A–Z letter of a track; with it, a letter strip on the right jumps through the list. */
  index: ((Track) -> String?)? = null,
  /** A row's second line, when it should say more than artist and source. */
  subtitle: @Composable (Track) -> String = { trackSubtitle(it) },
  trailing: (@Composable RowScope.(Track) -> Unit)? = null
) {
  val listState = androidx.compose.foundation.lazy.rememberLazyListState()
  val letters = remember(tracks, index) {
    if (index == null) emptyList() else {
      // Each letter's first track, in list order.
      val firsts = LinkedHashMap<String, Int>()
      tracks.forEachIndexed { i, t -> index(t)?.let { firsts.putIfAbsent(it, i) } }
      firsts.entries.map { it.key to it.value }
    }
  }
  Box(modifier = Modifier.fillMaxSize()) {
  LazyColumn(
    state = listState,
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    header()
    if (tracks.isEmpty()) {
      // Centered in the space the list has, below whatever the header holds.
      item(key = "empty") {
        Box(
          modifier = Modifier.fillMaxWidth().fillParentMaxHeight(0.8f),
          contentAlignment = Alignment.Center
        ) { empty() }
      }
    } else {
      item(key = "play_shuffle") { PlayShuffleRow(onPlay = { onPlay(tracks.first()) }, onShuffle = onShuffle) }
      itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
        Column(modifier = itemMotion(index)) {
          if (index > 0) TrackDivider()
          TrackRow(
            track = track,
            subtitle = subtitle(track),
            onClick = { onPlay(track) },
            isCurrent = currentTrack?.id == track.id,
            isPlaying = isPlaying,
            modifier = Modifier.opensPlayer(),
            trailing = { trailing?.invoke(this, track) }
          )
        }
      }
    }
  }
  // Only worth it for a long list with several letters.
  if (letters.size >= 3 && tracks.size >= 30) {
    // The tracks are the list's last items: header rows and the play row come before them.
    LetterStrip(
      letters = letters.map { it.first },
      onLetter = { letter ->
        val first = letters.firstOrNull { it.first == letter }?.second ?: return@LetterStrip
        val offset = listState.layoutInfo.totalItemsCount - tracks.size
        listState.requestScrollToItem(offset + first)
      },
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .padding(bottom = LocalBarInset.current)
    )
  }
  }
}

/** The order of the device's songs: title, artist, album or newest first. */
@Composable
private fun SongOrderRow(order: com.example.data.playback.SongOrder, onChange: (com.example.data.playback.SongOrder) -> Unit) {
  androidx.compose.foundation.lazy.LazyRow(
    horizontalArrangement = Arrangement.spacedBy(Space.xs),
    contentPadding = PaddingValues(horizontal = Space.page - Space.xs, vertical = Space.xs)
  ) {
    items(com.example.data.playback.SongOrder.entries.size) { i ->
      val o = com.example.data.playback.SongOrder.entries[i]
      FilterChip(
        label = stringResource(o.labelRes()),
        selected = o == order,
        onClick = { onChange(o) },
        modifier = Modifier.testTag("song_order_${o.key}")
      )
    }
  }
}

/**
 * A–Z strip on the list's right edge: touching or sliding along it jumps to the first track of the
 * letter under the finger, with a tick per letter and the letter shown large beside it.
 * TalkBack users scroll the list itself, so the strip is hidden from it.
 */
@Composable
private fun LetterStrip(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
  val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
  var active by remember { mutableStateOf<String?>(null) }
  var height by remember { mutableStateOf(1) }
  fun pick(y: Float) {
    val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
    val l = letters[i]
    if (l != active) {
      active = l
      haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
      onLetter(l)
    }
  }
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    // The bubble grows from nothing in 100 ms and keeps the last letter while it shrinks away.
    var shown by remember { mutableStateOf("") }
    active?.let { shown = it }
    val bubble by androidx.compose.animation.core.animateFloatAsState(
      targetValue = if (active != null) 1f else 0f,
      animationSpec = if (com.example.ui.theme.rememberReducedMotion()) {
        androidx.compose.animation.core.snap()
      } else {
        androidx.compose.animation.core.tween(100, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
      },
      label = "letterBubble"
    )
    if (bubble > 0f) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .graphicsLayer {
            scaleX = bubble
            scaleY = bubble
            alpha = bubble
          }
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
      ) {
        Text(shown, style = AppText.Title2, color = MaterialTheme.colorScheme.onSurface)
      }
      Spacer(Modifier.width(Space.s))
    }
    Column(
      modifier = Modifier
        .padding(end = Space.xxs)
        .onSizeChanged { height = it.height.coerceAtLeast(1) }
        .pointerInput(letters) {
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            pick(down.position.y)
            while (true) {
              val e = awaitPointerEvent()
              val c = e.changes.firstOrNull() ?: break
              if (!c.pressed) break
              c.consume()
              pick(c.position.y)
            }
            active = null
          }
        }
        .clearAndSetSemantics {}
        .testTag("letter_strip"),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      letters.forEach { l ->
        Text(
          l,
          style = AppText.Caption2,
          color = if (l == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = Space.xs, vertical = 1.dp)
        )
      }
    }
  }
}

@Composable
private fun PlaylistsList(
  playlists: List<Playlist>,
  byId: Map<String, Track>,
  onOpen: (Playlist) -> Unit,
  onNew: () -> Unit,
  onImportAudio: () -> Unit
) {
  var query by rememberSaveable { mutableStateOf("") }
  val shown = playlists.filter { com.example.data.TextMatch.contains(it.name, query) }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(top = Space.s, bottom = LocalBarInset.current + Space.l)
  ) {
    item(key = "search") {
      SearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = stringResource(R.string.library_search_playlists_hint),
        modifier = Modifier
          .padding(horizontal = Space.page)
          .padding(bottom = Space.s)
          .testTag("library_search_input")
      )
    }
    item(key = "new") {
      ActionRow(stringResource(R.string.library_new_playlist), Icons.Rounded.Add, onNew, Modifier.testTag("library_new_playlist"))
    }
    item(key = "import") {
      TrackDivider()
      ActionRow(stringResource(R.string.library_import_audio), Icons.Rounded.AudioFile, onImportAudio, Modifier.testTag("library_add_local_audio"))
    }
    when {
      playlists.isEmpty() -> item(key = "empty") {
        Note(stringResource(R.string.library_playlists_empty_note))
      }
      shown.isEmpty() -> item(key = "no_match") { Note(stringResource(R.string.library_search_no_playlist, query.trim())) }
    }
    itemsIndexed(shown, key = { _, p -> p.id }) { index, playlist ->
      Column(modifier = itemMotion(index)) {
        TrackDivider()
        PlaylistRow(
          playlist = playlist,
          tracks = playlist.trackIds.mapNotNull { byId[it] },
          onClick = { onOpen(playlist) }
        )
      }
    }
  }
}

/** A row that creates something, in the accent: a tinted square with the glyph, then the label. */
@Composable
private fun ActionRow(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .highlightClickable(onClick = onClick)
      .padding(horizontal = Space.page, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(Size.rowArtwork)
        .clip(Shapes.xs)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Size.icon))
    }
    Spacer(Modifier.width(Space.m))
    Text(title, style = AppText.Body, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun PlaylistRow(playlist: Playlist, tracks: List<Track>, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .highlightClickable(onClickLabel = stringResource(R.string.library_open_playlist, playlist.name), onClick = onClick)
      .padding(horizontal = Space.page, vertical = 6.dp)
      .testTag("playlist_row_${playlist.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    PlaylistArtwork(tracks)
    Spacer(Modifier.width(Space.m))
    Column(modifier = Modifier.weight(1f)) {
      Text(playlist.name, style = AppText.Body, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        if (playlist.trackIds.isEmpty()) stringResource(R.string.library_tracks_none)
        else pluralStringResource(R.plurals.library_track_count, playlist.trackIds.size, playlist.trackIds.size),
        style = AppText.Subhead,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Icon(
      Icons.Rounded.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      modifier = Modifier.size(Size.iconSmall)
    )
  }
}

/**
 * A playlist's cover: four covers in a grid once it has four songs with artwork, else the first
 * song's cover, else a quiet tile with the playlist glyph.
 */
@Composable
private fun PlaylistArtwork(tracks: List<Track>) {
  val covers = tracks.filter { it.artworkUrl != null }.take(4)
  Box(
    modifier = Modifier
      .size(PlaylistArtworkSize)
      .clip(Shapes.xs)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center
  ) {
    when {
      covers.size == 4 -> Column {
        covers.chunked(2).forEach { pair ->
          Row {
            pair.forEach { t ->
              TrackArtwork(track = t, shape = RectangleShape, iconSize = 14.dp, modifier = Modifier.size(PlaylistArtworkSize / 2))
            }
          }
        }
      }
      tracks.isNotEmpty() -> TrackArtwork(
        track = covers.firstOrNull() ?: tracks.first(),
        shape = RectangleShape,
        iconSize = Size.icon,
        modifier = Modifier.size(PlaylistArtworkSize)
      )
      else -> Icon(
        Icons.AutoMirrored.Rounded.QueueMusic,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(Size.icon)
      )
    }
  }
}

@Composable
private fun PlaylistDetail(
  playlist: Playlist,
  tracks: List<Track>,
  currentTrack: Track?,
  isPlaying: Boolean,
  onBack: () -> Unit,
  onPlay: (Track) -> Unit,
  onShuffle: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onRemoveTrack: (String) -> Unit
) {
  TrackList(
    tracks = tracks,
    currentTrack = currentTrack,
    isPlaying = isPlaying,
    onPlay = onPlay,
    onShuffle = onShuffle,
    header = {
      item(key = "header") {
        SubPageHeader(
          title = playlist.name,
          backLabel = stringResource(R.string.library_back_label),
          onBack = onBack,
          modifier = Modifier.testTag("library_back"),
          trailing = {
            IconAction(Icons.Rounded.Edit, contentDescription = stringResource(R.string.library_rename_desc), onClick = onRename, tint = MaterialTheme.colorScheme.primary)
            IconAction(Icons.Rounded.Delete, contentDescription = stringResource(R.string.library_delete_desc), onClick = onDelete, tint = MaterialTheme.colorScheme.error)
          }
        )
      }
    },
    empty = {
      EmptyState(
        icon = Icons.AutoMirrored.Rounded.QueueMusic,
        title = stringResource(R.string.library_playlist_empty_title),
        message = stringResource(R.string.library_playlist_empty_message),
        modifier = Modifier.padding(horizontal = Space.page)
      )
    },
    trailing = { track ->
      IconAction(
        icon = Icons.Rounded.Close,
        contentDescription = stringResource(R.string.library_playlist_remove, track.title),
        onClick = { onRemoveTrack(track.id) },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.trailingEdge()
      )
    }
  )
}
