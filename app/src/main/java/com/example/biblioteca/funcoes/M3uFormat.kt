package com.example.data.library

import com.example.data.TextMatch
import com.example.data.model.Track
import java.net.URLDecoder

/**
 * Standard M3U / M3U8 playlist export and import.
 *
 * Export writes `#EXTM3U`, `#PLAYLIST:<name>`, and `#EXTINF:<sec>,<artist> - <title>` followed by
 * the track's file path or URI. Import matches lines against the local library by exact path,
 * filename, or `#EXTINF` title/artist metadata. Pure Kotlin so all rules are unit-tested on JVM.
 */
object M3uFormat {
  fun export(playlistName: String, tracks: List<Track>): String {
    val sb = StringBuilder()
    sb.append("#EXTM3U\n")
    if (playlistName.isNotBlank()) {
      sb.append("#PLAYLIST:").append(playlistName.replace('\n', ' ').trim()).append('\n')
    }
    for (track in tracks) {
      val sec = (track.durationMs / 1000L).coerceAtLeast(-1L)
      val display = if (track.artist.isNotBlank()) "${track.artist} - ${track.title}" else track.title
      sb.append("#EXTINF:").append(sec).append(',').append(display.replace('\n', ' ')).append('\n')
      val loc = track.filePath?.takeIf { it.isNotBlank() } ?: track.id
      sb.append(loc).append('\n')
    }
    return sb.toString()
  }

  /**
   * Parses an M3U / M3U8 document and resolves entries against [library], preserving order and
   * deduplicating by track ID.
   */
  fun parse(text: String, library: List<Track>): List<Track> {
    if (text.isBlank() || library.isEmpty()) return emptyList()

    val byFullPath = HashMap<String, Track>(library.size)
    val byFileName = HashMap<String, Track>(library.size)
    val byFoldedArtistTitle = HashMap<String, Track>(library.size)
    val byFoldedTitle = HashMap<String, Track>(library.size)

    for (t in library) {
      val path = t.filePath?.takeIf { it.isNotBlank() }
      if (path != null) {
        val norm = normalizePath(path)
        byFullPath.putIfAbsent(norm, t)
        val fileName = fileNameOf(norm)
        if (fileName.isNotEmpty()) {
          byFileName.putIfAbsent(fileName, t)
        }
      }
      val foldedTitle = TextMatch.fold(t.title)
      if (foldedTitle.isNotEmpty()) {
        byFoldedTitle.putIfAbsent(foldedTitle, t)
        val foldedArtist = TextMatch.fold(t.artist)
        if (foldedArtist.isNotEmpty()) {
          byFoldedArtistTitle.putIfAbsent("$foldedArtist - $foldedTitle", t)
        }
      }
    }

    val result = ArrayList<Track>()
    val seenIds = HashSet<String>()
    var pendingExtInf: String? = null

    for (rawLine in text.lineSequence()) {
      val line = rawLine.trim().removePrefix("\uFEFF")
      if (line.isEmpty()) continue
      if (line.startsWith("#EXTINF:", ignoreCase = true)) {
        pendingExtInf = line.substringAfter(',', "").trim().takeIf { it.isNotEmpty() }
        continue
      }
      if (line.startsWith("#")) continue

      val norm = normalizePath(line)
      val fileName = fileNameOf(norm)
      val stem = fileName.substringBeforeLast('.')

      val matched = byFullPath[norm]
        ?: byFileName[fileName]
        ?: pendingExtInf?.let { ext ->
          val foldedExt = TextMatch.fold(ext)
          byFoldedArtistTitle[foldedExt]
            ?: byFoldedTitle[foldedExt]
            ?: ext.substringAfter(" - ", "").takeIf { it.isNotBlank() }?.let { byFoldedTitle[TextMatch.fold(it)] }
        }
        ?: stem.takeIf { it.isNotEmpty() }?.let { byFoldedTitle[TextMatch.fold(it)] }

      pendingExtInf = null
      if (matched != null && seenIds.add(matched.id)) {
        result += matched
      }
    }
    return result
  }

  private fun normalizePath(raw: String): String {
    val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    return decoded.replace('\\', '/').trim().lowercase()
  }

  private fun fileNameOf(normalizedPath: String): String =
    normalizedPath.substringAfterLast('/').substringBefore('?').trim()
}
