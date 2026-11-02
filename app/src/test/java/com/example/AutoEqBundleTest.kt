package com.example

import com.example.data.autoeq.AutoEqBundle
import com.example.data.autoeq.AutoEqEntry
import com.example.data.autoeq.AutoEqParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/** The offline AutoEq package in the app's assets: complete, and identical to the downloaded files. */
class AutoEqBundleTest {

  private val asset = File("src/main/assets/autoeq/parametric.aeq")
  private val profiles by lazy { GZIPInputStream(asset.inputStream()).bufferedReader().use { AutoEqBundle.parse(it.readText()) } }

  @Test
  fun thePackageHoldsEveryProfileOnce() {
    assertTrue("asset missing: ${asset.absolutePath}", asset.exists())
    assertEquals(8849, profiles.size)
    assertEquals(profiles.size, profiles.map { it.entry.path }.toSet().size)
    // Every packaged curve reads back.
    profiles.forEach { assertNotNull(it.entry.name, AutoEqBundle.correction(it)) }
  }

  @Test
  fun aPackagedCurveEqualsTheDownloadedFile() {
    // oratory1990's Sennheiser HD 650 as AutoEq publishes it (results/oratory1990/over-ear/...).
    val original = """
      Preamp: -6.1 dB
      Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
      Filter 2: ON PK Fc 8800 Hz Gain 5.1 dB Q 1.42
      Filter 3: ON PK Fc 118 Hz Gain -3.1 dB Q 0.50
      Filter 4: ON PK Fc 37 Hz Gain 0.7 dB Q 3.96
      Filter 5: ON PK Fc 3169 Hz Gain -1.7 dB Q 3.89
      Filter 6: ON HSC Fc 10000 Hz Gain -2.1 dB Q 0.70
      Filter 7: ON PK Fc 1227 Hz Gain -1.2 dB Q 2.53
      Filter 8: ON PK Fc 2055 Hz Gain 1.2 dB Q 3.23
      Filter 9: ON PK Fc 587 Hz Gain 0.4 dB Q 1.19
      Filter 10: ON PK Fc 5332 Hz Gain -1.1 dB Q 5.75
    """.trimIndent()
    val packaged = profiles.first { it.entry.path == "oratory1990/over-ear/Sennheiser%20HD%20650" }
    val entry = AutoEqEntry("Sennheiser HD 650", "oratory1990", "", "oratory1990/over-ear/Sennheiser%20HD%20650")
    assertEquals(AutoEqParser.parseParametric(original, entry), AutoEqBundle.correction(packaged))
  }

  @Test
  fun aLineWithoutAllFieldsIsSkipped() {
    val parsed = AutoEqBundle.parse("#header\nonly\tthree\tfields\nA\tsrc\trig\tsrc/rig/A\t-1.0\tPK 100 2.0 1.00 1\n")
    assertEquals(1, parsed.size)
    assertEquals(1, AutoEqBundle.correction(parsed.single())!!.bands.size)
  }
}
