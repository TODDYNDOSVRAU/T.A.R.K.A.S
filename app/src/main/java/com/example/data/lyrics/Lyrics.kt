package com.example.data.lyrics

/** One line of a song's lyrics. [timeMs] is null when the source carries no timing. */
data class LyricLine(val timeMs: Long?, val text: String)

/** Where the lyrics came from. */
enum class LyricsSource { EMBEDDED_SYNCED, EMBEDDED_PLAIN, SIDECAR_LRC }

/** The lyrics of one song. [synced] is true when every line has a time and the lines are in time order. */
data class Lyrics(val lines: List<LyricLine>, val source: LyricsSource) {
  val synced: Boolean get() = lines.isNotEmpty() && lines.all { it.timeMs != null }

  /** The line that is being sung at [positionMs]: the last one whose time has passed, or -1 before the first. */
  fun activeIndex(positionMs: Long): Int {
    if (!synced) return -1
    var lo = 0
    var hi = lines.lastIndex
    var found = -1
    while (lo <= hi) {
      val mid = (lo + hi) ushr 1
      if (lines[mid].timeMs!! <= positionMs) {
        found = mid
        lo = mid + 1
      } else {
        hi = mid - 1
      }
    }
    return found
  }
}

/** Which of the two sources to show: timed lyrics first (the file beside the song before the tag), plain ones after. */
object LyricsChoice {
  fun best(embedded: Lyrics?, sidecar: Lyrics?): Lyrics? = when {
    sidecar?.synced == true -> sidecar
    embedded?.synced == true -> embedded
    sidecar != null -> sidecar
    else -> embedded
  }
}
