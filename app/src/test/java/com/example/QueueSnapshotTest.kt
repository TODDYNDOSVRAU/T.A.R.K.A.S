package com.example

import com.example.data.model.Track
import com.example.data.playback.QueueSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/** P3: the list saved from the queue keeps order and repeats. */
class QueueSnapshotTest {
  private fun t(id: String, demo: Boolean = false) =
    Track(id, id, "a", "al", 1000, "g", "e", isLocalFile = true, filePath = "content://x/$id", isDemo = demo)

  @Test fun currentThenQueueThenUpcoming() {
    assertEquals(listOf("c", "q1", "q2", "u1"), QueueSnapshot.tracks(t("c"), listOf(t("q1"), t("q2")), listOf(t("u1"))).map { it.id })
  }

  @Test fun repeatsStayInPlace() {
    assertEquals(listOf("a", "b", "a", "a"), QueueSnapshot.tracks(t("a"), listOf(t("b"), t("a")), listOf(t("a"))).map { it.id })
  }

  @Test fun noCurrentStillSavesTheRest() {
    assertEquals(listOf("q"), QueueSnapshot.tracks(null, listOf(t("q")), emptyList()).map { it.id })
  }

  @Test fun emptyIsEmpty() {
    assertEquals(emptyList<Track>(), QueueSnapshot.tracks(null, emptyList(), emptyList()))
  }

  @Test fun demoSongsAreLeftOut() {
    assertEquals(listOf("q"), QueueSnapshot.tracks(t("d", demo = true), listOf(t("q")), emptyList()).map { it.id })
  }
}
