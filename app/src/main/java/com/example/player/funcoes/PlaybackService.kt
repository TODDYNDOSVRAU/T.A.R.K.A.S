package com.example.data.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.AppGraph
import com.example.MainActivity
import com.example.R
import com.example.data.model.Track
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The media session: the notification, the lock screen, headphone and Bluetooth buttons, cars and
 * watches. Media3 keeps the service in the foreground while something plays (or a curiosity is being
 * narrated), so Android does not freeze or kill the app with the screen off, and lets a headset button
 * start playback even with the app in the background.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

  private var session: MediaLibrarySession? = null

  /** The folder the browser opened last: a track picked from it plays inside that folder. */
  private var lastParent: String? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  override fun onCreate() {
    super.onCreate()
    AppGraph.init(this)
    setMediaNotificationProvider(TarkasNotificationProvider(this))
    val open = PendingIntent.getActivity(
      this, 0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    session = MediaLibrarySession.Builder(this, SessionPlayer(this, AppGraph.playback), LibraryCallback())
      .setSessionActivity(open)
      .build()
    // The favourite and repeat buttons follow the state they change (and the track they act on).
    scope.launch {
      combine(
        AppGraph.libraryRepository.favorites, AppGraph.playback.currentTrack, AppGraph.playback.repeatMode
      ) { favorites, track, repeat ->
        SessionButtons.layout(track != null, track != null && track.id in favorites, repeat)
      }.collect { session?.setCustomLayout(it.toButtons()) }
    }
    // The home-screen widget follows the track and the play state.
    scope.launch {
      combine(AppGraph.playback.currentTrack, AppGraph.playback.isPlaying) { track, playing ->
        com.example.data.widget.WidgetModel.of(track, playing)
      }.collect { com.example.data.widget.PlayerWidget.update(this@PlaybackService, it) }
    }
  }

  private fun List<SessionButtons.Spec>.toButtons(): ImmutableList<CommandButton> =
    ImmutableList.copyOf(map {
      CommandButton.Builder()
        .setSessionCommand(SessionCommand(it.action, Bundle.EMPTY))
        .setIconResId(it.iconRes)
        .setDisplayName(getString(it.labelRes))
        .build()
    })

  // RESULT_ERROR_* are valid in Media3 1.4.1; lint checks them against a newer constant list.
  @android.annotation.SuppressLint("WrongConstant")
  private inner class LibraryCallback : MediaLibrarySession.Callback {
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
      val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
        .add(SessionCommand(SessionButtons.FAVORITE, Bundle.EMPTY))
        .add(SessionCommand(SessionButtons.REPEAT, Bundle.EMPTY))
        .build()
      return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailableSessionCommands(commands)
        .setCustomLayout(
          with(AppGraph) {
            val track = playback.currentTrack.value
            SessionButtons.layout(track != null, track != null && track.id in libraryRepository.favorites.value, playback.repeatMode.value)
          }.toButtons()
        )
        .build()
    }

    override fun onGetLibraryRoot(
      session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> =
      Futures.immediateFuture(LibraryResult.ofItem(folderItem(LibraryTree.root()), params))

    override fun onGetChildren(
      session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
      page: Int, pageSize: Int, params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
      val nodes = currentChildren(parentId)
        ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
      lastParent = parentId
      val from = (page.toLong() * pageSize).coerceAtMost(nodes.size.toLong()).toInt()
      val to = (from.toLong() + pageSize).coerceAtMost(nodes.size.toLong()).toInt()
      return Futures.immediateFuture(
        LibraryResult.ofItemList(ImmutableList.copyOf(nodes.subList(from, to).map(::nodeItem)), params)
      )
    }

    override fun onGetItem(
      session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
      val item = if (LibraryTree.isFolder(mediaId)) {
        if (mediaId == LibraryTree.ROOT) folderItem(LibraryTree.root())
        else findNode(mediaId)?.let(::nodeItem)
      } else findTrack(mediaId)?.let { trackItem(it) }
      return Futures.immediateFuture(
        if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
      )
    }

    /** A track chosen in a car or on a watch starts playing here; the session list then mirrors the player. */
    override fun onSetMediaItems(
      mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
      mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
      val wanted = mediaItems.getOrNull(if (startIndex == C.INDEX_UNSET) 0 else startIndex)
      val library = AppGraph.musicRepository.tracks.value
      // A voice search ("play X", or just "play music" with an empty query) arrives as a query, not an id.
      val query = wanted?.requestMetadata?.searchQuery
      val track = if (query != null) LibraryTree.search(query, library) ?: library.firstOrNull().takeIf { query.isBlank() }
      else wanted?.mediaId?.let(::findTrack)
      if (track != null) {
        val context = LibraryTree.contextFor(
          if (query != null) null else lastParent, track, library, AppGraph.libraryRepository.favorites.value,
          AppGraph.libraryRepository.playlists.value, AppGraph.libraryRepository.savedTracks.value
        )
        AppGraph.playback.playFromList(track, context)
      }
      return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
    }

    // RESULT_ERROR_NOT_SUPPORTED is valid in Media3 1.4.1; lint checks it against a newer constant list.
    @android.annotation.SuppressLint("WrongConstant")
    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      customCommand: SessionCommand,
      args: Bundle
    ): ListenableFuture<SessionResult> {
      when (customCommand.customAction) {
        SessionButtons.FAVORITE -> AppGraph.playback.currentTrack.value?.let { AppGraph.libraryRepository.toggleFavorite(it) }
        SessionButtons.REPEAT -> AppGraph.playback.cycleRepeatMode()
        else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
      }
      return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

  override fun onTaskRemoved(rootIntent: Intent?) {
    // Swiped away while playing: the music goes on, with its notification. Paused: everything stops.
    val player = session?.player
    if (player == null || !player.playWhenReady) {
      AppGraph.playback.stopAll()
      stopSelf()
    }
  }

  private fun currentChildren(parentId: String): List<LibraryTree.Node>? = LibraryTree.children(
    parentId, AppGraph.musicRepository.tracks.value, AppGraph.libraryRepository.favorites.value,
    AppGraph.libraryRepository.playlists.value, AppGraph.libraryRepository.savedTracks.value
  )

  private fun findNode(id: String): LibraryTree.Node? =
    (currentChildren(LibraryTree.ROOT).orEmpty() + currentChildren(LibraryTree.PLAYLISTS).orEmpty()).firstOrNull { it.id == id }

  private fun findTrack(id: String): Track? =
    AppGraph.musicRepository.tracks.value.firstOrNull { it.id == id } ?: AppGraph.libraryRepository.savedTracks.value[id]

  private fun nodeItem(node: LibraryTree.Node): MediaItem = node.track?.let { trackItem(it) } ?: folderItem(node)

  private fun folderItem(node: LibraryTree.Node): MediaItem {
    val title = when (node.folder) {
      LibraryTree.Folder.FAVORITES -> getString(R.string.auto_favorites)
      LibraryTree.Folder.PLAYLISTS -> getString(R.string.auto_playlists)
      LibraryTree.Folder.LIBRARY -> getString(R.string.auto_library)
      LibraryTree.Folder.PLAYLIST -> node.name.orEmpty()
      null -> getString(R.string.app_name)
    }
    val metadata = MediaMetadata.Builder()
      .setTitle(title)
      .setIsBrowsable(true)
      .setIsPlayable(false)
      .setMediaType(if (node.folder == LibraryTree.Folder.PLAYLIST) MediaMetadata.MEDIA_TYPE_PLAYLIST else MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
      .build()
    return MediaItem.Builder().setMediaId(node.id).setMediaMetadata(metadata).build()
  }

  private fun trackItem(track: Track): MediaItem {
    val metadata = MediaMetadata.Builder()
      .setTitle(track.title)
      .setArtist(track.artist)
      .setAlbumTitle(track.album)
      .setIsBrowsable(false)
      .setIsPlayable(true)
      .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
      .apply { track.artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
      .build()
    return MediaItem.Builder().setMediaId(track.id).setMediaMetadata(metadata).build()
  }

  override fun onDestroy() {
    scope.cancel()
    session?.run {
      player.release()
      release()
    }
    session = null
    super.onDestroy()
  }
}

/** Media3's notification, with the app's icon and the curiosity next to the artist when there is one. */
@OptIn(UnstableApi::class)
private class TarkasNotificationProvider(context: Context) : DefaultMediaNotificationProvider(
  context,
  { NOTIFICATION_ID },
  CHANNEL_ID,
  R.string.playback_channel_name
) {
  private companion object {
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "tarkas_playback"
  }

  init {
    setSmallIcon(R.drawable.ic_notification)
  }

  override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
    val artist = metadata.artist
    val trivia = metadata.description?.toString()?.takeIf { it.isNotBlank() } ?: return artist
    val snippet = if (trivia.length > 75) trivia.take(75) + "…" else trivia
    return if (artist.isNullOrBlank()) snippet else "$artist • $snippet"
  }
}
