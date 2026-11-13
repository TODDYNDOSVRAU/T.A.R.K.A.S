package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.playback.SleepTimer
import com.example.ui.theme.AppText
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import kotlinx.coroutines.delay

private val MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)

/** What the sleep timer is set to, for the player's menu: "23 min", "fim da faixa"; null when off. */
@Composable
fun sleepTimerLabel(timer: SleepTimer?): String? {
  if (timer == null) return null
  if (timer.endOfTrack) return stringResource(R.string.player_sleep_label_end_of_track)
  val endsAt = timer.endsAtMs ?: return null
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(endsAt) {
    while (true) {
      now = System.currentTimeMillis()
      delay(15_000)
    }
  }
  val minutes = ((endsAt - now + 59_999) / 60_000).coerceAtLeast(1)
  return stringResource(R.string.player_sleep_label_minutes, minutes)
}

/**
 * Picks when the music pauses by itself: after some minutes, or at the end of the current track.
 * The options scroll, because eight 48 dp rows plus the title do not fit a small screen at a large
 * system font size.
 */
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
    title = { Text(stringResource(R.string.player_sleep_title), style = AppText.Title3) },
    text = {
      // The list is capped at half the window height, so in landscape the dialog keeps clear of the
      // screen edges and its title and buttons stay in view while only the options scroll.
      val maxListHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).dp
      Column(
        modifier = Modifier
          .heightIn(max = maxListHeight)
          .verticalScroll(rememberScrollState())
      ) {
        if (label != null) {
          Text(
            stringResource(R.string.player_sleep_pauses_in, label),
            style = AppText.Subhead,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Space.s)
          )
        }
        MINUTES.forEach { m ->
          Option(pluralStringResource(R.plurals.player_sleep_minutes, m, m), "sleep_$m") { onMinutes(m); onDismiss() }
        }
        Option(stringResource(R.string.player_sleep_end_of_track), "sleep_end_of_track") { onEndOfTrack(); onDismiss() }
      }
    },
    confirmButton = {
      if (timer != null) {
        TextButton(onClick = { onCancel(); onDismiss() }) {
          Text(stringResource(R.string.player_sleep_turn_off), color = MaterialTheme.colorScheme.error)
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_sleep_close)) } }
  )
}

@Composable
private fun Option(text: String, tag: String, onClick: () -> Unit) {
  Text(
    text,
    style = AppText.Body,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = Size.touch)
      .highlightClickable(onClick = onClick)
      .padding(vertical = Space.m)
      .testTag(tag)
  )
}
