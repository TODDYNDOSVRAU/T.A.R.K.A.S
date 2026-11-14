package com.example.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades the top [height] of a scrolling list to transparent while it is scrolled away from its start, so
 * rows leave under the header softly instead of being cut in a straight line. At the very top nothing
 * is faded, so the first row keeps its full strength.
 */
fun Modifier.topScrollFade(state: LazyListState, height: Dp = 20.dp): Modifier = this
  .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
  .drawWithContent {
    drawContent()
    if (state.canScrollBackward) {
      val h = height.toPx().coerceAtMost(size.height)
      drawRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = h),
        size = androidx.compose.ui.geometry.Size(size.width, h),
        blendMode = BlendMode.DstIn
      )
    }
  }
