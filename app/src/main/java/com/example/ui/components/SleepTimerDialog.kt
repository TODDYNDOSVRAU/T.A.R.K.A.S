package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.playback.SleepTimer
import kotlinx.coroutines.delay

private val MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)

/** What the sleep timer is set to, for the player's menu: "23 min", "fim da faixa"; null when off. */
@Composable
fun sleepTimerLabel(timer: SleepTimer?): String? {
  if (timer == null) return null
  if (timer.endOfTrack) return "fim da faixa"
  val endsAt = timer.endsAtMs ?: return null
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(endsAt) {
    while (true) {
      now = System.currentTimeMillis()
      delay(15_000)
    }
  }
  val minutes = ((endsAt - now + 59_999) / 60_000).coerceAtLeast(1)
  return "$minutes min"
}

/** Picks when the music pauses by itself: after some minutes, or at the end of the current track. */
@Composable
fun SleepTimerDialog(
  timer: SleepTimer?,
  onMinutes: (Int) -> Unit,
  onEndOfTrack: () -> Unit,
  onCancel: () -> Unit,
  onDismiss: () -> Unit
) {
  val label = sleepTimerLabel(timer)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Timer de dormir") },
    text = {
      Column {
        if (label != null) {
          Text(
            "A música pausa em $label.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
          )
        }
        MINUTES.forEach { m ->
          Option("$m minutos", "sleep_$m") { onMinutes(m); onDismiss() }
        }
        Option("No fim desta faixa", "sleep_end_of_track") { onEndOfTrack(); onDismiss() }
      }
    },
    confirmButton = {
      if (timer != null) TextButton(onClick = { onCancel(); onDismiss() }) { Text("Desligar timer") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
  )
}

@Composable
private fun Option(text: String, tag: String, onClick: () -> Unit) {
  Text(
    text,
    fontSize = 17.sp,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp)
      .testTag(tag)
  )
}
