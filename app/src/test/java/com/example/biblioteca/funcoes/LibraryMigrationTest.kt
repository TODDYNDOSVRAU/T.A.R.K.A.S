package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.library.JsonSalvage
import com.example.data.library.LibraryMigration
import com.example.data.library.TrackIds
import com.example.data.model.Track
import com.example.data.model.TrackJson
import com.example.data.repository.LibraryRepository
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryMigrationTest {
  private lateinit var context: Context
  private lateinit var db: AppDatabase

  private fun track(id: String, path: String? = null) = Track(
    id = id, title = "T $id", artist = "A", album = "B", durationMs = 1000, genre = "g", era = "e",
    isLocalFile = path != null, filePath = path, colorHex = 0xFF46C9D3
  )

  private fun playlists(vararg p: Pair<String, List<String>>) = JSONArray().apply {
    p.forEach { (id, ids) -> put(JSONObject().put("id", id).put("name", "N $id").put("tracks", JSONArray(ids))) }
  }.toString()

  private fun store(favorites: String?, playlists: String?, saved: String?, picked: String?) {
    context.getSharedPreferences("tarkas_library", Context.MODE_PRIVATE).edit()
      .putString("favorites", favorites).putString("playlists", playlists).putString("saved_tracks", saved).commit()
    context.getSharedPreferences("tarkas_library_files", Context.MODE_PRIVATE).edit().putString("picked", picked).commit()
  }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
  }

  @After
  fun tearDown() = db.close()

  private fun migrate() = runBlocking { LibraryMigration.ensure(context, db) }

  @Test
  fun `a real library moves with its order, playlists and repeated tracks`() {
    val saved = TrackJson.listToJson(listOf(track("ms:1"), track("ms:2"), track("ms:3"))).toString()
    store(
      JSONArray(listOf("ms:3", "ms:1", "ms:2")).toString(),
      playlists("pl_1" to listOf("ms:1", "ms:2", "ms:1"), "pl_2" to emptyList(), "pl_3" to listOf("ms:3")),
      saved, null
    )
    migrate()
    val library = LibraryRepository(context, db)
    runBlocking { library.idle() }
    assertEquals(listOf("ms:3", "ms:1", "ms:2"), library.favorites.value)
    assertEquals(listOf("pl_1", "pl_2", "pl_3"), library.playlists.value.map { it.id })
    assertEquals(listOf("ms:1", "ms:2", "ms:1"), library.playlists.value[0].trackIds)
    assertTrue(library.playlists.value[1].trackIds.isEmpty())
    assertEquals(3, library.savedTracks.value.size)
  }

  @Test
  fun `the old preferences are left as they were`() {
    val favorites = JSONArray(listOf("ms:1")).toString()
    store(favorites, "[]", "[]", "[]")
    migrate()
    assertEquals(favorites, context.getSharedPreferences("tarkas_library", Context.MODE_PRIVATE).getString("favorites", null))
  }

  @Test
  fun `it runs once`() {
    store(JSONArray(listOf("ms:1")).toString(), "[]", "[]", "[]")
    migrate()
    store(JSONArray(listOf("ms:9")).toString(), "[]", "[]", "[]")
    migrate()
    assertEquals(listOf("ms:1"), runBlocking { db.favorites().all() })
  }

  @Test
  fun `corrupted JSON keeps what is readable`() {
    val full = TrackJson.toJson(track("ms:1")).toString()
    // favorites cut in the middle; a playlist without an id; a playlist without tracks; a last one cut short
    store(
      "[\"ms:1\",\"ms:2\",\"ms:3",
      "[{\"id\":\"pl_a\",\"name\":\"A\",\"tracks\":[\"ms:1\",\"ms:2\"]},{\"name\":\"No id\",\"tracks\":[\"ms:3\"]}," +
        "{\"id\":\"pl_c\",\"name\":\"C\"},{\"id\":\"pl_d\",\"name\":\"D\",\"tracks\":[\"ms:",
      "[$full,{\"title\":\"no id\"},{\"id\":\"ms:2\",\"title\":\"cut",
      null
    )
    migrate()
    // the last favorite was cut in the middle and is dropped; the two whole ones keep their order
    assertEquals(listOf("ms:1", "ms:2"), runBlocking { db.favorites().all() })
    val lists = runBlocking { db.playlists().all() }
    assertEquals(3, lists.size)
    assertEquals("pl_a", lists[0].id)
    assertNotEquals("", lists[1].id)
    assertEquals("pl_c", lists[2].id)
    val entries = runBlocking { db.playlists().entries() }
    assertEquals(listOf("ms:1", "ms:2"), entries.filter { it.playlistId == "pl_a" }.map { it.trackId })
    assertEquals(listOf("ms:3"), entries.filter { it.playlistId == lists[1].id }.map { it.trackId })
    assertTrue(entries.none { it.playlistId == "pl_c" })
    assertEquals(listOf("ms:1"), runBlocking { db.tracks().all() }.map { it.id })
  }

  @Test
  fun `text that is not JSON at all migrates as empty and leaves the old text`() {
    store("not json", "{", "", null)
    migrate()
    assertTrue(runBlocking { db.favorites().all() }.isEmpty())
    assertEquals("not json", context.getSharedPreferences("tarkas_library", Context.MODE_PRIVATE).getString("favorites", null))
  }

  @Test
  fun `a picked file gets its stable id and favorites and playlists follow it`() {
    val uri = "content://docs/document/1"
    val old = track("local_1731000000000", uri)
    store(
      JSONArray(listOf("local_1731000000000")).toString(),
      playlists("pl_1" to listOf("local_1731000000000")),
      TrackJson.listToJson(listOf(old)).toString(),
      TrackJson.listToJson(listOf(old)).toString()
    )
    migrate()
    val id = TrackIds.picked(uri)
    assertEquals(mapOf("local_1731000000000" to id), runBlocking { LibraryMigration.idMap(db) })
    assertEquals(listOf(id), runBlocking { db.favorites().all() })
    assertEquals(listOf(id), runBlocking { db.playlists().entries() }.map { it.trackId })
    val music = MusicRepository(context, lazy { db })
    runBlocking { music.idle() }
    assertTrue(music.tracks.value.any { it.id == id })
  }

  @Test
  fun `picked files keep their order`() {
    val picked = listOf(track("local_3", "content://c"), track("local_2", "content://b"), track("local_1", "content://a"))
    store("[]", "[]", "[]", TrackJson.listToJson(picked).toString())
    migrate()
    val music = MusicRepository(context, lazy { db })
    runBlocking { music.idle() }
    val paths = music.tracks.value.mapNotNull { it.filePath }.filter { it.startsWith("content://") }
    assertEquals(listOf("content://c", "content://b", "content://a"), paths)
  }

  @Test
  fun `the same file picked twice is the same track and the favorite stays`() {
    val music = MusicRepository(context, lazy { db })
    val library = LibraryRepository(context, db)
    val first = music.addLocalTrack("Song", "Art", "content://docs/document/7", 90_000)
    library.toggleFavorite(first)
    val again = music.addLocalTrack("Song", "Art", "content://docs/document/7", 90_000)
    runBlocking { music.idle(); library.idle() }
    assertEquals(first.id, again.id)
    assertEquals(1, music.tracks.value.count { it.id == first.id })
    assertEquals(listOf(first.id), library.favorites.value)

    val restartedMusic = MusicRepository(context, lazy { db })
    runBlocking { restartedMusic.idle() }
    assertEquals(1, restartedMusic.tracks.value.count { it.id == first.id })
  }

  @Test
  fun `stable ids differ per file and repeat per address`() {
    assertEquals(TrackIds.picked("content://a"), TrackIds.picked("content://a"))
    assertNotEquals(TrackIds.picked("content://a"), TrackIds.picked("content://b"))
  }

  @Test
  fun `salvage keeps the whole elements of a cut array`() {
    assertEquals(listOf("\"a\"", "{\"x\":[1,2]}"), JsonSalvage.elements("[\"a\", {\"x\":[1,2]}, {\"y\":\"cu"))
    assertEquals(listOf("\"a\"", "\"b\""), JsonSalvage.elements("[\"a\",\"b\" "))
    assertEquals(emptyList<String>(), JsonSalvage.elements("not an array"))
    assertEquals(listOf("\"q,]\""), JsonSalvage.elements("[\"q,]\"]"))
  }
}
