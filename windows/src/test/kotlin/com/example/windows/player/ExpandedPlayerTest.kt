package com.example.windows.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedPlayerTest {
  @Test fun slideStartsLowerAndEndsInPlace() {
    assertEquals(80f, ExpandMath.translateY(0f, 1000f), 0.001f)
    assertEquals(0f, ExpandMath.translateY(1f, 1000f), 0.001f)
    assertEquals(0f, ExpandMath.translateY(1.4f, 1000f), 0.001f)
  }

  @Test fun alphaIsClamped() {
    assertEquals(0f, ExpandMath.alpha(-1f), 0f)
    assertEquals(1f, ExpandMath.alpha(2f), 0f)
  }

  @Test fun durationsStayInTheDecelerationWindow() {
    assertTrue(ExpandMath.OpenMs in 150..300 && ExpandMath.CloseMs in 150..300)
  }

  @Test fun drawnOnlyWhileOpenOrMoving() {
    assertFalse(ExpandMath.visible(0f, false))
    assertTrue(ExpandMath.visible(0.4f, false))
    assertTrue(ExpandMath.visible(0f, true))
  }

  @Test fun closeReportsWhetherItWasOpen() {
    ExpandedPlayer.close()
    assertFalse(ExpandedPlayer.close())
    ExpandedPlayer.show()
    assertTrue(ExpandedPlayer.close())
  }
}
