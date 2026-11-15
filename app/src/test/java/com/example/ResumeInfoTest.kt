package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.playback.PlaybackStateStore
import com.example.data.playback.ResumeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "Continuar ouvindo": the small read the home screen uses, with no JSON on the caller's side. */
@RunWith(RobolectricTestRunner::class)
class ResumeInfoTest {
  private lateinit var db: AppDatabase
  private lateinit var store: PlaybackStateStore

  @Before fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    store = PlaybackStateStore(db.playback(), Dispatchers.IO)
  }

  @After fun tearDown() = db.close()

  private fun track(id: String, duration: Long = 180_000) =
    """{"id":"$id","title":"t","artist":"a","album":"al","dur":$duration,"genre":"g","extension":"e","isLocal":true,"path":"/x/$id"}"""

  private fun json(current: String = track("a")) = """{"current":$current,"queue":[]}"""

  @Test fun realStateGivesTrackPositionAndDuration() = runBlocking {
    store.save(json(), 42_000L, "a")
    assertEquals(ResumeInfo("a", 42_000L, 180_000L), store.resumeInfo())
  }

  @Test fun noStateIsNull() = runBlocking { assertNull(store.resumeInfo()) }

  @Test fun positionOfAnotherTrackIsNull() = runBlocking {
    store.save(json(), 7_777L, "zzz")
    assertNull(store.resumeInfo())
  }

  @Test fun unreadableStateIsNull() = runBlocking {
    store.save("{broken", 1L, "a")
    assertNull(store.resumeInfo())
  }

  @Test fun unknownLengthIsNullDuration() = runBlocking {
    store.save(json(track("a", duration = 0)), 5_000L, "a")
    assertEquals(ResumeInfo("a", 5_000L, null), store.resumeInfo())
  }

  @Test fun positionSavedBeforeOwnerWasKeptIsTrusted() = runBlocking {
    store.save(json(), 9_000L, null)
    assertEquals(9_000L, store.resumeInfo()!!.positionMs)
  }
}
