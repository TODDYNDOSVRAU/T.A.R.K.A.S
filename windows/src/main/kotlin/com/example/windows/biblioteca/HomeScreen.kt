package com.example.windows.biblioteca

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.library.AutoList
import com.example.data.model.Track
import com.example.data.playback.LibrarySearch
import com.example.ui.components.TextAction
import com.example.ui.components.TonalButton
import com.example.ui.components.PrimaryButton
import com.example.ui.components.highlightClickable
import com.example.ui.theme.CoverCarousel
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.studioCard
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.res.*
import com.example.windows.shell.glass
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * The Início tab, a page of discovery: a featured card, then carousels that scroll sideways (what is playing and what
 * played just now, mixes made from the library, albums, the free catalogues). Importing and managing local music
 * lives in Biblioteca. The search field lives in the shell's dock: [query] is what it holds, and a non-blank query
 * swaps the page for results over the library and both catalogues.
 *
 * [queueStack] is the player's stack of queue cards. It gets a column at the lower right, beside the page and never
 * over it, and only when the window is wide enough.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(lib: BibliotecaState, modifier: Modifier = Modifier, query: String = "", queueStack: (@Composable () -> Unit)? = null) {
  val debounced by produceDebounced(query)

  val library by lib.library.collectAsState()
  val stats by lib.store.stats.collectAsState()
  val ui by lib.player.ui.collectAsState()
  val mixes = remember(library, stats) {
    HomeData.mixes(library, stats, { autoName0(it) }) { trPluralNow(Res.plurals.library_track_count, it, it) }
  }
  val listening = remember(ui.current, ui.history) { HomeData.listening(ui.current, ui.history) }
  val libraryAlbums = remember(library) { HomeData.albums(library.distinctBy { it.id }) }

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
  val featured = remember(mixes, libraryAlbums, albums) { HomeData.featured(mixes, libraryAlbums + albums) }
  var showAll by remember { mutableStateOf(setOf<String>()) }
  fun toggle(k: String) { showAll = if (k in showAll) showAll - k else showAll + k }

  BoxWithConstraints(modifier.fillMaxSize()) {
    val stackWidth = queueWidth(maxWidth)
    Row(Modifier.fillMaxSize()) {
      if (debounced.isNotBlank()) {
        LazyVerticalGrid(
          GridCells.Adaptive(Grid.u96 + Grid.u24), Modifier.weight(1f).fillMaxHeight(),
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
        Column(
          Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
            .padding(start = Grid.margin, end = if (stackWidth > 0.dp) Grid.u12 else Grid.margin, top = Grid.u12, bottom = Grid.u24)
        ) {
          Text(greeting(), style = GridText.Title, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = Grid.u12))
          if (library.isEmpty()) EmptyHint(lib)
          featured?.let { Hero(lib, it) }
          ui.current?.let { AboutCard(it, library) }

          Rail(lt("Continue listening", "Continuar ouvindo"), "now", listening, { it.id }, "now" in showAll, ::toggle) { t -> TrackCard(lib, t, listening) }
          val picks = mixes.filter { it.id != featured?.id }
          Rail(lt("Made for you", "Feito para você"), "mix", picks, { it.id }, "mix" in showAll, ::toggle) { e -> EntryCard(lib, e) }
          val mine = libraryAlbums.filter { it.id != featured?.id }
          Rail(lt("Your albums", "Seus álbuns"), "lib", mine, { it.id }, "lib" in showAll, ::toggle) { e -> EntryCard(lib, e) }
          val again = mixes.firstOrNull { it.id == "a:" + AutoList.RECENTLY_PLAYED.name }?.tracks.orEmpty().take(TILES)
          Rail(lt("Listen again", "Voltar a ouvir"), "again", again, { it.id }, "again" in showAll, ::toggle) { t -> TrackCard(lib, t, again) }
          val recommended = albums.filter { it.id != featured?.id }
          Rail(lt("Recommended albums", "Álbuns recomendados"), "alb", recommended, { it.id }, "alb" in showAll, ::toggle) { e -> EntryCard(lib, e) }

          Shelf(lt("Free catalogues", "Catálogos gratuitos"), "new", "new" in showAll, fresh.size > 1, ::toggle)
          OnlineState(trendA, trendJ, fresh.isEmpty()) { reload++ }
          // The albums rail above already shows these covers; this one lists the other songs.
          val albumCovers = recommended.mapNotNull { it.cover?.id }.toSet()
          val others = fresh.filter { it.id !in albumCovers }.ifEmpty { fresh }
          if (others.isNotEmpty()) Cards(others, "new" in showAll, { it.id }) { t -> TrackCard(lib, t, others) }
        }
      }
      if (queueStack != null && stackWidth > 0.dp) {
        Box(Modifier.width(stackWidth).fillMaxHeight().padding(end = Grid.margin, top = Grid.u12, bottom = Grid.u12), contentAlignment = Alignment.BottomEnd) {
          Box(Modifier.fillMaxWidth().heightIn(max = stackWidth * 5 / 4)) { queueStack() }
        }
      }
    }
  }
}

/** The query as the page should act on it: trimmed, and only after the typing paused for 350 ms. */
@Composable
private fun produceDebounced(query: String) = androidx.compose.runtime.produceState("", query) {
  if (query.isBlank()) value = "" else { delay(350); value = query.trim() }
}

/** The width of the queue column at the lower right: none when the window is narrow, 216 dp on laptops, 288 dp on wide screens. */
internal fun queueWidth(page: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = when {
  page >= Grid.u192 * 9 -> Grid.u192 + Grid.u96
  page >= Grid.u192 * 5 + Grid.u96 -> Grid.u192 + Grid.u24
  else -> 0.dp
}

/** The title of a shelf with "Mostrar tudo" at its end, which spreads the cards over the page instead of one row. */
@Composable
private fun Shelf(title: String, key: String, open: Boolean, more: Boolean, toggle: (String) -> Unit) {
  Row(Modifier.fillMaxWidth().padding(top = Grid.u24, bottom = Grid.u12), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = GridText.BodyStrong, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
    if (more) TextAction(if (open) lt("Show less", "Mostrar menos") else lt("Show all", "Mostrar tudo"), { toggle(key) })
  }
}

/** A titled carousel; nothing at all when there is nothing to show, so the page never keeps an empty block. */
@Composable
private fun <T> Rail(title: String, key: String, all: List<T>, id: (T) -> Any, open: Boolean, toggle: (String) -> Unit, card: @Composable (T) -> Unit) {
  if (all.isEmpty()) return
  Shelf(title, key, open, all.size > 1, toggle)
  Cards(all, open, id, card)
}

/** The featured entry: a 192 dp cover, what it is, and the two ways to play it. */
@Composable
private fun Hero(lib: BibliotecaState, e: HomeEntry) {
  val colors = MaterialTheme.colorScheme
  Row(
    Modifier.fillMaxWidth().padding(top = Grid.u12).studioCard().padding(Grid.u12),
    horizontalArrangement = Arrangement.spacedBy(Grid.u24), verticalAlignment = Alignment.CenterVertically
  ) {
    Box(Modifier.size(Grid.u192).clip(RoundedCornerShape(Grid.u12))) { e.cover?.let { TrackArtwork(it, Grid.u192) } }
    Column(Modifier.weight(1f)) {
      Text(lt("Featured", "Em destaque"), style = GridText.Label, color = colors.primary)
      Text(e.title, style = GridText.Title, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Grid.u6))
      Text(e.subtitle, style = GridText.Body, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Row(Modifier.padding(top = Grid.u24), horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
        PrimaryButton(lt("Play", "Tocar"), { e.tracks.firstOrNull()?.let { lib.play(it, e.tracks) } }, icon = Icons.Filled.PlayArrow)
        TonalButton(lt("Shuffle", "Aleatório"), { e.tracks.shuffled().let { s -> s.firstOrNull()?.let { lib.play(it, s) } } }, icon = Icons.Filled.Shuffle)
      }
    }
  }
}

/** With no local music yet, the way in: importing happens in Biblioteca. */
@Composable
private fun EmptyHint(lib: BibliotecaState) {
  Row(
    Modifier.fillMaxWidth().studioCard().padding(Grid.u12),
    horizontalArrangement = Arrangement.spacedBy(Grid.u12), verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      lt("Your own music is not here yet. Add a folder or files in Biblioteca; the free catalogues below work meanwhile.", "Sua própria música ainda não está aqui. Adicione uma pasta ou arquivos na Biblioteca; os catálogos gratuitos abaixo funcionam enquanto isso."),
      style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
    )
    TonalButton(lt("Open Biblioteca", "Abrir Biblioteca"), { lib.app.selectTab(TarkasNavTab.LIBRARY) })
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
private fun <T> Cards(all: List<T>, open: Boolean, key: (T) -> Any, card: @Composable (T) -> Unit) {
  val items = remember(all) { all.distinctBy(key) }
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
      .sidewaysScroll(state, scope, step)
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

/**
 * Wheel and touchpad scrolling for a row inside a page that scrolls up and down. Each event goes to one axis only, the
 * dominant one: mostly sideways (or vertical with Shift held) moves the row and is consumed; anything else is left
 * untouched so the page scrolls. The row never moves vertically and never takes the page's vertical scroll.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.sidewaysScroll(state: LazyListState, scope: CoroutineScope, step: Float): Modifier =
  onPointerEvent(PointerEventType.Scroll) { ev ->
    val d = ev.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
    val delta = sidewaysDelta(d.x, d.y, ev.keyboardModifiers.isShiftPressed) ?: return@onPointerEvent
    ev.changes.forEach { it.consume() }
    scope.launch { state.scrollBy(delta * step / 3) }
  }

/** The sideways part of a scroll movement ([dx], [dy]), or null when it belongs to the page's vertical scroll. */
internal fun sidewaysDelta(dx: Float, dy: Float, shift: Boolean): Float? = when {
  abs(dx) > abs(dy) -> dx
  shift && dy != 0f -> dy
  else -> null
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

@Composable
private fun EntryCard(lib: BibliotecaState, e: HomeEntry) =
  Card(e.cover, e.title, e.subtitle, entryMenu(lib, e)) { e.tracks.firstOrNull()?.let { lib.play(it, e.tracks) } }

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
 * What is known about the artist of the song playing (the player on the left already shows the cover and title).
 * Online songs are looked up on their own; for the user's own files the lookup waits for a click, because it sends
 * the title and artist to the network.
 */
@Composable
private fun AboutCard(current: Track, library: List<Track>) {
  val colors = MaterialTheme.colorScheme
  var info by remember(current.id) { mutableStateOf<RecordingInfo?>(null) }
  var state by remember(current.id) { mutableStateOf(if (current.onlineSource != null) "loading" else "idle") }
  LaunchedEffect(current.id, state) {
    if (state != "loading") return@LaunchedEffect
    info = runCatching { MusicBrainzCatalog.lookup(current.title, current.artist) }.getOrNull()
    state = if (info != null) "done" else "none"
  }
  Column(Modifier.fillMaxWidth().padding(top = Grid.u12).studioCard().padding(Grid.u12)) {
    Text(lt("About ${current.artist}", "Sobre ${current.artist}"), style = GridText.BodyStrong, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun Empty(text: String) {
  Box(Modifier.fillMaxWidth().background(glass(), RoundedCornerShape(Grid.u12)).padding(Grid.u12)) {
    Text(text, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
  // A catalogue can repeat an id; a repeated key in a lazy list crashes the app, so each id shows once.
  items(list.distinctBy { it.id }, key = { "$tag:" + it.id }) { Tile(lib, it, list) }
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
