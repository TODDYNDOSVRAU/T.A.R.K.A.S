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
    val loopSameTrack: Boolean = false
  )

  /**
   * @param list the playback context: the whole library, or the playlist/favorites being played
   * @param auto true when the track ended by itself, false when the user pressed Next
   */
  fun resolveNext(
    current: Track?,
    queue: List<Track>,
    list: List<Track>,
    shuffle: Boolean,
    repeat: RepeatMode,
    auto: Boolean,
    random: Random = Random.Default
  ): Next {
    // Repeat-one only applies when a track ends by itself; the Next button always moves on.
    if (auto && repeat == RepeatMode.ONE && current != null) {
      return Next(current, queue, loopSameTrack = true)
    }
    if (queue.isNotEmpty()) return Next(queue.first(), queue.drop(1))
    if (list.isEmpty()) return Next(null, queue)

    if (shuffle) {
      val pool = if (list.size > 1) list.filter { it.id != current?.id } else list
      return Next(pool.random(random), queue)
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
