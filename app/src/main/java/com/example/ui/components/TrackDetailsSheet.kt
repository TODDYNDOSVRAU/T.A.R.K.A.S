package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.theme.AppText
import com.example.ui.theme.Radius
import com.example.ui.theme.Shapes
import com.example.ui.theme.Space
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
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(bottom = Space.l)
        .testTag("track_details_sheet")
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TrackArtwork(
          // The track's own cover first; the release's from the Cover Art Archive when it has none.
          track = if (track.artworkUrl == null && info?.coverUrl != null) track.copy(artworkUrl = info.coverUrl) else track,
          shape = Shapes.s,
          iconSize = 32.dp,
          modifier = Modifier.size(88.dp)
        )
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
          Text(track.title, style = AppText.Title3, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text(track.artist, style = AppText.Subhead, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Hairline(Modifier.padding(vertical = Space.s))
      GroupHeader("Sobre a faixa", Modifier.padding(top = Space.s))

      when {
        state.loading -> Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xl)
            .semantics { stateDescription = "Buscando no MusicBrainz" },
          contentAlignment = Alignment.Center
        ) {
          CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        state.offline -> EmptyState(
          icon = Icons.Rounded.CloudOff,
          title = "Sem conexão com o MusicBrainz",
          message = "Confira a internet e tente de novo.",
          actionLabel = "Tentar de novo",
          onAction = onRetry,
          modifier = Modifier.padding(horizontal = Space.page).testTag("track_details_offline")
        )
        info == null -> Text(
          "O MusicBrainz não tem esta faixa, ou não tem certeza de qual é.",
          style = AppText.Subhead,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s).testTag("track_details_not_found")
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
          info.release?.let { release ->
            InfoRow("Álbum", listOfNotNull(release, info.releaseType?.let(::releaseTypeLabel)).joinToString(" · "))
            GroupDivider()
          }
          info.firstReleaseDate?.let {
            InfoRow("Lançamento", formatReleaseDate(it))
            GroupDivider()
          }
          if (info.artist.isNotBlank() && !info.artist.equals(track.artist, ignoreCase = true)) {
            InfoRow("Artista no MusicBrainz", info.artist)
            GroupDivider()
          }
          if (info.tags.isNotEmpty()) {
            InfoRow("Gêneros", info.tags.joinToString(", ") { it.replaceFirstChar(Char::uppercase) })
            GroupDivider()
          }
          ListRow(
            title = "Ver no MusicBrainz",
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
        "Dados do MusicBrainz (CC0) e capas do Cover Art Archive.",
        style = AppText.Footnote,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.page, vertical = Space.m)
      )
    }
  }
}

private fun releaseTypeLabel(type: String): String = when (type.lowercase()) {
  "album" -> "Álbum"
  "single" -> "Single"
  "ep" -> "EP"
  "broadcast" -> "Transmissão"
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
