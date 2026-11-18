package com.example

import com.example.data.jamendo.JamendoApiService
import com.example.data.jamendo.JamendoNotConfigured
import com.example.data.model.Track
import com.example.data.model.TrackJson
import com.example.data.musicbrainz.MusicBrainzService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** How the answers of Jamendo and MusicBrainz are read, and what happens to tracks of removed services. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlineCatalogTest {

  private val jamendoAnswer = """
    {"headers":{"status":"success","code":0,"results_count":3},
     "results":[
       {"id":"1532771","name":"Wish You Were Here","duration":227,"artist_name":"Alex Cohen","album_name":"Dreams",
        "releasedate":"2018-06-01","album_image":"https://usercontent.jamendo.com/album.jpg","image":"https://usercontent.jamendo.com/track.jpg",
        "audio":"https://prod-1.storage.jamendo.com/?trackid=1532771&format=mp31",
        "musicinfo":{"tags":{"genres":["pop","rock"]}}},
       {"id":"99","name":"No stream","duration":100,"artist_name":"X","audio":""},
       {"id":"1532771","name":"Wish You Were Here","duration":227,"artist_name":"Alex Cohen",
        "audio":"https://prod-1.storage.jamendo.com/?trackid=1532771&format=mp31"}
     ]}
  """.trimIndent()

  @Test
  fun `jamendo tracks are read, playable only, once each`() {
    val tracks = JamendoApiService.parseTracks(jamendoAnswer)
    assertEquals(1, tracks.size)
    val t = tracks.single()
    assertEquals("jamendo_1532771", t.id)
    assertEquals("Wish You Were Here", t.title)
    assertEquals("Alex Cohen", t.artist)
    assertEquals("Dreams", t.album)
    assertEquals(227_000L, t.durationMs)
    assertEquals("Pop", t.genre)
    assertEquals("2018", t.era)
    assertEquals("https://usercontent.jamendo.com/album.jpg", t.artworkUrl)
    assertTrue(t.filePath!!.startsWith("https://prod-1.storage.jamendo.com/"))
    assertEquals("Jamendo", t.onlineSource)
  }

  @Test
  fun `jamendo names arrive html-escaped and are shown decoded`() {
    val t = JamendoApiService.parseTracks(
      """{"headers":{"status":"success"},"results":[{"id":"7","name":"Etude &quot;The Passion&quot; &amp; more",
         "artist_name":"Rock &#39;n&#39; Roll","album_name":"A &lt;B&gt;","duration":60,
         "audio":"https://prod-1.storage.jamendo.com/?trackid=7&format=mp32"}]}"""
    ).single()
    assertEquals("Etude \"The Passion\" & more", t.title)
    assertEquals("Rock 'n' Roll", t.artist)
    assertEquals("A <B>", t.album)
  }

  @Test(expected = JamendoNotConfigured::class)
  fun `a refused client id is a configuration problem, not offline`() {
    JamendoApiService.parseTracks(
      """{"headers":{"status":"failed","code":5,"error_message":"Your credential is not authorized. Invalid client_id"},"results":[]}"""
    )
  }

  @Test
  fun `the build without a jamendo key does not call jamendo`() {
    // The tests build with the placeholder of .env.example.
    if (BuildConfig.JAMENDO_CLIENT_ID == "MY_JAMENDO_CLIENT_ID") assertTrue(!JamendoApiService.isConfigured)
  }

  private val musicBrainzAnswer = """
    {"count":2,"recordings":[
      {"id":"low","score":55,"title":"Something else","artist-credit":[{"name":"Other"}]},
      {"id":"8f3471b5-7e6a-48da-86a9-c1c07a0f47ae","score":100,"title":"Bohemian Rhapsody","length":354000,
       "first-release-date":"1975-10-31",
       "artist-credit":[{"name":"Queen","joinphrase":""}],
       "releases":[
         {"id":"rel-late","title":"Greatest Hits","date":"1981-10-26","release-group":{"primary-type":"Album"}},
         {"id":"rel-first","title":"A Night at the Opera","date":"1975-11-21","release-group":{"primary-type":"Album"}}
       ],
       "tags":[{"count":2,"name":"progressive rock"},{"count":9,"name":"rock"}]}
    ]}
  """.trimIndent()

  @Test
  fun `musicbrainz best match, its first release and its cover`() {
    val info = MusicBrainzService.parseBest(musicBrainzAnswer)!!
    assertEquals("8f3471b5-7e6a-48da-86a9-c1c07a0f47ae", info.mbid)
    assertEquals("Queen", info.artist)
    assertEquals("A Night at the Opera", info.release)
    assertEquals("Album", info.releaseType)
    assertEquals("1975-10-31", info.firstReleaseDate)
    assertEquals(354_000L, info.lengthMs)
    assertEquals(listOf("rock", "progressive rock"), info.tags)
    assertEquals("https://coverartarchive.org/release/rel-first/front-250", info.coverUrl)
    assertEquals("https://musicbrainz.org/recording/8f3471b5-7e6a-48da-86a9-c1c07a0f47ae", info.pageUrl)
  }

  @Test
  fun `musicbrainz guesses below the score threshold are not shown`() {
    assertNull(MusicBrainzService.parseBest("""{"recordings":[{"id":"x","score":60,"title":"T"}]}"""))
    assertNull(MusicBrainzService.parseBest("""{"recordings":[]}"""))
  }

  @Test
  fun `songs saved from youtube music or spotify are dropped, the others kept`() {
    val audius = Track(id = "audius_1", title = "A", artist = "B", album = "C", durationMs = 1, genre = "", era = "", filePath = "https://x")
    val saved = TrackJson.toJson(audius)
    assertEquals("audius_1", TrackJson.fromJson(saved)?.id)
    assertNull(TrackJson.fromJson(JSONObject(saved.toString()).put("web", "spotify").put("webId", "abc")))
    assertNull(TrackJson.fromJson(JSONObject(saved.toString()).put("web", "youtube")))
    assertEquals("audius_1", TrackJson.fromJson(JSONObject(saved.toString()).put("web", JSONObject.NULL))?.id)
  }
}
