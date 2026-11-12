package com.example.data.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.R

/**
 * Receives the installer's answer. When Android needs one confirmation from the person (the first
 * update of a copy this app did not install), the confirmation screen opens at once if the app is in
 * front, or is offered as a notification if it is not.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    UpdateManager.init(context)
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    UpdateManager.onInstallResult(status)
    if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return
    @Suppress("DEPRECATION")
    val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!runCatching { context.startActivity(confirm) }.isSuccess) notify(context, confirm)
  }

  private fun notify(context: Context, confirm: Intent) {
    val nm = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26) {
      nm.createNotificationChannel(NotificationChannel(CHANNEL, "Atualizações", NotificationManager.IMPORTANCE_DEFAULT))
    }
    val pending = PendingIntent.getActivity(context, 1, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.ic_notification)
      .setContentTitle("Atualização do T.A.R.K.A.S. pronta")
      .setContentText("Toque para confirmar a instalação.")
      .setContentIntent(pending)
      .setAutoCancel(true)
      .build()
    runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
  }

  private companion object {
    const val CHANNEL = "tarkas_updates"
    const val NOTIFICATION_ID = 4418
  }
}
