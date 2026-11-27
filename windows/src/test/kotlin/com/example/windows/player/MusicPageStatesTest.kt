package com.example.windows.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.Lyrics
import com.example.data.lyrics.LyricsSource
import com.example.data.model.Track
import androidx.compose.runtime.collectAsState
import com.example.ui.theme.TarkasTheme
import com.example.windows.res.Res
import com.example.windows.res.pd_empty_action
import com.example.windows.res.pd_empty_message
import com.example.windows.res.pd_lyrics_idle
import com.example.windows.res.pd_lyrics_loading
import com.example.windows.res.player_screen_nothing_playing
import com.example.windows.res.trNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Música page's states: nothing playing is an empty state (what it is, the one action), never a loading one; the
 * lyrics area tells idle, reading and none apart; and the artist line is never squeezed out of a short window.
 */
@OptIn(ExperimentalTestApi::class)
class MusicPageStatesTest {
  private fun t(n: Int) = Track("t$n", "Title $n", "Artist $n", "A$n", 200_000, "", "", true, "C:/m/t$n.mp3")

  private class Fixture {
    val controller = PlayerController(FakeOut(), MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
  }

  // ---- the pure rules ----

  @Test fun theLyricsStatusSeparatesIdleReadingNoneAndReady() {
    val lines = Lyrics(listOf(LyricLine(0L, "x")), LyricsSource.EMBEDDED_SYNCED)
    assertEquals(LyricsStatus.Idle, LyricsStatus.of(hasTrack = false, settled = false, lyrics = null))
    assertEquals(LyricsStatus.Idle, LyricsStatus.of(hasTrack = false, settled = true, lyrics = null))
    assertEquals(LyricsStatus.Loading, LyricsStatus.of(hasTrack = true, settled = false, lyrics = null))
    assertEquals(LyricsStatus.None, LyricsStatus.of(hasTrack = true, settled = true, lyrics = null))
    assertEquals(LyricsStatus.Ready, LyricsStatus.of(hasTrack = true, settled = true, lyrics = lines))
  }

  @Test fun theCoverIs384OnlyWhenTheBandAlsoHoldsTheTextUnderIt() {
    assertEquals(192, pageCoverDp(428f))    // 1280 x 800 window: the band is 428 dp, the 384 cover left no room for the artist
    assertEquals(192, pageCoverDp(491.9f))
    assertEquals(384, pageCoverDp(492f))
    assertEquals(384, pageCoverDp(600f))
  }

  @Test fun theLyricsOfATrackAreReadyOnceLookedForAndResetOnTheNextTrack() = runBlocking {
    val a = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
    assertFalse(a.lyricsReady.value)
    a.follow(t(1))
    withTimeout(10_000) { a.lyricsReady.first { it } }        // the file does not exist: looked for, none found
    assertEquals(null, a.lyrics.value)
    a.follow(null)
    assertFalse("nothing playing is not 'ready'", a.lyricsReady.value)
  }

  // ---- the page ----

  private fun androidx.compose.ui.test.ComposeUiTest.page(f: Fixture, onOpen: () -> Unit = {}, h: Int = 728) {
    setContent { TarkasTheme { Box(Modifier.size(1280.dp, h.dp)) { PlayerScreen(f.hooks, f.assets, onOpen) } } }
    waitForIdle()
  }

  @Test fun nothingPlayingIsAnEmptyStateWithOneActionAndNoReadingMessage() = runDesktopComposeUiTest(width = 1280, height = 728) {
    var opened = 0
    page(Fixture(), onOpen = { opened++ })
    onNodeWithTag("music_empty", useUnmergedTree = true).assertExists()
    onNodeWithText(trNow(Res.string.player_screen_nothing_playing)).assertExists()
    onNodeWithText(trNow(Res.string.pd_empty_message)).assertExists()          // the explanation
    onNodeWithText(trNow(Res.string.pd_empty_action)).assertExists()           // the action
    onNodeWithText(trNow(Res.string.pd_lyrics_loading)).assertDoesNotExist()   // not a loading state
    onNodeWithTag("lyrics_loading", useUnmergedTree = true).assertDoesNotExist()
    onNodeWithTag("music_empty_action").performClick()
    assertEquals(1, opened)
    // The explanation is not cut: its box has a height and sits inside the window.
    val b = onNodeWithTag("music_empty", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue("empty state inside the window: $b", b.top >= 0f && b.bottom <= 728f && b.height > 96f)
  }

  @Test fun aTrackReplacesTheEmptyStateAndKeepsItsArtistLineInA1280By800Window() = runDesktopComposeUiTest(width = 1280, height = 728) {
    val f = Fixture(); f.controller.play(t(1), listOf(t(1)))
    page(f)
    onNodeWithTag("music_empty", useUnmergedTree = true).assertDoesNotExist()
    val artist = onNodeWithText("Artist 1 · A1").fetchSemanticsNode().boundsInRoot
    assertTrue("the artist line has room (height ${artist.height})", artist.height >= 12f)
    assertTrue("the artist line is inside the window", artist.bottom <= 728f)
  }

  @Test fun theLyricsAreReadingForATrackNotYetSettledAndIdleWithNone() = runDesktopComposeUiTest(width = 400, height = 200) {
    val f = Fixture()
    setContent { TarkasTheme { Box(Modifier.size(400.dp, 200.dp)) { LyricsView(null, false, f.hooks.state.value, f.hooks, Modifier.size(400.dp, 200.dp)) } } }
    waitForIdle()
    onNodeWithTag("lyrics_idle", useUnmergedTree = true).assertExists()
    onNodeWithText(trNow(Res.string.pd_lyrics_idle)).assertExists()
    onNodeWithText(trNow(Res.string.pd_lyrics_loading)).assertDoesNotExist()
  }

  @Test fun withATrackTheLyricsAreReadingThenNone() = runDesktopComposeUiTest(width = 400, height = 200) {
    val f = Fixture(); f.controller.play(t(1), listOf(t(1)))
    val settled = MutableStateFlow(false)
    setContent {
      val s = settled.collectAsState().value
      TarkasTheme { Box(Modifier.size(400.dp, 200.dp)) { LyricsView(null, s, f.hooks.state.value, f.hooks, Modifier.size(400.dp, 200.dp)) } }
    }
    waitForIdle()
    onNodeWithTag("lyrics_loading", useUnmergedTree = true).assertExists()
    onNodeWithText(trNow(Res.string.pd_lyrics_loading)).assertExists()
    settled.value = true
    waitForIdle()
    onNodeWithTag("lyrics_none", useUnmergedTree = true).assertExists()
    onNodeWithTag("lyrics_loading", useUnmergedTree = true).assertDoesNotExist()
  }
}
