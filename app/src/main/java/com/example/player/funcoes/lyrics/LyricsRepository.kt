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
 * Finds the lyrics of a song: local ID3v2 SYLT/USLT tags, sidecar `.lrc` file, local cache
 * (including user-imported `.lrc` files), and automatic online fallback via [LrclibClient]
 * (free, open, no API key required). Online results are cached locally so they load offline next time.
 */
class LyricsRepository(private val context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) {
  /** Why nothing was found, so the screen can say it instead of staying empty. */
  sealed interface Result {
    data class Found(val lyrics: Lyrics) : Result
    data object None : Result
    data object NotOnDevice : Result
  }

  @Volatile private var last: Pair<String, Result>? = null
  private val cacheDir: File by lazy { File(context.filesDir, "lyrics_cache").apply { mkdirs() } }

  suspend fun load(track: Track): Result {
    last?.let { (id, r) -> if (id == track.id) return r }
    val result = withContext(io) { read(track) }
    last = track.id to result
    return result
  }

  /** Saves a manually imported `.lrc` or plain text file for [track] into local cache and returns it. */
  suspend fun importLrc(track: Track, rawText: String): Result = withContext(io) {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return@withContext Result.None
    val parsed = LrcParser.parse(trimmed)
      ?: Lyrics(trimmed.lineSequence().map { LyricLine(null, it.trim()) }.toList(), LyricsSource.SIDECAR_LRC)
    runCatching { cacheFile(track.id).writeText(trimmed, Charsets.UTF_8) }
    val res = Result.Found(parsed)
    last = track.id to res
    res
  }

  private fun cacheFile(trackId: String): File {
    val safe = trackId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
    return File(cacheDir, "$safe.lrc")
  }

  private fun readCached(trackId: String): Lyrics? {
    val f = cacheFile(trackId)
    if (!f.isFile || !f.canRead() || f.length() > MAX_LRC) return null
    val text = f.readText(Charsets.UTF_8).trim()
    if (text.isEmpty()) return null
    return LrcParser.parse(text)
      ?: Lyrics(text.lineSequence().map { LyricLine(null, it.trim()) }.toList(), LyricsSource.SIDECAR_LRC)
  }

  private fun read(track: Track): Result {
    if (track.isDemo) return Result.NotOnDevice
    val path = track.filePath
    val embedded = if (track.isLocalFile && !path.isNullOrBlank()) runCatching { embedded(path) }.getOrNull() else null
    val sidecar = if (track.isLocalFile && !path.isNullOrBlank()) runCatching { sidecar(path) }.getOrNull() else null
    val cached = runCatching { readCached(track.id) }.getOrNull()
    val localBest = LyricsChoice.best(LyricsChoice.best(embedded, sidecar), cached)
    if (localBest != null) return Result.Found(localBest)

    // Automatic online fallback via LRCLIB.net (free, no API key), cached on disk when found.
    val onlineBody = LrclibClient.fetch(track)
    if (onlineBody != null) {
      val parsed = LrclibClient.parseResponse(onlineBody)
      if (parsed != null) {
        runCatching { cacheFile(track.id).writeText(parsed.first, Charsets.UTF_8) }
        return Result.Found(parsed.second)
      }
    }
    return Result.None
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
