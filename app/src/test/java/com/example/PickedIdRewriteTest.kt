package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.playback.PickedIdRewrite
import com.example.data.playback.PlayStatsStore
import com.example.data.playback.PlaybackStateStore
import com.example.data.playback.TrackStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** P12: ids of picked files that changed (local_<ms> to local_<hash>) are rewritten once in stats and saved state. */
@RunWith(RobolectricTestRunner::class)
class PickedIdRewriteTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var stats: PlayStatsStore
  private lateinit var state: PlaybackStateStore

  private val flags get() = context.getSharedPreferences(PlaybackStateStore.FLAGS_FILE, Context.MODE_PRIVATE)
  private val map = mapOf("local_1" to "local_aaa", "local_2" to "local_bbb")

  @Before fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    flags.edit().clear().commit()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    stats = PlayStatsStore(db.playStats(), Dispatchers.IO)
    state = PlaybackStateStore(db.playback(), Dispatchers.IO)
  }

  @After fun tearDown() = db.close()

  private fun track(id: String) =
    """{"id":"$id","title":"t","artist":"a","album":"al","durationMs":1000,"genre":"g","extension":"e","isLocal":true,"path":"/x/$id"}"""

  private fun ids(raw: String): Triple<String, List<String>, List<String>> {
    val o = JSONObject(raw)
    fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("id") } } ?: emptyList()
    return Triple(o.getJSONObject("current").getString("id"), list("queue"), list("context"))
  }

  private fun statsRows(vararg rows: Pair<String, TrackStats>) = runBlocking {
    rows.forEach { (id, st) -> db.playStats().upsert(com.example.data.db.PlayStatEntity(id, st.plays, st.skips, st.lastPlayedMs, st.lastSkippedMs)) }
  }

  @Test fun statsMoveToTheNewIdAndNothingIsLost() = runBlocking {
    statsRows("local_1" to TrackStats(3, 1, 10, 20), "other" to TrackStats(7, 0, 5, 0))
    stats.remapIds(map, flags, db)
    assertEquals(mapOf("local_aaa" to TrackStats(3, 1, 10, 20), "other" to TrackStats(7, 0, 5, 0)), stats.all())
  }

  @Test fun statsAreAddedWhenTheNewIdAlreadyExists() = runBlocking {
    statsRows("local_1" to TrackStats(3, 1, 10, 50), "local_aaa" to TrackStats(2, 4, 30, 20))
    stats.remapIds(map, flags, db)
    assertEquals(mapOf("local_aaa" to TrackStats(5, 5, 30, 50)), stats.all())
  }

  @Test fun statsSecondRunChangesNothing() = runBlocking {
    statsRows("local_1" to TrackStats(3, 1, 10, 20))
    stats.remapIds(map, flags, db)
    val first = stats.all()
    statsRows("local_1" to TrackStats(9, 9, 9, 9))
    stats.remapIds(map, flags, db)
    assertEquals(first + ("local_1" to TrackStats(9, 9, 9, 9)), stats.all())
  }

  @Test fun savedStateGetsTheNewIdsEverywhere() = runBlocking {
    val raw = """{"current":${track("local_1")},"queue":[${track("local_2")},${track("keep")}],"context":[${track("local_1")},${track("local_2")}]}"""
    state.save(raw, 1234L, "local_1")
    state.remapIds(map, flags)
    val row = state.load()!!
    assertEquals(Triple("local_aaa", listOf("local_bbb", "keep"), listOf("local_aaa", "local_bbb")), ids(row.stateJson))
    assertEquals("local_aaa", row.positionTrackId)
    assertEquals(1234L, row.positionMs)
    assertEquals("t", JSONObject(row.stateJson).getJSONObject("current").getString("title"))
  }

  @Test fun savedStateSecondRunChangesNothing() = runBlocking {
    state.save("""{"current":${track("local_1")},"queue":[]}""", 5L, "local_1")
    state.remapIds(map, flags)
    val first = state.load()
    // Something new saved under an id that is in the map again is not touched: the marker is set.
    state.save("""{"current":${track("local_1")},"queue":[]}""", 6L, "local_1")
    state.remapIds(map, flags)
    assertEquals("local_1", state.load()!!.positionTrackId)
    assertEquals("local_aaa", first!!.positionTrackId)
  }

  @Test fun emptyMapMarksDoneAndChangesNothing() = runBlocking {
    val raw = """{"current":${track("local_1")},"queue":[]}"""
    state.save(raw, 5L, "local_1")
    statsRows("local_1" to TrackStats(1, 0, 1, 0))
    state.remapIds(emptyMap(), flags)
    stats.remapIds(emptyMap(), flags, db)
    assertEquals(raw, state.load()!!.stateJson)
    assertEquals(setOf("local_1"), stats.all().keys)
    assertTrue(flags.getBoolean(PickedIdRewrite.DONE + ".state", false))
    assertTrue(flags.getBoolean(PickedIdRewrite.DONE + ".stats", false))
  }

  @Test fun unreadableStateIsLeftAsItWas() {
    assertEquals("{broken", PickedIdRewrite.state("{broken", map))
  }

  @Test fun noRowIsFine() = runBlocking {
    state.remapIds(map, flags)
    assertEquals(null, state.load())
  }
}
