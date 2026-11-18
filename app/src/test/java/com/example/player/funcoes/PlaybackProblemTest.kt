package com.example

import com.example.data.playback.PlaybackFailure
import com.example.data.playback.PlaybackProblem
import com.example.data.playback.SleepTimerPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProblemTest {

  @Test fun aBadFileSkipsWhenThereIsANextTrack() {
    assertEquals(PlaybackFailure.Outcome.Skip, PlaybackFailure.decide("a", network = false, failuresInARow = 1, canAdvance = true))
  }

  @Test fun aBadFileStopsAfterThreeInARow() {
    val out = PlaybackFailure.decide("a", network = false, failuresInARow = 3, canAdvance = true)
    assertEquals(PlaybackFailure.Outcome.Stop(PlaybackProblem("a", network = false)), out)
  }

  @Test fun aBadFileStopsWhenThereIsNothingToAdvanceTo() {
    val out = PlaybackFailure.decide("a", network = false, failuresInARow = 1, canAdvance = false)
    assertTrue(out is PlaybackFailure.Outcome.Stop)
  }

  @Test fun aLostConnectionNeverSkipsAndIsMarkedAsNetwork() {
    val out = PlaybackFailure.decide("a", network = true, failuresInARow = 1, canAdvance = true)
    assertEquals(PlaybackFailure.Outcome.Stop(PlaybackProblem("a", network = true)), out)
  }

  @Test fun sleepTimerEndsMinutesFromNow() {
    assertEquals(1_000L + 15 * 60_000L, SleepTimerPlan.endsAt(1_000L, 15))
  }

  @Test fun sleepTimerNeverGoesBackwards() {
    assertEquals(5_000L, SleepTimerPlan.endsAt(5_000L, -3))
  }

  @Test fun remainingTimeIsNeverNegative() {
    assertEquals(40_000L, SleepTimerPlan.remainingMs(100_000L, 60_000L))
    assertEquals(0L, SleepTimerPlan.remainingMs(100_000L, 130_000L))
  }
}
