package com.example.windows.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The tilt springs with plain numbers: the limits of D4/D5, the return to neutral, reduced motion and rest. */
class TiltEngineTest {
  private fun run(e: TiltEngine, seconds: Float) {
    var t = 0f
    while (t < seconds) { e.frame(1f / 120f); t += 1f / 120f }
  }

  @Test fun theLimitsAreTheStackOnesAtTheEdges() {
    val e = TiltEngine()
    e.aim(100f, 0f, 100f, 100f)
    assertEquals(StackTuning.MaxRx.toDouble(), e.rx.t.toDouble(), 1e-4)
    assertEquals(StackTuning.MaxRy.toDouble(), e.ry.t.toDouble(), 1e-4)
    e.aim(0f, 100f, 100f, 100f)
    assertEquals(-StackTuning.MaxRx.toDouble(), e.rx.t.toDouble(), 1e-4)
    assertEquals(-StackTuning.MaxRy.toDouble(), e.ry.t.toDouble(), 1e-4)
  }

  @Test fun theCentreIsNeutralAndAnAimOutsideTheCardIsClamped() {
    val e = TiltEngine()
    e.aim(50f, 50f, 100f, 100f)
    assertEquals(0.0, e.rx.t.toDouble(), 1e-4)
    assertEquals(0.0, e.ry.t.toDouble(), 1e-4)
    e.aim(900f, -900f, 100f, 100f)
    assertEquals(StackTuning.MaxRx.toDouble(), e.rx.t.toDouble(), 1e-4)
    assertEquals(StackTuning.MaxRy.toDouble(), e.ry.t.toDouble(), 1e-4)
  }

  @Test fun theContainedVariantNeverPassesFourAndSixDegrees() {
    val e = TiltEngine(maxRx = 4f, maxRy = 6f)
    e.aim(100f, 0f, 100f, 100f)
    var peak = 0f; var peakY = 0f
    for (i in 0 until 300) { e.frame(1f / 120f); peak = maxOf(peak, abs(e.rx.x)); peakY = maxOf(peakY, abs(e.ry.x)) }
    // The spring is under-damped (zeta ~0.51): it may overshoot its target a little, never by half again.
    assertTrue("rx peak $peak", peak <= 4f * 1.2f)
    assertTrue("ry peak $peakY", peakY <= 6f * 1.2f)
    assertEquals(4.0, e.rx.x.toDouble(), 0.05)
    assertEquals(6.0, e.ry.x.toDouble(), 0.05)
  }

  @Test fun theStackLimitsHoldWithTheOvershootOfTheSpring() {
    val e = TiltEngine()
    e.aim(100f, 0f, 100f, 100f)
    var peak = 0f; var peakY = 0f
    for (i in 0 until 300) { e.frame(1f / 120f); peak = maxOf(peak, abs(e.rx.x)); peakY = maxOf(peakY, abs(e.ry.x)) }
    assertTrue("rx peak $peak", peak <= StackTuning.MaxRx * 1.2f)
    assertTrue("ry peak $peakY", peakY <= StackTuning.MaxRy * 1.2f)
  }

  @Test fun leavingBringsItBackToNeutralAndStops() {
    val e = TiltEngine()
    e.aim(100f, 0f, 100f, 100f)
    run(e, 1f)
    e.leave()
    run(e, 2f)
    assertEquals(0.0, e.rx.x.toDouble(), 1e-6)
    assertEquals(0.0, e.ry.x.toDouble(), 1e-6)
    assertTrue(e.atRest)
    assertTrue(e.frame(1f / 120f))
  }

  @Test fun theFrameSaysRestOnlyWhenBothSpringsSettled() {
    val e = TiltEngine()
    assertTrue("a fresh engine is at rest", e.frame(1f / 120f))
    e.aim(100f, 0f, 100f, 100f)
    assertFalse(e.frame(1f / 120f))
    var guard = 0
    while (!e.frame(1f / 120f) && guard++ < 1000) { }
    assertTrue("settles in under 8 s, took $guard frames", guard < 960)
  }

  @Test fun reducedMotionKeepsThePoseAtZeroWhateverTheAim() {
    val e = TiltEngine()
    e.aim(100f, 0f, 100f, 100f)
    run(e, 0.3f)
    assertTrue(e.rx.x != 0f)
    e.reduced = true
    assertEquals(0f, e.rx.x, 0f)
    assertEquals(0f, e.ry.x, 0f)
    e.aim(100f, 0f, 100f, 100f)
    e.lean(1f, 1f)
    assertEquals(0f, e.rx.t, 0f)
    assertEquals(0f, e.ry.t, 0f)
    assertTrue(e.frame(1f / 60f))
    assertEquals(0f, e.rx.x, 0f)
    assertEquals(0f, e.ry.x, 0f)
  }

  @Test fun theKeyboardLeansWithinTheSameLimits() {
    val e = TiltEngine()
    e.lean(1f, 0f)
    assertEquals(StackTuning.MaxRy.toDouble(), e.ry.t.toDouble(), 1e-4)
    assertEquals(0.0, e.rx.t.toDouble(), 1e-4)
    e.lean(0f, -1f)
    assertEquals(StackTuning.MaxRx.toDouble(), e.rx.t.toDouble(), 1e-4)
    e.lean(5f, 5f)
    assertEquals(-StackTuning.MaxRx.toDouble(), e.rx.t.toDouble(), 1e-4)
    assertEquals(StackTuning.MaxRy.toDouble(), e.ry.t.toDouble(), 1e-4)
  }

  @Test fun aLongFrameIsSubSteppedAndStaysStable() {
    val e = TiltEngine()
    e.aim(100f, 0f, 100f, 100f)
    e.frame(5f)
    assertTrue(abs(e.rx.x) < 20f && abs(e.ry.x) < 20f)
    assertTrue(e.rx.x.isFinite() && e.ry.x.isFinite())
  }

  @Test fun anEmptyCardOrZeroSizeAimsNowhere() {
    val e = TiltEngine()
    e.aim(10f, 10f, 0f, 0f)
    assertEquals(0f, e.rx.t, 0f)
    assertEquals(0f, e.ry.t, 0f)
  }
}
