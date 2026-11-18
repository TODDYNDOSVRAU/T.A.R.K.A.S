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

  private fun on(id: String, album: String, artist: String = "A") = Track(id, "T$id", artist, album, 1000, "", "")

  @Test fun interleaveWeavesAndDropsRepeats() {
    val out = HomeData.interleave(listOf(on("a1", ""), on("a2", "")), listOf(on("j1", ""), on("a2", ""), on("j3", "")))
    assertEquals(listOf("a1", "j1", "a2", "j3"), out.map { it.id })
  }

  @Test fun albumsGroupBySharedAlbumAndArtistAndSkipBlank() {
    val tracks = listOf(on("1", "X"), on("2", ""), on("3", "x"), on("4", "X", "B"))
    val al = HomeData.albums(tracks)
    assertEquals(2, al.size)
    assertEquals(listOf("1", "3"), al[0].tracks.map { it.id })
    assertEquals(HomeEntry.Kind.ALBUM, al[0].kind)
  }
}

class ShelfMomentumTest {
  @org.junit.Test fun releaseCarriesInTheDirectionOfTheDragAndStandsStillAtRest() {
    assertEquals(0f, com.example.ui.theme.CoverCarousel.project(0f, 0f), 0f)
    assertTrue(com.example.ui.theme.CoverCarousel.project(0f, 2000f) > 0f)
    assertTrue(com.example.ui.theme.CoverCarousel.project(0f, -2000f) < 0f)
  }
}

class PlaylistAndGreetingTest {
  private fun t(id: String, album: String) = Track(id, id, "A", album, 60_000, "", "")
  @org.junit.Test fun durationReadsInMinutesAndHours() {
    assertEquals("42 min", totalDuration(42 * 60_000L))
    assertEquals("1 h 05 min", totalDuration(65 * 60_000L))
  }
  @org.junit.Test fun mosaicTakesFourDistinctCovers() {
    assertEquals(4, mosaicTracks(listOf(t("1", "a"), t("2", "a"), t("3", "b"), t("4", "c"), t("5", "d"), t("6", "e"))).size)
    assertEquals(1, mosaicTracks(listOf(t("1", "a"), t("2", "a"))).size)
  }
  @org.junit.Test fun greetingFollowsTheHourAndNamesTheUser() {
    assertTrue(greeting(9, "Ana").endsWith(", Ana"))
    assertEquals(greeting(9, ""), greeting(10, "").also { }) // same part of the day
  }
}
