package com.example.data.playback

import com.example.data.model.Track

/**
 * The list the media session shows to notifications, the lock screen, cars and watches: the tracks
 * Previous goes back to, the current one, and the tracks Next goes to. It is built from the same
 * [CoverNeighbors] as the player's carousel, so the session never offers a track the buttons would not
 * play. Pure.
 */
object SessionQueue {

  /** One entry. [track] is null for a placeholder: Previous or Next is possible but the track is not known. */
  data class Item(val uid: String, val track: Track?)

  data class Plan(val items: List<Item>, val currentIndex: Int)

  /** Where a jump to another entry of the list goes. */
  sealed interface Move {
    data object Stay : Move
    data class Forward(val steps: Int) : Move
    data class Back(val steps: Int) : Move
  }

  const val PREVIOUS_UID = "tarkas:previous"
  const val NEXT_UID = "tarkas:next"

  /**
   * [neighbors] counts only when it was worked out for [current]; right after a track change it can
   * still describe the old one, and the session would show the wrong neighbours.
   */
  fun plan(current: Track, neighbors: CoverNeighbors.Result, hasPrevious: Boolean, hasNext: Boolean): Plan {
    val known = neighbors.current?.id == current.id
    val before = if (known) neighbors.before else emptyList()
    val after = if (known) neighbors.after else emptyList()
    val items = ArrayList<Item>(before.size + after.size + 3)
    // Nearest first in the neighbours, so the left side is read backwards.
    before.asReversed().forEachIndexed { i, t -> items += Item("before:${before.size - i}:${t.id}", t) }
    if (before.isEmpty() && hasPrevious) items += Item(PREVIOUS_UID, null)
    val currentIndex = items.size
    items += Item(current.id, current)
    after.forEachIndexed { i, t -> items += Item("after:${i + 1}:${t.id}", t) }
    if (after.isEmpty() && hasNext) items += Item(NEXT_UID, null)
    return Plan(items, currentIndex)
  }

  fun move(currentIndex: Int, target: Int): Move = when {
    target > currentIndex -> Move.Forward(target - currentIndex)
    target < currentIndex -> Move.Back(currentIndex - target)
    else -> Move.Stay
  }

  /**
   * When the next [steps] tracks are exactly the first [steps] of the "A seguir" queue, the index in
   * that queue to jump to (which drops what came before it); null when the list's own order is in the way.
   */
  fun queueIndexFor(steps: Int, after: List<Track>, queue: List<Track>): Int? {
    if (steps < 1 || after.size < steps || queue.size < steps) return null
    for (i in 0 until steps) if (after[i].id != queue[i].id) return null
    return steps - 1
  }
}
