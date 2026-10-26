package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Playlist
import com.example.data.model.Track

/** The "..." menu on a track row: favorite, play next, queue, add to playlist. */
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
  icon: ImageVector = Icons.Default.MoreVert,
  /** Shown only in the player: the sleep timer entry, with what it is set to (null = off). */
  sleepTimerLabel: String? = null,
  onSleepTimer: (() -> Unit)? = null
) {
  var expanded by remember { mutableStateOf(false) }
  var showPlaylists by remember { mutableStateOf(false) }
  var showNewPlaylist by remember { mutableStateOf(false) }

  Column(modifier = modifier) {
    IconButton(
      onClick = { expanded = true },
      modifier = Modifier
        .size(48.dp)
        .testTag("track_menu_${track.id}")
    ) {
      Icon(
        imageVector = icon,
        contentDescription = "Mais opções",
        tint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false; showPlaylists = false }) {
      if (!showPlaylists) {
        DropdownMenuItem(
          text = { Text(if (isFavorite) "Remover dos favoritos" else "Favoritar") },
          leadingIcon = {
            Icon(
              imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
          },
          onClick = { onToggleFavorite(); expanded = false }
        )
        DropdownMenuItem(
          text = { Text("Tocar em seguida") },
          leadingIcon = { Icon(Icons.Default.SkipNext, contentDescription = null) },
          onClick = { onPlayNext(); expanded = false }
        )
        DropdownMenuItem(
          text = { Text("Adicionar à fila") },
          leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
          onClick = { onAddToQueue(); expanded = false }
        )
        DropdownMenuItem(
          text = { Text("Adicionar à playlist…") },
          leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
          onClick = { showPlaylists = true }
        )
        if (onSleepTimer != null) {
          DropdownMenuItem(
            text = { Text(sleepTimerLabel?.let { "Timer de dormir: $it" } ?: "Timer de dormir") },
            leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null) },
            onClick = { onSleepTimer(); expanded = false },
            modifier = Modifier.testTag("menu_sleep_timer")
          )
        }
      } else {
        DropdownMenuItem(
          text = { Text("Nova playlist…") },
          leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
          onClick = { showNewPlaylist = true; expanded = false; showPlaylists = false }
        )
        playlists.forEach { playlist ->
          DropdownMenuItem(
            text = { Text(playlist.name) },
            onClick = { onAddToPlaylist(playlist.id); expanded = false; showPlaylists = false }
          )
        }
      }
    }
  }

  if (showNewPlaylist) {
    NamePlaylistDialog(
      title = "Nova playlist",
      confirmLabel = "Criar",
      initialName = "",
      onConfirm = { name -> onCreatePlaylist(name); showNewPlaylist = false },
      onDismiss = { showNewPlaylist = false }
    )
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
  var name by remember { mutableStateOf(initialName) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        placeholder = { Text("Nome da playlist") },
        singleLine = true,
        modifier = Modifier.testTag("playlist_name_input")
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
  )
}
