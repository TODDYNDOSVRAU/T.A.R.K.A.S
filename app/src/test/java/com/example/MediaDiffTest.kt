package com.example

import com.example.data.library.DeviceCatalog
import com.example.data.library.MediaDiff
import com.example.data.library.ScannedSong
import com.example.data.library.Stamp
import com.example.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDiffTest {
  private fun song(id: Long, title: String, folder: String = "Music/", stamp: Stamp = Stamp(1, 1)) = ScannedSong(
    id, Track(id = "ms:$id", title = title, artist = "", album = "", durationMs = 1, genre = "", era = ""), folder, stamp
  )

  private val byTitle = Comparator<Track> { a, b -> a.title.compareTo(b.title) }

  @Test
  fun `nothing changed is an empty diff`() {
    val stamps = mapOf(1L to Stamp(5, 1), 2L to Stamp(6, 1))
    assertTrue(MediaDiff.of(stamps, stamps).isEmpty)
  }

  @Test
  fun `added, changed and removed are told apart`() {
    val known = mapOf(1L to Stamp(5, 1), 2L to Stamp(6, 1), 3L to Stamp(7, 1))
    val now = mapOf(1L to Stamp(5, 1), 2L to Stamp(9, 1), 4L to Stamp(1, 1))
    val diff = MediaDiff.of(known, now)
    assertEquals(setOf(4L), diff.added)
    assertEquals(setOf(2L), diff.changed)
    assertEquals(setOf(3L), diff.removed)
    assertEquals(setOf(2L, 4L), diff.toRead)
  }

  @Test
  fun `a new generation alone is a change`() {
    val diff = MediaDiff.of(mapOf(1L to Stamp(5, 1)), mapOf(1L to Stamp(5, 2)))
    assertEquals(setOf(1L), diff.changed)
  }

  @Test
  fun `the first read and a big change read everything`() {
    assertTrue(MediaDiff.of(emptyMap(), mapOf(1L to Stamp(1, 1))).readsAll(known = 0))
    assertFalse(MediaDiff(setOf(1L), emptySet(), emptySet()).readsAll(known = 1000))
    assertTrue(MediaDiff((1L..401L).toSet(), emptySet(), emptySet()).readsAll(known = 1000))
  }

  @Test
  fun `applying a diff touches only what changed and keeps the order`() {
    val first = DeviceCatalog().apply(
      MediaDiff.of(emptyMap(), mapOf(1L to Stamp(1, 1), 2L to Stamp(1, 1), 3L to Stamp(1, 1))),
      listOf(song(1, "b"), song(2, "c"), song(3, "a", "Other/")), byTitle
    )
    assertEquals(listOf("a", "b", "c"), first.tracks.map { it.title })
    assertEquals(mapOf("Music/" to 2, "Other/" to 1), first.folderCounts)

    val next = mapOf(1L to Stamp(1, 1), 2L to Stamp(2, 1), 4L to Stamp(1, 1)) // 3 gone, 2 changed, 4 new
    val second = first.apply(MediaDiff.of(first.stamps, next), listOf(song(2, "z", stamp = Stamp(2, 1)), song(4, "a2")), byTitle)
    assertEquals(listOf("a2", "b", "z"), second.tracks.map { it.title })
    assertEquals(next, second.stamps)
    assertEquals(mapOf("Music/" to 3), second.folderCounts)
  }

  @Test
  fun `a song the query did not return keeps its old stamp so it is read again`() {
    val first = DeviceCatalog().apply(MediaDiff.of(emptyMap(), mapOf(1L to Stamp(1, 1))), listOf(song(1, "a")), byTitle)
    val now = mapOf(1L to Stamp(2, 1), 2L to Stamp(1, 1))
    val second = first.apply(MediaDiff.of(first.stamps, now), emptyList(), byTitle) // nothing could be read
    assertEquals(first.stamps, second.stamps)
    assertEquals(listOf("a"), second.tracks.map { it.title })
    assertEquals(setOf(1L, 2L), MediaDiff.of(second.stamps, now).toRead)
  }
}
