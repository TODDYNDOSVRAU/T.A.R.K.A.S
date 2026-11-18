package com.example.data.lyrics

/**
 * Reads the text of a `.lrc` file: `[mm:ss.xx] words` lines, several times on one line, an optional
 * `[offset:ms]`, word marks `<mm:ss.xx>` (dropped, the line keeps its own time) and information tags
 * such as `[ar:...]` (ignored). A file with no timed line is read as plain lyrics, line by line.
 */
object LrcParser {
  private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
  private val OFFSET = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
  private val TAG = Regex("""^\[[A-Za-z#]+:[^\]]*]\s*$""")
  private val WORD_MARK = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

  fun parse(raw: String, source: LyricsSource = LyricsSource.SIDECAR_LRC): Lyrics? {
    val text = raw.removePrefix("\uFEFF")
    val rawLines = text.split('\n', '\r').map { it.trimEnd() }
    var offset = 0L
    val timed = ArrayList<LyricLine>()
    val plain = ArrayList<String>()
    for (line in rawLines) {
      val trimmed = line.trim()
      if (trimmed.isEmpty()) {
        plain.add("")
        continue
      }
      val off = OFFSET.find(trimmed)
      if (off != null) {
        offset = off.groupValues[1].toLong()
        continue
      }
      val stamps = ArrayList<Long>()
      var rest = trimmed
      while (true) {
        val m = TIME.find(rest)
        if (m == null || m.range.first != 0) break
        stamps.add(toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3]))
        rest = rest.substring(m.range.last + 1).trimStart()
      }
      if (stamps.isEmpty()) {
        if (!TAG.matches(trimmed)) plain.add(WORD_MARK.replace(trimmed, "").trim())
        continue
      }
      val words = WORD_MARK.replace(rest, "").trim()
      stamps.forEach { timed.add(LyricLine(it, words)) }
    }
    if (timed.isNotEmpty()) {
      // Stable sort: two lines at one instant keep the file's order.
      // A positive offset makes the words appear sooner, as players of the format do.
      val shifted = timed.map { LyricLine((it.timeMs!! - offset).coerceAtLeast(0L), it.text) }
      return Lyrics(shifted.sortedBy { it.timeMs }, source)
    }
    val lines = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
    if (lines.none { it.isNotEmpty() }) return null
    return Lyrics(lines.map { LyricLine(null, it) }, source)
  }

  private fun toMs(min: String, sec: String, frac: String): Long {
    val fraction = when (frac.length) {
      0 -> 0L
      1 -> frac.toLong() * 100
      2 -> frac.toLong() * 10
      else -> frac.take(3).toLong()
    }
    return min.toLong() * 60_000 + sec.toLong() * 1000 + fraction
  }
}
