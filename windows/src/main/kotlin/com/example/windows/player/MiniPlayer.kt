package com.example.windows.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.Shapes
import com.example.windows.res.Res
import com.example.windows.res.lyrics_title
import com.example.windows.res.pd_pane_details
import com.example.windows.res.player_screen_next
import com.example.windows.res.player_screen_nothing_playing
import com.example.windows.res.player_screen_previous
import com.example.windows.res.queue_header
import com.example.windows.res.tr
import com.example.windows.shell.PanelContent
import com.example.windows.shell.Panels
import com.example.windows.shell.SidePane
import com.example.windows.state.TarkasState

/** Numbers of the pill, apart from the UI so they can be tested. */
object MiniMath {
  /** Pill width in the dock (grid 384). */
  const val WidthDp = 384
  /** Hover shows prev/next beside play; without it only play stays. */
  fun showSkip(hovered: Boolean, focused: Boolean = false): Boolean = hovered || focused
}

/** Pill-shaped mini player for the floating dock: cover, title and artist, prev/play/next on hover, thin progress. A click opens the full player. */
@Composable
fun MiniPlayer(state: TarkasState, modifier: Modifier = Modifier) {
  val hooks = PlayerHost.hooks(state)
  val assets = PlayerHost.assets(state)
  PlayerShellWiring(hooks, assets)
  MiniPlayerBody(hooks, assets, modifier)
}

/**
 * What the shell needs from whichever player surface is on screen (the bottom pill or the Home's side player):
 * the pane toggler, Ctrl+Q / Ctrl+L, Esc on the expanded panel and the expanded panel itself. Both surfaces call
 * it; the shell hides the bottom transport once the side player is registered, so one of them is always composed.
 */
@Composable
internal fun PlayerShellWiring(hooks: PlayerHooks, assets: TrackAssets) {
  val titles = mapOf(
    PlayerPane.QUEUE to tr(Res.string.queue_header),
    PlayerPane.LYRICS to tr(Res.string.lyrics_title),
    PlayerPane.DETAILS to tr(Res.string.pd_pane_details),
  )
  val toggle: (PlayerPane) -> Unit = { k -> SidePane.toggle(k.name, titles.getValue(k)) { PlayerSidePane(hooks, assets, k) } }
  SideEffect {
    hooks.shellDrawsPane = true
    PlayerHost.paneToggler = toggle
    Panels.toggleQueue = { toggle(PlayerPane.QUEUE) }
    Panels.toggleLyrics = { toggle(PlayerPane.LYRICS) }
    Panels.closeOverlay = ExpandedPlayer::close
    if (Panels.overlay == null) Panels.overlay = PanelContent { s -> ExpandedPlayerPanel(PlayerHost.hooks(s), PlayerHost.assets(s)) }
  }
}

/** The pill's content, without the shell wiring, so it can be tested with plain hooks. The dock draws the pill shape and border around it. */
@Composable
internal fun MiniPlayerBody(hooks: PlayerHooks, assets: TrackAssets, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  val t by hooks.state.collectAsState()
  val art by assets.art.collectAsState()
  val ui by hooks.controller.ui.collectAsState()
  val src = remember { MutableInteractionSource() }
  val hovered by src.collectIsHoveredAsState()
  val dir = trackDirection(ui, t.track?.id)
  Box(
    modifier.fillMaxSize().testTag("mini_player")
      .hoverable(src).pointerHoverIcon(PointerIcon.Hand)
      .clickable(enabled = t.track != null) { ExpandedPlayer.show() },
  ) {
    // 12 above, the 48 row, then the 24 progress strip (12 of it bottom margin of the dock box): nothing overlaps.
    Row(Modifier.fillMaxWidth().padding(start = Grid.u24, end = Grid.u24, top = Grid.u12).height(Grid.u48), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u6)) {
      TrackSwap(Shown(t.track, art), dir, Modifier.weight(1f).height(Grid.u48)) { s ->
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
          BarCover(s, Modifier.size(Grid.u48).clip(Shapes.pill))
          Column(Modifier.weight(1f)) {
            Text(s.track?.title ?: tr(Res.string.player_screen_nothing_playing), style = GridText.BodyStrong, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.track?.artist.orEmpty(), style = GridText.Small, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
      if (MiniMath.showSkip(hovered)) IconButton(hooks::prev, Modifier.size(Grid.u48).testTag("mini_prev")) { Icon(Icons.Filled.SkipPrevious, tr(Res.string.player_screen_previous), tint = cs.onSurface) }
      IconButton(hooks::toggle, Modifier.size(Grid.u48).testTag("mini_play"), enabled = t.track != null) {
        PlayGlyph(t.playing, t.loading, cs.primary)
      }
      if (MiniMath.showSkip(hovered)) IconButton(hooks::next, Modifier.size(Grid.u48).testTag("mini_next")) { Icon(Icons.Filled.SkipNext, tr(Res.string.player_screen_next), tint = cs.onSurface) }
    }
    ThinProgress(t.fraction, t.durationMs > 0, hooks::seekFraction, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Grid.u48).padding(bottom = Grid.u12).testTag("mini_progress"), live = false)
  }
}
