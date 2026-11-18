package com.example.data.playback

import com.example.data.model.RepeatMode
import com.example.data.model.Track

/**
 * The covers beside the current one in the full player: on the left the tracks Previous goes back to,
 * on the right the tracks Next goes to, nearest first. Pure, and built on the same rules as the buttons
 * ([PlaybackOrder.previous] and [PlaybackOrder.resolveNext] with `auto = false`), pressed again and
 * again, so the carousel never shows a track the buttons would not play.
 */
object CoverNeighbors {

  /** How many tracks are worked out on each side: two are drawn, the third only has its cover loaded. */
  const val Depth = 3

  /**
   * @param before the tracks Previous goes back to, nearest first (empty when there is none)
   * @param after the tracks Next goes to, nearest first (empty when there is none or it is not known)
   * @param nextUnknown true when Next does go somewhere but the track is drawn at random when pressed
   *   (shuffle at the end of a pass): the swipe still works, with no cover shown on the right
   */
  data class Result(
    val current: Track? = null,
    val before: List<Track> = emptyList(),
    val after: List<Track> = emptyList(),
    val nextUnknown: Boolean = false
  ) {
    /** The track on the left, or null when there is none to go back to. */
    val previous: Track? get() = before.firstOrNull()
    /** The track on the right, or null when there is none or it is not known yet. */
    val next: Track? get() = after.firstOrNull()
    val canGoPrevious: Boolean get() = previous != null
    val canGoNext: Boolean get() = next != null || nextUnknown
  }

  /**
   * @param queue the "A seguir" queue, played before the list
   * @param list the playback context (the library, or the playlist being played)
   * @param history the tracks played before, most recent last
   * @param shuffleOrder the shuffled order in use (track ids), already fitted with
   *   [PlaybackOrder.shuffleOrderFor]
   * @param depth how many tracks to work out on each side
   */
  fun of(
    current: Track?,
    queue: List<Track>,
    list: List<Track>,
    history: List<Track>,
    shuffle: Boolean,
    shuffleOrder: List<String>,
    repeat: RepeatMode,
    depth: Int = Depth
  ): Result {
    if (current == null) return Result()
    val before = previousChain(current, list, history, depth)
    var unknown = false
    val after = ArrayList<Track>(depth)
    var at: Track = current
    var q = queue
    while (after.size < depth) {
      val fromQueue = q.isNotEmpty()
      val step: Track? = when {
        fromQueue -> q.first().also { q = q.drop(1) }
        list.isEmpty() -> null
        shuffle && !shuffleIsPredictable(at, list, shuffleOrder) -> {
          // Next draws a new order: something plays, but which track is not decided yet. A list of
          // only this track just restarts it. Further out nothing is known either.
          if (after.isEmpty()) unknown = list.any { it.id != current.id }
          null
        }
        else -> PlaybackOrder.resolveNext(
          current = at, queue = emptyList(), list = list,
          shuffle = shuffle, repeat = repeat, auto = false, shuffleOrder = shuffleOrder
        ).track
      }
      // The list coming round to the current track again (or Next only restarting it) ends the row:
      // the queue, though, may really hold this song again.
      if (step == null || (!fromQueue && step.id == current.id)) break
      after += step
      at = step
    }
    return Result(current, before, after, nextUnknown = unknown)
  }

  /**
   * Previous pressed again and again: the history from the most recent back, then the list before the
   * oldest track reached, wrapping to the end. Stops where the list would come round to [current].
   */
  private fun previousChain(current: Track, list: List<Track>, history: List<Track>, depth: Int): List<Track> {
    val out = ArrayList<Track>(depth)
    val h = history.toMutableList()
    var at: Track = current
    while (out.size < depth) {
      val fromHistory = h.isNotEmpty()
      val prev = PlaybackOrder.previous(h, list, at) ?: break
      if (fromHistory) h.removeAt(h.lastIndex)
      // Previous on a track that is the only one there is restarts it: nothing to show beside it.
      if (out.isEmpty() && prev.id == current.id) break
      if (!fromHistory && prev.id == current.id) break
      out += prev
      at = prev
    }
    return out
  }

  /** With shuffle, the next track is known only while the current pass has a track after this one. */
  private fun shuffleIsPredictable(current: Track, list: List<Track>, order: List<String>): Boolean {
    if (!PlaybackOrder.shuffleOrderFits(list, current, order)) return false
    return order.indexOf(current.id) < order.lastIndex
  }
}
