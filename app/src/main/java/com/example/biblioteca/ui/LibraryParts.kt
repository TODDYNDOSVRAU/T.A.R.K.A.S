package com.example.ui.screens.library

import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.theme.listEntrance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.Track
import com.example.ui.components.GroupDivider
import com.example.ui.components.TonalButton

/*
 * Pieces shared by the track lists of Início and Biblioteca, so a song reads the same in both.
 */

/**
 * The line under a track's title. Streamed tracks name their service as a quiet last part of the
 * line ("Artista · Jamendo") instead of a coloured badge; device tracks show artist and album.
 * [showSource] is off in search results, where the section header already names the service.
 */
internal fun trackSubtitle(track: Track, showSource: Boolean = true): String {
  val source = if (showSource) track.onlineSource else null
  val main = if (track.onlineSource != null) track.artist.ifBlank { track.album }
  else listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ")
  return listOfNotNull(main.ifBlank { null }, source).joinToString(" · ")
}

/** The hairline between two track rows, inset to where the title starts. */
@Composable
internal fun TrackDivider() {
  GroupDivider(inset = Grid.margin + Grid.u48 + Grid.inset, endInset = Grid.margin)
}

/**
 * Moves a trailing button of a [com.example.ui.components.TrackRow] to the edge a row's own "more"
 * button sits at. TrackRow keeps the full page margin when it has no "more" button, so a 48 dp
 * trailing button there would sit 12 dp further in than the ellipsis in the next list.
 */
internal fun Modifier.trailingEdge(): Modifier = offset(x = Grid.inset)

/**
 * How a list row arrives and moves: the list animation chosen in Ajustes ([stagger]) and the slide when
 * rows above it come and go. Neither runs with reduced motion; the rows then just appear in place.
 */
@Composable
internal fun LazyItemScope.itemMotion(index: Int, stagger: Boolean = true): Modifier {
  if (rememberReducedMotion()) return Modifier
  val placed = Modifier.animateItem(placementSpec = com.example.ui.theme.Motion.gel<androidx.compose.ui.unit.IntOffset>())
  return if (stagger) placed.listEntrance(index) else placed
}

/** Play and Shuffle above a list, side by side, as in the platform's music app. */
@Composable
internal fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Grid.margin, vertical = Grid.u6),
    horizontalArrangement = Arrangement.spacedBy(Grid.inset)
  ) {
    TonalButton(
      text = stringResource(R.string.library_play),
      icon = Icons.Rounded.PlayArrow,
      onClick = onPlay,
      modifier = Modifier.weight(1f).testTag("library_btn_play")
    )
    TonalButton(
      text = stringResource(R.string.library_shuffle),
      icon = Icons.Rounded.Shuffle,
      onClick = onShuffle,
      modifier = Modifier.weight(1f).testTag("library_btn_shuffle")
    )
  }
}
