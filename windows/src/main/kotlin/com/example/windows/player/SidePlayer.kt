package com.example.windows.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.biblioteca.displayArtist
import com.example.windows.res.Res
import com.example.windows.res.player_screen_next
import com.example.windows.res.player_screen_nothing_playing
import com.example.windows.res.player_screen_previous
import com.example.windows.res.tr
import com.example.windows.shell.PanelContent
import com.example.windows.state.TarkasState

/** Numbers of the side player, apart from the UI so they can be tested. */
object SidePlayerMath {
  /** dp of everything that is not the cover: 12 inset above and below, 12 gap, 48 title and artist, 12, 24 line, 18 times, 6, 48 controls. */
  const val FixedDp = 192
  /** Below this the cover is dropped: a picture smaller than a touch target says nothing. */
  const val MinCoverDp = 48

  /** The widest square that fits the column (inset on both sides) and what is left of the height; 0 when it would be below [MinCoverDp]. */
  fun coverDp(widthDp: Float, heightDp: Float): Int {
    val c = minOf(widthDp - 2 * 12, heightDp - FixedDp).toInt()
    return if (c < MinCoverDp) 0 else c
  }
}

/** The fixed player at the left of the Home: real cover, title and artist, the seek line with its times, and previous, play and next. */
@Composable
fun SidePlayer(state: TarkasState, modifier: Modifier = Modifier) {
  val hooks = PlayerHost.hooks(state)
  val assets = PlayerHost.assets(state)
  PlayerShellWiring(hooks, assets)
  SidePlayerBody(hooks, assets, modifier)
}

/** The content without the shell wiring, so it can be tested with plain hooks. */
@Composable
internal fun SidePlayerBody(hooks: PlayerHooks, assets: TrackAssets, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  // No position here: the clock is read in PositionSeekLine only.
  val t by hooks.steady.collectAsState()
  val ui by hooks.controller.ui.collectAsState()
  val art by assets.art.collectAsState()
  val dir = trackDirection(ui, t.track?.id)
  val src = remember { MutableInteractionSource() }
  BoxWithConstraints(modifier.fillMaxSize().testTag("side_player")) {
    val cover = SidePlayerMath.coverDp(maxWidth.value, maxHeight.value)
    val coverDp: Dp = cover.dp
    Column(
      Modifier.fillMaxSize().padding(Grid.inset),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      TrackSwap(
        Shown(t.track, art), dir,
        Modifier.fillMaxWidth().height(if (cover > 0) coverDp + Grid.u12 + Grid.u48 else Grid.u48),
      ) { sh ->
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Grid.u12), horizontalAlignment = Alignment.CenterHorizontally) {
          if (cover > 0) {
            Box(
              Modifier.testTag("side_cover").size(coverDp).clip(Grid.r12).hoverable(src)
                .pointerHoverIcon(if (sh.track != null) PointerIcon.Hand else PointerIcon.Default)
                .clickable(enabled = sh.track != null) { ExpandedPlayer.show() },
            ) {
              Cover(sh.track, sh.track?.let { Color(it.colorHex or 0xFF000000) } ?: cs.primary, sh.art, coverDp)
            }
          }
          Column(Modifier.fillMaxWidth().height(Grid.u48)) {
            Text(
              sh.track?.title ?: tr(Res.string.player_screen_nothing_playing),
              style = GridText.BodyStrong, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("side_title"),
            )
            // A playback problem takes the artist's line: the fixed height never changes.
            val problem = ui.problem?.toString() ?: t.error
            Text(
              problem ?: displayArtist(sh.track?.artist.orEmpty()),
              style = GridText.Small, color = if (problem != null) cs.primary else cs.onSurfaceVariant,
              maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("side_artist"),
            )
          }
        }
      }
      Spacer(Modifier.height(Grid.u12))
      PositionSeekLine(hooks)
      Spacer(Modifier.height(Grid.u6))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(hooks::prev, Modifier.size(Grid.u48).testTag("side_prev"), enabled = t.track != null) {
          Icon(Icons.Filled.SkipPrevious, tr(Res.string.player_screen_previous), tint = cs.onSurface)
        }
        IconButton(hooks::toggle, Modifier.size(Grid.u48).testTag("side_play"), enabled = t.track != null) {
          PlayGlyph(t.playing, t.loading, cs.primary)
        }
        IconButton(hooks::next, Modifier.size(Grid.u48).testTag("side_next"), enabled = t.track != null) {
          Icon(Icons.Filled.SkipNext, tr(Res.string.player_screen_next), tint = cs.onSurface)
        }
      }
    }
  }
}

/** The registration the shell fills [com.example.windows.shell.Panels.sidePlayer] with. */
internal val SidePlayerPanel = PanelContent { s -> SidePlayer(s) }
