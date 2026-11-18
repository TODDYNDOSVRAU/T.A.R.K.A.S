package com.example.windows.audio

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * What the player controller needs from an audio engine. [DesktopPlayer] is the real one; tests use a fake.
 * Positions are milliseconds.
 */
interface AudioOut {
  val state: StateFlow<PlayerState>

  /** Output level 0..1, applied after the EQ. */
  var volume: Float

  /**
   * Called from a helper thread when a track ends on its own (`crossfaded` false: the engine is idle and the
   * controller must start the next one) or when the engine already moved on to the armed next file by
   * crossfading (`crossfaded` true: the next file is playing).
   */
  var onEnded: ((crossfaded: Boolean) -> Unit)?

  fun play(file: File, startMs: Long = 0, paused: Boolean = false)
  fun togglePause()
  fun pause()
  fun resume()
  fun seek(ms: Long)
  fun stop()

  /** Arms [file] as the track to crossfade into over [fadeMs] near the end of the current one; null disarms. */
  fun armNext(file: File?, fadeMs: Int)
}
