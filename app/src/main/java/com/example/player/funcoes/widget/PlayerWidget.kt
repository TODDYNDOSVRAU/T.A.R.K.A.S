package com.example.data.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.example.AppGraph
import com.example.MainActivity
import com.example.R

/**
 * Home-screen widget: track, artist and Previous / Play-Pause / Next. The buttons send media keys
 * to the media session, which also restarts it (restoring the last track) when the app was closed.
 * While the session lives, [com.example.data.playback.PlaybackService] calls [update] on every change.
 */
@UnstableApi
class PlayerWidget : AppWidgetProvider() {

  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
    val model = runCatching {
      AppGraph.init(context)
      WidgetModel.of(AppGraph.playback.currentTrack.value, AppGraph.playback.isPlaying.value)
    }.getOrDefault(WidgetModel.of(null, false))
    ids.forEach { manager.updateAppWidget(it, views(context, model)) }
  }

  companion object {
    /** Redraws every placed widget; does nothing when there is none. */
    fun update(context: Context, model: WidgetModel) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
      if (ids.isEmpty()) return
      val views = views(context, model)
      ids.forEach { manager.updateAppWidget(it, views) }
    }

    /** A media key press addressed to the app's media button receiver (one request code per key). */
    private fun key(context: Context, code: Int): PendingIntent {
      val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(context, MediaButtonReceiver::class.java)
        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
      return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun views(context: Context, model: WidgetModel): RemoteViews {
      val v = RemoteViews(context.packageName, R.layout.widget_player)
      v.setTextViewText(R.id.widget_title, model.title ?: context.getString(R.string.widget_empty_title))
      v.setTextViewText(R.id.widget_artist, model.artist ?: context.getString(R.string.widget_empty_hint))
      v.setImageViewResource(R.id.widget_play, if (model.playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
      v.setContentDescription(R.id.widget_play, context.getString(if (model.playing) R.string.widget_cd_pause else R.string.widget_cd_play))
      v.setOnClickPendingIntent(R.id.widget_play, key(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
      v.setOnClickPendingIntent(R.id.widget_prev, key(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
      v.setOnClickPendingIntent(R.id.widget_next, key(context, KeyEvent.KEYCODE_MEDIA_NEXT))
      v.setOnClickPendingIntent(
        R.id.widget_info,
        PendingIntent.getActivity(
          context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
      )
      return v
    }
  }
}
