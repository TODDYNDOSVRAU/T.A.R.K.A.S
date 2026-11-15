package com.example.ui.screens.library

import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import androidx.compose.foundation.layout.fillMaxHeight
import com.example.ui.layout.readingColumn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.ui.components.EmptyState
import com.example.ui.components.IconAction
import com.example.ui.components.SearchField
import com.example.ui.components.SubPageHeader
import com.example.ui.components.TrackArtwork
import com.example.ui.components.highlightClickable
import com.example.ui.screens.library.TrackDivider
import com.example.ui.screens.library.itemMotion
import com.example.ui.screens.library.trailingEdge
import com.example.ui.theme.AppText
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space

/** Artwork of a playlist row. */
internal val PlaylistArtworkSize = 48.dp

@Composable
internal fun PlaylistsList(
  playlists: List<Playlist>,
  byId: Map<String, Track>,
  onOpen: (Playlist) -> Unit,
  onNew: () -> Unit,
  onImportAudio: () -> Unit
) {
  var query by rememberSaveable { mutableStateOf("") }
  val shown = playlists.filter { com.example.data.TextMatch.contains(it.name, query) }
  LazyColumn(
    modifier = Modifier.readingColumn().fillMaxHeight(),
    contentPadding = PaddingValues(top = Grid.u6, bottom = LocalBarInset.current + Grid.inset)
  ) {
    item(key = "search") {
      SearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = stringResource(R.string.library_search_playlists_hint),
        modifier = Modifier
          .padding(horizontal = Grid.margin)
          .padding(bottom = Grid.u6)
          .testTag("library_search_input")
      )
    }
    item(key = "new") {
      ActionRow(stringResource(R.string.library_new_playlist), Icons.Rounded.Add, onNew, Modifier.testTag("library_new_playlist"))
    }
    item(key = "import") {
      TrackDivider()
      ActionRow(stringResource(R.string.library_import_audio), Icons.Rounded.AudioFile, onImportAudio, Modifier.testTag("library_add_local_audio"))
    }
    when {
      playlists.isEmpty() -> item(key = "empty") {
        Note(stringResource(R.string.library_playlists_empty_note))
      }
      shown.isEmpty() -> item(key = "no_match") { Note(stringResource(R.string.library_search_no_playlist, query.trim())) }
    }
    itemsIndexed(shown, key = { _, p -> p.id }) { index, playlist ->
      Column(modifier = itemMotion(index)) {
        TrackDivider()
        PlaylistRow(
          playlist = playlist,
          tracks = playlist.trackIds.mapNotNull { byId[it] },
          onClick = { onOpen(playlist) }
        )
      }
    }
  }
}

/** A row that creates something, in the accent: a tinted square with the glyph, then the label. */
@Composable
internal fun ActionRow(title: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .highlightClickable(onClick = onClick)
      .padding(horizontal = Grid.margin, vertical = Grid.u6),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(Grid.u48)
        .clip(Grid.r6)
        .background(MaterialTheme.colorScheme.surfaceContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Grid.u24))
    }
    Spacer(Modifier.width(Grid.inset))
    Text(title, style = GridText.Body, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
internal fun PlaylistRow(playlist: Playlist, tracks: List<Track>, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .highlightClickable(onClickLabel = stringResource(R.string.library_open_playlist, playlist.name), onClick = onClick)
      .padding(horizontal = Grid.margin, vertical = Grid.u6)
      .testTag("playlist_row_${playlist.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    PlaylistArtwork(tracks)
    Spacer(Modifier.width(Grid.inset))
    Column(modifier = Modifier.weight(1f)) {
      Text(playlist.name, style = GridText.Body, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(
        if (playlist.trackIds.isEmpty()) stringResource(R.string.library_tracks_none)
        else pluralStringResource(R.plurals.library_track_count, playlist.trackIds.size, playlist.trackIds.size),
        style = GridText.Small,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Icon(
      Icons.Rounded.ChevronRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      modifier = Modifier.size(Grid.u24)
    )
  }
}

/**
 * A playlist's cover: four covers in a grid once it has four songs with artwork, else the first
 * song's cover, else a quiet tile with the playlist glyph.
 */
@Composable
internal fun PlaylistArtwork(tracks: List<Track>) {
  val covers = tracks.filter { it.artworkUrl != null }.take(4)
  Box(
    modifier = Modifier
      .size(PlaylistArtworkSize)
      .clip(Grid.r6)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center
  ) {
    when {
      covers.size == 4 -> Column {
        covers.chunked(2).forEach { pair ->
          Row {
            pair.forEach { t ->
              TrackArtwork(track = t, shape = RectangleShape, iconSize = 12.dp, modifier = Modifier.size(PlaylistArtworkSize / 2))
            }
          }
        }
      }
      tracks.isNotEmpty() -> TrackArtwork(
        track = covers.firstOrNull() ?: tracks.first(),
        shape = RectangleShape,
        iconSize = Grid.u24,
        modifier = Modifier.size(PlaylistArtworkSize)
      )
      else -> Icon(
        Icons.AutoMirrored.Rounded.QueueMusic,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(Grid.u24)
      )
    }
  }
}

@Composable
internal fun PlaylistDetail(
  playlist: Playlist,
  tracks: List<Track>,
  currentTrack: Track?,
  isPlaying: Boolean,
  onBack: () -> Unit,
  onPlay: (Track) -> Unit,
  onShuffle: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onRemoveTrack: (String) -> Unit
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
          title = playlist.name,
          backLabel = stringResource(R.string.library_back_label),
          onBack = onBack,
          modifier = Modifier.testTag("library_back"),
          trailing = {
            IconAction(Icons.Rounded.Edit, contentDescription = stringResource(R.string.library_rename_desc), onClick = onRename, tint = MaterialTheme.colorScheme.primary)
            IconAction(Icons.Rounded.Delete, contentDescription = stringResource(R.string.library_delete_desc), onClick = onDelete, tint = MaterialTheme.colorScheme.error)
          }
        )
      }
    },
    empty = {
      EmptyState(
        icon = Icons.AutoMirrored.Rounded.QueueMusic,
        title = stringResource(R.string.library_playlist_empty_title),
        message = stringResource(R.string.library_playlist_empty_message),
        modifier = Modifier.padding(horizontal = Grid.margin)
      )
    },
    trailing = { track ->
      IconAction(
        icon = Icons.Rounded.Close,
        contentDescription = stringResource(R.string.library_playlist_remove, track.title),
        onClick = { onRemoveTrack(track.id) },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.trailingEdge()
      )
    }
  )
}
