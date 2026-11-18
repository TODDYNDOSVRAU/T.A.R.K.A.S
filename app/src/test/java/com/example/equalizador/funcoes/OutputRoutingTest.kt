package com.example

import com.example.data.audio.OutputDevice
import com.example.data.audio.OutputKind
import com.example.data.audio.OutputRouting
import com.example.data.autoeq.AutoEqEntry
import com.example.data.model.DeviceCorrection
import com.example.data.model.EqBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which fone correction each output gets, and which AutoEq measurement a fone's name suggests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OutputRoutingTest {

  private fun correction(name: String) = DeviceCorrection(name, "oratory1990", "over-ear", listOf(EqBand(freqHz = 100f, gainDb = 3f)), -3f)

  private val sony = correction("Sony WH-1000XM4")
  private val buds = correction("Samsung Galaxy Buds2 Pro")
  private val btSony = OutputDevice("bt:AA:BB", OutputKind.BLUETOOTH, "WH-1000XM4")
  private val btBuds = OutputDevice("bt:CC:DD", OutputKind.BLUETOOTH, "Galaxy Buds2 Pro")

  @Test
  fun eachFoneGetsItsOwnCorrectionBack() {
    val bindings = mapOf(btSony.key to sony, btBuds.key to buds)
    assertEquals(sony, OutputRouting.decide(btSony, bindings).correction)
    assertEquals(buds, OutputRouting.decide(btBuds, bindings).correction)
  }

  @Test
  fun theLoudspeakerGetsNoFoneCorrectionUnlessOneWasChosenOnIt() {
    assertNull(OutputRouting.decide(OutputDevice.SPEAKER, mapOf(btSony.key to sony)).correction)
    assertEquals(sony, OutputRouting.decide(OutputDevice.SPEAKER, mapOf("speaker" to sony)).correction)
  }

  @Test
  fun noneChosenOnAFoneStaysNone() {
    val d = OutputRouting.decide(btSony, mapOf(btSony.key to null))
    assertNull(d.correction)
  }

  @Test
  fun anUnknownFoneGetsNoCorrection() {
    assertNull(OutputRouting.decide(btBuds, mapOf(btSony.key to sony)).correction)
  }

  @Test
  fun theCorrectionInUseBeforeTheUpdateWaitsForAFoneWithAMatchingName() {
    val start = OutputRouting.firstRun(sony)
    // On the loudspeaker first: nothing applied, still waiting.
    val onSpeaker = OutputRouting.decide(OutputDevice.SPEAKER, start)
    assertNull(onSpeaker.correction)
    assertEquals(start, onSpeaker.bindings)
    // Another fone connects first: it does not get the Sony's correction (seen on a device: a USB-C
    // fone's correction went to Galaxy Buds2), and the correction keeps waiting.
    val other = OutputRouting.decide(btBuds, onSpeaker.bindings)
    assertNull(other.correction)
    assertEquals(start, other.bindings)
    // The Sony takes it and keeps it.
    val sonyOn = OutputRouting.decide(btSony, other.bindings)
    assertEquals(sony, sonyOn.correction)
    assertEquals(mapOf(btSony.key to sony), sonyOn.bindings)
    assertTrue(OutputRouting.firstRun(null).isEmpty())
  }

  @Test
  fun namesMatchOnADistinctiveWordOnly() {
    assertTrue(OutputRouting.sameFone("Sony WH-1000XM4", "WH-1000XM4"))
    assertTrue(OutputRouting.sameFone("Samsung Galaxy Buds2 Pro", "Galaxy Buds2 Pro"))
    assertFalse(OutputRouting.sameFone("Samsung AKG EO-IG955 Type-C", "Buds2 de roger"))
    assertFalse(OutputRouting.sameFone("Apple AirPods Pro", "USB-C Headset Pro"))
  }

  @Test
  fun keysTellFonesApartButTheJackIsOneOutput() {
    assertEquals("bt:AA:BB", OutputDevice.keyFor(OutputKind.BLUETOOTH, "AA:BB", "WH-1000XM4"))
    assertEquals("bt:wh-1000xm4", OutputDevice.keyFor(OutputKind.BLUETOOTH, "", "WH-1000XM4"))
    assertEquals("wired", OutputDevice.keyFor(OutputKind.WIRED, null, "anything"))
    assertEquals("usb:dac x", OutputDevice.keyFor(OutputKind.USB, null, "DAC X"))
  }

  @Test
  fun bindingsSurviveAJsonRoundTripIncludingNone() {
    val bindings = mapOf(btSony.key to sony, "speaker" to null, OutputRouting.CARRY_OVER to buds)
    assertEquals(bindings, OutputRouting.fromJson(OutputRouting.toJson(bindings)))
    assertNull(OutputRouting.fromJson(null))
    assertNull(OutputRouting.fromJson("not json"))
  }

  private val index = listOf(
    AutoEqEntry("Sony WH-1000XM4", "Rtings", "over-ear", "Rtings/over-ear/Sony%20WH-1000XM4"),
    AutoEqEntry("Sony WH-1000XM4", "oratory1990", "over-ear", "oratory1990/over-ear/Sony%20WH-1000XM4"),
    AutoEqEntry("Samsung Galaxy Buds2 Pro", "crinacle", "711 in-ear", "crinacle/711%20in-ear/Samsung%20Galaxy%20Buds2%20Pro"),
    AutoEqEntry("Sennheiser HD 650", "oratory1990", "over-ear", "oratory1990/over-ear/Sennheiser%20HD%20650")
  )

  @Test
  fun aFonesNamePicksTheBestKnownReviewersMeasurement() {
    val s = OutputRouting.suggestions(index, "WH-1000XM4")!!
    assertTrue(s.exact)
    assertEquals(1, s.entries.size)
    assertEquals("oratory1990", s.entries.first().source)
    assertEquals("Samsung Galaxy Buds2 Pro", OutputRouting.suggestions(index, "LE-Galaxy Buds2 Pro")!!.entries.first().name)
  }

  @Test
  fun aNameTheUserGaveTheFoneFallsBackToItsModelWordForTheUserToPick() {
    // Samsung reports the name the user gave the fone, as seen on the S23.
    val s = OutputRouting.suggestions(index + buds2, "Buds2 de roger")!!
    assertFalse(s.exact)
    assertEquals("buds2", s.query)
    assertEquals(setOf("Samsung Galaxy Buds2", "Samsung Galaxy Buds2 Pro"), s.entries.map { it.name }.toSet())
  }

  private val buds2 = AutoEqEntry("Samsung Galaxy Buds2", "Rtings", "in-ear", "Rtings/in-ear/Samsung%20Galaxy%20Buds2")

  @Test
  fun genericOrUnknownNamesSuggestNothing() {
    assertNull(OutputRouting.suggestions(index, "Headset"))
    assertNull(OutputRouting.suggestions(index, "USB Audio"))
    assertNull(OutputRouting.suggestions(index, "Fone do Roger"))
    assertNotNull(OutputRouting.suggestions(index, "HD 650"))
  }
}
