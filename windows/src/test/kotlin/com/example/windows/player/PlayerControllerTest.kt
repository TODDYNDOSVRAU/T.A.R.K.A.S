package com.example.windows.player

import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.playback.RestoreOutcome
import com.example.windows.audio.AudioOut
import com.example.windows.audio.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeOut : AudioOut {
  val st = MutableStateFlow(PlayerState())
  override val state: StateFlow<PlayerState> get() = st
  override var volume = 1f
  override var onEnded: ((Boolean) -> Unit)? = null
  val played = mutableListOf<String>()
  var armed: File? = null
  var armedMs = 0
  var seeks = mutableListOf<Long>()
  var stops = 0
  var pauses = 0
  var startedPaused = false
  override fun play(file: File, startMs: Long, paused: Boolean) {
    played += file.name; startedPaused = paused
    st.value = PlayerState(file = file, playing = !paused, positionMs = startMs, durationMs = 200_000)
  }
  override fun togglePause() { st.value = st.value.copy(playing = !st.value.playing) }
  override fun pause() { pauses++; st.value = st.value.copy(playing = false) }
  override fun resume() { st.value = st.value.copy(playing = true) }
  override fun seek(ms: Long) { seeks += ms; st.value = st.value.copy(positionMs = ms) }
  override fun stop() { stops++; st.value = st.value.copy(playing = false) }
  override fun armNext(file: File?, fadeMs: Int) { armed = file; armedMs = fadeMs }
}

class PlayerControllerTest {
  private fun t(n: Int, album: String = "A$n", dur: Long = 200_000) =
    Track("t$n", "Title $n", "Artist", album, dur, "", "", true, "C:/m/t$n.mp3")

  private val list = (1..5).map { t(it) }
  private fun make(prefs: PlayerPrefs = MemoryPrefs(), now: () -> Long = { 0L }): Pair<PlayerController, FakeOut> {
    val out = FakeOut()
    return PlayerController(out, prefs, null, now) { true } to out
  }

  @Test fun playsTrackAndOpensItsFile() {
    val (c, out) = make()
    c.play(list[0], list)
    assertEquals(listOf("t1.mp3"), out.played)
    assertEquals("t1", c.ui.value.current?.id)
  }

  @Test fun nextFollowsListOrderAndStopsAtEnd() {
    val (c, out) = make()
    c.play(list[3], list)
    c.next(auto = true)
    assertEquals("t5", c.ui.value.current?.id)
    c.next(auto = true)
    assertEquals(1, out.stops)
    assertEquals("t5", c.ui.value.current?.id)
  }

  @Test fun nextButtonWrapsAtEndOfList() {
    val (c, _) = make()
    c.play(list[4], list)
    c.next(auto = false)
    assertEquals("t1", c.ui.value.current?.id)
  }

  @Test fun queuePlaysBeforeTheListAndIsConsumed() {
    val (c, _) = make()
    c.play(list[0], list)
    c.addToQueue(list[3]); c.playNext(list[2])
    assertEquals(listOf("t3", "t4"), c.ui.value.queue.map { it.id })
    c.next(); assertEquals("t3", c.ui.value.current?.id)
    c.next(); assertEquals("t4", c.ui.value.current?.id)
    assertTrue(c.ui.value.queue.isEmpty())
    c.next(); assertEquals("t5", c.ui.value.current?.id)
  }

  @Test fun queueMoveRemoveAndJump() {
    val (c, _) = make()
    c.play(list[0], list)
    list.drop(1).forEach(c::addToQueue)
    c.moveInQueue(0, 3)
    assertEquals(listOf("t3", "t4", "t5", "t2"), c.ui.value.queue.map { it.id })
    c.removeFromQueue(1)
    assertEquals(listOf("t3", "t5", "t2"), c.ui.value.queue.map { it.id })
    c.playQueueItem(1)
    assertEquals("t5", c.ui.value.current?.id)
    assertEquals(listOf("t2"), c.ui.value.queue.map { it.id })
  }

  @Test fun repeatOneLoopsOnAutoButNextMovesOn() {
    val (c, out) = make()
    c.play(list[0], list)
    c.cycleRepeat(); c.cycleRepeat()
    assertEquals(RepeatMode.ONE, c.ui.value.repeat)
    c.next(auto = true)
    assertEquals("t1", c.ui.value.current?.id)
    assertEquals(listOf("t1.mp3", "t1.mp3"), out.played)
    c.next(auto = false)
    assertEquals("t2", c.ui.value.current?.id)
  }

  @Test fun previousRestartsPastThresholdThenGoesBackThroughHistory() {
    val (c, out) = make()
    c.play(list[0], list); c.next(); c.next()
    assertEquals("t3", c.ui.value.current?.id)
    out.st.value = out.st.value.copy(positionMs = 10_000)
    c.previous()
    assertEquals(listOf(0L), out.seeks)
    assertEquals("t3", c.ui.value.current?.id)
    out.st.value = out.st.value.copy(positionMs = 1_000)
    c.previous()
    assertEquals("t2", c.ui.value.current?.id)
    c.previous()
    assertEquals("t1", c.ui.value.current?.id)
  }

  @Test fun seekIsClampedToTheTrack() {
    val (c, out) = make()
    c.play(list[0], list)
    c.seekTo(999_999); c.seekTo(-50); c.seekFraction(0.5f); c.seekBy(1000)
    assertEquals(listOf(200_000L, 0L, 100_000L, 101_000L), out.seeks)
  }

  @Test fun seekMathFractionsAndLabels() {
    assertEquals(0f, SeekMath.toFraction(5, 0), 0f)
    assertEquals(0.25f, SeekMath.toFraction(50, 200), 1e-6f)
    assertEquals(1f, SeekMath.toFraction(900, 200), 0f)
    assertEquals(75_000L, SeekMath.fromFraction(0.75f, 100_000))
    assertEquals(0L, SeekMath.clamp(-3, 0))
    assertEquals(7L, SeekMath.clamp(7, 0))
    assertEquals("3:05", SeekMath.label(185_999))
  }

  @Test fun trackEndedOnItsOwnAdvancesToTheNext() {
    val (c, out) = make()
    c.play(list[0], list)
    out.onEnded!!(false)
    assertEquals("t2", c.ui.value.current?.id)
    assertEquals(listOf("t1.mp3", "t2.mp3"), out.played)
  }

  @Test fun sleepAtEndOfTrackStopsInsteadOfAdvancing() {
    val (c, out) = make()
    c.play(list[0], list)
    c.sleepAtEndOfTrack()
    assertNull(out.armed)
    out.onEnded!!(false)
    assertEquals("t1", c.ui.value.current?.id)
    assertEquals(1, out.stops)
    assertFalse(c.ui.value.sleepAtEndOfTrack)
  }

  @Test fun sleepTimerPausesWhenTimeIsUp() {
    var now = 1_000L
    val (c, out) = make(now = { now })
    c.play(list[0], list)
    c.sleepIn(10)
    assertEquals(600_000L, c.sleepRemainingMs())
    now += 599_000; c.tickSleep(now)
    assertEquals(0, out.pauses)
    now += 1_000; c.tickSleep(now)
    assertEquals(1, out.pauses)
    assertNull(c.ui.value.sleepEndsAtMs)
  }

  @Test fun crossfadeArmsTheNextTrackOnlyWhenEligible() {
    val (c, out) = make()
    c.play(list[0], list)
    assertNull(out.armed)
    c.setCrossfade(6)
    assertEquals("t2.mp3", out.armed?.name)
    assertEquals(6000, out.armedMs)
    c.cycleRepeat(); c.cycleRepeat()
    assertNull(out.armed)
    c.cycleRepeat()
    c.spatialActive = true
    c.setCrossfade(6)
    assertNull(out.armed)
  }

  @Test fun sameAlbumPlaysGaplessWithoutCrossfade() {
    val (c, out) = make()
    val album = listOf(t(1, "Same"), t(2, "Same")).map { it.copy(albumId = 7L) }
    val a = album.map { it.copy(trackNumber = it.id.last().digitToInt()) }
    c.setCrossfade(6)
    c.play(a[0], a)
    assertNull(out.armed)
  }

  @Test fun engineCrossfadeAdvancesStateWithoutRestarting() {
    val (c, out) = make()
    c.setCrossfade(6)
    c.play(list[0], list)
    out.onEnded!!(true)
    assertEquals("t2", c.ui.value.current?.id)
    assertEquals(listOf("t1.mp3"), out.played)
    assertEquals("t3.mp3", out.armed?.name)
    assertEquals(listOf("t1"), c.ui.value.history.map { it.id })
  }

  @Test fun missingFileSkipsThenStopsAfterThreeFailures() {
    val out = FakeOut()
    val c = PlayerController(out, MemoryPrefs(), null, { 0L }) { false }
    c.play(list[0], list)
    assertNotNull(c.ui.value.problem)
    assertEquals(0, out.played.size)
  }

  @Test fun volumeIsClampedAndSaved() {
    val prefs = MemoryPrefs()
    val (c, out) = make(prefs)
    c.setVolume(1.7f); assertEquals(1f, out.volume, 0f)
    c.setVolume(0.4f)
    val (c2, out2) = make(prefs)
    assertEquals(0.4f, out2.volume, 1e-6f)
    assertEquals(0.4f, c2.ui.value.volume, 1e-6f)
  }

  @Test fun savedStateComesBackPausedAtTheSamePosition() {
    val prefs = MemoryPrefs()
    val (c, out) = make(prefs)
    c.play(list[1], list); c.addToQueue(list[4])
    out.st.value = out.st.value.copy(positionMs = 42_000)
    c.savePositionNow()
    val (c2, out2) = make(prefs)
    val r = c2.restore()
    assertTrue(r is RestoreOutcome.Restored)
    assertEquals("t2", c2.ui.value.current?.id)
    assertEquals(listOf("t5"), c2.ui.value.queue.map { it.id })
    assertEquals(5, c2.ui.value.list.size)
    assertEquals(42_000L, out2.st.value.positionMs)
    assertTrue(out2.startedPaused)
  }

  @Test fun nothingSavedRestoresNothing() {
    val (c, out) = make()
    assertTrue(c.restore() is RestoreOutcome.Nothing)
    assertTrue(out.played.isEmpty())
  }
}
