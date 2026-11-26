package com.example.windows.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * The worker used to publish the position on every 512-frame chunk (about 94 times a second), recomposing every
 * screen that reads the player state. It now publishes when the position moved [DesktopPlayer.PositionStepMs].
 * Plays about 2 s of silence through the real output, so it is skipped on a machine with no sound line.
 */
class PositionPublishTest {
  @get:Rule val tmp = TemporaryFolder()

  private val fmt = AudioFormat(44_100f, 16, 2, true, false)

  private fun silentWav(seconds: Int): File {
    val frames = 44_100 * seconds
    val bytes = ByteArray(frames * fmt.frameSize)
    val f = tmp.newFile("silence.wav")
    AudioSystem.write(AudioInputStream(ByteArrayInputStream(bytes), fmt, frames.toLong()), AudioFileFormat.Type.WAVE, f)
    return f
  }

  private fun waitFor(timeoutMs: Long, cond: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(10) }
    return cond()
  }

  @Test fun thePositionIsPublishedAtMostAboutTenTimesASecond() {
    assumeTrue("no sound line on this machine", runCatching { AudioSystem.getSourceDataLine(fmt).close() }.isSuccess)
    val seconds = 2
    val player = DesktopPlayer()
    val scope = CoroutineScope(Dispatchers.Unconfined)
    var emissions = 0
    val seen = ArrayList<Long>()
    scope.launch { player.state.collect { emissions++; synchronized(seen) { seen.add(it.positionMs) } } }
    try {
      player.play(silentWav(seconds))
      assertTrue("the track starts", waitFor(5_000) { player.state.value.playing })
      assertTrue("the track ends", waitFor(8_000) { !player.state.value.playing && player.state.value.positionMs >= seconds * 1000L - 200 })
      // Chunks of 512 frames at 44.1 kHz = 86 a second; the old code emitted once per chunk (about 172 for 2 s).
      val perSecond = emissions / seconds.toDouble()
      assertTrue("$emissions emissions for $seconds s of audio ($perSecond per second)", perSecond <= 25.0)
      // It still moves: the bar and the clock are fed.
      assertTrue("the position advances ($emissions emissions)", emissions >= 8)
      val steps = synchronized(seen) { seen.toList() }.zipWithNext { a, b -> b - a }.filter { it > 0 }
      assertTrue("steps between publishes stay near the 100 ms step: $steps", steps.all { it <= 400 })
      // The end is always published: the full duration, not a position up to 100 ms short of it.
      assertEquals(player.state.value.durationMs, player.state.value.positionMs)
      assertFalse(player.state.value.playing)
    } finally {
      player.stop()
      scope.cancel()
    }
  }
}
