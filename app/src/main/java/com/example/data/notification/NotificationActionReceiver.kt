package com.example.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {
  companion object {
    const val ACTION_PREV = "com.example.tarkas.ACTION_PREV"
    const val ACTION_TOGGLE = "com.example.tarkas.ACTION_TOGGLE"
    const val ACTION_NEXT = "com.example.tarkas.ACTION_NEXT"

    var onActionReceived: ((String) -> Unit)? = null
  }

  override fun onReceive(context: Context?, intent: Intent?) {
    intent?.action?.let { action ->
      onActionReceived?.invoke(action)
    }
  }
}
