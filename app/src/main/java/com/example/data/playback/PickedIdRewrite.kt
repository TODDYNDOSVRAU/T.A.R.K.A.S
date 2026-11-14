package com.example.data.playback

import org.json.JSONArray
import org.json.JSONObject

/**
 * Picked files changed id (`local_<ms>` to `local_<hash of the uri>`). These are the pure rules for
 * moving what the player stored under an old id to the new one; the stores apply them once.
 */
object PickedIdRewrite {
  /** Marker, in the migrations file, that the old ids were rewritten. */
  const val DONE = "picked_ids_rewritten"

  /**
   * The saved state with every old id (current, queue, origin list) replaced. Anything that cannot be
   * parsed is returned as it was: the reader deals with it, nothing is lost here.
   */
  fun state(raw: String, map: Map<String, String>): String {
    if (map.isEmpty()) return raw
    return try {
      val o = JSONObject(raw)
      o.optJSONObject("current")?.let { rewriteTrack(it, map) }
      for (key in listOf("queue", "context")) o.optJSONArray(key)?.let { rewriteList(it, map) }
      o.toString()
    } catch (_: Exception) {
      raw
    }
  }

  private fun rewriteList(list: JSONArray, map: Map<String, String>) {
    for (i in 0 until list.length()) list.optJSONObject(i)?.let { rewriteTrack(it, map) }
  }

  private fun rewriteTrack(track: JSONObject, map: Map<String, String>) {
    val id = track.optString("id", "")
    map[id]?.let { track.put("id", it) }
  }

  /** Counters of two ids that became one: plays and skips add up, the latest times stay. */
  fun merge(a: TrackStats, b: TrackStats) = TrackStats(
    a.plays + b.plays,
    a.skips + b.skips,
    maxOf(a.lastPlayedMs, b.lastPlayedMs),
    maxOf(a.lastSkippedMs, b.lastSkippedMs)
  )
}
