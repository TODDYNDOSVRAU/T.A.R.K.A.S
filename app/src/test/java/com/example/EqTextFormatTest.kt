package com.example

import com.example.data.audio.dsp.EqTextFormat
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import com.example.data.model.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqTextFormatTest {

  private val apo = """
    Preamp: -6.1 dB
    Filter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70
    Filter 2: ON PK Fc 2500 Hz Gain -3.2 dB Q 2.10
    Filter 3: OFF HSC Fc 9000 Hz Gain 2.0 dB Q 0.70
  """.trimIndent()

  @Test fun readsApoFilters() {
    val r = EqTextFormat.parse(apo) as EqTextFormat.Result.Parametric
    assertEquals(-6.1f, r.preampDb, 0.001f)
    assertEquals(3, r.bands.size)
    assertEquals(FilterType.LOW_SHELF, r.bands[0].type)
    assertEquals(105f, r.bands[0].freqHz, 0f)
    assertEquals(-3.2f, r.bands[1].gainDb, 0.001f)
    assertEquals(2.1f, r.bands[1].q, 0.001f)
    assertEquals(false, r.bands[2].enabled)
  }

  @Test fun exportThenImportKeepsTheCurve() {
    val s = EqSettings(
      parametric = true,
      preampDb = -4.5f,
      paramBands = listOf(
        EqBand(60f, 5.5f, 4f, FilterType.PEAK),
        EqBand(80f, 0f, 0.7f, FilterType.HIGH_PASS),
        EqBand(12000f, -3f, 0.7f, FilterType.HIGH_SHELF)
      )
    )
    val r = EqTextFormat.parse(EqTextFormat.export(s)) as EqTextFormat.Result.Parametric
    assertEquals(-4.5f, r.preampDb, 0.001f)
    assertEquals(s.paramBands.size, r.bands.size)
    s.paramBands.zip(r.bands).forEach { (a, b) ->
      assertEquals(a.type, b.type)
      assertEquals(a.freqHz, b.freqHz, 0.01f)
      assertEquals(a.gainDb, b.gainDb, 0.01f)
      assertEquals(a.q, b.q, 0.01f)
    }
  }

  @Test fun capsAtSixtyFourFilters() {
    val text = (1..80).joinToString("\n") { "Filter $it: ON PK Fc ${100 + it} Hz Gain 1 dB Q 1" }
    assertEquals(64, (EqTextFormat.parse(text) as EqTextFormat.Result.Parametric).bands.size)
  }

  @Test fun graphicEqLandsOnTheGraphicBands() {
    // A flat +3 dB curve with enough points.
    val text = "GraphicEQ: 20 3; 100 3; 1000 3; 5000 3; 20000 3"
    val r = EqTextFormat.parse(text) as EqTextFormat.Result.Graphic
    assertEquals(EqSettings.DEFAULT_FREQUENCIES.size, r.gainsDb.size)
    assertTrue(r.gainsDb.all { it == 3f })
  }

  @Test fun graphicEqInterpolatesOnLogFrequency() {
    val text = "GraphicEQ: 10 0; 100 0; 1000 6; 10000 6; 20000 6"
    val r = EqTextFormat.parse(text) as EqTextFormat.Result.Graphic
    // 316 Hz is half way between 100 and 1000 on a log axis.
    val i = EqSettings.DEFAULT_FREQUENCIES.indexOf(250f)
    assertTrue(r.gainsDb[i] in 2f..3.5f)
  }

  @Test fun tooFewGraphicPointsIsRefused() {
    assertNull(EqTextFormat.parse("GraphicEQ: 20 1; 100 2; 1000 3"))
  }

  @Test fun textWithNoCurveIsRefused() {
    assertNull(EqTextFormat.parse("hello"))
    assertNotNull(EqTextFormat.parse(apo))
  }

  @Test fun nanFrequencyInGraphicEqIsIgnored() {
    val r = EqTextFormat.parse("GraphicEQ: NaN 3; 20 1; 100 2; 1000 3; 5000 2; 20000 1") as EqTextFormat.Result.Graphic
    assertEquals(5, r.points)
    assertTrue(r.gainsDb.all { it.isFinite() })
  }

  @Test fun decimalCommaAndPlusSignAreRead() {
    val r = EqTextFormat.parse("Preamp: -6,1 dB\nFilter 1: ON PK Fc 1000 Hz Gain +3,5 dB Q 1,5") as EqTextFormat.Result.Parametric
    assertEquals(-6.1f, r.preampDb, 0.001f)
    assertEquals(3.5f, r.bands[0].gainDb, 0.001f)
    assertEquals(1.5f, r.bands[0].q, 0.001f)
  }

  @Test fun bassAndTrebleAreNotAppliedTwiceOnReimport() {
    val s = EqSettings(parametric = true, bassDb = 4f, trebleDb = -2f, paramBands = listOf(EqBand(1000f, 1f, 1f, FilterType.PEAK)))
    val back = EqTextFormat.apply(s, EqTextFormat.parse(EqTextFormat.export(s))!!)
    assertEquals(0f, back.bassDb, 0f)
    assertEquals(0f, back.trebleDb, 0f)
    assertEquals(3, back.paramBands.size)
  }

  @Test fun applySwitchesTheMode() {
    val p = EqTextFormat.parse(apo)!!
    val s = EqTextFormat.apply(EqSettings(), p)
    assertTrue(s.parametric)
    assertEquals(3, s.paramBands.size)
    val g = EqTextFormat.apply(EqSettings(parametric = true), EqTextFormat.Result.Graphic(List(15) { 1f }, 5))
    assertEquals(false, g.parametric)
    assertEquals(1f, g.bands[0].gainDb, 0f)
  }
}
