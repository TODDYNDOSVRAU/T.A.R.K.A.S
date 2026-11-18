package com.example.windows.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File

/** Finds a track's real cover: ID3 APIC, FLAC picture block, or a cover image beside the file. Never invents one. */
object CoverArt {
  private val SIDE = listOf("cover", "folder", "front", "album")
  private val EXT = listOf("jpg", "jpeg", "png")

  fun bytes(file: File): ByteArray? {
    if (!file.isFile) return null
    val embedded = try { file.inputStream().use { i ->
      val head = i.readNBytes(10)
      when {
        head.size == 10 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte() -> {
          val size = synchsafe(head, 6)
          if (size <= 0 || size > MAX_TAG) null else id3(head[3].toInt(), i.readNBytes(size))
        }
        head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC" -> flac(head.copyOfRange(4, 10), i)
        else -> null
      }
    } } catch (e: Exception) { null }
    return embedded ?: sidecar(file)
  }

  private fun sidecar(file: File): ByteArray? {
    val dir = file.parentFile ?: return null
    for (n in SIDE) for (x in EXT) {
      val f = File(dir, "$n.$x")
      if (f.isFile && f.length() in 1..MAX_IMG) return try { f.readBytes() } catch (e: Exception) { null }
    }
    return null
  }

  private fun synchsafe(b: ByteArray, o: Int) = (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)
  private fun be32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

  /** The first APIC frame of an ID3v2.3/2.4 tag body ([tag] excludes the 10-byte header). */
  fun id3(version: Int, tag: ByteArray): ByteArray? {
    if (version != 3 && version != 4) return null
    var p = 0
    while (p + 10 <= tag.size && tag[p].toInt() != 0) {
      val id = String(tag, p, 4, Charsets.ISO_8859_1)
      val size = if (version == 4) synchsafe(tag, p + 4) else be32(tag, p + 4)
      val start = p + 10
      if (size < 0 || start + size > tag.size) return null
      if (id == "APIC") return apic(tag, start, start + size)
      p = start + size
    }
    return null
  }

  private fun apic(t: ByteArray, from: Int, to: Int): ByteArray? {
    if (to - from < 4) return null
    val enc = t[from].toInt()
    var p = from + 1
    while (p < to && t[p].toInt() != 0) p++          // mime
    p += 2                                           // its null and the picture type
    val wide = enc == 1 || enc == 2
    while (p < to) {                                 // description
      if (wide) { if (p + 1 < to && t[p].toInt() == 0 && t[p + 1].toInt() == 0) { p += 2; break }; p += 2 }
      else { if (t[p].toInt() == 0) { p++; break }; p++ }
    }
    return if (p < to) t.copyOfRange(p, to) else null
  }

  private fun flac(first6: ByteArray, i: java.io.InputStream): ByteArray? {
    var pending = first6
    fun take(n: Int): ByteArray { val have = minOf(n, pending.size); val out = pending.copyOfRange(0, have) + i.readNBytes(n - have); pending = pending.copyOfRange(have, pending.size); return out }
    var last = false
    while (!last) {
      val h = take(4); if (h.size < 4) return null
      last = h[0].toInt() and 0x80 != 0
      val type = h[0].toInt() and 0x7F
      val len = (h[1].toInt() and 0xFF shl 16) or (h[2].toInt() and 0xFF shl 8) or (h[3].toInt() and 0xFF)
      if (len > MAX_IMG) return null
      val body = take(len); if (body.size < len) return null
      if (type == 6) {
        var p = 4
        val mimeLen = be32(body, p); p += 4 + mimeLen
        val descLen = be32(body, p); p += 4 + descLen + 16
        val dataLen = be32(body, p); p += 4
        return if (dataLen > 0 && p + dataLen <= body.size) body.copyOfRange(p, p + dataLen) else null
      }
    }
    return null
  }

  fun decode(bytes: ByteArray): ImageBitmap? = try {
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
  } catch (e: Throwable) { null }

  fun load(file: File): ImageBitmap? = bytes(file)?.let(::decode)

  private const val MAX_TAG = 16 * 1024 * 1024
  private const val MAX_IMG = 16L * 1024 * 1024
}
