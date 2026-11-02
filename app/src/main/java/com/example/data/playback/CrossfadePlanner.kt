package com.example.data.playback

import com.example.data.model.Track
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * When two tracks overlap, and how their levels cross. Pure, so the rules are tested.
 *
 * The curve is equal power: the outgoing track at cos(πt/2), the incoming at sin(πt/2), so the summed
 * power of two unrelated songs stays at 1 for the whole transition. (Apple Music's manual crossfade
 * uses an exponential fade-out against a logarithmic fade-in, which dips about 4 dB near a quarter of
 * the way.)
 */
object CrossfadePlanner {

  /** Seconds the user can pick; 0 is off. */
  val CHOICES = 1..12
  const val DEFAULT_SECONDS = 6

  /**
   * Whether [current] crossfades into [next] over [seconds]. Not when:
   * - it is off, or there is no next track, or the same track repeats;
   * - either track is shorter than twice the overlap, or its length is unknown;
   * - [next] follows [current] on the same album (albums play gapless, as recorded);
   * - Tarkas Spatial is on (its processor belongs to one player; the outgoing tail would lose it);
   * - a narration sits between the two, or the sleep timer stops at the end of [current].
   */
  fun eligible(
    current: Track?,
    next: Track?,
    seconds: Int,
    repeatsSameTrack: Boolean,
    spatialOn: Boolean,
    narrationBetween: Boolean,
    sleepAtEndOfTrack: Boolean
  ): Boolean {
    if (seconds <= 0 || current == null || next == null) return false
    if (repeatsSameTrack || current.id == next.id || next.filePath == null) return false
    if (spatialOn || narrationBetween || sleepAtEndOfTrack) return false
    val overlap = seconds * 1000L
    if (current.durationMs < 2 * overlap || next.durationMs < 2 * overlap) return false
    return !AlbumOrder.follows(current, next)
  }

  /** Level of the outgoing track at [t] (0 → 1 through the transition). */
  fun outGain(t: Float): Float = cos(t.coerceIn(0f, 1f) * PI / 2).toFloat()

  /** Level of the incoming track at [t]. */
  fun inGain(t: Float): Float = sin(t.coerceIn(0f, 1f) * PI / 2).toFloat()
}
