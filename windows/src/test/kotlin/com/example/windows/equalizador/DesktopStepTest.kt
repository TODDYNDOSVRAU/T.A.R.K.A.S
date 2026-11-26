package com.example.windows.equalizador

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopStepTest {
  @Test fun wheelUpRaisesAndDownLowers() {
    assertEquals(0.1f, steppedValue(0f, 1, 0.1f, -1f, 1f), 1e-5f)
    assertEquals(-0.1f, steppedValue(0f, -1, 0.1f, -1f, 1f), 1e-5f)
  }

  @Test fun stepsStopAtTheEnds() {
    assertEquals(1f, steppedValue(0.98f, 1, 0.1f, -1f, 1f), 0f)
    assertEquals(0f, steppedValue(0.02f, -1, 0.1f, 0f, 1f), 0f)
  }

  @Test fun manyStepsDoNotDrift() {
    var v = 0f
    repeat(30) { v = steppedValue(v, 1, 1f / 30f, -1f, 1f) }
    assertEquals(1f, v, 1e-4f)
  }

  @Test fun stopsSetTheStepSize() {
    assertEquals(2f / 30f, stepSize(-1f, 1f, 29, 0.05f), 1e-6f)
    assertEquals(0.05f, stepSize(0f, 1f, 0, 0.05f), 0f)
  }

  @Test fun limitsAreDetectedPerDirection() {
    assertEquals(true, atLimit(1f, 1, -1f, 1f))
    assertEquals(false, atLimit(1f, -1, -1f, 1f))
    assertEquals(true, atLimit(-1f, -1, -1f, 1f))
  }

  @Test fun fineStepIsAFifthOfTheStep() {
    val coarse = steppedValue(0f, 1, 0.5f, -1f, 1f)
    val fine = steppedValue(0f, 1, 0.5f / FINE_DIVISOR, -1f, 1f)
    assertEquals(coarse / FINE_DIVISOR, fine, 1e-5f)
  }
}
