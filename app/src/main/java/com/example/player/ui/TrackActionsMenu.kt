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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.SkipNext
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
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
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
  onAbout: (() -> Unit)? = null,
  onLyrics: (() -> Unit)? = null,
  /** Shown for free remote tracks (Jamendo / Audius): downloads to private offline storage. */
  onDownloadOffline: (() -> Unit)? = null
) {
  // Saved, so a rotation keeps the sheet (and the playlist step) where the user left it.
  var expanded by rememberSaveable { mutableStateOf(false) }
  var showPlaylists by rememberSaveable { mutableStateOf(false) }
  var showNewPlaylist by rememberSaveable { mutableStateOf(false) }
  val context = LocalContext.current
  val chooserTitle = stringResource(R.string.share_chooser_title)
  val noShareApp = stringResource(R.string.share_no_app)
  val share = {
    TrackShare.intent(track)?.let { send ->
      try {
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, noShareApp, Toast.LENGTH_SHORT).show()
      }
    }
    Unit
  }

  IconButton(
    onClick = { showPlaylists = false; expanded = true },
    modifier = modifier
      .size(Grid.u48)
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
      sheetMaxWidth = Grid.u384 * 2,
      onDismissRequest = { expanded = false; showPlaylists = false },
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
      shape = RoundedCornerShape(topStart = Grid.u24, topEnd = Grid.u24),
      dragHandle = { SheetHandle() }
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(bottom = Grid.u12)
      ) {
        SheetHeader(track)
        Hairline(Modifier.padding(vertical = Grid.u6))
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
          if (TrackShare.canShare(track)) {
            ListRow(
              title = stringResource(R.string.player_menu_share),
              icon = Icons.Rounded.Share,
              chevron = false,
              onClick = { close(share) },
              modifier = Modifier.testTag("menu_share")
            )
          }
          if (onDownloadOffline != null && track.onlineSource != null && !track.isLocalFile) {
            ListRow(
              title = stringResource(R.string.player_menu_download_offline),
              icon = Icons.Rounded.Download,
              chevron = false,
              onClick = { close(onDownloadOffline) },
              modifier = Modifier.testTag("menu_download_offline")
            )
          }
          if (onLyrics != null) {
            ListRow(
              title = stringResource(R.string.player_menu_lyrics),
              icon = Icons.Rounded.Lyrics,
              chevron = false,
              onClick = { close(onLyrics) },
              modifier = Modifier.testTag("menu_lyrics")
            )
          }
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
          GroupHeader(stringResource(R.string.player_menu_add_playlist), Modifier.padding(top = Grid.u6))
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
      .size(Grid.u48)
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
      sheetMaxWidth = Grid.u384 * 2,
      onDismissRequest = { expanded = false },
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
      shape = RoundedCornerShape(topStart = Grid.u24, topEnd = Grid.u24),
      dragHandle = { SheetHandle() }
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(bottom = Grid.u12)
      ) {
        SheetHeader(track)
        Hairline(Modifier.padding(vertical = Grid.u6))
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
      .padding(horizontal = Grid.margin, vertical = Grid.u6),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TrackArtwork(track = track, shape = Grid.r6, iconSize = Grid.icon, modifier = Modifier.size(Grid.u48))
    Spacer(Modifier.width(Grid.u12))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        track.title,
        style = GridText.BodyStrong,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        track.artist,
        style = GridText.Small,
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
  TarkasAlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = GridText.BodyStrong) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        placeholder = { Text(stringResource(R.string.player_menu_playlist_name), style = GridText.Body) },
        textStyle = GridText.Body,
        singleLine = true,
        shape = Grid.r12,
        modifier = Modifier.testTag("playlist_name_input")
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.player_menu_cancel)) } }
  )
}
