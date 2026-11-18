package com.example.windows.biblioteca

import com.example.data.db.AppDatabase
import com.example.data.library.DeviceCatalog
import com.example.data.model.Track
import com.example.data.playback.LibrarySort
import com.example.data.playback.SongOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

/** A silent 16-bit mono WAV of [ms] milliseconds at 8 kHz. */
private fun wav(file: File, ms: Int = 500) {
  val data = ByteArray(8000 * 2 * ms / 1000)
  val out = ByteArrayOutputStream()
  fun le32(v: Int) { out.write(v and 255); out.write(v shr 8 and 255); out.write(v shr 16 and 255); out.write(v shr 24 and 255) }
  fun le16(v: Int) { out.write(v and 255); out.write(v shr 8 and 255) }
  out.write("RIFF".toByteArray()); le32(36 + data.size); out.write("WAVE".toByteArray())
  out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1); le32(8000); le32(16000); le16(2); le16(16)
  out.write("data".toByteArray()); le32(data.size); out.write(data)
  file.parentFile.mkdirs()
  file.writeBytes(out.toByteArray())
}

/** An MP3-named file holding only an ID3v2.3 tag. */
private fun id3(file: File, title: String, artist: String, album: String, track: Int) {
  fun frame(id: String, text: String): ByteArray {
    val body = byteArrayOf(0) + text.toByteArray(Charsets.ISO_8859_1)
    val n = body.size
    return id.toByteArray() + byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte(), 0, 0) + body
  }
  val frames = frame("TIT2", title) + frame("TPE1", artist) + frame("TALB", album) + frame("TCON", "Rock") +
    frame("TYER", "1999") + frame("TRCK", "$track/10")
  val n = frames.size
  val header = "ID3".toByteArray() + byteArrayOf(3, 0, 0, (n shr 21 and 127).toByte(), (n shr 14 and 127).toByte(), (n shr 7 and 127).toByte(), (n and 127).toByte())
  file.parentFile.mkdirs()
  file.writeBytes(header + frames)
}

class LibraryScannerTest {
  private lateinit var dir: File

  @Before fun setUp() { dir = Files.createTempDirectory("tarkas-lib").toFile() }
  @After fun tearDown() { dir.deleteRecursively() }

  @Test fun reads_tags_from_an_ID3_file_and_the_file_name_for_a_WAV_without_tags() {
    id3(File(dir, "Rock/a.mp3"), "Song A", "Band", "Album", 3)
    wav(File(dir, "Rock/plain.wav"))
    val c = LibraryScanner().scan(listOf(dir), DeviceCatalog())
    assertEquals(2, c.tracks.size)
    val a = c.tracks.first { it.title == "Song A" }
    assertEquals("Band", a.artist); assertEquals("Album", a.album); assertEquals("Rock", a.genre)
    assertEquals("1999", a.era); assertEquals(3, a.trackNumber)
    val plain = c.tracks.first { it.title == "plain" }
    assertEquals(LibraryScanner.UNKNOWN_ARTIST, plain.artist)
    assertTrue(plain.durationMs in 400..600)
    assertTrue(plain.isLocalFile && File(plain.filePath!!).isFile)
  }

  @Test fun a_second_scan_returns_the_same_catalog_and_changes_are_picked_up() {
    val f = File(dir, "a.wav"); wav(f)
    val scanner = LibraryScanner()
    val first = scanner.scan(listOf(dir), DeviceCatalog())
    assertSame(first, scanner.scan(listOf(dir), first))
    wav(f, 900)
    val second = scanner.scan(listOf(dir), first)
    assertEquals(1, second.tracks.size)
    assertTrue(second.tracks[0].durationMs in 800..1000)
    wav(File(dir, "b.wav"))
    val third = scanner.scan(listOf(dir), second)
    assertEquals(2, third.tracks.size)
    f.delete()
    assertEquals(1, scanner.scan(listOf(dir), third).tracks.size)
  }

  @Test fun ids_are_stable_and_the_cache_file_round_trips() {
    val f = File(dir, "a.wav"); wav(f)
    assertEquals(LibraryScanner.idOf(f), LibraryScanner.idOf(File(f.path.uppercase())))
    val scanner = LibraryScanner(File(dir, "cache/lib.json"))
    val c = scanner.scan(listOf(dir), DeviceCatalog())
    scanner.saveCache(c)
    val back = scanner.loadCache()
    assertEquals(c.tracks.map { it.id }, back.tracks.map { it.id })
    assertEquals(c.folderOf, back.folderOf)
    assertSame(back, scanner.scan(listOf(dir), back))
  }

  @Test fun non_audio_files_are_skipped() {
    File(dir, "notes.txt").writeText("x"); wav(File(dir, "ok.wav"))
    assertEquals(listOf("ok"), LibraryScanner().scan(listOf(dir), DeviceCatalog()).tracks.map { it.title })
  }

  @Test fun two_roots_with_the_same_name_get_distinct_labels() {
    val a = File(dir, "x/Music").also { it.mkdirs() }
    val b = File(dir, "y/Music").also { it.mkdirs() }
    assertEquals(listOf("Music", "Music~2"), LibraryScanner().rootLabels(listOf(a, b)).values.toList())
  }
}

class LibrarySortTest {
  private fun t(id: String, title: String, artist: String = "A", date: Long = 0) =
    Track(id, title, artist, "", 1000, "", "", dateAddedSec = date)

  @Test fun title_order_ignores_case_and_accents_and_artists_skip_the_article() {
    val list = listOf(t("1", "banana"), t("2", "Árvore"), t("3", "Apple"))
    assertEquals(listOf("Apple", "Árvore", "banana"), LibrarySort.sorted(list, SongOrder.TITLE).map { it.title })
    val bands = listOf(t("1", "x", "The Zebras"), t("2", "y", "Mango"))
    assertEquals(listOf("Mango", "The Zebras"), LibrarySort.sorted(bands, SongOrder.ARTIST).map { it.artist })
  }

  @Test fun recent_puts_the_newest_first_and_most_played_uses_the_counters() {
    val list = listOf(t("1", "a", date = 10), t("2", "b", date = 30), t("3", "c", date = 20))
    assertEquals(listOf("2", "3", "1"), LibrarySort.sorted(list, SongOrder.RECENT).map { it.id })
    assertEquals(listOf("3", "1", "2"), LibrarySort.sorted(list, SongOrder.MOST_PLAYED, mapOf("3" to 9, "1" to 2)).map { it.id })
  }

  @Test fun index_letter() {
    assertEquals("B", LibrarySort.section(t("1", "banana"), SongOrder.TITLE))
    assertEquals("#", LibrarySort.section(t("1", "9 lives"), SongOrder.TITLE))
    assertNull(LibrarySort.section(t("1", "x"), SongOrder.RECENT))
  }

  @Test fun a_scanned_library_sorts_by_title() {
    val dir = Files.createTempDirectory("tarkas-sort").toFile()
    try {
      wav(File(dir, "b.wav")); wav(File(dir, "a.wav")); wav(File(dir, "c.wav"))
      val c = LibraryScanner().scan(listOf(dir), DeviceCatalog())
      assertEquals(listOf("a", "b", "c"), LibrarySort.sorted(c.tracks.reversed(), SongOrder.TITLE).map { it.title })
    } finally { dir.deleteRecursively() }
  }
}

class LibraryStoreTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private lateinit var db: AppDatabase
  private fun track(id: String) = Track(id, "T$id", "Artist", "Album", 1000, "", "", isLocalFile = true, filePath = "C:/m/$id.mp3")

  @Before fun setUp() { db = AppDatabase.inMemory() }
  @After fun tearDown() { db.close() }

  @Test fun favourites_and_playlists_keep_order_and_survive_a_reload() = runBlocking {
    val store = LibraryStore(db, scope)
    store.toggleFavorite(track("ms:1")); store.toggleFavorite(track("ms:2")); store.toggleFavorite(track("ms:1"))
    val p = store.createPlaylist("  Mix  ", listOf(track("ms:3")))
    store.addToPlaylist(p.id, track("ms:4")); store.addToPlaylist(p.id, track("ms:4"))
    store.idle()
    assertEquals(listOf("ms:2"), store.favorites.value)
    assertEquals(listOf("ms:3", "ms:4"), store.playlists.value.single().trackIds)
    assertEquals("Mix", store.playlists.value.single().name)

    val again = LibraryStore(db, scope); again.idle()
    assertEquals(listOf("ms:2"), again.favorites.value)
    assertEquals(listOf("ms:3", "ms:4"), again.playlists.value.single().trackIds)
    assertNotNull(again.savedTracks.value["ms:3"])

    again.removeFromPlaylist(p.id, "ms:3"); again.renamePlaylist(p.id, "Other"); again.idle()
    assertEquals("Other", again.playlists.value.single().name)
    assertEquals(listOf("ms:4"), again.playlists.value.single().trackIds)
    again.deletePlaylist(p.id); again.idle()
    assertTrue(again.playlists.value.isEmpty())
  }

  @Test fun play_counters_and_picked_files_persist() = runBlocking {
    val store = LibraryStore(db, scope)
    store.recordPlay("ms:1", 5000); store.recordPlay("ms:1", 9000)
    store.addPicked(track("ms:7")); store.addPicked(track("ms:7"))
    store.idle()
    assertEquals(2, store.stats.value.getValue("ms:1").plays)
    assertEquals(9000L, store.stats.value.getValue("ms:1").lastPlayedMs)
    assertEquals(1, store.picked.value.size)
    val again = LibraryStore(db, scope); again.idle()
    assertEquals(2, again.stats.value.getValue("ms:1").plays)
    assertEquals(listOf("ms:7"), again.picked.value.map { it.id })
  }
}

class CatalogParseTest {
  @Test fun audius_skips_entries_without_an_id_and_builds_the_stream_address_on_the_node() {
    val body = """{"data":[{"id":"abc","title":"Hi","user":{"name":"Me"},"duration":61,"genre":"Pop","artwork":{"480x480":"https://x/a.jpg"}},{"title":"no id"},{"id":"z","is_streamable":false}]}"""
    val tracks = AudiusCatalog.parse(body, "https://node")!!
    assertEquals(1, tracks.size)
    val t = tracks[0]
    assertEquals("audius_abc", t.id); assertEquals("Me", t.artist); assertEquals(61_000L, t.durationMs)
    assertTrue(t.filePath!!.startsWith("https://node/tracks/abc/stream")); assertEquals("https://x/a.jpg", t.artworkUrl)
    assertFalse(t.isLocalFile)
    assertNull(AudiusCatalog.parse("not json", "https://node"))
  }

  @Test fun jamendo_parses_results_decodes_entities_and_reports_a_missing_client_id() {
    val body = """{"headers":{"status":"success"},"results":[{"id":"7","name":"Etude &quot;One&quot;","artist_name":"A &amp; B","audio":"https://a/7.mp3","duration":10,"releasedate":"2020-01-01"},{"id":"8","name":"x","audio":"http://insecure"}]}"""
    val tracks = JamendoCatalog.parse(body)
    assertEquals(1, tracks.size)
    assertEquals("Etude \"One\"", tracks[0].title); assertEquals("A & B", tracks[0].artist); assertEquals("2020", tracks[0].era)
    val failed = """{"headers":{"status":"failed","error_message":"invalid client_id"},"results":[]}"""
    try { JamendoCatalog.parse(failed); fail("expected JamendoNotConfigured") } catch (_: JamendoNotConfigured) {}
  }

  @Test fun musicbrainz_takes_the_best_scoring_recording_and_ignores_weak_ones() {
    val body = """{"recordings":[{"id":"low","score":50,"title":"x"},{"id":"best","score":95,"title":"Song","length":200000,"artist-credit":[{"name":"A","joinphrase":" & "},{"name":"B","joinphrase":""}],"releases":[{"id":"r2","title":"Later","date":"2001"},{"id":"r1","title":"First","date":"1999"}],"tags":[{"name":"rock","count":3}]}]}"""
    val r = MusicBrainzCatalog.parseBest(body)!!
    assertEquals("best", r.mbid); assertEquals("A & B", r.artist); assertEquals("First", r.release); assertEquals(listOf("rock"), r.tags)
    assertNull(MusicBrainzCatalog.parseBest("""{"recordings":[{"id":"w","score":10}]}"""))
  }
}
