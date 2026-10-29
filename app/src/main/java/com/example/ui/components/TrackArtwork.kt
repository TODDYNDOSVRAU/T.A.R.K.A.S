package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Track

/**
 * A track's cover: its artwork or thumbnail when it has one, over a calm placeholder that stays as the
 * fallback when the image is missing or fails. The placeholder is the page's raised fill with a faint
 * wash of the track's own colour, so a list of songs without covers reads as a quiet column of tiles
 * rather than a row of loud swatches. [playing] marks the current track with an equaliser glyph.
 */
@Composable
fun TrackArtwork(
  track: Track,
  shape: Shape,
  iconSize: Dp,
  modifier: Modifier = Modifier,
  playing: Boolean = false
) {
  val colors = MaterialTheme.colorScheme
  val tint = Color(track.colorHex)
  val placeholder = remember(track.colorHex, colors.surfaceContainerHigh) {
    tint.copy(alpha = 0.16f).compositeOver(colors.surfaceContainerHigh)
  }
  Box(
    modifier = modifier
      .clip(shape)
      .background(placeholder),
    contentAlignment = Alignment.Center
  ) {
    val url = track.artworkUrl
    if (url == null) {
      Icon(
        imageVector = if (playing) Icons.Rounded.Equalizer else Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = if (playing) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier.size(iconSize)
      )
    } else {
      Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(iconSize))
      // The cover fades in over the placeholder instead of popping in.
      val context = LocalContext.current
      val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(220).build() }
      AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
      )
      if (playing) {
        // A scrim over the artwork itself, so the glyph stays legible on any cover.
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
        Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
      }
    }
  }
}
