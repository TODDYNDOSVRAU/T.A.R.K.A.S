package com.example.data.lyrics

import java.nio.charset.Charset

/**
 * Reads the lyrics frames of an ID3v2 tag (versions 2.2, 2.3 and 2.4): SYLT (synchronized, with the
 * time of each line) and USLT (plain text). Works on the bytes at the start of the file; anything it
 * cannot read gives null, never an exception.
 */
object Id3Lyrics {
  /** The tag's size from its 10-byte header, or -1 when the bytes are not an ID3v2 header. */
  fun tagSize(header: ByteArray): Int {
    if (header.size < 10 || header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return -1
    val v = header[3].toInt()
    if (v !in 2..4) return -1
    return 10 + syncsafe(header, 6) + (if (v == 4 && header[5].toInt() and 0x10 != 0) 10 else 0)
  }

  fun read(bytes: ByteArray): Lyrics? = try {
    parse(bytes)
  } catch (_: RuntimeException) {
    null
  }

  private fun parse(file: ByteArray): Lyrics? {
    val total = tagSize(file)
    if (total < 0) return null
    val version = file[3].toInt()
    val flags = file[5].toInt()
    val end = minOf(total, file.size)
    var body = file.copyOfRange(10, end)
    if (flags and 0x80 != 0 && version != 4) body = deUnsync(body)
    var pos = 0
    if (flags and 0x40 != 0 && body.size >= 4) {
      // Extended header: its size is syncsafe in 2.4 and plain in 2.3.
      val size = if (version == 4) syncsafe(body, 0) else int32(body, 0) + 4
      pos = size.coerceIn(0, body.size)
    }
    val idLen = if (version == 2) 3 else 4
    val headLen = if (version == 2) 6 else 10
    var synced: Lyrics? = null
    var plain: Lyrics? = null
    while (pos + headLen <= body.size) {
      if (body[pos].toInt() == 0) break // padding
      val id = String(body, pos, idLen, Charsets.ISO_8859_1)
      val size = when (version) {
        2 -> (body[pos + 3].toInt() and 0xFF shl 16) or (body[pos + 4].toInt() and 0xFF shl 8) or (body[pos + 5].toInt() and 0xFF)
        4 -> syncsafe(body, pos + 4)
        else -> int32(body, pos + 4)
      }
      val dataStart = pos + headLen
      if (size < 0 || dataStart + size > body.size) break
      var data = body.copyOfRange(dataStart, dataStart + size)
      val frameFlags = if (version == 2) 0 else (body[pos + 8].toInt() and 0xFF shl 8) or (body[pos + 9].toInt() and 0xFF)
      // 2.4: bit 0 of the second flag byte = data length indicator (4 bytes to skip), bit 1 = unsynchronised.
      if (version == 4) {
        if (frameFlags and 0x0001 != 0 && data.size >= 4) data = data.copyOfRange(4, data.size)
        if (frameFlags and 0x0002 != 0) data = deUnsync(data)
      }
      val compressedOrEncrypted = version != 2 && (if (version == 4) frameFlags and 0x000C != 0 else frameFlags and 0x00C0 != 0)
      if (!compressedOrEncrypted) {
        when (id) {
          "SYLT", "SLT" -> if (synced == null) synced = sylt(data)
          "USLT", "ULT" -> if (plain == null) plain = uslt(data)
        }
      }
      pos = dataStart + size
    }
    return synced ?: plain
  }

  private fun uslt(d: ByteArray): Lyrics? {
    if (d.size < 5) return null
    val enc = d[0].toInt()
    var p = 4 // encoding + 3 language bytes
    p = skipTerminated(d, p, enc)
    if (p > d.size) return null
    val text = decode(d, p, d.size, enc).replace("\r\n", "\n").replace('\r', '\n')
    // Some taggers put timed lines in the plain frame.
    LrcParser.parse(text, LyricsSource.EMBEDDED_PLAIN)?.let { lrc ->
      if (lrc.synced) return Lyrics(lrc.lines, LyricsSource.EMBEDDED_SYNCED)
    }
    val lines = text.split('\n').map { it.trimEnd() }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
    if (lines.none { it.isNotEmpty() }) return null
    return Lyrics(lines.map { LyricLine(null, it) }, LyricsSource.EMBEDDED_PLAIN)
  }

  private fun sylt(d: ByteArray): Lyrics? {
    if (d.size < 7) return null
    val enc = d[0].toInt()
    val format = d[4].toInt() // 1 = MPEG frames, 2 = milliseconds
    if (format != 2) return null // frames cannot be turned into time without the stream; left to the plain frame
    var p = skipTerminated(d, 6, enc) // after encoding, language, format, content type, then the descriptor
    val lines = ArrayList<LyricLine>()
    while (p < d.size) {
      val stop = terminatorAt(d, p, enc)
      if (stop < 0) break
      val text = decode(d, p, stop, enc)
      val timeAt = stop + (if (enc == 1 || enc == 2) 2 else 1)
      if (timeAt + 4 > d.size) break
      val time = int32(d, timeAt).toLong() and 0xFFFFFFFFL
      lines.add(LyricLine(time, text.trim('\n', '\r')))
      p = timeAt + 4
    }
    if (lines.none { it.text.isNotEmpty() }) return null
    return Lyrics(lines.sortedBy { it.timeMs }, LyricsSource.EMBEDDED_SYNCED)
  }

  private fun charset(enc: Int): Charset = when (enc) {
    0 -> Charsets.ISO_8859_1
    1 -> Charsets.UTF_16
    2 -> Charsets.UTF_16BE
    else -> Charsets.UTF_8
  }

  private fun decode(d: ByteArray, from: Int, to: Int, enc: Int): String {
    if (from >= to) return ""
    var s = String(d, from, to - from, charset(enc))
    if (enc == 1 && s.startsWith("\uFEFF")) s = s.substring(1)
    return s
  }

  /** Index of the terminator that ends the string starting at [from], or -1. */
  private fun terminatorAt(d: ByteArray, from: Int, enc: Int): Int {
    if (enc == 1 || enc == 2) {
      var i = from
      while (i + 1 < d.size) {
        if (d[i].toInt() == 0 && d[i + 1].toInt() == 0) return i
        i += 2
      }
      return -1
    }
    for (i in from until d.size) if (d[i].toInt() == 0) return i
    return -1
  }

  /** The position just after the terminated string at [from]; the end of the data when it never ends. */
  private fun skipTerminated(d: ByteArray, from: Int, enc: Int): Int {
    val t = terminatorAt(d, from, enc)
    return if (t < 0) d.size else t + (if (enc == 1 || enc == 2) 2 else 1)
  }

  private fun syncsafe(b: ByteArray, at: Int): Int =
    ((b[at].toInt() and 0x7F) shl 21) or ((b[at + 1].toInt() and 0x7F) shl 14) or
      ((b[at + 2].toInt() and 0x7F) shl 7) or (b[at + 3].toInt() and 0x7F)

  private fun int32(b: ByteArray, at: Int): Int =
    ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
      ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

  private fun deUnsync(b: ByteArray): ByteArray {
    val out = java.io.ByteArrayOutputStream(b.size)
    var i = 0
    while (i < b.size) {
      out.write(b[i].toInt())
      if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1].toInt() == 0) i++
      i++
    }
    return out.toByteArray()
  }
}
