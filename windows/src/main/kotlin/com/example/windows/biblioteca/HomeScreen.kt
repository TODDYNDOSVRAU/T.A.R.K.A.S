package com.example.windows.biblioteca

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.example.ui.theme.CoverCarousel
import kotlin.math.abs
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
 * The Início tab: shortcuts in a 4x2 grid, shelves that scroll sideways ("Feito para você", "Novos lançamentos",
 * albums from the free catalogues, what played just now) and, when the window is wide enough, a right panel with the
 * current song and what is known about its artist. Search answers over the library and both catalogues.
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

  // What is new on the free catalogues: loaded once, again when the user asks.
  var reload by remember { mutableStateOf(0) }
  var trendA by remember { mutableStateOf<Online>(Online.Loading) }
  var trendJ by remember { mutableStateOf<Online>(Online.Loading) }
  LaunchedEffect(reload) {
    trendA = Online.Loading; trendJ = Online.Loading
    trendA = fetchOnline { AudiusCatalog.trending() }
    trendJ = fetchOnline { JamendoCatalog.popular() }
  }
  val fresh = remember(trendA, trendJ) {
    val a = (trendA as? Online.Done)?.tracks.orEmpty()
    val j = (trendJ as? Online.Done)?.tracks.orEmpty()
    HomeData.interleave(a, j)
  }
  val albums = remember(fresh) { HomeData.albums(fresh) }
  var showAll by remember { mutableStateOf(setOf<String>()) }
  fun toggle(k: String) { showAll = if (k in showAll) showAll - k else showAll + k }

  BoxWithConstraints(modifier.fillMaxSize()) {
    val panel = maxWidth >= Grid.u384 * 2 && debounced.isBlank()
    Row(Modifier.fillMaxSize()) {
      Column(Modifier.weight(1f).fillMaxHeight()) {
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
            Text(greeting(), style = GridText.Title, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = Grid.u12))
            if (dial.isEmpty()) Empty(lt("Favourites, playlists and the songs you play most will show up here.", "Favoritos, playlists e as músicas mais tocadas aparecem aqui."))
            else dial.take(SHORTCUTS).chunked(SHORTCUT_COLUMNS).forEach { line ->
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
                line.forEach { DialRow(lib, it, Modifier.weight(1f)) }
                repeat(SHORTCUT_COLUMNS - line.size) { Box(Modifier.weight(1f)) }
              }
            }

            Shelf(lt("Listening right now", "Ouvindo agora"), Icons.Filled.Headphones, "now", "now" in showAll, listening.size > 1, ::toggle)
            if (listening.isEmpty()) Empty(lt("Nothing has played yet.", "Nada foi tocado ainda."))
            else Cards(listening, "now" in showAll, { it.id }) { t -> TrackCard(lib, t, listening) }

            val again = mixes.firstOrNull { it.id == "a:RECENTLY_PLAYED" }?.tracks.orEmpty().take(TILES)
            if (again.isNotEmpty()) {
              Shelf(lt("Listen again", "Voltar a ouvir"), Icons.Filled.Shuffle, "again", "again" in showAll, again.size > 1, ::toggle)
              Cards(again, "again" in showAll, { it.id }) { t -> TrackCard(lib, t, again) }
            }

            Shelf(lt("Made for you", "Feito para você"), Icons.Filled.Shuffle, "mix", "mix" in showAll, mixes.size > 1, ::toggle)
            if (mixes.isEmpty()) Empty(lt("Play some songs and your mixes will be built here.", "Toque algumas músicas e seus mixes serão montados aqui."))
            else Cards(mixes, "mix" in showAll, { it.id }) { e ->
              Card(e.cover, e.title, e.subtitle, entryMenu(lib, e)) { e.tracks.firstOrNull()?.let { lib.play(it, e.tracks) } }
            }

            Shelf(lt("New releases", "Novos lançamentos"), Icons.Filled.NewReleases, "new", "new" in showAll, fresh.size > 1, ::toggle)
            OnlineState(trendA, trendJ, fresh.isEmpty()) { reload++ }
            if (fresh.isNotEmpty()) Cards(fresh, "new" in showAll, { it.id }) { t -> TrackCard(lib, t, fresh) }

            if (albums.isNotEmpty()) {
              Shelf(lt("Albums", "Álbuns"), Icons.Filled.Album, "alb", "alb" in showAll, albums.size > 1, ::toggle)
              Cards(albums, "alb" in showAll, { it.id }) { e ->
                Card(e.cover, e.title, e.subtitle, entryMenu(lib, e)) { e.tracks.firstOrNull()?.let { lib.play(it, e.tracks) } }
              }
            }

            if (library.isEmpty()) {
              EmptyState(tr(Res.string.home_mine_empty_title), tr(Res.string.home_mine_empty_message)) {
                TextAction(tr(Res.string.home_mine_empty_action), { chooseFolder(lib) })
              }
            }
          }
        }
      }
      if (panel) NowPanel(lib, ui.current, library, Modifier.width(Grid.u192 + Grid.u96).fillMaxHeight())
    }
  }
}

private const val SHORTCUTS = 6
private const val SHORTCUT_COLUMNS = 3

/** The title of a shelf with "Mostrar tudo" at its end, which spreads the cards over the page instead of one row. */
@Composable
private fun Shelf(title: String, icon: ImageVector, key: String, open: Boolean, more: Boolean, toggle: (String) -> Unit) {
  Row(Modifier.fillMaxWidth().padding(top = Grid.u24, bottom = Grid.u12), verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, Modifier.size(Grid.u24), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      title, style = GridText.BodyStrong, color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(start = Grid.u12).weight(1f)
    )
    if (more) TextAction(if (open) lt("Show less", "Mostrar menos") else lt("Show all", "Mostrar tudo"), { toggle(key) })
  }
}

/** Why the online shelf has nothing yet: loading, no connection, or no Jamendo key. Silent when it has cards. */
@Composable
private fun OnlineState(a: Online, j: Online, empty: Boolean, retry: () -> Unit) {
  if (!empty) return
  when {
    a is Online.Loading || j is Online.Loading -> Empty(tr(Res.string.home_loading))
    a is Online.Offline && j !is Online.Done ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Empty(tr(Res.string.home_offline_catalog_message)) }
        TextAction(tr(Res.string.home_retry), retry)
      }
    j is Online.NoKey && a !is Online.Done -> Empty(tr(Res.string.home_jamendo_missing_key))
    else -> Empty(tr(Res.string.home_catalog_empty))
  }
}

/**
 * One row of cards that scrolls with the wheel, the Left/Right keys and by dragging. A drag released with speed
 * carries on to where [CoverCarousel.project] (the mobile cover carousel's momentum) says it would rest, over 300 ms
 * decelerating; opened ("Mostrar tudo") the cards wrap over the page instead.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> Cards(items: List<T>, open: Boolean, key: (T) -> Any, card: @Composable (T) -> Unit) {
  if (open) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u12), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
      items.forEach { card(it) }
    }
    return
  }
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
          Key.DirectionRight -> { scope.launch { state.animateScrollBy(step, tween(DRAG_MS, easing = LinearOutSlowInEasing)) }; true }
          Key.DirectionLeft -> { scope.launch { state.animateScrollBy(-step, tween(DRAG_MS, easing = LinearOutSlowInEasing)) }; true }
          else -> false
        }
      }
      .pointerInput(Unit) {
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
          onDragStart = { tracker.resetTracking() },
          onDragEnd = {
            val v = tracker.calculateVelocity().x
            val carry = CoverCarousel.project(0f, v)
            if (abs(carry) > 1f) scope.launch { state.animateScrollBy(-carry, tween(DRAG_MS, easing = LinearOutSlowInEasing)) }
          },
          onDragCancel = {},
          onHorizontalDrag = { change, dx ->
            tracker.addPosition(change.uptimeMillis, change.position)
            state.dispatchRawDelta(-dx)
          }
        )
      }
  ) {
    rowItems(items, key = key) { card(it) }
  }
}

/** Motion: 150 to 300 ms with deceleration (T.A.L. identity). */
private const val DRAG_MS = 300

/** A 192 dp cover with title and subtitle; click plays, right-click opens [menu]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Card(cover: Track?, title: String, subtitle: String, menu: List<ContextMenuItem>, onClick: () -> Unit) {
  ContextMenuArea(items = { menu }) {
    Column(Modifier.width(Grid.u192).highlightClickable(onClick = onClick)) {
      Box(Modifier.size(Grid.u192).clip(RoundedCornerShape(Grid.u12))) { cover?.let { TrackArtwork(it, Grid.u192) } }
      Text(title, style = GridText.Body, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Grid.u6))
      Text(subtitle, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackCard(lib: BibliotecaState, t: Track, context: List<Track>) {
  val menu = listOf(
    ContextMenuItem(lt("Play", "Tocar")) { lib.play(t, context) },
    ContextMenuItem(lt("Play next", "Tocar em seguida")) { lib.playNext(t) },
    ContextMenuItem(lt("Add to queue", "Adicionar à fila")) { lib.addToQueue(t) }
  )
  Card(t, t.title, t.artist, menu) { lib.play(t, context) }
}

/**
 * The right panel: the song playing and what MusicBrainz knows about it. Online songs are looked up on their own;
 * for the user's own files the lookup waits for a click, because it sends the title and artist to the network.
 */
@Composable
private fun NowPanel(lib: BibliotecaState, current: Track?, library: List<Track>, modifier: Modifier) {
  val colors = MaterialTheme.colorScheme
  var info by remember(current?.id) { mutableStateOf<RecordingInfo?>(null) }
  var state by remember(current?.id) { mutableStateOf(if (current?.onlineSource != null) "loading" else "idle") }
  LaunchedEffect(current?.id, state) {
    if (current == null || state != "loading") return@LaunchedEffect
    info = runCatching { MusicBrainzCatalog.lookup(current.title, current.artist) }.getOrNull()
    state = if (info != null) "done" else "none"
  }
  Column(
    modifier.padding(end = Grid.margin, top = Grid.u12, bottom = Grid.u12).background(glass(), RoundedCornerShape(Grid.u12))
      .verticalScroll(rememberScrollState()).padding(Grid.u12)
  ) {
    if (current == null) {
      Text(lt("Now playing", "Tocando agora"), style = GridText.BodyStrong, color = colors.onSurface)
      Text(
        lt("Start a song and its details show up here.", "Toque uma música e os detalhes aparecem aqui."),
        style = GridText.Small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Grid.u6)
      )
      return@Column
    }
    Box(Modifier.size(Grid.u192).clip(RoundedCornerShape(Grid.u12)).align(Alignment.CenterHorizontally)) { TrackArtwork(current, Grid.u192) }
    Text(current.title, style = GridText.BodyStrong, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Grid.u12))
    Text(current.artist, style = GridText.Body, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (current.album.isNotBlank()) Text(current.album, style = GridText.Small, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

    Text(lt("About the artist", "Sobre o artista"), style = GridText.BodyStrong, color = colors.onSurface, modifier = Modifier.padding(top = Grid.u24))
    val own = library.count { it.artist.equals(current.artist, ignoreCase = true) }
    if (own > 0) Text(trPluralNow(Res.plurals.library_track_count, own, own), style = GridText.Small, color = colors.onSurfaceVariant)
    val facts = info
    when {
      state == "idle" -> TextAction(lt("Look up on MusicBrainz", "Buscar no MusicBrainz"), { state = "loading" })
      state == "loading" -> Text(tr(Res.string.home_loading), style = GridText.Small, color = colors.onSurfaceVariant)
      facts == null -> Text(lt("Nothing found for this song.", "Nada encontrado para esta música."), style = GridText.Small, color = colors.onSurfaceVariant)
      else -> {
        facts.release?.let { Text(it, style = GridText.Body, color = colors.onSurface) }
        facts.firstReleaseDate?.let { Text(lt("First released ", "Primeiro lançamento ") + it, style = GridText.Small, color = colors.onSurfaceVariant) }
        if (facts.tags.isNotEmpty()) Text(facts.tags.take(5).joinToString(" · "), style = GridText.Small, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Grid.u6))
        Text("MusicBrainz", style = GridText.Label, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = Grid.u6))
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

/** "Bom dia, name" by the local hour; the name is the Windows account's. */
internal fun greeting(hour: Int = java.time.LocalTime.now().hour, name: String = System.getProperty("user.name").orEmpty()): String {
  val en = when { hour < 5 -> "Good evening"; hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
  val pt = when { hour < 5 -> "Boa noite"; hour < 12 -> "Bom dia"; hour < 18 -> "Boa tarde"; else -> "Boa noite" }
  val base = lt(en, pt)
  return if (name.isBlank()) base else "$base, $name"
}
