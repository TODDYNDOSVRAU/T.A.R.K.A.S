package com.example.windows.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.example.data.library.AutoList
import com.example.data.library.AutoLists
import com.example.data.library.LibraryGroups
import com.example.data.library.Stamp
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.playback.LibrarySearch
import com.example.ui.components.IconAction
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SearchField
import com.example.ui.components.TextAction
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.*
import com.example.windows.shell.ShellSearch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SETTINGS = "window"
private const val KEY_SORT = "library.sort"
private const val KEY_COLS = "library.cols"
private const val UNDO_MS = 8000L

/** One thing the user can take back: what happened and how to reverse it. */
private class Undo(val label: String, val restore: () -> Unit)

/** Opens Windows Explorer with the file selected. */
internal fun revealInExplorer(file: File) {
  runCatching { ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start() }
}

/**
 * The Biblioteca tab as a desktop window: sources on the left, the track table in the middle, details on the right.
 * Everything the table lists is real: the scanned folders, the playlists and the queue of the player.
 */
@Composable
fun LibraryScreen(lib: BibliotecaState, modifier: Modifier = Modifier) {
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val settings = lib.app.settings
  var source by remember { mutableStateOf<Source>(Source.All) }
  var query by remember { mutableStateOf("") }
  var expanded by remember { mutableStateOf(setOf(GroupKind.ALBUMS)) }
  var selection by remember { mutableStateOf(Selection()) }
  var sort by remember { mutableStateOf(SortSpec.decode(settings.get(SETTINGS, KEY_SORT))) }
  var widths by remember { mutableStateOf(TableLogic.decodeWidths(settings.get(SETTINGS, KEY_COLS))) }
  var undo by remember { mutableStateOf<Undo?>(null) }
  var newPlaylist by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<Playlist?>(null) }
  var origin by remember { mutableStateOf(Offset.Zero) }
  val hub = remember { DragHub() }
  val tableFocus = remember { FocusRequester() }
  val searchFocus = remember { FocusRequester() }

  val library by lib.library.collectAsState()
  val store = lib.store
  val favorites by store.favorites.collectAsState()
  val playlists by store.playlists.collectAsState()
  val saved by store.savedTracks.collectAsState()
  val stats by store.stats.collectAsState()
  val sortedLibrary by lib.sorted.collectAsState()
  val ui by lib.player.ui.collectAsState()
  val scanning by lib.scanning.collectAsState()
  val everScanned by lib.everScanned.collectAsState()

  val plays = remember(stats) { stats.mapValues { it.value.plays } }
  val byId = remember(library, saved) { saved + library.associateBy { it.id } }
  val favoriteSet = remember(favorites) { favorites.toSet() }

  val sourceData = remember(library, stats, playlists, favorites, ui.queue.size) {
    SourceData(
      songs = library.size, favorites = favorites.size, queue = ui.queue.size, playlists = playlists,
      playlistCounts = playlists.associate { it.id to it.trackIds.size },
      autoCounts = AutoList.entries.associateWith { AutoLists.build(it, library, stats).size },
      albums = LibraryGroups.albums(library), artists = LibraryGroups.artists(library), genres = LibraryGroups.genres(library)
    )
  }

  val base: List<Track> = when (val s = source) {
    Source.All -> sortedLibrary
    Source.Favorites -> favorites.mapNotNull { byId[it] }
    Source.Queue -> ui.queue
    is Source.Group -> when (s.kind) {
      GroupKind.ALBUMS -> sourceData.albums
      GroupKind.ARTISTS -> sourceData.artists
      GroupKind.GENRES -> sourceData.genres
    }.firstOrNull { it.key == s.key }?.tracks.orEmpty()
    is Source.PlaylistOf -> playlists.firstOrNull { it.id == s.id }?.trackIds.orEmpty().mapNotNull { byId[it] }
    is Source.Auto -> AutoLists.build(s.list, library, stats)
    Source.Folders -> emptyList()
  }
  val tracks = remember(base, query, sort, plays) {
    val found = if (query.isBlank()) base else LibrarySearch.filter(base, query)
    sort?.let { TableLogic.sort(found, it, plays) } ?: found
  }
  val chosen = remember(tracks, selection) { selection.tracks(tracks) }

  fun notice(text: String) = lib.app.showNotice(text)
  fun go(s: Source) { source = s; selection = Selection(); query = "" }

  fun addToPlaylist(list: List<Track>, id: String) {
    val name = playlists.firstOrNull { it.id == id }?.name ?: return
    val existing = playlists.firstOrNull { it.id == id }?.trackIds.orEmpty().toSet()
    list.forEach { store.addToPlaylist(id, it) }
    val added = list.count { it.id !in existing }
    notice(
      if (added == 0 && list.size == 1) trNow(Res.string.library_notice_already_in_playlist, list[0].title, name)
      else trNow(Res.string.library_notice_added_to_playlist, name)
    )
  }

  val actions = TableActions(
    play = { t -> lib.play(t, tracks) },
    playAll = { list -> list.firstOrNull()?.let { lib.play(it, list) } },
    playNext = { list -> list.asReversed().forEach(lib::playNext); list.singleOrNull()?.let { notice(trNow(Res.string.library_notice_play_next, it.title)) } },
    addToQueue = { list -> list.forEach(lib::addToQueue); list.singleOrNull()?.let { notice(trNow(Res.string.library_notice_added_to_queue, it.title)) } },
    addToPlaylist = ::addToPlaylist,
    reveal = { t -> TableLogic.fileOf(t)?.let(::revealInExplorer) },
    copyInfo = { list ->
      Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(list.joinToString("\n") { "${it.artist} - ${it.title}" }), null)
    },
    toggleFavorite = { t -> store.toggleFavorite(t) },
    remove = when (val s = source) {
      Source.Queue -> ({ list: List<Track> ->
        val before = ui.queue
        val ids = list.map { it.id }.toSet()
        lib.player.setQueue(before.filter { it.id !in ids })
        undo = Undo(lt("${list.size} removed from the queue", "${list.size} removidas da fila")) { lib.player.setQueue(before) }
        selection = Selection()
      })
      is Source.PlaylistOf -> ({ list: List<Track> ->
        list.forEach { store.removeFromPlaylist(s.id, it.id) }
        undo = Undo(lt("${list.size} removed from the playlist", "${list.size} removidas da playlist")) { list.forEach { store.addToPlaylist(s.id, it) } }
        selection = Selection()
      })
      Source.Favorites -> ({ list: List<Track> ->
        list.forEach { store.toggleFavorite(it) }
        undo = Undo(lt("${list.size} removed from favourites", "${list.size} removidas dos favoritos")) { list.forEach { store.toggleFavorite(it) } }
        selection = Selection()
      })
      else -> null
    },
    removeLabel = when (source) {
      Source.Queue -> lt("Remove from queue", "Remover da fila")
      is Source.PlaylistOf -> lt("Remove from playlist", "Remover da playlist")
      Source.Favorites -> lt("Remove from favourites", "Remover dos favoritos")
      else -> ""
    }
  )

  val onFiles = remember(lib) {
    { files: List<File> ->
      val plan = TableLogic.importPlan(files)
      plan.folders.forEach(lib::addRoot)
      scope.launch {
        val read = withContext(Dispatchers.IO) {
          plan.audio.map { f -> lib.scanner.readSong(LibraryScanner.idOf(f), f, f.parent.orEmpty(), Stamp(f.lastModified(), f.length())).track }
        }
        read.forEach { store.addPicked(it) }
        val n = plan.folders.size + read.size
        lib.app.showNotice(
          if (n == 0) lt("No audio files in what was dropped.", "Nenhum arquivo de áudio no que foi solto.")
          else lt("Imported ${plan.folders.size} folders and ${read.size} files.", "Importadas ${plan.folders.size} pastas e ${read.size} arquivos.")
        )
      }
      Unit
    }
  }

  LaunchedEffect(Unit) { ShellSearch.requests.collect { if (it > 0) runCatching { searchFocus.requestFocus() } } }
  LaunchedEffect(undo) { if (undo != null) { delay(UNDO_MS); undo = null } }
  LaunchedEffect(sort) { settings.put(SETTINGS, KEY_SORT, sort?.encode().orEmpty()) }
  LaunchedEffect(widths) { settings.put(SETTINGS, KEY_COLS, TableLogic.encodeWidths(widths)) }
  // A source whose playlist was deleted falls back to the whole library.
  LaunchedEffect(playlists) { (source as? Source.PlaylistOf)?.let { s -> if (playlists.none { it.id == s.id }) go(Source.All) } }

  val colors = MaterialTheme.colorScheme
  BoxWithConstraints(
    modifier.explorerDrop(onFiles).onGloballyPositioned { origin = it.positionInWindow() }
  ) {
    val showDetails = maxWidth >= Grid.u192 * 4
    Row(Modifier.fillMaxSize()) {
      SourcesPane(
        sourceData, source, ::go, expanded, { k -> expanded = if (k in expanded) expanded - k else expanded + k }, hub,
        onNewPlaylist = { newPlaylist = true }, onRename = { renaming = it },
        onDelete = { p ->
          val tracksOf = p.trackIds.mapNotNull { byId[it] }
          store.deletePlaylist(p.id)
          undo = Undo(lt("Playlist “${p.name}” deleted", "Playlist “${p.name}” excluída")) { store.createPlaylist(p.name, tracksOf) }
        },
        modifier = Modifier.width(Grid.u192)
      )
      Box(Modifier.width(Grid.u6 / 6).fillMaxHeight().background(colors.outlineVariant))
      Column(Modifier.weight(1f).fillMaxHeight()) {
        if (source == Source.Folders) FoldersPane(lib, Modifier.weight(1f))
        else {
          Row(Modifier.fillMaxWidth().padding(horizontal = Grid.u12), verticalAlignment = Alignment.CenterVertically) {
            SearchField(
              query, { query = it; selection = Selection() }, tr(Res.string.library_search_songs_hint),
              Modifier.weight(1f).focusRequester(searchFocus).padding(vertical = Grid.u6)
            )
            IconAction(Icons.Filled.PlayArrow, tr(Res.string.library_play), { actions.playAll(tracks) })
            IconAction(Icons.Filled.Shuffle, tr(Res.string.library_shuffle), { actions.playAll(tracks.shuffled()) })
            IconAction(Icons.Filled.Refresh, tr(Res.string.library_refresh), { lib.rescan() })
          }
          if (scanning) Text(
            tr(Res.string.library_loading_songs), style = GridText.Label, color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Grid.u12)
          )
          when {
            tracks.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth()) { Empty(lib, source, query, everScanned, library.isEmpty()) }
            else -> TrackTable(
              tracks, selection, { selection = it }, sort,
              { c -> sort = sort?.click(c) ?: SortSpec(c, c != Col.PLAYS) },
              widths, { widths = it }, plays, favoriteSet, ui.current?.id,
              playlists.map { it.id to it.name }, actions, hub, Modifier.weight(1f).fillMaxWidth(), tableFocus
            )
          }
          undo?.let { u ->
            Row(
              Modifier.fillMaxWidth().background(colors.surfaceVariant).padding(horizontal = Grid.u12),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(u.label, style = GridText.Small, color = colors.onSurface, modifier = Modifier.weight(1f))
              TextAction(lt("Undo", "Desfazer"), { u.restore(); undo = null })
            }
          }
        }
      }
      if (showDetails) {
        Box(Modifier.width(Grid.u6 / 6).fillMaxHeight().background(colors.outlineVariant))
        DetailsPane(chosen, stats, Modifier.width(Grid.u192))
      }
    }
    DragChip(hub, origin)
    if (newPlaylist) NamePlaylistDialog(
      tr(Res.string.library_new_playlist_title), "", tr(Res.string.library_create), { newPlaylist = false },
      { name ->
        newPlaylist = false
        val p = store.createPlaylist(name, chosen)
        go(Source.PlaylistOf(p.id))
      }
    )
    renaming?.let { p ->
      NamePlaylistDialog(tr(Res.string.library_rename_desc), p.name, tr(Res.string.library_save), { renaming = null }, { name ->
        renaming = null
        store.renamePlaylist(p.id, name)
      })
    }
  }
}

@Composable
private fun Empty(lib: BibliotecaState, source: Source, query: String, everScanned: Boolean, libraryEmpty: Boolean) {
  when {
    query.isNotBlank() -> EmptyState(tr(Res.string.library_search_empty_title), trNow(Res.string.library_search_empty_songs, query))
    source == Source.All && !everScanned && libraryEmpty -> EmptyState(tr(Res.string.library_loading_songs), "")
    source == Source.All -> EmptyState(tr(Res.string.library_empty_songs_title), tr(Res.string.library_empty_songs_message)) {
      PrimaryButton(tr(Res.string.library_import_audio), { chooseFolder(lib) })
    }
    source == Source.Favorites -> EmptyState(tr(Res.string.library_favorites_empty_title), tr(Res.string.library_favorites_empty_message))
    source is Source.PlaylistOf -> EmptyState(tr(Res.string.library_playlist_empty_title), tr(Res.string.library_playlist_empty_message))
    source is Source.Auto -> EmptyState(autoEmptyTitle(source.list), autoEmptyMessage(source.list))
    else -> EmptyState(tr(Res.string.library_empty_songs_title), tr(Res.string.library_tracks_none))
  }
}
