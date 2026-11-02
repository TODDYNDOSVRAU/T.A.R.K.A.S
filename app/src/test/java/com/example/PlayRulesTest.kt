package com.example

import com.example.data.playback.EndReason
import com.example.data.playback.PlayRules
import com.example.data.playback.TrackStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What counts as a play and what counts as a skip. */
class PlayRulesTest {

  private val song = 200_000L // 3:20

  @Test
  fun aTrackThatEndsCountsAsPlayed() {
    assertTrue(PlayRules.countsAsPlay(EndReason.NATURAL, 1_000, song))
  }

  @Test
  fun halfTheTrackOrFourMinutesCountsAsPlayed() {
    assertTrue(PlayRules.countsAsPlay(EndReason.SKIP_FORWARD, 100_000, song))
    assertFalse(PlayRules.countsAsPlay(EndReason.SKIP_FORWARD, 99_000, song))
    // A 20-minute track: 4 minutes are enough.
    assertTrue(PlayRules.countsAsPlay(EndReason.STOPPED, 240_000, 1_200_000))
  }

  @Test
  fun nextWithinTheFirstThirtySecondsIsASkip() {
    assertTrue(PlayRules.countsAsSkip(EndReason.SKIP_FORWARD, 10_000, song))
    assertTrue(PlayRules.countsAsSkip(EndReason.NEW_SELECTION, 10_000, song))
    assertFalse(PlayRules.countsAsSkip(EndReason.SKIP_FORWARD, 45_000, song))
    // Racing through the list does not judge each track.
    assertFalse(PlayRules.countsAsSkip(EndReason.SKIP_FORWARD, 1_000, song))
    // Going back or stopping is not a skip.
    assertFalse(PlayRules.countsAsSkip(EndReason.SKIP_BACKWARD, 10_000, song))
    assertFalse(PlayRules.countsAsSkip(EndReason.STOPPED, 10_000, song))
  }

  @Test
  fun aVeryShortTrackSkippedLateIsAPlayNotASkip() {
    // 20 s track, Next at 12 s: more than half was heard.
    assertTrue(PlayRules.countsAsPlay(EndReason.SKIP_FORWARD, 12_000, 20_000))
    assertFalse(PlayRules.countsAsSkip(EndReason.SKIP_FORWARD, 12_000, 20_000))
  }

  @Test
  fun countersMoveAndSurviveEncoding() {
    var s = TrackStats()
    s = PlayRules.apply(s, EndReason.NATURAL, song, song, 1000)
    s = PlayRules.apply(s, EndReason.SKIP_FORWARD, 5_000, song, 2000)
    s = PlayRules.apply(s, EndReason.STOPPED, 60_000, song, 3000) // neither
    assertEquals(TrackStats(plays = 1, skips = 1, lastPlayedMs = 1000, lastSkippedMs = 2000), s)
    assertEquals(s, PlayRules.decode(PlayRules.encode(s)))
    assertNull(PlayRules.decode("garbage"))
  }
}
