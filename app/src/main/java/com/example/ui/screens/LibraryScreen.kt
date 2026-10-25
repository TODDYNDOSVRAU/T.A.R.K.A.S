package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Playlist
import com.example.data.model.Track
import com.example.ui.components.NamePlaylistDialog
import com.example.ui.theme.SpaceGrotesk

private enum class LibrarySection(val label: String) {
  PLAYLISTS("Playlists"),
  FAVORITES("Favoritos"),
  QUEUE("Fila")
}

@Composable
fun LibraryScreen(
  playlists: List<Playlist>,
  favoriteTracks: List<Track>,
  savedTracks: Map<String, Track>,
  allTracks: List<Track>,
  queue: List<Track>,
  currentTrack: Track?,
  isPlaying: Boolean,
  onPlayFromList: (Track, List<Track>) -> Unit,
  onShuffleList: (List<Track>) -> Unit,
  onCreatePlaylist: (String) -> Unit,
  onRenamePlaylist: (String, String) -> Unit,
  onDeletePlaylist: (String) -> Unit,
  onRemoveFromPlaylist: (String, String) -> Unit,
  onToggleFavorite: (Track) -> Unit,
  onPlayQueueItem: (Int) -> Unit,
  onRemoveQueueItem: (Int) -> Unit,
  onClearQueue: () -> Unit,
  modifier: Modifier = Modifier
) {
  var section by remember { mutableStateOf(LibrarySection.PLAYLISTS) }
  var openPlaylistId by remember { mutableStateOf<String?>(null) }
  var showNewPlaylist by remember { mutableStateOf(false) }
  var renamingPlaylist by remember { mutableStateOf<Playlist?>(null) }

  // Tracks of the current catalog win over the saved snapshot (fresher data).
  val byId = savedTracks + allTracks.associateBy { it.id }
  val openPlaylist = playlists.firstOrNull { it.id == openPlaylistId }

  Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    if (openPlaylist != null) {
      val tracks = openPlaylist.trackIds.mapNotNull { byId[it] }
      PlaylistDetail(
        playlist = openPlaylist,
        tracks = tracks,
        currentTrack = currentTrack,
        isPlaying = isPlaying,
        onBack = { openPlaylistId = null },
        onPlay = { track -> onPlayFromList(track, tracks) },
        onShuffle = { onShuffleList(tracks) },
        onRename = { renamingPlaylist = openPlaylist },
        onDelete = { onDeletePlaylist(openPlaylist.id); openPlaylistId = null },
        onRemoveTrack = { trackId -> onRemoveFromPlaylist(openPlaylist.id, trackId) }
      )
    } else {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        LibrarySection.values().forEach { s ->
          FilterChip(
            selected = section == s,
            onClick = { section = s },
            label = {
              Text(
                if (s == LibrarySection.QUEUE && queue.isNotEmpty()) "${s.label} (${queue.size})" else s.label,
                fontSize = 15.sp
              )
            },
            border = FilterChipDefaults.filterChipBorder(
              enabled = true,
              selected = section == s,
              borderColor = Color.Transparent,
              selectedBorderColor = Color.Transparent
            ),
            colors = FilterChipDefaults.filterChipColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant,
              labelColor = MaterialTheme.colorScheme.onSurface,
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.heightIn(min = 40.dp).testTag("library_chip_${s.name.lowercase()}")
          )
        }
      }

      when (section) {
        LibrarySection.PLAYLISTS -> PlaylistsList(
          playlists = playlists,
          byId = byId,
          onOpen = { openPlaylistId = it.id },
          onNew = { showNewPlaylist = true }
        )

        LibrarySection.FAVORITES -> TrackList(
          emptyText = "Nenhum favorito ainda. Toque no coração de uma faixa para guardá-la aqui.",
          tracks = favoriteTracks,
          onHeaderPlay = { onPlayFromList(favoriteTracks.first(), favoriteTracks) },
          onHeaderShuffle = { onShuffleList(favoriteTracks) },
          currentTrack = currentTrack,
          isPlaying = isPlaying,
          onPlay = { track -> onPlayFromList(track, favoriteTracks) },
          trailingIcon = Icons.Default.Favorite,
          trailingDescription = "Remover dos favoritos",
          onTrailing = onToggleFavorite
        )

        LibrarySection.QUEUE -> QueueList(
          queue = queue,
          onPlay = onPlayQueueItem,
          onRemove = onRemoveQueueItem,
          onClear = onClearQueue
        )
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
  renamingPlaylist?.let { pl ->
    NamePlaylistDialog(
      title = "Renomear playlist",
      confirmLabel = "Salvar",
      initialName = pl.name,
      onConfirm = { name -> onRenamePlaylist(pl.id, name); renamingPlaylist = null },
      onDismiss = { renamingPlaylist = null }
    )
  }
}

@Composable
private fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    ActionPill("Tocar", Icons.Default.PlayArrow, onPlay, Modifier.weight(1f).testTag("library_btn_play"))
    ActionPill("Aleatório", Icons.Default.Shuffle, onShuffle, Modifier.weight(1f).testTag("library_btn_shuffle"))
  }
}

@Composable
private fun ActionPill(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant)
      .clickable(onClick = onClick)
      .padding(vertical = 12.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
  }
}

@Composable
private fun PlaylistsList(
  playlists: List<Playlist>,
  byId: Map<String, Track>,
  onOpen: (Playlist) -> Unit,
  onNew: () -> Unit
) {
  var query by remember { mutableStateOf("") }
  val shown = playlists.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
  LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
    item {
      TextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Buscar nas playlists", fontSize = 17.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
          unfocusedIndicatorColor = Color.Transparent,
          focusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("library_search_input")
      )
    }
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onNew)
          .padding(vertical = 4.dp)
          .testTag("library_new_playlist"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier.size(80.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text("Nova playlist…", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
      }
      HorizontalDivider(modifier = Modifier.padding(top = 4.dp, start = 96.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
    if (playlists.isEmpty()) {
      item {
        Text(
          "Crie uma playlist e adicione faixas pelo menu ⋮ de qualquer música.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 15.sp,
          modifier = Modifier.padding(vertical = 16.dp)
        )
      }
    }
    itemsIndexed(shown) { _, playlist ->
      val firstColor = playlist.trackIds.firstNotNullOfOrNull { byId[it] }?.let { Color(it.colorHex) }
        ?: MaterialTheme.colorScheme.surfaceVariant
      Column {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(playlist) }
            .padding(vertical = 4.dp)
            .testTag("playlist_row_${playlist.id}"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(80.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Brush.verticalGradient(listOf(firstColor, firstColor.copy(alpha = 0.6f)))),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
          }
          Spacer(Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
            Text(if (playlist.trackIds.size == 1) "1 faixa" else "${playlist.trackIds.size} faixas", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp, start = 96.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
      }
    }
  }
}

@Composable
private fun PlaylistDetail(
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
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Row(
      modifier = Modifier
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onBack)
        .padding(end = 12.dp)
        .testTag("library_back"),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(Icons.Default.ChevronLeft, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
      Text("Biblioteca", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.weight(1f))
    IconButton(onClick = onRename) { Icon(Icons.Default.Edit, contentDescription = "Renomear", tint = MaterialTheme.colorScheme.primary) }
    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Excluir playlist", tint = MaterialTheme.colorScheme.primary) }
  }
  Text(
    playlist.name,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    letterSpacing = (-0.5).sp,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    color = MaterialTheme.colorScheme.onBackground,
    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
  )
  TrackList(
    emptyText = "Playlist vazia. Use o menu ⋮ de uma faixa para adicioná-la.",
    tracks = tracks,
    onHeaderPlay = { onPlay(tracks.first()) },
    onHeaderShuffle = onShuffle,
    currentTrack = currentTrack,
    isPlaying = isPlaying,
    onPlay = onPlay,
    trailingIcon = Icons.Default.Close,
    trailingDescription = "Remover da playlist",
    onTrailing = { track -> onRemoveTrack(track.id) }
  )
}

@Composable
private fun TrackList(
  emptyText: String,
  tracks: List<Track>,
  onHeaderPlay: () -> Unit,
  onHeaderShuffle: () -> Unit,
  currentTrack: Track?,
  isPlaying: Boolean,
  onPlay: (Track) -> Unit,
  trailingIcon: androidx.compose.ui.graphics.vector.ImageVector,
  trailingDescription: String,
  onTrailing: (Track) -> Unit
) {
  if (tracks.isEmpty()) {
    Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
    return
  }
  LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
    item { PlayShuffleRow(onPlay = onHeaderPlay, onShuffle = onHeaderShuffle) }
    itemsIndexed(tracks) { _, track ->
      val selected = currentTrack?.id == track.id
      TrackRow(
        track = track,
        highlighted = selected,
        subtitle = "${track.artist} • ${track.album}",
        onClick = { onPlay(track) },
        trailing = {
          IconButton(onClick = { onTrailing(track) }) {
            Icon(trailingIcon, contentDescription = trailingDescription, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
          }
        }
      )
    }
  }
}

@Composable
private fun QueueList(
  queue: List<Track>,
  onPlay: (Int) -> Unit,
  onRemove: (Int) -> Unit,
  onClear: () -> Unit
) {
  if (queue.isEmpty()) {
    Text(
      "A fila está vazia. Use \"Tocar em seguida\" ou \"Adicionar à fila\" no menu ⋮ de uma faixa.",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 13.sp,
      modifier = Modifier.padding(vertical = 16.dp)
    )
    return
  }
  LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
    item {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          "A seguir",
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onClear, modifier = Modifier.testTag("library_clear_queue")) { Text("Limpar") }
      }
    }
    itemsIndexed(queue) { index, track ->
      TrackRow(
        track = track,
        highlighted = false,
        subtitle = track.artist,
        onClick = { onPlay(index) },
        trailing = {
          IconButton(onClick = { onRemove(index) }) {
            Icon(Icons.Default.Close, contentDescription = "Remover da fila", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      )
    }
  }
}

@Composable
private fun TrackRow(
  track: Track,
  highlighted: Boolean,
  subtitle: String,
  onClick: () -> Unit,
  trailing: @Composable () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(Brush.verticalGradient(listOf(Color(track.colorHex), Color(track.colorHex).copy(alpha = 0.6f)))),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        track.title,
        fontSize = 17.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
      )
      Text(subtitle, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    trailing()
  }
  HorizontalDivider(modifier = Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}
