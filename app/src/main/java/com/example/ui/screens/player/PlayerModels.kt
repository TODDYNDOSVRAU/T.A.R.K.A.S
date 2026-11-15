package com.example.ui.screens.player

import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.data.lyrics.LyricsState
import com.example.data.model.Curiosity
import com.example.data.model.Playlist
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.example.data.playback.CoverNeighbors
import com.example.data.playback.PlaybackProblem
import com.example.data.playback.SleepTimer
import com.example.ui.viewmodel.TrackDetailsState

/**
 * What the full player shows: everything MusicScreen reads, nothing it can call. The position and the
 * duration are lambdas so that only the blocks that draw them recompose as they tick.
 */
@Immutable
data class PlayerState(
  val currentTrack: Track? = null,
  val isPlaying: Boolean = false,
  /** Drives the cover's size: true while a track is loading too, so a track change does not step it back. */
  val coverPlaying: Boolean = isPlaying,
  val currentPositionMs: () -> Long = { 0L },
  val durationMs: () -> Long = { 0L },
  val activeCuriosity: Curiosity? = null,
  /** The playing track's 100 amplitudes when the waveform bar is on and they are known; null keeps the thin bar. */
  val waveform: ByteArray? = null,
  val isShuffle: Boolean = false,
  val repeatMode: RepeatMode = RepeatMode.OFF,
  val favoriteIds: List<String> = emptyList(),
  val playlists: List<Playlist> = emptyList(),
  val sleepTimer: SleepTimer? = null,
  val trackDetails: TrackDetailsState = TrackDetailsState(),
  val lyrics: LyricsState = LyricsState(),
  /** The tracks beside the cover: what Previous and Next go to. */
  val coverNeighbors: CoverNeighbors.Result = CoverNeighbors.Result(),
  /** Why the current track does not play; null when nothing is wrong. Shown on the screen until it plays. */
  val problem: PlaybackProblem? = null
) {
  // A ByteArray is compared by content, not by reference, so the data class's equals stays meaningful.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PlayerState) return false
    return currentTrack == other.currentTrack && isPlaying == other.isPlaying &&
      coverPlaying == other.coverPlaying && currentPositionMs == other.currentPositionMs &&
      durationMs == other.durationMs && activeCuriosity == other.activeCuriosity &&
      waveform.contentEquals(other.waveform) && isShuffle == other.isShuffle &&
      repeatMode == other.repeatMode && favoriteIds == other.favoriteIds && playlists == other.playlists &&
      sleepTimer == other.sleepTimer && trackDetails == other.trackDetails && lyrics == other.lyrics &&
      coverNeighbors == other.coverNeighbors && problem == other.problem
  }

  override fun hashCode(): Int {
    var h = currentTrack.hashCode()
    h = 31 * h + isPlaying.hashCode()
    h = 31 * h + coverPlaying.hashCode()
    h = 31 * h + currentPositionMs.hashCode()
    h = 31 * h + durationMs.hashCode()
    h = 31 * h + activeCuriosity.hashCode()
    h = 31 * h + waveform.contentHashCode()
    h = 31 * h + isShuffle.hashCode()
    h = 31 * h + repeatMode.hashCode()
    h = 31 * h + favoriteIds.hashCode()
    h = 31 * h + playlists.hashCode()
    h = 31 * h + sleepTimer.hashCode()
    h = 31 * h + trackDetails.hashCode()
    h = 31 * h + lyrics.hashCode()
    h = 31 * h + coverNeighbors.hashCode()
    h = 31 * h + problem.hashCode()
    return h
  }
}

/** What the full player can ask for. Every action has a harmless default, so a preview needs none. */
@Immutable
data class PlayerActions(
  val onTogglePlayPause: () -> Unit = {},
  val onNextTrack: () -> Unit = {},
  val onPreviousTrack: () -> Unit = {},
  /** Previous from the cover carousel: the cover of that track was swiped in, so it always moves. */
  val onPreviousFromCover: () -> Unit = onPreviousTrack,
  val onSeekTo: (Long) -> Unit = {},
  val onToggleShuffle: () -> Unit = {},
  val onCycleRepeat: () -> Unit = {},
  val onToggleFavorite: (Track) -> Unit = {},
  val onPlayNext: (Track) -> Unit = {},
  val onAddToQueue: (Track) -> Unit = {},
  val onAddToPlaylist: (String, Track) -> Unit = { _, _ -> },
  val onCreatePlaylist: (String, Track) -> Unit = { _, _ -> },
  val onOpenQueue: () -> Unit = {},
  val onClose: () -> Unit = {},
  val onSetSleepTimer: (Int) -> Unit = {},
  val onSleepAtEndOfTrack: () -> Unit = {},
  val onCancelSleepTimer: () -> Unit = {},
  val onLoadTrackDetails: (Track) -> Unit = {},
  val onLoadLyrics: (Track) -> Unit = {},
  /** Tries again the track that failed. */
  val onRetry: () -> Unit = {}
)
