package com.example.data.lyrics

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.data.model.Track
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds the lyrics of a song on the device: the SYLT/USLT frames of the file's ID3v2 tag and a
 * `.lrc` file with the same name beside it. Nothing is stored: the file is read when the screen
 * opens, so a changed tag or a new `.lrc` shows the next time.
 */
class LyricsRepository(private val context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) {
  /** Why nothing was found, so the screen can say it instead of staying empty. */
  sealed interface Result {
    data class Found(val lyrics: Lyrics) : Result
    data object None : Result
    data object NotOnDevice : Result
  }

  @Volatile private var last: Pair<String, Result>? = null

  suspend fun load(track: Track): Result {
    last?.let { (id, r) -> if (id == track.id) return r }
    val result = withContext(io) { read(track) }
    last = track.id to result
    return result
  }

  private fun read(track: Track): Result {
    val path = track.filePath
    if (track.isDemo || !track.isLocalFile || path.isNullOrBlank()) return Result.NotOnDevice
    val embedded = runCatching { embedded(path) }.getOrNull()
    val sidecar = runCatching { sidecar(path) }.getOrNull()
    val best = LyricsChoice.best(embedded, sidecar)
    return if (best != null) Result.Found(best) else Result.None
  }

  private fun open(path: String): InputStream? =
    if (path.startsWith("content://")) context.contentResolver.openInputStream(Uri.parse(path)) else File(path).takeIf { it.canRead() }?.inputStream()

  private fun embedded(path: String): Lyrics? = open(path)?.use { input ->
    val header = ByteArray(10)
    var n = 0
    while (n < 10) {
      val r = input.read(header, n, 10 - n)
      if (r < 0) break
      n += r
    }
    val size = if (n == 10) Id3Lyrics.tagSize(header) else -1
    if (size < 0 || size > MAX_TAG) return@use null
    val all = header.copyOf(size)
    var got = 10
    while (got < size) {
      val r = input.read(all, got, size - got)
      if (r < 0) break
      got += r
    }
    Id3Lyrics.read(if (got < size) all.copyOf(got) else all)
  }

  private fun sidecar(path: String): Lyrics? {
    val file = audioFile(path) ?: return null
    val base = file.nameWithoutExtension
    val dir = file.parentFile ?: return null
    val lrc = listOf("lrc", "LRC").map { File(dir, "$base.$it") }.firstOrNull { it.isFile && it.canRead() } ?: return null
    if (lrc.length() > MAX_LRC) return null
    return LrcParser.parse(lrc.readText(Charsets.UTF_8))
  }

  /** The file behind the song: the path itself, or the media library's record of a content address. */
  private fun audioFile(path: String): File? {
    if (!path.startsWith("content://")) return File(path)
    val data = context.contentResolver.query(Uri.parse(path), arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
      if (it.moveToFirst()) it.getString(0) else null
    }
    return data?.let { File(it) }
  }

  private companion object {
    const val MAX_TAG = 8 * 1024 * 1024
    const val MAX_LRC = 1024 * 1024
  }
}
