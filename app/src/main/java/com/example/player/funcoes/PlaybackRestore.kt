package com.example.data.playback

import com.example.data.model.Track
import com.example.data.model.TrackJson
import org.json.JSONArray
import org.json.JSONObject

/** What was saved of the playback state, read back: the track, its position, the queue and the list it came from. */
data class SavedPlayback(
  val track: Track,
  val queue: List<Track>,
  val context: List<Track>?,
  val positionMs: Long,
  /** What could not be read and was left out; no titles or paths, only what and how many. */
  val problems: List<String>
)

/** The result of reading the saved state. */
sealed interface RestoreOutcome {
  /** Nothing saved, a demo tone or a track from a removed integration: nothing to put back, nothing wrong. */
  data object Nothing : RestoreOutcome

  /** The state was read; parts that were unreadable are listed in [SavedPlayback.problems]. */
  data class Restored(val state: SavedPlayback) : RestoreOutcome

  /** The current track cannot be read, so there is nothing to keep: the saved state is cleared. */
  data class Unreadable(val reason: String) : RestoreOutcome
}

/**
 * Reads the saved playback state without touching the player, so each case is testable.
 * Only a current track that cannot be read makes the whole state unreadable; a broken queue,
 * a broken list or a bad item inside them is dropped on its own and the rest is kept.
 */
object PlaybackRestore {

  /**
   * [positionOwner] is the track id the stored position belongs to (null in saves from before it was kept)
   * and [storedPositionMs] the position stored beside the state (null when it is missing or unreadable).
   */
  fun read(raw: String?, positionOwner: String?, storedPositionMs: Long?): RestoreOutcome {
    if (raw == null) return RestoreOutcome.Nothing
    val o = try {
      JSONObject(raw)
    } catch (e: Exception) {
      return RestoreOutcome.Unreadable("saved state is not valid JSON (${e.javaClass.simpleName})")
    }
    val current = o.optJSONObject("current")
      ?: return RestoreOutcome.Unreadable("saved state has no current track")
    val track = try {
      TrackJson.fromJson(current)
    } catch (e: Exception) {
      return RestoreOutcome.Unreadable("current track is missing a field (${e.javaClass.simpleName})")
    } ?: return RestoreOutcome.Nothing
    // The demo tones are generated at first launch and are not worth a restore.
    if (track.isDemo) return RestoreOutcome.Nothing

    val problems = mutableListOf<String>()
    val queue = readList(o, "queue", problems) ?: emptyList()
    val context = readList(o, "context", problems)
    // Older saves kept the position inside the JSON; saves before the owner was kept are trusted as they are.
    val position = if (positionOwner == null || positionOwner == track.id) {
      storedPositionMs ?: o.optLong("pos", 0L)
    } else 0L
    return RestoreOutcome.Restored(SavedPlayback(track, queue, context, position.coerceAtLeast(0L), problems))
  }

  /** Null when the key is absent or not a list (the latter is a problem); bad items are dropped and counted. */
  private fun readList(o: JSONObject, key: String, problems: MutableList<String>): List<Track>? {
    if (!o.has(key) || o.isNull(key)) return null
    val array: JSONArray = o.optJSONArray(key) ?: run {
      problems += "$key is not a list"
      return null
    }
    val tracks = TrackJson.listFromJson(array)
    val dropped = array.length() - tracks.size
    if (dropped > 0) problems += "$key: $dropped of ${array.length()} items unreadable"
    return tracks
  }
}
