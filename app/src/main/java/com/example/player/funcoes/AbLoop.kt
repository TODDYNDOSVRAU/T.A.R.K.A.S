package com.example.data.playback

/**
 * A-B Loop markers for repeating a track segment (ideal for auditing EQ adjustments on a passage).
 * Pure value class so transitions and clamping are unit-tested on the JVM.
 */
data class AbLoop(
  val startMs: Long? = null,
  val endMs: Long? = null
) {
  /** True when both A and B are set and define a valid positive span. */
  val isActive: Boolean get() = startMs != null && endMs != null && endMs > startMs

  /** True when point A is set and waiting for point B. */
  val isPendingB: Boolean get() = startMs != null && endMs == null

  /** Advances the 3-step state machine: Off -> Set A -> Set B (Active) -> Off. */
  fun nextStep(positionMs: Long): AbLoop {
    val pos = positionMs.coerceAtLeast(0L)
    return when {
      startMs == null -> AbLoop(startMs = pos, endMs = null)
      endMs == null -> {
        val lo = minOf(startMs, pos)
        val hi = maxOf(startMs, pos)
        if (hi - lo < MIN_SPAN_MS) AbLoop(startMs = lo, endMs = lo + MIN_SPAN_MS)
        else AbLoop(startMs = lo, endMs = hi)
      }
      else -> AbLoop()
    }
  }

  /** True when [positionMs] has reached or passed point B and should jump back to point A. */
  fun shouldWrap(positionMs: Long): Boolean = isActive && positionMs >= endMs!!

  companion object {
    const val MIN_SPAN_MS = 500L
  }
}
