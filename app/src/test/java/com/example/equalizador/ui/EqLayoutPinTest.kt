package com.example

import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqBand
import com.example.data.model.EqCompare
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * E14: what the user hears and what counts as "edited" must not depend on how the bands are stored.
 * The settings are built from their saved JSON, so these tests describe the stored format and hold
 * before and after the graphic and parametric lists were merged into one source.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EqLayoutPinTest {

  private fun bands(vararg b: Triple<Float, Float, Float>) = JSONArray().apply {
    b.forEach { (f, g, q) -> put(JSONObject().put("f", f.toDouble()).put("g", g.toDouble()).put("q", q.toDouble())) }
  }

  private fun json(parametric: Boolean, graphic: JSONArray? = null, param: JSONArray? = null): JSONObject =
    JSONObject().put("parametric", parametric).put("autoPre", false).apply {
      graphic?.let { put("bands", it) }
      param?.let { put("paramBands", it) }
    }

  private fun settings(o: JSONObject) = EqPresets.settingsFromJson(o)

  private fun flipped(o: JSONObject): JSONObject = JSONObject(o.toString()).put("parametric", !o.optBoolean("parametric"))

  private fun roundTrip(s: EqSettings) = EqPresets.settingsFromJson(JSONObject(EqPresets.settingsToJson(s).toString()))

  private val probes = listOf(40.0, 100.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
  private fun curve(s: EqSettings) = probes.map { EqDsp.responseDb(s, it) }

  private fun assertSameCurve(a: EqSettings, b: EqSettings) =
    curve(a).zip(curve(b)).forEach { (x, y) -> assertEquals(x, y, 1e-6) }

  private val graphicBoost = bands(Triple(1000f, 6f, 1.4f))
  private val paramCut = bands(Triple(1000f, -6f, 1f))

  @Test
  fun onlyTheLayoutInUseIsHeard() {
    val o = json(parametric = false, graphic = graphicBoost, param = paramCut)
    assertTrue(EqDsp.responseDb(settings(o), 1000.0) > 3.0)
    assertEquals(-6.0, EqDsp.responseDb(settings(flipped(o)), 1000.0), 0.2)
  }

  @Test
  fun bothLayoutsSurviveASaveWhicheverIsInUse() {
    listOf(false, true).forEach { mode ->
      val o = json(parametric = mode, graphic = graphicBoost, param = paramCut)
      val s = settings(o)
      val back = roundTrip(s)
      assertSameCurve(s, back)
      // the layout not in use is still there to come back to
      assertSameCurve(settings(flipped(o)), settings(flipped(EqPresets.settingsToJson(back))))
      assertEquals(mode, back.parametric)
    }
  }

  @Test
  fun savedSettingsWithoutTheParametricListStayGraphicAndFlatThere() {
    val s = settings(JSONObject().put("preamp", -1.0).put("bands", graphicBoost))
    assertFalse(s.parametric)
    val asParam = settings(flipped(EqPresets.settingsToJson(s)))
    // the single default parametric band is flat: nothing to filter
    assertTrue(EqDsp.activeFilters(asParam, 48000).isEmpty())
    assertEquals(0.0, EqDsp.responseDb(asParam.copy(preampDb = 0f), 1000.0), 0.01)
  }

  @Test
  fun theLayoutNotInUseNeverMakesAPresetLookEdited() {
    val preset = settings(json(false))
    assertFalse(EqCompare.edited(settings(json(false, param = paramCut)), preset))
    val paramPreset = settings(json(true))
    assertFalse(EqCompare.edited(settings(json(true, graphic = graphicBoost)), paramPreset))
  }

  @Test
  fun theSameCurveInAnotherLayoutIsNotAnEdit() {
    val g = settings(json(false, graphic = graphicBoost))
    val p = settings(json(true, param = bands(Triple(1000f, 6f, 1.4f))))
    assertFalse(EqCompare.edited(p, g))
    assertFalse(EqCompare.edited(g, p))
  }

  @Test
  fun aDifferentCurveIsAnEditInEitherLayout() {
    val flat = settings(json(false))
    assertTrue(EqCompare.edited(settings(json(false, graphic = graphicBoost)), flat))
    assertTrue(EqCompare.edited(settings(json(true, param = paramCut)), flat))
    assertTrue(EqCompare.edited(settings(json(true, param = paramCut)), settings(json(true, param = graphicBoost))))
  }

  @Test
  fun everythingOutsideTheBandsStillCounts() {
    val a = settings(json(false))
    assertTrue(EqCompare.edited(settings(json(false).put("bass", 2.0)), a))
    assertTrue(EqCompare.edited(settings(json(true).put("preamp", 1.0)), a))
    assertFalse(EqCompare.edited(settings(json(false).put("enabled", false)), a))
  }

  @Test
  fun theHeardBandsOfTheTwoLayoutsAreTheOnesTheFiltersUse() {
    val s = settings(json(true, param = bands(Triple(200f, 3f, 1f), Triple(3000f, -2f, 2f))))
    assertEquals(2, EqDsp.activeFilters(s, 48000).size)
    assertEquals(0, EqDsp.activeFilters(settings(json(false, param = bands(Triple(200f, 3f, 1f)))), 48000).size)
  }

  @Test
  fun aBandOfEachLayoutHasItsOwnDefaultWhenNothingWasSaved() {
    val s = settings(json(true))
    assertEquals(1, roundTrip(s).let { EqPresets.settingsToJson(it).getJSONArray("paramBands").length() })
    assertEquals(10, roundTrip(s).let { EqPresets.settingsToJson(it).getJSONArray("bands").length() })
    assertEquals(EqBand(freqHz = 1000f, q = 1.4f), EqSettings.DEFAULT_PARAM_BANDS.single())
  }
}
