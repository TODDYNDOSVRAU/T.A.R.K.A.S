package com.example.data.library

import com.example.data.model.Track

/** What the media library says changed about a file: when it was modified and its generation (Android 11+; 0 before). */
data class Stamp(val modified: Long, val generation: Long)

/** One song read from the media library, with the folder it lives in. */
class ScannedSong(val mediaId: Long, val track: Track, val folder: String, val stamp: Stamp)

/** What differs between the songs known and the ones the media library lists now. */
class MediaDiff(val added: Set<Long>, val changed: Set<Long>, val removed: Set<Long>) {
  val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()

  /** The songs whose rows have to be read again. */
  val toRead: Set<Long> get() = added + changed

  /** True when most of the library changed (or it is the first read): one query for all is cheaper. */
  fun readsAll(known: Int): Boolean = known == 0 || toRead.size > FULL_READ_ABOVE

  companion object {
    const val FULL_READ_ABOVE = 400

    fun of(known: Map<Long, Stamp>, current: Map<Long, Stamp>): MediaDiff = MediaDiff(
      added = current.keys - known.keys,
      changed = current.filter { (id, s) -> known[id]?.let { it != s } == true }.keys,
      removed = known.keys - current.keys
    )
  }
}

/** The device songs as last read: sorted tracks, their folders, and the stamp of each. Immutable. */
class DeviceCatalog(
  val tracks: List<Track> = emptyList(),
  val folderOf: Map<String, String> = emptyMap(),
  val stamps: Map<Long, Stamp> = emptyMap()
) {
  /** How many songs each folder holds. */
  val folderCounts: Map<String, Int> = tracks.groupingBy { folderOf[it.id].orEmpty() }.eachCount()

  /**
   * The catalog after a diff: removed songs go, the rows read replace or join the rest. A song in the
   * diff that was not read (the query failed) keeps its old row and its old stamp, so it is read again
   * next time.
   */
  fun apply(diff: MediaDiff, read: List<ScannedSong>, order: Comparator<Track>): DeviceCatalog {
    val readIds = read.associateBy { it.track.id }
    val removedIds = diff.removed.map { "ms:$it" }.toSet()
    val kept = tracks.filter { it.id !in removedIds && it.id !in readIds }
    val merged = (kept + read.map { it.track }).sortedWith(order)
    val folders = HashMap<String, String>(folderOf).apply {
      removedIds.forEach { remove(it) }
      read.forEach { put(it.track.id, it.folder) }
    }
    val newStamps = HashMap(stamps).apply {
      diff.removed.forEach { remove(it) }
      read.forEach { put(it.mediaId, it.stamp) }
    }
    return DeviceCatalog(merged, folders, newStamps)
  }
}
