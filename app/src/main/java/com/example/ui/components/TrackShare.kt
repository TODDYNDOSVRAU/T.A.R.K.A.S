package com.example.ui.components

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.example.data.model.Track

/**
 * What "Share" sends for a track. A file on the device goes as the audio itself, through the content
 * address the app was given for it (the receiver gets a temporary read grant); a stream goes as a link;
 * anything else goes as "title — artist". The short demo songs are the app's own and are not offered.
 */
object TrackShare {
  fun canShare(track: Track): Boolean = !track.isDemo

  fun text(track: Track): String =
    if (track.artist.isBlank()) track.title else "${track.title} — ${track.artist}"

  /** The intent to hand to the chooser, or null when the track is not shareable. */
  fun intent(track: Track): Intent? {
    if (!canShare(track)) return null
    val path = track.filePath
    val send = Intent(Intent.ACTION_SEND)
    return when {
      track.isLocalFile && path != null && path.startsWith("content://") -> {
        val uri = Uri.parse(path)
        send.setType("audio/*")
          .putExtra(Intent.EXTRA_STREAM, uri)
          .putExtra(Intent.EXTRA_TITLE, text(track))
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          .also { it.clipData = ClipData.newRawUri(track.title, uri) }
      }
      path != null && (path.startsWith("https://") || path.startsWith("http://")) ->
        send.setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${text(track)}\n$path")
      else -> send.setType("text/plain").putExtra(Intent.EXTRA_TEXT, text(track))
    }
  }
}
