package com.example.windows.assistente
// Port of app/.../assistente/funcoes (win-assistente). Android file not shareable: it names Android-only types in the same package.

import com.example.data.model.Track
import java.text.Normalizer

/**
 * A chat request that changes the app's state (a sleep timer, the queue, spatial audio, the device volume,
 * or starting something from the library). The engine asks the user to confirm each one before it runs.
 */
sealed interface AssistantRequest {
  data class PlayLibrary(val query: String) : AssistantRequest
  data class QueueLibrary(val query: String, val asNext: Boolean) : AssistantRequest
  data object ClearQueue : AssistantRequest
  data class SleepIn(val minutes: Int) : AssistantRequest
  data object SleepAtEndOfTrack : AssistantRequest
  data object CancelSleep : AssistantRequest
  data class Spatial(val on: Boolean) : AssistantRequest
  data class Volume(val percent: Int) : AssistantRequest
}

/** A playlist of the library with its tracks already resolved (ids that no longer exist are dropped). */
data class LibraryPlaylist(val name: String, val tracks: List<Track>)

/** One thing the library search found: a playlist, an album, an artist's songs or a single track. */
data class LibraryMatch(val kind: Kind, val label: String, val tracks: List<Track>) {
  enum class Kind { PLAYLIST, ALBUM, ARTIST, TRACK }
}

/** Lowercase, without accents and with single spaces, so "Clair de Lune" and "clair de lune" match. */
internal fun normalizeForSearch(text: String): String =
  Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase()
    .replace(Regex("\\s+"), " ")
    .trim()

private fun has(text: String, words: String) = Regex("\\b($words)\\b").containsMatchIn(text)

/**
 * Turns a sentence into a state-changing request, or null. Only clear phrasings match: anything else
 * returns null, so the engine answers "não entendi" instead of guessing what the user meant.
 * [lower] is the sentence in lowercase.
 */
internal fun assistantRequestFor(lower: String): AssistantRequest? {
  val text = lower.trim()

  // Sleep timer. "dormir" or "sleep", or "desligar/parar/pausar a música", with a time or "fim da faixa".
  val sleepish = has(text, "dormir|sleep") || Regex("\\b(desligar|parar|pausar) a m[úu]sica\\b").containsMatchIn(text)
  if (sleepish) {
    if (has(text, "cancelar|cancela|cancele|tirar|remover") && !Regex("\\d").containsMatchIn(text)) {
      return AssistantRequest.CancelSleep
    }
    if (Regex("\\bfim (d[aoe]s?|desta|deste) (faixa|m[úu]sica|música)s?\\b|\\bfaixa atual\\b").containsMatchIn(text)) {
      return AssistantRequest.SleepAtEndOfTrack
    }
    val minutes = Regex("(\\d{1,3})\\s*(min|minutos?)\\b").find(text)?.groupValues?.get(1)?.toIntOrNull()
    if (minutes != null && minutes in 1..MAX_SLEEP_MINUTES) return AssistantRequest.SleepIn(minutes)
    return null
  }

  // Spatial audio. "desligar" is checked first; the \b keeps "desligar" from matching "ligar".
  if (has(text, "espacial|espaciais")) {
    return when {
      has(text, "desligar|desligue|desativar|desative") -> AssistantRequest.Spatial(on = false)
      has(text, "ligar|ligue|ativar|ative") -> AssistantRequest.Spatial(on = true)
      else -> null
    }
  }

  // Device volume: "volume 40", "volume para 40%".
  if (has(text, "volume")) {
    val percent = Regex("\\bvolume\\b.*?(\\d{1,3})").find(text)?.groupValues?.get(1)?.toIntOrNull()
    return if (percent != null && percent in 0..100) AssistantRequest.Volume(percent) else null
  }

  // Queue.
  if (Regex("\\b(limpar|esvaziar) (a )?fila\\b").containsMatchIn(text)) return AssistantRequest.ClearQueue
  Regex("\\b(adicionar|adicione|adiciona|coloque|coloca|p[õo]e)\\s+(.+?)\\s+(na|à|a) fila\\b").find(text)?.let {
    val query = it.groupValues[2].trim()
    if (query.isNotEmpty()) return AssistantRequest.QueueLibrary(query, asNext = false)
  }
  Regex("\\b(tocar|toca|toque)\\s+(.+?)\\s+(depois|em seguida|a seguir)\\b").find(text)?.let {
    val query = it.groupValues[2].trim()
    if (query.isNotEmpty()) return AssistantRequest.QueueLibrary(query, asNext = true)
  }

  // "tocar <nome>": a track, album, artist or playlist. "tocar" alone is the play command, handled elsewhere.
  Regex("^(tocar|toca|toque)\\s+(?:a |o |as |os |um |uma )?(.+)$").find(text)?.let {
    val query = it.groupValues[2].trim().trimEnd('?', '.', '!')
    val isCommandWord = isCommandPhrase(query) || query in setOf("agora", "musica", "música", "faixa", "som")
    if (query.isNotEmpty() && !isCommandWord && !query.startsWith("que ")) return AssistantRequest.PlayLibrary(query)
  }
  return null
}

/**
 * Finds the library items whose name contains [query]. An exact name wins over partial ones, so "Tarkus"
 * finds the album "Tarkus", not every track that has the word. Several results are returned as they are:
 * the caller asks the user to choose instead of picking one.
 */
internal fun findInLibrary(query: String, tracks: List<Track>, playlists: List<LibraryPlaylist>): List<LibraryMatch> {
  val q = normalizeForSearch(query)
  if (q.isEmpty()) return emptyList()
  val found = mutableListOf<LibraryMatch>()

  playlists.filter { it.tracks.isNotEmpty() && normalizeForSearch(it.name).contains(q) }
    .forEach { found += LibraryMatch(LibraryMatch.Kind.PLAYLIST, it.name, it.tracks) }

  tracks.filter { it.album.isNotBlank() && normalizeForSearch(it.album).contains(q) }
    .groupBy { normalizeForSearch(it.album) }
    .forEach { (_, group) -> found += LibraryMatch(LibraryMatch.Kind.ALBUM, group.first().album, group) }

  tracks.filter { it.artist.isNotBlank() && normalizeForSearch(it.artist).contains(q) }
    .groupBy { normalizeForSearch(it.artist) }
    .forEach { (_, group) -> found += LibraryMatch(LibraryMatch.Kind.ARTIST, group.first().artist, group) }

  tracks.filter { normalizeForSearch(it.title).contains(q) }
    .forEach { found += LibraryMatch(LibraryMatch.Kind.TRACK, it.title, listOf(it)) }

  val exact = found.filter { normalizeForSearch(it.label) == q }
  return exact.ifEmpty { found }
}

internal const val MAX_SLEEP_MINUTES = 600
