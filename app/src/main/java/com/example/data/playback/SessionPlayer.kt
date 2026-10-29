package com.example.data.playback

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.example.data.model.RepeatMode
import com.example.data.model.Track
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the media session (notification, lock screen, headphones, car, watch) sees and controls: a
 * player that mirrors [PlaybackController], whatever plays underneath (the app's player or a curiosity
 * narration). Commands go back to the controller.
 *
 * The playlist it shows is [previous, current, next], with placeholders around the current track,
 * so Previous and Next are offered whenever they can do something.
 */
@OptIn(UnstableApi::class)
class SessionPlayer(
  private val context: Context,
  private val controller: PlaybackController
) : SimpleBasePlayer(Looper.getMainLooper()) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  /**
   * Covers embedded in local files, by track id, loaded once off the main thread and scaled down: a
   * full-size cover would ride every update to every controller. Only the last few are kept.
   */
  private val embeddedArt = object : LinkedHashMap<String, ByteArray?>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray?>?) = size > ART_CACHE_SIZE
  }

  init {
    scope.launch {
      merge(
        controller.currentTrack, controller.wantsToPlay, controller.durationMs,
        controller.activeCuriosity, controller.queue, controller.shuffle, controller.repeatMode
      ).collect { invalidateState() }
    }
    // A jump in position (seek, new track) is not something the session can work out on its own.
    scope.launch {
      combine(controller.currentTrack, controller.positionMs) { t, p -> t?.id to p }
        .collect { (_, pos) -> if (kotlin.math.abs(pos - lastPositionMs) > POSITION_JUMP_MS) invalidateState(); lastPositionMs = pos }
    }
  }

  private var lastPositionMs = 0L

  override fun getState(): State {
    val track = controller.currentTrack.value
    val commands = Player.Commands.Builder()
      .addAll(
        COMMAND_PLAY_PAUSE, COMMAND_STOP, COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, COMMAND_SEEK_TO_MEDIA_ITEM,
        COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_GET_METADATA,
        COMMAND_SET_SHUFFLE_MODE, COMMAND_SET_REPEAT_MODE, COMMAND_PREPARE
      )
      .build()
    val builder = State.Builder()
      .setAvailableCommands(commands)
      .setShuffleModeEnabled(controller.shuffle.value)
      .setRepeatMode(
        when (controller.repeatMode.value) {
          RepeatMode.OFF -> REPEAT_MODE_OFF
          RepeatMode.ALL -> REPEAT_MODE_ALL
          RepeatMode.ONE -> REPEAT_MODE_ONE
        }
      )
      .setMaxSeekToPreviousPositionMs(RESTART_THRESHOLD_MS)
    if (track == null) {
      return builder.setPlaybackState(STATE_IDLE).setPlaylist(emptyList()).build()
    }

    // A narration, and the moment between two tracks, count as playing: the notification stays and the
    // service stays in the foreground, so the next track is never refused its audio focus.
    val playing = controller.wantsToPlay.value
    val playlist = mutableListOf<MediaItemData>()
    if (controller.hasPrevious) playlist += placeholder(PREVIOUS_UID)
    val currentIndex = playlist.size
    playlist += currentItem(track)
    if (controller.hasNext) playlist += placeholder(NEXT_UID)

    return builder
      .setPlaylist(playlist)
      .setCurrentMediaItemIndex(currentIndex)
      .setContentPositionMs { controller.positionMs.value }
      .setPlaybackState(STATE_READY)
      .setPlayWhenReady(playing, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
      .build()
  }

  private fun currentItem(track: Track): MediaItemData {
    val curiosity = controller.activeCuriosity.value?.shortTrivia
    val metadata = MediaMetadata.Builder()
      .setTitle(track.title)
      .setArtist(track.artist)
      .setAlbumTitle(track.album)
      // Shown next to the artist in the notification (see TarkasNotificationProvider).
      .setDescription(curiosity)
      .apply {
        val art = track.artworkUrl
        when {
          art != null -> setArtworkUri(Uri.parse(art))
          track.isLocalFile -> embeddedArtFor(track)?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
        }
      }
      .build()
    val duration = controller.durationMs.value
    return MediaItemData.Builder(track.id)
      .setMediaItem(MediaItem.Builder().setMediaId(track.id).setMediaMetadata(metadata).build())
      .setMediaMetadata(metadata)
      .setDurationUs(if (duration > 0) duration * 1000 else C.TIME_UNSET)
      .setIsSeekable(true)
      .build()
  }

  private fun placeholder(uid: String): MediaItemData =
    MediaItemData.Builder(uid).setMediaItem(MediaItem.Builder().setMediaId(uid).build()).build()

  private fun embeddedArtFor(track: Track): ByteArray? {
    if (embeddedArt.containsKey(track.id)) return embeddedArt[track.id]
    embeddedArt[track.id] = null
    val path = track.filePath ?: return null
    scope.launch {
      val bytes = withContext(Dispatchers.IO) {
        runCatching {
          val retriever = MediaMetadataRetriever()
          try {
            retriever.setDataSource(context, Uri.parse(path))
            retriever.embeddedPicture?.let(::scaledCover)
          } finally {
            retriever.release()
          }
        }.getOrNull()
      }
      if (bytes != null) {
        embeddedArt[track.id] = bytes
        invalidateState()
      }
    }
    return null
  }

  override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
    if (playWhenReady) controller.play() else controller.pause()
    return Futures.immediateVoidFuture()
  }

  override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

  override fun handleStop(): ListenableFuture<*> {
    controller.pause()
    return Futures.immediateVoidFuture()
  }

  override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
    val currentIndex = if (controller.hasPrevious) 1 else 0
    when {
      mediaItemIndex > currentIndex -> controller.playNextTrack()
      mediaItemIndex in 0 until currentIndex -> controller.playPreviousTrack(force = true)
      else -> controller.seekTo(if (positionMs == C.TIME_UNSET) 0L else positionMs)
    }
    return Futures.immediateVoidFuture()
  }

  override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
    controller.setShuffle(shuffleModeEnabled)
    return Futures.immediateVoidFuture()
  }

  override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
    controller.setRepeatMode(
      when (repeatMode) {
        REPEAT_MODE_ALL -> RepeatMode.ALL
        REPEAT_MODE_ONE -> RepeatMode.ONE
        else -> RepeatMode.OFF
      }
    )
    return Futures.immediateVoidFuture()
  }

  override fun handleRelease(): ListenableFuture<*> {
    scope.cancel()
    return Futures.immediateVoidFuture()
  }

  private companion object {
    const val PREVIOUS_UID = "tarkas:previous"
    const val NEXT_UID = "tarkas:next"
    const val RESTART_THRESHOLD_MS = 3000L
    const val POSITION_JUMP_MS = 2500L
    const val ART_CACHE_SIZE = 6
    const val ART_MAX_PX = 512

    /** The cover re-encoded as a JPEG no larger than [ART_MAX_PX] on its long side. */
    fun scaledCover(bytes: ByteArray): ByteArray? {
      val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
      android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      val longSide = maxOf(bounds.outWidth, bounds.outHeight)
      if (longSide <= 0) return null
      var sample = 1
      while (longSide / (sample * 2) >= ART_MAX_PX) sample *= 2
      val bitmap = android.graphics.BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
      ) ?: return null
      val out = java.io.ByteArrayOutputStream()
      bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
      bitmap.recycle()
      return out.toByteArray()
    }
  }
}
