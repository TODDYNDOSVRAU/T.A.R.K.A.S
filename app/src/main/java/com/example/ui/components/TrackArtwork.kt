package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Track

/**
 * A track's cover: its artwork or thumbnail when it has one, over the color gradient that stays
 * as the placeholder (and the fallback when the image fails). [playing] marks the current track.
 */
@Composable
fun TrackArtwork(
  track: Track,
  shape: Shape,
  iconSize: Dp,
  modifier: Modifier = Modifier,
  playing: Boolean = false,
  gradientEndAlpha: Float = 0.6f
) {
  val color = Color(track.colorHex)
  Box(
    modifier = modifier
      .clip(shape)
      .background(Brush.verticalGradient(listOf(color, color.copy(alpha = gradientEndAlpha)))),
    contentAlignment = Alignment.Center
  ) {
    val url = track.artworkUrl
    if (url == null) {
      Icon(
        imageVector = if (playing) Icons.Default.Equalizer else Icons.Default.MusicNote,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(iconSize)
      )
    } else {
      Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
      // The cover fades in over the track's colour instead of popping in.
      val context = LocalContext.current
      val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(220).build() }
      AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
      )
      if (playing) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))
        Icon(Icons.Default.Equalizer, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
      }
    }
  }
}
