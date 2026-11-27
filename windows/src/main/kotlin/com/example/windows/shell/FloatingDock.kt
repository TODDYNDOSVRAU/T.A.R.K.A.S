package com.example.windows.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import com.example.windows.res.shell_pages
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
 * stiffness 150, damping 12). Here each page rests in a 48 dp slot (the T.A.L. touch target) and keeps the same 2x
 * peak, so the reach scales by the same 48/40. Slots touch: the spacing is the circle's own inset (0.75 of the slot),
 * so the capsule is 48 tall at rest, on the 2x chain, and every circle is concentric with its round ends. All dp.
 */
object DockEngine {
  const val REST = 48f
  const val MAX = 96f
  const val REACH = 180f
  /** Visible circle and icon as parts of the slot: 36 and 24 at rest, 72 and 48 at the peak. */
  const val CIRCLE = 0.75f
  const val ICON = 0.5f
  /** The original spring for unit mass: ratio = c / (2 sqrt(k m)), stiffness = k / m. */
  const val DAMPING_RATIO = 1.549f
  const val STIFFNESS = 1500f

  /** Slot size for a pointer [distance] dp from the slot's rest centre. */
  fun target(distance: Float): Float {
    val d = abs(distance)
    return if (d >= REACH) REST else MAX - (MAX - REST) * d / REACH
  }

  fun restWidth(n: Int) = n * REST
  /** Centre of slot [i] at rest, measured from the capsule's left edge. */
  fun restCentre(i: Int) = i * REST + REST / 2

  /** Largest total growth of [n] slots. The sum of tents peaks with the pointer on a slot centre. */
  fun maxGrowth(n: Int): Float = (0 until n).maxOf { k ->
    var s = 0f
    for (i in 0 until n) s += target(restCentre(i) - restCentre(k)) - REST
    s
  }

  /** Width the dock reserves, so magnification never moves the layout around it. */
  fun reservedWidth(n: Int) = restWidth(n) + maxGrowth(n)

  /**
   * Entry delay of slot [i] of [n] when the island opens: the shape leads by [LEAD_MS], then the icons follow from the
   * centre outwards, [STEP_MS] apart.
   */
  fun enterDelay(i: Int, n: Int): Long = LEAD_MS + abs(2 * i - (n - 1)) / 2 * STEP_MS

  const val LEAD_MS = 60L
  const val STEP_MS = 24L
  const val ENTER_MS = 200
  const val EXIT_MS = 150
}

/**
 * Pages in the island, in the Ctrl+1..6 order. Ajustes is not one of them: it has its own button at the top right
 * (owner D2, 2026-11-27), and the search lives in the Biblioteca (D1).
 */
internal val DockPages = NavOrder - TarkasNavTab.SETTINGS

/** States of the Dynamic Island (owner's spec, 2026-11-25; search left it on 2026-11-27). */
enum class IslandState { COMPACT, NAVIGATION }

enum class IslandEvent {
  /** Click, Enter or Space on the compact pill, or the mouse resting on it. */
  OPEN,
  /** The narrow toggle: opens or closes the page list. */
  TOGGLE,
  /** Esc while the pages are shown. */
  ESCAPE,
  /** The mouse left the island. */
  LEAVE,
  /** A press outside the island. */
  OUTSIDE,
  /** The page changed. */
  NAVIGATED,
}

/** The whole state machine, pure so the tests read it without a window. Unknown pairs keep the state. */
object IslandMachine {
  fun next(s: IslandState, e: IslandEvent): IslandState = when (e) {
    IslandEvent.OPEN -> if (s == IslandState.COMPACT) IslandState.NAVIGATION else s
    IslandEvent.TOGGLE -> if (s == IslandState.COMPACT) IslandState.NAVIGATION else IslandState.COMPACT
    IslandEvent.ESCAPE, IslandEvent.LEAVE, IslandEvent.OUTSIDE, IslandEvent.NAVIGATED -> IslandState.COMPACT
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
    send(IslandEvent.OUTSIDE)
  }

  internal fun reset() { _state.value = IslandState.COMPACT; bounds = Rect.Zero; menuBounds = Rect.Zero }
}

/** Compact pill: 192 x 48, the current page's icon and name. */
private const val PILL_W = 192f
private const val PILL_H = 48f
/** Where the pill's icon centre sits, from the pill's left edge: padding 12 plus half the 24 dp icon. */
private const val PILL_ICON_X = 24f
/** The open capsule at rest: 240 x 48 (5 slots of 48), the same height as the pill, so the morph only widens. */
private val CAPSULE_W = DockEngine.restWidth(DockPages.size)
/** Mouse dwell before the pill opens, and grace after it leaves before the island closes. */
private const val HOVER_OPEN_MS = 120L
private const val LEAVE_CLOSE_MS = 400L
/** k = (2 pi / 0.30)^2, with a light bounce (zeta 0.8, owner 2026-11-26): about 1.5 % past the target, then back. */
private const val ISLAND_STIFFNESS = 438.65f
private const val ISLAND_DAMPING = 0.8f
/** Share of the morph in which the pill's own content fades out, so the shape leads and nothing doubles. */
private const val PILL_FADE = 0.25f

/**
 * What the collapsed island shows while music plays (owner D3, 2026-11-27): five bars cut from the track's real
 * waveform at the play position, the output in use and a live dot. The player registers [peaks] and the audio area
 * [output]; until they do, the bars stay flat and no output icon is drawn. Nothing here is invented.
 */
object IslandActivity {
  /** The current track's waveform (0..255 per bucket over the whole track); null while it loads or with no track. */
  var peaks: StateFlow<ByteArray?>? by mutableStateOf(null)
  /** Icon of the output the sound goes to (headphones, speaker, Bluetooth); null until the audio area registers it. */
  var output: StateFlow<ImageVector?>? by mutableStateOf(null)

  const val BARS = 5
  /** Height of a bar with no waveform: a quarter, so the row reads as idle, never as a fake meter. */
  const val FLAT = 0.25f

  /**
   * Fills [out] with [BARS] heights in [FLAT]..1: the waveform buckets centred on [fraction] of the track. Buckets
   * outside the track (its first and last moments) and missing peaks give [FLAT].
   */
  fun bars(peaks: ByteArray?, fraction: Float, out: FloatArray) {
    if (peaks == null || peaks.isEmpty() || fraction.isNaN()) { out.fill(FLAT, 0, BARS); return }
    val centre = (fraction.coerceIn(0f, 1f) * (peaks.size - 1)).toInt()
    for (i in 0 until BARS) {
      val k = centre + i - BARS / 2
      out[i] = if (k in peaks.indices) max(FLAT, (peaks[k].toInt() and 0xFF) / 255f) else FLAT
    }
  }

  internal fun reset() { peaks = null; output = null }
}

/**
 * The single navigation of the desktop app: a Dynamic Island at the top centre of the window. At rest it is a black
 * pill with the current page, or the music activity while a track plays; click, Enter, Space or resting the mouse on
 * it opens the capsule with the five pages (magnification, tooltips and shortcuts as before). Esc, a press outside or
 * a page change closes it, and focus goes back to the pill. When [compact], the narrow toggle plus page list.
 * [fraction] (play position over duration) is read only while drawing the bars, so playback never recomposes it.
 */
@Composable
fun FloatingDock(
  current: TarkasNavTab,
  onSelect: (TarkasNavTab) -> Unit,
  reduceMotion: Boolean,
  compact: Boolean,
  modifier: Modifier = Modifier,
  playing: Boolean = false,
  fraction: () -> Float = { 0f },
) {
  // The island is black in both themes, so it reads the dark scheme: white text and Ice accent on #080808.
  MaterialTheme(colorScheme = TarkasDarkColorScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
    var lastTab by remember { mutableStateOf(current) }
    LaunchedEffect(current) { if (current != lastTab) { lastTab = current; ShellIsland.send(IslandEvent.NAVIGATED) } }
    val bounded = modifier.onGloballyPositioned { ShellIsland.bounds = it.boundsInWindow() }
    if (compact) CompactDock(current, onSelect, reduceMotion, bounded)
    else WideIsland(current, onSelect, reduceMotion, playing, fraction, bounded)
  }
}

/**
 * Wide window: the pill and the open capsule are two layers in one place. They cross over through scale and alpha in
 * graphicsLayer only, so the morph never measures or lays out anything; the pill stretches into the capsule.
 */
@Composable
private fun WideIsland(
  current: TarkasNavTab, onSelect: (TarkasNavTab) -> Unit, reduce: Boolean, playing: Boolean, fraction: () -> Float,
  modifier: Modifier,
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
    if (reduce) p.snapTo(t) else p.animateTo(t, spring(dampingRatio = ISLAND_DAMPING, stiffness = ISLAND_STIFFNESS, visibilityThreshold = 0.001f))
  }
  LaunchedEffect(state) {
    when {
      state == IslandState.NAVIGATION && byKeyboard -> { withFrameNanos { }; runCatching { pageFocus.requestFocus() } }
      state == IslandState.COMPACT && byKeyboard -> { byKeyboard = false; withFrameNanos { }; runCatching { pillFocus.requestFocus() } }
    }
  }
  // Esc: back to the pill, focus with it.
  BackHandler(state == IslandState.NAVIGATION) { byKeyboard = true; ShellIsland.send(IslandEvent.ESCAPE) }

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
      WideDock(current, onSelect, reduce, pageFocus, expanded, morph = { p.value }, Modifier)
    }
    if (!expanded || showPill) {
      IslandPill(
        current, playing, fraction, pillFocus, onKeys = openFromKeys,
        modifier = Modifier.graphicsLayer {
          // The pill hands its shape to the dock's capsule and fades in the first quarter; under 0 (the closing bounce,
          // about 1.5 % at zeta 0.8) it narrows a little and settles, the same small bounce the capsule has when it opens.
          val v = p.value
          alpha = (1f - v / PILL_FADE).coerceIn(0f, 1f)
          if (v < 0f) scaleX = lerp(1f, CAPSULE_W / PILL_W, v)
          transformOrigin = TransformOrigin(0.5f, 0f)
        },
      ) { byKeyboard = false; ShellIsland.send(IslandEvent.OPEN) }
    }
  }
}

/**
 * The island at rest: a black pill with where you are or, while a track plays, its activity. Its accessible name
 * always says what it does and which page is shown.
 */
@Composable
private fun IslandPill(
  current: TarkasNavTab, playing: Boolean, fraction: () -> Float, focus: FocusRequester, onKeys: () -> Unit,
  modifier: Modifier, onClick: () -> Unit,
) {
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
    if (playing) IslandActivityRow(fraction)
    else {
      Icon(current.icon(), contentDescription = null, tint = c.primary, modifier = Modifier.size(Grid.u24))
      Text(page, style = GridText.BodyStrong, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/**
 * The music activity in the pill: the output icon (24, when known), five 6 dp bars filling the rest of the row (24
 * tall) and a 6 dp live dot. The bars are drawn in drawBehind from the waveform at the play position, so playback only
 * redraws them, never recomposes.
 */
@Composable
private fun RowScope.IslandActivityRow(fraction: () -> Float) {
  val c = MaterialTheme.colorScheme
  val peaks = IslandActivity.peaks?.collectAsState()
  val output = IslandActivity.output?.collectAsState()?.value
  val bars = remember { FloatArray(IslandActivity.BARS) }
  val bar = c.primary
  // Output at the leading edge, the meter and the live dot at the trailing one (Apple's island reads the same way).
  // With no output known the meter sits in the middle instead of next to an empty slot.
  if (output != null) Icon(output, contentDescription = null, tint = c.onSurface, modifier = Modifier.size(Grid.u24).testTag("island_output"))
  Spacer(Modifier.weight(1f))
  Box(
    Modifier.width(Grid.u48).height(Grid.u24).testTag("island_activity").drawBehind {
      IslandActivity.bars(peaks?.value, fraction(), bars)
      val n = IslandActivity.BARS
      val w = Grid.u6.toPx()
      val gap = (size.width - n * w) / (n - 1)   // 4.5 dp: five 6 dp bars in one 48 dp block
      for (i in 0 until n) {
        val h = bars[i] * size.height
        drawRoundRect(bar, Offset(i * (w + gap), (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
      }
    },
  )
  Box(Modifier.size(Grid.u6).clip(CircleShape).background(c.primary).testTag("island_live"))
  if (output == null) Spacer(Modifier.weight(1f))
}

@Composable
private fun WideDock(
  current: TarkasNavTab, onSelect: (TarkasNavTab) -> Unit, reduce: Boolean, pageFocus: FocusRequester,
  expanded: Boolean, morph: () -> Float, modifier: Modifier,
) {
  val c = MaterialTheme.colorScheme
  val n = DockPages.size
  val sizes = remember { List(n) { Animatable(DockEngine.REST) } }
  val targets = remember { List(n) { mutableFloatStateOf(DockEngine.REST) } }
  /** Entry of each slot, 0 (gone) to 1 (in place): read only in the layers, so the cascade never re-lays out. */
  val enter = remember { List(n) { Animatable(if (reduce) 1f else 0f) } }
  val widths = remember { FloatArray(n) }      // px, written while placing, read by the pointer handler
  val centres = remember { FloatArray(n) }
  var hovering by remember { mutableStateOf(false) }
  var hoverTip by remember { mutableIntStateOf(-1) }
  var focusTip by remember { mutableIntStateOf(-1) }
  val shown = if (focusTip >= 0) focusTip else hoverTip
  var lastTip by remember { mutableIntStateOf(-1) }
  if (shown >= 0) lastTip = shown
  val tipAlpha by animateFloatAsState(if (shown >= 0) 1f else 0f, tween(200, easing = LinearOutSlowInEasing))
  val fill = c.background
  val rim = c.onSurface.copy(alpha = 0.12f)
  // -1 on Ajustes, which has no slot: every icon then enters from the centre and none flies from the pill.
  val currentIndex = DockPages.indexOf(current)

  LaunchedEffect(Unit) {
    val spec = spring(DockEngine.DAMPING_RATIO, DockEngine.STIFFNESS, 0.01f)
    for (i in 0 until n) launch { snapshotFlow { targets[i].floatValue }.collectLatest { sizes[i].animateTo(it, spec) } }
  }
  LaunchedEffect(expanded, reduce) {
    if (reduce) { enter.forEach { it.snapTo(if (expanded) 1f else 0f) }; return@LaunchedEffect }
    if (expanded) {
      for (i in 0 until n) launch {
        // The current page is the pill's icon flying to its slot; the others follow the shape from the centre out.
        if (i == currentIndex) enter[i].animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        else enter[i].animateTo(1f, tween(DockEngine.ENTER_MS, delayMillis = DockEngine.enterDelay(i, n).toInt(), easing = LinearOutSlowInEasing))
      }
    } else {
      // Closing: every icon leaves at once and faster than the shape, so the capsule never shrinks over them.
      enter.forEach { launch { it.animateTo(0f, tween(DockEngine.EXIT_MS, easing = FastOutLinearInEasing)) } }
    }
  }
  val updReduce by rememberUpdatedState(reduce)

  val labels = DockPages.map { stringResource(it.label) }
  // The shortcut is the page's place in the full Ctrl+1..6 order, so it stays the same with Ajustes out of the dock.
  val tipText = if (lastTip in DockPages.indices) "${labels[lastTip]}  Ctrl+${NavOrder.indexOf(DockPages[lastTip]) + 1}" else ""

  fun slot(i: Int): Float = sizes[i].value

  /** Capsule height in dp: the tallest slot, so a grown circle never leaves the island and stays concentric. */
  fun tall(): Float {
    var t = DockEngine.REST
    for (s in sizes) t = max(t, s.value)
    return t
  }

  Layout(
    content = {
      DockPages.forEachIndexed { i, tab ->
        val here = tab == current
        // Keyboard entry lands on the current page or, on Ajustes (no slot), on the first one.
        val takesFocus = here || (currentIndex < 0 && i == 0)
        DockTile(
          labels[i], tab.icon(), here, { focusTip = if (it) i else if (focusTip == i) -1 else focusTip },
          Modifier.then(if (takesFocus) Modifier.focusRequester(pageFocus) else Modifier).testTag("dock_${tab.name.lowercase()}"),
          slot = Grid.u96, appear = if (here) { { enter[i].value } } else { { 1f } },
        ) {
          SidePane.close(); onSelect(tab)
        }
      }
      Tooltip(tipText)
    },
    modifier = modifier
      .testTag("floating_dock")
      .pointerInput(Unit) {
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
            val tallPx = tall().dp.toPx()
            if (p.y > tallPx) { reset(); continue }
            hovering = true
            val top = (tallPx - widths[k]) / 2
            hoverTip = if (onTile && p.y >= top && p.y <= top + widths[k]) k else -1
            if (!updReduce) {
              val xDp = p.x / density
              val start = (size.width / density - DockEngine.restWidth(n)) / 2
              for (i in 0 until n) targets[i].floatValue = DockEngine.target(xDp - start - DockEngine.restCentre(i))
            }
          }
        }
      }
      .drawBehind {
        // The one shape of the island, from the pill (morph 0) to the capsule (1). Read here, so it redraws without a
        // layout pass; past 1 the spring's bounce widens it a little beyond the slots, then it settles.
        val v = morph()
        var total = 0f
        for (i in 0 until n) total += slot(i)
        val w = lerp(PILL_W, total, v).dp.toPx()
        val h = lerp(PILL_H, tall(), v).dp.toPx()
        val r = CornerRadius(h / 2)
        val tl = Offset((size.width - w) / 2, 0f)
        drawRoundRect(fill, tl, Size(w, h), r)
        drawRoundRect(rim, tl, Size(w, h), r, style = Stroke(Grid.hairline.toPx()))
      },
  ) { measurables, _ ->
    val big = DockEngine.MAX.dp.roundToPx()
    val width = DockEngine.reservedWidth(n).dp.roundToPx()
    val height = (if (hovering) DockEngine.MAX else DockEngine.REST).dp.roundToPx()
    // Tiles are drawn at the 96 dp peak and shrunk by their layer, so the icon is rasterised at the largest size it
    // reaches: magnifying only ever scales it down, never blurs it up.
    val tiles = measurables.subList(0, n).map { it.measure(Constraints.fixed(big, big)) }
    val tip = measurables[n].measure(Constraints())
    val pillIconX = width / 2f - (PILL_W / 2 - PILL_ICON_X).dp.toPx()
    val tipGap = Grid.u6.toPx()
    layout(width, height) {
      val tallPx = tall().dp.toPx()
      var total = 0f
      for (i in 0 until n) { widths[i] = slot(i).dp.toPx(); total += widths[i] }
      var x = (width - total) / 2
      for (i in 0 until n) {
        val w = widths[i]
        val cx = x + w / 2
        centres[i] = cx
        val s = w / big
        tiles[i].placeWithLayer((cx - big / 2f).toInt(), ((tallPx - w) / 2).toInt()) {
          val e = enter[i].value
          transformOrigin = TransformOrigin(0.5f, 0f)
          if (i == currentIndex) {
            scaleX = s; scaleY = s
            translationX = (1f - e) * (pillIconX - cx)
          } else {
            val q = lerp(0.5f, 1f, e)
            scaleX = s * q; scaleY = s * q
            translationY = (1f - q) * w / 2          // grow about the slot's centre, not its top
            alpha = e
          }
        }
        x += w
      }
      val k = lastTip
      if (k in 0 until n) {
        val tx = (centres[k] - tip.width / 2f).toInt().coerceIn(0, (width - tip.width).coerceAtLeast(0))
        val ty = (tallPx + tipGap).toInt()
        tip.placeWithLayer(tx, ty) { alpha = tipAlpha; translationY = -(1f - tipAlpha) * tipGap }
      }
    }
  }
}

/**
 * One page of the dock, drawn for a [slot]: a circle of 0.75 of it, an icon of half of it, a dot when it is the current
 * page, a primary ring on focus. The wide dock draws it at the 96 dp peak and its layer shrinks it, so the vector stays
 * sharp while it grows; the narrow one draws it at 48. Pressing shrinks the circle to 0.96, hovering lifts its fill one
 * step; both run in the draw and layer phases only. [appear] fades the fill and dot of the tile that flies in.
 */
@Composable
private fun DockTile(
  title: String, icon: ImageVector, active: Boolean, onFocus: (Boolean) -> Unit, modifier: Modifier = Modifier,
  slot: Dp = Grid.u48, appear: () -> Float = { 1f }, onClick: () -> Unit,
) {
  val c = MaterialTheme.colorScheme
  val src = remember { MutableInteractionSource() }
  val focused by src.collectIsFocusedAsState()
  val hovered by src.collectIsHoveredAsState()
  val pressed by src.collectIsPressedAsState()
  val hover by animateFloatAsState(if (hovered) 1f else 0f, tween(150, easing = LinearOutSlowInEasing))
  val pressSpec: AnimationSpec<Float> =
    if (pressed) tween(100, easing = FastOutSlowInEasing) else spring(ISLAND_DAMPING, ISLAND_STIFFNESS)
  val press by animateFloatAsState(if (pressed) 0.96f else 1f, pressSpec)
  val ring = (Grid.hairline + Grid.hairline) * (slot / Grid.u48)
  val fillColor = if (active) c.primary else c.onSurface
  val ringColor = c.primary
  Box(
    modifier.size(slot)
      .onFocusChanged { onFocus(it.isFocused) }
      .hoverable(src)
      .clickable(interactionSource = src, indication = null, role = Role.Tab, onClick = onClick)
      .semantics { contentDescription = title; selected = active },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier.size(slot * DockEngine.CIRCLE)
        .graphicsLayer { scaleX = press; scaleY = press }
        .drawBehind {
          val base = if (active) 0.16f else 0.06f + 0.06f * hover
          drawCircle(fillColor.copy(alpha = base * appear()))
          if (focused) {
            val r = ring.toPx()
            drawCircle(ringColor, radius = size.minDimension / 2 - r / 2, style = Stroke(r))
          }
        },
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, tint = if (active) c.primary else c.onSurfaceVariant, modifier = Modifier.size(slot * DockEngine.ICON))
      if (active) Box(
        Modifier.align(Alignment.BottomCenter).padding(bottom = slot / 16).size(slot / 8)
          .graphicsLayer { alpha = appear() }.clip(CircleShape).background(c.primary),
      )
    }
  }
}

/** Narrow window: the current page as a toggle that opens the five pages below it. */
@Composable
private fun CompactDock(current: TarkasNavTab, onSelect: (TarkasNavTab) -> Unit, reduce: Boolean, modifier: Modifier) {
  val c = MaterialTheme.colorScheme
  val island by ShellIsland.state.collectAsState()
  val menu = island == IslandState.NAVIGATION
  val toggleFocus = remember { FocusRequester() }
  val pagesTitle = stringResource(Res.string.shell_pages)
  val dismiss = { ShellIsland.send(IslandEvent.ESCAPE); runCatching { toggleFocus.requestFocus() }; Unit }
  if (!menu) ShellIsland.menuBounds = Rect.Zero
  BackHandler(menu) { dismiss() }
  Row(
    // 48 dp tall, like the pill: each 48 dp slot draws its own 36 dp circle, so the margin to the edge is built in.
    modifier.testTag("floating_dock").clip(CircleShape).background(c.background).border(Grid.hairline, c.onSurface.copy(alpha = 0.12f), CircleShape),
    verticalAlignment = Alignment.CenterVertically,
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
            DockPages.forEachIndexed { i, tab ->
              val a = remember { Animatable(if (reduce) 1f else 0f) }
              // The cascade runs on the frame clock (tween delay), never a real delay, so a test scene never blocks.
              LaunchedEffect(Unit) { if (!reduce) a.animateTo(1f, tween(200, delayMillis = i * 50, easing = LinearOutSlowInEasing)) }
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
                Text("Ctrl+${NavOrder.indexOf(tab) + 1}", style = GridText.Small, color = c.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }
}

private class BelowAnchor(private val gap: Int) : PopupPositionProvider {
  override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
    val x = (anchorBounds.left).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
    return IntOffset(x, anchorBounds.bottom + gap)
  }
}
