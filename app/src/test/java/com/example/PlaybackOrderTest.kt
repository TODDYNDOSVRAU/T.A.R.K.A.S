package com.example

import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.playback.PlaybackOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackOrderTest {

  private fun track(id: String) = Track(
    id = id, title = "Faixa $id", artist = "Artista", album = "Álbum",
    durationMs = 1000L, genre = "g", era = "e"
  )

  private val a = track("a")
  private val b = track("b")
  private val c = track("c")
  private val d = track("d")
  private val library = listOf(a, b, c)

  private fun next(
    current: Track?,
    queue: List<Track> = emptyList(),
    list: List<Track> = library,
    shuffle: Boolean = false,
    repeat: RepeatMode = RepeatMode.OFF,
    auto: Boolean = true,
    random: Random = Random(1)
  ) = PlaybackOrder.resolveNext(current, queue, list, shuffle, repeat, auto, random)

  // --- order without shuffle ---

  @Test
  fun `sequential playback moves to the next track`() {
    assertEquals(b, next(a).track)
    assertEquals(c, next(b).track)
  }

  @Test
  fun `an unknown current track starts at the first one`() {
    assertEquals(a, next(track("zzz")).track)
    assertEquals(a, next(null).track)
  }

  @Test
  fun `an empty list stops playback`() {
    assertNull(next(a, list = emptyList()).track)
  }

  // --- repeat ---

  @Test
  fun `end of list with repeat off stops when the track ends by itself`() {
    assertNull(next(c, repeat = RepeatMode.OFF, auto = true).track)
  }

  @Test
  fun `end of list with repeat off wraps when the user presses Next`() {
    assertEquals(a, next(c, repeat = RepeatMode.OFF, auto = false).track)
  }

  @Test
  fun `repeat all wraps to the first track at the end of the list`() {
    assertEquals(a, next(c, repeat = RepeatMode.ALL, auto = true).track)
  }

  @Test
  fun `repeat one loops the same track when it ends by itself`() {
    val result = next(b, repeat = RepeatMode.ONE, auto = true)
    assertEquals(b, result.track)
    assertTrue(result.loopSameTrack)
  }

  @Test
  fun `repeat one never blocks the Next button`() {
    val result = next(b, repeat = RepeatMode.ONE, auto = false)
    assertEquals(c, result.track)
    assertFalse(result.loopSameTrack)
  }

  @Test
  fun `repeat one leaves the queue untouched while looping`() {
    val result = next(b, queue = listOf(d), repeat = RepeatMode.ONE, auto = true)
    assertEquals(listOf(d), result.remainingQueue)
  }

  @Test
  fun `repeat mode cycles off, all, one, off`() {
    assertEquals(RepeatMode.ALL, PlaybackOrder.nextRepeatMode(RepeatMode.OFF))
    assertEquals(RepeatMode.ONE, PlaybackOrder.nextRepeatMode(RepeatMode.ALL))
    assertEquals(RepeatMode.OFF, PlaybackOrder.nextRepeatMode(RepeatMode.ONE))
  }

  // --- queue ---

  @Test
  fun `the queue plays before the rest of the list and is consumed`() {
    val result = next(b, queue = listOf(d, a))
    assertEquals(d, result.track)
    assertEquals(listOf(a), result.remainingQueue)
  }

  @Test
  fun `the queue plays before shuffle picks`() {
    val result = next(b, queue = listOf(d), shuffle = true)
    assertEquals(d, result.track)
    assertTrue(result.remainingQueue.isEmpty())
  }

  @Test
  fun `an empty list still plays what is in the queue`() {
    assertEquals(d, next(a, queue = listOf(d), list = emptyList()).track)
  }

  @Test
  fun `play next goes first and add to queue goes last`() {
    assertEquals(listOf(c, a, b), PlaybackOrder.enqueueNext(listOf(a, b), c))
    assertEquals(listOf(a, b, c), PlaybackOrder.enqueueLast(listOf(a, b), c))
  }

  @Test
  fun `removing from the queue drops only that position`() {
    assertEquals(listOf(a, c), PlaybackOrder.removeAt(listOf(a, b, c), 1))
    assertEquals(listOf(a, b, c), PlaybackOrder.removeAt(listOf(a, b, c), 9))
  }

  @Test
  fun `jumping in the queue plays that item and drops everything before it`() {
    val jump = PlaybackOrder.jumpTo(listOf(a, b, c, d), 1)
    assertNotNull(jump)
    assertEquals(b, jump!!.track)
    assertEquals(listOf(c, d), jump.remainingQueue)
  }

  @Test
  fun `jumping outside the queue does nothing`() {
    assertNull(PlaybackOrder.jumpTo(listOf(a, b), 2))
    assertNull(PlaybackOrder.jumpTo(listOf(a, b), -1))
    assertNull(PlaybackOrder.jumpTo(emptyList(), 0))
  }

  // --- shuffle ---

  @Test
  fun `shuffle never repeats the current track when there are other tracks`() {
    val random = Random(42)
    repeat(300) {
      val result = next(b, shuffle = true, random = random)
      assertNotEquals(b, result.track)
      assertTrue(result.track in library)
    }
  }

  @Test
  fun `shuffle only picks from the playback context`() {
    val playlist = listOf(a, b)
    val random = Random(7)
    repeat(200) {
      assertTrue(next(a, list = playlist, shuffle = true, random = random).track in playlist)
    }
  }

  @Test
  fun `shuffle with a single track returns that track`() {
    assertEquals(a, next(a, list = listOf(a), shuffle = true).track)
  }

  @Test
  fun `shuffle eventually reaches every other track`() {
    val random = Random(3)
    val seen = mutableSetOf<String>()
    repeat(200) { seen += next(a, shuffle = true, random = random).track!!.id }
    assertEquals(setOf("b", "c"), seen)
  }

  @Test
  fun `the same seed gives the same shuffle`() {
    val first = (0 until 20).map { next(a, shuffle = true, random = Random(99)).track }
    val second = (0 until 20).map { next(a, shuffle = true, random = Random(99)).track }
    assertEquals(first, second)
  }

  // --- history ---

  @Test
  fun `history ignores the same track and keeps only the last entries`() {
    assertEquals(emptyList<Track>(), PlaybackOrder.pushHistory(emptyList(), a, a))
    assertEquals(listOf(a), PlaybackOrder.pushHistory(emptyList(), a, b))

    var history = emptyList<Track>()
    repeat(60) { i -> history = PlaybackOrder.pushHistory(history, track("t$i"), b) }
    assertEquals(50, history.size)
    assertEquals("t59", history.last().id)
    assertEquals("t10", history.first().id)
  }
}
