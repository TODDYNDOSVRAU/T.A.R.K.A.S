package com.example

import com.example.data.model.Track
import com.example.data.playback.LibrarySearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Search in the song lists of Biblioteca: title, artist and album, any case or accent, order kept. */
class LibrarySearchTest {

  private fun t(id: String, title: String, artist: String, album: String) =
    Track(id = id, title = title, artist = artist, album = album, durationMs = 1000, genre = "", era = "")

  private val songs = listOf(
    t("1", "Help!", "The Beatles", "Help!"),
    t("2", "Coração Vagabundo", "Caetano Veloso", "Caetano Veloso"),
    t("3", "Ação e Reação", "O Rappa", "Lado B Lado A"),
    t("4", "Yesterday", "The Beatles", "Help!"),
    t("5", "Sem álbum", "Fulano", "")
  )

  @Test
  fun matchesTitleArtistAndAlbum() {
    assertEquals(listOf("2"), LibrarySearch.filter(songs, "vagabundo").map { it.id })
    assertEquals(listOf("1", "4"), LibrarySearch.filter(songs, "beatles").map { it.id })
    assertEquals(listOf("3"), LibrarySearch.filter(songs, "lado b").map { it.id })
  }

  @Test
  fun ignoresCaseAndAccents() {
    assertEquals(listOf("2"), LibrarySearch.filter(songs, "CORACAO").map { it.id })
    assertEquals(listOf("3"), LibrarySearch.filter(songs, "reacao").map { it.id })
    assertEquals(listOf("5"), LibrarySearch.filter(songs, "ALBUM").map { it.id })
  }

  @Test
  fun everyWordHasToMatch() {
    assertEquals(listOf("4"), LibrarySearch.filter(songs, "beatles yesterday").map { it.id })
    assertEquals(emptyList<String>(), LibrarySearch.filter(songs, "beatles vagabundo").map { it.id })
  }

  @Test
  fun keepsTheOrderOfTheList() {
    val reversed = songs.reversed()
    assertEquals(listOf("4", "1"), LibrarySearch.filter(reversed, "help").map { it.id })
  }

  @Test
  fun aBlankQueryReturnsTheListItself() {
    assertSame(songs, LibrarySearch.filter(songs, ""))
    assertSame(songs, LibrarySearch.filter(songs, "   "))
  }

  @Test
  fun noMatchGivesAnEmptyList() {
    assertEquals(emptyList<Track>(), LibrarySearch.filter(songs, "xyzzy"))
  }

  @Test
  fun usesTheHaystackTheCallerKeeps() {
    var calls = 0
    val result = LibrarySearch.filter(songs, "yesterday") { calls++; LibrarySearch.haystack(it) }
    assertEquals(listOf("4"), result.map { it.id })
    assertEquals(songs.size, calls)
  }
}
