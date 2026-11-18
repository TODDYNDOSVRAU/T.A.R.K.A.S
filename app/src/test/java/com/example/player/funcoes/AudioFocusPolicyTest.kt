package com.example

import android.media.AudioManager
import com.example.data.audio.AudioFocusPolicy
import com.example.data.audio.AudioFocusPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFocusPolicyTest {

  private val policy = AudioFocusPolicy()

  @Test
  fun `losing the focus for good pauses and gives it up, even if already paused`() {
    assertEquals(Action.PAUSE_AND_ABANDON, policy.onChange(AudioManager.AUDIOFOCUS_LOSS, isPlaying = true))
    assertEquals(Action.PAUSE_AND_ABANDON, policy.onChange(AudioManager.AUDIOFOCUS_LOSS, isPlaying = false))
  }

  @Test
  fun `a short interruption pauses and resumes when the focus comes back`() {
    assertEquals(Action.PAUSE, policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isPlaying = true))
    assertEquals(Action.RESUME, policy.onChange(AudioManager.AUDIOFOCUS_GAIN, isPlaying = false))
  }

  @Test
  fun `resuming happens only once per interruption`() {
    policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isPlaying = true)
    assertEquals(Action.RESUME, policy.onChange(AudioManager.AUDIOFOCUS_GAIN, isPlaying = false))
    assertEquals(Action.UNDUCK, policy.onChange(AudioManager.AUDIOFOCUS_GAIN, isPlaying = true))
  }

  @Test
  fun `a short interruption while already paused does not start playback later`() {
    assertEquals(Action.NONE, policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isPlaying = false))
    assertEquals(Action.UNDUCK, policy.onChange(AudioManager.AUDIOFOCUS_GAIN, isPlaying = false))
  }

  @Test
  fun `ducking lowers the volume and the gain brings it back without restarting`() {
    assertEquals(Action.DUCK, policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, isPlaying = true))
    assertEquals(Action.UNDUCK, policy.onChange(AudioManager.AUDIOFOCUS_GAIN, isPlaying = true))
  }

  @Test
  fun `ducking while paused does nothing`() {
    assertEquals(Action.NONE, policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, isPlaying = false))
  }

  @Test
  fun `a permanent loss cancels a pending resume`() {
    policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isPlaying = true)
    policy.onChange(AudioManager.AUDIOFOCUS_LOSS, isPlaying = false)
    assertEquals(Action.UNDUCK, policy.onChange(AudioManager.AUDIOFOCUS_GAIN, isPlaying = false))
  }

  @Test
  fun `reset forgets a pending resume`() {
    policy.onChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, isPlaying = true)
    policy.reset()
    assertEquals(Action.UNDUCK, policy.onChange(AudioManager.AUDIOFOCUS_GAIN, isPlaying = false))
  }

  @Test
  fun `unknown focus values are ignored`() {
    assertEquals(Action.NONE, policy.onChange(12345, isPlaying = true))
  }
}
