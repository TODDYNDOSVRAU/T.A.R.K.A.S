package com.example.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import com.example.MainActivity
import com.example.data.model.Curiosity
import com.example.data.model.Track

object MusicNotificationManager {
  private const val CHANNEL_ID = "tarkas_playback_channel"
  const val NOTIFICATION_ID = 1001

  private var mediaSession: MediaSession? = null

  // The playback service shows this same notification while it runs in the foreground.
  @Volatile
  private var lastNotification: Notification? = null
  var onSeekToRequested: ((Long) -> Unit)? = null

  fun getOrCreateMediaSession(context: Context): MediaSession {
    if (mediaSession == null) {
      mediaSession = MediaSession(context, "TarkasMediaSession").apply {
        setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        setCallback(object : MediaSession.Callback() {
          override fun onPlay() {
            NotificationActionReceiver.onActionReceived?.invoke(NotificationActionReceiver.ACTION_TOGGLE)
          }

          override fun onPause() {
            NotificationActionReceiver.onActionReceived?.invoke(NotificationActionReceiver.ACTION_TOGGLE)
          }

          override fun onSkipToNext() {
            NotificationActionReceiver.onActionReceived?.invoke(NotificationActionReceiver.ACTION_NEXT)
          }

          override fun onSkipToPrevious() {
            NotificationActionReceiver.onActionReceived?.invoke(NotificationActionReceiver.ACTION_PREV)
          }

          override fun onSeekTo(pos: Long) {
            onSeekToRequested?.invoke(pos)
          }
        })
        isActive = true
      }
    }
    return mediaSession!!
  }

  fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = "Reprodução Nativa Tarkas"
      val descriptionText = "Controle nativo de mídia e curiosidades da faixa"
      val importance = NotificationManager.IMPORTANCE_LOW
      val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
        description = descriptionText
        setShowBadge(false)
      }
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun createArtworkBitmap(track: Track): Bitmap {
    val size = 256
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = track.colorHex.toInt()
    canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 48f, 48f, paint)

    paint.color = Color.WHITE
    paint.textSize = 80f
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true

    val initial = track.title.take(1).uppercase()
    val yPos = (size / 2f - (paint.descent() + paint.ascent()) / 2f)
    canvas.drawText(initial, size / 2f, yPos, paint)

    return bitmap
  }

  fun updatePlaybackNotification(
    context: Context,
    track: Track?,
    isPlaying: Boolean,
    curiosity: Curiosity?,
    positionMs: Long = 0L
  ) {
    if (track == null) {
      clearNotification(context)
      return
    }

    try {
      createNotificationChannel(context)
      val session = getOrCreateMediaSession(context)
      val artworkBitmap = createArtworkBitmap(track)

      // Update MediaSession state so Android native Quick Settings / notification popup displays native controls
      val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
      val playbackState = PlaybackState.Builder()
        .setActions(
          PlaybackState.ACTION_PLAY or
              PlaybackState.ACTION_PAUSE or
              PlaybackState.ACTION_PLAY_PAUSE or
              PlaybackState.ACTION_SKIP_TO_NEXT or
              PlaybackState.ACTION_SKIP_TO_PREVIOUS or
              PlaybackState.ACTION_SEEK_TO
        )
        .setState(state, positionMs, 1.0f)
        .build()
      session.setPlaybackState(playbackState)

      val metadata = MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
        .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
        .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)
        .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artworkBitmap)
        .putBitmap(MediaMetadata.METADATA_KEY_ART, artworkBitmap)
        .build()
      session.setMetadata(metadata)

      val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingOpenApp = PendingIntent.getActivity(
        context,
        0,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val shortCuriositySnippet = curiosity?.shortTrivia?.take(75)?.let {
        if (curiosity.shortTrivia.length > 75) "$it..." else it
      } ?: "Curiosidade verificada Tarkas"

      val prevIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_PREV
      }
      val prevPending = PendingIntent.getBroadcast(
        context,
        1,
        prevIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val playPauseIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_TOGGLE
      }
      val playPausePending = PendingIntent.getBroadcast(
        context,
        2,
        playPauseIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val nextIntent = Intent(context, NotificationActionReceiver::class.java).apply {
        action = NotificationActionReceiver.ACTION_NEXT
      }
      val nextPending = PendingIntent.getBroadcast(
        context,
        3,
        nextIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

      // Native Android MediaStyle Notification
      val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(context, CHANNEL_ID)
      } else {
        @Suppress("DEPRECATION")
        Notification.Builder(context)
      }

      val mediaStyle = Notification.MediaStyle()
        .setMediaSession(session.sessionToken)
        .setShowActionsInCompactView(0, 1, 2)

      val notification = builder
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setLargeIcon(artworkBitmap)
        .setContentTitle(track.title)
        .setContentText("${track.artist} • $shortCuriositySnippet")
        .setSubText("Tarkas Audio")
        .setContentIntent(pendingOpenApp)
        .setOngoing(isPlaying)
        .setStyle(mediaStyle)
        .addAction(Notification.Action.Builder(android.R.drawable.ic_media_previous, "Anterior", prevPending).build())
        .addAction(Notification.Action.Builder(playPauseIcon, if (isPlaying) "Pausar" else "Play", playPausePending).build())
        .addAction(Notification.Action.Builder(android.R.drawable.ic_media_next, "Próxima", nextPending).build())
        .build()

      lastNotification = notification
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.notify(NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
    } catch (_: Exception) {}
  }

  /** The latest playback notification, or a plain one if none was built yet. */
  fun currentOrPlaceholder(context: Context): Notification {
    lastNotification?.let { return it }
    createNotificationChannel(context)
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(context, CHANNEL_ID)
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(context)
    }
    return builder
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle("Tarkas")
      .setContentText("Reproduzindo")
      .setOngoing(true)
      .build()
  }

  fun clearNotification(context: Context) {
    lastNotification = null
    try {
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.cancel(NOTIFICATION_ID)
      mediaSession?.isActive = false
    } catch (_: Exception) {}
  }
}
