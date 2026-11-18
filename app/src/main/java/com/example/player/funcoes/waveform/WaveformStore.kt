package com.example.data.waveform

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/** What identifies the version of a file a shape was made from; a change in either makes the shape stale. */
data class FileStamp(val sizeBytes: Long, val modifiedSec: Long)

/** What the store knows of a track. */
sealed interface StoredWaveform {
  /** A shape made from the file as it is now. */
  class Ready(val amplitudes: ByteArray) : StoredWaveform
  /** The file was tried and cannot be shown as a wave (too long, protected, unreadable); not tried again until it changes. */
  data object Unavailable : StoredWaveform
}

/**
 * One small file per track in [dir]: format version, track id, the file's size and modification time, a
 * state and the amplitudes. No database: the app has none, and a shape is 100 bytes that can always be
 * made again. A file that cannot be read back is treated as missing.
 */
class WaveformStore(private val dir: File) {

  /** The stored result for [trackId] if it was made from a file that still matches [stamp]; otherwise null. */
  fun read(trackId: String, stamp: FileStamp): StoredWaveform? {
    val file = fileFor(trackId)
    if (!file.isFile) return null
    return try {
      DataInputStream(file.inputStream().buffered()).use { input ->
        if (input.readInt() != MAGIC || input.readByte().toInt() != VERSION) return null
        if (input.readUTF() != trackId) return null
        if (input.readLong() != stamp.sizeBytes || input.readLong() != stamp.modifiedSec) return null
        when (input.readByte().toInt()) {
          STATE_READY -> {
            val n = input.readUnsignedShort()
            if (n != PeakAccumulator.BUCKETS) return null
            StoredWaveform.Ready(ByteArray(n).also { input.readFully(it) })
          }
          STATE_UNAVAILABLE -> StoredWaveform.Unavailable
          else -> null
        }
      }
    } catch (e: java.io.IOException) {
      null
    }
  }

  fun writeReady(trackId: String, stamp: FileStamp, amplitudes: ByteArray) = write(trackId, stamp, amplitudes)

  fun writeUnavailable(trackId: String, stamp: FileStamp) = write(trackId, stamp, null)

  fun delete(trackId: String) {
    fileFor(trackId).delete()
  }

  /** Removes the shapes of tracks that are no longer in [keep] (files deleted from the device). */
  fun retainOnly(keep: Set<String>) {
    val keepNames = keep.mapTo(HashSet()) { fileName(it) }
    dir.listFiles()?.forEach { if (it.name !in keepNames) it.delete() }
  }

  private fun write(trackId: String, stamp: FileStamp, amplitudes: ByteArray?) {
    try {
      dir.mkdirs()
      val target = fileFor(trackId)
      val temp = File(dir, target.name + ".tmp")
      DataOutputStream(temp.outputStream().buffered()).use { out ->
        out.writeInt(MAGIC)
        out.writeByte(VERSION)
        out.writeUTF(trackId)
        out.writeLong(stamp.sizeBytes)
        out.writeLong(stamp.modifiedSec)
        if (amplitudes != null) {
          out.writeByte(STATE_READY)
          out.writeShort(amplitudes.size)
          out.write(amplitudes)
        } else {
          out.writeByte(STATE_UNAVAILABLE)
        }
      }
      // Replaced whole, so a crash mid-write never leaves a half file under the real name.
      if (!temp.renameTo(target)) {
        target.delete()
        temp.renameTo(target)
      }
    } catch (e: java.io.IOException) {
      android.util.Log.w("WaveformStore", "Could not save the wave of $trackId", e)
    }
  }

  private fun fileFor(trackId: String) = File(dir, fileName(trackId))

  private fun fileName(trackId: String) = trackId.map { if (it.isLetterOrDigit() || it == '-') it else '_' }.joinToString("") + ".wf"

  private companion object {
    const val MAGIC = 0x54574631 // "TWF1"
    const val VERSION = 1
    const val STATE_READY = 1
    const val STATE_UNAVAILABLE = 0
  }
}
