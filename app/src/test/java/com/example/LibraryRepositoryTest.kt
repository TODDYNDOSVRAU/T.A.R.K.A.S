package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Track
import com.example.data.repository.LibraryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    library = LibraryRepository(context)
  }

  // --- favorites ---

  @Test
  fun `toggling a favorite adds and then removes it`() {
    library.toggleFavorite(a)
    assertTrue(library.isFavorite("a"))
    library.toggleFavorite(a)
    assertFalse(library.isFavorite("a"))
    assertTrue(library.favorites.value.isEmpty())
  }

  @Test
  fun `favorites are listed newest first`() {
    library.toggleFavorite(a)
    library.toggleFavorite(b)
    assertEquals(listOf("b", "a"), library.favorites.value)
  }

  @Test
  fun `favorites and their track data survive a restart`() {
    val local = track("local_1", local = true, path = "content://media/42")
    library.toggleFavorite(local)

    val reloaded = LibraryRepository(context)
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
    library.toggleFavorite(a)
    assertNull(LibraryRepository(context).savedTracks.value["a"]!!.filePath)
  }

  @Test
  fun `snapshots of tracks nobody uses anymore are not kept after a restart`() {
    library.toggleFavorite(a)
    library.toggleFavorite(a) // a is no longer used
    library.toggleFavorite(b) // saving b rewrites the stored snapshots

    val reloaded = LibraryRepository(context)
    assertNull(reloaded.savedTracks.value["a"])
    assertNotNull(reloaded.savedTracks.value["b"])
  }

  // --- playlists ---

  @Test
  fun `a playlist with a blank name gets a default name`() {
    val playlist = library.createPlaylist("   ")
    assertEquals("Nova playlist", playlist.name)
    assertTrue(playlist.trackIds.isEmpty())
  }

  @Test
  fun `a playlist can start with a first track`() {
    val playlist = library.createPlaylist("Prog", a)
    assertEquals("Prog", playlist.name)
    assertEquals(listOf("a"), playlist.trackIds)
    assertEquals(playlist, library.playlists.value.single())
  }

  @Test
  fun `adding the same track twice keeps a single entry`() {
    val playlist = library.createPlaylist("Prog")
    library.addToPlaylist(playlist.id, a)
    library.addToPlaylist(playlist.id, a)
    library.addToPlaylist(playlist.id, b)
    assertEquals(listOf("a", "b"), library.playlists.value.single().trackIds)
  }

  @Test
  fun `adding to an unknown playlist changes nothing`() {
    val playlist = library.createPlaylist("Prog")
    library.addToPlaylist("does_not_exist", a)
    assertTrue(library.playlists.value.single { it.id == playlist.id }.trackIds.isEmpty())
  }

  @Test
  fun `removing a track keeps the playlist and the other tracks`() {
    val playlist = library.createPlaylist("Prog", a)
    library.addToPlaylist(playlist.id, b)
    library.removeFromPlaylist(playlist.id, "a")
    assertEquals(listOf("b"), library.playlists.value.single().trackIds)
  }

  @Test
  fun `renaming trims the name and ignores a blank one`() {
    val playlist = library.createPlaylist("Prog")
    library.renamePlaylist(playlist.id, "  Clássicos  ")
    assertEquals("Clássicos", library.playlists.value.single().name)
    library.renamePlaylist(playlist.id, "   ")
    assertEquals("Clássicos", library.playlists.value.single().name)
  }

  @Test
  fun `deleting a playlist removes only that playlist`() {
    val one = library.createPlaylist("Um")
    val two = library.createPlaylist("Dois")
    library.deletePlaylist(one.id)
    assertEquals(listOf(two.id), library.playlists.value.map { it.id })
  }

  @Test
  fun `playlists and their tracks survive a restart`() {
    val playlist = library.createPlaylist("Prog", a)
    library.addToPlaylist(playlist.id, b)

    val reloaded = LibraryRepository(context)
    val restored = reloaded.playlists.value.single()
    assertEquals("Prog", restored.name)
    assertEquals(listOf("a", "b"), restored.trackIds)
    assertTrue(restored.trackIds.all { reloaded.savedTracks.value.containsKey(it) })
  }

  @Test
  fun `a track in a playlist keeps its snapshot even if it is not a favorite`() {
    val playlist = library.createPlaylist("Prog", a)
    library.toggleFavorite(b)
    library.toggleFavorite(b)

    val reloaded = LibraryRepository(context)
    assertTrue(playlist.id in reloaded.playlists.value.map { it.id })
    assertNotNull(reloaded.savedTracks.value["a"])
  }
}
