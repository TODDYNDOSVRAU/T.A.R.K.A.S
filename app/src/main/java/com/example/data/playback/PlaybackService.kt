package com.example.data.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.AppGraph
import com.example.MainActivity
import com.example.R

/**
 * The media session: the notification, the lock screen, headphone and Bluetooth buttons, cars and
 * watches. Media3 keeps the service in the foreground while something plays (or a curiosity is being
 * narrated), so Android does not freeze or kill the app with the screen off, and lets a headset button
 * start playback even with the app in the background.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

  private var session: MediaSession? = null

  override fun onCreate() {
    super.onCreate()
    AppGraph.init(this)
    setMediaNotificationProvider(TarkasNotificationProvider(this))
    val open = PendingIntent.getActivity(
      this, 0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    session = MediaSession.Builder(this, SessionPlayer(this, AppGraph.playback))
      .setSessionActivity(open)
      .build()
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

  override fun onTaskRemoved(rootIntent: Intent?) {
    // Swiped away while playing: the music goes on, with its notification. Paused: everything stops.
    val player = session?.player
    if (player == null || !player.playWhenReady) {
      AppGraph.playback.stopAll()
      stopSelf()
    }
  }

  override fun onDestroy() {
    session?.run {
      player.release()
      release()
    }
    session = null
    super.onDestroy()
  }
}

/** Media3's notification, with the app's icon and the curiosity next to the artist when there is one. */
@OptIn(UnstableApi::class)
private class TarkasNotificationProvider(context: Context) : DefaultMediaNotificationProvider(
  context,
  { NOTIFICATION_ID },
  CHANNEL_ID,
  R.string.playback_channel_name
) {
  private companion object {
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "tarkas_playback"
  }

  init {
    setSmallIcon(R.drawable.ic_notification)
  }

  override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
    val artist = metadata.artist
    val trivia = metadata.description?.toString()?.takeIf { it.isNotBlank() } ?: return artist
    val snippet = if (trivia.length > 75) trivia.take(75) + "…" else trivia
    return if (artist.isNullOrBlank()) snippet else "$artist • $snippet"
  }
}
