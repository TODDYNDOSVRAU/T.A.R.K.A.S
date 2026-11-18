package com.example.windows.biblioteca

import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.data.playback.TrackStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDataTest {
  private fun t(id: String, genre: String = "") = Track(id, "T$id", "A", "", 1000, genre, "")
  private val lib = listOf(t("1", "rock"), t("2", "rock"), t("3", "jazz"))
  private val label = { n: Int -> "$n" }

  @Test fun emptyLibraryGivesEmptySections() {
    assertTrue(HomeData.speedDial(emptyList(), emptySet(), emptyList(), emptyMap(), "Fav", label).isEmpty())
    assertTrue(HomeData.mixes(emptyList(), emptyMap(), { it.name }, label).isEmpty())
    assertTrue(HomeData.listening(null, emptyList()).isEmpty())
  }

  @Test fun speedDialHoldsFavouritesPlaylistsAndMostPlayedOnly() {
    val stats = mapOf("3" to TrackStats(plays = 4, skips = 0, lastPlayedMs = 10, lastSkippedMs = 0))
    val d = HomeData.speedDial(lib, setOf("1"), listOf(Playlist("p", "Mine", listOf("2", "gone")), Playlist("q", "Empty", listOf("gone"))), stats, "Fav", label)
    assertEquals(listOf("fav", "p:p", "t:3"), d.map { it.id })
    assertEquals(listOf("2"), d[1].tracks.map { it.id })
  }

  @Test fun mixesSkipEmptyListsAndAddTopGenres() {
    val stats = mapOf("1" to TrackStats(plays = 2, skips = 0, lastPlayedMs = 5, lastSkippedMs = 0))
    val m = HomeData.mixes(lib, stats, { it.name }, label)
    assertTrue(m.any { it.id == "a:MOST_PLAYED" } && m.any { it.id == "a:RECENTLY_PLAYED" })
    assertEquals(2, m.first { it.kind == HomeEntry.Kind.GENRE }.tracks.size)
  }

  @Test fun listeningPutsCurrentFirstNewestHistoryNext() {
    assertEquals(listOf("3", "2", "1"), HomeData.listening(t("3"), listOf(t("1"), t("2"), t("3"))).map { it.id })
  }
}
