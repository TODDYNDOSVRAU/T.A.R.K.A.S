package com.example.windows.biblioteca

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import com.example.windows.shell.glass
import kotlinx.coroutines.launch
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.library.AutoList
import com.example.data.library.AutoLists
import com.example.data.model.Track
import com.example.data.playback.LibrarySearch
import com.example.ui.components.SearchField
import com.example.ui.components.TextAction
import com.example.ui.components.highlightClickable
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.*
import com.example.windows.shell.ShellSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** What an online list is doing: shown as tiles, or why it is not. */
private sealed interface Online {
  data object Loading : Online
  data object Offline : Online
  data object NoKey : Online
  data class Done(val tracks: List<Track>) : Online
}

private suspend fun fetchOnline(block: suspend () -> List<Track>): Online = try {
  Online.Done(block())
} catch (e: CancellationException) {
  throw e
} catch (_: JamendoNotConfigured) {
  Online.NoKey
} catch (_: Exception) {
  Online.Offline
}

private const val TILES = 12

/**
 * The Início tab as a dashboard: what to resume and the numbers of the library on top, then a grid of covers for
 * the recent, most played and newly added songs and the two online lists. Search answers over the library and both catalogues.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(lib: BibliotecaState, modifier: Modifier = Modifier) {
  var query by remember { mutableStateOf("") }
  var debounced by remember { mutableStateOf("") }
  LaunchedEffect(query) { delay(350); debounced = query.trim() }
  val searchFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { ShellSearch.requests.collect { if (it > 0) runCatching { searchFocus.requestFocus() } } }

  val library by lib.library.collectAsState()
  val stats by lib.store.stats.collectAsState()
  val favorites by lib.store.favorites.collectAsState()
  val playlists by lib.store.playlists.collectAsState()
  val ui by lib.player.ui.collectAsState()
  val favLabel = tr(Res.string.library_section_favorites)
  val dial = remember(library, favorites, playlists, stats, favLabel) {
    HomeData.speedDial(library, favorites.toSet(), playlists, stats, favLabel) { trPluralNow(Res.plurals.library_track_count, it, it) }
  }
  val mixes = remember(library, stats) {
    HomeData.mixes(library, stats, { autoName0(it) }) { trPluralNow(Res.plurals.library_track_count, it, it) }
  }
  val listening = remember(ui.current, ui.history) { HomeData.listening(ui.current, ui.history) }

  var found by remember { mutableStateOf<Online?>(null) }
  var foundJamendo by remember { mutableStateOf<Online?>(null) }
  LaunchedEffect(debounced) {
    if (debounced.isBlank()) { found = null; foundJamendo = null; return@LaunchedEffect }
    found = Online.Loading; foundJamendo = Online.Loading
    found = fetchOnline { AudiusCatalog.search(debounced) }
    foundJamendo = fetchOnline { JamendoCatalog.search(debounced) }
  }

  Column(modifier.fillMaxSize()) {
    SearchField(
      query, { query = it }, tr(Res.string.home_search_placeholder),
      Modifier.fillMaxWidth().padding(horizontal = Grid.margin, vertical = Grid.u12).focusRequester(searchFocus)
    )
    if (debounced.isNotBlank()) {
      LazyVerticalGrid(
        GridCells.Adaptive(Grid.u96 + Grid.u24), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Grid.margin, vertical = Grid.u12),
        horizontalArrangement = Arrangement.spacedBy(Grid.u12), verticalArrangement = Arrangement.spacedBy(Grid.u12)
      ) {
        val mine = LibrarySearch.filter(library, debounced).take(TILES * 2)
        header(trNow(Res.string.home_search_library_header))
        if (mine.isEmpty()) note(trNow(Res.string.home_search_library_empty)) else tiles(lib, mine, "m")
        header("Audius"); online(lib, found, "fa") {}
        header("Jamendo"); online(lib, foundJamendo, "fj") {}
      }
    } else {
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Grid.margin, vertical = Grid.u12)) {
        Section(lt("Speed dial", "Acesso rápido"), Icons.Filled.Bolt)
        if (dial.isEmpty()) Empty(lt("Favourites, playlists and the songs you play most will show up here.", "Favoritos, playlists e as músicas mais tocadas aparecem aqui."))
        else dial.chunked(2).forEach { pair ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
            pair.forEach { DialRow(lib, it, Modifier.weight(1f)) }
            if (pair.size == 1) Box(Modifier.weight(1f))
          }
        }
        Section(lt("Daily Mixes", "Mixes do dia"), Icons.Filled.Shuffle)
        if (mixes.isEmpty()) Empty(lt("Play some songs and your mixes will be built here.", "Toque algumas músicas e seus mixes serão montados aqui."))
        else MixRow(lib, mixes)
        Section(lt("Listening right now", "Ouvindo agora"), Icons.Filled.Headphones)
        if (listening.isEmpty()) Empty(lt("Nothing has played yet.", "Nada foi tocado ainda."))
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
          rowItems(listening, key = { it.id }) { Tile(lib, it, listening) }
        }
        if (library.isEmpty()) {
          EmptyState(tr(Res.string.home_mine_empty_title), tr(Res.string.home_mine_empty_message)) {
            TextAction(tr(Res.string.home_mine_empty_action), { chooseFolder(lib) })
          }
        }
      }
    }
  }
}

@Composable
private fun Section(title: String, icon: ImageVector) {
  Row(Modifier.padding(top = Grid.u24, bottom = Grid.u12), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(Grid.u24), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(title, style = GridText.BodyStrong, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = Grid.u12))
  }
}

@Composable
private fun Empty(text: String) {
  Box(Modifier.fillMaxWidth().background(glass(), RoundedCornerShape(Grid.u12)).padding(Grid.u12)) {
    Text(text, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** A speed-dial row: 48 dp cover, title and subtitle; a rounded tile shows on hover. Click plays, right-click opens the menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialRow(lib: BibliotecaState, e: HomeEntry, modifier: Modifier) {
  val source = remember { MutableInteractionSource() }
  val hovered by source.collectIsHoveredAsState()
  val colors = MaterialTheme.colorScheme
  val tile = glass()
  ContextMenuArea(items = { entryMenu(lib, e) }) {
    Row(
      modifier.padding(bottom = Grid.u12).hoverable(source)
        .background(if (hovered) tile else Color.Transparent, RoundedCornerShape(Grid.u12))
        .highlightClickable(onClick = { e.tracks.firstOrNull()?.let { lib.play(it, e.tracks) } }).padding(Grid.u6),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(Modifier.size(Grid.u48).clip(RoundedCornerShape(Grid.u6))) { e.cover?.let { TrackArtwork(it, Grid.u48) } }
      Column(Modifier.padding(start = Grid.u12)) {
        Text(e.title, style = GridText.Body, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(e.subtitle, style = GridText.Small, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** The Daily Mixes row: 192 dp covers that scroll with the wheel and the Left/Right keys. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MixRow(lib: BibliotecaState, mixes: List<HomeEntry>) {
  val state = rememberLazyListState()
  val scope = rememberCoroutineScope()
  val step = with(LocalDensity.current) { (Grid.u192 + Grid.u12).toPx() }
  LazyRow(
    state = state, horizontalArrangement = Arrangement.spacedBy(Grid.u12),
    modifier = Modifier.focusable()
      .onPointerEvent(PointerEventType.Scroll) { ev ->
        val d = ev.changes.first().scrollDelta
        scope.launch { state.scrollBy((if (d.x != 0f) d.x else d.y) * step / 3) }
      }
      .onKeyEvent {
        if (it.type != KeyEventType.KeyDown) false
        else when (it.key) {
          Key.DirectionRight -> { scope.launch { state.animateScrollBy(step) }; true }
          Key.DirectionLeft -> { scope.launch { state.animateScrollBy(-step) }; true }
          else -> false
        }
      }
  ) {
    rowItems(mixes, key = { it.id }) { e ->
      ContextMenuArea(items = { entryMenu(lib, e) }) {
        Column(Modifier.width(Grid.u192).highlightClickable(onClick = { e.tracks.firstOrNull()?.let { lib.play(it, e.tracks) } })) {
          Box(Modifier.size(Grid.u192).clip(RoundedCornerShape(Grid.u12))) { e.cover?.let { TrackArtwork(it, Grid.u192) } }
          Text(e.title, style = GridText.Body, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Grid.u6))
          Text(e.subtitle, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
      }
    }
  }
}

private fun entryMenu(lib: BibliotecaState, e: HomeEntry) = listOf(
  ContextMenuItem(lt("Play", "Tocar")) { e.tracks.firstOrNull()?.let { lib.play(it, e.tracks) } },
  ContextMenuItem(lt("Play next", "Tocar em seguida")) { e.tracks.asReversed().forEach { lib.playNext(it) } },
  ContextMenuItem(lt("Add to queue", "Adicionar à fila")) { e.tracks.forEach { lib.addToQueue(it) } }
)

private fun autoName0(l: AutoList): String = when (l) {
  AutoList.RECENTLY_ADDED -> trNow(Res.string.library_auto_recently_added)
  AutoList.RECENTLY_PLAYED -> trNow(Res.string.library_auto_recently_played)
  AutoList.MOST_PLAYED -> trNow(Res.string.library_auto_most_played)
}

private fun LazyGridScope.header(text: String) {
  item(key = "h:$text", span = { GridItemSpan(maxLineSpan) }) {
    Text(text, style = GridText.BodyStrong, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = Grid.u12))
  }
}

private fun LazyGridScope.note(text: String) {
  // No key: the same note ("Carregando", "Sem conexão") shows under several sections at once, and keys must be unique.
  item(span = { GridItemSpan(maxLineSpan) }) {
    Text(text, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun LazyGridScope.tiles(lib: BibliotecaState, list: List<Track>, tag: String) {
  items(list, key = { "$tag:" + it.id }) { Tile(lib, it, list) }
}

private fun LazyGridScope.online(lib: BibliotecaState, state: Online?, tag: String, onRetry: () -> Unit) {
  when (state) {
    null -> {}
    Online.Loading -> note(trNow(Res.string.home_loading))
    Online.Offline -> {
      note(trNow(Res.string.home_offline_catalog_message))
      item(key = "retry:$tag", span = { GridItemSpan(maxLineSpan) }) { TextAction(tr(Res.string.home_retry), onRetry) }
    }
    Online.NoKey -> note(trNow(Res.string.home_jamendo_missing_key))
    is Online.Done -> if (state.tracks.isEmpty()) note(trNow(Res.string.home_catalog_empty)) else tiles(lib, state.tracks.take(TILES), tag)
  }
}

@Composable
private fun Figure(value: String, label: String) {
  Column {
    Text(value, style = GridText.Title, color = MaterialTheme.colorScheme.onSurface)
    Text(label, style = GridText.Label, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** A cover with its title and artist: click plays inside the list, right-click offers play next and add to queue. */
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
private fun Tile(lib: BibliotecaState, t: Track, context: List<Track>) {
  ContextMenuArea(items = {
    listOf(
      ContextMenuItem(lt("Play", "Tocar")) { lib.play(t, context) },
      ContextMenuItem(lt("Play next", "Tocar em seguida")) { lib.playNext(t) },
      ContextMenuItem(lt("Add to queue", "Adicionar à fila")) { lib.addToQueue(t) }
    )
  }) {
    Column(Modifier.highlightClickable(onClick = { lib.play(t, context) })) {
      TrackArtwork(t, Grid.u96)
      Text(t.title, style = GridText.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
      Text(t.artist, style = GridText.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
