package com.example.windows.biblioteca

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
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
import com.example.windows.shell.LocalCornerInsets
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
 * lives in Biblioteca. The greeting is the shell's top title (see [BibliotecaHost]). Search lives in Biblioteca now
 * (Ctrl+F), so the shell passes no [query]; a non-blank one still swaps the page for results over the library and
 * both catalogues, which is how the online results will be reused there.
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

  val reduce by lib.app.reduceMotion.collectAsState()
  CompositionLocalProvider(LocalSnap provides reduce, LocalCoverFade provides !reduce) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    // The stack column is reserved only when a stack is drawn; otherwise the page owns the full width (and the lanes are measured on it).
    val stackWidth = if (queueStack != null) queueWidth(maxWidth) else 0.dp
    val pageWidth = maxWidth - stackWidth
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
        // The player's corner (bottom left) takes a lane from the left edge: wide windows centre the column in the free
        // margin, medium ones start the whole column after the lane, narrow ones have no card to step around. Every
        // section below shares that one left edge (contentPadding); none adds an indent of its own.
        val lanes = HomeLayout.lanes(pageWidth, LocalCornerInsets.current, end = if (stackWidth > 0.dp) Grid.u12 else Grid.margin)
        // Which carousels show: empty ones and ones that only repeat the one before are skipped, and four at most.
        val picks = mixes.filter { it.id != featured?.id }
        val mine = libraryAlbums.filter { it.id != featured?.id }
        val again = mixes.firstOrNull { it.id == "a:" + AutoList.RECENTLY_PLAYED.name }?.tracks.orEmpty().take(TILES)
        val recommended = albums.filter { it.id != featured?.id }
        val plan = remember(listening, picks, mine, again, recommended) {
          HomeData.railsToShow(
            listOf(
              "now" to listening.map { it.id }, "mix" to picks.map { it.id }, "lib" to mine.map { it.id },
              "again" to again.map { it.id }, "alb" to recommended.map { it.id }
            )
          )
        }
        LazyColumn(
          Modifier.weight(1f).fillMaxHeight(),
          contentPadding = PaddingValues(start = lanes.start, end = lanes.end, top = Grid.u12, bottom = Grid.u24)
        ) {
          // The greeting lives in the shell's top band (BibliotecaHost registers it as the page title).
          if (library.isEmpty()) item(key = "empty") { Box(Modifier.testTag(TAG_SECTION)) { EmptyHint(lib) } }
          featured?.let { f -> item(key = "hero") { Box(Modifier.testTag(TAG_SECTION)) { Hero(lib, f) } } }
          for (rail in plan) item(key = rail) {
            Column(Modifier.testTag(TAG_SECTION)) {
              when (rail) {
                "now" -> Rail(lt("Continue listening", "Continuar ouvindo"), "now", listening, { it.id }, "now" in showAll, ::toggle) { t -> TrackCard(lib, t, listening) }
                "mix" -> Rail(lt("Made for you", "Feito para você"), "mix", picks, { it.id }, "mix" in showAll, ::toggle) { e -> EntryCard(lib, e) }
                "lib" -> Rail(lt("Your albums", "Seus álbuns"), "lib", mine, { it.id }, "lib" in showAll, ::toggle) { e -> EntryCard(lib, e) }
                "again" -> Rail(lt("Listen again", "Voltar a ouvir"), "again", again, { it.id }, "again" in showAll, ::toggle) { t -> TrackCard(lib, t, again) }
                "alb" -> Rail(lt("Recommended albums", "Álbuns recomendados"), "alb", recommended, { it.id }, "alb" in showAll, ::toggle) { e -> EntryCard(lib, e) }
              }
            }
          }
          item(key = "new") {
            Column(Modifier.testTag(TAG_SECTION)) {
              Shelf(lt("Free catalogues", "Catálogos gratuitos"), "new", "new" in showAll, fresh.size > 1, ::toggle)
              OnlineState(trendA, trendJ, fresh.isEmpty()) { reload++ }
              // The albums rail above, when it is shown, already has these covers; this one lists the other songs.
              val albumCovers = if ("alb" in plan) recommended.mapNotNull { it.cover?.id }.toSet() else emptySet()
              val others = fresh.filter { it.id !in albumCovers }.ifEmpty { fresh }
              if (others.isNotEmpty()) Cards(others, "new" in showAll, { it.id }) { t -> TrackCard(lib, t, others) }
            }
          }
          ui.current?.let { c -> item(key = "about") { Box(Modifier.testTag(TAG_SECTION)) { AboutCard(c, library) } } }
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
}

/** Every top-level section of the Início (hero, carousels, catalogues, about): they all share one left edge. */
internal const val TAG_SECTION = "home-section"

/** True where the page must not animate (reduced motion): every transition snaps. */
internal val LocalSnap = staticCompositionLocalOf { false }

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
  // 18 above and 6 below: the row under it keeps 6 dp of room all round for the scaled card, so the spacing stays 24 and 12.
  Row(Modifier.fillMaxWidth().padding(top = Grid.u12 + Grid.u6, bottom = Grid.u6), verticalAlignment = Alignment.CenterVertically) {
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

/** The featured entry. When it changes, the old one fades out as the new one fades in (300 ms; at once under reduced motion). */
@Composable
private fun Hero(lib: BibliotecaState, e: HomeEntry) {
  val snap = LocalSnap.current
  Crossfade(e, animationSpec = if (snap) snap() else tween(HERO_FADE_MS), label = "hero") { HeroBody(lib, it) }
}

/** A 192 dp cover, what the featured entry is, and the two ways to play it. */
@Composable
private fun HeroBody(lib: BibliotecaState, e: HomeEntry) {
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
        Box(Modifier.hoverScale()) {
          PrimaryButton(lt("Play", "Tocar"), { e.tracks.firstOrNull()?.let { lib.play(it, e.tracks) } }, icon = Icons.Filled.PlayArrow)
        }
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
    a is Online.Loading || j is Online.Loading -> SkeletonRow()
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
 * One row of cards that scrolls with the wheel, the Left/Right keys and by dragging. Keys and a drag released with
 * speed move it with a critically damped spring ([SpringScroller]) that keeps its speed when another key press arrives
 * in flight; a drag carries on to where [CoverCarousel.project] (the mobile cover carousel's momentum) says it would
 * rest. Opened ("Mostrar tudo") the cards wrap over the page instead.
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
  val snap = LocalSnap.current
  val scroller = remember(state, snap) { SpringScroller(state, snap) }
  val step = with(LocalDensity.current) { (Grid.u192 + Grid.u12).toPx() }
  LazyRow(
    state = state, horizontalArrangement = Arrangement.spacedBy(Grid.u12),
    contentPadding = ROW_ROOM,
    modifier = Modifier.bleed(Grid.u6).focusable()
      .sidewaysScroll(state, scope, step)
      .onKeyEvent {
        if (it.type != KeyEventType.KeyDown) false
        else when (it.key) {
          Key.DirectionRight -> { scope.launch { scroller.by(step) }; true }
          Key.DirectionLeft -> { scope.launch { scroller.by(-step) }; true }
          else -> false
        }
      }
      .pointerInput(scroller) {
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
          onDragStart = { tracker.resetTracking(); scope.launch { scroller.stop() } },
          onDragEnd = {
            val v = tracker.calculateVelocity().x
            val carry = CoverCarousel.project(0f, v)
            if (abs(carry) > 1f) scope.launch { scroller.by(-carry, -v) }
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

/** Room around a row of cards, so a card scaled up on hover (1.04) is not cut off by the row's edge. */
private val ROW_ROOM = PaddingValues(horizontal = Grid.u6, vertical = Grid.u6)

/** Makes the row [by] wider on each side and shifts it back, so the cards keep their place on the page and the room around them lies outside it. */
private fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
  val b = by.roundToPx()
  val p = measurable.measure(constraints.offset(horizontal = 2 * b))
  layout(p.width - 2 * b, p.height) { p.place(-b, 0) }
}

/**
 * Scrolls a row with a critically damped spring ([SCROLL_SPEC]). The distance still to go and the speed are kept, so a
 * key press that arrives mid-flight adds to the target and carries the speed on instead of restarting from rest. Under
 * reduced motion ([snap]) it jumps.
 */
internal class SpringScroller(private val state: LazyListState, private val snap: Boolean) {
  private var remaining = 0f
  private var speed = 0f
  private var generation = 0

  /** Moves [distance] pixels on, plus whatever is left of the movement in flight; [flick] is a release speed to start from. */
  suspend fun by(distance: Float, flick: Float = 0f) {
    if (snap) { state.scrollBy(distance); return }
    val target = remaining + distance
    val from = HomeData.springVelocity(speed + flick, target)
    val mine = ++generation
    remaining = target; speed = from
    try {
      state.scroll {
        var last = 0f
        AnimationState(0f, from).animateTo(target, SCROLL_SPEC) {
          val d = value - last
          last = value
          val got = scrollBy(d)
          if (mine == generation) { remaining = target - value; speed = velocity }
          if (abs(d - got) > 0.5f) cancelAnimation()
        }
      }
    } finally {
      if (mine == generation) { remaining = 0f; speed = 0f }
    }
  }

  /** Stops the movement in flight where it is (a finger came down on the row). */
  suspend fun stop() {
    generation++; remaining = 0f; speed = 0f
    state.scroll(MutatePriority.UserInput) {}
  }
}

/** The spring of every Home movement: critically damped, stiffness 439 (about 300 ms to settle), no overshoot. */
private val SETTLE_SPEC = spring<Float>(dampingRatio = 1f, stiffness = HomeData.SETTLE_STIFFNESS)
private val SCROLL_SPEC = spring<Float>(dampingRatio = 1f, stiffness = HomeData.SETTLE_STIFFNESS, visibilityThreshold = 0.5f)

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

/** Hero crossfade: 150 to 300 ms (T.A.L. identity). */
private const val HERO_FADE_MS = 300

/**
 * What the pointer is doing to a control: hovered (scaled up and lifted) or pressed (scaled down). Both are read from
 * a source and animated with [SETTLE_SPEC]; the values are read only inside layer blocks, so a frame of the movement
 * recomposes nothing. [snap] (reduced motion) jumps instead.
 */
private class PointerMotion(val scale: State<Float>, val lift: State<Float>)

@Composable
private fun rememberPointerMotion(source: MutableInteractionSource, snap: Boolean): PointerMotion {
  val hovered by source.collectIsHoveredAsState()
  val pressed by source.collectIsPressedAsState()
  val spec: androidx.compose.animation.core.AnimationSpec<Float> = if (snap) snap() else SETTLE_SPEC
  val scale = animateFloatAsState(HomeData.cardScale(hovered, pressed), spec, label = "scale")
  val lift = animateFloatAsState(if (hovered && !pressed) 1f else 0f, spec, label = "lift")
  return remember(scale, lift) { PointerMotion(scale, lift) }
}

/**
 * Reports hover and press on [source] without taking part: the press is seen on the initial pass and never consumed, so
 * the click and the drag below it work as before. Put it outside the graphics layer that scales, so the area that
 * counts as hovered does not change with the scale.
 */
private fun Modifier.pointerTracking(source: MutableInteractionSource): Modifier =
  hoverable(source).pointerInput(source) {
    awaitPointerEventScope {
      var press: PressInteraction.Press? = null
      while (true) {
        val ev = awaitPointerEvent(PointerEventPass.Initial)
        val down = ev.changes.any { it.pressed }
        val before = press
        if (down && before == null) {
          val p = PressInteraction.Press(ev.changes.first().position)
          press = p; source.tryEmit(p)
        } else if (!down && before != null) {
          source.tryEmit(PressInteraction.Release(before)); press = null
        }
      }
    }
  }

/** Hover scale 1.04 and press scale 0.96 on whatever this is applied to. */
@Composable
private fun Modifier.hoverScale(): Modifier {
  val source = remember { MutableInteractionSource() }
  val m = rememberPointerMotion(source, LocalSnap.current)
  return pointerTracking(source).graphicsLayer { scaleX = m.scale.value; scaleY = m.scale.value }
}

/**
 * A 192 dp cover with title and subtitle; click plays, right-click opens [menu]. Under the pointer the card scales to
 * 1.04 and its cover takes a tonal lift, pressed it scales to 0.96; both run on the GPU layer only. Reached by keyboard
 * it shows a focus ring.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Card(cover: Track?, title: String, subtitle: String, menu: List<ContextMenuItem>, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val source = remember { MutableInteractionSource() }
  val m = rememberPointerMotion(source, LocalSnap.current)
  var focused by remember { mutableStateOf(false) }
  var byPointer by remember { mutableStateOf(false) }
  val pressed by source.collectIsPressedAsState()
  LaunchedEffect(pressed) { if (pressed) byPointer = true }
  ContextMenuArea(items = { menu }) {
    Column(
      Modifier.width(Grid.u192).testTag("home-card")
        .pointerTracking(source)
        .graphicsLayer { scaleX = m.scale.value; scaleY = m.scale.value }
        .onFocusChanged { focused = it.hasFocus; if (!it.hasFocus) byPointer = false }
        .then(if (focused && !byPointer) Modifier.border(Grid.hairline * 2, colors.primary, Grid.r12) else Modifier)
        .highlightClickable(onClick = onClick)
    ) {
      Box(Modifier.size(Grid.u192).clip(RoundedCornerShape(Grid.u12))) {
        cover?.let { TrackArtwork(it, Grid.u192) }
        Box(Modifier.matchParentSize().graphicsLayer { alpha = m.lift.value }.background(colors.onSurface.copy(alpha = LIFT_ALPHA)))
      }
      Text(title, style = GridText.Body, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Grid.u6).line(GridText.Body))
      Text(subtitle, style = GridText.Small, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.line(GridText.Small))
    }
  }
}

/** How much of the text colour the cover takes on under the pointer. */
private const val LIFT_ALPHA = 0.10f

/** How many placeholder cards a loading row holds (the row only composes those that are on screen). */
private const val SKELETON_CARDS = 12

/** One pulse of the placeholder, in milliseconds each way. */
private const val SKELETON_PULSE_MS = 900

/**
 * The row that stands in for cards while a catalogue loads. It takes the room of a real row from the start, so nothing
 * moves when the cards arrive, but stays invisible for the first [HomeData.SKELETON_DELAY_MS]: a catalogue that answers
 * at once never flashes it. Its cards pulse in opacity (0.6 to 1.0) on the GPU layer; under reduced motion they hold still.
 */
@Composable
private fun SkeletonRow() {
  val snap = LocalSnap.current
  val visible by produceState(HomeData.skeletonShows(true, 0)) {
    delay(HomeData.SKELETON_DELAY_MS)
    value = HomeData.skeletonShows(true, HomeData.SKELETON_DELAY_MS)
  }
  val shown = animateFloatAsState(if (visible) 1f else 0f, if (snap) snap() else tween(150), label = "skeleton-in")
  val pulse: State<Float> =
    if (snap) remember { mutableStateOf(0.8f) }
    else rememberInfiniteTransition(label = "skeleton").animateFloat(
      0.6f, 1f, infiniteRepeatable(tween(SKELETON_PULSE_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
  LazyRow(
    Modifier.bleed(Grid.u6), horizontalArrangement = Arrangement.spacedBy(Grid.u12), contentPadding = ROW_ROOM,
    userScrollEnabled = false
  ) {
    items(SKELETON_CARDS) { SkeletonCard(pulse, shown) }
  }
}

/** A card's exact geometry: a 192 dp square and two text bars at 12 sp inside the title and subtitle lines. */
@Composable
internal fun SkeletonCard(pulse: State<Float>, shown: State<Float>) {
  val tone = MaterialTheme.colorScheme.surfaceVariant
  Column(Modifier.width(Grid.u192).testTag("home-skeleton").graphicsLayer { alpha = shown.value * pulse.value }) {
    Box(Modifier.size(Grid.u192).clip(RoundedCornerShape(Grid.u12)).background(tone))
    SkeletonBar(GridText.Body, 0.7f, tone, Modifier.padding(top = Grid.u6))
    SkeletonBar(GridText.Small, 0.45f, tone, Modifier)
  }
}

/** Makes a line of text exactly as tall as its style's line height, whatever the font reports, so a card and its placeholder have one height. */
@Composable
private fun Modifier.line(style: androidx.compose.ui.text.TextStyle): Modifier = height(with(LocalDensity.current) { style.lineHeight.toDp() })

/** A bar 12 sp tall, centred in a line as tall as [line]'s, so the placeholder is as high as the text it stands for. */
@Composable
private fun SkeletonBar(line: androidx.compose.ui.text.TextStyle, fraction: Float, tone: androidx.compose.ui.graphics.Color, modifier: Modifier) {
  val bar = with(LocalDensity.current) { GridText.Small.fontSize.toDp() }
  Box(modifier.fillMaxWidth().line(line), contentAlignment = Alignment.CenterStart) {
    Box(Modifier.fillMaxWidth(fraction).height(bar).clip(RoundedCornerShape(Grid.u6)).background(tone))
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
  Card(t, t.title, t.artistLabel(), menu) { lib.play(t, context) }
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
    Text(lt("About ${current.artistLabel()}", "Sobre ${current.artistLabel()}"), style = GridText.BodyStrong, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
  // An opaque card, like the hint and the featured entry: glass() is translucent now, and this is a message to read.
  Box(Modifier.fillMaxWidth().studioCard().padding(Grid.u12)) {
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
      Text(t.artistLabel(), style = GridText.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** "Bom dia, name" by the local hour; [name] is the app's `displayName` (nickname, else the Windows account's), empty for none. */
internal fun greeting(hour: Int = java.time.LocalTime.now().hour, name: String = ""): String {
  val en = when { hour < 5 -> "Good evening"; hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
  val pt = when { hour < 5 -> "Boa noite"; hour < 12 -> "Bom dia"; hour < 18 -> "Boa tarde"; else -> "Boa noite" }
  val base = lt(en, pt)
  return if (name.isBlank()) base else "$base, $name"
}
