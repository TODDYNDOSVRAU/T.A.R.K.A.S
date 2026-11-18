package com.example.windows.biblioteca

import com.example.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** The one HTTP client of the library; Java's own, so the desktop build needs no extra library. */
internal object CatalogHttp {
  val client: HttpClient by lazy {
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build()
  }

  /** The answer body, or an [IOException] for a refusal or no connection. [status] is told the code on a refusal. */
  fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutSec: Long = 20): Pair<Int, String> {
    val b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSec)).header("Accept", "application/json")
    headers.forEach { (k, v) -> b.header(k, v) }
    return try {
      val r = client.send(b.build(), HttpResponse.BodyHandlers.ofString())
      r.statusCode() to r.body()
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw IOException("interrupted", e)
    }
  }

  fun bytes(url: String, timeoutSec: Long = 30): ByteArray {
    val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSec)).build()
    val r = client.send(req, HttpResponse.BodyHandlers.ofByteArray())
    if (r.statusCode() !in 200..299) throw IOException("HTTP ${r.statusCode()}")
    return r.body()
  }

  /** Saves [url] to [file] (through a temp file, so a half download is never played). */
  fun download(url: String, file: File, timeoutSec: Long = 120) {
    file.parentFile?.mkdirs()
    val tmp = File(file.path + ".part")
    val req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSec)).build()
    val r = client.send(req, HttpResponse.BodyHandlers.ofFile(tmp.toPath()))
    if (r.statusCode() !in 200..299) { tmp.delete(); throw IOException("HTTP ${r.statusCode()}") }
    java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
  }

  fun enc(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)
}

/** Audius (https://docs.audius.org): free streaming from the artists' own uploads. Same nodes and keys as the app. */
object AudiusCatalog {
  private const val APP_NAME = "Tarkas"
  private const val API_KEY = "0xb37c3c789148f33197d9710d4b021a313c20902b"
  private val NODES = listOf(
    "https://discoveryprovider.audius.co/v1",
    "https://audius-dp.amsterdam.creatorseed.com/v1",
    "https://audius-discovery-1.cultur3stake.com/v1",
    "https://dn1.nodeoperator.io/v1"
  )
  private val COLORS = listOf(0xFFFA243C, 0xFF00E5FF, 0xFFA855F7, 0xFF38BDF8, 0xFFF59E0B, 0xFF10B981)

  /** Throws [IOException] when no server answered, so the screen can tell "offline" from "nothing found". */
  suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    val q = CatalogHttp.enc(query.trim())
    firstAnswer { "$it/tracks/search?query=$q&app_name=$APP_NAME&api_key=$API_KEY" }
  }

  suspend fun trending(): List<Track> = withContext(Dispatchers.IO) {
    firstAnswer { "$it/tracks/trending?app_name=$APP_NAME&api_key=$API_KEY" }
  }

  private fun firstAnswer(urlFor: (String) -> String): List<Track> {
    var answered = false
    for (node in NODES) {
      val tracks = try {
        val (code, body) = CatalogHttp.get(urlFor(node))
        if (code !in 200..299) null else parse(body, node)
      } catch (_: Exception) {
        null
      } ?: continue
      answered = true
      if (tracks.isNotEmpty()) return tracks.distinctBy { it.id }
    }
    if (!answered) throw IOException("No Audius server answered")
    return emptyList()
  }

  /** Reads one node's answer; null when it is not an answer. A malformed entry is skipped, not the whole list. */
  fun parse(body: String, node: String): List<Track>? {
    val data = try { JSONObject(body).optJSONArray("data") } catch (_: Exception) { null } ?: return null
    val out = ArrayList<Track>()
    for (i in 0 until data.length()) {
      val item = data.optJSONObject(i) ?: continue
      val id = item.optString("id").ifBlank { item.optInt("track_id", 0).toString() }
      if (id.isBlank() || id == "0") continue
      if (!item.optBoolean("is_streamable", true)) continue
      val genre = item.optString("genre", "Audius Music")
      out += Track(
        id = "audius_$id",
        title = item.optString("title", "Untitled"),
        artist = item.optJSONObject("user")?.optString("name", "Audius artist") ?: "Audius artist",
        album = "Audius • $genre",
        durationMs = item.optLong("duration", 180L) * 1000L,
        genre = genre,
        era = "Audius Streaming",
        isLocalFile = false,
        filePath = "$node/tracks/$id/stream?app_name=$APP_NAME&api_key=$API_KEY",
        colorHex = COLORS[Math.floorMod(id.hashCode(), COLORS.size)],
        artworkUrl = item.optJSONObject("artwork")?.let { art ->
          listOf("1000x1000", "480x480", "150x150").firstNotNullOfOrNull { s -> art.optString(s).ifBlank { null } }
        }
      )
    }
    return out
  }
}

/** Jamendo said no: the client id is missing or refused. Not a network problem. */
class JamendoNotConfigured : Exception("Jamendo client id missing or refused")

/**
 * Jamendo (https://developer.jamendo.com/v3.0/docs): independent music under Creative Commons. Every call needs
 * a client id, a public identifier from Jamendo's developer portal. It is read from the environment variable
 * JAMENDO_CLIENT_ID or from [clientIdOverride] (a setting); without one, Jamendo results say so.
 */
object JamendoCatalog {
  private const val BASE = "https://api.jamendo.com/v3.0"
  private const val ATTEMPTS = 4
  private const val RETRY_DELAY_MS = 300L
  private val COLORS = listOf(0xFFF59E0B, 0xFF10B981, 0xFF38BDF8, 0xFFA855F7, 0xFFFA243C, 0xFF00E5FF)

  @Volatile var clientIdOverride: String? = null

  private val clientId: String get() = (clientIdOverride ?: System.getenv("JAMENDO_CLIENT_ID") ?: "").trim()
  val isConfigured: Boolean get() = clientId.isNotEmpty() && clientId != "MY_JAMENDO_CLIENT_ID"

  suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    val q = query.trim()
    firstNonEmpty { attempt -> fetch(if (attempt % 2 == 0) "search" to q else "namesearch" to q, "order" to "relevance") }
  }

  suspend fun popular(): List<Track> = withContext(Dispatchers.IO) {
    firstNonEmpty { fetch("order" to "popularity_week") }
  }

  /** Jamendo answers an empty list to about a third of identical requests: an empty answer is asked again. */
  private suspend fun firstNonEmpty(request: (Int) -> List<Track>): List<Track> {
    repeat(ATTEMPTS - 1) { attempt ->
      request(attempt).let { if (it.isNotEmpty()) return it }
      delay(RETRY_DELAY_MS)
    }
    return request(ATTEMPTS - 1)
  }

  private fun fetch(vararg params: Pair<String, String>): List<Track> {
    if (!isConfigured) throw JamendoNotConfigured()
    val query = buildList {
      add("client_id" to clientId); add("format" to "json"); add("limit" to "20"); add("include" to "musicinfo")
      add("audioformat" to "mp32"); add("imagesize" to "600"); addAll(params)
    }.joinToString("&") { (k, v) -> "$k=${CatalogHttp.enc(v)}" }
    val (code, body) = CatalogHttp.get("$BASE/tracks/?$query")
    if (code !in 200..299) throw IOException("Jamendo answered $code")
    return parse(body)
  }

  fun parse(body: String): List<Track> {
    val json = JSONObject(body)
    val headers = json.optJSONObject("headers")
    if (headers?.optString("status") == "failed") {
      val message = headers.optString("error_message")
      if (message.contains("client", ignoreCase = true)) throw JamendoNotConfigured()
      throw IOException("Jamendo failed: $message")
    }
    val results = json.optJSONArray("results") ?: return emptyList()
    val tracks = ArrayList<Track>()
    for (i in 0 until results.length()) {
      val item = results.optJSONObject(i) ?: continue
      val id = item.optString("id")
      val audio = item.optString("audio")
      if (id.isBlank() || !audio.startsWith("https://")) continue
      val genre = item.optJSONObject("musicinfo")?.optJSONObject("tags")?.optJSONArray("genres")
        ?.optString(0)?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Jamendo"
      tracks += Track(
        id = "jamendo_$id",
        title = decode(item.optString("name")).ifBlank { "Untitled" },
        artist = decode(item.optString("artist_name")).ifBlank { "Jamendo artist" },
        album = decode(item.optString("album_name")).ifBlank { "Jamendo" },
        durationMs = item.optLong("duration", 0L) * 1000L,
        genre = genre,
        era = item.optString("releasedate").take(4).ifBlank { "Jamendo" },
        isLocalFile = false,
        filePath = audio,
        colorHex = COLORS[Math.floorMod(id.hashCode(), COLORS.size)],
        artworkUrl = item.optString("album_image").ifBlank { item.optString("image") }.ifBlank { null }
      )
    }
    return tracks.distinctBy { it.id }
  }

  /** Jamendo sends some names HTML-escaped ("Etude &quot;The Passion&quot;"). */
  fun decode(s: String): String = Regex("&(#x?[0-9a-fA-F]+|[a-z]+);").replace(s) { m ->
    val e = m.groupValues[1]
    when {
      e == "amp" -> "&"; e == "lt" -> "<"; e == "gt" -> ">"; e == "quot" -> "\""; e == "apos" -> "'"; e == "nbsp" -> " "
      e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
      e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
      else -> m.value
    }
  }.trim()
}

/** What MusicBrainz knows about a recording, for the track details. */
data class RecordingInfo(
  val mbid: String, val title: String, val artist: String, val release: String?, val releaseType: String?,
  val firstReleaseDate: String?, val lengthMs: Long?, val tags: List<String>, val coverUrl: String?, val pageUrl: String
)

/**
 * MusicBrainz (https://musicbrainz.org/doc/MusicBrainz_API): looks a song up by title and artist. It asks for a
 * User-Agent that names the app and at most one request per second, so calls go one at a time and are cached.
 */
object MusicBrainzCatalog {
  private const val BASE = "https://musicbrainz.org/ws/2"
  private const val MIN_GAP_MS = 1100L
  private const val MIN_SCORE = 80
  private const val USER_AGENT = "TARKAS-Windows/2.1 ( https://github.com/totally-accurate-labs )"

  private val gate = Mutex()
  private var lastCallAt = 0L
  private val cache = object : LinkedHashMap<String, RecordingInfo?>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, RecordingInfo?>?) = size > 64
  }

  suspend fun lookup(title: String, artist: String): RecordingInfo? = withContext(Dispatchers.IO) {
    val key = "${title.trim().lowercase()}|${artist.trim().lowercase()}"
    synchronized(cache) { if (cache.containsKey(key)) return@withContext cache[key] }
    val found = gate.withLock {
      val wait = lastCallAt + MIN_GAP_MS - System.currentTimeMillis()
      if (wait > 0) delay(wait)
      try { search(title, artist) } finally { lastCallAt = System.currentTimeMillis() }
    }
    synchronized(cache) { cache[key] = found }
    found
  }

  private suspend fun search(title: String, artist: String): RecordingInfo? {
    val base = buildString {
      append("recording:\"").append(escape(title)).append('"')
      if (artist.isNotBlank()) append(" AND artist:\"").append(escape(artist)).append('"')
    }
    request("$base AND status:official AND NOT secondarytype:live AND NOT secondarytype:compilation")?.let { return it }
    delay(MIN_GAP_MS)
    return request(base)
  }

  private fun request(query: String): RecordingInfo? {
    val url = "$BASE/recording?query=${CatalogHttp.enc(query)}&fmt=json&limit=5"
    repeat(2) { attempt ->
      val (code, body) = CatalogHttp.get(url, mapOf("User-Agent" to USER_AGENT))
      when {
        code == 503 && attempt == 0 -> Thread.sleep(MIN_GAP_MS)
        code !in 200..299 -> throw IOException("MusicBrainz answered $code")
        else -> return parseBest(body)
      }
    }
    throw IOException("MusicBrainz is busy")
  }

  private fun escape(text: String): String =
    text.trim().replace(Regex("""([+\-!(){}\[\]^"~*?:\\/]|&&|\|\|)"""), """\\$1""")

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
    val releases = r.optJSONArray("releases")
    var release: JSONObject? = null
    for (i in 0 until (releases?.length() ?: 0)) {
      val rel = releases!!.optJSONObject(i) ?: continue
      val date = rel.optString("date")
      val current = release?.optString("date").orEmpty()
      if (release == null || (date.isNotBlank() && (current.isBlank() || date < current))) release = rel
    }
    val tags = r.optJSONArray("tags")?.let { arr ->
      (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.sortedByDescending { it.optInt("count") }
        .map { it.optString("name") }.filter { it.isNotBlank() }.take(5)
    }.orEmpty()
    val releaseId = release?.optString("id")?.ifBlank { null }
    return RecordingInfo(
      mbid, r.optString("title"), artist, release?.optString("title")?.ifBlank { null },
      release?.optJSONObject("release-group")?.optString("primary-type")?.ifBlank { null },
      r.optString("first-release-date").ifBlank { release?.optString("date") }?.ifBlank { null },
      r.optLong("length", 0L).takeIf { it > 0 }, tags,
      releaseId?.let { "https://coverartarchive.org/release/$it/front-500" }, "https://musicbrainz.org/recording/$mbid"
    )
  }
}
