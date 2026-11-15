package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.PlayStatEntity
import com.example.data.playback.EndReason
import com.example.data.playback.PlayRules
import com.example.data.playback.PlayStatsStore
import com.example.data.playback.PlaybackStateStore
import com.example.data.playback.TrackStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** P12: the play counters move to the database with the same values; what cannot be read is left out alone. */
@RunWith(RobolectricTestRunner::class)
class PlayStatsMigrationTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var store: PlayStatsStore

  private val old get() = context.getSharedPreferences(PlayStatsStore.FILE, Context.MODE_PRIVATE)
  private val flags get() = context.getSharedPreferences(PlaybackStateStore.FLAGS_FILE, Context.MODE_PRIVATE)

  @Before fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    old.edit().clear().commit()
    flags.edit().clear().commit()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    store = PlayStatsStore(db.playStats(), Dispatchers.IO)
  }

  @After fun tearDown() = db.close()

  private fun migrate() = runBlocking { store.migrateFrom(old, flags, db) }

  @Test fun valuesAreEqualBeforeAndAfter() = runBlocking {
    val before = mapOf(
      "1" to TrackStats(5, 2, 1_700_000_000_000L, 1_699_000_000_000L),
      "2" to TrackStats(0, 9, 0L, 1_650_000_000_000L),
      "content://x/3" to TrackStats(12, 0, 1_720_000_000_000L, 0L)
    )
    old.edit().apply { before.forEach { (k, v) -> putString(k, PlayRules.encode(v)) } }.commit()
    migrate()
    assertEquals(before, store.all())
    assertEquals(mapOf("1" to 5, "2" to 0, "content://x/3" to 12), store.allPlays())
  }

  @Test fun malformedEntryIsIgnoredAndTheOthersStay() = runBlocking {
    old.edit()
      .putString("ok", "3|1|10|20")
      .putString("bad1", "3|x|10|20")
      .putString("bad2", "only|three|parts")
      .putString("bad3", "")
      .putString("ok2", "1|0|5|0")
      .commit()
    migrate()
    assertEquals(setOf("ok", "ok2"), store.all().keys)
    assertEquals(TrackStats(3, 1, 10, 20), store.get("ok"))
    assertTrue(flags.getBoolean(PlayStatsStore.MIGRATED, false))
  }

  @Test fun oldFileStaysIntact() {
    old.edit().putString("1", "4|0|7|0").putString("bad", "zz").commit()
    migrate()
    assertEquals("4|0|7|0", old.getString("1", null))
    assertEquals("zz", old.getString("bad", null))
    assertEquals(2, old.all.size)
  }

  @Test fun migratedOnlyOnce() = runBlocking {
    old.edit().putString("1", "4|0|7|0").commit()
    migrate()
    old.edit().putString("1", "99|0|7|0").putString("2", "1|1|1|1").commit()
    migrate()
    assertEquals(setOf("1"), store.all().keys)
    assertEquals(4, store.get("1").plays)
  }

  @Test fun rowsAlreadyInTheDatabaseAreNotOverwritten() = runBlocking {
    db.playStats().upsert(PlayStatEntity("1", 10, 0, 1, 0))
    old.edit().putString("1", "4|0|7|0").putString("2", "2|0|3|0").commit()
    migrate()
    assertEquals(10, store.get("1").plays)
    assertEquals(2, store.get("2").plays)
  }

  @Test fun nothingToMigrateIsStillMarkedDone() = runBlocking {
    migrate()
    assertTrue(store.all().isEmpty())
    assertTrue(flags.getBoolean(PlayStatsStore.MIGRATED, false))
  }

  @Test fun recordAppliesThePlayRules() = runBlocking {
    val after = store.record("t", EndReason.NATURAL, 1000, 1000, nowMs = 50L)
    assertEquals(TrackStats(1, 0, 50L, 0L), after)
    val skipped = store.record("t", EndReason.SKIP_FORWARD, 5_000, 200_000, nowMs = 60L)
    assertEquals(TrackStats(1, 1, 50L, 60L), skipped)
    assertEquals(skipped, store.get("t"))
  }

  @Test fun anEndThatCountsAsNothingWritesNothing() = runBlocking {
    store.record("t", EndReason.STOPPED, 60_000, 400_000, nowMs = 5L)
    assertTrue(store.all().isEmpty())
  }

  @Test fun concurrentEndsDoNotLoseACount() = runBlocking {
    (1..40).map { async(Dispatchers.IO) { store.record("t", EndReason.NATURAL, 1, 1, nowMs = it.toLong()) } }.awaitAll()
    assertEquals(40, store.get("t").plays)
  }
}
