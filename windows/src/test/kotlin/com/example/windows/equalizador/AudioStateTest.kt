package com.example.windows.equalizador

import com.example.data.audio.loudness.LoudnessSource
import com.example.data.audio.loudness.NormalizationMode
import com.example.data.audio.spatial.SpatialControl
import com.example.data.autoeq.AutoEqRepository
import com.example.data.db.AppDatabase
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import com.example.windows.audio.DesktopPlayer
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AudioStateTest {
  private val dir = java.nio.file.Files.createTempDirectory("tarkas-audio").toFile()
  private val db = AppDatabase.inMemory()
  private val player = DesktopPlayer()
  // Settings are written on this scope; the teardown waits for those writes before the database closes.
  private val stateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val state = TarkasState(player, db, scope = stateScope)

  private fun audio() = AudioState(
    state, AutoEqRepository(dir), CoroutineScope(Dispatchers.Unconfined),
    listOutputs = { listOf("Speakers", "Headset") }, saveDelayMs = 0,
  )

  @After fun close() {
    SpatialControl.configure(false, SpatialControl.preset, 1.0)
    SpatialControl.onActiveChanged = null
    runBlocking {
      val job = stateScope.coroutineContext[Job]!!
      job.children.filter { it.isActive && it.isCompleted.not() }.toList().forEach { child ->
        // Long-lived collectors never finish; the one-shot writes do. Give them a moment, then cancel.
        kotlinx.coroutines.withTimeoutOrNull(500) { child.join() } ?: child.cancelAndJoin()
      }
      job.cancelAndJoin()
    }
    db.close(); dir.deleteRecursively()
  }

  @Test fun presetsSaveSelectAndDelete() {
    val a = audio()
    assertEquals(EqPresets.FLAT.id, a.activePresetId.value)
    a.updateBand(0) { it.copy(gainDb = 3f) }
    assertTrue("a changed curve is modified", a.eqModified.value)
    a.savePresetAs("  Mine ")
    val saved = a.presets.value.single { it.isCustom }
    assertEquals("Mine", saved.name)
    assertEquals(saved.id, a.activePresetId.value)
    assertFalse(a.eqModified.value)
    a.updateBand(1) { it.copy(gainDb = -2f) }
    assertTrue(a.eqModified.value)
    a.overwritePreset()
    assertFalse(a.eqModified.value)
    a.deletePreset(saved.id)
    assertEquals(EqPresets.FLAT.id, a.activePresetId.value)
    assertTrue(a.presets.value.none { it.isCustom })
  }

  @Test fun settingsSurviveANewState() {
    val a = audio()
    a.updateBand(2) { it.copy(gainDb = 4.5f) }
    a.setEnabled(false)
    a.saveEqNow()
    state.resetEq()
    assertEquals(0f, state.eqSettings.value.bands[2].gainDb, 0f)
    audio()
    assertEquals(4.5f, state.eqSettings.value.bands[2].gainDb, 0.001f)
    assertFalse(state.eqSettings.value.enabled)
  }

  @Test fun resetReturnsToFlat() {
    val a = audio()
    a.updateBand(3) { it.copy(gainDb = 6f) }
    a.reset()
    assertTrue(state.eqSettings.value.bands.all { it.gainDb == 0f })
    assertEquals(EqPresets.FLAT.id, a.activePresetId.value)
  }

  @Test fun spatialChoicesArePersisted() {
    val a = audio()
    a.spatialActions.onEnabled(true)
    a.spatialActions.onIntensity(0.6f)
    a.spatialActions.onIntensityFinished(0.6f)
    assertTrue(SpatialControl.enabled)
    val b = audio()
    assertTrue(b.spatial.value.enabled)
    assertEquals(0.6f, b.spatial.value.intensity, 0.001f)
  }

  @Test fun outputChoiceReachesThePlayer() {
    val a = audio()
    assertEquals(listOf("Speakers", "Headset"), a.outputs.value.names)
    a.selectOutput("Headset")
    assertEquals("Headset", player.outputMixer)
    a.selectOutput(null)
    assertEquals(null, player.outputMixer)
  }

  // ---- the audio path: what the player's chain really does to PCM ----

  private fun stereo(frames: Int, f: (Int) -> Float): FloatArray =
    FloatArray(frames * 2).also { for (i in 0 until frames) { it[2 * i] = f(i); it[2 * i + 1] = f(i) } }

  private fun rms(x: FloatArray, from: Int): Double {
    var s = 0.0
    for (i in from until x.size) s += x[i] * x[i]
    return sqrt(s / (x.size - from))
  }

  private fun runBlocks(chain: AudioChain, make: (Int) -> Float, blocks: Int, frames: Int = 512): FloatArray {
    chain.configure(48_000, 2)
    var last = FloatArray(0)
    for (b in 0 until blocks) {
      last = stereo(frames) { make(b * frames + it) }
      chain.process(last, frames)
    }
    return last
  }

  @Test fun equalizerGainChangesTheSound() {
    val a = audio()
    val tone = { n: Int -> 0.1f * sin(2 * PI * 1000.0 * n / 48_000).toFloat() }
    val idx = state.eqSettings.value.bands.indices.minBy { kotlin.math.abs(kotlin.math.ln(state.eqSettings.value.bands[it].freqHz / 1000.0)) }
    // The automatic preamp makes room for a boost, so compare a boost with a cut on the same band (12 dB apart).
    a.updateBand(idx) { it.copy(gainDb = 6f) }
    val boosted = rms(runBlocks(player.chain, tone, 40), 0)
    a.updateBand(idx) { it.copy(gainDb = -6f) }
    val cut = rms(runBlocks(player.chain, tone, 40), 0)
    assertTrue("boost must be louder than cut: boost=$boosted cut=$cut", boosted > cut * 2.0)
  }

  @Test fun spatialStageDelaysStereoByTheDocumentedFrames() {
    player.setEq(EqSettings(enabled = false))
    val chain = AudioChain(com.example.data.audio.dsp.EqDsp().also { it.update(EqSettings(enabled = false)) })
    chain.configure(48_000, 2)
    val frames = 512
    val impulseAt = 100
    val out = ArrayList<Float>()
    for (b in 0 until 4) {
      val blk = stereo(frames) { if (b == 0 && it == impulseAt) 1f else 0f }
      chain.process(blk, frames)
      for (i in 0 until frames) out.add(blk[2 * i])
    }
    assertEquals(AudioChain.DELAY, chain.spatialDelayFrames)
    assertEquals(impulseAt + AudioChain.DELAY, out.indexOfFirst { it > 0.5f })
  }

  @Test fun loudnessGainLowersLoudTracks() {
    val a = audio()
    val f = File(dir, "loud.flac")
    state.settings.put(AudioState.G, "measured_v1_${f.absolutePath}", "-8.00")
    a.setNormalizationMode(NormalizationMode.TRACK)
    a.onTrackChanged(f, 200_000)
    val ui = a.loudness.value
    assertTrue(ui.hasTrack)
    assertTrue("a loud track must get a negative gain: ${ui.gainDb}", ui.gainDb < 0.0)
    assertTrue(ui.source != LoudnessSource.NONE)
    player.setEq(EqSettings(enabled = false))
    val out = runBlocks(player.chain, { 0.5f }, 30)
    val expected = 0.5 * Math.pow(10.0, ui.gainDb / 20)
    assertEquals(expected, rms(out, out.size / 2).let { it }, 0.02)
    a.setNormalizationMode(NormalizationMode.OFF)
    assertEquals(0.0, a.loudness.value.gainDb, 0.0)
  }

  @Test fun capabilitiesDescribeWindows() {
    val c = audio().capabilities(playing = true)
    assertFalse(c.isVirtualizerAvailable)
    assertNotNull(c.androidVersion)
    assertTrue(c.isAudioSessionActive)
  }
}
