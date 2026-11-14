package com.example

import com.example.data.library.AutoList
import com.example.data.library.AutoLists
import com.example.data.model.Track
import com.example.data.playback.TrackStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoListsTest {
  private fun t(id: String, added: Long = 0, title: String = id) =
    Track(id = id, title = title, artist = "", album = "", durationMs = 1, genre = "", era = "", dateAddedSec = added)

  private fun ids(list: AutoList, tracks: List<Track>, stats: Map<String, TrackStats> = emptyMap(), limit: Int = AutoLists.LIMIT) =
    AutoLists.build(list, tracks, stats, limit).map { it.id }

  @Test
  fun `recently added is newest first and leaves out songs without a date`() {
    val tracks = listOf(t("old", 10), t("none", 0), t("new", 30), t("mid", 20))
    assertEquals(listOf("new", "mid", "old"), ids(AutoList.RECENTLY_ADDED, tracks))
  }

  @Test
  fun `recently added breaks a tie by title`() {
    val tracks = listOf(t("b", 5, "Beta"), t("a", 5, "alfa"))
    assertEquals(listOf("a", "b"), ids(AutoList.RECENTLY_ADDED, tracks))
  }

  @Test
  fun `recently played is the latest first and ignores never played`() {
    val tracks = listOf(t("a"), t("b"), t("c"))
    val stats = mapOf("a" to TrackStats(plays = 1, lastPlayedMs = 100), "b" to TrackStats(skips = 3, lastSkippedMs = 900), "c" to TrackStats(plays = 5, lastPlayedMs = 200))
    assertEquals(listOf("c", "a"), ids(AutoList.RECENTLY_PLAYED, tracks, stats))
  }

  @Test
  fun `most played orders by plays, then by the latest play`() {
    val tracks = listOf(t("a"), t("b"), t("c"), t("d"))
    val stats = mapOf(
      "a" to TrackStats(plays = 3, lastPlayedMs = 10),
      "b" to TrackStats(plays = 7, lastPlayedMs = 5),
      "c" to TrackStats(plays = 3, lastPlayedMs = 50),
      "d" to TrackStats(plays = 0)
    )
    assertEquals(listOf("b", "c", "a"), ids(AutoList.MOST_PLAYED, tracks, stats))
  }

  @Test
  fun `counters of songs that left the library are not listed`() {
    val tracks = listOf(t("here"))
    val stats = mapOf("here" to TrackStats(plays = 1, lastPlayedMs = 1), "gone" to TrackStats(plays = 99, lastPlayedMs = 99))
    assertEquals(listOf("here"), ids(AutoList.MOST_PLAYED, tracks, stats))
    assertEquals(listOf("here"), ids(AutoList.RECENTLY_PLAYED, tracks, stats))
  }

  @Test
  fun `a list stops at its limit`() {
    val tracks = (1..80).map { t("s$it", added = it.toLong()) }
    assertEquals(AutoLists.LIMIT, ids(AutoList.RECENTLY_ADDED, tracks).size)
    assertEquals("s80", ids(AutoList.RECENTLY_ADDED, tracks).first())
    assertEquals(3, ids(AutoList.RECENTLY_ADDED, tracks, limit = 3).size)
  }

  @Test
  fun `no data gives empty lists`() {
    val all = AutoLists.all(listOf(t("a")), emptyMap())
    assertEquals(AutoList.entries.toSet(), all.keys)
    assertTrue(all.getValue(AutoList.RECENTLY_PLAYED).isEmpty())
    assertTrue(all.getValue(AutoList.MOST_PLAYED).isEmpty())
    assertTrue(all.getValue(AutoList.RECENTLY_ADDED).isEmpty())
  }
}
