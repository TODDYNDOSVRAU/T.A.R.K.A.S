package com.example

import com.example.ui.screens.audio.parseNumberInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberEntryTest {
  @Test fun `comma and dot are both decimals`() {
    assertEquals(3.5f, parseNumberInput("3,5", -15f, 15f)!!, 0f)
    assertEquals(3.5f, parseNumberInput("3.5", -15f, 15f)!!, 0f)
    assertEquals(-2f, parseNumberInput(" -2 ", -15f, 15f)!!, 0f)
  }

  @Test fun `value is held inside the range`() {
    assertEquals(15f, parseNumberInput("40", -15f, 15f)!!, 0f)
    assertEquals(-15f, parseNumberInput("-99", -15f, 15f)!!, 0f)
    assertEquals(20f, parseNumberInput("5", 20f, 20000f, allowK = true)!!, 0f)
  }

  @Test fun `text that is not a number is rejected`() {
    assertNull(parseNumberInput("", -15f, 15f))
    assertNull(parseNumberInput("abc", -15f, 15f))
    assertNull(parseNumberInput("NaN", -15f, 15f))
    assertNull(parseNumberInput("Infinity", -15f, 15f))
  }

  @Test fun `k and kHz mean thousands only for frequencies`() {
    assertEquals(1500f, parseNumberInput("1,5k", 20f, 20000f, allowK = true)!!, 0.01f)
    assertEquals(2000f, parseNumberInput("2 kHz", 20f, 20000f, allowK = true)!!, 0.01f)
    assertEquals(440f, parseNumberInput("440 Hz", 20f, 20000f, allowK = true)!!, 0f)
    assertNull(parseNumberInput("1k", 20f, 20000f, allowK = false))
  }
}
