package com.example.data.library

import android.content.Context
import com.example.data.model.Track
import com.example.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads free remote tracks (Jamendo / Audius) into app-private storage (`filesDir/offline_tracks/`)
 * so they can be played completely offline without network access.
 */
object OfflineTrackDownloader {
  private const val DIR_NAME = "offline_tracks"

  private val client by lazy {
    Http.client.newBuilder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build()
  }

  /** Safe filesystem basename derived from a track ID. Pure, tested on JVM. */
  fun sanitizeFileName(trackId: String): String {
    val cleaned = trackId.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
    return cleaned.takeIf { it.isNotEmpty() }?.take(80) ?: "track"
  }

  fun offlineFile(context: Context, track: Track): File {
    val dir = File(context.filesDir, DIR_NAME)
    return File(dir, "${sanitizeFileName(track.id)}.mp3")
  }

  fun isDownloaded(context: Context, track: Track): Boolean {
    val file = offlineFile(context, track)
    return file.exists() && file.length() > 0L
  }

  suspend fun download(context: Context, track: Track): Result<File> = withContext(Dispatchers.IO) {
    val url = track.filePath?.trim()
    if (url.isNullOrBlank() || (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true))) {
      return@withContext Result.failure(IllegalArgumentException("Track has no downloadable HTTP URL"))
    }
    runCatching {
      val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
      val target = File(dir, "${sanitizeFileName(track.id)}.mp3")
      if (target.exists() && target.length() > 0L) return@runCatching target

      val temp = File(dir, "${sanitizeFileName(track.id)}.part")
      val req = Request.Builder().url(url).get().build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        val body = resp.body ?: throw IOException("Empty response body")
        body.byteStream().use { input ->
          temp.outputStream().use { output ->
            input.copyTo(output, bufferSize = 16 * 1024)
          }
        }
      }
      if (temp.length() <= 0L) {
        temp.delete()
        throw IOException("Downloaded file is empty")
      }
      if (target.exists()) target.delete()
      if (!temp.renameTo(target)) {
        temp.copyTo(target, overwrite = true)
        temp.delete()
      }
      target
    }
  }
}
