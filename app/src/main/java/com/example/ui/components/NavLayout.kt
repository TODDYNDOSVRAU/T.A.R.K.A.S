package com.example.ui.components

import kotlin.math.abs

/**
 * Geometry of the tab bar's "expanding label": the active tab is wider (icon and label inside a pill),
 * the others are icon only. Everything is a pure function of one fractional position, so a single
 * spring drives widths, pill and label together, and any frame in between two tabs is well defined.
 */
object NavLayout {

  /**
   * How much tab [index] is "the active one" at [selectedPosition]: 1 on its own position, falling
   * linearly to 0 one tab away. Between two tabs the two shares always add up to 1.
   */
  fun share(selectedPosition: Float, index: Int, count: Int): Float {
    if (count <= 0) return 0f
    val p = selectedPosition.coerceIn(0f, (count - 1).toFloat())
    return (1f - abs(p - index)).coerceIn(0f, 1f)
  }

  /**
   * Widths of the [count] slots for a bar [total] wide. A tab weighs 1 plus ([activeWeight] - 1)
   * times its [share], so the weights always add up to count - 1 + activeWeight and the widths
   * always add up to [total], at whole and fractional positions alike.
   */
  fun slotWidths(selectedPosition: Float, count: Int, total: Float, activeWeight: Float): FloatArray {
    val out = FloatArray(count.coerceAtLeast(0))
    slotWidths(selectedPosition, count, total, activeWeight, out)
    return out
  }

  /** Same as [slotWidths], written into [out] (no allocation; used on every frame of the animation). */
  fun slotWidths(selectedPosition: Float, count: Int, total: Float, activeWeight: Float, out: FloatArray) {
    if (count <= 0) return
    val a = activeWeight.coerceAtLeast(1f)
    val unit = total / (count - 1 + a)
    for (i in 0 until count) out[i] = unit * (1f + (a - 1f) * share(selectedPosition, i, count))
  }

  /**
   * The active weight that gives the active slot [activeWidth] out of [total] when there are [count]
   * slots (the others share the rest equally). Never below 1 (the active tab is never the narrowest).
   */
  fun weightFor(activeWidth: Float, count: Int, total: Float): Float {
    if (count <= 1) return 1f
    val rest = total - activeWidth
    if (rest <= 0f) return Float.MAX_VALUE
    return (activeWidth * (count - 1) / rest).coerceAtLeast(1f)
  }
}
