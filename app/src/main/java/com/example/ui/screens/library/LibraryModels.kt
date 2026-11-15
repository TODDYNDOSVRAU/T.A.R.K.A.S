package com.example.ui.screens.library

import com.example.data.model.Playlist
import com.example.data.model.Track

/** What the library shows: everything LibraryScreen reads, nothing it can call. */
data class LibraryState(
  val playlists: List<Playlist> = emptyList(),
  val favoriteTracks: List<Track> = emptyList(),
  val savedTracks: Map<String, Track> = emptyMap(),
  val allTracks: List<Track> = emptyList(),
  val deviceTracks: List<Track> = emptyList(),
  val deviceScanning: Boolean = false,
  val hasMusicPermission: Boolean = false,
  val songsPerFolder: Map<String, Int> = emptyMap(),
  val folderRules: com.example.data.library.FolderRules = com.example.data.library.FolderRules.None,
  val queue: List<Track> = emptyList(),
  val currentTrack: Track? = null,
  val isPlaying: Boolean = false,
  val upcoming: List<Track> = emptyList(),
  val initialSongOrder: com.example.data.playback.SongOrder = com.example.data.playback.SongOrder.TITLE,
  /** Plays counted on this device per track id, for "Mais tocadas"; [onNeedPlayCounts] loads them. */
  val playCounts: Map<String, Int> = emptyMap(),
  val openQueue: Boolean = false
)

/** What the library can ask for: every callback LibraryScreen invokes. */
data class LibraryActions(
  val onScanDevice: () -> Unit = {},
  val onSetFolderIncluded: (String, Boolean) -> Unit = { _, _ -> },
  val onPlayFromList: (Track, List<Track>) -> Unit = { _, _ -> },
  val onShuffleList: (List<Track>) -> Unit = { _ -> },
  val onCreatePlaylist: (String) -> Unit = { _ -> },
  val onAddLocalTrack: (String, String, String, Long) -> Unit = { _, _, _, _ -> },
  val onRenamePlaylist: (String, String) -> Unit = { _, _ -> },
  val onDeletePlaylist: (String) -> Unit = { _ -> },
  val onRemoveFromPlaylist: (String, String) -> Unit = { _, _ -> },
  val onToggleFavorite: (Track) -> Unit = { _ -> },
  /** The track menu in the song and favourite rows. */
  val onPlayNext: (Track) -> Unit = {},
  val onAddToQueue: (Track) -> Unit = {},
  val onAddToPlaylist: (String, Track) -> Unit = { _, _ -> },
  val onCreatePlaylistWith: (String, Track) -> Unit = { _, _ -> },
  val onPlayQueueItem: (Int) -> Unit = { _ -> },
  val onRemoveQueueItem: (Int) -> Unit = { _ -> },
  val onClearQueue: () -> Unit = { },
  val onSaveQueueAsPlaylist: (String) -> Unit = {},
  val onSongOrderChange: (com.example.data.playback.SongOrder) -> Unit = {},
  val onNeedPlayCounts: () -> Unit = {},
  /** Plays, skips and last play per track, for the automatic lists. */
  val onLoadPlayStats: suspend () -> Map<String, com.example.data.playback.TrackStats> = { emptyMap() },
  val onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
  val onPlayUpcoming: (Track) -> Unit = {},
  val onQueueOpened: () -> Unit = {}
)
