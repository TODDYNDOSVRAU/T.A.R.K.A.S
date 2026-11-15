package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.theme.rememberReducedMotion
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
      if (playing) {
        PlayingBars(color = colors.primary, size = iconSize)
      } else {
        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(iconSize))
      }
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
        PlayingBars(color = Color.White, size = iconSize)
      }
    }
  }
}

/**
 * Three bars that rise and fall while the track plays. Only the height (a scale) moves, each bar on its
 * own period so they do not march together. With reduced motion they stand still at different heights.
 * It is drawn only while playing, so a paused or off-screen row runs no animation.
 */
@Composable
private fun PlayingBars(color: Color, size: Dp) {
  val reduced = rememberReducedMotion()
  val still = floatArrayOf(0.55f, 1f, 0.7f)
  val periods = intArrayOf(520, 680, 600)
  val transition = rememberInfiniteTransition(label = "playingBars")
  Row(
    modifier = Modifier.size(size * 0.8f),
    horizontalArrangement = Arrangement.spacedBy(size * 0.08f),
    verticalAlignment = Alignment.Bottom
  ) {
    for (i in 0..2) {
      val level = if (reduced) {
        still[i]
      } else {
        transition.animateFloat(
          initialValue = 0.3f,
          targetValue = 1f,
          animationSpec = infiniteRepeatable(tween(periods[i], easing = LinearEasing), RepeatMode.Reverse),
          label = "bar$i"
        ).value
      }
      Box(
        Modifier
          .weight(1f)
          .fillMaxHeight()
          .graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleY = level
          }
          .clip(com.example.ui.theme.Shapes.pill)
          .background(color)
      )
    }
  }
}
