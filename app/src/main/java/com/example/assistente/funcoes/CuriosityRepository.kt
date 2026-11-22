package com.example.data.repository

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.example.R
import com.example.data.assistant.GEMINI_MODEL_URL
import com.example.data.assistant.geminiReplyText
import com.example.data.model.Curiosity
import com.example.data.model.Track
import com.example.data.model.VerificationStatus
import com.example.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The verified curiosities of the built-in tracks, plus an on-demand Gemini lookup cached on disk
 * (`filesDir/curiosities_cache/`) for user tracks when online AI is enabled.
 */
class CuriosityRepository(context: Context) {

  // Only Resources and filesDir are kept, never an Activity Context.
  private val resources = context.resources
  private val cacheDir = File(context.applicationContext.filesDir, "curiosities_cache")

  private class Entry(
    val trackId: String,
    val trackTitle: String,
    val artist: String,
    @StringRes val trivia: Int,
    @StringRes val facts: Int,
    @ArrayRes val sources: Int,
    val status: VerificationStatus,
    val verifiedDate: String
  )

  private val entries = listOf(
    Entry(
      trackId = "track_tarkus",
      trackTitle = "Tarkus: Eruption Suite",
      artist = "Emerson, Lake & Palmer",
      trivia = R.string.curiosity_tarkus_trivia,
      facts = R.string.curiosity_tarkus_facts,
      sources = R.array.curiosity_tarkus_sources,
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-15"
    ),
    Entry(
      trackId = "track_tubular",
      trackTitle = "Tubular Bells (Part One)",
      artist = "Mike Oldfield",
      trivia = R.string.curiosity_tubular_trivia,
      facts = R.string.curiosity_tubular_facts,
      sources = R.array.curiosity_tubular_sources,
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-10"
    ),
    Entry(
      trackId = "track_debussy",
      trackTitle = "Clair de Lune",
      artist = "Claude Debussy",
      trivia = R.string.curiosity_debussy_trivia,
      facts = R.string.curiosity_debussy_facts,
      sources = R.array.curiosity_debussy_sources,
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-09-28"
    ),
    Entry(
      trackId = "track_satie",
      trackTitle = "Gymnopédie No. 1",
      artist = "Erik Satie",
      trivia = R.string.curiosity_satie_trivia,
      facts = R.string.curiosity_satie_facts,
      sources = R.array.curiosity_satie_sources,
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-02"
    ),
    Entry(
      trackId = "track_sweep",
      trackTitle = "Soundcheck Sweep (20Hz-20kHz)",
      artist = "Tarkas Acoustic Lab",
      trivia = R.string.curiosity_sweep_trivia,
      facts = R.string.curiosity_sweep_facts,
      sources = R.array.curiosity_sweep_sources,
      status = VerificationStatus.TECHNICAL_SPEC,
      verifiedDate = "2026-10-18"
    )
  )

  // Read on each call, so the text always matches the language the app is showing now.
  private fun build(entry: Entry): Curiosity {
    val trivia = resources.getString(entry.trivia).trim()
    return Curiosity(
      trackId = entry.trackId,
      trackTitle = entry.trackTitle,
      artist = entry.artist,
      shortTrivia = trivia,
      wordCount = trivia.split(Regex("\\s+")).size,
      documentedFacts = resources.getString(entry.facts).trim(),
      sources = resources.getStringArray(entry.sources).toList(),
      status = entry.status,
      verifiedDate = entry.verifiedDate
    )
  }

  /**
   * The verified curiosity of a track, or null when there is none. Nothing is shown, spoken or put
   * in the notification for a track without one: never invent trivia or sources.
   */
  fun getCuriosityForTrack(track: Track): Curiosity? =
    entries.firstOrNull { it.trackId == track.id }?.let(::build) ?: readCache(track)

  fun getAllCuriosities(): List<Curiosity> = entries.map(::build)

  /**
   * Returns a built-in or cached curiosity if available; otherwise asks Gemini on demand for a
   * concise factual note about [track] and caches the result on disk so it never repeats the call.
   * Replies "NONE" from Gemini are discarded (T.A.L. integrity: never invent trivia).
   */
  suspend fun fetchOrGenerateForTrack(track: Track, apiKey: String): Curiosity? = withContext(Dispatchers.IO) {
    getCuriosityForTrack(track)?.let { return@withContext it }
    if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return@withContext null
    if (track.title.isBlank() || track.artist.isBlank()) return@withContext null

    val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
    val langName = if (lang.startsWith("pt")) "Portuguese" else "English"
    val prompt = "Give one concise, factual musical trivia note (max 45 words, plain prose, no markdown, no exclamation marks) about the song \"${track.title}\" by ${track.artist}. Reply strictly with NONE if you are not sure of verified facts. Write in $langName."
    val payload = JSONObject().apply {
      put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
      put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 120))
    }
    val req = Request.Builder()
      .url(GEMINI_MODEL_URL)
      .header("x-goog-api-key", apiKey)
      .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
      .build()

    val reply = runCatching {
      Http.client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        val body = resp.body?.string().orEmpty()
        geminiReplyText(body)
      }
    }.getOrNull()?.trim() ?: return@withContext null

    if (reply.equals("NONE", ignoreCase = true) || reply.length < 15) return@withContext null
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val curiosity = Curiosity(
      trackId = track.id,
      trackTitle = track.title,
      artist = track.artist,
      shortTrivia = reply,
      wordCount = reply.split(Regex("\\s+")).size,
      documentedFacts = reply,
      sources = listOf("Gemini"),
      status = VerificationStatus.HISTORICAL_ANALYSIS,
      verifiedDate = today
    )
    writeCache(track, curiosity)
    curiosity
  }

  private fun cacheFile(track: Track): File {
    val lang = Locale.getDefault().language.lowercase(Locale.ROOT).take(2).ifBlank { "en" }
    val safeId = track.id.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    return File(cacheDir, "${safeId}_$lang.json")
  }

  private fun readCache(track: Track): Curiosity? = runCatching {
    val f = cacheFile(track)
    if (!f.exists()) return null
    val o = JSONObject(f.readText())
    val trivia = o.optString("trivia").takeIf { it.isNotBlank() } ?: return null
    Curiosity(
      trackId = track.id,
      trackTitle = o.optString("title", track.title),
      artist = o.optString("artist", track.artist),
      shortTrivia = trivia,
      wordCount = trivia.split(Regex("\\s+")).size,
      documentedFacts = o.optString("facts", trivia),
      sources = listOf(o.optString("source", "Gemini")),
      status = VerificationStatus.HISTORICAL_ANALYSIS,
      verifiedDate = o.optString("date", "")
    )
  }.getOrNull()

  private fun writeCache(track: Track, c: Curiosity) {
    runCatching {
      cacheDir.mkdirs()
      val o = JSONObject()
        .put("title", c.trackTitle)
        .put("artist", c.artist)
        .put("trivia", c.shortTrivia)
        .put("facts", c.documentedFacts)
        .put("source", c.sources.firstOrNull() ?: "Gemini")
        .put("date", c.verifiedDate)
      cacheFile(track).writeText(o.toString())
    }
  }
}
