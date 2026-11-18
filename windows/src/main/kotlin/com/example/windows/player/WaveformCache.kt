package com.example.windows.player

import com.example.data.waveform.PeakAccumulator
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/** What identifies the version of a file a shape was made from. */
data class FileStamp(val sizeBytes: Long, val modifiedSec: Long) {
  companion object { fun of(f: File) = FileStamp(f.length(), f.lastModified() / 1000) }
}

/**
 * One small file per track in [dir] holding the peak shape (same layout as the Android store, which cannot be
 * shared because it logs through android.util.Log). A file that cannot be read back is treated as missing.
 */
class WaveformCache(private val dir: File) {
  fun read(trackId: String, stamp: FileStamp): ByteArray? {
    val f = fileFor(trackId)
    if (!f.isFile) return null
    return try {
      DataInputStream(f.inputStream().buffered()).use { i ->
        if (i.readInt() != MAGIC || i.readByte().toInt() != VERSION) return null
        if (i.readUTF() != trackId) return null
        if (i.readLong() != stamp.sizeBytes || i.readLong() != stamp.modifiedSec) return null
        val n = i.readUnsignedShort()
        if (n != PeakAccumulator.BUCKETS) return null
        ByteArray(n).also { i.readFully(it) }
      }
    } catch (e: IOException) { null }
  }

  fun write(trackId: String, stamp: FileStamp, amplitudes: ByteArray) {
    try {
      dir.mkdirs()
      val target = fileFor(trackId)
      val temp = File(dir, target.name + ".tmp")
      DataOutputStream(temp.outputStream().buffered()).use { o ->
        o.writeInt(MAGIC); o.writeByte(VERSION); o.writeUTF(trackId)
        o.writeLong(stamp.sizeBytes); o.writeLong(stamp.modifiedSec)
        o.writeShort(amplitudes.size); o.write(amplitudes)
      }
      if (!temp.renameTo(target)) { target.delete(); temp.renameTo(target) }
    } catch (e: IOException) { /* a shape can always be made again */ }
  }

  private fun fileFor(id: String) = File(dir, id.map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("") + ".wf")

  private companion object { const val MAGIC = 0x54574631; const val VERSION = 1 }
}
