package com.example.data.widget

import com.example.data.model.Track

/** What the home-screen widget shows: pure, so it is tested without a device. */
data class WidgetModel(val title: String?, val artist: String?, val playing: Boolean) {
  /** True when no track is loaded: the widget shows a hint. */
  val empty: Boolean get() = title == null

  companion object {
    fun of(track: Track?, playing: Boolean) =
      if (track == null) WidgetModel(null, null, false)
      else WidgetModel(track.title.ifBlank { null }, track.artist.ifBlank { null }, playing)
  }
}
