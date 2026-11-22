package com.example.data.assistant

import com.example.data.model.Track

/** The library as the assistant sees it: every known track and the playlists with their tracks resolved. */
data class LibrarySnapshot(val tracks: List<Track>, val playlists: List<LibraryPlaylist>)

/**
 * What the assistant can change in the app. The engine only calls these after the user confirmed the
 * action. Each returns false when the app could not run it, and the reply then says so.
 * Implementations run on the main thread.
 */
interface AssistantActions {
  fun library(): LibrarySnapshot
  fun playTracks(tracks: List<Track>): Boolean
  fun queueTracks(tracks: List<Track>, asNext: Boolean): Boolean
  fun clearQueue(): Boolean
  fun sleepIn(minutes: Int): Boolean
  fun sleepAtEndOfTrack(): Boolean
  fun cancelSleep(): Boolean
  fun setSpatial(on: Boolean): Boolean
  fun setVolumePercent(percent: Int): Boolean
  fun toggleAbLoop(): Boolean = false
}

/** Used when the engine runs without the app (tests, previews): nothing is found and nothing changes. */
object NoAssistantActions : AssistantActions {
  override fun library() = LibrarySnapshot(emptyList(), emptyList())
  override fun playTracks(tracks: List<Track>) = false
  override fun queueTracks(tracks: List<Track>, asNext: Boolean) = false
  override fun clearQueue() = false
  override fun sleepIn(minutes: Int) = false
  override fun sleepAtEndOfTrack() = false
  override fun cancelSleep() = false
  override fun setSpatial(on: Boolean) = false
  override fun setVolumePercent(percent: Int) = false
}
