package com.example.windows.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
  /** Legacy transport pill at the bottom (player area). Drawn only while [sidePlayer] is not registered. */
  var transport: PanelContent? = null
  /** Fixed player column at the left, full window height. Backed by state, so a late registration recomposes the shell. */
  var sidePlayer: PanelContent? by mutableStateOf(null)
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
 * The search. Its field lives in the [FloatingDock]; the Home reads [query] and shows the results. Ctrl+F on the
 * Biblioteca still bumps [requests], which focuses the field of the Biblioteca itself.
 */
object ShellSearch {
  private val _requests = MutableStateFlow(0)
  val requests: StateFlow<Int> = _requests.asStateFlow()
  fun request() { _requests.value++ }

  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query.asStateFlow()
  private val _open = MutableStateFlow(false)
  val isOpen: StateFlow<Boolean> = _open.asStateFlow()
  private val _focus = MutableStateFlow(0)
  /** Bumped by [openDock], so a second Ctrl+F refocuses a field that is already open. */
  internal val focusRequests: StateFlow<Int> = _focus.asStateFlow()

  fun setQuery(q: String) { _query.value = q; if (q.isNotEmpty()) _open.value = true }
  fun openDock() { _open.value = true; _focus.value++ }
  /** Closes the field and clears the query; true if there was anything to close. */
  fun close(): Boolean { val was = _open.value || _query.value.isNotEmpty(); _open.value = false; _query.value = ""; return was }
}

/** Height of the top band that holds the dock. 72 = 48 + 24. */
private val BandHeight = Grid.u48 + Grid.u24
/** From this window width on, the side player gets 384 dp instead of 192. 1536 = 4 x 384. */
private val WidePlayerWidth = Grid.u384 * 4

/** Side player, top band with the Floating Dock, main area and side pane, in the T.A.L. theme. */
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
              }
            }
          },
      ) {
        Backdrop()
        val winW = maxWidth
        val player = Panels.sidePlayer
        val legacy = player == null && Panels.transport != null
        val barInset = if (legacy) Grid.u96 + Grid.u6 else 0.dp
        CompositionLocalProvider(LocalBarInset provides barInset) {
          Row(Modifier.fillMaxSize()) {
            if (player != null) {
              Box(Modifier.width(if (winW >= WidePlayerWidth) Grid.u384 else Grid.u192).fillMaxHeight().background(glass()).testTag("side_player_slot")) {
                player.Show(state)
              }
              VerticalDivider(color = c.outlineVariant)
            }
            BoxWithConstraints(Modifier.fillMaxHeight().weight(1f)) {
              val mainW = maxWidth
              Box(Modifier.fillMaxSize().padding(top = BandHeight)) {
                Box(Modifier.fillMaxSize().padding(bottom = barInset)) {
                  val panel = Panels.slots[tab]
                  if (panel != null) panel.Show(state) else Pending(tab)
                }
                Panels.overlay?.Show(state)
                if (legacy) Panels.transport?.let { t ->
                  Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = Grid.u6).width(Grid.u384).height(Grid.u96)
                      .clip(Shapes.pill).background(glass()).border(Grid.hairline, c.onSurface.copy(alpha = 0.08f), Shapes.pill)
                  ) { t.Show(state) }
                }
              }
              TopBand(state, tab, mainW, reduce)
            }
            AnimatedVisibility(side != null, enter = expandHorizontally(tween(200)), exit = shrinkHorizontally(tween(150))) {
              side?.let { SidePaneView(it) }
            }
          }
        }
        ToastHost(state.toaster)
      }
    }
  }
}

/** Main-area width under which the dock turns into its compact toggle. */
internal val CompactDockBelow: Dp get() = DockEngine.reservedWidth(NavOrder.size + 1).dp + Grid.u48

/**
 * Where am I and where can I go: the dock at the top centre, the route title at the left when there is room for it,
 * the page's actions at the right. The band does not clip, so magnified tiles may reach over the top of the content.
 */
@Composable
private fun TopBand(state: TarkasState, tab: TarkasNavTab, mainW: Dp, reduce: Boolean) {
  val c = MaterialTheme.colorScheme
  val compact = mainW < CompactDockBelow
  val dockW = if (compact) Grid.u192 + Grid.u96 else DockEngine.reservedWidth(NavOrder.size + 1).dp
  val room = (mainW - dockW) / 2
  Box(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().height(BandHeight).padding(horizontal = Grid.margin), verticalAlignment = Alignment.CenterVertically) {
      if (room >= Grid.u192 + Grid.margin) {
        Text(stringResource(tab.label), style = GridText.Title, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(Grid.u192))
      }
      Box(Modifier.weight(1f))
      Panels.topActions[tab]?.let { a ->
        Row(horizontalArrangement = Arrangement.spacedBy(Grid.u12), verticalAlignment = Alignment.CenterVertically) { with(a) { Show(state) } }
      }
    }
    FloatingDock(
      current = tab,
      onSelect = { state.selectTab(it) },
      onTyped = { if (state.currentTab.value != TarkasNavTab.HOME) { SidePane.close(); state.selectTab(TarkasNavTab.HOME) } },
      reduceMotion = reduce,
      compact = compact,
      modifier = Modifier.align(Alignment.TopCenter).padding(top = Grid.u6),
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
