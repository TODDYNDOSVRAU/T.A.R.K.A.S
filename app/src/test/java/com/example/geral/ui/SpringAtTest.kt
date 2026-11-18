package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringAtTest {
  @Test
  fun startsAtZeroAndSettlesAtOne() {
    assertEquals(0f, springAt(0f), 0f)
    assertEquals(1f, springAt(3f), 0.001f)
    assertEquals(1f, springAt(3f, damping = 40f), 0.001f)
  }

  @Test
  fun remotionDefaultsOvershoot() {
    val peak = (1..100).maxOf { springAt(it / 100f) }
    assertTrue("peak $peak", peak > 1.1f)
  }

  @Test
  fun criticalDampingNeverOvershoots() {
    // damping = 2 * sqrt(stiffness * mass): critically damped
    assertTrue((1..300).all { springAt(it / 100f, damping = 20f) <= 1f })
  }
}
