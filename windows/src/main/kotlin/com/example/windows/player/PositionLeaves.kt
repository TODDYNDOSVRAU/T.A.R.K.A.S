package com.example.windows.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.theme.Grid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.GridText

/**
 * The only places that read the playing position. The screens' bodies read [PlayerHooks.steady] (no position), so
 * the clock recomposes one of these leaves and nothing else.
 */

/** The thin seek line alone: the bars of the mini player and the transport bar. */
@Composable
internal fun PositionProgress(hooks: PlayerHooks, modifier: Modifier) {
  val t by hooks.state.collectAsState()
  ThinProgress(t.fraction, t.durationMs > 0, hooks::seekFraction, modifier, live = false)
}

/** The side player's seek line with the elapsed and total times under it. */
@Composable
internal fun PositionSeekLine(hooks: PlayerHooks, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  val t by hooks.state.collectAsState()
  var scrub by remember { mutableStateOf<Float?>(null) }
  val shownMs = scrub?.let { SeekMath.fromFraction(it, t.durationMs) } ?: t.positionMs
  Column(modifier) {
    ThinProgress(
      t.fraction, t.durationMs > 0, hooks::seekFraction, Modifier.fillMaxWidth().testTag("side_progress"),
      live = false, onScrub = { scrub = it },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(SeekMath.label(shownMs), style = GridText.Label, color = if (scrub != null) cs.primary else cs.onSurfaceVariant, modifier = Modifier.testTag("side_elapsed"))
      Text(SeekMath.label(t.durationMs), style = GridText.Label, color = cs.onSurfaceVariant, modifier = Modifier.testTag("side_total"))
    }
  }
}

/** The compact player's second row: elapsed time, the seek line and the total, 36 dp of time on each side. Reads the position, nothing else does. */
@Composable
internal fun PositionSeekRow(hooks: PlayerHooks, modifier: Modifier = Modifier) {
  val cs = MaterialTheme.colorScheme
  val t by hooks.state.collectAsState()
  var scrub by remember { mutableStateOf<Float?>(null) }
  val shownMs = scrub?.let { SeekMath.fromFraction(it, t.durationMs) } ?: t.positionMs
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(Grid.u6), verticalAlignment = Alignment.CenterVertically) {
    Text(
      SeekMath.label(shownMs), style = GridText.Label, color = if (scrub != null) cs.primary else PillText.secondary(cs), maxLines = 1,
      textAlign = TextAlign.End, modifier = Modifier.width(Grid.u12 * 3).testTag("compact_elapsed"),
    )
    ThinProgress(
      t.fraction, t.durationMs > 0, hooks::seekFraction, Modifier.weight(1f).testTag("compact_progress"),
      live = false, onScrub = { scrub = it },
    )
    Text(SeekMath.label(t.durationMs), style = GridText.Label, color = PillText.secondary(cs), maxLines = 1, modifier = Modifier.width(Grid.u12 * 3).testTag("compact_total"))
  }
}
