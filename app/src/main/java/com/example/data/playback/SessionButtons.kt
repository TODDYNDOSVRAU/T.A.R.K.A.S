package com.example.data.playback

import com.example.R
import com.example.data.model.RepeatMode

/**
 * The buttons the app adds to the media notification, beside Previous, Play and Next: favourite and
 * repeat. Each shows the state it is in (the icon is the heart filled or not; the repeat icon is dimmed
 * when off, and carries the "1" for one track), and a tap changes it. Pure: the service turns these
 * into session commands.
 */
object SessionButtons {
  const val FAVORITE = "com.example.tarkas.session.FAVORITE"
  const val REPEAT = "com.example.tarkas.session.REPEAT"

  data class Spec(val action: String, val iconRes: Int, val labelRes: Int)

  /** Nothing is offered while nothing is loaded: there is no track to favourite. */
  fun layout(hasTrack: Boolean, isFavorite: Boolean, repeat: RepeatMode): List<Spec> {
    if (!hasTrack) return emptyList()
    val favorite = if (isFavorite) {
      Spec(FAVORITE, R.drawable.ic_session_favorite, R.string.session_favorite_remove)
    } else {
      Spec(FAVORITE, R.drawable.ic_session_favorite_off, R.string.session_favorite_add)
    }
    val repeatSpec = when (repeat) {
      RepeatMode.OFF -> Spec(REPEAT, R.drawable.ic_session_repeat_off, R.string.session_repeat_off)
      RepeatMode.ALL -> Spec(REPEAT, R.drawable.ic_session_repeat_all, R.string.session_repeat_all)
      RepeatMode.ONE -> Spec(REPEAT, R.drawable.ic_session_repeat_one, R.string.session_repeat_one)
    }
    return listOf(favorite, repeatSpec)
  }
}
