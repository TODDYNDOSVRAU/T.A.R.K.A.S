package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.RestoreException
import com.example.data.backup.UserDataBackup
import com.example.data.db.AppDatabase
import com.example.data.db.FavoriteEntity
import com.example.data.db.PlayStatEntity
import com.example.data.db.PlaybackStateEntity
import com.example.data.db.PlaylistEntity
import com.example.data.db.SettingEntity
import com.example.data.db.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S2: export, wipe, restore, compare. A bad file changes nothing. */
@RunWith(RobolectricTestRunner::class)
class UserDataBackupTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var backup: UserDataBackup

  @Before fun open() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    backup = UserDataBackup(db) { context.getSharedPreferences("test_$it", Context.MODE_PRIVATE) }
    for (s in UserDataBackup.PREFS_SCOPES) context.getSharedPreferences("test_$s", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @After fun close() = db.close()

  private fun track(id: String, picked: Boolean = false) = TrackEntity(
    id, "t $id", "a", "al", 1000, "g", "e", true, if (picked) "/x/$id" else null, "#112233", null, false, 7, 2, 3, 99, picked
  )

  private fun prefs(scope: String) = context.getSharedPreferences("test_$scope", Context.MODE_PRIVATE)

  private suspend fun fill() {
    db.tracks().upsert(listOf(track("a", true), track("b")))
    db.favorites().add(FavoriteEntity("a", 5))
    db.favorites().add(FavoriteEntity("b", 9))
    db.playlists().insert(PlaylistEntity("p1", "One", 1))
    db.playlists().setTracks("p1", listOf("b", "a", "b"))
    db.playStats().upsert(PlayStatEntity("a", 3, 1, 10, 20))
    db.playback().put(PlaybackStateEntity(0, "{\"q\":[\"a\"]}", 4321, "a"))
    db.settings().put(SettingEntity("x", "k", "v"))
    prefs("tarkas_prefs").edit().putBoolean("amoled", true).putInt("n", 3).putLong("l", 1L shl 40)
      .putFloat("f", 0.5f).putString("s", "oi").putStringSet("set", setOf("b", "a")).commit()
    prefs("tarkas_loudness").edit().putString("mode", "track").commit()
  }

  private suspend fun snapshot() = backup.export("1", "t").toString()

  @Test fun exportWipeRestoreGivesTheSameData() = runBlocking {
    fill()
    val before = snapshot()
    db.clearAllTables()
    for (s in UserDataBackup.PREFS_SCOPES) prefs(s).edit().clear().commit()
    backup.restore(before)
    assertEquals(before, snapshot())
    assertEquals(listOf("b", "a", "b"), db.playlists().entries().map { it.trackId })
    assertEquals(listOf("b", "a"), db.favorites().all())
    assertEquals(1L shl 40, prefs("tarkas_prefs").getLong("l", 0))
    assertEquals(setOf("a", "b"), prefs("tarkas_prefs").getStringSet("set", null))
  }

  @Test fun restoreReplacesWhatWasThere() = runBlocking {
    fill()
    val saved = snapshot()
    db.favorites().add(FavoriteEntity("zzz", 100))
    prefs("tarkas_prefs").edit().putString("extra", "x").commit()
    backup.restore(saved)
    assertEquals(saved, snapshot())
  }

  @Test fun anInvalidFileChangesNothing() = runBlocking {
    fill()
    val before = snapshot()
    val broken = listOf(
      "not json",
      "{}",
      "{\"app\":\"T.A.R.K.A.S.\",\"format\":99}",
      before.replace("\"tracks\":[", "\"tracks\":[{\"id\":1},")
    )
    for (text in broken) {
      val failed = try { backup.restore(text); false } catch (e: RestoreException) { true }
      assertTrue("should refuse: ${text.take(40)}", failed)
      assertEquals(before, snapshot())
    }
  }
}
