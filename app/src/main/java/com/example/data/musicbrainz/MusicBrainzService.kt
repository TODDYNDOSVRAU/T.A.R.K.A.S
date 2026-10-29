package com.example.data.musicbrainz

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** What MusicBrainz knows about a recording, for the "Sobre a faixa" sheet. */
data class RecordingInfo(
  val mbid: String,
  val title: String,
  val artist: String,
  /** The first release that has it (an album, single or EP), and its type. */
  val release: String?,
  val releaseType: String?,
  /** "2014-03-17", "2014-03" or "2014". */
  val firstReleaseDate: String?,
  val lengthMs: Long?,
  val tags: List<String>,
  /** Front cover from the Cover Art Archive (may answer 404 when the release has none). */
  val coverUrl: String?,
  val pageUrl: String
)

/**
 * MusicBrainz (https://musicbrainz.org/doc/MusicBrainz_API), the open music encyclopedia: looks up a
 * song by title and artist and returns its album, release date and genres, with the cover from the
 * Cover Art Archive. Metadata only, no audio. MusicBrainz asks for a User-Agent that names the app and
 * a contact, and at most one request per second, so calls go one at a time and answers are cached.
 */
object MusicBrainzService {
  private const val BASE = "https://musicbrainz.org/ws/2"
  private const val MIN_GAP_MS = 1100L
  /** Below this search score (0..100) the match is a guess, and a wrong album is worse than none. */
  private const val MIN_SCORE = 80

  private val userAgent = "TARKAS/${BuildConfig.VERSION_NAME} ( roger.moreiraa8@gmail.com )"

  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  private val gate = Mutex()
  private var lastCallAt = 0L
  private val cache = object : LinkedHashMap<String, RecordingInfo?>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RecordingInfo?>?) = size > 64
  }

  /**
   * The best match for [title] by [artist], or null when MusicBrainz has none it is sure of. Throws
   * [java.io.IOException] when MusicBrainz did not answer.
   */
  suspend fun lookup(title: String, artist: String): RecordingInfo? = withContext(Dispatchers.IO) {
    val key = "${title.trim().lowercase()}|${artist.trim().lowercase()}"
    synchronized(cache) { if (cache.containsKey(key)) return@withContext cache[key] }
    val found = gate.withLock {
      val wait = lastCallAt + MIN_GAP_MS - System.currentTimeMillis()
      if (wait > 0) kotlinx.coroutines.delay(wait)
      try {
        search(title, artist)
      } finally {
        lastCallAt = System.currentTimeMillis()
      }
    }
    synchronized(cache) { cache[key] = found }
    found
  }

  /**
   * A popular song has dozens of recordings that all score 100 (live takes, compilations, karaoke).
   * The studio one is asked for first; only when that finds nothing is every recording considered.
   * Two requests at most, the second after the same one-second gap.
   */
  private suspend fun search(title: String, artist: String): RecordingInfo? {
    val base = buildString {
      append("recording:\"").append(escape(title)).append('"')
      if (artist.isNotBlank()) append(" AND artist:\"").append(escape(artist)).append('"')
    }
    request("$base AND status:official AND NOT secondarytype:live AND NOT secondarytype:compilation")?.let { return it }
    lastCallAt = System.currentTimeMillis()
    kotlinx.coroutines.delay(MIN_GAP_MS)
    return request(base)
  }

  private fun request(query: String): RecordingInfo? {
    val url = "$BASE/recording".toHttpUrl().newBuilder()
      .addQueryParameter("query", query)
      .addQueryParameter("fmt", "json")
      .addQueryParameter("limit", "5")
      .build()
    val call = Request.Builder().url(url).header("User-Agent", userAgent).header("Accept", "application/json").build()
    // 503 is MusicBrainz's "too many requests from this address": it passes in a second, so one retry.
    repeat(2) { attempt ->
      val (code, body) = client.newCall(call).execute().use { it.code to it.body?.string() }
      when {
        code == 503 && attempt == 0 -> Thread.sleep(MIN_GAP_MS)
        code !in 200..299 -> throw java.io.IOException("MusicBrainz answered $code")
        else -> return parseBest(body ?: throw java.io.IOException("Empty answer from MusicBrainz"))
      }
    }
    throw java.io.IOException("MusicBrainz is busy")
  }

  /** Lucene special characters would change the query; they are escaped. */
  private fun escape(text: String): String =
    text.trim().replace(Regex("""([+\-!(){}\[\]^"~*?:\\/]|&&|\|\|)"""), """\\$1""")

  /** The highest-scoring recording of a search answer. Public for the tests. */
  fun parseBest(body: String): RecordingInfo? {
    val recordings = JSONObject(body).optJSONArray("recordings") ?: return null
    var best: JSONObject? = null
    for (i in 0 until recordings.length()) {
      val r = recordings.optJSONObject(i) ?: continue
      if (r.optInt("score") < MIN_SCORE) continue
      if (best == null || r.optInt("score") > best.optInt("score")) best = r
    }
    val r = best ?: return null
    val mbid = r.optString("id").ifBlank { return null }
    val credit = r.optJSONArray("artist-credit")
    val artist = buildString {
      for (i in 0 until (credit?.length() ?: 0)) {
        val c = credit!!.optJSONObject(i) ?: continue
        append(c.optString("name")).append(c.optString("joinphrase"))
      }
    }.trim()
    // The earliest dated release, which is usually the original album or single.
    val releases = r.optJSONArray("releases")
    var release: JSONObject? = null
    for (i in 0 until (releases?.length() ?: 0)) {
      val rel = releases!!.optJSONObject(i) ?: continue
      val date = rel.optString("date")
      val current = release?.optString("date").orEmpty()
      if (release == null || (date.isNotBlank() && (current.isBlank() || date < current))) release = rel
    }
    val tags = r.optJSONArray("tags")?.let { arr ->
      (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        .sortedByDescending { it.optInt("count") }
        .map { it.optString("name") }
        .filter { it.isNotBlank() }
        .take(5)
    }.orEmpty()
    val releaseId = release?.optString("id")?.ifBlank { null }
    return RecordingInfo(
      mbid = mbid,
      title = r.optString("title"),
      artist = artist,
      release = release?.optString("title")?.ifBlank { null },
      releaseType = release?.optJSONObject("release-group")?.optString("primary-type")?.ifBlank { null },
      firstReleaseDate = r.optString("first-release-date").ifBlank { release?.optString("date") }?.ifBlank { null },
      lengthMs = r.optLong("length", 0L).takeIf { it > 0 },
      tags = tags,
      coverUrl = releaseId?.let { "https://coverartarchive.org/release/$it/front-250" },
      pageUrl = "https://musicbrainz.org/recording/$mbid"
    )
  }
}
