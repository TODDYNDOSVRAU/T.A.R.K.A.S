package com.example.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {
  companion object {
    const val ACTION_PREV = "com.example.tarkas.ACTION_PREV"
    const val ACTION_TOGGLE = "com.example.tarkas.ACTION_TOGGLE"
    const val ACTION_NEXT = "com.example.tarkas.ACTION_NEXT"
    // From the media session only (headset, car, watch): a device asks for one state, not a toggle.
    const val ACTION_PLAY = "com.example.tarkas.ACTION_PLAY"
    const val ACTION_PAUSE = "com.example.tarkas.ACTION_PAUSE"

    var onActionReceived: ((String) -> Unit)? = null
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    intent?.action?.let { action ->
      onActionReceived?.invoke(action)
    }
  }
}
