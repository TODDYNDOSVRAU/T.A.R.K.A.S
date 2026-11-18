package com.example.data.library

import java.security.MessageDigest

/** Identifiers of tracks the app creates itself, so the same thing always gets the same id. */
object TrackIds {
  const val PICKED_PREFIX = "local_"

  /**
   * The id of a file the user picked: derived from its address, so picking the same file again gives
   * the same id and favourites and playlists keep pointing at it. (Before 1.18 it was the time of the
   * pick, which changed every time.)
   */
  fun picked(uri: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
    return PICKED_PREFIX + digest.take(8).joinToString("") { "%02x".format(it) }
  }

  /** A playlist id that cannot collide with another one, made on this device or elsewhere. */
  fun newPlaylist(): String = "pl_" + java.util.UUID.randomUUID().toString()
}
