package com.example.data.model

import android.content.Context
import com.example.R

/**
 * The words a track gets when its source has none (no title, no artist). They are text the user reads,
 * so they come from the string resources, in the language of the moment. Set once by AppGraph.
 * Tracks already saved keep the words they were saved with; nothing is rewritten on read.
 */
object Placeholders {
  @Volatile private var appContext: Context? = null

  fun init(context: Context) {
    appContext = context.applicationContext
  }

  private fun text(id: Int, vararg args: Any, fallback: String): String =
    appContext?.getString(id, *args) ?: fallback

  fun untitled(): String = text(R.string.track_untitled, fallback = "Untitled")

  fun unknownArtist(): String = text(R.string.track_unknown_artist, fallback = "Unknown artist")

  /** "Audius artist" / "Jamendo artist": a catalog track with no artist name. */
  fun catalogArtist(service: String): String = text(R.string.track_catalog_artist, service, fallback = "$service artist")
}
