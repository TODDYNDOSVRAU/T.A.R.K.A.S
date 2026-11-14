package com.example.data.playback

import android.content.SharedPreferences
import com.example.data.db.PlaybackStateDao
import com.example.data.db.PlaybackStateEntity
import com.example.data.diagnostics.Diagnostics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The player's saved state in the database, written by one worker off the main thread. Every
 * operation, reads included, goes through the same queue, so they run in the order they were asked:
 * the last position saved is the one that stays, and a read never sees an older state than a write
 * asked before it.
 */
class PlaybackStateStore(
  private val dao: PlaybackStateDao,
  dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
  private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

  init {
    CoroutineScope(SupervisorJob() + dispatcher).launch {
      for (op in queue) {
        try {
          op()
        } catch (e: Exception) {
          // A write that fails is recorded and the worker goes on with the next one.
          Diagnostics.record("playback.store", "Playback state operation failed", e)
        }
      }
    }
  }

  /** Saves the state with its position and the track the position belongs to. Never blocks. */
  fun save(stateJson: String, positionMs: Long, positionTrackId: String?) {
    queue.trySend { dao.put(PlaybackStateEntity(0, stateJson, positionMs, positionTrackId)) }
  }

  /** Saves only the position; a state that was never saved is left alone. Never blocks. */
  fun savePosition(positionMs: Long, trackId: String?) {
    queue.trySend { dao.putPosition(positionMs, trackId) }
  }

  fun clear() {
    queue.trySend { dao.clear() }
  }

  /** Reads the saved row once everything asked before has been written. */
  suspend fun load(): PlaybackStateEntity? = run { dao.get() }

  /** Waits until everything asked so far has run. */
  suspend fun flush() { run { } }

  /**
   * Copies the state of the old preferences file into the database, once. The old file is only read:
   * it stays as a copy. Done when [done] says so; if the database already has a row it is not
   * replaced, and a copy that cannot be written is tried again at the next start.
   */
  suspend fun migrateFrom(old: SharedPreferences, done: SharedPreferences) {
    run {
      if (done.getBoolean(MIGRATED, false)) return@run
      val raw = try { old.getString(KEY_STATE, null) } catch (_: Exception) { null }
      if (raw != null && dao.get() == null) {
        val owner = try { old.getString(KEY_POSITION_TRACK, null) } catch (_: Exception) { null }
        // -1 means "no position was stored": the reader then falls back to the one inside the JSON.
        val position = try { if (old.contains(KEY_POSITION)) old.getLong(KEY_POSITION, 0L) else -1L } catch (_: Exception) { -1L }
        dao.put(PlaybackStateEntity(0, raw, position, owner))
        if (dao.get()?.stateJson != raw) {
          Diagnostics.record("playback.migrate", "Saved playback could not be copied to the database")
          return@run
        }
      }
      done.edit().putBoolean(MIGRATED, true).apply()
    }
  }

  /** Replaces old picked-file ids in the saved state, once; the marker is set only after the row reads back right. */
  suspend fun remapIds(map: Map<String, String>, done: SharedPreferences) {
    run {
      val flag = PickedIdRewrite.DONE + ".state"
      if (done.getBoolean(flag, false)) return@run
      val row = dao.get()
      if (row != null && map.isNotEmpty()) {
        val json = PickedIdRewrite.state(row.stateJson, map)
        val owner = row.positionTrackId?.let { map[it] ?: it }
        if (json != row.stateJson || owner != row.positionTrackId) {
          dao.put(row.copy(stateJson = json, positionTrackId = owner))
          val back = dao.get()
          if (back?.stateJson != json || back.positionTrackId != owner) {
            Diagnostics.record("playback.migrate", "Saved playback: old ids were not rewritten; will try again")
            return@run
          }
        }
      }
      done.edit().putBoolean(flag, true).apply()
    }
  }

  private suspend fun <T> run(block: suspend () -> T): T {
    val result = CompletableDeferred<T>()
    queue.send {
      try {
        result.complete(block())
      } catch (e: Throwable) {
        result.completeExceptionally(e)
      }
    }
    return result.await()
  }

  companion object {
    const val KEY_STATE = "state"
    const val KEY_POSITION = "pos"
    const val KEY_POSITION_TRACK = "pos_track"
    const val MIGRATED = "playback_state_in_database"
    /** The old preferences file, kept as it was. */
    const val OLD_FILE = "tarkas_playback"
    /** Where "already migrated" is written, apart from the old files so they stay untouched. */
    const val FLAGS_FILE = "tarkas_migrations"
  }
}
