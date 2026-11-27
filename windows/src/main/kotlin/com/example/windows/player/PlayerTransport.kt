package com.example.windows.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import com.example.windows.res.player_common_pause
import com.example.windows.res.player_common_play
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
import com.example.windows.biblioteca.displayArtist
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
  // No position here: the clock is read in PositionProgress only.
  val t by hooks.steady.collectAsState()
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
    if (Panels.overlay == null) Panels.overlay = PanelContent { s -> val reduced by s.reduceMotion.collectAsState(); ExpandedPlayerPanel(PlayerHost.hooks(s), PlayerHost.assets(s), reduced) }
  }
  Row(Modifier.fillMaxSize().padding(horizontal = Grid.margin), verticalAlignment = Alignment.CenterVertically) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
      val ui by hooks.controller.ui.collectAsState()
      val dir = trackDirection(ui, t.track?.id)
      TrackSwap(Shown(t.track, art), dir, Modifier.weight(1f).height(Grid.u48)) { s ->
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12)) {
          BarCover(s, Modifier.size(Grid.u48).clip(Grid.r6).clickable(enabled = s.track != null) { ExpandedPlayer.toggle() })
          Column(Modifier.weight(1f)) {
            Text(s.track?.title ?: tr(Res.string.player_screen_nothing_playing), style = GridText.BodyStrong, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(displayArtist(s.track?.artist.orEmpty()), style = GridText.Small, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
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
          PlayGlyph(t.playing, t.loading, cs.primary)
        }
        IconButton(hooks::next, Modifier.size(Grid.u48)) { Icon(Icons.Filled.SkipNext, tr(Res.string.player_screen_next), tint = cs.onSurface) }
      }
      PositionProgress(hooks, Modifier.fillMaxWidth().testTag("bar_progress"))
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

/**
 * A thin line with a fill; a tap sets the value and a drag moves it. The hit area is 24 dp tall around the 6 dp
 * line, a 12 dp knob marks the position while the pointer is over it or dragging.
 *
 * With [live] false (the seek line) a drag only scrubs: the fill and [onScrub] follow the pointer and [onSet]
 * is called once, on release, so the engine reopens the file once and not on every pixel. The volume line keeps
 * [live] true: each move sets the level.
 */
@Composable
internal fun ThinProgress(
  value: Float, enabled: Boolean, onSet: (Float) -> Unit, modifier: Modifier,
  live: Boolean = true, onScrub: (Float?) -> Unit = {},
) {
  val cs = MaterialTheme.colorScheme
  val src = remember { MutableInteractionSource() }
  val hovered by src.collectIsHoveredAsState()
  var scrub by remember { mutableStateOf<Float?>(null) }
  val shown = (scrub ?: value).coerceIn(0f, 1f)
  fun frac(x: Float, w: Int) = if (w <= 0) 0f else (x / w).coerceIn(0f, 1f)
  BoxWithConstraints(
    modifier.height(Grid.u24).hoverable(src).then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
      .pointerInput(enabled) {
        if (enabled) detectTapGestures { o -> onSet(frac(o.x, size.width)) }
      }.pointerInput(enabled, live) {
        if (enabled) detectDragGestures(
          onDragStart = { o -> val f = frac(o.x, size.width); scrub = f; onScrub(f); if (live) onSet(f) },
          onDrag = { ch, _ -> val f = frac(ch.position.x, size.width); scrub = f; onScrub(f); if (live) onSet(f) },
          onDragEnd = { val f = scrub; scrub = null; onScrub(null); if (!live && f != null) onSet(f) },
          onDragCancel = { scrub = null; onScrub(null) },
        )
      },
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(Modifier.fillMaxWidth().height(Grid.u6).clip(Grid.r6).background(cs.onSurface.copy(alpha = 0.2f)))
    Box(Modifier.width(maxWidth * shown).height(Grid.u6).clip(Grid.r6).background(cs.primary))
    if (enabled && (hovered || scrub != null)) {
      Box(Modifier.offset(x = (maxWidth - Grid.u12) * shown).size(Grid.u12).clip(CircleShape).background(cs.primary).testTag("progress_knob"))
    }
  }
}

/**
 * The play/pause glyph of a transport button. While the file opens it is a progress ring: the state is
 * "loading", which is neither playing nor paused.
 */
@Composable
internal fun PlayGlyph(playing: Boolean, loading: Boolean, tint: Color, modifier: Modifier = Modifier.size(Grid.icon)) {
  if (loading) {
    CircularProgressIndicator(modifier.testTag("play_loading"), color = tint, strokeWidth = Grid.u6 / 3)
  } else {
    Icon(
      if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
      tr(if (playing) Res.string.player_common_pause else Res.string.player_common_play), modifier, tint = tint,
    )
  }
}

/** The 48 dp cover of the bar: the track's own picture, or its colour and a note only while there is none. */
@Composable
internal fun BarCover(s: Shown, modifier: Modifier) {
  val cs = MaterialTheme.colorScheme
  val a = s.art
  if (a != null) Image(a, s.track?.title, modifier, contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
  else {
    val tint = s.track?.let { Color(it.colorHex or 0xFF000000) } ?: cs.primary
    Box(modifier.background(tint.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
      Icon(Icons.Filled.MusicNote, null, Modifier.size(Grid.icon), tint = cs.onPrimary)
    }
  }
}
