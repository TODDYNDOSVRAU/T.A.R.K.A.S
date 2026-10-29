package com.example.data.audio.spatial

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/** Why Tarkas Spatial is or is not processing right now, for the Áudio screen. */
enum class SpatialStatus {
  /** Turned off by the user. */
  OFF,
  /** Building the HRTF tables for this sample rate (once, about a second). */
  PREPARING,
  /** Processing the music. */
  ACTIVE,
  /** On, but the sound goes to the phone's speaker: binaural only works on headphones. */
  NEEDS_HEADPHONES,
  /** On, but Android's own spatial audio already processes this output; not done twice. */
  SYSTEM_SPATIAL,
  /** The playing track is not stereo (mono or multichannel). */
  NOT_STEREO
}

/**
 * Shared state between the settings (Áudio screen), the output route (headphones or speaker) and the
 * audio processor. Builds the HRTF tables and engines off the audio thread, once per sample rate.
 */
object SpatialControl {
  private const val TAG = "SpatialControl"

  @Volatile var enabled = false; private set
  @Volatile var preset = SpatialPreset.STANDARD; private set
  /** The user's intensity, 0.2..1, applied on top of the preset's own. */
  @Volatile var userIntensity = 1.0; private set
  @Volatile private var headphones = false
  @Volatile private var systemSpatial = false
  @Volatile private var stereoInput = true

  private val _status = MutableStateFlow(SpatialStatus.OFF)
  val status: StateFlow<SpatialStatus> = _status.asStateFlow()

  /** True when the processor should be rendering (otherwise it glides to the dry sound). */
  val active: Boolean get() = enabled && headphones && !systemSpatial && stereoInput

  private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "tarkas-spatial").apply { priority = Thread.NORM_PRIORITY - 1 } }
  private val hrtfs = mutableMapOf<Int, SphericalHeadHrtf>()
  @Volatile private var preparingRate = 0
  /** Engines built for the processor, by sample rate. */
  private val engines = java.util.concurrent.ConcurrentHashMap<Int, SpatialEngine>()

  fun configure(enabled: Boolean, preset: SpatialPreset, intensity: Double) {
    this.enabled = enabled
    this.preset = preset
    this.userIntensity = intensity.coerceIn(0.0, 1.0)
    val params = targetParams()
    // New tables are built off the UI thread; the engine keeps gliding to the right intensity.
    worker.execute {
      engines.values.forEach {
        it.setParams(params)
        it.targetIntensity = targetIntensity()
      }
    }
    update()
  }

  /** Only the intensity changed (a slider drag): no new tables, just a new target. */
  fun setIntensity(intensity: Double) {
    userIntensity = intensity.coerceIn(0.0, 1.0)
    update()
  }

  fun setRoute(headphones: Boolean, systemSpatial: Boolean) {
    this.headphones = headphones
    this.systemSpatial = systemSpatial
    update()
  }

  internal fun setStereoInput(stereo: Boolean) {
    stereoInput = stereo
    update()
  }

  private fun targetParams(): SpatialParams = preset.params.copy(intensity = preset.params.intensity * userIntensity)

  /** What the engines glide to now: the preset's intensity when active, 0 (dry) otherwise. */
  private fun targetIntensity(): Double = if (active) targetParams().intensity else 0.0

  private fun update() {
    val target = targetIntensity()
    engines.values.forEach { it.targetIntensity = target }
    _status.value = when {
      !enabled -> SpatialStatus.OFF
      !stereoInput -> SpatialStatus.NOT_STEREO
      systemSpatial -> SpatialStatus.SYSTEM_SPATIAL
      !headphones -> SpatialStatus.NEEDS_HEADPHONES
      preparingRate != 0 -> SpatialStatus.PREPARING
      else -> SpatialStatus.ACTIVE
    }
  }

  /**
   * The engine for [sampleRate], or null while it is being built (the processor plays the dry sound
   * meanwhile). Never blocks: the tables are built on a background thread the first time.
   */
  fun engineFor(sampleRate: Int): SpatialEngine? {
    engines[sampleRate]?.let { return it }
    if (enabled && preparingRate != sampleRate) prepare(sampleRate)
    return null
  }

  private fun prepare(sampleRate: Int) {
    preparingRate = sampleRate
    update()
    worker.execute {
      try {
        val started = System.nanoTime()
        val hrtf = synchronized(hrtfs) { hrtfs.getOrPut(sampleRate) { SphericalHeadHrtf(sampleRate) } }
        val engine = SpatialEngine(sampleRate, hrtf, targetParams())
        engine.targetIntensity = targetIntensity()
        engines[sampleRate] = engine
        Log.i(TAG, "Spatial engine ready for $sampleRate Hz in ${(System.nanoTime() - started) / 1_000_000} ms")
      } catch (e: Throwable) {
        // Out of memory or anything else: the effect stays off, the music keeps playing.
        Log.w(TAG, "Could not prepare the spatial engine", e)
      } finally {
        if (preparingRate == sampleRate) preparingRate = 0
        update()
      }
    }
  }
}
