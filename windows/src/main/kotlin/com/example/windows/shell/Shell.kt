package com.example.windows.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Shapes
import com.example.ui.theme.TarkasTheme
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.base.ProvidePlatform
import com.example.windows.base.ToastHost
import com.example.windows.state.TarkasState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.stringResource

/**
 * What an area session provides for one tab. The shell asks [Show] for the tab being shown. Until an area
 * registers its screen, the shell draws [Pending] for it.
 */
fun interface PanelContent {
  @Composable fun Show(state: TarkasState)
}

/** Actions an area puts in the top band, at the right of the dock (buttons). */
fun interface TopActions {
  @Composable fun RowScope.Show(state: TarkasState)
}

/** Registry the area sessions fill. Desktop contract: docs/WINDOWS-DESKTOP.md. */
object Panels {
  /** Main area content for each page. */
  val slots: MutableMap<TarkasNavTab, PanelContent> = mutableMapOf()
  /** Top-band actions for each page. */
  val topActions: MutableMap<TarkasNavTab, TopActions> = mutableMapOf()
  /**
   * The bottom-left corner (owner D10, 2026-11-27): the compact mini-player on every page and, on the Home only, the
   * floating art card above it. The player registers it and reads [LocalCornerInsets] to know which of the two to draw.
   * While it is not registered, the shell draws [transport] there in a 384 x 96 glass pill.
   */
  var corner: PanelContent? by mutableStateOf(null)
  /** Transport pill (player area), drawn in the corner while [corner] is not registered. */
  var transport: PanelContent? by mutableStateOf(null)
  /** Former player column. The shell no longer draws it (owner, 2026-11-27); kept so older registrations compile. */
  var sidePlayer: PanelContent? by mutableStateOf(null)
  /** Replaces the route title at the top left for a page, such as the Home greeting. 384 wide when there is room. */
  val topTitle: MutableMap<TarkasNavTab, PanelContent> = mutableStateMapOf()
  /** The player's stack of queue cards. The Home places and sizes it; the shell only keeps the registration. */
  var queueStack: PanelContent? by mutableStateOf(null)
  /** Ctrl+Q and Ctrl+L: the player registers what opens its queue and lyrics in the [SidePane]. */
  var toggleQueue: (() -> Unit)? = null
  var toggleLyrics: (() -> Unit)? = null
  /** Full-area panel drawn over the main area (the player's expanded panel); it draws nothing while closed. */
  var overlay: PanelContent? = null
  /** Esc: closes the overlay when open; true if it did. */
  var closeOverlay: (() -> Boolean)? = null
}

/** The side pane at the right of the main area (replaces Android bottom sheets). One at a time; [id] lets a button toggle its own. */
object SidePane {
  class Content(val id: String, val title: String, val body: @Composable () -> Unit)
  private val _current = MutableStateFlow<Content?>(null)
  val current: StateFlow<Content?> = _current.asStateFlow()
  fun open(id: String, title: String, body: @Composable () -> Unit) { _current.value = Content(id, title, body) }
  fun toggle(id: String, title: String, body: @Composable () -> Unit) {
    if (_current.value?.id == id) close() else open(id, title, body)
  }
  fun isOpen(id: String) = _current.value?.id == id
  fun close(): Boolean { val was = _current.value != null; _current.value = null; return was }
}

/**
 * The search lives in the Biblioteca (owner D1, 2026-11-27). Ctrl+F opens the Biblioteca and bumps [requests], which
 * focuses its own field; the island has no field any more.
 */
object ShellSearch {
  private val _requests = MutableStateFlow(0)
  val requests: StateFlow<Int> = _requests.asStateFlow()
  fun request() { _requests.value++ }

  /** Ctrl+F from any page: to the Biblioteca, its search focused. */
  fun openLibrary(state: TarkasState) {
    SidePane.close()
    if (state.currentTab.value != TarkasNavTab.LIBRARY) state.selectTab(TarkasNavTab.LIBRARY)
    request()
  }

  /** Former dock query. Always empty now; kept so the readers of the old contract compile until they move. */
  val query: StateFlow<String> = MutableStateFlow("").asStateFlow()
  /** Nothing to close any more; false, so Esc goes on to the next layer. */
  fun close(): Boolean = false
}

/**
 * What the shell tells the page about the bottom-left corner. [lane] is the width the corner takes from the left edge,
 * [height] how much it takes from the bottom (24 dp margin included), [card] whether the art card is shown above the
 * mini-player (Home only). The corner floats over the page; a page lays out around it ([LocalCornerInsets]) or pads its scrolling end with
 * [LocalBarInset], which is [MiniPlayerInset] while the corner is shown.
 */
data class CornerInsets(val lane: Dp, val height: Dp, val card: Boolean)

val LocalCornerInsets = staticCompositionLocalOf { CornerInsets(0.dp, 0.dp, false) }

/** Height of the top band that holds the dock. 72 = 48 + 24. */
internal val BandHeight = Grid.u48 + Grid.u24
/** The mini-player: 384 x 96, 24 from the left and bottom edges. */
internal val CornerWidth = Grid.u384
internal val MiniPlayerHeight = Grid.u96
/** Art card (192) plus the 12 gap above the mini-player. */
internal val CardHeight = Grid.u192 + Grid.u12
/** Bottom space every page keeps free for the mini-player: 96 + 24. */
internal val MiniPlayerInset = MiniPlayerHeight + Grid.margin

/** Top band with the Dynamic Island and Ajustes, main area with the corner player, side pane, in the T.A.L. theme. */
@Composable
fun TarkasShell(state: TarkasState, back: BackDispatcher, lifecycle: AppLifecycle) {
  val mode by state.themeMode.collectAsState()
  TarkasTheme(themeMode = mode) {
    ProvidePlatform(state.toaster, back, lifecycle) {
      val c = MaterialTheme.colorScheme
      val tab by state.currentTab.collectAsState()
      val side by SidePane.current.collectAsState()
      val reduce by state.reduceMotion.collectAsState()
      var origin by remember { mutableStateOf(Offset.Zero) }
      BoxWithConstraints(
        Modifier.fillMaxSize()
          .onGloballyPositioned { origin = it.positionInWindow() }
          .pointerInput(Unit) {
            // Every press, seen before anyone handles it and never consumed: a press outside the island closes it.
            awaitPointerEventScope {
              while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.type == PointerEventType.Press) e.changes.firstOrNull()?.let { ShellIsland.press(origin + it.position) }
                // The silk lifts under the pointer (SilkBackdrop): it only reads where the pointer is.
                when (e.type) {
                  PointerEventType.Move, PointerEventType.Enter, PointerEventType.Press -> e.changes.firstOrNull()?.let {
                    if (size.width > 0 && size.height > 0) ShellSilk.Pointer.at(it.position.x / size.width, it.position.y / size.height)
                  }
                  PointerEventType.Exit -> ShellSilk.Pointer.left()
                  else -> {}
                }
              }
            }
          },
      ) {
        val glassBackdrop = remember { GlassBackdrop() }
        Backdrop(Modifier.recordsGlass(glassBackdrop, GlassPart.Back), still = reduce)
        val winW = maxWidth
        val corner = Panels.corner
        val transport = Panels.transport
        // Música is the full player, with its own cover, progress and transport: the corner would repeat them over it.
        val hasCorner = (corner != null || transport != null) && tab != TarkasNavTab.MUSIC
        val card = tab == TarkasNavTab.HOME
        val insets = if (!hasCorner) CornerInsets(0.dp, 0.dp, false)
        else CornerInsets(CornerWidth + Grid.margin * 2, MiniPlayerInset + if (card) CardHeight else 0.dp, card)
        // The page fills the whole height and the corner floats over it: no band is reserved at the bottom. Pages that
        // scroll pad their own end by LocalBarInset so the last row can still leave the player.
        CompositionLocalProvider(
          LocalBarInset provides if (hasCorner) MiniPlayerInset else 0.dp,
          LocalCornerInsets provides insets,
          LocalGlassBackdrop provides glassBackdrop,
        ) {
          Row(Modifier.fillMaxSize().padding(top = BandHeight)) {
            Box(Modifier.fillMaxHeight().weight(1f)) {
              Box(Modifier.fillMaxSize().recordsGlass(glassBackdrop, GlassPart.Page).testTag("shell_content")) {
                val panel = Panels.slots[tab]
                if (panel != null) panel.Show(state) else Pending(tab)
              }
              if (hasCorner) Box(
                Modifier.align(Alignment.BottomStart).padding(start = Grid.margin, bottom = Grid.margin)
                  .width(CornerWidth).height(insets.height - Grid.margin).testTag("shell_corner"),
                contentAlignment = Alignment.BottomStart,
              ) {
                if (corner != null) corner.Show(state)
                else Box(
                  Modifier.width(CornerWidth).height(MiniPlayerHeight)
                    .clip(Shapes.pill).background(glass()).border(Grid.hairline, c.onSurface.copy(alpha = 0.08f), Shapes.pill),
                ) { transport?.Show(state) }
              }
              Panels.overlay?.Show(state)
            }
            AnimatedVisibility(side != null, enter = expandHorizontally(tween(200)), exit = shrinkHorizontally(tween(150))) {
              side?.let { SidePaneView(it) }
            }
          }
          // Window wide, after the main area: the island is centred on the window, whatever is open at the sides.
          TopBand(state, tab, winW, reduce)
        }
        ToastHost(state.toaster)
      }
    }
  }
}

/** Window width under which the island turns into its compact toggle. 403.2 + 48 = 451.2 dp. */
internal val CompactDockBelow: Dp get() = DockEngine.reservedWidth(DockPages.size).dp + Grid.u48

/**
 * Where am I and where can I go: the island at the top centre of the window, the route title (or what the page puts
 * there, such as the Home greeting) at the left when there is room for it, the page's actions and the one Ajustes
 * button (Ctrl+,) at the right. The band does not clip, so magnified tiles may reach over the top of the content.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBand(state: TarkasState, tab: TarkasNavTab, winW: Dp, reduce: Boolean) {
  val c = MaterialTheme.colorScheme
  val compact = winW < CompactDockBelow
  val dockW = if (compact) Grid.u192 + Grid.u96 else DockEngine.reservedWidth(DockPages.size).dp
  val room = (winW - dockW) / 2
  val playing by state.isPlaying.collectAsState()
  // Read only by the island's drawing, so the play position never recomposes the band.
  val pos = state.positionMs.collectAsState()
  val dur = state.durationMs.collectAsState()
  val settingsLabel = stringResource(TarkasNavTab.SETTINGS.label)
  Box(Modifier.fillMaxWidth().testTag("top_band")) {
    Row(Modifier.fillMaxWidth().height(BandHeight).padding(horizontal = Grid.margin), verticalAlignment = Alignment.CenterVertically) {
      val titleW = when {
        room >= Grid.u384 + Grid.margin -> Grid.u384
        room >= Grid.u192 + Grid.margin -> Grid.u192
        else -> 0.dp
      }
      if (titleW > 0.dp) Box(Modifier.width(titleW).testTag("top_title"), contentAlignment = Alignment.CenterStart) {
        val custom = Panels.topTitle[tab]
        if (custom != null) custom.Show(state)
        else Text(stringResource(tab.label), style = GridText.Title, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Box(Modifier.weight(1f))
      Row(horizontalArrangement = Arrangement.spacedBy(Grid.u12), verticalAlignment = Alignment.CenterVertically) {
        Panels.topActions[tab]?.let { a -> with(a) { Show(state) } }
        TooltipArea(tooltip = { Tooltip("$settingsLabel  Ctrl+,") }, delayMillis = 400) {
          val on = tab == TarkasNavTab.SETTINGS
          IconButton(
            onClick = { SidePane.close(); state.selectTab(TarkasNavTab.SETTINGS) },
            modifier = Modifier.size(Grid.u48).testTag("top_settings").semantics { contentDescription = settingsLabel; selected = on },
          ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = if (on) c.primary else c.onSurfaceVariant, modifier = Modifier.size(Grid.u24))
          }
        }
      }
    }
    FloatingDock(
      current = tab,
      onSelect = { state.selectTab(it) },
      reduceMotion = reduce,
      compact = compact,
      modifier = Modifier.align(Alignment.TopCenter).padding(top = Grid.u6),
      playing = playing,
      fraction = { val d = dur.value; if (d > 0L) pos.value.toFloat() / d else 0f },
    )
  }
}

internal fun TarkasNavTab.icon(): ImageVector = when (this) {
  TarkasNavTab.HOME -> Icons.Outlined.Home
  TarkasNavTab.MUSIC -> Icons.Outlined.PlayCircle
  TarkasNavTab.LIBRARY -> Icons.Outlined.LibraryMusic
  TarkasNavTab.AUDIO -> Icons.Outlined.Equalizer
  TarkasNavTab.ASSISTANT -> Icons.Outlined.AutoAwesome
  TarkasNavTab.SETTINGS -> Icons.Outlined.Settings
}

/** Ctrl+1..6 follows this order, which is also the order of the dock. */
internal val NavOrder = listOf(TarkasNavTab.HOME, TarkasNavTab.LIBRARY, TarkasNavTab.MUSIC, TarkasNavTab.AUDIO, TarkasNavTab.ASSISTANT, TarkasNavTab.SETTINGS)

@Composable
internal fun Tooltip(text: String) {
  val c = MaterialTheme.colorScheme
  Box(Modifier.clip(Grid.r6).background(c.inverseSurface).padding(horizontal = Grid.u12, vertical = Grid.u6)) {
    Text(text, style = GridText.Small, color = c.inverseOnSurface, maxLines = 1)
  }
}

@Composable
private fun SidePaneView(content: SidePane.Content) {
  val c = MaterialTheme.colorScheme
  Row(Modifier.fillMaxHeight()) {
    VerticalDivider(color = c.outlineVariant)
    Column(Modifier.width(Grid.u384).fillMaxHeight().background(glass())) {
      Row(Modifier.fillMaxWidth().height(Grid.u48 + Grid.u24).padding(start = Grid.margin, end = Grid.u12), verticalAlignment = Alignment.CenterVertically) {
        Text(content.title, style = GridText.BodyStrong, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = { SidePane.close() }) { Icon(Icons.Outlined.Close, contentDescription = "Esc", tint = c.onSurfaceVariant, modifier = Modifier.size(Grid.u24)) }
      }
      HorizontalDivider(color = c.outlineVariant)
      Box(Modifier.fillMaxSize()) { content.body() }
    }
  }
}

/** Honest placeholder for an area that has not registered its screen yet. No fake data. */
@Composable
private fun Pending(tab: TarkasNavTab) {
  val c = MaterialTheme.colorScheme
  Box(Modifier.fillMaxSize().padding(Grid.margin), contentAlignment = Alignment.Center) {
    Text(stringResource(tab.label), style = GridText.Body, color = c.onSurfaceVariant)
  }
}
