package com.example.windows.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.theme.Shapes
import com.example.windows.biblioteca.displayArtist
import com.example.windows.res.Res
import com.example.windows.res.player_screen_next
import com.example.windows.res.player_screen_nothing_playing
import com.example.windows.res.player_screen_previous
import com.example.windows.res.tr
import com.example.windows.shell.LocalCornerInsets
import com.example.windows.shell.PanelContent
import com.example.windows.state.TarkasState

/** Numbers of the bottom-left corner (D10), apart from the UI so they can be tested. */
object CompactMath {
  const val WidthDp = 384
  const val HeightDp = 96
  /** The art card above the player, Home only. */
  const val CardDp = 192
  const val GapDp = 12
  /** Left inset of the card: the pill's own start padding, so the card's edge is the cover's edge. */
  const val CardInsetDp = 24
  /** Crossfade between two tracks' cards. */
  const val FadeMs = 200
}

/**
 * The corner of every page: the compact player, 384 x 96, and, on the Home only ([LocalCornerInsets] says so), the 192
 * art card above it with a 12 dp gap. Registered in [com.example.windows.shell.Panels.corner]; it replaces the old bottom
 * pill and the side column. Static: it has no rotating layer of its own, only the art card leans (D4).
 */
@Composable
fun CompactPlayer(state: TarkasState, modifier: Modifier = Modifier) {
  val hooks = PlayerHost.hooks(state)
  val assets = PlayerHost.assets(state)
  val reduced by state.reduceMotion.collectAsState()
  PlayerShellWiring(hooks, assets)
  CompactPlayerBody(hooks, assets, LocalCornerInsets.current.card, reduced, modifier)
}

/** Without the shell wiring and state, so it can be tested with plain hooks. */
@Composable
internal fun CompactPlayerBody(hooks: PlayerHooks, assets: TrackAssets, showCard: Boolean, reduced: Boolean, modifier: Modifier = Modifier) {
  Column(modifier.width(CompactMath.WidthDp.dp).testTag("compact_corner"), horizontalAlignment = Alignment.Start) {
    if (showCard) {
      // The pill is a capsule: its content (the round cover) starts 24 dp inside it, so the card lines up with the
      // cover and not with the capsule's curved edge, which made it look pushed to the left.
      Box(Modifier.padding(start = CompactMath.CardInsetDp.dp)) { ArtCard(hooks, assets, reduced) }
      Spacer(Modifier.height(Grid.u12))
    }
    CompactPill(hooks, assets, reduced)
  }
}

/** The 192 card of the current cover: leans toward the pointer, crossfades in 200 ms when the track changes, a click opens the player. */
@Composable
private fun ArtCard(hooks: PlayerHooks, assets: TrackAssets, reduced: Boolean) {
  val t by hooks.steady.collectAsState()
  val art by assets.art.collectAsState()
  val shown = Shown(t.track, art)
  TiltCard(Modifier.size(Grid.u192).testTag("art_card"), reduced = reduced, keyboard = true) {
    AnimatedContent(
      targetState = shown,
      modifier = Modifier.fillMaxSize()
        .pointerHoverIcon(if (t.track != null) PointerIcon.Hand else PointerIcon.Default)
        .clickable(enabled = t.track != null) { ExpandedPlayer.show() },
      contentKey = { it.track?.id },
      transitionSpec = { fadeIn(tween(CompactMath.FadeMs)).togetherWith(fadeOut(tween(CompactMath.FadeMs))) },
      label = "artCard",
    ) { s -> BarCover(s, Modifier.fillMaxSize().testTag("art_card_cover")) }
  }
}

/** Row one: cover, title and artist, previous, play and next. Row two: elapsed, the seek line, total and the volume. */
@Composable
private fun CompactPill(hooks: PlayerHooks, assets: TrackAssets, reduced: Boolean) {
  val cs = MaterialTheme.colorScheme
  // No position here: the clock is read in PositionSeekRow only.
  val t by hooks.steady.collectAsState()
  val art by assets.art.collectAsState()
  val ui by hooks.controller.ui.collectAsState()
  val dir = trackDirection(ui, t.track?.id)
  val on = t.track != null
  // Idle: the text keeps its full colour (AA over the glass); only the controls, which do nothing, are dimmed.
  val ctl = if (on) cs.onSurface else cs.onSurface.copy(alpha = GlassSpec.DisabledAlpha)
  // The glass surface (LiquidGlass.kt, contract of expo-glass-effect): regular style, refracted backdrop, the nine-shadow
  // border, hover growth and a light that follows the pointer; 50% content and border when off.
  GlassView(
    effect = PillGlass.effect, tint = PillGlass.tint, interactive = true, enabled = on, reduced = reduced, dimContent = false,
    modifier = Modifier.testTag("compact_player"),
  ) {
  Column(Modifier.fillMaxSize()) {
    // 12 above, the 48 row, 0 between, the 24 row, 12 below: 96, nothing overlaps.
    Row(
      Modifier.fillMaxWidth().padding(start = Grid.u24, end = Grid.u12, top = Grid.u12).height(Grid.u48),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TrackSwap(Shown(t.track, art), dir, Modifier.weight(1f).height(Grid.u48)) { s ->
        Row(
          Modifier.fillMaxSize().pointerHoverIcon(if (on) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = on) { ExpandedPlayer.show() }.testTag("compact_open"),
          verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12),
        ) {
          BarCover(s, Modifier.size(Grid.u48).clip(Shapes.pill))
          Column(Modifier.weight(1f)) {
            Text(
              s.track?.title ?: tr(Res.string.player_screen_nothing_playing), style = GridText.BodyStrong, color = cs.onSurface,
              maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("compact_title"),
            )
            // A playback problem takes the artist's line, as in the side player: the height never changes.
            val problem = ui.problem?.toString() ?: t.error
            Text(
              problem ?: displayArtist(s.track?.artist.orEmpty()), style = GridText.Small, color = if (problem != null) cs.primary else PillText.secondary(cs),
              maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("compact_artist"),
            )
          }
        }
      }
      IconButton(hooks::prev, Modifier.size(Grid.u48).testTag("compact_prev"), enabled = on) { Icon(Icons.Filled.SkipPrevious, tr(Res.string.player_screen_previous), tint = ctl) }
      IconButton(hooks::toggle, Modifier.size(Grid.u48).testTag("compact_play"), enabled = on) { PlayGlyph(t.playing, t.loading, cs.primary) }
      IconButton(hooks::next, Modifier.size(Grid.u48).testTag("compact_next"), enabled = on) { Icon(Icons.Filled.SkipNext, tr(Res.string.player_screen_next), tint = ctl) }
    }
    Row(
      Modifier.fillMaxWidth().padding(start = Grid.u24, end = Grid.u24, top = 0.dp).height(Grid.u24),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u12),
    ) {
      PositionSeekRow(hooks, Modifier.weight(1f))
      ThinProgress(t.volume, true, hooks::setVolume, Modifier.width(Grid.u96).testTag("compact_volume"))
    }
  }
  }
}

/** The registration the shell fills [com.example.windows.shell.Panels.corner] with. */
internal val CompactPlayerPanel = PanelContent { s -> CompactPlayer(s) }
