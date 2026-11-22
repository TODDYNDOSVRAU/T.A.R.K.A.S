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
import com.example.ui.theme.Grid

/**
 * Fades the top [height] of a scrolling list to transparent while it is scrolled away from its start.
 * Note: [TarkasLargeTitle] already renders the top gradient overlay directly in [drawBehind], so we
 * avoid wrapping full-screen LazyColumns in `CompositingStrategy.Offscreen` (which forced a second
 * full-screen 2304x1440 hardware buffer allocation and BlendMode.DstIn pass on every scroll frame).
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.topScrollFade(state: LazyListState, height: Dp = Grid.u24): Modifier = this
