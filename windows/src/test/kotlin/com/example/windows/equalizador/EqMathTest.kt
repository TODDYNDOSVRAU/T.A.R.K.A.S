package com.example.windows.equalizador

import org.junit.Assert.assertEquals
import org.junit.Test

class EqMathTest {
  @Test fun aTouchHeightReadsBackTheGainDrawnThere() {
    listOf(-15f, -6f, 0f, 3.5f, 15f).forEach { db ->
      assertEquals(db, yToDb(dbToY(db, 600f), 600f), 1e-4f)
    }
  }
}
