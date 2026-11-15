package com.example

import com.example.data.audio.OutputDevice
import com.example.data.audio.OutputKind
import com.example.data.audio.OutputProfile
import com.example.data.audio.OutputProfileHost
import com.example.data.audio.OutputProfileManager
import com.example.data.audio.OutputProfileStore
import com.example.data.audio.OutputProfiles
import com.example.data.audio.OutputRouting
import com.example.data.audio.SpatialProfile
import com.example.data.model.DeviceCorrection
import com.example.data.model.EqBand
import com.example.data.model.EqSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** E1: each output keeps its whole sound (curve, spatial, correction) and gets it back with the output. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OutputProfilesTest {

  private val speaker = OutputDevice.SPEAKER
  private val buds = OutputDevice("bt:aa", OutputKind.BLUETOOTH, "Sony WH-1000XM4")
  private val other = OutputDevice("bt:bb", OutputKind.BLUETOOTH, "Soundcore Life Q30")

  private fun band(g: Float) = EqBand(freqHz = 1000f, gainDb = g)
  private fun correction(name: String) = DeviceCorrection(name, "oratory1990", bands = listOf(band(2f)))
  private fun eq(gain: Float, device: DeviceCorrection? = null) =
    EqSettings(bands = EqSettings.DEFAULT_BANDS.mapIndexed { i, b -> if (i == 3) b.copy(gainDb = gain) else b }, device = device)
  private fun profile(gain: Float, spatialOn: Boolean = false, device: DeviceCorrection? = null, intensity: Float = 1f) =
    OutputProfile(eq(gain, device), SpatialProfile(spatialOn, "standard", intensity), "flat")

  // ---- rules ----

  @Test
  fun aNewOutputStartsFromWhatPlaysAndIsKept() {
    val now = profile(4f, spatialOn = true, device = correction("Sony WH-1000XM4"))
    val d = OutputProfiles.decide(speaker, OutputProfiles.Book(), now)
    assertEquals(4f, d.profile.eq.bands[3].gainDb, 0f)
    assertTrue(d.profile.spatial.enabled)
    // the loudspeaker never plays a fone's correction
    assertNull(d.profile.eq.device)
    assertEquals(d.profile, d.book.profiles["speaker"])
  }

  @Test
  fun aKnownOutputGetsItsOwnProfileButKeepsTheSwitch() {
    val book = OutputProfiles.Book(profiles = mapOf(buds.key to profile(6f, spatialOn = true)))
    val now = profile(0f).let { it.copy(eq = it.eq.copy(enabled = false)) }
    val d = OutputProfiles.decide(buds, book, now)
    assertEquals(6f, d.profile.eq.bands[3].gainDb, 0f)
    assertTrue(d.profile.spatial.enabled)
    assertFalse("the on/off switch is the user's, not the output's", d.profile.eq.enabled)
    assertEquals(book, d.book)
  }

  @Test
  fun theCarriedOverCorrectionGoesToTheFoneItFitsOnce() {
    val carry = correction("Sony WH-1000XM4")
    val book = OutputProfiles.Book(carry = carry)
    val miss = OutputProfiles.decide(other, book, profile(0f))
    assertNull(miss.profile.eq.device)
    assertEquals(carry, miss.book.carry)
    val hit = OutputProfiles.decide(buds, book, profile(0f))
    assertEquals(carry, hit.profile.eq.device)
    assertNull(hit.book.carry)
    assertTrue(buds.key in hit.book.chosen)
  }

  @Test
  fun bindingsOfThePreviousVersionBecomeProfilesOnTheCurrentSound() {
    val legacy = mapOf(buds.key to correction("Sony WH-1000XM4"), "wired" to null, OutputRouting.CARRY_OVER to correction("X"))
    val book = OutputProfiles.migrate(legacy, profile(3f, spatialOn = true))
    assertEquals("Sony WH-1000XM4", book.profiles[buds.key]!!.eq.device!!.name)
    assertNull(book.profiles["wired"]!!.eq.device)
    assertEquals(3f, book.profiles["wired"]!!.eq.bands[3].gainDb, 0f)
    assertEquals(setOf(buds.key, "wired"), book.chosen)
    assertEquals("X", book.carry!!.name)
  }

  @Test
  fun theBookSurvivesTheFile() {
    val book = OutputProfiles.Book(
      profiles = mapOf(buds.key to profile(5f, spatialOn = true, device = correction("A"), intensity = 0.6f), "speaker" to profile(-2f)),
      carry = correction("C"), chosen = setOf(buds.key)
    )
    val back = OutputProfiles.fromJson(OutputProfiles.toJson(book))!!
    assertEquals(book.chosen, back.chosen)
    assertEquals("C", back.carry!!.name)
    assertEquals(0.6f, back.profiles[buds.key]!!.spatial.intensity, 1e-6f)
    assertEquals("A", back.profiles[buds.key]!!.eq.device!!.name)
    assertEquals(-2f, back.profiles["speaker"]!!.eq.bands[3].gainDb, 0f)
    assertEquals("flat", back.profiles["speaker"]!!.presetId)
  }

  @Test
  fun anUnreadableFileIsNoBook() {
    assertNull(OutputProfiles.fromJson(null))
    assertNull(OutputProfiles.fromJson("{ not json"))
  }

  // ---- manager ----

  private class Host(var now: OutputProfile) : OutputProfileHost {
    override val output: MutableStateFlow<OutputDevice> = MutableStateFlow(OutputDevice.SPEAKER)
    private val bus = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    override val changes: Flow<Unit> = bus.asSharedFlow()
    val applied = mutableListOf<OutputProfile>()
    override fun current() = now
    override fun apply(profile: OutputProfile) { applied += profile; now = profile; bus.tryEmit(Unit) }
    fun user(p: OutputProfile) { now = p; bus.tryEmit(Unit) }
  }

  private class Store(var profiles: String? = null, var legacy: String? = null, var auto: Boolean = true) : OutputProfileStore {
    var saves = 0
    override fun profilesJson() = profiles
    override fun saveProfilesJson(json: String) { profiles = json; saves++ }
    override fun legacyBindingsJson() = legacy
    override fun auto() = auto
    override fun saveAuto(on: Boolean) { auto = on }
  }

  private fun manager(host: Host, store: Store, applied: MutableList<Triple<String, Boolean, Boolean>> = mutableListOf()) =
    OutputProfileManager(CoroutineScope(Dispatchers.Unconfined), host, store, saveDelayMs = 0) { out, changed, announce -> applied += Triple(out.key, changed, announce) }

  @Test
  fun eachOutputGetsBackWhatWasSetOnIt() {
    val host = Host(profile(0f)); val store = Store()
    val m = manager(host, store); m.start()
    host.user(profile(5f, spatialOn = true))
    host.output.value = buds
    assertEquals("a new output starts from what plays", 5f, host.now.eq.bands[3].gainDb, 0f)
    host.user(profile(-3f, spatialOn = false, device = correction("Sony WH-1000XM4")))
    host.output.value = speaker
    assertEquals(5f, host.now.eq.bands[3].gainDb, 0f)
    assertTrue(host.now.spatial.enabled)
    assertNull(host.now.eq.device)
    host.output.value = buds
    assertEquals(-3f, host.now.eq.bands[3].gainDb, 0f)
    assertFalse(host.now.spatial.enabled)
    assertEquals("Sony WH-1000XM4", host.now.eq.device!!.name)
  }

  @Test
  fun comingBackWithoutEditsChangesNothing() {
    val host = Host(profile(2f)); val store = Store()
    val m = manager(host, store); m.start()
    host.output.value = buds
    host.output.value = speaker
    host.output.value = buds
    val saved = store.profiles
    host.output.value = speaker
    assertEquals(saved, store.profiles)
    assertEquals(2f, host.now.eq.bands[3].gainDb, 0f)
  }

  @Test
  fun theQuietLaunchAndTheNoticeAfterwards() {
    val host = Host(profile(0f, device = correction("Sony WH-1000XM4"))); val store = Store()
    val calls = mutableListOf<Triple<String, Boolean, Boolean>>()
    manager(host, store, calls).start()
    // first output: announce = false; the loudspeaker drops the fone's correction quietly
    assertEquals(Triple("speaker", true, false), calls[0])
    host.output.value = buds
    // the fone the carried-over correction fits gets it back, and says so
    assertEquals(Triple("bt:aa", true, true), calls[1])
  }

  @Test
  fun withTheSwitchOffNothingIsPlayedButChangesAreStillKept() {
    val host = Host(profile(0f)); val store = Store(auto = false)
    val m = manager(host, store); m.start()
    host.user(profile(7f))
    host.output.value = buds
    assertTrue(host.applied.isEmpty())
    assertEquals(7f, host.now.eq.bands[3].gainDb, 0f)
    host.output.value = speaker
    m.setAuto(true)
    assertEquals("turning it on plays this output's profile", 7f, host.now.eq.bands[3].gainDb, 0f)
    assertTrue(store.auto)
  }

  @Test
  fun pickingOrClearingACorrectionMarksTheOutputAsChosen() {
    val host = Host(profile(0f)); val store = Store()
    val m = manager(host, store); m.start()
    host.output.value = buds
    assertFalse(m.hasChosen(buds.key))
    assertNull(m.correctionOf(buds.key))
    host.user(profile(0f, device = correction("Sony WH-1000XM4")))
    assertTrue(m.hasChosen(buds.key))
    assertEquals("Sony WH-1000XM4", m.correctionOf(buds.key)!!.name)
  }

  @Test
  fun profilesComeBackAfterARestart() {
    val host = Host(profile(0f)); val store = Store()
    manager(host, store).start()
    host.output.value = buds
    host.user(profile(9f, spatialOn = true))
    assertNotNull(store.profiles)
    val host2 = Host(profile(0f)); host2.output.value = buds
    manager(host2, store).start()
    assertEquals(9f, host2.now.eq.bands[3].gainDb, 0f)
    assertTrue(host2.now.spatial.enabled)
  }

  @Test
  fun theFirstRunReadsTheBindingsOfThePreviousVersion() {
    val legacy = OutputRouting.toJson(mapOf(buds.key to correction("Sony WH-1000XM4")))
    val host = Host(profile(1f)); host.output.value = buds
    val store = Store(legacy = legacy)
    manager(host, store).start()
    assertEquals("Sony WH-1000XM4", host.now.eq.device!!.name)
    assertEquals(1f, host.now.eq.bands[3].gainDb, 0f)
    assertNotNull("the new file is written at once", store.profiles)
  }
}
