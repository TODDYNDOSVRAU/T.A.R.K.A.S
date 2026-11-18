package com.example

import com.example.data.audio.loudness.LoudnessNormalization
import com.example.data.audio.loudness.LoudnessSource
import com.example.data.audio.loudness.NormalizationMode
import com.example.data.audio.loudness.TaggedLoudness
import com.example.data.model.Track
import com.example.data.playback.AlbumOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When tracks follow each other on an album, and the automatic volume mode built on it. */
class AlbumOrderTest {

  private fun t(id: String, album: Long?, disc: Int, n: Int) =
    Track(id = id, title = id, artist = "A", album = "Disco", durationMs = 1000, genre = "", era = "", albumId = album, discNumber = disc, trackNumber = n)

  @Test
  fun theMediaLibraryTrackColumnHoldsDiscAndTrack() {
    assertEquals(2 to 5, AlbumOrder.discAndTrack(2005))
    assertEquals(0 to 7, AlbumOrder.discAndTrack(7))
    assertEquals(1 to 12, AlbumOrder.discAndTrack(1012))
    assertEquals(0 to 0, AlbumOrder.discAndTrack(0))
  }

  @Test
  fun theNextTrackOfTheSameDiscFollows() {
    assertTrue(AlbumOrder.follows(t("a", 9, 1, 3), t("b", 9, 1, 4)))
    assertFalse(AlbumOrder.follows(t("a", 9, 1, 3), t("b", 9, 1, 5)))   // skipped one
    assertFalse(AlbumOrder.follows(t("a", 9, 1, 3), t("b", 9, 2, 4)))   // other disc
    assertFalse(AlbumOrder.follows(t("a", 9, 1, 3), t("b", 8, 1, 4)))   // other album
    assertFalse(AlbumOrder.follows(t("a", null, 0, 3), t("b", null, 0, 4))) // album unknown
    assertFalse(AlbumOrder.follows(t("a", 9, 0, 0), t("b", 9, 0, 0)))   // numbers unknown
    assertTrue(AlbumOrder.follows(t("a", 9, 0, 1), t("b", 9, 0, 2)))    // no disc numbers at all
  }

  @Test
  fun theFirstTrackOfAnAlbumPlayedInOrderCountsToo() {
    val one = t("1", 9, 1, 1)
    val two = t("2", 9, 1, 2)
    assertTrue(AlbumOrder.inAlbumSequence(null, one, two))
    assertTrue(AlbumOrder.inAlbumSequence(one, two, null))
    assertFalse(AlbumOrder.inAlbumSequence(null, one, t("x", 3, 1, 7)))
  }

  private fun song(title: String, artist: String = "X", album: String = "", n: Int = 0, added: Long = 0) =
    Track(id = title, title = title, artist = artist, album = album, durationMs = 1000, genre = "", era = "", trackNumber = n, dateAddedSec = added)

  @Test
  fun songsSortAsPeopleReadThem() {
    val list = listOf(song("Zebra"), song("The Beatles Song"), song("ágape"), song("123"), song("Banana"))
    val titles = com.example.data.playback.LibrarySort.sorted(list, com.example.data.playback.SongOrder.TITLE).map { it.title }
    assertEquals(listOf("123", "ágape", "Banana", "The Beatles Song", "Zebra"), titles)
    assertEquals("B", com.example.data.playback.LibrarySort.section(song("The Beatles Song"), com.example.data.playback.SongOrder.TITLE))
    assertEquals("A", com.example.data.playback.LibrarySort.section(song("Ágape"), com.example.data.playback.SongOrder.TITLE))
    assertEquals("#", com.example.data.playback.LibrarySort.section(song("123"), com.example.data.playback.SongOrder.TITLE))
  }

  @Test
  fun anAlbumKeepsItsTrackOrderAndRecentPutsNewestFirst() {
    val list = listOf(song("C", album = "Disco", n = 3), song("A", album = "Disco", n = 1, added = 50), song("B", album = "Disco", n = 2, added = 99))
    assertEquals(listOf("A", "B", "C"), com.example.data.playback.LibrarySort.sorted(list, com.example.data.playback.SongOrder.ALBUM).map { it.title })
    assertEquals(listOf("B", "A", "C"), com.example.data.playback.LibrarySort.sorted(list, com.example.data.playback.SongOrder.RECENT).map { it.title })
  }

  @Test
  fun automaticUsesTheAlbumValueOnlyForAnAlbumInOrder() {
    val tags = TaggedLoudness(trackLufs = -9.0, albumLufs = -11.0)
    val inOrder = LoudnessNormalization.decide(NormalizationMode.AUTO, tags, null, albumInOrder = true)
    assertEquals(LoudnessSource.TAG_ALBUM, inOrder.source)
    assertEquals(-11.0, inOrder.loudnessLufs!!, 1e-9)
    val mixed = LoudnessNormalization.decide(NormalizationMode.AUTO, tags, null, albumInOrder = false)
    assertEquals(LoudnessSource.TAG_TRACK, mixed.source)
    // No album value in the file: the track's own, even in order.
    val noAlbum = LoudnessNormalization.decide(NormalizationMode.AUTO, TaggedLoudness(trackLufs = -9.0), null, albumInOrder = true)
    assertEquals(LoudnessSource.TAG_TRACK, noAlbum.source)
  }
}
