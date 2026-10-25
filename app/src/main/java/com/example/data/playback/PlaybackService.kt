package com.example.data.playback

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.notification.MusicNotificationManager

/**
 * Keeps the app process alive and visible while music plays with the app in the background
 * or the screen off. It does not own the player: the player lives in the view model and this
 * service only holds the media notification as a foreground service.
 */
class PlaybackService : Service() {

  companion object {
    private const val TAG = "PlaybackService"

    @Volatile
    private var instance: PlaybackService? = null

    /** Starts (or re-promotes) the service. Safe to call repeatedly. */
    fun start(context: Context) {
      try {
        ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
      } catch (e: Exception) {
        // Android can refuse a foreground start from the background; playback itself keeps working.
        Log.w(TAG, "Could not start the playback service", e)
      }
    }

    /** Nothing is playing any more: leave the foreground but keep the notification until dismissed. */
    fun release() {
      instance?.leaveForeground(removeNotification = false)
    }

    /** The player is gone: leave the foreground and remove the notification. */
    fun stop() {
      instance?.leaveForeground(removeNotification = true)
    }
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    try {
      val notification = MusicNotificationManager.currentOrPlaceholder(this)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          MusicNotificationManager.NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
      } else {
        startForeground(MusicNotificationManager.NOTIFICATION_ID, notification)
      }
    } catch (e: Exception) {
      Log.w(TAG, "startForeground failed", e)
      stopSelf()
    }
    return START_NOT_STICKY
  }

  private fun leaveForeground(removeNotification: Boolean) {
    try {
      stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
    } catch (e: Exception) {
      Log.w(TAG, "stopForeground failed", e)
    }
    stopSelf()
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    // The user swiped the app away: the player goes with it, so the service must not linger.
    leaveForeground(removeNotification = true)
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    instance = null
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
