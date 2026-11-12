package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.Playlist
import com.example.data.model.Track
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.AppText
import com.example.ui.theme.Radius
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * The "more" button of a track and the sheet it opens: favourite, play next, queue, add to a playlist
 * (and, in the player, "Sobre a faixa" and the sleep timer). A bottom sheet rather than a dropdown, so every action is a
 * full-width row with a large target, and the playlist list has room to scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsMenu(
  track: Track,
  isFavorite: Boolean,
  playlists: List<Playlist>,
  onToggleFavorite: () -> Unit,
  onPlayNext: () -> Unit,
  onAddToQueue: () -> Unit,
  onAddToPlaylist: (playlistId: String) -> Unit,
  onCreatePlaylist: (name: String) -> Unit,
  modifier: Modifier = Modifier,
  iconTint: Color? = null,
  icon: ImageVector = Icons.Rounded.MoreHoriz,
  /** Shown only in the player: the sleep timer entry, with what it is set to (null = off). */
  sleepTimerLabel: String? = null,
  onSleepTimer: (() -> Unit)? = null,
  /** Shown only in the player: "Sobre a faixa" (album, date and genres from MusicBrainz). */
  onAbout: (() -> Unit)? = null
) {
  // Saved, so a rotation keeps the sheet (and the playlist step) where the user left it.
  var expanded by rememberSaveable { mutableStateOf(false) }
  var showPlaylists by rememberSaveable { mutableStateOf(false) }
  var showNewPlaylist by rememberSaveable { mutableStateOf(false) }

  IconButton(
    onClick = { showPlaylists = false; expanded = true },
    modifier = modifier
      .size(Size.touch)
      .testTag("track_menu_${track.id}")
  ) {
    Icon(
      imageVector = icon,
      contentDescription = stringResource(R.string.player_menu_more, track.title),
      tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant
    )
  }

  if (expanded) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Runs the action, then lets the sheet slide away before it leaves the composition.
    fun close(then: () -> Unit = {}) {
      then()
      scope.launch { sheetState.hide() }.invokeOnCompletion {
        expanded = false
        showPlaylists = false
      }
    }
    ModalBottomSheet(
      onDismissRequest = { expanded = false; showPlaylists = false },
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
      shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(bottom = Space.l)
      ) {
        SheetHeader(track)
        Hairline(Modifier.padding(vertical = Space.s))
        if (!showPlaylists) {
          ListRow(
            title = stringResource(if (isFavorite) R.string.player_menu_unfavorite else R.string.player_menu_favorite),
            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            chevron = false,
            onClick = { close(onToggleFavorite) }
          )
          ListRow(
            title = stringResource(R.string.player_menu_play_next),
            icon = Icons.Rounded.SkipNext,
            chevron = false,
            onClick = { close(onPlayNext) }
          )
          ListRow(
            title = stringResource(R.string.player_menu_add_queue),
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            chevron = false,
            onClick = { close(onAddToQueue) }
          )
          ListRow(
            title = stringResource(R.string.player_menu_add_playlist),
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            onClick = { showPlaylists = true }
          )
          if (onAbout != null) {
            ListRow(
              title = stringResource(R.string.player_menu_about),
              icon = Icons.Rounded.Info,
              chevron = false,
              onClick = { close(onAbout) },
              modifier = Modifier.testTag("menu_track_details")
            )
          }
          if (onSleepTimer != null) {
            ListRow(
              title = stringResource(R.string.player_menu_sleep_timer),
              icon = Icons.Rounded.Bedtime,
              value = sleepTimerLabel,
              chevron = false,
              onClick = { close(onSleepTimer) },
              modifier = Modifier.testTag("menu_sleep_timer")
            )
          }
        } else {
          GroupHeader(stringResource(R.string.player_menu_add_playlist), Modifier.padding(top = Space.s))
          ListRow(
            title = stringResource(R.string.player_menu_new_playlist_item),
            icon = Icons.Rounded.Add,
            chevron = false,
            onClick = { close { showNewPlaylist = true } }
          )
          playlists.forEach { playlist ->
            ListRow(
              title = playlist.name,
              subtitle = pluralStringResource(R.plurals.player_menu_tracks, playlist.trackIds.size, playlist.trackIds.size),
              icon = Icons.AutoMirrored.Rounded.QueueMusic,
              iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
              chevron = false,
              onClick = { close { onAddToPlaylist(playlist.id) } }
            )
          }
        }
      }
    }
  }

  if (showNewPlaylist) {
    NamePlaylistDialog(
      title = stringResource(R.string.player_menu_new_playlist),
      confirmLabel = stringResource(R.string.player_menu_create),
      initialName = "",
      onConfirm = { name -> onCreatePlaylist(name); showNewPlaylist = false },
      onDismiss = { showNewPlaylist = false }
    )
  }
}

/**
 * The menu of a queued song: move it up or down and take it out, the touch alternative to dragging by
 * the handle and swiping away. "Up" is off on the first song and "down" on the last. Removing goes
 * through the same callback as the swipe, so the undo is the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueItemMenu(
  track: Track,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by rememberSaveable { mutableStateOf(false) }

  IconButton(
    onClick = { expanded = true },
    modifier = modifier
      .size(Size.touch)
      .testTag("queue_menu_${track.id}")
  ) {
    Icon(
      imageVector = Icons.Rounded.MoreHoriz,
      contentDescription = stringResource(R.string.queue_item_menu, track.title),
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }

  if (expanded) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) {
      then()
      scope.launch { sheetState.hide() }.invokeOnCompletion { expanded = false }
    }
    ModalBottomSheet(
      onDismissRequest = { expanded = false },
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
      shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(bottom = Space.l)
      ) {
        SheetHeader(track)
        Hairline(Modifier.padding(vertical = Space.s))
        ListRow(
          title = stringResource(R.string.queue_move_up),
          icon = Icons.Rounded.ArrowUpward,
          chevron = false,
          enabled = canMoveUp,
          onClick = { close(onMoveUp) },
          modifier = Modifier.testTag("queue_menu_up")
        )
        ListRow(
          title = stringResource(R.string.queue_move_down),
          icon = Icons.Rounded.ArrowDownward,
          chevron = false,
          enabled = canMoveDown,
          onClick = { close(onMoveDown) },
          modifier = Modifier.testTag("queue_menu_down")
        )
        ListRow(
          title = stringResource(R.string.queue_remove),
          icon = Icons.Rounded.Delete,
          chevron = false,
          onClick = { close(onRemove) },
          modifier = Modifier.testTag("queue_menu_remove")
        )
      }
    }
  }
}

/** The track the sheet acts on, so the user sees which song the actions apply to. */
@Composable
private fun SheetHeader(track: Track) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page, vertical = Space.s),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TrackArtwork(track = track, shape = Shapes.xs, iconSize = Size.iconSmall, modifier = Modifier.size(Size.rowArtwork))
    Spacer(Modifier.width(Space.m))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        track.title,
        style = AppText.Headline,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        track.artist,
        style = AppText.Subhead,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun NamePlaylistDialog(
  title: String,
  confirmLabel: String,
  initialName: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  // Saved, so a rotation does not wipe what was typed; keyed, so renaming another playlist starts fresh.
  var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = AppText.Title3) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        placeholder = { Text(stringResource(R.string.player_menu_playlist_name), style = AppText.Body) },
        textStyle = AppText.Body,
        singleLine = true,
        shape = Shapes.s,
        modifier = Modifier.testTag("playlist_name_input")
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_menu_cancel)) } }
  )
}
