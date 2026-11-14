package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.library.toEntity
import com.example.data.model.Track
import com.example.data.repository.LibraryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryRepositoryTest {

  private lateinit var context: Context
  private lateinit var db: AppDatabase
  private lateinit var library: LibraryRepository

  private fun track(id: String, local: Boolean = false, path: String? = null) = Track(
    id = id, title = "Faixa $id", artist = "Artista $id", album = "Álbum",
    durationMs = 125000L, genre = "Rock", era = "1971",
    isLocalFile = local, filePath = path, colorHex = 0xFF46C9D3
  )

  private val a = track("a")
  private val b = track("b")

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("tarkas_library", Context.MODE_PRIVATE).edit().clear().commit()
    context.getSharedPreferences("tarkas_library_files", Context.MODE_PRIVATE).edit().clear().commit()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    library = LibraryRepository(context, db)
    settle()
  }

  @After
  fun tearDown() = db.close()

  /** Waits until everything asked of [library] is written and published. */
  private fun settle(repo: LibraryRepository = library) = runBlocking { repo.idle() }

  private fun restart(): LibraryRepository = LibraryRepository(context, db).also { settle(it) }

  // --- favorites ---

  @Test
  fun `toggling a favorite adds and then removes it`() {
    library.toggleFavorite(a); settle()
    assertTrue(library.isFavorite("a"))
    library.toggleFavorite(a); settle()
    assertFalse(library.isFavorite("a"))
    assertTrue(library.favorites.value.isEmpty())
  }

  @Test
  fun `favorites are listed newest first`() {
    library.toggleFavorite(a)
    library.toggleFavorite(b); settle()
    assertEquals(listOf("b", "a"), library.favorites.value)
    assertEquals(listOf("b", "a"), restart().favorites.value)
  }

  @Test
  fun `favorites and their track data survive a restart`() {
    val local = track("local_1", local = true, path = "content://media/42")
    library.toggleFavorite(local); settle()

    val reloaded = restart()
    assertEquals(listOf("local_1"), reloaded.favorites.value)
    val saved = reloaded.savedTracks.value["local_1"]
    assertNotNull(saved)
    assertEquals("Faixa local_1", saved!!.title)
    assertEquals("content://media/42", saved.filePath)
    assertTrue(saved.isLocalFile)
    assertEquals(125000L, saved.durationMs)
    assertEquals(0xFF46C9D3, saved.colorHex)
  }

  @Test
  fun `a track without a file path round-trips with a null path`() {
    library.toggleFavorite(a); settle()
    assertNull(restart().savedTracks.value["a"]!!.filePath)
  }

  @Test
  fun `snapshots of tracks nobody uses anymore are not kept after a restart`() {
    library.toggleFavorite(a)
    library.toggleFavorite(a) // a is no longer used
    library.toggleFavorite(b); settle()

    val reloaded = restart()
    assertNull(reloaded.savedTracks.value["a"])
    assertNotNull(reloaded.savedTracks.value["b"])
    assertEquals(listOf("b"), runBlocking { db.tracks().all() }.map { it.id })
  }

  // --- playlists ---

  @Test
  fun `a playlist with a blank name gets a default name`() {
    val playlist = library.createPlaylist("   ")
    assertEquals(context.getString(com.example.R.string.library_new_playlist_title), playlist.name)
    assertTrue(playlist.trackIds.isEmpty())
  }

  @Test
  fun `playlist ids never repeat`() {
    val ids = (1..50).map { library.createPlaylist("p").id }.toSet()
    assertEquals(50, ids.size)
  }

  @Test
  fun `a playlist can start with a first track`() {
    val playlist = library.createPlaylist("Prog", a); settle()
    assertEquals("Prog", playlist.name)
    assertEquals(listOf("a"), playlist.trackIds)
    assertEquals(playlist, library.playlists.value.single())
  }

  @Test
  fun `a playlist made from a list keeps its order and its repeats`() {
    val playlist = library.createPlaylist("Fila", listOf(b, a, b)); settle()
    assertEquals(listOf("b", "a", "b"), playlist.trackIds)
    assertEquals(listOf("b", "a", "b"), restart().playlists.value.single().trackIds)
    assertEquals(setOf("a", "b"), restart().savedTracks.value.keys)
  }

  @Test
  fun `a playlist made from a list with a blank name gets the default name`() {
    val playlist = library.createPlaylist(" ", listOf(a))
    assertEquals(context.getString(com.example.R.string.library_new_playlist_title), playlist.name)
  }

  @Test
  fun `playlists keep the order they were created in`() {
    val ids = listOf("Um", "Dois", "Tres").map { library.createPlaylist(it).id }; settle()
    assertEquals(ids, library.playlists.value.map { it.id })
    assertEquals(ids, restart().playlists.value.map { it.id })
  }

  @Test
  fun `adding the same track twice keeps a single entry`() {
    val playlist = library.createPlaylist("Prog")
    library.addToPlaylist(playlist.id, a)
    library.addToPlaylist(playlist.id, a)
    library.addToPlaylist(playlist.id, b); settle()
    assertEquals(listOf("a", "b"), library.playlists.value.single().trackIds)
  }

  @Test
  fun `adding to an unknown playlist changes nothing`() {
    val playlist = library.createPlaylist("Prog")
    library.addToPlaylist("does_not_exist", a); settle()
    assertTrue(library.playlists.value.single { it.id == playlist.id }.trackIds.isEmpty())
    assertTrue(runBlocking { db.tracks().all() }.isEmpty())
  }

  @Test
  fun `removing a track keeps the playlist and the other tracks`() {
    val playlist = library.createPlaylist("Prog", a)
    library.addToPlaylist(playlist.id, b)
    library.removeFromPlaylist(playlist.id, "a"); settle()
    assertEquals(listOf("b"), library.playlists.value.single().trackIds)
    assertEquals(listOf("b"), restart().playlists.value.single().trackIds)
  }

  @Test
  fun `renaming trims the name and ignores a blank one`() {
    val playlist = library.createPlaylist("Prog")
    library.renamePlaylist(playlist.id, "  Clássicos  "); settle()
    assertEquals("Clássicos", library.playlists.value.single().name)
    library.renamePlaylist(playlist.id, "   "); settle()
    assertEquals("Clássicos", library.playlists.value.single().name)
  }

  @Test
  fun `deleting a playlist removes only that playlist`() {
    val one = library.createPlaylist("Um")
    val two = library.createPlaylist("Dois")
    library.deletePlaylist(one.id); settle()
    assertEquals(listOf(two.id), library.playlists.value.map { it.id })
    assertEquals(listOf(two.id), restart().playlists.value.map { it.id })
  }

  @Test
  fun `playlists and their tracks survive a restart`() {
    val playlist = library.createPlaylist("Prog", a)
    library.addToPlaylist(playlist.id, b); settle()

    val reloaded = restart()
    val restored = reloaded.playlists.value.single()
    assertEquals("Prog", restored.name)
    assertEquals(listOf("a", "b"), restored.trackIds)
    assertTrue(restored.trackIds.all { reloaded.savedTracks.value.containsKey(it) })
  }

  @Test
  fun `a track in a playlist keeps its snapshot even if it is not a favorite`() {
    val playlist = library.createPlaylist("Prog", a)
    library.toggleFavorite(b)
    library.toggleFavorite(b); settle()

    val reloaded = restart()
    assertTrue(playlist.id in reloaded.playlists.value.map { it.id })
    assertNotNull(reloaded.savedTracks.value["a"])
    assertNotEquals(null, reloaded.savedTracks.value["a"])
  }

  @Test
  fun `favoriting a picked file does not unpick it`() {
    val picked = track("local_x", local = true, path = "content://x")
    runBlocking {
      db.tracks().upsert(listOf(picked.toEntity(picked = true, dateAddedSec = 5)))
    }
    library.toggleFavorite(picked); settle()
    assertTrue(runBlocking { db.tracks().picked() }.any { it.id == "local_x" })
  }
}
