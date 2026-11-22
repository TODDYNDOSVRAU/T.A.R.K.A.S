package com.example.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Sprint2PlayerTest {

  @Test
  fun abLoopCyclesOffToPendingToActiveAndBackToOff() {
    val off = AbLoop()
    assertFalse(off.isActive)
    assertFalse(off.isPendingB)

    val a = off.nextStep(12_000L)
    assertTrue(a.isPendingB)
    assertFalse(a.isActive)
    assertEquals(12_000L, a.startMs)
    assertNull(a.endMs)

    val ab = a.nextStep(28_000L)
    assertTrue(ab.isActive)
    assertFalse(ab.isPendingB)
    assertEquals(12_000L, ab.startMs)
    assertEquals(28_000L, ab.endMs)
    assertFalse(ab.shouldWrap(27_999L))
    assertTrue(ab.shouldWrap(28_000L))
    assertTrue(ab.shouldWrap(30_000L))

    val cleared = ab.nextStep(15_000L)
    assertFalse(cleared.isActive)
    assertFalse(cleared.isPendingB)
    assertNull(cleared.startMs)
    assertNull(cleared.endMs)
  }

  @Test
  fun abLoopSwapsReversedMarkersAndEnforcesMinSpan() {
    val a = AbLoop().nextStep(20_000L)
    val reversed = a.nextStep(10_000L)
    assertTrue(reversed.isActive)
    assertEquals(10_000L, reversed.startMs)
    assertEquals(20_000L, reversed.endMs)

    val tiny = AbLoop().nextStep(5_000L).nextStep(5_100L)
    assertTrue(tiny.isActive)
    assertEquals(5_000L, tiny.startMs)
    assertEquals(5_000L + AbLoop.MIN_SPAN_MS, tiny.endMs)
  }
}
