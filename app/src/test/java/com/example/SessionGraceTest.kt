package com.example

import com.example.data.systemaudio.DpCurve
import com.example.data.systemaudio.SessionGrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When other apps' sessions are let go, and the input gain the effect gets for preamp and balance. */
class SessionGraceTest {

  @Test
  fun aSessionMissingForLessThanTheGraceTimeKeepsItsEffect() {
    val g = SessionGrace(2000)
    assertEquals(emptySet<Int>(), g.expired(setOf(7), emptySet(), 1000))
    assertEquals(emptySet<Int>(), g.expired(setOf(7), emptySet(), 2999))
    assertEquals(setOf(7), g.expired(setOf(7), emptySet(), 3000))
  }

  @Test
  fun aSessionSeenAgainWithinTheGraceTimeStartsOver() {
    val g = SessionGrace(2000)
    g.expired(setOf(7), emptySet(), 0)
    g.expired(setOf(7), setOf(7), 1500) // the track change reopened it
    assertEquals(emptySet<Int>(), g.expired(setOf(7), emptySet(), 3000))
    assertEquals(setOf(7), g.expired(setOf(7), emptySet(), 5000))
  }

  @Test
  fun nextExpiryPointsAtTheEarliestMissingSession() {
    val g = SessionGrace(2000)
    assertNull(g.nextExpiry())
    g.expired(setOf(1, 2), setOf(2), 100)
    g.expired(setOf(1, 2), emptySet(), 900)
    assertEquals(2100L, g.nextExpiry())
  }

  @Test
  fun aRefusedSessionGetsFiveTriesThenWaitsUntilItGoes() {
    val g = SessionGrace()
    repeat(SessionGrace.MAX_ATTEMPTS - 1) { assertTrue(g.attached(9, ok = false)) }
    assertFalse(g.attached(9, ok = false))
    assertFalse(g.shouldTry(9))
    // The session ended: when it comes back it is a new chance.
    g.expired(emptySet(), emptySet(), 0)
    assertTrue(g.shouldTry(9))
  }

  @Test
  fun successClearsTheFailures() {
    val g = SessionGrace()
    g.attached(4, ok = false)
    assertFalse(g.attached(4, ok = true))
    assertTrue(g.shouldTry(4))
  }

  @Test
  fun centredBalanceGivesBothChannelsThePreamp() {
    val gains = DpCurve.inputGainsDb(-6f, 0f)
    assertEquals(-6f, gains[0], 1e-4f)
    assertEquals(-6f, gains[1], 1e-4f)
  }

  @Test
  fun balanceLowersOnlyTheOtherSideLikeTheAppsOwnProcessor() {
    // Balance 0.5 to the right: left at half amplitude (-6.02 dB), right untouched.
    val right = DpCurve.inputGainsDb(-3f, 0.5f)
    assertEquals(-3f - 6.0206f, right[0], 1e-3f)
    assertEquals(-3f, right[1], 1e-4f)
    val left = DpCurve.inputGainsDb(0f, -0.5f)
    assertEquals(0f, left[0], 1e-4f)
    assertEquals(-6.0206f, left[1], 1e-3f)
  }

  @Test
  fun fullBalanceCutsTheOtherSideBy48Db() {
    val gains = DpCurve.inputGainsDb(0f, 1f)
    assertEquals(DpCurve.MIN_INPUT_DB, gains[0], 0f)
    assertEquals(0f, gains[1], 0f)
  }
}
