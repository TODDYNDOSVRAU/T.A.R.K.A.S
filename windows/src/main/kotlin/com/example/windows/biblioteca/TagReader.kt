package com.example.windows.biblioteca

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/** What the tags and the header of an audio file say. A field the file does not have is blank or 0. */
class TagInfo(
  val title: String = "",
  val artist: String = "",
  val album: String = "",
  val genre: String = "",
  val year: Int = 0,
  val trackNumber: Int = 0,
  val discNumber: Int = 0,
  val durationMs: Long = 0L,
  val hasCover: Boolean = false
)

/**
 * Reads the tags of MP3 (ID3v2.2, 2.3, 2.4), FLAC (Vorbis comments) and WAV (RIFF INFO) files, the formats the
 * desktop player decodes, and the embedded cover. Never throws: a file that cannot be read gives an empty [TagInfo].
 */
object TagReader {
  val EXTENSIONS = setOf("mp3", "flac", "wav")

  fun isAudio(file: File) = file.extension.lowercase() in EXTENSIONS

  fun read(file: File): TagInfo = try {
    when (file.extension.lowercase()) {
      "mp3" -> RandomAccessFile(file, "r").use { readId3(it, file) }
      "flac" -> RandomAccessFile(file, "r").use { readFlac(it, true)?.first ?: TagInfo() }
      "wav" -> RandomAccessFile(file, "r").use { readWav(it) }
      else -> TagInfo()
    }
  } catch (_: Exception) {
    TagInfo()
  }

  /** The embedded cover (JPEG or PNG bytes), or null. */
  fun cover(file: File): ByteArray? = try {
    when (file.extension.lowercase()) {
      "mp3" -> RandomAccessFile(file, "r").use { id3Cover(it) }
      "flac" -> RandomAccessFile(file, "r").use { readFlac(it, true)?.second }
      else -> null
    }
  } catch (_: Exception) {
    null
  }

  // --- ID3v2 ---

  private class Frames(val version: Int, val body: ByteArray)

  private fun id3Frames(f: RandomAccessFile): Frames? {
    if (f.length() < 10) return null
    val h = ByteArray(10).also { f.seek(0); f.readFully(it) }
    if (h[0] != 'I'.code.toByte() || h[1] != 'D'.code.toByte() || h[2] != '3'.code.toByte()) return null
    val version = h[3].toInt()
    val size = syncsafe(h, 6)
    if (size <= 0 || size > f.length() - 10) return null
    var body = ByteArray(size).also { f.readFully(it) }
    if (h[5].toInt() and 0x80 != 0 && version < 4) body = unsync(body)
    if (h[5].toInt() and 0x40 != 0 && body.size >= 4) { // extended header
      val ext = if (version == 4) syncsafe(body, 0) else 4 + be32(body, 0)
      if (ext in 1..body.size) body = body.copyOfRange(ext, body.size)
    }
    return Frames(version, body)
  }

  private fun forEachFrame(fr: Frames, visit: (id: String, data: ByteArray) -> Boolean) {
    val b = fr.body
    val idLen = if (fr.version == 2) 3 else 4
    val head = if (fr.version == 2) 6 else 10
    var p = 0
    while (p + head <= b.size && b[p].toInt() != 0) {
      val id = String(b, p, idLen, Charsets.ISO_8859_1)
      val size = when (fr.version) {
        2 -> (b[p + 3].toInt() and 0xFF shl 16) or (b[p + 4].toInt() and 0xFF shl 8) or (b[p + 5].toInt() and 0xFF)
        4 -> syncsafe(b, p + 4)
        else -> be32(b, p + 4)
      }
      if (size < 0 || p + head + size > b.size) return
      var data = b.copyOfRange(p + head, p + head + size)
      if (fr.version >= 3) {
        val flags = b[p + 9].toInt() and 0xFF
        val unsynced = fr.version == 4 && (flags and 0x02) != 0
        val hasLength = (fr.version == 4 && (flags and 0x01) != 0) || (fr.version == 3 && (flags and 0x20) != 0)
        if (hasLength && data.size >= 4) data = data.copyOfRange(4, data.size)
        if (unsynced) data = unsync(data)
        val compressedOrEncrypted = if (fr.version == 4) (flags and 0x0C) != 0 else (flags and 0xC0) != 0
        if (compressedOrEncrypted) { p += head + size; continue }
      }
      if (!visit(id, data)) return
      p += head + size
    }
  }

  private fun readId3(f: RandomAccessFile, file: File): TagInfo {
    val fr = id3Frames(f)
    var title = ""; var artist = ""; var album = ""; var genre = ""
    var year = 0; var track = 0; var disc = 0; var cover = false
    if (fr != null) {
      forEachFrame(fr) { id, data ->
        when (id) {
          "TIT2", "TT2" -> title = text(data)
          "TPE1", "TP1" -> artist = text(data)
          "TALB", "TAL" -> album = text(data)
          "TCON", "TCO" -> genre = genreName(text(data))
          "TYER", "TYE", "TDRC" -> if (year == 0) year = text(data).take(4).toIntOrNull() ?: 0
          "TRCK", "TRK" -> track = number(text(data))
          "TPOS", "TPA" -> disc = number(text(data))
          "APIC", "PIC" -> cover = true
        }
        true
      }
    }
    return TagInfo(title, artist, album, genre, year, track, disc, mp3Duration(file), cover)
  }

  private fun id3Cover(f: RandomAccessFile): ByteArray? {
    val fr = id3Frames(f) ?: return null
    var best: ByteArray? = null
    var bestIsFront = false
    forEachFrame(fr) { id, data ->
      if (id == "APIC" || id == "PIC") {
        val (isFront, image) = picture(data, id == "PIC")
        if (image != null && (best == null || (isFront && !bestIsFront))) {
          best = image; bestIsFront = isFront
        }
      }
      true
    }
    return best
  }

  /** APIC: encoding, mime (ends with 0), picture type, description (ends with 0 or 00 00), data. */
  private fun picture(d: ByteArray, v22: Boolean): Pair<Boolean, ByteArray?> {
    if (d.size < 4) return false to null
    val enc = d[0].toInt()
    var p = 1
    if (v22) p += 3 else { while (p < d.size && d[p].toInt() != 0) p++; p++ }
    if (p >= d.size) return false to null
    val type = d[p].toInt(); p++
    val wide = enc == 1 || enc == 2
    while (p < d.size) {
      if (wide) { if (p + 1 < d.size && d[p].toInt() == 0 && d[p + 1].toInt() == 0) { p += 2; break } else p += 2 }
      else { if (d[p].toInt() == 0) { p++; break } else p++ }
    }
    if (p >= d.size) return false to null
    return (type == 3) to d.copyOfRange(p, d.size)
  }

  private fun text(d: ByteArray): String {
    if (d.isEmpty()) return ""
    val enc = d[0].toInt()
    val cs: Charset = when (enc) {
      1 -> if (d.size >= 3 && d[1] == 0xFE.toByte() && d[2] == 0xFF.toByte()) Charsets.UTF_16BE else Charsets.UTF_16LE
      2 -> Charsets.UTF_16BE
      3 -> Charsets.UTF_8
      else -> Charsets.ISO_8859_1
    }
    var s = String(d, 1, d.size - 1, cs).trimStart('﻿')
    s = s.substringBefore('\u0000') // several values: the first
    return s.trim()
  }

  private fun number(s: String) = s.substringBefore('/').trim().toIntOrNull() ?: 0

  private fun genreName(raw: String): String {
    val m = Regex("""^\((\d{1,3})\)(.*)$""").matchEntire(raw)
    if (m != null) return m.groupValues[2].ifBlank { ID3V1_GENRES.getOrNull(m.groupValues[1].toInt()).orEmpty() }
    raw.toIntOrNull()?.let { return ID3V1_GENRES.getOrNull(it).orEmpty() }
    return raw
  }

  private fun syncsafe(b: ByteArray, o: Int) =
    (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)

  private fun be32(b: ByteArray, o: Int) =
    (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

  private fun unsync(b: ByteArray): ByteArray {
    val out = java.io.ByteArrayOutputStream(b.size)
    var i = 0
    while (i < b.size) {
      out.write(b[i].toInt())
      if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1].toInt() == 0) i++
      i++
    }
    return out.toByteArray()
  }

  /** MP3 length: the player's own decoder reports it for the file; 0 when it cannot say. */
  private fun mp3Duration(file: File): Long = try {
    val fmt = javax.sound.sampled.AudioSystem.getAudioFileFormat(file)
    ((fmt.properties()["duration"] as? Long) ?: -1L).takeIf { it > 0 }?.div(1000L)
      ?: fmt.frameLength.takeIf { it > 0 && fmt.format.frameRate > 0 }?.let { (it * 1000.0 / fmt.format.frameRate).toLong() }
      ?: 0L
  } catch (_: Exception) {
    0L
  }

  // --- FLAC ---

  /** Tags and, when [wantCover], the first PICTURE block of the front-cover kind (else the first). */
  private fun readFlac(f: RandomAccessFile, wantCover: Boolean): Pair<TagInfo, ByteArray?>? {
    val magic = ByteArray(4).also { f.seek(0); f.readFully(it) }
    if (String(magic, Charsets.ISO_8859_1) != "fLaC") return null
    val tags = HashMap<String, String>()
    var duration = 0L
    var cover: ByteArray? = null
    var coverIsFront = false
    var hasCover = false
    var last = false
    while (!last && f.filePointer + 4 <= f.length()) {
      val h = f.readInt()
      last = h ushr 31 == 1
      val type = (h ushr 24) and 0x7F
      val len = h and 0xFFFFFF
      if (f.filePointer + len > f.length()) break
      when (type) {
        0 -> {
          val b = ByteArray(len).also { f.readFully(it) }
          if (b.size >= 18) {
            val rate = (b[10].toInt() and 0xFF shl 12) or (b[11].toInt() and 0xFF shl 4) or (b[12].toInt() and 0xFF shr 4)
            val total = ((b[13].toLong() and 0x0F) shl 32) or (b[14].toLong() and 0xFF shl 24) or
              (b[15].toLong() and 0xFF shl 16) or (b[16].toLong() and 0xFF shl 8) or (b[17].toLong() and 0xFF)
            if (rate > 0 && total > 0) duration = total * 1000L / rate
          }
        }
        4 -> {
          val b = ByteArray(len).also { f.readFully(it) }
          var p = 0
          fun le32(): Int { val v = (b[p].toInt() and 0xFF) or (b[p + 1].toInt() and 0xFF shl 8) or (b[p + 2].toInt() and 0xFF shl 16) or (b[p + 3].toInt() and 0xFF shl 24); p += 4; return v }
          val vendor = le32(); p += vendor
          val n = le32()
          repeat(n) {
            if (p + 4 > b.size) return@repeat
            val l = le32()
            if (l < 0 || p + l > b.size) return@repeat
            val s = String(b, p, l, Charsets.UTF_8); p += l
            val eq = s.indexOf('=')
            if (eq > 0) tags.putIfAbsent(s.substring(0, eq).uppercase(), s.substring(eq + 1).trim())
          }
        }
        6 -> {
          hasCover = true
          if (wantCover) {
            val b = ByteArray(len).also { f.readFully(it) }
            var p = 0
            fun be(): Int { val v = be32(b, p); p += 4; return v }
            val pictureType = be()
            p += be() // mime
            p += be() // description
            p += 16 // width, height, depth, colours
            val n = be()
            if (n > 0 && p + n <= b.size && (cover == null || (pictureType == 3 && !coverIsFront))) {
              cover = b.copyOfRange(p, p + n); coverIsFront = pictureType == 3
            }
          } else f.skipBytes(len)
        }
        else -> f.skipBytes(len)
      }
    }
    val info = TagInfo(
      title = tags["TITLE"].orEmpty(), artist = tags["ARTIST"].orEmpty(), album = tags["ALBUM"].orEmpty(),
      genre = tags["GENRE"].orEmpty(),
      year = (tags["DATE"] ?: tags["YEAR"]).orEmpty().take(4).toIntOrNull() ?: 0,
      trackNumber = number(tags["TRACKNUMBER"].orEmpty()), discNumber = number(tags["DISCNUMBER"].orEmpty()),
      durationMs = duration, hasCover = hasCover
    )
    return info to cover
  }

  // --- WAV ---

  private fun readWav(f: RandomAccessFile): TagInfo {
    val head = ByteArray(12).also { f.seek(0); f.readFully(it) }
    if (String(head, 0, 4, Charsets.ISO_8859_1) != "RIFF" || String(head, 8, 4, Charsets.ISO_8859_1) != "WAVE") return TagInfo()
    var byteRate = 0L
    var dataSize = 0L
    val info = HashMap<String, String>()
    while (f.filePointer + 8 <= f.length()) {
      val id = ByteArray(4).also { f.readFully(it) }.toString(Charsets.ISO_8859_1)
      val size = Integer.reverseBytes(f.readInt()).toLong() and 0xFFFFFFFFL
      val start = f.filePointer
      when (id) {
        "fmt " -> {
          val b = ByteArray(minOf(size, 16L).toInt()).also { f.readFully(it) }
          if (b.size >= 12) byteRate = (b[8].toLong() and 0xFF) or (b[9].toLong() and 0xFF shl 8) or (b[10].toLong() and 0xFF shl 16) or (b[11].toLong() and 0xFF shl 24)
        }
        "data" -> dataSize = minOf(size, f.length() - start)
        "LIST" -> if (size in 4..65536) {
          val b = ByteArray(size.toInt()).also { f.readFully(it) }
          if (String(b, 0, 4, Charsets.ISO_8859_1) == "INFO") {
            var p = 4
            while (p + 8 <= b.size) {
              val key = String(b, p, 4, Charsets.ISO_8859_1)
              val l = (b[p + 4].toInt() and 0xFF) or (b[p + 5].toInt() and 0xFF shl 8) or (b[p + 6].toInt() and 0xFF shl 16) or (b[p + 7].toInt() and 0xFF shl 24)
              if (l < 0 || p + 8 + l > b.size) break
              info[key] = String(b, p + 8, l, Charsets.UTF_8).substringBefore('\u0000').trim()
              p += 8 + l + (l and 1)
            }
          }
        }
      }
      val next = start + size + (size and 1L)
      if (next <= f.filePointer && id != "data") { /* already consumed */ }
      if (next > f.length()) break
      f.seek(next)
    }
    val duration = if (byteRate > 0) dataSize * 1000L / byteRate else 0L
    return TagInfo(
      title = info["INAM"].orEmpty(), artist = info["IART"].orEmpty(), album = info["IPRD"].orEmpty(),
      genre = info["IGNR"].orEmpty(), year = info["ICRD"].orEmpty().take(4).toIntOrNull() ?: 0,
      trackNumber = number(info["ITRK"].orEmpty()), durationMs = duration
    )
  }

  private val ID3V1_GENRES = listOf(
    "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age",
    "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
    "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion",
    "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise", "Alt. Rock",
    "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
    "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy",
    "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave",
    "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro",
    "Musical", "Rock & Roll", "Hard Rock"
  )
}
