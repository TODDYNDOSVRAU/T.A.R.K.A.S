package com.example.data.playback

import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.data.db.PlayStatDao
import com.example.data.db.PlayStatEntity
import com.example.data.diagnostics.Diagnostics
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

/**
 * The counters of every track, on this device only, in the database. Reads and writes run off the main
 * thread; a record is read, changed and written under one lock, so two ends in a row never lose a count.
 * The old preferences file ("plays|skips|lastPlayed|lastSkipped" per track) is copied once by [migrateFrom]
 * and then left as it was.
 */
class PlayStatsStore(
  private val dao: PlayStatDao,
  private val io: CoroutineDispatcher = Dispatchers.IO
) {
  private val lock = Mutex()

  suspend fun get(trackId: String): TrackStats = withContext(io) {
    dao.get(trackId)?.toStats() ?: TrackStats()
  }

  /** Every track's plays, for sorting ("Mais tocadas"). */
  suspend fun allPlays(): Map<String, Int> = withContext(io) { dao.all().associate { it.trackId to it.plays } }

  /** Every track's counters, as stored. */
  suspend fun all(): Map<String, TrackStats> = withContext(io) { dao.all().associate { it.trackId to it.toStats() } }

  /** Records how [trackId] ended; returns its counters after it. */
  suspend fun record(trackId: String, reason: EndReason, playedMs: Long, durationMs: Long, nowMs: Long = System.currentTimeMillis()): TrackStats =
    lock.withLock {
      withContext(io) {
        val before = dao.get(trackId)?.toStats() ?: TrackStats()
        val after = PlayRules.apply(before, reason, playedMs, durationMs, nowMs)
        if (after != before) dao.upsert(after.toEntity(trackId))
        after
      }
    }

  /**
   * Copies the old file into the database in one transaction, once. A malformed entry is skipped and
   * counted; the others are kept. The copy is checked (same count, same values) before it is marked
   * done, and the old file is only read. A track already in the database keeps its own counters.
   */
  suspend fun migrateFrom(old: SharedPreferences, done: SharedPreferences, db: RoomDatabase) = lock.withLock {
    withContext(io) {
      if (done.getBoolean(MIGRATED, false)) return@withContext
      val parsed = LinkedHashMap<String, TrackStats>()
      var malformed = 0
      for ((key, value) in old.all) {
        val stats = PlayRules.decode(value as? String)
        if (stats == null) malformed++ else parsed[key] = stats
      }
      val existing = dao.all().map { it.trackId }.toSet()
      val toCopy = parsed.filterKeys { it !in existing }
      db.withTransaction { dao.upsertAll(toCopy.map { (id, st) -> st.toEntity(id) }) }
      val stored = dao.all().associate { it.trackId to it.toStats() }
      if (toCopy.any { (id, st) -> stored[id] != st }) {
        Diagnostics.record("playback.migrate", "Play statistics were not copied correctly; will try again")
        return@withContext
      }
      if (malformed > 0) Diagnostics.record("playback.migrate", "Play statistics: $malformed unreadable entries were skipped")
      done.edit().putBoolean(MIGRATED, true).apply()
    }
  }

  /**
   * Moves the counters stored under an old picked-file id to the new id, once. When the new id already
   * has counters the two are added up. Rows are written in one transaction and checked before the marker.
   */
  suspend fun remapIds(map: Map<String, String>, done: SharedPreferences, db: RoomDatabase) = lock.withLock {
    withContext(io) {
      if (done.getBoolean(PickedIdRewrite.DONE + ".stats", false)) return@withContext
      if (map.isNotEmpty()) {
        db.withTransaction {
          val rows = dao.all().associate { it.trackId to it.toStats() }.toMutableMap()
          for ((oldId, newId) in map) {
            if (oldId == newId) continue
            val old = rows.remove(oldId) ?: continue
            rows[newId] = rows[newId]?.let { PickedIdRewrite.merge(it, old) } ?: old
          }
          // The table is written back whole, in the same transaction, so nothing is half moved.
          dao.deleteAll()
          dao.upsertAll(rows.map { (id, st) -> st.toEntity(id) })
        }
        if (map.any { (oldId, newId) -> oldId != newId && dao.get(oldId) != null }) {
          Diagnostics.record("playback.migrate", "Play statistics: old ids were not rewritten; will try again")
          return@withContext
        }
      }
      done.edit().putBoolean(PickedIdRewrite.DONE + ".stats", true).apply()
    }
  }

  private fun PlayStatEntity.toStats() = TrackStats(plays, skips, lastPlayed, lastSkipped)
  private fun TrackStats.toEntity(trackId: String) = PlayStatEntity(trackId, plays, skips, lastPlayedMs, lastSkippedMs)

  companion object {
    /** The old preferences file, kept as it was. */
    const val FILE = "tarkas_stats"
    const val MIGRATED = "play_stats_in_database"
  }
}
