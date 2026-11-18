package com.example.windows.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.example.windows.shell.PanelContent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import com.example.windows.res.library_section_favorites
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.Res
import com.example.windows.res.lyrics_title
import com.example.windows.res.pd_pane_details
import com.example.windows.res.player_screen_next
import com.example.windows.res.player_screen_nothing_playing
import com.example.windows.res.player_screen_previous
import com.example.windows.res.player_screen_queue
import com.example.windows.res.queue_header
import com.example.windows.res.tr
import com.example.windows.shell.Panels
import com.example.windows.shell.SidePane
import com.example.windows.state.TarkasState

/** The always-on 96 dp bar: cover, title and artist left; prev, play, next over a thin progress line; volume, queue, lyrics right. */
@Composable
fun TransportBar(state: TarkasState) {
  val hooks = PlayerHost.hooks(state)
  val assets = PlayerHost.assets(state)
  val cs = MaterialTheme.colorScheme
  val t by hooks.state.collectAsState()
  val art by assets.art.collectAsState()
  val side by SidePane.current.collectAsState()
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
  Row(Modifier.fillMaxSize().padding(horizontal = Grid.margin), verticalAlignment = Alignment.CenterVertically) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
      val tint = t.track?.let { Color(it.colorHex or 0xFF000000) } ?: cs.primary
      val a = art
      val openPanel = Modifier.size(Grid.u48).clip(Grid.r6).clickable(enabled = t.track != null) { ExpandedPlayer.show() }
      if (a != null) Image(a, t.track?.title, openPanel, contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
      else Box(openPanel.background(tint.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.MusicNote, null, Modifier.size(Grid.icon), tint = cs.onPrimary)
      }
      Column(Modifier.weight(1f)) {
        Text(t.track?.title ?: tr(Res.string.player_screen_nothing_playing), style = GridText.BodyStrong, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.track?.artist.orEmpty(), style = GridText.Small, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      val lib = com.example.windows.biblioteca.BibliotecaHost.state(state)
      val favs by lib.store.favorites.collectAsState()
      val cur = t.track
      val isFav = cur != null && cur.id in favs
      IconButton({ cur?.let(lib.store::toggleFavorite) }, Modifier.size(Grid.u48), enabled = cur != null) {
        Icon(if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, tr(Res.string.library_section_favorites), tint = if (isFav) cs.primary else cs.onSurfaceVariant)
      }
    }
    Column(Modifier.width(Grid.u384), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(hooks::prev, Modifier.size(Grid.u48)) { Icon(Icons.Filled.SkipPrevious, tr(Res.string.player_screen_previous), tint = cs.onSurface) }
        IconButton(hooks::toggle, Modifier.size(Grid.u48), enabled = t.track != null) {
          Icon(if (t.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(Grid.icon), tint = cs.primary)
        }
        IconButton(hooks::next, Modifier.size(Grid.u48)) { Icon(Icons.Filled.SkipNext, tr(Res.string.player_screen_next), tint = cs.onSurface) }
      }
      ThinProgress(t.fraction, t.durationMs > 0, hooks::seekFraction, Modifier.fillMaxWidth())
    }
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Filled.VolumeUp, null, Modifier.size(Grid.icon), tint = cs.onSurfaceVariant)
      ThinProgress(t.volume, true, hooks::setVolume, Modifier.width(Grid.u96).padding(horizontal = Grid.u6))
      IconButton({ toggle(PlayerPane.QUEUE) }, Modifier.size(Grid.u48)) {
        Icon(Icons.Filled.QueueMusic, tr(Res.string.player_screen_queue), tint = if (side?.id == PlayerPane.QUEUE.name) cs.primary else cs.onSurfaceVariant)
      }
      IconButton({ toggle(PlayerPane.LYRICS) }, Modifier.size(Grid.u48)) {
        Icon(Icons.Filled.Lyrics, tr(Res.string.lyrics_title), tint = if (side?.id == PlayerPane.LYRICS.name) cs.primary else cs.onSurfaceVariant)
      }
    }
  }
}

/** A thin line with a fill; a tap or drag sets the value. The hit area is 24 dp tall around the 6 dp line. */
@Composable
private fun ThinProgress(value: Float, enabled: Boolean, onSet: (Float) -> Unit, modifier: Modifier) {
  val cs = MaterialTheme.colorScheme
  BoxWithConstraints(
    modifier.height(Grid.u24).pointerInput(enabled) {
      if (enabled) detectTapGestures { o -> onSet((o.x / size.width).coerceIn(0f, 1f)) }
    }.pointerInput(enabled) {
      if (enabled) detectDragGestures { ch, _ -> onSet((ch.position.x / size.width).coerceIn(0f, 1f)) }
    },
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(Modifier.fillMaxWidth().height(Grid.u6).clip(Grid.r6).background(cs.onSurface.copy(alpha = 0.2f)))
    Box(Modifier.width(maxWidth * value.coerceIn(0f, 1f)).height(Grid.u6).clip(Grid.r6).background(cs.primary))
  }
}
