package com.example.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGuardTest {
  @Test fun fastFramesKeepTheEffectOn() {
    val g = FrameGuard()
    repeat(100) { g.onFrame(16.6f) }
    assertTrue(g.allowed)
  }

  @Test fun threeSlowFramesInARowTurnItOff() {
    val g = FrameGuard()
    g.onFrame(40f); g.onFrame(40f)
    assertTrue(g.allowed)
    g.onFrame(40f)
    assertFalse(g.allowed)
  }

  @Test fun aFastFrameBetweenSlowOnesStartsTheCountAgain() {
    val g = FrameGuard()
    repeat(10) { g.onFrame(40f); g.onFrame(40f); g.onFrame(16f) }
    assertTrue(g.allowed)
  }

  @Test fun onceOffItStaysOffUntilReset() {
    val g = FrameGuard()
    repeat(3) { g.onFrame(50f) }
    g.onFrame(8f)
    assertFalse(g.allowed)
    g.reset()
    assertTrue(g.allowed)
  }
}
