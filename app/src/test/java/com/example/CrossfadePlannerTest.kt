package com.example

import com.example.data.model.Track
import com.example.data.playback.CrossfadePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When tracks crossfade, and the equal-power curve. */
class CrossfadePlannerTest {

  private fun t(id: String, ms: Long = 200_000, album: Long? = null, n: Int = 0, path: String? = "f") =
    Track(id = id, title = id, artist = "A", album = "", durationMs = ms, genre = "", era = "", filePath = path, albumId = album, trackNumber = n)

  private fun ok(a: Track?, b: Track?, s: Int = 6, repeat: Boolean = false, spatial: Boolean = false, narration: Boolean = false, sleep: Boolean = false) =
    CrossfadePlanner.eligible(a, b, s, repeat, spatial, narration, sleep)

  @Test
  fun twoOrdinaryTracksCrossfade() = assertTrue(ok(t("a"), t("b")))

  @Test
  fun offOrNothingNextOrSameTrackDoesNot() {
    assertFalse(ok(t("a"), t("b"), s = 0))
    assertFalse(ok(t("a"), null))
    assertFalse(ok(t("a"), t("a")))
    assertFalse(ok(t("a"), t("b"), repeat = true))
    assertFalse(ok(t("a"), t("b", path = null)))
  }

  @Test
  fun shortTracksAndUnknownLengthsDoNot() {
    assertFalse(ok(t("a", ms = 11_000), t("b")))   // under 2 × 6 s
    assertTrue(ok(t("a", ms = 12_000), t("b")))
    assertFalse(ok(t("a"), t("b", ms = 0)))
  }

  @Test
  fun anAlbumInOrderPlaysGapless() {
    assertFalse(ok(t("a", album = 5, n = 3), t("b", album = 5, n = 4)))
    assertTrue(ok(t("a", album = 5, n = 3), t("b", album = 5, n = 7)))
  }

  @Test
  fun spatialNarrationAndSleepTimerTurnItOff() {
    assertFalse(ok(t("a"), t("b"), spatial = true))
    assertFalse(ok(t("a"), t("b"), narration = true))
    assertFalse(ok(t("a"), t("b"), sleep = true))
  }

  @Test
  fun theCurveKeepsTheSummedPowerAtOne() {
    for (i in 0..100) {
      val x = i / 100f
      val power = CrossfadePlanner.outGain(x).let { it * it } + CrossfadePlanner.inGain(x).let { it * it }
      assertEquals("t=$x", 1f, power, 0.01f)
    }
    assertEquals(1f, CrossfadePlanner.outGain(0f), 1e-6f)
    assertEquals(0f, CrossfadePlanner.inGain(0f), 1e-6f)
    assertEquals(0f, CrossfadePlanner.outGain(1f), 1e-6f)
  }
}
