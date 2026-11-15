package com.example.data.library

import com.example.data.TextMatch
import com.example.data.model.Track
import com.example.data.playback.LibrarySort
import com.example.data.playback.SongOrder
import java.text.Collator

/** A group of songs shown as one row: an album, an artist or a genre. [key] is stable for the same group. */
sealed interface LibraryGroup {
  val key: String
  val name: String
  val tracks: List<Track>
}

/** [artist] is null when the songs of the album have more than one artist (a compilation). */
data class AlbumGroup(
  override val key: String,
  override val name: String,
  val artist: String?,
  /** The year of the first song that has one; 0 when none has. */
  val year: Int,
  override val tracks: List<Track>
) : LibraryGroup

data class ArtistGroup(
  override val key: String,
  override val name: String,
  val albumCount: Int,
  override val tracks: List<Track>
) : LibraryGroup

data class GenreGroup(
  override val key: String,
  override val name: String,
  override val tracks: List<Track>
) : LibraryGroup

/**
 * Groups the songs of the library into albums, artists and genres. Pure, so the rules are tested on the
 * JVM. Lists are sorted by name the way the library sorts songs (accents and case ignored, a leading
 * "The" skipped); the songs of an album are in disc and track order.
 *
 * A song with no album is in no album (it is in its artist and in Songs); a song with no genre is in no
 * genre. Names that differ only in case, accents or spaces at the edges are one group.
 */
object LibraryGroups {
  private fun collator() = Collator.getInstance().apply { strength = Collator.PRIMARY }

  private fun key(name: String) = TextMatch.fold(name.trim())

  /** The spelling used most among [names], the first one on a tie. */
  private fun commonName(names: List<String>): String =
    names.groupingBy { it.trim() }.eachCount().entries.maxByOrNull { it.value }?.key ?: ""

  private fun <T : LibraryGroup> byName(groups: List<T>): List<T> {
    val c = collator()
    return groups.sortedWith(compareBy({ c.getCollationKey(LibrarySort.sortName(it.name)) }, { it.key }))
  }

  private fun inAlbumOrder(tracks: List<Track>) =
    tracks.sortedWith(compareBy<Track>({ it.discNumber }, { it.trackNumber }).thenBy { LibrarySort.sortName(it.title) }.thenBy { it.id })

  fun albums(tracks: List<Track>): List<AlbumGroup> =
    tracks.filter { it.album.isNotBlank() }
      .groupBy { it.albumId?.let { id -> "album:$id" } ?: "album:${key(it.album)}" }
      .map { (k, list) ->
        val artists = list.map { key(it.artist) }.toSet()
        AlbumGroup(
          key = k,
          name = commonName(list.map { it.album }),
          artist = if (artists.size == 1) commonName(list.map { it.artist }) else null,
          year = list.firstNotNullOfOrNull { it.era.toIntOrNull()?.takeIf { y -> y > 0 } } ?: 0,
          tracks = inAlbumOrder(list)
        )
      }
      .let(::byName)

  fun artists(tracks: List<Track>): List<ArtistGroup> =
    tracks.filter { it.artist.isNotBlank() }
      .groupBy { "artist:${key(it.artist)}" }
      .map { (k, list) ->
        ArtistGroup(
          key = k,
          name = commonName(list.map { it.artist }),
          albumCount = list.filter { it.album.isNotBlank() }
            .map { it.albumId?.toString() ?: key(it.album) }.toSet().size,
          tracks = LibrarySort.sorted(list, SongOrder.ARTIST)
        )
      }
      .let(::byName)

  fun genres(tracks: List<Track>): List<GenreGroup> =
    tracks.filter { it.genre.isNotBlank() }
      .groupBy { "genre:${key(it.genre)}" }
      .map { (k, list) ->
        GenreGroup(k, commonName(list.map { it.genre }), LibrarySort.sorted(list, SongOrder.ARTIST))
      }
      .let(::byName)
}
