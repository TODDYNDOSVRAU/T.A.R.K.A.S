package com.example.windows.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.theme.TarkasTheme
import com.example.windows.biblioteca.LibraryScanner
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scanner stores "Unknown artist" for a file without the tag; the player surfaces show it through displayArtist, so a
 * Portuguese user reads "Artista desconhecido". Display only: the track's own value never changes.
 */
@OptIn(ExperimentalTestApi::class)
class UnknownArtistDisplayTest {
  private val unknown = Track("u1", "Faixa", LibraryScanner.UNKNOWN_ARTIST, "Album", 200_000, "", "", true, "C:/m/u1.mp3")
  private val list = listOf(unknown, Track("u2", "Outra", "Artist 2", "Album", 200_000, "", "", true, "C:/m/u2.mp3"))
  private var saved: Locale = Locale.getDefault()

  @Before fun keep() { saved = Locale.getDefault() }
  @After fun restore() { Locale.setDefault(saved) }

  private class Fixture {
    val controller = PlayerController(FakeOut(), MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
  }

  private fun shows(language: String, expected: String) = runDesktopComposeUiTest(width = 432, height = 120) {
    Locale.setDefault(Locale.forLanguageTag(language))
    val f = Fixture(); f.controller.play(unknown, list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 120.dp)) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = false) } } }
    waitForIdle()
    onNodeWithTag("compact_artist", useUnmergedTree = true).assertTextEquals(expected)
    assertEquals("the stored value is untouched", "Unknown artist", f.controller.ui.value.current?.artist)
  }

  @Test fun thePillShowsTheArtistInPortuguese() = shows("pt", "Artista desconhecido")

  @Test fun thePillShowsTheArtistInEnglish() = shows("en", "Unknown artist")

  @Test fun aRealArtistIsNeverChanged() = runDesktopComposeUiTest(width = 432, height = 120) {
    Locale.setDefault(Locale.forLanguageTag("pt"))
    val f = Fixture(); f.controller.play(list[1], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 120.dp)) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = false) } } }
    waitForIdle()
    onNodeWithTag("compact_artist", useUnmergedTree = true).assertTextEquals("Artist 2")
  }

  @Test fun theQueueStackShowsTheArtistInPortuguese() = runDesktopComposeUiTest(width = 400, height = 320) {
    Locale.setDefault(Locale.forLanguageTag("pt"))
    val f = Fixture(); f.controller.play(unknown, list)
    setContent { TarkasTheme { Box(Modifier.size(400.dp, 320.dp)) { QueueCardStackBody(f.hooks, f.assets, false, Modifier, QueueStackEngine()) } } }
    mainClock.advanceTimeBy(1000)
    waitForIdle()
    // The stack keeps several cards; the current one is the unknown artist, the others show their own.
    val shown = onAllNodesWithTag("stack_artist", useUnmergedTree = true).fetchSemanticsNodes()
      .flatMap { n -> (n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text) ?: emptyList()).map { it.text } }
    assertTrue("$shown", "Artista desconhecido" in shown)
    assertTrue("$shown", "Unknown artist" !in shown)
  }
}
