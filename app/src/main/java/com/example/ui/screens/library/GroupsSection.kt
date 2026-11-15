package com.example.ui.screens.library

import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import androidx.compose.foundation.layout.fillMaxHeight
import com.example.ui.layout.readingColumn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.library.AlbumGroup
import com.example.data.library.ArtistGroup
import com.example.data.library.LibraryGroup
import com.example.data.library.LibraryGroups
import com.example.data.model.Track
import com.example.ui.components.SearchField
import com.example.ui.components.SubPageHeader
import com.example.ui.components.highlightClickable
import com.example.ui.screens.library.TrackDivider
import com.example.ui.screens.library.itemMotion
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Size
import com.example.ui.theme.Space

/** Separates the keys of the group pages kept in one saved string. */
internal const val GroupKeySeparator = '\u001F'

/** The line under a group's name: its artist and year (albums), its albums (artists) and its songs. */
@Composable
internal fun groupSubtitle(group: LibraryGroup, showArtist: Boolean = true): String {
  val parts = mutableListOf<String>()
  when (group) {
    is AlbumGroup -> {
      if (showArtist) parts += group.artist ?: stringResource(R.string.library_various_artists)
      if (group.year > 0) parts += group.year.toString()
    }
    is ArtistGroup -> if (group.albumCount > 0) {
      parts += pluralStringResource(R.plurals.library_album_count, group.albumCount, group.albumCount)
    }
    else -> Unit
  }
  parts += pluralStringResource(R.plurals.library_track_count, group.tracks.size, group.tracks.size)
  return parts.joinToString(" · ")
}

/** The songs whose covers stand for a group: its first cover for an album, up to four different ones otherwise. */
internal fun groupCovers(group: LibraryGroup): List<Track> =
  if (group is AlbumGroup) {
    listOfNotNull(group.tracks.firstOrNull { it.artworkUrl != null } ?: group.tracks.firstOrNull())
  } else {
    group.tracks.distinctBy { it.artworkUrl ?: it.id }
  }

/** Albums, artists or genres: a search field and one row per group. [kind] names the row tags. */
@Composable
internal fun GroupsList(
  groups: List<LibraryGroup>,
  kind: String,
  @StringRes hintRes: Int,
  @StringRes emptyRes: Int,
  ready: Boolean,
  onOpen: (LibraryGroup) -> Unit
) {
  var query by rememberSaveable(kind) { mutableStateOf("") }
  val shown = remember(groups, query) { groups.filter { com.example.data.TextMatch.contains(it.name, query) } }
  LazyColumn(
    modifier = Modifier.readingColumn().fillMaxHeight(),
    contentPadding = PaddingValues(top = Grid.u6, bottom = LocalBarInset.current + Grid.inset)
  ) {
    if (groups.isNotEmpty()) item(key = "search") {
      SearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = stringResource(hintRes),
        modifier = Modifier
          .padding(horizontal = Grid.margin)
          .padding(bottom = Grid.u6)
          .testTag("library_search_$kind")
      )
    }
    when {
      groups.isEmpty() -> item(key = "empty") { if (ready) Note(stringResource(emptyRes)) }
      shown.isEmpty() -> item(key = "no_match") {
        NoMatch(message = stringResource(R.string.library_search_no_group, query.trim()), onClear = { query = "" })
      }
    }
    itemsIndexed(shown, key = { _, g -> g.key }) { index, group ->
      Column(modifier = itemMotion(index)) {
        if (index > 0) TrackDivider()
        GroupRow(group = group, subtitle = groupSubtitle(group), onClick = { onOpen(group) })
      }
    }
  }
}

@Composable
internal fun GroupRow(group: LibraryGroup, subtitle: String, onClick: () -> Unit) {
  val covers = remember(group) { groupCovers(group) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .highlightClickable(onClickLabel = stringResource(R.string.library_open_group, group.name), onClick = onClick)
      .padding(horizontal = Grid.margin, vertical = Grid.u6)
      .testTag("group_row_${group.key}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    PlaylistArtwork(covers)
    Spacer(Modifier.width(Grid.inset))
    Column(modifier = Modifier.weight(1f)) {
      Text(group.name, style = GridText.Body, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(subtitle, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(
      Icons.Rounded.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      modifier = Modifier.size(Grid.u24)
    )
  }
}

/** The page of one album, artist or genre: its songs, and for an artist its albums above them. */
@Composable
internal fun GroupDetail(
  group: LibraryGroup,
  currentTrack: Track?,
  isPlaying: Boolean,
  onBack: () -> Unit,
  onPlay: (Track) -> Unit,
  onShuffle: () -> Unit,
  onOpenGroup: (String) -> Unit,
  trailing: @Composable RowScope.(Track) -> Unit
) {
  val subtitle = groupSubtitle(group)
  val albums = remember(group) { if (group is ArtistGroup) LibraryGroups.albums(group.tracks) else emptyList() }
  TrackList(
    tracks = group.tracks,
    currentTrack = currentTrack,
    isPlaying = isPlaying,
    onPlay = onPlay,
    onShuffle = onShuffle,
    header = {
      item(key = "header") {
        SubPageHeader(
          title = group.name,
          backLabel = stringResource(R.string.library_back_label),
          onBack = onBack,
          modifier = Modifier.testTag("library_back")
        )
      }
      item(key = "info") {
        Text(
          subtitle,
          style = GridText.Small,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = Grid.margin).padding(bottom = Grid.u6)
        )
      }
      if (albums.isNotEmpty()) {
        item(key = "albums_heading") {
          Text(
            stringResource(R.string.library_group_albums_heading),
            style = GridText.Small,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u6).semantics { heading() }
          )
        }
        itemsIndexed(albums, key = { _, a -> "album_${a.key}" }) { index, album ->
          Column {
            if (index > 0) TrackDivider()
            GroupRow(group = album, subtitle = groupSubtitle(album, showArtist = false), onClick = { onOpenGroup(album.key) })
          }
        }
      }
    },
    empty = {},
    trailing = trailing
  )
}
