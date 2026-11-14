package com.example.data.audio.dsp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the output stage of the equalizer did in the last moments (limiter reduction, clipped samples), for
 * the screen. The engine publishes about ten times a second while music plays; the screen only reads.
 *
 * A short event would flash by too fast to read, so what was seen stays for [HOLD_MS] after it ends.
 */
object EqActivity {
  /**
   * PROVISIONAL display hold (not a product target): long enough to read a word, short enough that the
   * indicator does not claim the limiter is still working after it stopped.
   */
  const val HOLD_MS = 600L

  private val _state = MutableStateFlow(LimiterActivity())
  val state: StateFlow<LimiterActivity> = _state.asStateFlow()

  private var heldUntilMs = 0L
  private var held = LimiterActivity()

  @Synchronized
  fun publish(activity: LimiterActivity, nowMs: Long = System.currentTimeMillis()) {
    if (activity.limiting || activity.clipped) {
      // Keep the worst of what was seen while the hold lasts, so a deep dip is not replaced by a shallow one.
      held = if (nowMs < heldUntilMs) {
        LimiterActivity(maxOf(held.reductionDb, activity.reductionDb), held.clippedSamples + activity.clippedSamples)
      } else activity
      heldUntilMs = nowMs + HOLD_MS
      _state.value = held
    } else if (nowMs >= heldUntilMs) {
      held = LimiterActivity()
      _state.value = held
    }
  }

  /** Forgets everything, e.g. when playback stops. */
  @Synchronized
  fun clear() {
    held = LimiterActivity()
    heldUntilMs = 0L
    _state.value = held
  }
}
