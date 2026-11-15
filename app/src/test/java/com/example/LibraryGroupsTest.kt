package com.example

import com.example.data.library.LibraryGroups
import com.example.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGroupsTest {
  private fun t(
    id: String, title: String = id, artist: String = "Art", album: String = "Alb", albumId: Long? = null,
    genre: String = "", year: String = "", disc: Int = 0, no: Int = 0
  ) = Track(
    id = id, title = title, artist = artist, album = album, durationMs = 1, genre = genre, era = year,
    albumId = albumId, discNumber = disc, trackNumber = no
  )

  @Test
  fun `an album keeps its songs in disc and track order`() {
    val tracks = listOf(
      t("c", albumId = 1, disc = 2, no = 1), t("b", albumId = 1, disc = 1, no = 2), t("a", albumId = 1, disc = 1, no = 1)
    )
    val album = LibraryGroups.albums(tracks).single()
    assertEquals(listOf("a", "b", "c"), album.tracks.map { it.id })
  }

  @Test
  fun `songs without an album are in no album`() {
    val tracks = listOf(t("a", album = ""), t("b", album = "  "), t("c", album = "X"))
    assertEquals(listOf("c"), LibraryGroups.albums(tracks).flatMap { it.tracks }.map { it.id })
  }

  @Test
  fun `the same album id is one album and different ids are two`() {
    val tracks = listOf(t("a", album = "Live", albumId = 1), t("b", album = "Live", albumId = 2), t("c", album = "Live", albumId = 1))
    val albums = LibraryGroups.albums(tracks)
    assertEquals(2, albums.size)
    assertEquals(setOf(2, 1), albums.map { it.tracks.size }.toSet())
  }

  @Test
  fun `without an id, albums with the same name and different case or accents are one`() {
    val tracks = listOf(t("a", album = "Ação"), t("b", album = " acao "))
    assertEquals(1, LibraryGroups.albums(tracks).size)
  }

  @Test
  fun `an album with several artists has no single artist`() {
    val one = LibraryGroups.albums(listOf(t("a", artist = "X", albumId = 1), t("b", artist = "x", albumId = 1))).single()
    assertEquals("X", one.artist)
    val many = LibraryGroups.albums(listOf(t("a", artist = "X", albumId = 1), t("b", artist = "Y", albumId = 1))).single()
    assertNull(many.artist)
  }

  @Test
  fun `the album year is the first one a song has`() {
    val album = LibraryGroups.albums(listOf(t("a", albumId = 1, year = ""), t("b", albumId = 1, year = "1999"))).single()
    assertEquals(1999, album.year)
    assertEquals(0, LibraryGroups.albums(listOf(t("a", albumId = 1))).single().year)
  }

  @Test
  fun `lists are sorted by name without case, accents or a leading The`() {
    val tracks = listOf(t("1", artist = "The Zebras"), t("2", artist = "ábaco"), t("3", artist = "Mango"))
    assertEquals(listOf("ábaco", "Mango", "The Zebras"), LibraryGroups.artists(tracks).map { it.name })
  }

  @Test
  fun `an artist counts its albums and keeps the common spelling`() {
    val tracks = listOf(
      t("a", artist = "Beatles", album = "One", albumId = 1), t("b", artist = "Beatles", album = "One", albumId = 1),
      t("c", artist = "beatles", album = "Two", albumId = 2), t("d", artist = "Beatles", album = "")
    )
    val artist = LibraryGroups.artists(tracks).single()
    assertEquals("Beatles", artist.name)
    assertEquals(2, artist.albumCount)
    assertEquals(4, artist.tracks.size)
  }

  @Test
  fun `genres leave out songs without one and join spellings`() {
    val tracks = listOf(t("a", genre = "Rock"), t("b", genre = "rock "), t("c", genre = ""), t("d", genre = "Jazz"))
    val genres = LibraryGroups.genres(tracks)
    assertEquals(listOf("Jazz", "Rock"), genres.map { it.name })
    assertEquals(2, genres.last().tracks.size)
    assertTrue(genres.flatMap { it.tracks }.none { it.id == "c" })
  }

  @Test
  fun `no songs give no groups`() {
    assertTrue(LibraryGroups.albums(emptyList()).isEmpty())
    assertTrue(LibraryGroups.artists(emptyList()).isEmpty())
    assertTrue(LibraryGroups.genres(emptyList()).isEmpty())
  }
}
