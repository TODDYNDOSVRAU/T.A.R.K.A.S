package com.example

import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.playback.CoverNeighbors
import com.example.data.playback.SessionButtons
import com.example.data.playback.SessionQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionQueueTest {

  private fun t(id: String) = Track(id, "T$id", "A", "B", 1000L, "g", "e")

  private fun neighbors(current: Track, before: List<Track> = emptyList(), after: List<Track> = emptyList()) =
    CoverNeighbors.Result(current = current, before = before, after = after)

  @Test fun realNeighboursAroundTheCurrentTrack() {
    val cur = t("c")
    // Nearest first on each side: before = [b1, b2] means b1 is what Previous plays.
    val plan = SessionQueue.plan(cur, neighbors(cur, listOf(t("b1"), t("b2")), listOf(t("a1"), t("a2"))), true, true)
    assertEquals(listOf("b2", "b1", "c", "a1", "a2"), plan.items.map { it.track?.id })
    assertEquals(2, plan.currentIndex)
  }

  @Test fun uidsAreUniqueEvenWhenATrackRepeats() {
    val cur = t("c")
    val plan = SessionQueue.plan(cur, neighbors(cur, listOf(t("x")), listOf(t("x"), t("c"))), true, true)
    assertEquals(plan.items.size, plan.items.map { it.uid }.toSet().size)
  }

  @Test fun placeholderOnlyWhereTheTrackIsUnknown() {
    val cur = t("c")
    val plan = SessionQueue.plan(cur, neighbors(cur), hasPrevious = true, hasNext = true)
    assertEquals(listOf(null, "c", null), plan.items.map { it.track?.id })
    assertEquals(SessionQueue.PREVIOUS_UID, plan.items.first().uid)
    assertEquals(SessionQueue.NEXT_UID, plan.items.last().uid)
    assertEquals(1, plan.currentIndex)
  }

  @Test fun noPlaceholderWhenNothingIsPossible() {
    val cur = t("c")
    val plan = SessionQueue.plan(cur, neighbors(cur), hasPrevious = false, hasNext = false)
    assertEquals(listOf("c"), plan.items.map { it.track?.id })
    assertEquals(0, plan.currentIndex)
  }

  @Test fun staleNeighboursAreIgnored() {
    val cur = t("c")
    val stale = neighbors(t("old"), listOf(t("b1")), listOf(t("a1")))
    val plan = SessionQueue.plan(cur, stale, hasPrevious = false, hasNext = true)
    assertEquals(listOf("c", null), plan.items.map { it.track?.id })
  }

  @Test fun moveFromIndex() {
    assertEquals(SessionQueue.Move.Stay, SessionQueue.move(2, 2))
    assertEquals(SessionQueue.Move.Forward(3), SessionQueue.move(2, 5))
    assertEquals(SessionQueue.Move.Back(2), SessionQueue.move(2, 0))
  }

  @Test fun queueJumpOnlyWhenTheNextTracksAreTheQueue() {
    val after = listOf(t("q1"), t("q2"), t("l1"))
    val queue = listOf(t("q1"), t("q2"))
    assertEquals(0, SessionQueue.queueIndexFor(1, after, queue))
    assertEquals(1, SessionQueue.queueIndexFor(2, after, queue))
    assertNull(SessionQueue.queueIndexFor(3, after, queue))
    assertNull(SessionQueue.queueIndexFor(1, listOf(t("l1")), queue))
    assertNull(SessionQueue.queueIndexFor(0, after, queue))
  }

  @Test fun buttonsFollowTheState() {
    assertTrue(SessionButtons.layout(false, false, RepeatMode.OFF).isEmpty())
    val on = SessionButtons.layout(true, true, RepeatMode.ONE)
    val off = SessionButtons.layout(true, false, RepeatMode.OFF)
    assertEquals(listOf(SessionButtons.FAVORITE, SessionButtons.REPEAT), on.map { it.action })
    assertTrue(on[0].iconRes != off[0].iconRes)
    assertTrue(on[0].labelRes != off[0].labelRes)
    assertEquals(3, RepeatMode.entries.map { SessionButtons.layout(true, false, it)[1].iconRes }.toSet().size)
  }
}
