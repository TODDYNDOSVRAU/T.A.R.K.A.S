package com.example.ui.screens.library

import androidx.compose.foundation.layout.fillMaxHeight
import com.example.ui.layout.readingColumn
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.library.AutoList
import com.example.data.model.Track
import com.example.ui.components.EmptyState
import com.example.ui.components.SubPageHeader
import com.example.ui.components.highlightClickable
import com.example.ui.screens.library.TrackDivider
import com.example.ui.screens.library.itemMotion
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Size
import com.example.ui.theme.Space

@StringRes
internal fun AutoList.nameRes(): Int = when (this) {
  AutoList.RECENTLY_ADDED -> R.string.library_auto_recently_added
  AutoList.RECENTLY_PLAYED -> R.string.library_auto_recently_played
  AutoList.MOST_PLAYED -> R.string.library_auto_most_played
}

/** The three automatic lists, one row each with the same cover grid as a playlist. */
@Composable
internal fun AutoListsOverview(lists: Map<AutoList, List<Track>>, byId: Map<String, Track>, onOpen: (AutoList) -> Unit) {
  LazyColumn(
    modifier = Modifier.readingColumn().fillMaxHeight(),
    contentPadding = PaddingValues(top = Space.s, bottom = LocalBarInset.current + Space.l)
  ) {
    itemsIndexed(AutoList.entries.toList(), key = { _, l -> l.name }) { index, list ->
      val tracks = lists.getValue(list)
      val name = stringResource(list.nameRes())
      Column(modifier = itemMotion(index)) {
        if (index > 0) TrackDivider()
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .highlightClickable(onClickLabel = stringResource(R.string.library_auto_open, name), onClick = { onOpen(list) })
            .padding(horizontal = Space.page, vertical = 6.dp)
            .testTag("auto_row_${list.name.lowercase()}"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          PlaylistArtwork(tracks.map { byId[it.id] ?: it })
          Spacer(Modifier.width(Space.m))
          Column(modifier = Modifier.weight(1f)) {
            Text(name, style = AppText.Body, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
              if (tracks.isEmpty()) stringResource(R.string.library_tracks_none)
              else pluralStringResource(R.plurals.library_track_count, tracks.size, tracks.size),
              style = AppText.Subhead,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(Size.iconSmall)
          )
        }
      }
    }
  }
}

@Composable
internal fun AutoListDetail(
  list: AutoList,
  tracks: List<Track>,
  currentTrack: Track?,
  isPlaying: Boolean,
  onBack: () -> Unit,
  onPlay: (Track) -> Unit,
  onShuffle: () -> Unit
) {
  TrackList(
    tracks = tracks,
    currentTrack = currentTrack,
    isPlaying = isPlaying,
    onPlay = onPlay,
    onShuffle = onShuffle,
    header = {
      item(key = "header") {
        SubPageHeader(
          title = stringResource(list.nameRes()),
          backLabel = stringResource(R.string.library_back_label),
          onBack = onBack,
          modifier = Modifier.testTag("library_back")
        )
      }
    },
    empty = {
      val (title, message) = when (list) {
        AutoList.RECENTLY_ADDED -> R.string.library_auto_empty_title_added to R.string.library_auto_empty_message_added
        AutoList.RECENTLY_PLAYED -> R.string.library_auto_empty_title_played to R.string.library_auto_empty_message_played
        AutoList.MOST_PLAYED -> R.string.library_auto_empty_title_most to R.string.library_auto_empty_message_most
      }
      EmptyState(
        icon = Icons.AutoMirrored.Rounded.QueueMusic,
        title = stringResource(title),
        message = stringResource(message),
        modifier = Modifier.padding(horizontal = Space.page)
      )
    }
  )
}
