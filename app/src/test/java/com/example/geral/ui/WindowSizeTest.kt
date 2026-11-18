package com.example

import com.example.ui.layout.WidthClass
import com.example.ui.layout.twoPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowSizeTest {
  @Test fun theBreakpointsAre600And840() {
    assertEquals(WidthClass.COMPACT, WidthClass.of(0))
    assertEquals(WidthClass.COMPACT, WidthClass.of(411))
    assertEquals(WidthClass.COMPACT, WidthClass.of(599))
    assertEquals(WidthClass.MEDIUM, WidthClass.of(600))
    assertEquals(WidthClass.MEDIUM, WidthClass.of(839))
    assertEquals(WidthClass.EXPANDED, WidthClass.of(840))
    assertEquals(WidthClass.EXPANDED, WidthClass.of(1280))
  }

  @Test fun onlyAPhoneUprightIsOneColumn() {
    assertFalse(WidthClass.COMPACT.twoPane)
    assertTrue(WidthClass.MEDIUM.twoPane)
    assertTrue(WidthClass.EXPANDED.twoPane)
  }
}
