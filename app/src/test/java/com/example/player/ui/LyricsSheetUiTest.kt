package com.example.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.Lyrics
import com.example.data.lyrics.LyricsSource
import com.example.data.lyrics.LyricsState
import com.example.data.model.Track
import com.example.ui.components.LyricsSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lyrics sheet is shown from the player menu; the tests give it the state the ViewModel would. */
abstract class LyricsSheetUiBase(private val expectedTitle: String, private val expectedNone: String) {
  @get:Rule val rule = createComposeRule()

  private val track = Track("t1", "Song", "Artist", "Album", 180_000L, "Rock", "2000s")
  private val synced = Lyrics(
    listOf(LyricLine(0, "First line"), LyricLine(10_000, "Second line"), LyricLine(20_000, "Third line")),
    LyricsSource.EMBEDDED_SYNCED
  )

  private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)

  @Test fun syncedLyricsShowLinesAndSeekOnClick() {
    var sought: Long? = null
    val theme = rule.setTarkasContent {
      LyricsSheet(
        track = track,
        state = LyricsState(trackId = track.id, lyrics = synced),
        currentPositionMs = { 12_000L },
        onSeek = { sought = it },
        onDismiss = {}
      )
    }
    rule.mainClock.advanceTimeBy(1_000)
    rule.forEachTheme(theme) { mode ->
      sought = null
      rule.onNodeWithTag("lyrics_sheet").assertIsDisplayed()
      // Compared with a literal, so the test fails if the qualifier picked the wrong language.
      rule.onNodeWithText(expectedTitle).assertIsDisplayed()
      rule.onNodeWithText("Song").assertIsDisplayed()
      rule.onNodeWithText("First line").assertIsDisplayed()
      // 12 s is inside the second line, so that is the one marked as playing now.
      val active = rule.onNodeWithText("Second line").fetchSemanticsNode().config
      assertEquals("[$mode] active line", string(R.string.lyrics_now), active[SemanticsProperties.StateDescription])
      rule.onNodeWithText("Third line").performClick()
      assertEquals("[$mode] seek time", 20_000L, sought)
    }
  }

  @Test fun plainLyricsShowTheNoteAndDoNotSeek() {
    var sought: Long? = null
    rule.setTarkasContent {
      LyricsSheet(
        track = track,
        state = LyricsState(
          trackId = track.id,
          lyrics = Lyrics(listOf(LyricLine(null, "Only text")), LyricsSource.EMBEDDED_PLAIN)
        ),
        currentPositionMs = { 0L },
        onSeek = { sought = it },
        onDismiss = {}
      )
    }
    rule.mainClock.advanceTimeBy(1_000)
    rule.onNodeWithText(string(R.string.lyrics_plain_note)).assertIsDisplayed()
    rule.onNodeWithText("Only text").performClick()
    assertNull(sought)
  }

  @Test fun noLyricsShowTheMessage() {
    val theme = rule.setTarkasContent {
      LyricsSheet(track, LyricsState(trackId = track.id), { 0L }, {}, {})
    }
    rule.mainClock.advanceTimeBy(1_000)
    rule.forEachTheme(theme) {
      rule.onNodeWithTag("lyrics_message").assertIsDisplayed().assertTextEquals(expectedNone)
    }
  }

  @Test fun loadingShowsNoMessageAndNoList() {
    rule.setTarkasContent {
      LyricsSheet(track, LyricsState(trackId = track.id, loading = true), { 0L }, {}, {})
    }
    rule.mainClock.advanceTimeBy(1_000)
    rule.onNodeWithTag("lyrics_sheet").assertIsDisplayed()
    assertFalse(rule.hasNodeWithTag("lyrics_message"))
    assertFalse(rule.hasNodeWithTag("lyrics_list"))
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class LyricsSheetEnUiTest : LyricsSheetUiBase(
  expectedTitle = "Lyrics",
  expectedNone = "No lyrics found. Add them to the song's tag, or put a .lrc file with the same name beside the song."
)

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class LyricsSheetPtUiTest : LyricsSheetUiBase(
  expectedTitle = "Letra",
  expectedNone = "Nenhuma letra encontrada. Grave-a na tag da música ou coloque um arquivo .lrc com o mesmo nome ao lado dela."
)
