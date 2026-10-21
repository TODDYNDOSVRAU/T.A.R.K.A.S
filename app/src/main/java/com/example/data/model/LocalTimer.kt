package com.example.data.model

data class LocalTimer(
  val id: String,
  val label: String,
  val initialSeconds: Int,
  val remainingSeconds: Int,
  val isRunning: Boolean,
  val isCompleted: Boolean = false
) {
  fun formattedTime(): String {
    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    return String.format("%02d:%02d", mins, secs)
  }
}

data class LocalReminder(
  val id: String,
  val title: String,
  val scheduledEpochMs: Long,
  val isTriggered: Boolean = false
) {
  fun formattedDateTime(): String {
    val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(scheduledEpochMs))
  }
}
