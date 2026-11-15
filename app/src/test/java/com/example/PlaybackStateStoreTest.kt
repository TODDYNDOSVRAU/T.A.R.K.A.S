package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.PlaybackStateEntity
import com.example.data.playback.PlaybackRestore
import com.example.data.playback.PlaybackStateStore
import com.example.data.playback.RestoreOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** P12: the saved player state moves from preferences to the database, once, without losing or inventing anything. */
@RunWith(RobolectricTestRunner::class)
class PlaybackStateStoreTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var store: PlaybackStateStore

  private val old get() = context.getSharedPreferences(PlaybackStateStore.OLD_FILE, Context.MODE_PRIVATE)
  private val flags get() = context.getSharedPreferences(PlaybackStateStore.FLAGS_FILE, Context.MODE_PRIVATE)

  @Before fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    old.edit().clear().commit()
    flags.edit().clear().commit()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    store = PlaybackStateStore(db.playback(), Dispatchers.IO)
  }

  @After fun tearDown() = db.close()

  private fun track(id: String) =
    """{"id":"$id","title":"t","artist":"a","album":"al","durationMs":1000,"genre":"g","extension":"e","isLocal":true,"path":"/x/$id"}"""

  private fun json(current: String = track("a"), queue: String = "[${track("b")}]") =
    """{"current":$current,"queue":$queue}"""

  private fun seed(state: String?, pos: Long? = 4200L, owner: String? = "a") {
    val e = old.edit()
    if (state != null) e.putString("state", state)
    if (pos != null) e.putLong("pos", pos)
    if (owner != null) e.putString("pos_track", owner)
    e.commit()
  }

  private fun migrateAndRead() = runBlocking {
    store.migrateFrom(old, flags)
    store.load()
  }

  private fun outcome(row: PlaybackStateEntity?) =
    PlaybackRestore.read(row?.stateJson, row?.positionTrackId, row?.positionMs?.takeIf { it >= 0 })

  @Test fun realStateIsCopiedAsItWas() {
    val raw = json()
    seed(raw)
    val row = migrateAndRead()!!
    assertEquals(raw, row.stateJson)
    assertEquals(4200L, row.positionMs)
    assertEquals("a", row.positionTrackId)
    val restored = (outcome(row) as RestoreOutcome.Restored).state
    assertEquals("a", restored.track.id)
    assertEquals(4200L, restored.positionMs)
    assertEquals(listOf("b"), restored.queue.map { it.id })
  }

  @Test fun oldFileStaysIntactAndFlagIsKeptApart() {
    val raw = json()
    seed(raw)
    migrateAndRead()
    assertEquals(raw, old.getString("state", null))
    assertEquals(4200L, old.getLong("pos", 0))
    assertEquals("a", old.getString("pos_track", null))
    assertTrue(flags.getBoolean(PlaybackStateStore.MIGRATED, false))
    assertTrue(!old.contains(PlaybackStateStore.MIGRATED))
  }

  @Test fun nothingSavedMeansNoRow() {
    assertNull(migrateAndRead())
    assertTrue(flags.getBoolean(PlaybackStateStore.MIGRATED, false))
  }

  @Test fun truncatedJsonIsCopiedButReadsAsUnreadable() {
    seed(json().take(40))
    assertTrue(outcome(migrateAndRead()) is RestoreOutcome.Unreadable)
  }

  @Test fun currentWithoutIdIsUnreadable() {
    seed(json(current = """{"title":"t"}"""))
    assertTrue(outcome(migrateAndRead()) is RestoreOutcome.Unreadable)
  }

  @Test fun queueWithAnInvalidItemKeepsTheValidOnes() {
    seed(json(queue = """[${track("b")},{"title":"broken"},${track("c")}]"""))
    val state = (outcome(migrateAndRead()) as RestoreOutcome.Restored).state
    assertEquals(listOf("b", "c"), state.queue.map { it.id })
    assertTrue(state.problems.isNotEmpty())
  }

  @Test fun positionOfAnotherTrackIsNotUsed() {
    seed(json(), pos = 7777L, owner = "zzz")
    val state = (outcome(migrateAndRead()) as RestoreOutcome.Restored).state
    assertEquals("a", state.track.id)
    assertEquals(0L, state.positionMs)
  }

  @Test fun clearedStateIsNotImportedAgain() = runBlocking {
    seed(json())
    store.migrateFrom(old, flags)
    store.clear()
    store.migrateFrom(old, flags)
    assertNull(store.load())
  }

  @Test fun anExistingRowIsNotReplaced() = runBlocking {
    store.save(json(current = track("new")), 5L, "new")
    seed(json())
    store.migrateFrom(old, flags)
    assertEquals("new", store.load()!!.positionTrackId)
  }

  @Test fun lastPositionSavedWins() = runBlocking {
    store.save(json(), 0L, "a")
    for (i in 1..500) store.savePosition(i * 10L, "a")
    store.flush()
    assertEquals(5000L, store.load()!!.positionMs)
  }

  @Test fun aReadSeesEverythingAskedBeforeIt() = runBlocking {
    store.save(json(), 1L, "a")
    store.savePosition(2L, "a")
    store.save(json(), 3L, "a")
    store.savePosition(4L, "a")
    assertEquals(4L, store.load()!!.positionMs)
  }

  @Test fun positionAloneDoesNotCreateAState() = runBlocking {
    store.savePosition(10L, "a")
    store.flush()
    assertNull(store.load())
  }
}
