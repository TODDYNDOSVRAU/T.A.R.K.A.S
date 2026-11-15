package com.example.data.playback

/**
 * Why the current track is not playing, kept on the player screen until it plays again: the track that
 * failed and whether the cause was the connection (retrying can help) or the file itself.
 */
data class PlaybackProblem(val trackId: String, val network: Boolean)

/** What to do when a track fails to start. Pure, so the rule is tested without an audio engine. */
object PlaybackFailure {
  sealed interface Outcome {
    /** Say so briefly and go on to the next track. */
    data object Skip : Outcome

    /** Stop on this track and show [problem] on the player until the user retries or picks another. */
    data class Stop(val problem: PlaybackProblem) : Outcome
  }

  fun decide(trackId: String, network: Boolean, failuresInARow: Int, canAdvance: Boolean): Outcome =
    if (PlaybackOrder.skipAfterFailure(network, failuresInARow, canAdvance)) Outcome.Skip
    else Outcome.Stop(PlaybackProblem(trackId, network))
}

/** The sleep timer's arithmetic, apart from the coroutine that waits. */
object SleepTimerPlan {
  fun endsAt(nowMs: Long, minutes: Int): Long = nowMs + minutes.coerceAtLeast(0) * 60_000L

  /** Time left, never negative. */
  fun remainingMs(endsAtMs: Long, nowMs: Long): Long = (endsAtMs - nowMs).coerceAtLeast(0L)
}
