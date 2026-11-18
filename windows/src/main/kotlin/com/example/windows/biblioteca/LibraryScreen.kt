package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.library.AlbumGroup
import com.example.data.library.AutoList
import com.example.data.library.AutoLists
import com.example.data.library.FolderCheck
import com.example.data.library.FolderTree
import com.example.data.library.LibraryGroup
import com.example.data.library.LibraryGroups
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.playback.LibrarySearch
import com.example.data.playback.LibrarySort
import com.example.data.playback.SongOrder
import com.example.ui.components.FilterChip
import com.example.ui.components.IconAction
import com.example.ui.components.InsetGroup
import com.example.ui.components.ListRow
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SearchField
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubPageHeader
import com.example.ui.components.TarkasAlertDialog
import com.example.ui.components.TextAction
import com.example.ui.components.TonalButton
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.*
import kotlinx.coroutines.launch
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

private enum class Section { SONGS, ALBUMS, ARTISTS, GENRES, PLAYLISTS, FAVORITES, AUTO, QUEUE }

/** Where the Biblioteca is inside a section: the list itself, one group, one playlist, one automatic list or the folders. */
private sealed interface Page {
  data object Main : Page
  data class Group(val section: Section, val key: String) : Page
  data class PlaylistPage(val id: String) : Page
  data class Auto(val list: AutoList) : Page
  data object Folders : Page
}

/** The Biblioteca tab: songs, albums, artists, genres, playlists, favourites, automatic lists and the queue. */
@Composable
fun LibraryScreen(lib: BibliotecaState, modifier: Modifier = Modifier) {
  var section by rememberSaveable { mutableStateOf(Section.SONGS) }
  var page by remember { mutableStateOf<Page>(Page.Main) }
  var query by rememberSaveable { mutableStateOf("") }

  val sorted by lib.sorted.collectAsState()
  val library by lib.library.collectAsState()
  val store = lib.store
  val favorites by store.favorites.collectAsState()
  val playlists by store.playlists.collectAsState()
  val saved by store.savedTracks.collectAsState()
  val stats by store.stats.collectAsState()
  val order by lib.order.collectAsState()
  val ui by lib.player.ui.collectAsState()

  val byId = remember(library, saved) { saved + library.associateBy { it.id } }
  val favoriteTracks = remember(favorites, byId) { favorites.mapNotNull { byId[it] } }

  Column(modifier.fillMaxSize()) {
    when (val p = page) {
      Page.Main -> {
        SectionHeader(
          tr(Res.string.library_local_library), actionLabel = tr(Res.string.library_folders_chip), onAction = { page = Page.Folders }
        )
        SectionChips(section, ui.queue.size) { section = it; query = "" }
        SearchField(
          value = query, onValueChange = { query = it }, placeholder = searchHint(section),
          modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u6)
        )
        when (section) {
          Section.SONGS -> SongsSection(lib, sorted, query, order, favorites.toSet(), playlists, ui.current?.id)
          Section.FAVORITES -> TrackList(lib, LibrarySearch.filter(favoriteTracks, query), favorites.toSet(), playlists, ui.current?.id,
            emptyTitle = tr(Res.string.library_favorites_empty_title), emptyMessage = tr(Res.string.library_favorites_empty_message))
          Section.ALBUMS -> GroupList(LibraryGroups.albums(LibrarySearch.filter(library, query)), tr(Res.string.library_albums_empty)) { page = Page.Group(Section.ALBUMS, it.key) }
          Section.ARTISTS -> GroupList(LibraryGroups.artists(LibrarySearch.filter(library, query)), tr(Res.string.library_artists_empty)) { page = Page.Group(Section.ARTISTS, it.key) }
          Section.GENRES -> GroupList(LibraryGroups.genres(LibrarySearch.filter(library, query)), tr(Res.string.library_genres_empty)) { page = Page.Group(Section.GENRES, it.key) }
          Section.PLAYLISTS -> PlaylistsSection(lib, playlists, query, byId) { page = Page.PlaylistPage(it) }
          Section.AUTO -> AutoSection(library, stats.mapValues { it.value }) { page = Page.Auto(it) }
          Section.QUEUE -> QueueSection(lib, ui.queue, favorites.toSet(), playlists)
        }
      }
      is Page.Group -> {
        val groups: List<LibraryGroup> = when (p.section) {
          Section.ALBUMS -> LibraryGroups.albums(library)
          Section.ARTISTS -> LibraryGroups.artists(library)
          else -> LibraryGroups.genres(library)
        }
        val group = groups.firstOrNull { it.key == p.key }
        if (group == null) page = Page.Main
        else {
          SubPageHeader(group.name, tr(Res.string.library_back_label), { page = Page.Main })
          TrackList(lib, group.tracks, favorites.toSet(), playlists, ui.current?.id, withPlayAll = true,
            emptyTitle = tr(Res.string.library_empty_songs_title), emptyMessage = tr(Res.string.library_tracks_none))
        }
      }
      is Page.PlaylistPage -> {
        val playlist = playlists.firstOrNull { it.id == p.id }
        if (playlist == null) page = Page.Main
        else PlaylistPage(lib, playlist, byId, favorites.toSet(), playlists, ui.current?.id) { page = Page.Main }
      }
      is Page.Auto -> {
        SubPageHeader(autoName(p.list), tr(Res.string.library_back_label), { page = Page.Main })
        val tracks = remember(library, stats, p.list) { AutoLists.build(p.list, library, stats) }
        TrackList(lib, tracks, favorites.toSet(), playlists, ui.current?.id, withPlayAll = true,
          emptyTitle = autoEmptyTitle(p.list), emptyMessage = autoEmptyMessage(p.list))
      }
      Page.Folders -> FoldersPage(lib) { page = Page.Main }
    }
  }
}

@Composable
private fun searchHint(section: Section): String = when (section) {
  Section.SONGS, Section.QUEUE -> tr(Res.string.library_search_songs_hint)
  Section.ALBUMS -> tr(Res.string.library_search_albums_hint)
  Section.ARTISTS -> tr(Res.string.library_search_artists_hint)
  Section.GENRES -> tr(Res.string.library_search_genres_hint)
  Section.PLAYLISTS -> tr(Res.string.library_search_playlists_hint)
  Section.FAVORITES -> tr(Res.string.library_search_favorites_hint)
  Section.AUTO -> tr(Res.string.library_search_songs_hint)
}

@Composable
private fun autoName(l: AutoList) = when (l) {
  AutoList.RECENTLY_ADDED -> tr(Res.string.library_auto_recently_added)
  AutoList.RECENTLY_PLAYED -> tr(Res.string.library_auto_recently_played)
  AutoList.MOST_PLAYED -> tr(Res.string.library_auto_most_played)
}

@Composable
private fun autoEmptyTitle(l: AutoList) = when (l) {
  AutoList.RECENTLY_ADDED -> tr(Res.string.library_auto_empty_title_added)
  AutoList.RECENTLY_PLAYED -> tr(Res.string.library_auto_empty_title_played)
  AutoList.MOST_PLAYED -> tr(Res.string.library_auto_empty_title_most)
}

@Composable
private fun autoEmptyMessage(l: AutoList) = when (l) {
  AutoList.RECENTLY_ADDED -> tr(Res.string.library_auto_empty_message_added)
  AutoList.RECENTLY_PLAYED -> tr(Res.string.library_auto_empty_message_played)
  AutoList.MOST_PLAYED -> tr(Res.string.library_auto_empty_message_most)
}

@Composable
private fun SectionChips(selected: Section, queueCount: Int, onSelect: (Section) -> Unit) {
  Row(
    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Grid.margin),
    horizontalArrangement = Arrangement.spacedBy(Grid.u6)
  ) {
    for (s in Section.entries) {
      val label = when (s) {
        Section.SONGS -> tr(Res.string.library_section_songs)
        Section.ALBUMS -> tr(Res.string.library_section_albums)
        Section.ARTISTS -> tr(Res.string.library_section_artists)
        Section.GENRES -> tr(Res.string.library_section_genres)
        Section.PLAYLISTS -> tr(Res.string.library_section_playlists)
        Section.FAVORITES -> tr(Res.string.library_section_favorites)
        Section.AUTO -> tr(Res.string.library_section_auto)
        Section.QUEUE -> if (queueCount > 0) tr(Res.string.library_section_queue_count, queueCount) else tr(Res.string.library_section_queue)
      }
      FilterChip(label, s == selected, { onSelect(s) })
    }
  }
}

// ---- songs ----

@Composable
private fun SongsSection(
  lib: BibliotecaState, sorted: List<Track>, query: String, order: SongOrder,
  favorites: Set<String>, playlists: List<Playlist>, playingId: String?
) {
  val scanning by lib.scanning.collectAsState()
  val everScanned by lib.everScanned.collectAsState()
  val found = remember(sorted, query) { LibrarySearch.filter(sorted, query) }
  val roots by lib.roots.collectAsState()
  Row(Modifier.fillMaxWidth().padding(horizontal = Grid.margin), verticalAlignment = Alignment.CenterVertically) {
    Box {
      var open by remember { mutableStateOf(false) }
      TonalButton(orderName(order), { open = true }, icon = Icons.Filled.Sort)
      DropdownMenu(open, { open = false }) {
        for (o in SongOrder.entries) DropdownMenuItem(
          text = { Text(orderName(o), style = GridText.Body) },
          onClick = { open = false; lib.setOrder(o) }
        )
      }
    }
    Box(Modifier.weight(1f))
    IconAction(Icons.Filled.Refresh, tr(Res.string.library_refresh), { lib.rescan() })
  }
  when {
    found.isEmpty() && query.isNotBlank() ->
      EmptyState(tr(Res.string.library_search_empty_title), tr(Res.string.library_search_empty_songs))
    found.isEmpty() && (scanning || !everScanned) ->
      EmptyState(tr(Res.string.library_loading_songs), if (scanning) lib.scanned.collectAsState().value.toString() else "")
    found.isEmpty() ->
      EmptyState(tr(Res.string.library_empty_songs_title), tr(Res.string.library_empty_songs_message)) {
        if (roots.isEmpty()) PrimaryButton(tr(Res.string.library_import_audio), { chooseFolder(lib) })
      }
    else -> TrackList(lib, found, favorites, playlists, playingId, order = order, indexed = query.isBlank(),
      emptyTitle = "", emptyMessage = "")
  }
}

@Composable
private fun orderName(o: SongOrder) = when (o) {
  SongOrder.TITLE -> tr(Res.string.library_order_title)
  SongOrder.ARTIST -> tr(Res.string.library_order_artist)
  SongOrder.ALBUM -> tr(Res.string.library_order_album)
  SongOrder.RECENT -> tr(Res.string.library_order_recent)
  SongOrder.MOST_PLAYED -> tr(Res.string.library_order_most_played)
}

/** Opens the system folder chooser and adds the folder to the scanned ones. */
fun chooseFolder(lib: BibliotecaState) {
  SwingUtilities.invokeLater {
    val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) lib.addRoot(chooser.selectedFile)
  }
}

/** A list of songs. [order] adds the A-Z strip when the order has an index. */
@Composable
private fun TrackList(
  lib: BibliotecaState, tracks: List<Track>, favorites: Set<String>, playlists: List<Playlist>, playingId: String?,
  emptyTitle: String, emptyMessage: String,
  order: SongOrder? = null, indexed: Boolean = false, withPlayAll: Boolean = false
) {
  if (tracks.isEmpty()) { EmptyState(emptyTitle, emptyMessage); return }
  val state = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val letters = remember(tracks, order, indexed) {
    if (order == null || !indexed) emptyList()
    else tracks.mapNotNull { LibrarySort.section(it, order) }.distinct()
  }
  Row(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f), state = state) {
      if (withPlayAll) item(key = "play-all") { PlayAllBar(lib, tracks) }
      items(tracks, key = { it.id }) { t ->
        TrackRow(
          t, onClick = { lib.play(t, tracks) }, playing = t.id == playingId, favorite = t.id in favorites,
          menu = { dismiss -> TrackMenu(lib, t, favorites, playlists, dismiss) }
        )
      }
    }
    if (letters.isNotEmpty()) LetterStrip(letters, { letter ->
      val index = tracks.indexOfFirst { LibrarySort.section(it, order!!) == letter }
      if (index >= 0) scope.launch { state.scrollToItem(index) }
    })
  }
}

@Composable
private fun PlayAllBar(lib: BibliotecaState, tracks: List<Track>) {
  Row(Modifier.fillMaxWidth().padding(horizontal = Grid.margin, vertical = Grid.u6), horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
    PrimaryButton(tr(Res.string.library_play), { tracks.firstOrNull()?.let { lib.play(it, tracks) } }, icon = Icons.Filled.PlayArrow)
    TonalButton(tr(Res.string.library_shuffle), { tracks.randomOrNull()?.let { lib.player.toggleShuffle(); lib.play(it, tracks) } }, icon = Icons.Filled.Shuffle)
  }
}

/** The menu of one song: play next, add to the queue, favourite, add to a playlist. */
@Composable
fun TrackMenu(lib: BibliotecaState, track: Track, favorites: Set<String>, playlists: List<Playlist>, dismiss: () -> Unit) {
  val noticeNext = tr(Res.string.library_notice_play_next)
  val noticeQueue = tr(Res.string.library_notice_added_to_queue)
  val noticeAdded = tr(Res.string.library_notice_added_to_playlist)
  val noticeHas = tr(Res.string.library_notice_already_in_playlist)
  MenuEntry(tr(Res.string.library_play), { lib.play(track) }, dismiss)
  MenuEntry(noticeNext, { lib.playNext(track); lib.app.showNotice(noticeNext) }, dismiss)
  MenuEntry(noticeQueue, { lib.addToQueue(track); lib.app.showNotice(noticeQueue) }, dismiss)
  MenuEntry(
    if (track.id in favorites) tr(Res.string.library_favorite_remove) else tr(Res.string.library_section_favorites),
    { lib.store.toggleFavorite(track) }, dismiss
  )
  for (p in playlists) MenuEntry(p.name, {
    if (track.id in p.trackIds) lib.app.showNotice(noticeHas) else { lib.store.addToPlaylist(p.id, track); lib.app.showNotice(noticeAdded) }
  }, dismiss)
}

// ---- groups ----

@Composable
private fun <G : LibraryGroup> GroupList(groups: List<G>, empty: String, onOpen: (G) -> Unit) {
  if (groups.isEmpty()) { EmptyState(empty, ""); return }
  LazyColumn(Modifier.fillMaxSize()) {
    items(groups, key = { it.key }) { g ->
      val artist = (g as? AlbumGroup)?.artist
      val count = trPlural(Res.plurals.library_track_count, g.tracks.size, g.tracks.size)
      Row(Modifier.fillMaxWidth().padding(horizontal = Grid.margin)) {
        Box(Modifier.weight(1f)) {
          ListRow(g.name, { onOpen(g) }, subtitle = listOfNotNull(artist, count).joinToString(" · "))
        }
      }
    }
  }
}

// ---- playlists ----

@Composable
private fun PlaylistsSection(
  lib: BibliotecaState, playlists: List<Playlist>, query: String, byId: Map<String, Track>, onOpen: (String) -> Unit
) {
  var naming by remember { mutableStateOf(false) }
  Row(Modifier.fillMaxWidth().padding(horizontal = Grid.margin)) {
    TonalButton(tr(Res.string.library_new_playlist), { naming = true }, icon = Icons.Filled.Add)
  }
  val shown = remember(playlists, query) {
    if (query.isBlank()) playlists else playlists.filter { LibrarySearch.terms(query).all { w -> com.example.data.TextMatch.fold(it.name).contains(w) } }
  }
  if (shown.isEmpty()) EmptyState(tr(Res.string.library_search_no_playlist), tr(Res.string.library_playlists_empty_note))
  else LazyColumn(Modifier.fillMaxSize()) {
    items(shown, key = { it.id }) { p ->
      val live = p.trackIds.count { it in byId }
      ListRow(p.name, { onOpen(p.id) }, subtitle = trPlural(Res.plurals.library_track_count, live, live))
    }
  }
  if (naming) NamePlaylistDialog(tr(Res.string.library_new_playlist_title), "", tr(Res.string.library_create), { naming = false }) {
    lib.store.createPlaylist(it); naming = false
  }
}

@Composable
private fun PlaylistPage(
  lib: BibliotecaState, playlist: Playlist, byId: Map<String, Track>, favorites: Set<String>, playlists: List<Playlist>,
  playingId: String?, onBack: () -> Unit
) {
  var renaming by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf(false) }
  val tracks = remember(playlist, byId) { playlist.trackIds.mapNotNull { byId[it] } }
  SubPageHeader(playlist.name, tr(Res.string.library_back_label), onBack) {
    TextAction(tr(Res.string.library_rename_desc), { renaming = true })
    IconAction(Icons.Filled.Delete, tr(Res.string.library_delete_desc), { deleting = true })
  }
  if (tracks.isEmpty()) EmptyState(tr(Res.string.library_playlist_empty_title), tr(Res.string.library_playlist_empty_message))
  else {
    PlayAllBar(lib, tracks)
    LazyColumn(Modifier.fillMaxSize()) {
      items(playlist.trackIds.withIndex().toList(), key = { (i, id) -> "$i:$id" }) { (_, id) ->
        val t = byId[id] ?: return@items
        TrackRow(t, onClick = { lib.play(t, tracks) }, playing = t.id == playingId, favorite = t.id in favorites,
          menu = { dismiss ->
            TrackMenu(lib, t, favorites, playlists, dismiss)
            MenuEntry(tr(Res.string.library_playlist_remove, t.title), { lib.store.removeFromPlaylist(playlist.id, t.id) }, dismiss)
          })
      }
    }
  }
  if (renaming) NamePlaylistDialog(tr(Res.string.library_rename_playlist_title), playlist.name, tr(Res.string.library_save), { renaming = false }) {
    lib.store.renamePlaylist(playlist.id, it); renaming = false
  }
  if (deleting) TarkasAlertDialog(
    onDismissRequest = { deleting = false },
    title = { Text(tr(Res.string.library_delete_title, playlist.name), style = GridText.BodyStrong) },
    text = { Text(if (playlist.trackIds.isEmpty()) tr(Res.string.library_delete_body_empty) else trPlural(Res.plurals.library_delete_body, playlist.trackIds.size, playlist.trackIds.size), style = GridText.Body) },
    confirmButton = { TextAction(tr(Res.string.library_delete), { deleting = false; lib.store.deletePlaylist(playlist.id); onBack() }) },
    dismissButton = { TextAction(tr(Res.string.library_cancel), { deleting = false }) }
  )
}

/** Asks for a playlist name. [onDone] gets the text as typed; the store trims it and supplies a default when empty. */
@Composable
fun NamePlaylistDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
  var text by remember { mutableStateOf(initial) }
  TarkasAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = GridText.BodyStrong) },
    text = { OutlinedTextField(text, { text = it }, singleLine = true, textStyle = GridText.Body) },
    confirmButton = { TextAction(confirm, { onDone(text) }) },
    dismissButton = { TextAction(tr(Res.string.library_cancel), onDismiss) }
  )
}

// ---- automatic lists and queue ----

@Composable
private fun AutoSection(library: List<Track>, stats: Map<String, com.example.data.playback.TrackStats>, onOpen: (AutoList) -> Unit) {
  val lists = remember(library, stats) { AutoLists.all(library, stats) }
  LazyColumn(Modifier.fillMaxSize()) {
    items(AutoList.entries.toList(), key = { it.name }) { l ->
      val n = lists[l].orEmpty().size
      ListRow(autoName(l), { onOpen(l) }, subtitle = trPlural(Res.plurals.library_track_count, n, n))
    }
  }
}

@Composable
private fun QueueSection(lib: BibliotecaState, queue: List<Track>, favorites: Set<String>, playlists: List<Playlist>) {
  if (queue.isEmpty()) { EmptyState(tr(Res.string.queue_empty_title), tr(Res.string.queue_empty_message)); return }
  Row(Modifier.fillMaxWidth().padding(horizontal = Grid.margin), verticalAlignment = Alignment.CenterVertically) {
    Text(tr(Res.string.queue_upcoming_header), style = GridText.Label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    TextAction(tr(Res.string.queue_clear), { lib.player.clearQueue() })
  }
  LazyColumn(Modifier.fillMaxSize()) {
    items(queue.withIndex().toList(), key = { (i, t) -> "$i:${t.id}" }) { (i, t) ->
      TrackRow(t, onClick = { lib.player.playQueueItem(i) }, favorite = t.id in favorites,
        trailing = {
          IconAction(Icons.Filled.ArrowUpward, tr(Res.string.queue_move_up), { if (i > 0) lib.player.moveInQueue(i, i - 1) })
          IconAction(Icons.Filled.ArrowDownward, tr(Res.string.queue_move_down), { if (i < queue.lastIndex) lib.player.moveInQueue(i, i + 1) })
        },
        menu = { dismiss ->
          MenuEntry(tr(Res.string.queue_remove), { lib.player.removeFromQueue(i) }, dismiss)
          TrackMenu(lib, t, favorites, playlists, dismiss)
        })
    }
  }
}

// ---- folders ----

@Composable
private fun FoldersPage(lib: BibliotecaState, onBack: () -> Unit) {
  val catalog by lib.catalog.collectAsState()
  val rules by lib.rules.collectAsState()
  val roots by lib.roots.collectAsState()
  var expanded by remember { mutableStateOf(setOf<String>()) }
  val tree = remember(catalog) { FolderTree.build(catalog.folderCounts) }
  val rows = remember(tree, expanded) { FolderTree.rows(tree, expanded) }
  SubPageHeader(tr(Res.string.library_folders_title), tr(Res.string.library_back_label), onBack) {
    IconAction(Icons.Filled.Add, tr(Res.string.library_import_file), { chooseFolder(lib) })
  }
  Text(tr(Res.string.library_folders_note), style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u6))
  if (rows.isEmpty()) {
    EmptyState(tr(Res.string.library_folders_empty_title), tr(Res.string.library_folders_empty_message)) {
      PrimaryButton(tr(Res.string.library_import_audio), { chooseFolder(lib) })
    }
    return
  }
  LazyColumn(Modifier.fillMaxSize()) {
    items(rows, key = { it.node.path }) { row ->
      val node = row.node
      val state = node.state(rules)
      val stateLabel = when (state) {
        FolderCheck.ON -> tr(Res.string.library_folders_state_on)
        FolderCheck.OFF -> tr(Res.string.library_folders_state_off)
        FolderCheck.PARTIAL -> tr(Res.string.library_folders_state_partial)
      }
      Row(Modifier.fillMaxWidth().padding(start = Grid.margin + Grid.u24 * row.depth), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
          ListRow(
            node.name.ifEmpty { tr(Res.string.library_folders_root) },
            { lib.setRules(rules.with(node.path, state == FolderCheck.OFF)) },
            subtitle = trPlural(Res.plurals.library_track_count, node.total, node.total),
            icon = Icons.Filled.Folder, value = stateLabel, chevron = false
          )
        }
        if (node.children.isNotEmpty()) {
          val open = node.path in expanded
          TextAction(
            if (open) tr(Res.string.library_folders_collapse) else tr(Res.string.library_folders_expand),
            { expanded = if (open) expanded - node.path else expanded + node.path }
          )
        }
      }
    }
    item(key = "roots") {
      InsetGroup(Modifier.padding(horizontal = Grid.margin, vertical = Grid.u12)) {
        for (r in roots) ListRow(r.path, { lib.removeRoot(r) }, icon = Icons.Filled.Delete, chevron = false)
      }
    }
  }
}
