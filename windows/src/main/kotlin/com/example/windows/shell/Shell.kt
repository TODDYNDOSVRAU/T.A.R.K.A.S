package com.example.windows.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.TooltipArea
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
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
import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * What an area session provides for one tab. The shell asks [Show] for the tab being shown. Until an area
 * registers its screen, the shell draws [Pending] for it.
 */
fun interface PanelContent {
  @Composable fun Show(state: TarkasState)
}

/** Actions an area puts in the top bar, at the right of the route title (search field, buttons). */
fun interface TopActions {
  @Composable fun RowScope.Show(state: TarkasState)
}

/** Registry the area sessions fill. Desktop contract: docs/WINDOWS-DESKTOP.md. */
object Panels {
  /** Main area content for each sidebar entry. */
  val slots: MutableMap<TarkasNavTab, PanelContent> = mutableMapOf()
  /** Top-bar actions for each sidebar entry. */
  val topActions: MutableMap<TarkasNavTab, TopActions> = mutableMapOf()
  /** Always-visible transport bar (player area). Null until the player registers it. */
  var transport: PanelContent? = null
  /** Ctrl+Q and Ctrl+L: the player registers what opens its queue and lyrics in the [SidePane]. */
  var toggleQueue: (() -> Unit)? = null
  var toggleLyrics: (() -> Unit)? = null
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

/** Ctrl+F: the shell bumps [requests]; the area whose search field is shown collects it and requests focus. */
object ShellSearch {
  private val _requests = MutableStateFlow(0)
  val requests: StateFlow<Int> = _requests.asStateFlow()
  fun request() { _requests.value++ }
}

/** Below this width the sidebar collapses to its 48 dp icon rail. 960 dp = 5 x 192. */
private val CollapseWidth = Grid.u192 * 5

/** Sidebar, top bar, main area, side pane and transport bar, in the T.A.L. theme. */
@Composable
fun TarkasShell(state: TarkasState, back: BackDispatcher, lifecycle: AppLifecycle) {
  val mode by state.themeMode.collectAsState()
  TarkasTheme(themeMode = mode) {
    ProvidePlatform(state.toaster, back, lifecycle) {
      val c = MaterialTheme.colorScheme
      val tab by state.currentTab.collectAsState()
      val side by SidePane.current.collectAsState()
      BoxWithConstraints(Modifier.fillMaxSize()) {
        Backdrop()
        val compact = maxWidth < CollapseWidth
        Column(Modifier.fillMaxSize()) {
          Row(Modifier.fillMaxWidth().weight(1f)) {
            SideNav(state, tab, compact)
            VerticalDivider(color = c.outlineVariant)
            Column(Modifier.fillMaxHeight().weight(1f)) {
              TopBar(state, tab)
              HorizontalDivider(color = c.outlineVariant)
              Box(Modifier.fillMaxSize()) {
                val panel = Panels.slots[tab]
                if (panel != null) panel.Show(state) else Pending(tab)
              }
            }
            AnimatedVisibility(side != null, enter = expandHorizontally(tween(200)), exit = shrinkHorizontally(tween(150))) {
              side?.let { SidePaneView(it) }
            }
          }
          Panels.transport?.let { t ->
            HorizontalDivider(color = c.outlineVariant)
            Box(Modifier.fillMaxWidth().height(Grid.u96).background(glass())) { t.Show(state) }
          }
        }
        ToastHost(state.toaster)
      }
    }
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

/** Ctrl+1..6 follows this order. Settings sits at the bottom, apart from the content areas. */
internal val NavOrder = listOf(TarkasNavTab.HOME, TarkasNavTab.LIBRARY, TarkasNavTab.MUSIC, TarkasNavTab.AUDIO, TarkasNavTab.ASSISTANT, TarkasNavTab.SETTINGS)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SideNav(state: TarkasState, current: TarkasNavTab, compact: Boolean) {
  val c = MaterialTheme.colorScheme
  Column(
    Modifier.width(if (compact) Grid.u48 + Grid.u24 else Grid.u192).fillMaxHeight().background(glass()).padding(vertical = Grid.inset, horizontal = Grid.u12),
    verticalArrangement = Arrangement.spacedBy(Grid.u6)
  ) {
    Box(Modifier.fillMaxWidth().height(Grid.u48), contentAlignment = if (compact) Alignment.Center else Alignment.CenterStart) {
      Text(if (compact) "T" else "T.A.R.K.A.S.", style = GridText.Title, color = c.onSurface, maxLines = 1, modifier = if (compact) Modifier else Modifier.padding(horizontal = Grid.u12))
    }
    NavOrder.forEachIndexed { i, tab ->
      if (tab == TarkasNavTab.SETTINGS) Box(Modifier.weight(1f))
      val label = stringResource(tab.label)
      val item = @Composable {
        NavItem(tab, label, i + 1, tab == current, compact) { SidePane.close(); state.selectTab(tab) }
      }
      if (compact) TooltipArea(tooltip = { Tooltip("$label  Ctrl+${i + 1}") }) { item() } else item()
    }
  }
}

@Composable
private fun NavItem(tab: TarkasNavTab, label: String, index: Int, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
  val c = MaterialTheme.colorScheme
  Row(
    Modifier.fillMaxWidth().height(Grid.u48).clip(Grid.r12)
      .background(if (selected) c.onSurface.copy(alpha = 0.10f) else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(horizontal = Grid.u12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(Grid.u12)
  ) {
    Icon(tab.icon(), contentDescription = label, tint = if (selected) c.primary else c.onSurfaceVariant, modifier = Modifier.size(Grid.u24))
    if (!compact) {
      Text(label, style = if (selected) GridText.BodyStrong else GridText.Body, color = if (selected) c.onSurface else c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
      Text("$index", style = GridText.Small, color = c.onSurfaceVariant)
    }
  }
}

@Composable
internal fun Tooltip(text: String) {
  val c = MaterialTheme.colorScheme
  Box(Modifier.clip(Grid.r6).background(c.inverseSurface).padding(horizontal = Grid.u12, vertical = Grid.u6)) {
    Text(text, style = GridText.Small, color = c.inverseOnSurface)
  }
}

/** Where am I (route title) and what can I do here (the area's actions). */
@Composable
private fun TopBar(state: TarkasState, tab: TarkasNavTab) {
  val c = MaterialTheme.colorScheme
  Row(
    Modifier.fillMaxWidth().height(Grid.u48 + Grid.u24).padding(horizontal = Grid.margin),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    Text(stringResource(tab.label), style = GridText.Title, color = c.onSurface, maxLines = 1)
    Box(Modifier.weight(1f))
    Panels.topActions[tab]?.let { a -> with(a) { Show(state) } }
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
