package com.example.windows.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.theme.TarkasTheme
import com.example.windows.audio.PlayerState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 2.1.3 audit: Next/Previous, seek and the state the bars show, tested through the real controller and
 * hooks against the fake engine, and the seek bars through the real composables. What the audio device does
 * (decoder reopen, line buffer) is the engine's and is not covered here.
 */
@OptIn(ExperimentalTestApi::class)
class PlaybackAuditTest {
  private fun t(n: Int, dur: Long = 200_000) = Track("t$n", "Title $n", "Artist", "A$n", dur, "", "", true, "C:/m/t$n.mp3")
  private val list = (1..5).map { t(it) }

  private fun make(): Triple<PlayerController, FakeOut, PlayerHooks> {
    val out = FakeOut()
    val c = PlayerController(out, MemoryPrefs(), exists = { true })
    return Triple(c, out, PlayerHooks(c, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null)))
  }

  @Test fun rapidNextMovesOneTrackPerPress() {
    val (c, out, _) = make()
    c.play(list[0], list)
    repeat(3) { c.next() }
    assertEquals("t4", c.ui.value.current?.id)
    assertEquals(listOf("t1.mp3", "t2.mp3", "t3.mp3", "t4.mp3"), out.played)
  }

  @Test fun doublePreviousRestartsThenGoesBack() {
    val (c, out, _) = make()
    c.play(list[0], list); c.next(); c.next()
    out.st.value = out.st.value.copy(positionMs = 30_000)
    c.previous(); c.previous()
    // The first press restarts t3 (the engine publishes 0 at once), the second leaves it for t2.
    assertEquals(listOf(0L), out.seeks)
    assertEquals("t2", c.ui.value.current?.id)
  }

  @Test fun trackPlayedOutsideTheListStillHasNextAndPrevious() {
    val (c, out, _) = make()
    c.play(list[0], list)
    val outsider = t(9)
    c.play(outsider, null)
    assertEquals("t9", c.ui.value.current?.id)
    assertTrue(c.ui.value.list.any { it.id == "t9" })
    c.next()
    // With only itself in the list, Next wraps onto itself: audible restart, never a stuck state.
    assertEquals("t9", c.ui.value.current?.id)
    assertEquals("t9.mp3", out.played.last())
  }

  @Test fun fractionalSeekWaitsForALength() {
    val (c, out, _) = make()
    c.play(t(1, dur = 0), listOf(t(1, dur = 0)))
    out.st.value = out.st.value.copy(durationMs = 0)
    c.seekFraction(0.5f)
    assertTrue("no length yet: no seek to a made-up place", out.seeks.isEmpty())
    out.st.value = out.st.value.copy(durationMs = 100_000)
    c.seekFraction(0.5f)
    assertEquals(listOf(50_000L), out.seeks)
  }

  @Test fun seekWithNothingLoadedDoesNothing() {
    val (c, out, _) = make()
    c.seekTo(10_000); c.seekFraction(0.5f)
    assertTrue(out.seeks.isEmpty())
  }

  @Test fun loadingIsNeitherPlayingNorPausedAndTheTotalFallsBackToTheLibrary() {
    val (c, out, h) = make()
    c.play(list[0], list)
    out.st.value = PlayerState(file = File("C:/m/t1.mp3"), playing = false, positionMs = 0, durationMs = 0, loading = true)
    val s = h.state.value
    assertTrue(s.loading)
    assertFalse(s.playing)
    assertEquals("the total never reads 0:00 while the file opens", 200_000L, s.durationMs)
    out.st.value = out.st.value.copy(loading = false, playing = true, durationMs = 199_000)
    assertFalse(h.state.value.loading)
    assertEquals(199_000L, h.state.value.durationMs)
  }

  @Test fun barsFollowTheEnginePositionAndSeekMovesThemAtOnce() {
    val (c, out, h) = make()
    c.play(list[0], list)
    out.st.value = out.st.value.copy(positionMs = 50_000)
    assertEquals(0.25f, h.state.value.fraction, 1e-6f)
    h.seekFraction(0.75f)
    assertEquals(150_000L, out.seeks.last())
    assertEquals(0.75f, h.state.value.fraction, 1e-6f)
  }

  // Seek bars through the real composables -----------------------------------------------------------

  @Test fun clickOnTheThinLineSeeksToThatPoint() = runDesktopComposeUiTest(400, 120) {
    val got = mutableListOf<Float>()
    setContent { TarkasTheme { ThinProgress(0f, true, { got += it }, Modifier.width(300.dp).testTag("bar"), live = false) } }
    onNodeWithTag("bar").performMouseInput { click(Offset(width * 0.25f, height / 2f)) }
    assertEquals(1, got.size)
    assertEquals(0.25f, got[0], 0.02f)
  }

  @Test fun dragOnTheSeekLineCommitsOnceOnRelease() = runDesktopComposeUiTest(400, 120) {
    val got = mutableListOf<Float>()
    val scrubs = mutableListOf<Float?>()
    setContent { TarkasTheme { ThinProgress(0f, true, { got += it }, Modifier.width(300.dp).testTag("bar"), live = false, onScrub = { scrubs += it }) } }
    onNodeWithTag("bar").performMouseInput {
      moveTo(Offset(width * 0.1f, height / 2f)); press()
      moveTo(Offset(width * 0.5f, height / 2f)); moveTo(Offset(width * 0.8f, height / 2f))
      release()
    }
    assertEquals("one seek, at the release point", 1, got.size)
    assertEquals(0.8f, got[0], 0.03f)
    assertTrue("the label follows the pointer meanwhile", scrubs.filterNotNull().isNotEmpty())
    assertEquals("scrub ends with null", null, scrubs.last())
  }

  @Test fun volumeLineStaysLiveWhileDragging() = runDesktopComposeUiTest(400, 120) {
    val got = mutableListOf<Float>()
    setContent { TarkasTheme { ThinProgress(1f, true, { got += it }, Modifier.width(300.dp).testTag("vol")) } }
    onNodeWithTag("vol").performMouseInput {
      moveTo(Offset(width * 0.9f, height / 2f)); press()
      moveTo(Offset(width * 0.5f, height / 2f)); moveTo(Offset(width * 0.2f, height / 2f))
      release()
    }
    assertTrue("each move sets the level", got.size >= 2)
    assertEquals(0.2f, got.last(), 0.03f)
  }

  @Test fun disabledLineIgnoresClicks() = runDesktopComposeUiTest(400, 120) {
    val got = mutableListOf<Float>()
    setContent { TarkasTheme { ThinProgress(0f, false, { got += it }, Modifier.width(300.dp).testTag("bar"), live = false) } }
    onNodeWithTag("bar").performMouseInput { click(Offset(width * 0.5f, height / 2f)) }
    assertTrue(got.isEmpty())
  }

  @Test fun dragOnTheWaveformShowsTheTimeUnderThePointerThenSeeksOnce() = runDesktopComposeUiTest(600, 300) {
    val (_, out, h) = make()
    h.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.fillMaxWidth()) { SeekSection(h, h.state.value, 96.dp, Modifier.fillMaxWidth(), Modifier.testTag("wave")) } } }
    // 200 s long: 50 % is 3:20 / 2 = 1:40.
    onNodeWithTag("wave").performMouseInput {
      moveTo(Offset(width * 0.1f, height / 2f)); press()
      moveTo(Offset(width * 0.5f, height / 2f))
    }
    waitForIdle()
    onNodeWithTag("seek_elapsed", useUnmergedTree = true).assertTextEquals("1:40")
    assertTrue("nothing was sent to the engine while dragging", out.seeks.isEmpty())
    onNodeWithTag("wave").performMouseInput { release() }
    waitForIdle()
    assertEquals(1, out.seeks.size)
    assertTrue("seek at the release point: ${out.seeks[0]}", Math.abs(out.seeks[0] - 100_000L) <= 4_000L)
  }

  @Test fun loadingShowsTheProgressRingNotAPlayIcon() = runDesktopComposeUiTest(200, 200) {
    mainClock.autoAdvance = false
    var loading by mutableStateOf(true)
    setContent { TarkasTheme { PlayGlyph(playing = false, loading = loading, tint = MaterialTheme.colorScheme.primary) } }
    mainClock.advanceTimeBy(50)
    onNodeWithTag("play_loading").assertExists()
    loading = false
    mainClock.advanceTimeBy(50)
    onNodeWithTag("play_loading").assertDoesNotExist()
  }
}
