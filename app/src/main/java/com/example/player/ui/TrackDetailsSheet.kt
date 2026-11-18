package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.example.R
import com.example.data.model.Track
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.ui.viewmodel.TrackDetailsState

/**
 * "Sobre a faixa": what MusicBrainz knows about the playing song (album, release date, genres), with
 * the Cover Art Archive's cover when the track has none of its own, and a link to its page. Loading,
 * nothing found and offline each say so in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailsSheet(
  track: Track,
  state: TrackDetailsState,
  onRetry: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val info = state.info
  val loadingDescription = stringResource(R.string.player_details_loading)
  ModalBottomSheet(
    sheetMaxWidth = Grid.u384 * 2,
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(topStart = Grid.u24, topEnd = Grid.u24),
    dragHandle = { SheetHandle() }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(bottom = Grid.u12)
        .testTag("track_details_sheet")
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Grid.margin, vertical = Grid.u6),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TrackArtwork(
          // The track's own cover first; the release's from the Cover Art Archive when it has none.
          track = if (track.artworkUrl == null && info?.coverUrl != null) track.copy(artworkUrl = info.coverUrl) else track,
          shape = Grid.r12,
          iconSize = Grid.u24,
          modifier = Modifier.size(Grid.u96)
        )
        Spacer(Modifier.width(Grid.u12))
        Column(modifier = Modifier.weight(1f)) {
          Text(track.title, style = GridText.BodyStrong, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text(track.artist, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Hairline(Modifier.padding(vertical = Grid.u6))
      // The rows of this sheet run the full width, so the header aligns with them (GroupHeader is indented
      // for rows inside a card).
      Text(
        stringResource(R.string.player_details_header),
        style = GridText.Small,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .padding(start = Grid.margin, end = Grid.margin, top = Grid.u6, bottom = Grid.u6)
          .semantics { heading() }
      )

      when {
        state.loading -> Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Grid.u24)
            .semantics { stateDescription = loadingDescription },
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(modifier = Modifier.size(Grid.icon), strokeWidth = Grid.hairline * 2)
        }
        state.offline -> EmptyState(
          icon = Icons.Rounded.CloudOff,
          title = stringResource(R.string.player_details_offline_title),
          message = stringResource(R.string.player_details_offline_message),
          actionLabel = stringResource(R.string.player_details_retry),
          onAction = onRetry,
          modifier = Modifier.padding(horizontal = Grid.margin).testTag("track_details_offline")
        )
        info == null -> Text(
          stringResource(R.string.player_details_not_found),
          style = GridText.Small,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u6).testTag("track_details_not_found")
        )
        else -> Column {
          info.release?.let { release ->
            InfoRow(stringResource(R.string.player_details_album), listOfNotNull(release, info.releaseType?.let { releaseTypeLabel(it) }).joinToString(" · "))
            GroupDivider()
          }
          info.firstReleaseDate?.let {
            InfoRow(stringResource(R.string.player_details_release_date), formatReleaseDate(it))
            GroupDivider()
          }
          if (info.artist.isNotBlank() && !info.artist.equals(track.artist, ignoreCase = true)) {
            InfoRow(stringResource(R.string.player_details_artist), info.artist)
            GroupDivider()
          }
          if (info.tags.isNotEmpty()) {
            InfoRow(stringResource(R.string.player_details_genres), info.tags.joinToString(", ") { it.replaceFirstChar(Char::uppercase) })
            GroupDivider()
          }
          ListRow(
            title = stringResource(R.string.player_details_open),
            onClick = {
              runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
              }
            },
            modifier = Modifier.testTag("track_details_open_musicbrainz")
          )
        }
      }
      Text(
        stringResource(R.string.player_details_attribution),
        style = GridText.Small,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u12)
      )
    }
  }
}

@Composable
private fun releaseTypeLabel(type: String): String = when (type.lowercase()) {
  "album" -> stringResource(R.string.player_details_type_album)
  "single" -> stringResource(R.string.player_details_type_single)
  "ep" -> "EP"
  "broadcast" -> stringResource(R.string.player_details_type_broadcast)
  else -> type
}

/** "2014-03-17" → "17/03/2014"; "2014-03" → "03/2014"; a bare year stays as it is. */
internal fun formatReleaseDate(date: String): String {
  val parts = date.split('-')
  return when (parts.size) {
    3 -> "${parts[2]}/${parts[1]}/${parts[0]}"
    2 -> "${parts[1]}/${parts[0]}"
    else -> date
  }
}
