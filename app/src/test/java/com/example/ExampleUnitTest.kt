package com.example

import com.example.data.model.AudioProfile
import com.example.data.model.Track
import com.example.data.repository.CuriosityRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TarkasDomainTest {

  private val curiosityRepository = CuriosityRepository()

  @Test
  fun test15BandAudioProfilesAndZeroDbDefault() {
    val flat = AudioProfile.FLAT
    assertEquals("Must have 15 frequency bands", 15, flat.bandLevelsMb.size)
    assertTrue("Default flat profile must start at 0 dB for all 15 bands", flat.bandLevelsMb.all { it == 0.toShort() })

    val profiles = AudioProfile.DEFAULT_PROFILES
    assertTrue("Should have default calibrated audio profiles", profiles.isNotEmpty())

    profiles.forEach { profile ->
      assertEquals("Each profile must define 15 bands", 15, profile.bandLevelsMb.size)
      profile.bandLevelsMb.forEach { bandMb ->
        assertTrue("Band levels must be in safe range [-1500, +1500] mB", bandMb in -1500..1500)
      }
      assertTrue("Bass boost must be in [0, 1000]", profile.bassBoostStrength in 0..1000)
      assertTrue("Virtualizer must be in [0, 1000]", profile.virtualizerStrength in 0..1000)
    }
  }

  @Test
  fun testCuriosityRigorAndWordCountGuidelines() {
    val curiosities = curiosityRepository.getAllCuriosities()
    assertTrue("Curiosity database must contain curated tracks", curiosities.isNotEmpty())

    curiosities.forEach { cur ->
      assertTrue(
        "Curiosity for '${cur.trackTitle}' should adhere to conciseness (40-90 words), actual was ${cur.wordCount}",
        cur.wordCount in 40..90
      )
      assertTrue(
        "Verified curiosity must cite documented sources",
        cur.sources.isNotEmpty()
      )
      assertTrue("Documented facts must be filled in", cur.documentedFacts.isNotBlank())
    }
  }

  @Test
  fun testUnknownTrackHasNoCuriosityInsteadOfAPlaceholder() {
    val unknown = Track(id = "local_123", title = "Gravação", artist = "Eu", album = "", durationMs = 1000L, genre = "", era = "")
    assertNull("A track without a verified curiosity gets none, not a placeholder", curiosityRepository.getCuriosityForTrack(unknown))
    val demo = unknown.copy(id = "track_tarkus")
    assertNotNull(curiosityRepository.getCuriosityForTrack(demo))
  }

  @Test
  fun testTrackFormatting() {
    val track = Track(
      id = "test_track",
      title = "Tarkus Eruption",
      artist = "ELP",
      album = "Tarkus",
      durationMs = 125000L,
      genre = "Prog",
      era = "1971"
    )
    assertEquals("02:05", track.formattedDuration())
  }
}
