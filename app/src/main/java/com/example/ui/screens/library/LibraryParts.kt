package com.example.ui.screens.library

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
import com.example.data.model.Track
import com.example.ui.components.GroupDivider
import com.example.ui.components.TonalButton
import com.example.ui.theme.Size
import com.example.ui.theme.Space

/*
 * Pieces shared by the track lists of Início and Biblioteca, so a song reads the same in both.
 */

/** Which service a streamed track plays from, said in words ("YouTube Music"); null for local ones. */
internal fun sourceName(track: Track): String? = when {
  track.webSource == "spotify" -> "Spotify"
  track.webSource != null -> "YouTube Music"
  track.id.startsWith("audius_") -> "Audius"
  else -> null
}

/**
 * The line under a track's title. Streamed tracks name their service as a quiet last part of the
 * line ("Artista · YouTube Music") instead of a coloured badge; device tracks show artist and album.
 * [showSource] is off in search results, where the section header already names the service.
 */
internal fun trackSubtitle(track: Track, showSource: Boolean = true): String {
  val source = if (showSource) sourceName(track) else null
  val main = if (source != null || track.isWeb) track.artist.ifBlank { track.album }
  else listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ")
  return listOfNotNull(main.ifBlank { null }, source).joinToString(" · ")
}

/** The hairline between two track rows, inset to where the title starts. */
@Composable
internal fun TrackDivider() {
  GroupDivider(inset = Space.page + Size.rowArtwork + Space.m)
}

/**
 * Moves a trailing button of a [com.example.ui.components.TrackRow] to the edge a row's own "more"
 * button sits at. TrackRow keeps the full page margin when it has no "more" button, so a 48 dp
 * trailing button there would sit 16 dp further in than the ellipsis in the next list.
 */
internal fun Modifier.trailingEdge(): Modifier = offset(x = Space.page - Space.xs)

/**
 * How a list row arrives and moves: the list animation chosen in Ajustes ([stagger]) and the slide when
 * rows above it come and go. Neither runs with reduced motion; the rows then just appear in place.
 */
@Composable
internal fun LazyItemScope.itemMotion(index: Int, stagger: Boolean = true): Modifier {
  if (rememberReducedMotion()) return Modifier
  val placed = Modifier.animateItem()
  return if (stagger) placed.listEntrance(index) else placed
}

/** Play and Shuffle above a list, side by side, as in the platform's music app. */
@Composable
internal fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page, vertical = Space.s),
    horizontalArrangement = Arrangement.spacedBy(Space.m)
  ) {
    TonalButton(
      text = "Tocar",
      icon = Icons.Rounded.PlayArrow,
      onClick = onPlay,
      modifier = Modifier.weight(1f).testTag("library_btn_play")
    )
    TonalButton(
      text = "Aleatório",
      icon = Icons.Rounded.Shuffle,
      onClick = onShuffle,
      modifier = Modifier.weight(1f).testTag("library_btn_shuffle")
    )
  }
}
