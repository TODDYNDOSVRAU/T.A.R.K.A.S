package com.example

import com.example.data.model.Track
import com.example.data.model.TrackJson
import com.example.data.playback.PlaybackRestore
import com.example.data.playback.RestoreOutcome
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reading the saved playback state: only an unreadable current track loses everything. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackRestoreTest {

  private fun track(id: String, demo: Boolean = false) = Track(
    id = id, title = "Title $id", artist = "Artist", album = "Album", durationMs = 180_000L,
    genre = "", era = "", isLocalFile = true, filePath = "/music/$id.mp3", isDemo = demo
  )

  private fun state(
    current: JSONObject? = TrackJson.toJson(track("a")),
    queue: JSONArray? = TrackJson.listToJson(listOf(track("b"), track("c"))),
    context: JSONArray? = null
  ): JSONObject = JSONObject().apply {
    current?.let { put("current", it) }
    queue?.let { put("queue", it) }
    context?.let { put("context", it) }
  }

  private fun restored(outcome: RestoreOutcome) = (outcome as RestoreOutcome.Restored).state

  @Test fun nothing_saved_is_not_a_problem() {
    assertEquals(RestoreOutcome.Nothing, PlaybackRestore.read(null, null, null))
  }

  @Test fun valid_state_comes_back_whole() {
    val raw = state(context = TrackJson.listToJson(listOf(track("a"), track("b"), track("c")))).toString()
    val s = restored(PlaybackRestore.read(raw, "a", 42_000L))
    assertEquals("a", s.track.id)
    assertEquals(listOf("b", "c"), s.queue.map { it.id })
    assertEquals(listOf("a", "b", "c"), s.context!!.map { it.id })
    assertEquals(42_000L, s.positionMs)
    assertTrue(s.problems.isEmpty())
  }

  @Test fun position_of_another_track_is_not_used() {
    val s = restored(PlaybackRestore.read(state().toString(), "other", 42_000L))
    assertEquals(0L, s.positionMs)
  }

  @Test fun old_save_keeps_position_inside_the_json() {
    val raw = state().put("pos", 7_000L).toString()
    assertEquals(7_000L, restored(PlaybackRestore.read(raw, null, null)).positionMs)
  }

  @Test fun corrupt_json_is_unreadable() {
    val out = PlaybackRestore.read("{\"current\": {\"id\": ", null, null)
    assertTrue(out is RestoreOutcome.Unreadable)
  }

  @Test fun missing_current_is_unreadable() {
    val out = PlaybackRestore.read(state(current = null).toString(), "a", 1L)
    assertTrue(out is RestoreOutcome.Unreadable)
  }

  @Test fun current_missing_a_field_is_unreadable() {
    val broken = TrackJson.toJson(track("a")).apply { remove("title") }
    assertTrue(PlaybackRestore.read(state(current = broken).toString(), "a", 1L) is RestoreOutcome.Unreadable)
  }

  @Test fun reason_has_no_title_or_path() {
    val broken = TrackJson.toJson(track("a")).apply { remove("album") }
    val reason = (PlaybackRestore.read(state(current = broken).toString(), "a", 1L) as RestoreOutcome.Unreadable).reason
    assertFalse(reason.contains("Title"))
    assertFalse(reason.contains("/music"))
  }

  @Test fun invalid_item_in_the_middle_of_the_queue_is_dropped_alone() {
    val queue = JSONArray()
      .put(TrackJson.toJson(track("b")))
      .put(TrackJson.toJson(track("x")).apply { remove("artist") })
      .put(TrackJson.toJson(track("c")))
    val s = restored(PlaybackRestore.read(state(queue = queue).toString(), "a", 5_000L))
    assertEquals("a", s.track.id)
    assertEquals(5_000L, s.positionMs)
    assertEquals(listOf("b", "c"), s.queue.map { it.id })
    assertEquals(1, s.problems.size)
    assertFalse(s.problems[0].contains("Title"))
  }

  @Test fun unreadable_queue_keeps_track_and_position() {
    val raw = state(queue = null).put("queue", "garbage").toString()
    val s = restored(PlaybackRestore.read(raw, "a", 9_000L))
    assertEquals("a", s.track.id)
    assertEquals(9_000L, s.positionMs)
    assertTrue(s.queue.isEmpty())
    assertEquals(1, s.problems.size)
  }

  @Test fun unreadable_context_is_dropped_but_queue_stays() {
    val raw = state().put("context", 12).toString()
    val s = restored(PlaybackRestore.read(raw, "a", 0L))
    assertNull(s.context)
    assertEquals(listOf("b", "c"), s.queue.map { it.id })
    assertEquals(1, s.problems.size)
  }

  @Test fun absent_queue_is_not_a_problem() {
    val s = restored(PlaybackRestore.read(state(queue = null).toString(), "a", 0L))
    assertTrue(s.queue.isEmpty())
    assertTrue(s.problems.isEmpty())
  }

  @Test fun demo_track_and_removed_integration_are_quiet() {
    val demo = state(current = TrackJson.toJson(track("d", demo = true))).toString()
    assertEquals(RestoreOutcome.Nothing, PlaybackRestore.read(demo, "d", 0L))
    val web = state(current = TrackJson.toJson(track("w")).put("web", "x")).toString()
    assertEquals(RestoreOutcome.Nothing, PlaybackRestore.read(web, "w", 0L))
  }
}
