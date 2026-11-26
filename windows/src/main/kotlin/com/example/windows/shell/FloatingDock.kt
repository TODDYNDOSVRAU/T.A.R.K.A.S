package com.example.windows.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.base.BackHandler
import com.example.windows.res.Res
import com.example.windows.res.home_search_placeholder
import com.example.windows.res.shell_pages
import com.example.windows.res.shell_search
import com.example.windows.res.shell_search_close
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.util.lerp
import com.example.ui.theme.TarkasDarkColorScheme
import com.example.windows.res.shell_island_open
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.max

/**
 * Motor of the Floating Dock, ported from the React component the owner supplied (2026-11-25). The original maps the
 * pointer's distance to each icon, [-150, 0, 150] px, to a tile of [40, 80, 40] px through a spring (mass 0.1,
 * stiffness 150, damping 12). Here the tile rests at 48 dp (the T.A.L. touch target) and keeps the same 2x peak, so
 * the reach scales by the same 48/40. All values are dp.
 */
object DockEngine {
  const val REST = 48f
  const val MAX = 96f
  const val REACH = 180f
  const val GAP = 6f
  const val PAD = 6f
  /** Search field width when the dock is open (wide and compact). */
  const val FIELD = 288f
  const val COMPACT_FIELD = 192f
  /** The original spring for unit mass: ratio = c / (2 sqrt(k m)), stiffness = k / m. */
  const val DAMPING_RATIO = 1.549f
  const val STIFFNESS = 1500f

  /** Tile size for a pointer [distance] dp from the tile's rest centre. */
  fun target(distance: Float): Float {
    val d = abs(distance)
    return if (d >= REACH) REST else MAX - (MAX - REST) * d / REACH
  }

  fun restWidth(n: Int) = n * REST + (n - 1) * GAP + 2 * PAD
  /** Centre of tile [i] at rest, measured from the capsule's left edge. */
  fun restCentre(i: Int) = PAD + i * (REST + GAP) + REST / 2

  /** Largest total growth of [n] tiles. The sum of tents peaks with the pointer on a tile centre. */
  fun maxGrowth(n: Int): Float = (0 until n).maxOf { k ->
    var s = 0f
    for (i in 0 until n) s += target(restCentre(i) - restCentre(k)) - REST
    s
  }

  /** Width the dock reserves, so neither magnification nor the open search field ever moves the layout around it. */
  fun reservedWidth(n: Int) = restWidth(n) + max(maxGrowth(n), FIELD - REST)
}

/** Pages in the dock, in the Ctrl+1..6 order, then search. */
private val DockCount = NavOrder.size + 1

/**
 * States of the Dynamic Island (owner's spec, 2026-11-25). There is no music state: the side player already shows the
 * track, cover and controls on every page, and a second copy at the top would only repeat it.
 */
enum class IslandState { COMPACT, NAVIGATION, SEARCH }

enum class IslandEvent {
  /** Click, Enter or Space on the compact pill, or the mouse resting on it. */
  OPEN,
  /** The narrow toggle: opens or closes the page list. */
  TOGGLE,
  /** Esc while the pages are shown. Esc while typing closes the search first, through [SEARCH_CLOSED]. */
  ESCAPE,
  /** The mouse left the island. */
  LEAVE,
  /** A press outside the island while nothing is typed in the search. */
  OUTSIDE,
  /** A press outside the island while the search holds a query: its results live outside, so the island stays. */
  OUTSIDE_TYPED,
  /** The page changed. */
  NAVIGATED,
  /** The search field opened (search tile or Ctrl+F). */
  SEARCH_OPENED,
  /** The search field closed (Esc, its close button, or losing focus while empty). */
  SEARCH_CLOSED,
}

/** The whole state machine, pure so the tests read it without a window. Unknown pairs keep the state. */
object IslandMachine {
  fun next(s: IslandState, e: IslandEvent): IslandState = when (e) {
    IslandEvent.OPEN -> if (s == IslandState.COMPACT) IslandState.NAVIGATION else s
    IslandEvent.TOGGLE -> when (s) { IslandState.COMPACT -> IslandState.NAVIGATION; IslandState.NAVIGATION -> IslandState.COMPACT; else -> s }
    IslandEvent.ESCAPE, IslandEvent.LEAVE -> if (s == IslandState.NAVIGATION) IslandState.COMPACT else s
    IslandEvent.OUTSIDE -> IslandState.COMPACT
    IslandEvent.OUTSIDE_TYPED -> if (s == IslandState.NAVIGATION) IslandState.COMPACT else s
    // Typing in the search moves to Home: that page change must not shut the field the user is typing in.
    IslandEvent.NAVIGATED -> if (s == IslandState.SEARCH) s else IslandState.COMPACT
    IslandEvent.SEARCH_OPENED -> IslandState.SEARCH
    IslandEvent.SEARCH_CLOSED -> if (s == IslandState.SEARCH) IslandState.NAVIGATION else s
  }
}

/** The one island of the window. Its bounds (window px) let the shell tell a press outside from one inside. */
object ShellIsland {
  private val _state = MutableStateFlow(IslandState.COMPACT)
  val state: StateFlow<IslandState> = _state.asStateFlow()
  @Volatile internal var bounds: Rect = Rect.Zero
  /** The narrow page list opens in a popup below the island; a press there is inside too. */
  @Volatile internal var menuBounds: Rect = Rect.Zero

  fun send(e: IslandEvent) { _state.value = IslandMachine.next(_state.value, e) }

  /** Called by the shell for every press, in window px. */
  fun press(at: Offset) {
    if (_state.value == IslandState.COMPACT || bounds.contains(at) || menuBounds.contains(at)) return
    val typed = ShellSearch.query.value.isNotEmpty()
    val was = _state.value
    send(if (typed) IslandEvent.OUTSIDE_TYPED else IslandEvent.OUTSIDE)
    if (was == IslandState.SEARCH && _state.value == IslandState.COMPACT) ShellSearch.close()
  }

  internal fun reset() { _state.value = IslandState.COMPACT; bounds = Rect.Zero; menuBounds = Rect.Zero }
}

/** Compact pill: 192 x 48, the current page's icon and name. */
private const val PILL_W = 192f
private const val PILL_H = 48f
/** The open capsule at rest: 384 x 60 (7 tiles of 48, gaps and padding of 6). */
private val CAPSULE_W = DockEngine.restWidth(NavOrder.size + 1)
private const val CAPSULE_H = 2 * DockEngine.PAD + DockEngine.REST
/** Mouse dwell before the pill opens, and grace after it leaves before the island closes. */
private const val HOVER_OPEN_MS = 120L
private const val LEAVE_CLOSE_MS = 400L
/** Critically damped, settling in 0.30 s: k = (2 pi / 0.30)^2. */
private const val ISLAND_STIFFNESS = 438.65f

/**
 * The single navigation of the desktop app: a Dynamic Island at the top centre. At rest it is a black pill with the
 * current page; click, Enter, Space or resting the mouse on it opens the capsule with the six real pages and the
 * search (magnification, tooltips and shortcuts as before). Esc closes layer by layer, a press outside or a page
 * change closes it, and focus goes back to the pill. When [compact], the narrow toggle plus page list.
 */
@Composable
fun FloatingDock(
  current: TarkasNavTab,
  onSelect: (TarkasNavTab) -> Unit,
  onTyped: () -> Unit,
  reduceMotion: Boolean,
  compact: Boolean,
  modifier: Modifier = Modifier,
) {
  // The island is black in both themes, so it reads the dark scheme: white text and Ice accent on #080808.
  MaterialTheme(colorScheme = TarkasDarkColorScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
    IslandBody(current, onSelect, onTyped, reduceMotion, compact, modifier)
  }
}

@Composable
private fun IslandBody(
  current: TarkasNavTab,
  onSelect: (TarkasNavTab) -> Unit,
  onTyped: () -> Unit,
  reduceMotion: Boolean,
  compact: Boolean,
  modifier: Modifier,
) {
  LaunchedEffect(Unit) {
    ShellSearch.isOpen.collect { ShellIsland.send(if (it) IslandEvent.SEARCH_OPENED else IslandEvent.SEARCH_CLOSED) }
  }
  var lastTab by remember { mutableStateOf(current) }
  LaunchedEffect(current) { if (current != lastTab) { lastTab = current; ShellIsland.send(IslandEvent.NAVIGATED) } }
  val bounded = modifier.onGloballyPositioned { ShellIsland.bounds = it.boundsInWindow() }
  val open by ShellSearch.isOpen.collectAsState()
  val query by ShellSearch.query.collectAsState()
  val focusTick by ShellSearch.focusRequests.collectAsState()
  val fieldFocus = remember { FocusRequester() }
  val tileFocus = remember { FocusRequester() }
  var fieldFocused by remember { mutableStateOf(false) }

  BackHandler(open) { ShellSearch.close() }
  LaunchedEffect(open, focusTick) {
    if (open) { withFrameNanos { }; runCatching { fieldFocus.requestFocus() } }
  }
  LaunchedEffect(open) {
    // Esc while typing: focus goes back to the search tile instead of being lost.
    if (!open && fieldFocused) { fieldFocused = false; withFrameNanos { }; runCatching { tileFocus.requestFocus() } }
  }
  val field: @Composable (Float, () -> Float) -> Unit = { width, reveal ->
    DockSearchField(
      width = width, reveal = reveal, query = query, requester = fieldFocus,
      onQuery = { ShellSearch.setQuery(it); if (it.isNotBlank()) onTyped() },
      onFocus = { has -> if (has) fieldFocused = true else if (fieldFocused) { fieldFocused = false; if (ShellSearch.query.value.isEmpty()) ShellSearch.close() } },
    )
  }
  val searchTitle = stringResource(Res.string.shell_search)
  if (compact) CompactDock(current, onSelect, reduceMotion, open, searchTitle, tileFocus, field, bounded)
  else WideIsland(current, onSelect, reduceMotion, open, searchTitle, tileFocus, field, bounded)
}

/**
 * Wide window: the pill and the open capsule are two layers in one place. They cross over through scale and alpha in
 * graphicsLayer only, so the morph never measures or lays out anything; the pill stretches into the capsule.
 */
@Composable
private fun WideIsland(
  current: TarkasNavTab, onSelect: (TarkasNavTab) -> Unit, reduce: Boolean, open: Boolean, searchTitle: String,
  tileFocus: FocusRequester, field: @Composable (Float, () -> Float) -> Unit, modifier: Modifier,
) {
  val state by ShellIsland.state.collectAsState()
  val expanded = state != IslandState.COMPACT
  val p = remember { Animatable(if (expanded) 1f else 0f) }
  val showCapsule by remember { derivedStateOf { p.value > 0.001f } }
  val showPill by remember { derivedStateOf { p.value < 0.999f } }
  val pillFocus = remember { FocusRequester() }
  val pageFocus = remember { FocusRequester() }
  var byKeyboard by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  var timer by remember { mutableStateOf<Job?>(null) }

  LaunchedEffect(expanded, reduce) {
    val t = if (expanded) 1f else 0f
    if (reduce) p.snapTo(t) else p.animateTo(t, spring(dampingRatio = 1f, stiffness = ISLAND_STIFFNESS, visibilityThreshold = 0.001f))
  }
  LaunchedEffect(state) {
    when {
      state == IslandState.NAVIGATION && byKeyboard -> { withFrameNanos { }; runCatching { pageFocus.requestFocus() } }
      state == IslandState.COMPACT && byKeyboard -> { byKeyboard = false; withFrameNanos { }; runCatching { pillFocus.requestFocus() } }
    }
  }
  // Second Esc (the search closes on the first one): back to the pill, focus with it.
  BackHandler(state == IslandState.NAVIGATION && !open) { byKeyboard = true; ShellIsland.send(IslandEvent.ESCAPE) }

  val openFromKeys = { byKeyboard = true; ShellIsland.send(IslandEvent.OPEN) }
  Box(
    modifier.testTag("dynamic_island")
      .pointerInput(Unit) {
        // Mouse only: resting on the pill opens it, leaving the island closes it after a short grace.
        awaitPointerEventScope {
          while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.changes.firstOrNull()?.type == PointerType.Touch) continue
            when (e.type) {
              PointerEventType.Enter -> {
                timer?.cancel()
                if (ShellIsland.state.value == IslandState.COMPACT) timer = scope.launch { delay(HOVER_OPEN_MS); byKeyboard = false; ShellIsland.send(IslandEvent.OPEN) }
              }
              PointerEventType.Exit -> {
                timer?.cancel()
                if (!byKeyboard) timer = scope.launch { delay(LEAVE_CLOSE_MS); ShellIsland.send(IslandEvent.LEAVE) }
              }
              else -> Unit
            }
          }
        }
      },
    contentAlignment = Alignment.TopCenter,
  ) {
    if (expanded || showCapsule) {
      WideDock(
        current, onSelect, reduce, open, searchTitle, tileFocus, pageFocus, field,
        Modifier.graphicsLayer {
          val v = p.value
          alpha = v.coerceIn(0f, 1f)
          scaleX = lerp(PILL_W / CAPSULE_W, 1f, v)
          scaleY = lerp(PILL_H / CAPSULE_H, 1f, v)
          transformOrigin = TransformOrigin(0.5f, 0f)
        },
      )
    }
    if (!expanded || showPill) {
      IslandPill(
        current, pillFocus, onKeys = openFromKeys,
        modifier = Modifier.graphicsLayer {
          val v = p.value
          alpha = (1f - v).coerceIn(0f, 1f)
          scaleX = lerp(1f, CAPSULE_W / PILL_W, v)
          scaleY = lerp(1f, CAPSULE_H / PILL_H, v)
          transformOrigin = TransformOrigin(0.5f, 0f)
        },
      ) { byKeyboard = false; ShellIsland.send(IslandEvent.OPEN) }
    }
  }
}

/** The island at rest: a black pill with where you are. Its accessible name says what it does. */
@Composable
private fun IslandPill(current: TarkasNavTab, focus: FocusRequester, onKeys: () -> Unit, modifier: Modifier, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { MutableInteractionSource() }
  val focused by src.collectIsFocusedAsState()
  val openLabel = stringResource(Res.string.shell_island_open)
  val page = stringResource(current.label)
  Row(
    modifier.width(Grid.u192).height(Grid.u48).clip(CircleShape).background(c.background)
      .border(if (focused) Grid.hairline + Grid.hairline else Grid.hairline, if (focused) c.primary else c.onSurface.copy(alpha = 0.12f), CircleShape)
      .focusRequester(focus)
      .onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Spacebar)) { onKeys(); true } else false
      }
      .clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
      .semantics { contentDescription = "$openLabel: $page" }
      .testTag("island_pill")
      .padding(horizontal = Grid.u12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Grid.u12),
  ) {
    Icon(current.icon(), contentDescription = null, tint = c.primary, modifier = Modifier.size(Grid.u24))
    Text(page, style = GridText.BodyStrong, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun WideDock(
  current: TarkasNavTab, onSelect: (TarkasNavTab) -> Unit, reduce: Boolean, open: Boolean, searchTitle: String,
  tileFocus: FocusRequester, pageFocus: FocusRequester, field: @Composable (Float, () -> Float) -> Unit, modifier: Modifier,
) {
  val c = MaterialTheme.colorScheme
  val n = DockCount
  val sizes = remember { List(n) { Animatable(DockEngine.REST) } }
  val targets = remember { List(n) { mutableFloatStateOf(DockEngine.REST) } }
  val fieldW = remember { Animatable(DockEngine.REST) }
  val widths = remember { FloatArray(n) }      // px, written while placing, read by the pointer handler
  val centres = remember { FloatArray(n) }
  var hovering by remember { mutableStateOf(false) }
  var hoverTip by remember { mutableIntStateOf(-1) }
  var focusTip by remember { mutableIntStateOf(-1) }
  val shown = if (focusTip >= 0) focusTip else if (open) -1 else hoverTip
  var lastTip by remember { mutableIntStateOf(-1) }
  if (shown >= 0) lastTip = shown
  val tipAlpha by animateFloatAsState(if (shown >= 0) 1f else 0f, tween(200, easing = LinearOutSlowInEasing))
  val fill = c.background
  val px = LocalDensity.current.density
  val rim = c.onSurface.copy(alpha = 0.12f)

  LaunchedEffect(Unit) {
    val spec = spring(DockEngine.DAMPING_RATIO, DockEngine.STIFFNESS, 0.01f)
    for (i in 0 until n) launch { snapshotFlow { targets[i].floatValue }.collectLatest { sizes[i].animateTo(it, spec) } }
  }
  LaunchedEffect(open, reduce) {
    if (open) targets.forEach { it.floatValue = DockEngine.REST }
    val t = if (open) DockEngine.FIELD else DockEngine.REST
    if (reduce) fieldW.snapTo(t) else fieldW.animateTo(t, tween(250, easing = FastOutSlowInEasing))
  }
  val updReduce by rememberUpdatedState(reduce)
  val updOpen by rememberUpdatedState(open)

  val labels = NavOrder.map { stringResource(it.label) }
  val tipText = when {
    lastTip in NavOrder.indices -> "${labels[lastTip]}  Ctrl+${lastTip + 1}"
    lastTip == n - 1 -> "$searchTitle  Ctrl+F"
    else -> ""
  }

  /** Slot width in dp: the search slot follows the field while it opens or closes. */
  fun slot(i: Int): Float =
    if (i == n - 1 && fieldW.value > DockEngine.REST + 0.5f) fieldW.value else sizes[i].value

  Layout(
    content = {
      NavOrder.forEachIndexed { i, tab ->
        DockTile(labels[i], tab.icon(), tab == current, { focusTip = if (it) i else if (focusTip == i) -1 else focusTip }, Modifier.then(if (tab == current) Modifier.focusRequester(pageFocus) else Modifier).testTag("dock_${tab.name.lowercase()}")) {
          SidePane.close(); onSelect(tab)
        }
      }
      if (open) field(DockEngine.FIELD) { fieldW.value * px }
      else DockTile(searchTitle, Icons.Outlined.Search, false, { focusTip = if (it) n - 1 else if (focusTip == n - 1) -1 else focusTip }, Modifier.focusRequester(tileFocus).testTag("dock_search")) {
        ShellSearch.openDock()
      }
      Tooltip(tipText)
    },
    modifier = modifier
      .testTag("floating_dock")
      .pointerInput(Unit) {
        val padPx = DockEngine.PAD.dp.toPx()
        val capsuleH = (2 * DockEngine.PAD + DockEngine.REST).dp.toPx()
        fun reset() {
          hovering = false; hoverTip = -1
          targets.forEach { it.floatValue = DockEngine.REST }
        }
        awaitPointerEventScope {
          while (true) {
            val e = awaitPointerEvent()
            val ch = e.changes.firstOrNull() ?: continue
            if (ch.type == PointerType.Touch) continue
            if (e.type == PointerEventType.Exit) { reset(); continue }
            if (e.type != PointerEventType.Move && e.type != PointerEventType.Enter) continue
            val p = ch.position
            var k = 0
            for (i in 1 until n) if (abs(p.x - centres[i]) < abs(p.x - centres[k])) k = i
            val onTile = abs(p.x - centres[k]) <= widths[k] / 2
            val inside = p.y <= capsuleH || (onTile && p.y <= padPx + widths[k])
            if (!inside) { reset(); continue }
            hovering = true
            hoverTip = if (onTile && p.y >= padPx) k else -1
            if (!updReduce && !updOpen) {
              val xDp = p.x / density
              val start = (size.width / density - DockEngine.restWidth(n)) / 2
              for (i in 0 until n) targets[i].floatValue = DockEngine.target(xDp - start - DockEngine.restCentre(i))
            }
          }
        }
      }
      .drawBehind {
        // Capsule: same arithmetic as the placement below, read here so it redraws without a layout pass.
        var total = 2 * DockEngine.PAD + (n - 1) * DockEngine.GAP
        for (i in 0 until n) total += slot(i)
        val w = total.dp.toPx()
        val h = (2 * DockEngine.PAD + DockEngine.REST).dp.toPx()
        val r = CornerRadius(h / 2)
        val tl = Offset((size.width - w) / 2, 0f)
        drawRoundRect(fill, tl, Size(w, h), r)
        drawRoundRect(rim, tl, Size(w, h), r, style = Stroke(Grid.hairline.toPx()))
      },
  ) { measurables, _ ->
    val rest = DockEngine.REST.dp.roundToPx()
    val pad = DockEngine.PAD.dp.roundToPx()
    val gap = DockEngine.GAP.dp.toPx()
    val width = DockEngine.reservedWidth(n).dp.roundToPx()
    val height = (2 * DockEngine.PAD + if (hovering) DockEngine.MAX else DockEngine.REST).dp.roundToPx()
    val tiles = measurables.subList(0, n).mapIndexed { i, m ->
      if (i == n - 1 && open) m.measure(Constraints.fixed(DockEngine.FIELD.dp.roundToPx(), rest)) else m.measure(Constraints.fixed(rest, rest))
    }
    val tip = measurables[n].measure(Constraints())
    layout(width, height) {
      var total = 2 * pad + (n - 1) * gap
      for (i in 0 until n) { widths[i] = slot(i).dp.toPx(); total += widths[i] }
      var x = (width - total) / 2 + pad
      for (i in 0 until n) {
        val w = widths[i]
        centres[i] = x + w / 2
        if (i == n - 1 && open) tiles[i].place(x.toInt(), pad)
        else {
          val s = w / rest
          tiles[i].placeWithLayer((centres[i] - rest / 2f).toInt(), pad) {
            scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 0f)
          }
        }
        x += w + gap
      }
      val k = lastTip
      if (k in 0 until n) {
        val tx = (centres[k] - tip.width / 2f).toInt().coerceIn(0, (width - tip.width).coerceAtLeast(0))
        val ty = (pad + widths[k] + gap).toInt()
        val lift = 10.dp.toPx()
        tip.placeWithLayer(tx, ty) { alpha = tipAlpha; translationY = -(1f - tipAlpha) * lift }
      }
    }
  }
}

/** One page of the dock: 48 dp circle, 24 dp icon, a dot when it is the current page, a primary ring on focus. */
@Composable
private fun DockTile(title: String, icon: ImageVector, active: Boolean, onFocus: (Boolean) -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  val src = remember { MutableInteractionSource() }
  val focused by src.collectIsFocusedAsState()
  Box(
    modifier.size(Grid.u48).clip(CircleShape)
      .background(if (active) c.primary.copy(alpha = 0.16f) else c.onSurface.copy(alpha = 0.06f))
      .then(if (focused) Modifier.border(Grid.hairline + Grid.hairline, c.primary, CircleShape) else Modifier)
      .onFocusChanged { onFocus(it.isFocused) }
      .clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
      .semantics { contentDescription = title; selected = active },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = null, tint = if (active) c.primary else c.onSurfaceVariant, modifier = Modifier.size(Grid.u24))
    if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Grid.u6).size(Grid.u6).clip(CircleShape).background(c.primary))
  }
}

/** The real search field, inside the dock. [reveal] (px) clips it while it opens, so the slot grows without a re-layout. */
@Composable
private fun DockSearchField(
  width: Float, reveal: () -> Float, query: String, requester: FocusRequester,
  onQuery: (String) -> Unit, onFocus: (Boolean) -> Unit,
) {
  val c = MaterialTheme.colorScheme
  val focus = LocalFocusManager.current
  val placeholder = stringResource(Res.string.home_search_placeholder)
  val closeLabel = stringResource(Res.string.shell_search_close)
  Row(
    Modifier.width(width.dp).height(Grid.u48)
      .drawWithContent { clipRect(right = reveal()) { this@drawWithContent.drawContent() } }
      .clip(CircleShape).background(c.onSurface.copy(alpha = 0.06f)).padding(start = Grid.u12),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Search, contentDescription = null, tint = c.primary, modifier = Modifier.size(Grid.u24))
    Spacer(Modifier.width(Grid.u12))
    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (query.isEmpty()) Text(placeholder, style = GridText.Body, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      BasicTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        textStyle = GridText.Body.copy(color = c.onSurface), cursorBrush = SolidColor(c.primary),
        modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { onFocus(it.isFocused) }
          .onPreviewKeyEvent { e ->
            // Enter or Down: from the field to the results below it.
            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.DirectionDown)) {
              focus.moveFocus(FocusDirection.Down); true
            } else false
          }
          .testTag("dock_search_field")
          .semantics { contentDescription = placeholder },
      )
    }
    Box(
      Modifier.size(Grid.u48).clip(CircleShape).clickable(role = Role.Button) { ShellSearch.close() }.testTag("dock_search_close")
        .semantics { contentDescription = closeLabel },
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Close, contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(Grid.u24)) }
  }
}

/** Narrow window: the current page as a toggle that opens the pages below it, plus search. */
@Composable
private fun CompactDock(
  current: TarkasNavTab, onSelect: (TarkasNavTab) -> Unit, reduce: Boolean, open: Boolean, searchTitle: String,
  tileFocus: FocusRequester, field: @Composable (Float, () -> Float) -> Unit, modifier: Modifier,
) {
  val c = MaterialTheme.colorScheme
  val island by ShellIsland.state.collectAsState()
  val menu = island == IslandState.NAVIGATION
  val toggleFocus = remember { FocusRequester() }
  val pagesTitle = stringResource(Res.string.shell_pages)
  val dismiss = { ShellIsland.send(IslandEvent.ESCAPE); runCatching { toggleFocus.requestFocus() }; Unit }
  if (!menu) ShellIsland.menuBounds = Rect.Zero
  BackHandler(menu) { dismiss() }
  Row(
    modifier.testTag("floating_dock").clip(CircleShape).background(c.background).border(Grid.hairline, c.onSurface.copy(alpha = 0.12f), CircleShape)
      .padding(Grid.u6),
    horizontalArrangement = Arrangement.spacedBy(Grid.u6), verticalAlignment = Alignment.CenterVertically,
  ) {
    Box {
      DockTile("$pagesTitle: ${stringResource(current.label)}", current.icon(), true, {}, Modifier.focusRequester(toggleFocus).testTag("dock_toggle")) { ShellIsland.send(IslandEvent.TOGGLE) }
      if (menu) {
        val gap = with(LocalDensity.current) { Grid.u12.roundToPx() }
        Popup(popupPositionProvider = remember(gap) { BelowAnchor(gap) }, onDismissRequest = dismiss, properties = PopupProperties(focusable = true)) {
          Column(
            Modifier.width(Grid.u192 + Grid.u48).clip(Grid.r24).background(c.background).border(Grid.hairline, c.onSurface.copy(alpha = 0.12f), Grid.r24)
              .onGloballyPositioned { ShellIsland.menuBounds = it.boundsInWindow() }
              .padding(Grid.u6).testTag("dock_menu"),
            verticalArrangement = Arrangement.spacedBy(Grid.u6),
          ) {
            NavOrder.forEachIndexed { i, tab ->
              val a = remember { Animatable(if (reduce) 1f else 0f) }
              LaunchedEffect(Unit) { if (!reduce) { delay(i * 50L); a.animateTo(1f, tween(200, easing = LinearOutSlowInEasing)) } }
              val on = tab == current
              val label = stringResource(tab.label)
              Row(
                Modifier.fillMaxWidth().height(Grid.u48)
                  .graphicsLayer { alpha = a.value; translationY = -(1f - a.value) * Grid.u12.toPx() }
                  .clip(CircleShape).background(if (on) c.primary.copy(alpha = 0.16f) else Color.Transparent)
                  .clickable(role = Role.Tab) { SidePane.close(); onSelect(tab); ShellIsland.send(IslandEvent.NAVIGATED) }
                  .semantics { selected = on }
                  .testTag("dock_menu_${tab.name.lowercase()}")
                  .padding(horizontal = Grid.u12),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12),
              ) {
                Icon(tab.icon(), contentDescription = null, tint = if (on) c.primary else c.onSurfaceVariant, modifier = Modifier.size(Grid.u24))
                Text(label, style = if (on) GridText.BodyStrong else GridText.Body, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("Ctrl+${i + 1}", style = GridText.Small, color = c.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
    if (open) field(DockEngine.COMPACT_FIELD) { Float.MAX_VALUE }
    else DockTile(searchTitle, Icons.Outlined.Search, false, {}, Modifier.focusRequester(tileFocus).testTag("dock_search")) { ShellSearch.openDock() }
  }
}

private class BelowAnchor(private val gap: Int) : PopupPositionProvider {
  override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
    val x = (anchorBounds.left).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
    return IntOffset(x, anchorBounds.bottom + gap)
  }
}
