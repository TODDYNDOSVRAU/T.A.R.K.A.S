package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.playback.SleepTimer
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MIN_MINUTES = 5
private const val MAX_MINUTES = 120
private const val STEP_MINUTES = 5
private const val DEFAULT_MINUTES = 30

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
 * Picks when the music pauses by itself: slide to the minutes (5 to 120, in steps of 5) and start, or
 * choose the end of the current track. The body scrolls, because at a large system font size the
 * slider, the option and the buttons do not fit a small screen.
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
  var minutes by rememberSaveable { mutableIntStateOf(DEFAULT_MINUTES) }
  TarkasAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.player_sleep_title), style = GridText.BodyStrong) },
    text = {
      // The body is capped at half the window height, so in landscape the dialog keeps clear of the
      // screen edges and its title and buttons stay in view while only the body scrolls.
      val maxBodyHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).dp
      Column(
        modifier = Modifier
          .heightIn(max = maxBodyHeight)
          .verticalScroll(rememberScrollState())
      ) {
        if (label != null) {
          Text(
            stringResource(R.string.player_sleep_pauses_in, label),
            style = GridText.Small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Grid.u6)
          )
        }
        Text(
          pluralStringResource(R.plurals.player_sleep_minutes, minutes, minutes),
          style = GridText.Title,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxWidth().padding(vertical = Grid.u6).testTag("sleep_minutes_value")
        )
        Slider(
          value = minutes.toFloat(),
          onValueChange = { minutes = ((it / STEP_MINUTES).roundToInt() * STEP_MINUTES).coerceIn(MIN_MINUTES, MAX_MINUTES) },
          valueRange = MIN_MINUTES.toFloat()..MAX_MINUTES.toFloat(),
          steps = (MAX_MINUTES - MIN_MINUTES) / STEP_MINUTES - 1,
          modifier = Modifier.fillMaxWidth().heightIn(min = Grid.u48).testTag("sleep_slider")
        )
        Option(stringResource(R.string.player_sleep_end_of_track), "sleep_end_of_track") { onEndOfTrack(); onDismiss() }
        if (timer != null) {
          TextButton(onClick = { onCancel(); onDismiss() }, modifier = Modifier.testTag("sleep_turn_off")) {
            Text(stringResource(R.string.player_sleep_turn_off), color = MaterialTheme.colorScheme.error)
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onMinutes(minutes); onDismiss() }, modifier = Modifier.testTag("sleep_start")) {
        Text(stringResource(R.string.player_sleep_start))
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_sleep_close)) } }
  )
}

@Composable
private fun Option(text: String, tag: String, onClick: () -> Unit) {
  Text(
    text,
    style = GridText.Body,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = Grid.u48)
      .highlightClickable(onClick = onClick)
      .padding(vertical = Grid.u12)
      .testTag(tag)
  )
}
