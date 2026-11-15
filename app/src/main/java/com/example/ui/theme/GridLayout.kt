package com.example.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Rounds this element's height up to the next multiple of [step] (never under [min]) and centres the
 * content in it. A block whose content is text (a row with a wrapped subtitle, a header, a note) then
 * always ends on the grid's 12 dp rhythm, whatever the text length or the system font size, so the
 * block under it starts on the rhythm too.
 */
fun Modifier.gridHeight(min: Dp = 0.dp, step: Dp = Grid.u12): Modifier = layout { measurable, constraints ->
  val placeable = measurable.measure(constraints.copy(minHeight = 0))
  val stepPx = step.toPx()
  val snapped = (ceil(placeable.height / stepPx - 0.001f) * stepPx).roundToInt()
  val height = maxOf(snapped, min.roundToPx()).coerceIn(constraints.minHeight, constraints.maxHeight)
  layout(placeable.width, height) {
    placeable.placeRelative(0, (height - placeable.height) / 2)
  }
}
