package com.example.windows.biblioteca

import com.example.data.TextMatch
import com.example.data.library.DeviceCatalog
import com.example.data.model.Track
import java.io.File

/** The columns of the track table. Title takes what the others leave; the other four have a width the user can drag. */
enum class Col(val key: String, val defaultDp: Int, val minDp: Int) {
  TITLE("title", 0, 96),
  ARTIST("artist", 192, 96),
  ALBUM("album", 192, 96),
  DURATION("duration", 96, 48),
  PLAYS("plays", 96, 48)
}

/** The column the table is sorted by and the direction. */
data class SortSpec(val col: Col, val ascending: Boolean = true) {
  /** Clicking a header: the same column flips the direction, another column starts ascending (plays start descending). */
  fun click(c: Col): SortSpec = if (c == col) copy(ascending = !ascending) else SortSpec(c, c != Col.PLAYS)

  fun encode() = col.key + ":" + (if (ascending) "asc" else "desc")

  companion object {
    fun decode(s: String?): SortSpec? {
      val (k, d) = s?.split(":", limit = 2)?.takeIf { it.size == 2 } ?: return null
      val c = Col.entries.firstOrNull { it.key == k } ?: return null
      return SortSpec(c, d != "desc")
    }
  }
}

/** Sorting, column widths, type-ahead and the import of dropped files: everything the table does that is not drawing. */
object TableLogic {
  /** [tracks] ordered by [spec]. Text columns ignore accents and case; ties keep the incoming order. */
  fun sort(tracks: List<Track>, spec: SortSpec, plays: Map<String, Int>): List<Track> {
    val cmp: Comparator<Track> = when (spec.col) {
      Col.TITLE -> compareBy { TextMatch.fold(it.title) }
      Col.ARTIST -> compareBy { TextMatch.fold(it.artist) }
      Col.ALBUM -> compareBy { TextMatch.fold(it.album) }
      Col.DURATION -> compareBy { it.durationMs }
      Col.PLAYS -> compareBy { plays[it.id] ?: 0 }
    }
    return tracks.sortedWith(if (spec.ascending) cmp else cmp.reversed())
  }

  fun defaultWidths(): Map<Col, Int> = Col.entries.filter { it != Col.TITLE }.associateWith { it.defaultDp }

  /** Drag of the right edge of [col] by [deltaDp]. Title has no width of its own, so its edge moves the artist column the other way. */
  fun resize(widths: Map<Col, Int>, col: Col, deltaDp: Int): Map<Col, Int> {
    val target = if (col == Col.TITLE) Col.ARTIST else col
    val signed = if (col == Col.TITLE) -deltaDp else deltaDp
    val current = widths[target] ?: target.defaultDp
    return widths + (target to (current + signed).coerceAtLeast(target.minDp))
  }

  /** The width of the title column for a table that is [totalDp] wide, never under its minimum. */
  fun titleWidth(widths: Map<Col, Int>, totalDp: Int): Int =
    (totalDp - widths.values.sum()).coerceAtLeast(Col.TITLE.minDp)

  /** Shrinks the dragged columns, widest first, until the title column has its minimum in a table [totalDp] wide. */
  fun fit(widths: Map<Col, Int>, totalDp: Int): Map<Col, Int> {
    var out = widths
    var excess = out.values.sum() + Col.TITLE.minDp - totalDp
    if (excess <= 0) return out
    for (c in out.keys.sortedByDescending { out[it] }) {
      val room = (out.getValue(c) - c.minDp).coerceAtLeast(0)
      val take = minOf(room, excess)
      out = out + (c to out.getValue(c) - take)
      excess -= take
      if (excess <= 0) break
    }
    return out
  }

  /** Below [NARROW_DP] the album and plays columns are hidden (width 0), then the rest is fitted: a master pane keeps a readable title. */
  fun fitTo(widths: Map<Col, Int>, totalDp: Int): Map<Col, Int> {
    val t = totalDp.coerceAtLeast(0)
    if (t >= NARROW_DP) return fit(widths, t)
    return fit(widths - Col.ALBUM - Col.PLAYS, t) + (Col.ALBUM to 0) + (Col.PLAYS to 0)
  }

  const val NARROW_DP = 560

  fun encodeWidths(w: Map<Col, Int>) = w.entries.joinToString(",") { it.key.key + "=" + it.value }

  fun decodeWidths(s: String?): Map<Col, Int> {
    val saved = s.orEmpty().split(",").mapNotNull {
      val (k, v) = it.split("=", limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
      val c = Col.entries.firstOrNull { c -> c.key == k && c != Col.TITLE } ?: return@mapNotNull null
      c to (v.toIntOrNull()?.coerceAtLeast(c.minDp) ?: return@mapNotNull null)
    }.toMap()
    return defaultWidths() + saved
  }

  /**
   * The row type-ahead jumps to: the first title at or after [from] (wrapping round) that starts with [typed],
   * ignoring accents and case. -1 when none does.
   */
  fun typeAhead(tracks: List<Track>, typed: String, from: Int = 0): Int {
    val key = TextMatch.fold(typed.trimStart())
    if (key.isEmpty() || tracks.isEmpty()) return -1
    val start = from.coerceIn(0, tracks.size - 1)
    for (step in tracks.indices) {
      val i = (start + step) % tracks.size
      if (TextMatch.fold(tracks[i].title).startsWith(key)) return i
    }
    return -1
  }

  /** What dropping [files] on the library does: folders become scanned roots, audio files are added one by one. */
  class ImportPlan(val folders: List<File>, val audio: List<File>, val ignored: Int) {
    val isEmpty get() = folders.isEmpty() && audio.isEmpty()
  }

  fun importPlan(files: List<File>, isAudio: (File) -> Boolean = TagReader::isAudio): ImportPlan {
    val folders = files.filter { it.isDirectory }.map { it.absoluteFile }.distinct()
    val covered = { f: File -> folders.any { d -> f.absoluteFile.startsWith(d) } }
    val audio = files.filter { it.isFile && isAudio(it) && !covered(it) }.map { it.absoluteFile }.distinct()
    val ignored = files.count { !it.isDirectory && !(it.isFile && isAudio(it)) }
    return ImportPlan(folders, audio, ignored)
  }

  /** Path of the folder that holds a track's file, for "show in Explorer"; null for songs that are not files yet. */
  fun fileOf(track: Track): File? = track.filePath?.takeIf { track.isLocalFile }?.let(::File)?.takeIf { it.exists() }

  /** The folder labels shown in the sources pane for a catalogue, in path order. */
  fun folderLabels(catalog: DeviceCatalog): List<Pair<String, Int>> =
    catalog.folderCounts.entries.sortedBy { it.key }.map { it.key to it.value }
}

/**
 * The rows that are selected. [anchor] is where a range starts and [focus] is the row the keyboard moves from.
 * Selection is by track id, so sorting or filtering the table does not lose it.
 */
data class Selection(val ids: Set<String> = emptySet(), val anchor: Int = -1, val focus: Int = -1) {
  /** A click on row [index] of [order]: plain selects only it, Ctrl toggles it, Shift selects from the anchor to it. */
  fun click(index: Int, order: List<String>, ctrl: Boolean, shift: Boolean): Selection {
    if (index !in order.indices) return this
    return when {
      shift && anchor in order.indices -> {
        val range = order.subList(minOf(anchor, index), maxOf(anchor, index) + 1).toSet()
        Selection(if (ctrl) ids + range else range, anchor, index)
      }
      ctrl -> Selection(if (order[index] in ids) ids - order[index] else ids + order[index], index, index)
      else -> Selection(setOf(order[index]), index, index)
    }
  }

  /** A right-click: keeps the selection when the row is in it, otherwise selects only that row. */
  fun context(index: Int, order: List<String>): Selection =
    if (index in order.indices && order[index] in ids) this else click(index, order, ctrl = false, shift = false)

  fun all(order: List<String>) = Selection(order.toSet(), if (order.isEmpty()) -1 else 0, order.lastIndex)

  /** An arrow key: moves the focus by [delta] rows; with Shift the range from the anchor grows. */
  fun move(delta: Int, order: List<String>, shift: Boolean): Selection {
    if (order.isEmpty()) return this
    val to = (if (focus in order.indices) focus + delta else if (delta >= 0) 0 else order.lastIndex).coerceIn(0, order.lastIndex)
    return click(to, order, ctrl = false, shift = shift)
  }

  /** Keeps only the ids that are still in [order] (after a filter or a delete). */
  fun within(order: List<String>): Selection {
    val set = order.toHashSet()
    val kept = ids.filter { it in set }.toSet()
    return if (kept.size == ids.size) this else Selection(kept, anchor.coerceAtMost(order.lastIndex), focus.coerceAtMost(order.lastIndex))
  }

  fun tracks(from: List<Track>): List<Track> = from.filter { it.id in ids }
}

/** The letters typed in a row, joined while keystrokes come within [windowMs] of each other. */
class TypeAheadBuffer(private val windowMs: Long = 800, private val clock: () -> Long = System::currentTimeMillis) {
  private var text = ""
  private var last = 0L

  fun push(ch: Char): String {
    val now = clock()
    if (now - last > windowMs) text = ""
    text += ch
    last = now
    return text
  }
}
