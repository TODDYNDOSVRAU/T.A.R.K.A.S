package com.example.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.playback.PlaybackProblem

/**
 * Says on the player itself that the track does not play, and what to do: stays until it plays or
 * another track starts (a notice would be gone in seconds). Read aloud when it appears (live region).
 */
@Composable
fun PlaybackProblemBanner(problem: PlaybackProblem?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  if (problem == null) return
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(PlayerGrid.radius12))
      .background(Color.White.copy(alpha = 0.1f))
      .padding(start = PlayerGrid.g12)
      .semantics { liveRegion = LiveRegionMode.Polite }
      .testTag("playback_problem"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = stringResource(if (problem.network) R.string.player_problem_offline else R.string.player_problem_failed),
      color = Color(0xFFF2F2F0),
      fontSize = PlayerGrid.text12,
      modifier = Modifier.weight(1f).padding(vertical = PlayerGrid.g12)
    )
    Text(
      text = stringResource(R.string.player_notice_retry),
      color = Color(0xFF7EE3EA),
      fontSize = PlayerGrid.text12,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier
        .heightIn(min = PlayerGrid.g48)
        .clickable(role = Role.Button, onClick = onRetry)
        .padding(horizontal = PlayerGrid.g12, vertical = PlayerGrid.g12)
        .testTag("playback_problem_retry")
    )
  }
}
