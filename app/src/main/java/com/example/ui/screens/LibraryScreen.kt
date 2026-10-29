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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.data.model.Playlist
import com.example.data.model.Track
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

private enum class LibrarySection(val label: String) {
  SONGS("Músicas"),
  PLAYLISTS("Playlists"),
  FAVORITES("Favoritos"),
  QUEUE("Fila")
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
  openQueue: Boolean = false,
  onQueueOpened: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  var section by rememberSaveable { mutableStateOf(LibrarySection.SONGS) }
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
  val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    if (uri != null) {
      val appContext = context.applicationContext
      scope.launch {
        val (name, durationMs) = withContext(Dispatchers.IO) { readAudioFile(appContext, uri) }
        // Falls back to 3 min only when the file has no duration in its metadata.
        onAddLocalTrack(name, "Biblioteca Local", uri.toString(), if (durationMs > 0) durationMs else 180_000L)
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
              label = if (s == LibrarySection.QUEUE && queue.isNotEmpty()) "${s.label} (${queue.size})" else s.label,
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
              tracks = if (musicAllowed) deviceTracks else emptyList(),
              currentTrack = currentTrack,
              isPlaying = isPlaying,
              onPlay = { track -> onPlayFromList(track, deviceTracks) },
              onShuffle = { onShuffleList(deviceTracks) },
              empty = {
                when {
                  !musicAllowed -> MusicPermissionPrompt(
                    blocked = musicBlocked,
                    onAllow = { musicPermissionLauncher.launch(MusicPermission) },
                    onOpenSettings = openAppSettings
                  )
                  deviceScanning -> Loading("Procurando músicas no aparelho…")
                  else -> EmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "Nenhuma música no aparelho",
                    message = "Não achamos arquivos de áudio no celular. Você pode importar um arquivo em Playlists.",
                    modifier = Modifier.padding(horizontal = Space.page)
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
              tracks = favoriteTracks,
              currentTrack = currentTrack,
              isPlaying = isPlaying,
              onPlay = { track -> onPlayFromList(track, favoriteTracks) },
              onShuffle = { onShuffleList(favoriteTracks) },
              empty = {
                EmptyState(
                  icon = Icons.Rounded.FavoriteBorder,
                  title = "Nenhum favorito ainda",
                  message = "Toque em “Favoritar” no menu de uma música para guardá-la aqui.",
                  modifier = Modifier.padding(horizontal = Space.page)
                )
              },
              trailing = { track ->
                IconAction(
                  icon = Icons.Rounded.Favorite,
                  contentDescription = "Remover ${track.title} dos favoritos",
                  onClick = { onToggleFavorite(track) },
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.trailingEdge()
                )
              }
            )

            LibrarySection.QUEUE -> QueueList(
              queue = queue,
              onPlay = onPlayQueueItem,
              onRemove = onRemoveQueueItem,
              onClear = onClearQueue
            )
          }
        }
      }
    }
  }

  if (showNewPlaylist) {
    NamePlaylistDialog(
      title = "Nova playlist",
      confirmLabel = "Criar",
      initialName = "",
      onConfirm = { name -> onCreatePlaylist(name); showNewPlaylist = false },
      onDismiss = { showNewPlaylist = false }
    )
  }
  playlists.firstOrNull { it.id == renamingId }?.let { pl ->
    NamePlaylistDialog(
      title = "Renomear playlist",
      confirmLabel = "Salvar",
      initialName = pl.name,
      onConfirm = { name -> onRenamePlaylist(pl.id, name); renamingId = null },
      onDismiss = { renamingId = null }
    )
  }
  playlists.firstOrNull { it.id == deletingId }?.let { pl ->
    AlertDialog(
      onDismissRequest = { deletingId = null },
      title = { Text("Excluir “${pl.name}”?", style = AppText.Title3) },
      text = {
        Text(
          if (pl.trackIds.isEmpty()) "A playlist está vazia."
          else "A playlist e a ordem das ${pl.trackIds.size} faixas serão apagadas. As músicas continuam na biblioteca.",
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
        ) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Cancelar") } }
    )
  }
}

/** Name and duration of a picked audio file. Blocking: call it on [Dispatchers.IO]. */
private fun readAudioFile(context: Context, uri: Uri): Pair<String, Long> {
  runCatching {
    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  var displayName = "Áudio Local"
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
      "Suas músicas do aparelho",
      style = AppText.Title3,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.size(Space.s))
    Text(
      if (blocked) "O acesso às músicas está bloqueado. Libere em Ajustes › Permissões › Música e áudio para ver aqui tudo o que está no celular."
      else "Permita o acesso às músicas para ver aqui tudo o que está no celular, com capa, artista e álbum.",
      style = AppText.Subhead,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
    Spacer(Modifier.size(Space.l))
    if (blocked) {
      PrimaryButton("Abrir Ajustes do app", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().testTag("library_open_settings"))
      // The detection cannot tell "don't ask again" from a dismissed dialog, so asking stays possible.
      TextAction("Pedir de novo", onClick = onAllow)
    } else {
      PrimaryButton("Permitir acesso", onClick = onAllow, modifier = Modifier.fillMaxWidth().testTag("library_allow_music"))
    }
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
  trailing: (@Composable RowScope.(Track) -> Unit)? = null
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    header()
    if (tracks.isEmpty()) {
      item(key = "empty") { empty() }
    } else {
      item(key = "play_shuffle") { PlayShuffleRow(onPlay = { onPlay(tracks.first()) }, onShuffle = onShuffle) }
      itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
        Column(modifier = itemMotion(index)) {
          if (index > 0) TrackDivider()
          TrackRow(
            track = track,
            subtitle = trackSubtitle(track),
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
  val shown = playlists.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(top = Space.s, bottom = LocalBarInset.current + Space.l)
  ) {
    item(key = "search") {
      SearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = "Buscar nas playlists",
        modifier = Modifier
          .padding(horizontal = Space.page)
          .padding(bottom = Space.s)
          .testTag("library_search_input")
      )
    }
    item(key = "new") {
      ActionRow("Nova playlist…", Icons.Rounded.Add, onNew, Modifier.testTag("library_new_playlist"))
    }
    item(key = "import") {
      TrackDivider()
      ActionRow("Importar arquivo de áudio…", Icons.Rounded.AudioFile, onImportAudio, Modifier.testTag("library_add_local_audio"))
    }
    when {
      playlists.isEmpty() -> item(key = "empty") {
        Note("Crie uma playlist e adicione músicas pelo menu de qualquer faixa.")
      }
      shown.isEmpty() -> item(key = "no_match") { Note("Nenhuma playlist com “$query”.") }
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
      .highlightClickable(onClickLabel = "Abrir ${playlist.name}", onClick = onClick)
      .padding(horizontal = Space.page, vertical = 6.dp)
      .testTag("playlist_row_${playlist.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    PlaylistArtwork(tracks)
    Spacer(Modifier.width(Space.m))
    Column(modifier = Modifier.weight(1f)) {
      Text(playlist.name, style = AppText.Body, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        if (playlist.trackIds.size == 1) "1 faixa" else "${playlist.trackIds.size} faixas",
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
          backLabel = "Biblioteca",
          onBack = onBack,
          modifier = Modifier.testTag("library_back"),
          trailing = {
            IconAction(Icons.Rounded.Edit, contentDescription = "Renomear playlist", onClick = onRename, tint = MaterialTheme.colorScheme.primary)
            IconAction(Icons.Rounded.Delete, contentDescription = "Excluir playlist", onClick = onDelete, tint = MaterialTheme.colorScheme.error)
          }
        )
      }
    },
    empty = {
      EmptyState(
        icon = Icons.AutoMirrored.Rounded.QueueMusic,
        title = "Playlist vazia",
        message = "Use “Adicionar a uma playlist” no menu de uma música para colocá-la aqui.",
        modifier = Modifier.padding(horizontal = Space.page)
      )
    },
    trailing = { track ->
      IconAction(
        icon = Icons.Rounded.Close,
        contentDescription = "Remover ${track.title} da playlist",
        onClick = { onRemoveTrack(track.id) },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.trailingEdge()
      )
    }
  )
}

@Composable
private fun QueueList(
  queue: List<Track>,
  onPlay: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onClear: () -> Unit
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = LocalBarInset.current + Space.l)
  ) {
    if (queue.isEmpty()) {
      item(key = "empty") {
        EmptyState(
          icon = Icons.AutoMirrored.Rounded.QueueMusic,
          title = "A fila está vazia",
          message = "Use “Tocar em seguida” ou “Adicionar à fila” no menu de uma música.",
          modifier = Modifier.padding(horizontal = Space.page)
        )
      }
      return@LazyColumn
    }
    item(key = "header") {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = Size.touch)
          .padding(start = Space.page, end = Space.page - Space.s, top = Space.s),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          "A seguir",
          style = AppText.Title2,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier
            .weight(1f)
            .semantics { heading() }
        )
        TextAction("Limpar", onClick = onClear, modifier = Modifier.testTag("library_clear_queue"))
      }
    }
    // The queue may hold the same song twice, so the key pairs the position with the id. Removal goes
    // by position, as the ViewModel's queue does.
    itemsIndexed(queue, key = { index, track -> "$index-${track.id}" }) { index, track ->
      Column(modifier = Modifier.listEntrance(index)) {
        if (index > 0) TrackDivider()
        TrackRow(
          track = track,
          subtitle = trackSubtitle(track),
          onClick = { onPlay(index) },
          modifier = Modifier.opensPlayer(),
          trailing = {
            IconAction(
              icon = Icons.Rounded.Close,
              contentDescription = "Remover ${track.title} da fila",
              onClick = { onRemove(index) },
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.trailingEdge()
            )
          }
        )
      }
    }
  }
}
