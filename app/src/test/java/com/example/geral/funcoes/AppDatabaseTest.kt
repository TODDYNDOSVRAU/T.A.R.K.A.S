package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.FavoriteEntity
import com.example.data.db.PlayStatEntity
import com.example.data.db.PlaylistEntity
import com.example.data.db.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The base of Fase 3: rows go in and come out in the order the app relies on. */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {
  private lateinit var db: AppDatabase

  @Before fun open() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
  }

  @After fun close() = db.close()

  private fun track(id: String, picked: Boolean = false) = TrackEntity(
    id, "t $id", "a", "al", 1000, "g", "e", true, "/x/$id", null, null, false, 0, 1, 1, 0, picked
  )

  @Test fun favouritesComeNewestFirst() = runBlocking {
    db.favorites().add(FavoriteEntity("a", 1))
    db.favorites().add(FavoriteEntity("b", 3))
    db.favorites().add(FavoriteEntity("c", 2))
    assertEquals(listOf("b", "c", "a"), db.favorites().all())
  }

  @Test fun playlistKeepsOrderAndRepeatedTracks() = runBlocking {
    db.playlists().insert(PlaylistEntity("p1", "One", 1))
    db.playlists().setTracks("p1", listOf("x", "y", "x"))
    assertEquals(listOf("x", "y", "x"), db.playlists().entries().map { it.trackId })
    db.playlists().setTracks("p1", listOf("y"))
    assertEquals(listOf("y"), db.playlists().entries().map { it.trackId })
  }

  @Test fun deletingAPlaylistDeletesItsEntries() = runBlocking {
    db.playlists().insert(PlaylistEntity("p1", "One", 1))
    db.playlists().setTracks("p1", listOf("x"))
    db.playlists().delete("p1")
    assertEquals(0, db.playlists().entries().size)
  }

  @Test fun unreferencedTracksAreRemovedButReferencedAndPickedStay() = runBlocking {
    db.tracks().upsert(listOf(track("fav"), track("list"), track("picked", true), track("gone")))
    db.favorites().add(FavoriteEntity("fav", 1))
    db.playlists().insert(PlaylistEntity("p1", "One", 1))
    db.playlists().setTracks("p1", listOf("list"))
    db.tracks().deleteUnreferenced()
    assertEquals(setOf("fav", "list", "picked"), db.tracks().all().map { it.id }.toSet())
  }

  @Test fun statsAndPlaybackStateRoundTrip() = runBlocking {
    db.playStats().upsert(PlayStatEntity("a", 3, 1, 10, 20))
    db.playStats().upsert(PlayStatEntity("a", 4, 1, 11, 20))
    assertEquals(4, db.playStats().get("a")?.plays)
    assertNull(db.playback().get())
    db.playback().put(com.example.data.db.PlaybackStateEntity(0, "{}", 5, "a"))
    db.playback().putPosition(9, "a")
    assertEquals(9L, db.playback().get()?.positionMs)
    db.playback().clear()
    assertNull(db.playback().get())
  }
}
