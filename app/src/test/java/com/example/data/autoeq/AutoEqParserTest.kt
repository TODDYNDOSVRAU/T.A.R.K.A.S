package com.example.data.autoeq

import com.example.data.model.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqParserTest {
  private val index = """
    # Index
    This is a list of all equalization profiles.

    - [1Custom SA02](./crinacle/711%20in-ear/1Custom%20SA02) by crinacle on 711
    - [1MORE Aero (ANC Off)](./HypetheSonics/GRAS%20RA0045%20in-ear/1MORE%20Aero%20(ANC%20Off)) by HypetheSonics on GRAS RA0045
    - [Sennheiser HD 650](./oratory1990/over-ear/Sennheiser%20HD%20650) by oratory1990
    - [Sennheiser HD 600](./oratory1990/over-ear/Sennheiser%20HD%20600) by oratory1990
  """.trimIndent()

  @Test
  fun parsesIndexLinesWithParenthesesAndRigs() {
    val entries = AutoEqParser.parseIndex(index)
    assertEquals(4, entries.size)
    val aero = entries[1]
    assertEquals("1MORE Aero (ANC Off)", aero.name)
    assertEquals("HypetheSonics/GRAS%20RA0045%20in-ear/1MORE%20Aero%20(ANC%20Off)", aero.path)
    assertEquals("GRAS RA0045", aero.rig)
    assertEquals("Intra-auricular", aero.form)
    val hd650 = entries[2]
    assertEquals("oratory1990", hd650.source)
    assertEquals("over-ear", hd650.rig)
    assertEquals("Over-ear", hd650.form)
  }

  @Test
  fun searchIgnoresSpacesAndCase() {
    val entries = AutoEqParser.parseIndex(index)
    assertEquals(listOf("Sennheiser HD 650"), AutoEqParser.search(entries, "hd650").map { it.name })
    assertEquals(2, AutoEqParser.search(entries, "sennheiser hd").size)
    assertEquals(1, AutoEqParser.search(entries, "", source = "crinacle").size)
  }

  @Test
  fun parsesParametricFile() {
    val text = """
      Preamp: -6.1 dB
      Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
      Filter 2: ON PK Fc 8800 Hz Gain 5.1 dB Q 1.42
      Filter 6: ON HSC Fc 10000 Hz Gain -2.1 dB Q 0.70
    """.trimIndent()
    val entry = AutoEqParser.parseIndex(index)[2]
    val c = AutoEqParser.parseParametric(text, entry)
    assertNotNull(c)
    c!!
    assertEquals(-6.1f, c.preampDb, 0.001f)
    assertEquals(3, c.bands.size)
    assertEquals(FilterType.LOW_SHELF, c.bands[0].type)
    assertEquals(8800f, c.bands[1].freqHz, 0.1f)
    assertEquals(-2.1f, c.bands[2].gainDb, 0.001f)
    assertTrue(c.bands.all { it.enabled })
    assertEquals("Sennheiser HD 650", c.name)
  }
}
