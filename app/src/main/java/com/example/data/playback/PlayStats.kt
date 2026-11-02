package com.example.data.playback

import android.content.Context
import android.content.SharedPreferences

/** Why a track stopped playing. */
enum class EndReason {
  /** It played to its end (or the player moved on by itself, gapless). */
  NATURAL,
  /** Next was pressed. */
  SKIP_FORWARD,
  /** Previous was pressed and another track started. */
  SKIP_BACKWARD,
  /** The user picked another track. */
  NEW_SELECTION,
  /** Playback stopped (sleep timer, app closed). */
  STOPPED
}

/** A track's counters. */
data class TrackStats(
  val plays: Int = 0,
  val skips: Int = 0,
  /** When it last counted as played / skipped (ms since 1970), 0 for never. */
  val lastPlayedMs: Long = 0L,
  val lastSkippedMs: Long = 0L
)

/**
 * What a track's end counts as. Apple Music keeps the reason a track ended and decides from it; these
 * are the rules here, in one place so they are tested:
 * - a play: it reached its end, or was heard for half its length or 4 minutes, whichever comes first;
 * - a skip: Next or another pick within its first 30 seconds, after at least 2 seconds (a quick
 *   double tap through the list is not a judgement on the track).
 * A track can count as neither (stopped after a minute), never as both.
 */
object PlayRules {
  const val PLAY_MAX_MS = 4 * 60_000L
  const val SKIP_WINDOW_MS = 30_000L
  const val SKIP_MIN_MS = 2_000L

  fun countsAsPlay(reason: EndReason, playedMs: Long, durationMs: Long): Boolean =
    reason == EndReason.NATURAL ||
      (durationMs > 0 && playedMs >= minOf(durationMs / 2, PLAY_MAX_MS))

  fun countsAsSkip(reason: EndReason, playedMs: Long, durationMs: Long): Boolean =
    (reason == EndReason.SKIP_FORWARD || reason == EndReason.NEW_SELECTION) &&
      playedMs in SKIP_MIN_MS until SKIP_WINDOW_MS &&
      !countsAsPlay(reason, playedMs, durationMs)

  /** The counters after an end. */
  fun apply(stats: TrackStats, reason: EndReason, playedMs: Long, durationMs: Long, nowMs: Long): TrackStats = when {
    countsAsPlay(reason, playedMs, durationMs) -> stats.copy(plays = stats.plays + 1, lastPlayedMs = nowMs)
    countsAsSkip(reason, playedMs, durationMs) -> stats.copy(skips = stats.skips + 1, lastSkippedMs = nowMs)
    else -> stats
  }

  /** "plays|skips|lastPlayed|lastSkipped"; null for anything unreadable. */
  fun encode(s: TrackStats): String = "${s.plays}|${s.skips}|${s.lastPlayedMs}|${s.lastSkippedMs}"

  fun decode(text: String?): TrackStats? {
    val p = text?.split('|') ?: return null
    if (p.size != 4) return null
    return TrackStats(
      p[0].toIntOrNull() ?: return null,
      p[1].toIntOrNull() ?: return null,
      p[2].toLongOrNull() ?: return null,
      p[3].toLongOrNull() ?: return null
    )
  }
}

/** The counters of every track, on this device only, in a preferences file of their own. */
class PlayStatsStore(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

  fun get(trackId: String): TrackStats = PlayRules.decode(prefs.getString(trackId, null)) ?: TrackStats()

  /** Every track's plays, for sorting ("Mais tocadas"). */
  fun allPlays(): Map<String, Int> =
    prefs.all.mapNotNull { (k, v) -> PlayRules.decode(v as? String)?.let { k to it.plays } }.toMap()

  /** Records how [trackId] ended; returns its counters after it. */
  fun record(trackId: String, reason: EndReason, playedMs: Long, durationMs: Long, nowMs: Long = System.currentTimeMillis()): TrackStats {
    val before = get(trackId)
    val after = PlayRules.apply(before, reason, playedMs, durationMs, nowMs)
    if (after != before) prefs.edit().putString(trackId, PlayRules.encode(after)).apply()
    return after
  }

  companion object {
    const val FILE = "tarkas_stats"
  }
}
