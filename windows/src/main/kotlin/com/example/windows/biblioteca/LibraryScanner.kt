package com.example.windows.biblioteca

import com.example.data.library.DeviceCatalog
import com.example.data.library.FolderPaths
import com.example.data.library.MediaDiff
import com.example.data.library.ScannedSong
import com.example.data.library.Stamp
import com.example.data.model.Track
import com.example.data.model.TrackJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.Collator

/**
 * Reads the music folders of the computer into a [DeviceCatalog], the same shape the phone builds from its
 * media library. A file is identified by the hash of its path and stamped with its modification time and size,
 * so a second scan only reads the tags of files that are new or changed ([MediaDiff]).
 */
class LibraryScanner(private val cacheFile: File? = null) {

  /** What is listed on disk right now. */
  class Listing(val stamps: Map<Long, Stamp>, val files: Map<Long, File>, val folders: Map<Long, String>)

  /** The folder name shown in the folder picker for each root; two roots with one name get "~2", "~3"... */
  fun rootLabels(roots: List<File>): Map<File, String> {
    val used = HashMap<String, Int>()
    return roots.associateWith { r ->
      val base = r.name.ifBlank { r.path.trimEnd('\\', '/').replace(":", "") }
      val n = (used[base] ?: 0) + 1
      used[base] = n
      if (n == 1) base else "$base~$n"
    }
  }

  fun list(roots: List<File>): Listing {
    val stamps = HashMap<Long, Stamp>()
    val files = HashMap<Long, File>()
    val folders = HashMap<Long, String>()
    for ((root, label) in rootLabels(roots)) {
      if (!root.isDirectory) continue
      val rootPath = root.toPath()
      root.walkTopDown().onEnter { it == root || !it.name.startsWith(".") }.forEach { f ->
        if (!f.isFile || !TagReader.isAudio(f)) return@forEach
        val id = idOf(f)
        if (id in files) return@forEach // the same file reached through two roots
        val rel = runCatching { rootPath.relativize(f.parentFile.toPath()).toString() }.getOrDefault("")
        files[id] = f
        stamps[id] = Stamp(f.lastModified(), f.length())
        folders[id] = FolderPaths.normalize(if (rel.isEmpty()) label else "$label/$rel")
      }
    }
    return Listing(stamps, files, folders)
  }

  /** The catalog the cache file holds, or an empty one. */
  fun loadCache(): DeviceCatalog {
    val file = cacheFile?.takeIf { it.isFile } ?: return DeviceCatalog()
    return try {
      val root = JSONObject(file.readText())
      val rows = root.optJSONArray("songs") ?: return DeviceCatalog()
      val tracks = ArrayList<Track>()
      val folders = HashMap<String, String>()
      val stamps = HashMap<Long, Stamp>()
      for (i in 0 until rows.length()) {
        val o = rows.optJSONObject(i) ?: continue
        val track = TrackJson.fromJson(o.getJSONObject("t")) ?: continue
        val id = o.getLong("id")
        tracks += track
        folders[track.id] = o.optString("f")
        stamps[id] = Stamp(o.getLong("m"), o.getLong("s"))
      }
      DeviceCatalog(tracks, folders, stamps)
    } catch (_: Exception) {
      DeviceCatalog()
    }
  }

  fun saveCache(catalog: DeviceCatalog) {
    val file = cacheFile ?: return
    try {
      val byTrack = catalog.stamps.mapKeys { "ms:${it.key}" }
      val rows = JSONArray()
      for (t in catalog.tracks) {
        val id = t.id.removePrefix("ms:").toLongOrNull() ?: continue
        val st = byTrack[t.id] ?: continue
        rows.put(JSONObject().put("id", id).put("m", st.modified).put("s", st.generation)
          .put("f", catalog.folderOf[t.id].orEmpty()).put("t", TrackJson.toJson(t)))
      }
      file.parentFile?.mkdirs()
      val tmp = File(file.path + ".tmp")
      tmp.writeText(JSONObject().put("v", 1).put("songs", rows).toString())
      java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } catch (_: Exception) {
      // a cache that cannot be written only costs a full read next time
    }
  }

  /** Brings [known] up to date with what is on disk under [roots]. Returns the same object when nothing changed. */
  fun scan(roots: List<File>, known: DeviceCatalog, onProgress: (Int) -> Unit = {}): DeviceCatalog {
    val listing = list(roots)
    val diff = MediaDiff.of(known.stamps, listing.stamps)
    if (diff.isEmpty) return known
    val read = ArrayList<ScannedSong>()
    for (id in diff.toRead) {
      val file = listing.files[id] ?: continue
      read += readSong(id, file, listing.folders[id].orEmpty(), listing.stamps.getValue(id))
      if (read.size % 25 == 0) onProgress(read.size)
    }
    return known.apply(diff, read, ORDER)
  }

  fun readSong(id: Long, file: File, folder: String, stamp: Stamp): ScannedSong {
    val tags = TagReader.read(file)
    val (disc, number) = if (tags.discNumber > 0 || tags.trackNumber > 0) tags.discNumber to tags.trackNumber else (0 to 0)
    val artwork = when {
      tags.hasCover -> "tag:" + file.absolutePath
      else -> folderCover(file.parentFile)?.let { "file:" + it.absolutePath }
    }
    val track = Track(
      id = "ms:$id",
      title = tags.title.ifBlank { file.nameWithoutExtension },
      artist = tags.artist.ifBlank { UNKNOWN_ARTIST },
      album = tags.album,
      durationMs = tags.durationMs,
      genre = tags.genre,
      era = tags.year.takeIf { it > 0 }?.toString() ?: "",
      isLocalFile = true,
      filePath = file.absolutePath,
      artworkUrl = artwork,
      colorHex = 0xFF2F8FA8,
      discNumber = disc,
      trackNumber = number,
      dateAddedSec = stamp.modified / 1000L
    )
    return ScannedSong(id, track, folder, stamp)
  }

  private val coverCache = HashMap<File, File?>()

  private fun folderCover(dir: File?): File? {
    if (dir == null) return null
    return coverCache.getOrPut(dir) {
      val names = dir.listFiles { f -> f.isFile && f.extension.lowercase() in IMAGES }.orEmpty()
      COVER_NAMES.firstNotNullOfOrNull { n -> names.firstOrNull { it.nameWithoutExtension.equals(n, true) } }
    }
  }

  companion object {
    const val UNKNOWN_ARTIST = "Unknown artist"
    private val IMAGES = setOf("jpg", "jpeg", "png")
    private val COVER_NAMES = listOf("cover", "folder", "front", "album", "albumart", "albumartsmall")

    private val collator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    val ORDER: Comparator<Track> = Comparator { a, b -> collator.compare(a.title, b.title) }

    /** A stable positive id from the path, so playlists and favourites keep pointing at the file. */
    fun idOf(file: File): Long {
      val d = MessageDigest.getInstance("SHA-256").digest(file.absolutePath.lowercase().toByteArray(Charsets.UTF_8))
      var v = 0L
      for (i in 0 until 8) v = (v shl 8) or (d[i].toLong() and 0xFF)
      return v and Long.MAX_VALUE
    }
  }
}
