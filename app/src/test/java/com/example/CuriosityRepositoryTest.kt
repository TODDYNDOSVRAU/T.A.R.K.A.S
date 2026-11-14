package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Track
import com.example.data.repository.CuriosityRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The curiosity texts come from resources: each language must keep the same tracks and the same rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CuriosityRepositoryTest {

  private val expectedIds = listOf("track_tarkus", "track_tubular", "track_debussy", "track_satie", "track_sweep")

  private fun repository() = CuriosityRepository(ApplicationProvider.getApplicationContext())

  private fun track(id: String) =
    Track(id = id, title = "t", artist = "a", album = "", durationMs = 1000L, genre = "", era = "")

  private fun checkAll() {
    val all = repository().getAllCuriosities()
    assertEquals(expectedIds, all.map { it.trackId })
    all.forEach { cur ->
      assertTrue("${cur.trackId} trivia", cur.shortTrivia.isNotBlank())
      assertTrue("${cur.trackId} facts", cur.documentedFacts.isNotBlank())
      assertFalse("${cur.trackId} must not keep a raw resource key", cur.shortTrivia.startsWith("curiosity_"))
      assertTrue("${cur.trackId} word count ${cur.wordCount}", cur.wordCount in 40..90)
      assertTrue("${cur.trackId} sources", cur.sources.isNotEmpty())
    }
  }

  @Test
  fun englishTextsAreComplete() {
    checkAll()
  }

  @Test
  fun recognitionByIdIsTheSameInEveryLanguage() {
    expectedIds.forEach { id ->
      assertNotNull(id, repository().getCuriosityForTrack(track(id)))
    }
  }

  @Test
  @Config(sdk = [36], qualifiers = "pt-rBR")
  fun portugueseTextsAreCompleteAndKeepTheSameTracks() {
    checkAll()
    assertEquals("Tarkus: Eruption Suite", repository().getCuriosityForTrack(track("track_tarkus"))?.trackTitle)
  }
}
