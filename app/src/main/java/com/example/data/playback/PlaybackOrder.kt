package com.example.data.playback

import com.example.data.model.RepeatMode
import com.example.data.model.Track
import kotlin.random.Random

/**
 * Pure playback-order rules: queue ("Up Next"), shuffle and repeat.
 * No Android types, so it is unit-testable on the JVM. The ViewModel owns the state and calls this.
 */
object PlaybackOrder {

  /**
   * @param track what to play next, or null when playback should stop
   * @param remainingQueue the queue after consuming an item from it
   * @param loopSameTrack true when the current track should simply restart (repeat-one)
   */
  data class Next(
    val track: Track?,
    val remainingQueue: List<Track>,
    val loopSameTrack: Boolean = false,
    /** With shuffle, the order to keep from now on (a new one once a pass through the list ended). */
    val shuffleOrder: List<String>? = null
  )

  /**
   * @param list the playback context: the whole library, or the playlist/favorites being played
   * @param auto true when the track ended by itself, false when the user pressed Next
   * @param shuffleOrder the shuffled order in use (track ids, see [shuffleOrderFor])
   */
  fun resolveNext(
    current: Track?,
    queue: List<Track>,
    list: List<Track>,
    shuffle: Boolean,
    repeat: RepeatMode,
    auto: Boolean,
    random: Random = Random.Default,
    shuffleOrder: List<String> = emptyList()
  ): Next {
    // Repeat-one only applies when a track ends by itself; the Next button always moves on.
    if (auto && repeat == RepeatMode.ONE && current != null) {
      return Next(current, queue, loopSameTrack = true)
    }
    if (queue.isNotEmpty()) return Next(queue.first(), queue.drop(1))
    if (list.isEmpty()) return Next(null, queue)

    if (shuffle) {
      // Every track once before any repeats, like the list itself but in another order.
      val order = shuffleOrderFor(list, current, shuffleOrder, random)
      val byId = list.associateBy { it.id }
      val i = order.indexOf(current?.id)
      if (i < order.lastIndex) return Next(byId[order[i + 1]], queue, shuffleOrder = order)
      // The pass ended: a new order with the same rule as the end of the list.
      if (repeat != RepeatMode.ALL && auto) return Next(null, queue, shuffleOrder = order)
      val fresh = shuffled(list, null, random).let { o ->
        // The track that just ended does not open the next pass.
        if (o.size > 1 && o.first() == current?.id) o.drop(1) + o.first() else o
      }
      return Next(byId[fresh.first()], queue, shuffleOrder = fresh)
    }

    val nextIndex = list.indexOfFirst { it.id == current?.id } + 1
    val track = when {
      nextIndex < list.size -> list[nextIndex]
      // End of the list: wrap only with repeat-all, or when the user pressed Next.
      repeat == RepeatMode.ALL || !auto -> list.first()
      else -> null
    }
    return Next(track, queue)
  }

  /**
   * [order] when it still fits [list] and holds [current]; otherwise a new shuffled order that starts at
   * [current] (or anywhere when it is not in the list). Turning shuffle off needs nothing: the list
   * itself is the original order, and play goes on from the current track.
   */
  fun shuffleOrderFor(list: List<Track>, current: Track?, order: List<String>, random: Random = Random.Default): List<String> =
    if (shuffleOrderFits(list, current, order)) order else shuffled(list, current?.id, random)

  /** True when [order] still covers exactly [list] and holds [current], so [shuffleOrderFor] keeps it. */
  fun shuffleOrderFits(list: List<Track>, current: Track?, order: List<String>): Boolean =
    order.size == list.size &&
      (current == null || current.id in order || list.none { it.id == current.id }) &&
      order.toSet() == list.mapTo(HashSet()) { it.id }

  /**
   * The track the Previous button goes back to (once it is past the restart threshold): the last one
   * in [history], or else the one before [current] in [list], wrapping to the list's end.
   */
  fun previous(history: List<Track>, list: List<Track>, current: Track?): Track? {
    if (history.isNotEmpty()) return history.last()
    val i = list.indexOfFirst { it.id == current?.id }
    return list.getOrNull(if (i > 0) i - 1 else list.lastIndex)
  }

  /** A uniform permutation of [list]'s ids; [firstId], when in the list, goes first. */
  fun shuffled(list: List<Track>, firstId: String?, random: Random = Random.Default): List<String> {
    val ids = list.map { it.id }.distinct().shuffled(random)
    if (firstId == null || firstId !in ids) return ids
    return listOf(firstId) + ids.filter { it != firstId }
  }

  /**
   * What the list plays after the queue runs out, in order: up to [limit] tracks after [current], in
   * the shuffled order when [shuffle] is on. Wraps to the start only with repeat-all; with repeat-one
   * the current track repeats, so nothing follows.
   */
  fun upcoming(
    current: Track?,
    list: List<Track>,
    shuffle: Boolean,
    shuffleOrder: List<String>,
    repeat: RepeatMode,
    limit: Int
  ): List<Track> {
    if (list.isEmpty() || repeat == RepeatMode.ONE || limit <= 0) return emptyList()
    val ordered: List<Track> = if (shuffle) {
      val byId = list.associateBy { it.id }
      shuffleOrder.mapNotNull { byId[it] }
    } else list
    val i = ordered.indexOfFirst { it.id == current?.id }
    val after = ordered.drop(i + 1)
    val wrapped = if (repeat == RepeatMode.ALL) after + ordered.take(i.coerceAtLeast(0)) else after
    return wrapped.take(limit)
  }

  /**
   * Keys for the queue's rows that survive a move: the track id and which copy of it this is, since the
   * queue may hold the same song twice.
   */
  fun entryKeys(queue: List<Track>): List<String> {
    val seen = HashMap<String, Int>()
    return queue.map { t ->
      val n = seen.getOrDefault(t.id, 0)
      seen[t.id] = n + 1
      "${t.id}#$n"
    }
  }

  /**
   * After a track failed: true to skip to the next one, false to stop and offer a retry. A lost
   * connection never skips, since every next track would fail the same way and the list would run out;
   * a bad file skips, up to [failuresInARow] 3.
   */
  fun skipAfterFailure(network: Boolean, failuresInARow: Int, canAdvance: Boolean): Boolean =
    !network && failuresInARow < 3 && canAdvance

  fun nextRepeatMode(mode: RepeatMode): RepeatMode = when (mode) {
    RepeatMode.OFF -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.OFF
  }

  fun enqueueNext(queue: List<Track>, track: Track): List<Track> = listOf(track) + queue

  fun enqueueLast(queue: List<Track>, track: Track): List<Track> = queue + track

  fun removeAt(queue: List<Track>, index: Int): List<Track> =
    queue.filterIndexed { i, _ -> i != index }

  /** Jumps to the item at [index]: it plays now, and everything before it is dropped. */
  fun jumpTo(queue: List<Track>, index: Int): Next? {
    if (index !in queue.indices) return null
    return Next(queue[index], queue.drop(index + 1))
  }

  /** Adds [previous] to the history unless it is the same track, keeping the last [limit] entries. */
  fun pushHistory(history: List<Track>, previous: Track, newTrack: Track, limit: Int = 50): List<Track> {
    if (previous.id == newTrack.id) return history
    return (history + previous).takeLast(limit)
  }
}
