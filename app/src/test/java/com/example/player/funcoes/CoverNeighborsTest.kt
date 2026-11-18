package com.example

import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.playback.CoverNeighbors
import com.example.data.playback.PlaybackOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CoverNeighborsTest {

  private fun track(id: String) = Track(
    id = id, title = "Faixa $id", artist = "Artista", album = "Álbum",
    durationMs = 1000L, genre = "g", era = "e"
  )

  private val a = track("a")
  private val b = track("b")
  private val c = track("c")
  private val d = track("d")
  private val list = listOf(a, b, c)

  private fun of(
    current: Track?,
    queue: List<Track> = emptyList(),
    list: List<Track> = this.list,
    history: List<Track> = emptyList(),
    shuffle: Boolean = false,
    order: List<String> = emptyList(),
    repeat: RepeatMode = RepeatMode.OFF
  ) = CoverNeighbors.of(current, queue, list, history, shuffle, order, repeat)

  /** What the Next button plays from the same state (PlaybackController.advance(auto = false)). */
  private fun nextButton(current: Track, queue: List<Track> = emptyList(), shuffle: Boolean = false, order: List<String> = emptyList(), repeat: RepeatMode = RepeatMode.OFF) =
    PlaybackOrder.resolveNext(current, queue, list, shuffle, repeat, auto = false, shuffleOrder = order).track

  @Test fun noTrackHasNoNeighbours() {
    val r = of(null)
    assertNull(r.previous)
    assertNull(r.next)
    assertFalse(r.canGoNext)
    assertFalse(r.canGoPrevious)
  }

  @Test fun queueComesFirstOnTheRight() {
    val r = of(a, queue = listOf(d, c))
    assertEquals(d, r.next)
    assertEquals(nextButton(a, queue = listOf(d, c)), r.next)
  }

  @Test fun theSameSongQueuedAgainIsShownBesideItself() {
    // The queue really plays it again, so its cover is the next one.
    val r = of(a, queue = listOf(a))
    assertEquals(a, r.next)
  }

  @Test fun listOrderWithoutQueue() {
    assertEquals(b, of(a).next)
    assertEquals(c, of(b).next)
  }

  @Test fun endOfListFollowsTheNextButtonWhichWrapsEvenWithRepeatOff() {
    assertEquals(nextButton(c), of(c, repeat = RepeatMode.OFF).next)
    assertEquals(a, of(c, repeat = RepeatMode.OFF).next)
    assertEquals(a, of(c, repeat = RepeatMode.ALL).next)
  }

  @Test fun repeatOneStillMovesOnWithTheButton() {
    assertEquals(b, of(a, repeat = RepeatMode.ONE).next)
    assertEquals(nextButton(a, repeat = RepeatMode.ONE), of(a, repeat = RepeatMode.ONE).next)
  }

  @Test fun shuffleFollowsTheShuffledOrder() {
    val order = listOf("b", "c", "a")
    assertEquals(c, of(b, shuffle = true, order = order).next)
    assertEquals(a, of(c, shuffle = true, order = order).next)
    assertEquals(nextButton(b, shuffle = true, order = order), of(b, shuffle = true, order = order).next)
  }

  @Test fun shuffleAtTheEndOfAPassIsUnknownButStillGoesOn() {
    // Next draws a new order then: something plays, which one is not decided yet.
    val r = of(a, shuffle = true, order = listOf("b", "c", "a"), repeat = RepeatMode.OFF)
    assertNull(r.next)
    assertTrue(r.nextUnknown)
    assertTrue(r.canGoNext)
    // The button does move on to some other track there.
    val played = PlaybackOrder.resolveNext(a, emptyList(), list, true, RepeatMode.OFF, auto = false, random = Random(3), shuffleOrder = listOf("b", "c", "a")).track
    assertTrue(played != null && played.id != a.id)
  }

  @Test fun shuffleWithTheQueueShowsTheQueue() {
    assertEquals(d, of(a, queue = listOf(d), shuffle = true, order = listOf("b", "c", "a")).next)
  }

  @Test fun previousIsTheLastOfTheHistory() {
    val r = of(a, history = listOf(c, b))
    assertEquals(b, r.previous)
    assertEquals(PlaybackOrder.previous(listOf(c, b), list, a), r.previous)
  }

  @Test fun noHistoryFollowsThePreviousButtonThroughTheList() {
    assertEquals(a, of(b).previous)
    // At the start the button wraps to the end of the list.
    assertEquals(c, of(a).previous)
  }

  @Test fun noListAndNoHistoryHasNothingOnTheLeft() {
    val r = of(d, list = emptyList())
    assertNull(r.previous)
    assertNull(r.next)
    assertFalse(r.canGoNext)
  }

  @Test fun aListOfOnlyThisTrackHasNoNeighbours() {
    // Previous and Next would only restart it.
    val r = of(a, list = listOf(a))
    assertNull(r.previous)
    assertNull(r.next)
    assertFalse(r.nextUnknown)
    val s = of(a, list = listOf(a), shuffle = true, order = listOf("a"))
    assertNull(s.next)
    assertFalse(s.canGoNext)
  }

  @Test fun theSameTrackTwiceInTheHistoryIsKeptAsItIs() {
    // history holds a and b, then a again was played and now c: Previous goes to a.
    val r = of(c, history = listOf(a, b, a))
    assertEquals(a, r.previous)
    assertEquals(listOf(a, b, a), r.before)
  }

  @Test fun previousRuleMatchesTheControllerForEveryPosition() {
    for (cur in list) {
      val i = list.indexOf(cur)
      val expected = list[if (i > 0) i - 1 else list.lastIndex]
      assertEquals(expected, PlaybackOrder.previous(emptyList(), list, cur))
    }
    assertEquals(c, PlaybackOrder.previous(emptyList(), list, d))
    assertNull(PlaybackOrder.previous(emptyList(), emptyList(), a))
  }

  // ---- three on each side ----

  private val e = track("e")
  private val f = track("f")
  private val long = listOf(a, b, c, d, e, f)

  /** Next pressed [n] times from [current], as the controller does (no queue). */
  private fun pressNext(current: Track, n: Int, list: List<Track>, shuffle: Boolean = false, order: List<String> = emptyList(), repeat: RepeatMode = RepeatMode.OFF): List<Track> {
    val out = ArrayList<Track>()
    var at = current
    for (i in 0 until n) {
      val t = PlaybackOrder.resolveNext(at, emptyList(), list, shuffle, repeat, auto = false, shuffleOrder = order).track!!
      out += t
      at = t
    }
    return out
  }

  @Test fun threeOnEachSideInPlayOrder() {
    val r = of(c, list = long)
    assertEquals(listOf(d, e, f), r.after)
    assertEquals(listOf(b, a, f), r.before)
    assertEquals(pressNext(c, 3, long), r.after)
    assertEquals(d, r.next)
    assertEquals(b, r.previous)
  }

  @Test fun theQueueThenTheList() {
    val r = of(a, queue = listOf(e), list = long)
    // e from the queue, then the list goes on after e, as Next would.
    assertEquals(listOf(e, f, a).take(2), r.after.take(2))
    assertEquals(PlaybackOrder.resolveNext(e, emptyList(), long, false, RepeatMode.OFF, auto = false).track, r.after[1])
  }

  @Test fun aLongQueueFillsTheRight() {
    assertEquals(listOf(d, a, c), of(b, queue = listOf(d, a, c, e), list = long).after)
  }

  @Test fun historyThenTheListOnTheLeft() {
    // Previous goes back through the history (most recent first), then the list before the oldest.
    val r = of(f, list = long, history = listOf(c, e))
    assertEquals(listOf(e, c, b), r.before)
  }

  @Test fun emptyHistoryWalksTheListBackwards() {
    assertEquals(listOf(a, f, e), of(b, list = long).before)
  }

  @Test fun theSameTrackTwiceInTheHistoryIsShownTwice() {
    assertEquals(listOf(a, b, a), of(c, history = listOf(a, b, a)).before)
  }

  @Test fun shuffleThreeAheadWhileThePassLasts() {
    val order = listOf("c", "a", "f", "b", "d", "e")
    val r = of(c, list = long, shuffle = true, order = order)
    assertEquals(listOf(a, f, b), r.after)
    assertEquals(pressNext(c, 3, long, shuffle = true, order = order), r.after)
  }

  @Test fun shuffleStopsWhereThePassEnds() {
    val order = listOf("c", "a", "f", "b", "d", "e")
    // From d only e is decided; after e a new pass is drawn.
    val r = of(d, list = long, shuffle = true, order = order)
    assertEquals(listOf(e), r.after)
    assertFalse(r.nextUnknown)
    assertTrue(of(e, list = long, shuffle = true, order = order).let { it.after.isEmpty() && it.nextUnknown })
  }

  @Test fun repeatModesFollowTheButton() {
    for (mode in RepeatMode.values()) {
      val r = of(e, list = long, repeat = mode)
      // The button wraps at the end in every mode, and never repeats the track.
      assertEquals(pressNext(e, 3, long, repeat = mode), r.after)
      assertEquals(listOf(f, a, b), r.after)
    }
  }

  @Test fun aShortListShowsFewerAndNeverTheCurrentTrackFromTheList() {
    // a, b: b on both sides, nothing further (the list would come round to a).
    val r = of(a, list = listOf(a, b))
    assertEquals(listOf(b), r.after)
    assertEquals(listOf(b), r.before)
    val three = of(a)
    assertEquals(listOf(b, c), three.after)
    assertEquals(listOf(c, b), three.before)
  }

  @Test fun emptyHistoryAndEmptyListShowNothing() {
    val r = of(d, list = emptyList())
    assertTrue(r.before.isEmpty())
    assertTrue(r.after.isEmpty())
  }

  @Test fun depthIsRespected() {
    assertEquals(1, CoverNeighbors.of(c, emptyList(), long, emptyList(), false, emptyList(), RepeatMode.OFF, depth = 1).after.size)
    assertEquals(3, of(c, list = long).before.size)
  }

  @Test fun shuffleOrderFitsMatchesShuffleOrderFor() {
    val order = listOf("b", "c", "a")
    assertTrue(PlaybackOrder.shuffleOrderFits(list, a, order))
    assertTrue(PlaybackOrder.shuffleOrderFor(list, a, order) === order)
    assertFalse(PlaybackOrder.shuffleOrderFits(list + d, a, order))
  }
}
