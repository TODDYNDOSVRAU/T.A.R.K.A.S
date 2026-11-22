package com.example.data.library

import com.example.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sprint3LibraryTest {

  private fun track(
    id: String,
    title: String,
    artist: String = "Artist",
    filePath: String? = "/storage/emulated/0/Music/$id.flac",
    durationMs: Long = 200_000L
  ) = Track(
    id = id,
    title = title,
    artist = artist,
    album = "Album",
    durationMs = durationMs,
    genre = "Rock",
    era = "2020",
    isLocalFile = true,
    filePath = filePath
  )

  @Test
  fun folders_groupsTracksByDirectoryAndSortsByName() {
    val t1 = track("1", "Zeta")
    val t2 = track("2", "Alpha")
    val t3 = track("3", "Beta")
    val t4 = track("4", "Root Song")
    val folderOf = mapOf(
      "1" to "Music/Jazz",
      "2" to "Music/Ambient",
      "3" to "Music/Jazz",
      "4" to ""
    )

    val groups = LibraryGroups.folders(listOf(t1, t2, t3, t4), folderOf, rootLabel = "Main")
    assertEquals(3, groups.size)
    assertEquals("Ambient", groups[0].name)
    assertEquals("Music/Ambient", groups[0].fullPath)
    assertEquals(listOf("2"), groups[0].tracks.map { it.id })

    assertEquals("Jazz", groups[1].name)
    assertEquals("Music/Jazz", groups[1].fullPath)
    // Sorted by title inside folder
    assertEquals(listOf("3", "1"), groups[1].tracks.map { it.id })

    assertEquals("Main", groups[2].name)
    assertEquals("", groups[2].fullPath)
    assertEquals(listOf("4"), groups[2].tracks.map { it.id })
  }

  @Test
  fun m3u_exportAndParseRoundTrip() {
    val t1 = track("1", "Clair de Lune", "Claude Debussy", "/Music/Classical/clair_de_lune.flac", 300_000L)
    val t2 = track("2", "Tarkus", "ELP", "/Music/Prog/tarkus.mp3", 1200_000L)
    val t3 = track("3", "Unused", "Other", "/Music/Other/unused.mp3", 180_000L)

    val m3u = M3uFormat.export("Favorites", listOf(t1, t2))
    assertTrue(m3u.startsWith("#EXTM3U"))
    assertTrue(m3u.contains("#PLAYLIST:Favorites"))
    assertTrue(m3u.contains("#EXTINF:300,Claude Debussy - Clair de Lune"))

    val parsed = M3uFormat.parse(m3u, listOf(t3, t2, t1))
    assertEquals(listOf("1", "2"), parsed.map { it.id })
  }

  @Test
  fun m3u_parseMatchesWindowsPathFilenameAndExtInfAndDeduplicates() {
    val t1 = track("1", "Midnight City", "M83", "/storage/emulated/0/Music/midnight_city.flac")
    val t2 = track("2", "Time", "Pink Floyd", "/storage/emulated/0/Music/04_time.mp3")
    val m3u = """
      #EXTM3U
      #EXTINF:243,M83 - Midnight City
      C:\Users\Owner\Music\midnight_city.flac
      #EXTINF:413,Pink Floyd - Time
      relative\path\different_name.mp3
      # Duplicate should not appear twice
      /storage/emulated/0/Music/midnight_city.flac
    """.trimIndent()

    val parsed = M3uFormat.parse(m3u, listOf(t1, t2))
    assertEquals(listOf("1", "2"), parsed.map { it.id })
  }

  @Test
  fun offlineDownloader_sanitizesTrackIdsSafely() {
    assertEquals("audius_12345", OfflineTrackDownloader.sanitizeFileName("audius_12345"))
    assertEquals("jamendo_a_b_c", OfflineTrackDownloader.sanitizeFileName("jamendo:a/b?c"))
    assertEquals("track", OfflineTrackDownloader.sanitizeFileName("   "))
  }
}
