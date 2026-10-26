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
