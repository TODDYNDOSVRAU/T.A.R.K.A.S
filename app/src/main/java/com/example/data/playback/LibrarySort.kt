package com.example.data.playback

import com.example.data.TextMatch
import com.example.data.model.Track
import java.text.Collator

/** How the device's songs are listed in Biblioteca. */
enum class SongOrder(val key: String, val label: String) {
  TITLE("title", "Título"),
  ARTIST("artist", "Artista"),
  ALBUM("album", "Álbum"),
  /** Newest files first. */
  RECENT("recent", "Recentes"),
  /** Most plays first (counted on this device, see PlayRules). */
  MOST_PLAYED("most_played", "Mais tocadas");

  companion object {
    fun fromKey(key: String?): SongOrder = entries.firstOrNull { it.key == key } ?: TITLE
  }
}

/**
 * Sorting and the A–Z index. Names are compared as people read them: accents and case ignored, and a
 * leading English article ("The Beatles") sorted under the word after it, as music libraries do.
 * Albums and artists keep their tracks in disc and track order.
 */
object LibrarySort {

  private val ARTICLES = listOf("the ", "a ", "an ")

  /** The name a list is sorted by: folded, without a leading English article. */
  fun sortName(name: String): String {
    val folded = TextMatch.fold(name.trim())
    val article = ARTICLES.firstOrNull { folded.startsWith(it) && folded.length > it.length }
    return if (article != null) folded.substring(article.length) else folded
  }

  fun sorted(tracks: List<Track>, order: SongOrder, plays: Map<String, Int> = emptyMap()): List<Track> {
    if (order == SongOrder.RECENT) return tracks.sortedWith(compareByDescending<Track> { it.dateAddedSec }.thenBy { sortName(it.title) })
    if (order == SongOrder.MOST_PLAYED) return tracks.sortedWith(compareByDescending<Track> { plays[it.id] ?: 0 }.thenBy { sortName(it.title) })
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    // Collation keys once per track: comparing them is cheap, building them is not.
    val keyed = tracks.map { t ->
      val primary = when (order) {
        SongOrder.TITLE -> t.title
        SongOrder.ARTIST -> t.artist
        SongOrder.ALBUM -> t.album.ifBlank { "￿" }
        SongOrder.RECENT, SongOrder.MOST_PLAYED -> t.title
      }
      Triple(t, collator.getCollationKey(sortName(primary)), collator.getCollationKey(sortName(t.album)))
    }
    val byPrimary = compareBy<Triple<Track, java.text.CollationKey, java.text.CollationKey>> { it.second }
    val comparator = when (order) {
      SongOrder.ARTIST -> byPrimary.thenBy { it.third }.thenBy { it.first.discNumber }.thenBy { it.first.trackNumber }
      SongOrder.ALBUM -> byPrimary.thenBy { it.first.discNumber }.thenBy { it.first.trackNumber }
      else -> byPrimary
    }.thenBy { sortName(it.first.title) }
    return keyed.sortedWith(comparator).map { it.first }
  }

  /** The index letter of [track] in [order]: "A" to "Z", "#" for digits and symbols; null without an index. */
  fun section(track: Track, order: SongOrder): String? {
    val name = when (order) {
      SongOrder.TITLE -> track.title
      SongOrder.ARTIST -> track.artist
      SongOrder.ALBUM -> track.album
      SongOrder.RECENT, SongOrder.MOST_PLAYED -> return null
    }
    val c = sortName(name).firstOrNull() ?: return "#"
    return if (c in 'a'..'z') c.uppercaseChar().toString() else "#"
  }
}
