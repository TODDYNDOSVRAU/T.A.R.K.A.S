package com.example.data.audio

import android.media.AudioManager

/**
 * Decides what the player should do when Android changes the audio focus.
 * Kept free of player and Android state so it can be unit-tested on the JVM.
 */
class AudioFocusPolicy {

  enum class Action {
    /** Another app took the focus for good: pause and give the focus up. */
    PAUSE_AND_ABANDON,

    /** Short interruption (a call, an alarm): pause, keep the focus and resume later. */
    PAUSE,

    /** Another app only needs a moment: lower the volume and keep playing. */
    DUCK,

    /** The short interruption is over and we had paused for it: play again at full volume. */
    RESUME,

    /** The volume was lowered: bring it back. */
    UNDUCK,

    NONE
  }

  private var resumeOnGain = false

  fun onChange(change: Int, isPlaying: Boolean): Action = when (change) {
    AudioManager.AUDIOFOCUS_LOSS -> {
      resumeOnGain = false
      Action.PAUSE_AND_ABANDON
    }

    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
      resumeOnGain = isPlaying
      if (isPlaying) Action.PAUSE else Action.NONE
    }

    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
      if (isPlaying) Action.DUCK else Action.NONE

    AudioManager.AUDIOFOCUS_GAIN -> {
      if (resumeOnGain) {
        resumeOnGain = false
        Action.RESUME
      } else {
        Action.UNDUCK
      }
    }

    else -> Action.NONE
  }

  /** Forget any pending resume, for example when the user pauses or a new track starts. */
  fun reset() {
    resumeOnGain = false
  }
}
