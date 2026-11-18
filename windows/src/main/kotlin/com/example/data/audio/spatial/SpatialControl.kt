package com.example.data.audio.spatial

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
  /** The playing track is not stereo (mono or multichannel). */
  NOT_STEREO,
  /** The effect failed while playing (or while preparing) and the music plays without it. */
  FAILED
}

/**
 * Shared state between the settings (Áudio screen) and the
 * audio processor. Builds the HRTF tables and engines off the audio thread, once per sample rate.
 */
object SpatialControl {
  private const val TAG = "SpatialControl"

  @Volatile var enabled = false; private set
  @Volatile var preset = SpatialPreset.STANDARD; private set
  /** The user's intensity, 0.2..1, applied on top of the preset's own. */
  @Volatile var userIntensity = 1.0; private set
  @Volatile private var stereoInput = true
  /**
   * The effect failed and the processor plays the dry sound. Latched: set once ([reportFailure] says
   * whether this call was the first), cleared when the user switches the effect off or the processor
   * is flushed (next track or a seek), which is when the effect is tried again.
   */
  @Volatile var failed = false; private set
  /** Values the listener set by hand on top of each preset (only the ones that differ). */
  @Volatile private var tunings: Map<SpatialPreset, Map<SpatialKnob, Double>> = emptyMap()

  private val _status = MutableStateFlow(SpatialStatus.OFF)
  val status: StateFlow<SpatialStatus> = _status.asStateFlow()

  /**
   * The Virtualizer strength (0 = off, up to 1000) for other apps' audio, which Android's own effect
   * handles when the global equalizer reaches them. Follows the preset and the intensity.
   */
  private val _otherAppsStrength = MutableStateFlow(0)
  val otherAppsStrength: StateFlow<Int> = _otherAppsStrength.asStateFlow()

  /** True when the processor should be rendering (otherwise it glides to the dry sound). */
  val active: Boolean get() = enabled && stereoInput

  /** Called (from any thread) when [active] changes; the equalizer uses it to keep its headroom right. */
  @Volatile var onActiveChanged: ((Boolean) -> Unit)? = null
  private var lastActive = false

  /**
   * Opens an asset by name (set once by the app at start). Without it, or without a usable file for the
   * sample rate, the virtualizer keeps the spherical head.
   */
  @Volatile var assetOpener: ((String) -> java.io.InputStream?)? = null

  private val worker = Executors.newSingleThreadScheduledExecutor { r ->
    Thread(r, "tarkas-spatial").apply {
      isDaemon = true
      priority = Thread.NORM_PRIORITY - 1
    }
  }
  private val releaseGeneration = java.util.concurrent.atomic.AtomicInteger(0)
  private val hrtfs = mutableMapOf<Int, SphericalHeadHrtf>()
  @Volatile private var preparingRate = 0
  /** Engines built for the processor, by sample rate. */
  private val engines = java.util.concurrent.ConcurrentHashMap<Int, SpatialEngine>()
  /** 5.1 virtualizers for the processor, by sample rate (built with the engine, from the same HRTF). */
  private val surrounds = java.util.concurrent.ConcurrentHashMap<Int, SurroundVirtualizer>()
  /** Stereo-to-five-speakers stages for the processor (the Cinema preset), by sample rate. */
  private val cinemas = java.util.concurrent.ConcurrentHashMap<Int, UpmixVirtualizer>()

  fun configure(
    enabled: Boolean, preset: SpatialPreset, intensity: Double,
    tunings: Map<SpatialPreset, Map<SpatialKnob, Double>> = this.tunings
  ) {
    val gen = releaseGeneration.incrementAndGet()
    this.tunings = tunings
    this.enabled = enabled
    this.preset = preset
    this.userIntensity = intensity.coerceIn(0.0, 1.0)
    // Switching it off and on again is how the listener tries after a failure.
    if (!enabled) failed = false
    val params = targetParams()
    // New tables are built off the UI thread; the engine keeps gliding to the right intensity.
    worker.execute {
      engines.values.forEach {
        it.setParams(params)
        it.targetIntensity = targetIntensity()
      }
    }
    update()
    if (!enabled) releaseTablesSoon(gen)
  }

  /**
   * The tables take about 16 MB per sample rate. Once the effect is off they are dropped, after the
   * engine has had time to glide back to the dry sound. Scheduled without sleeping on the worker, so
   * toggling the effect back on within 1.5 s is never blocked behind the release delay.
   */
  private fun releaseTablesSoon(gen: Int) {
    worker.schedule({
      if (!enabled && releaseGeneration.get() == gen) {
        engines.clear()
        surrounds.clear()
        cinemas.clear()
        synchronized(hrtfs) { hrtfs.clear() }
      }
    }, RELEASE_AFTER_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
  }

  /** Only the intensity changed (a slider drag): no new tables, just a new target. */
  fun setIntensity(intensity: Double) {
    userIntensity = intensity.coerceIn(0.0, 1.0)
    update()
  }

  /** Where the head points, degrees, positive to the left; 0 without head tracking. Stereo scenes only. */
  @Volatile var headYaw = 0.0; private set

  /** Head tracking turns the scene across stereo, Cinema 5.1, and 5.1 playback from any thread. */
  fun setHeadYaw(degrees: Double) {
    headYaw = degrees
    engines.values.forEach { it.liveYaw = degrees }
    cinemas.values.forEach { it.liveYaw = degrees }
    surrounds.values.forEach { it.liveYaw = degrees }
  }

  /** True only for the call that moved the effect to the failed state: the caller records that one. */
  @Synchronized
  internal fun reportFailure(): Boolean {
    if (failed) return false
    failed = true
    update()
    return true
  }

  /** A new stream (next track, seek): the effect gets another try. */
  internal fun clearFailure() {
    if (!failed) return
    failed = false
    update()
  }

  internal fun setStereoInput(stereo: Boolean) {
    stereoInput = stereo
    update()
  }

  /** A hand-set value moved: the engines take the new parameters; their tables are rebuilt only if they must. */
  fun setTuning(preset: SpatialPreset, values: Map<SpatialKnob, Double>) {
    tunings = tunings + (preset to values)
    if (preset != this.preset) return
    val params = targetParams()
    worker.execute {
      engines.values.forEach {
        it.setParams(params)
        it.targetIntensity = targetIntensity()
      }
    }
  }

  /** The preset's parameters with the listener's values applied: what the sliders show. */
  fun paramsFor(preset: SpatialPreset, values: Map<SpatialKnob, Double>): SpatialParams = preset.params.tuned(values)

  private fun targetParams(): SpatialParams =
    paramsFor(preset, tunings[preset].orEmpty()).let { it.copy(intensity = it.intensity * userIntensity) }

  /**
   * What the stereo engines glide to now: the preset's intensity when active, 0 (dry) otherwise, and also 0
   * while the Cinema preset is chosen (that preset is the other path's, see [cinemaTarget]).
   */
  private fun targetIntensity(): Double = if (active && !preset.virtual51) targetParams().intensity else 0.0

  /** What the Cinema stage glides to: the user's intensity (a blend with the original) while it is chosen and active. */
  private fun cinemaTarget(): Double = if (active && preset.virtual51) userIntensity else 0.0

  private fun update() {
    val nowActive = active
    if (nowActive != lastActive) {
      lastActive = nowActive
      onActiveChanged?.invoke(nowActive)
    }
    val target = targetIntensity()
    engines.values.forEach { it.targetIntensity = target }
    cinemas.values.forEach { it.targetIntensity = cinemaTarget() }
    // A 5.1 source has no intensity to scale: it is the virtualizer or the plain downmix.
    surrounds.values.forEach { it.targetIntensity = if (active) 1.0 else 0.0 }
    _otherAppsStrength.value = if (enabled) (preset.virtualizerStrength * userIntensity).toInt().coerceIn(1, 1000) else 0
    _status.value = when {
      !enabled -> SpatialStatus.OFF
      failed -> SpatialStatus.FAILED
      !stereoInput -> SpatialStatus.NOT_STEREO
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

  /** The 5.1 virtualizer for [sampleRate], or null while it is being built (the processor plays the downmix meanwhile). */
  fun surroundFor(sampleRate: Int): SurroundVirtualizer? {
    surrounds[sampleRate]?.let { return it }
    if (enabled && preparingRate != sampleRate) prepare(sampleRate)
    return null
  }

  /** The Cinema stage for [sampleRate], or null while it is being built (the processor plays the dry sound meanwhile). */
  fun cinemaFor(sampleRate: Int): UpmixVirtualizer? {
    cinemas[sampleRate]?.let { return it }
    if (enabled && preparingRate != sampleRate) prepare(sampleRate)
    return null
  }

  private fun prepare(sampleRate: Int) {
    preparingRate = sampleRate
    update()
    worker.execute {
      try {
        val started = System.nanoTime()
        val hrtf = synchronized(hrtfs) {
          hrtfs.getOrPut(sampleRate) { SphericalHeadHrtf(sampleRate, accurateDiffraction = true) }
        }
        val engine = SpatialEngine(sampleRate, hrtf, targetParams())
        engine.targetIntensity = targetIntensity()
        engine.liveYaw = headYaw
        engines[sampleRate] = engine
        // Studio filters: KU100 measured HRTF (with L/R symmetry, Gunnarsson phase regularization & Grathwohl notch
        // protection) + ISO 9613-1 air absorption room, with accurateDiffraction fallback for unsupported sample rates.
        val filters = SurroundFilters.studio(sampleRate, hrtf, assetOpener, { System.err.println("$TAG: $it") })
        val cinemaFilters = SurroundFilters.studio(sampleRate, hrtf, assetOpener, { System.err.println("$TAG: $it") }, channels = 8)
        val surround = SurroundVirtualizer(filters, headHrtf = hrtf)
        surround.targetIntensity = if (active) 1.0 else 0.0
        surround.liveYaw = headYaw
        surrounds[sampleRate] = surround
        val cinema = UpmixVirtualizer(
          sampleRate = sampleRate,
          filters = cinemaFilters,
          headHrtf = hrtf,
          studioRoomTail = RoomParams.APPROVED_LEVEL,
          params = UpmixParams.CINEMA
        )
        cinema.targetIntensity = cinemaTarget()
        cinema.liveYaw = headYaw
        cinemas[sampleRate] = cinema
        // Only the rate in use is kept: a library mixing 44.1, 48 and 96 kHz would otherwise hold them all.
        engines.keys.filter { it != sampleRate }.forEach { engines.remove(it) }
        surrounds.keys.filter { it != sampleRate }.forEach { surrounds.remove(it) }
        cinemas.keys.filter { it != sampleRate }.forEach { cinemas.remove(it) }
        synchronized(hrtfs) { hrtfs.keys.retainAll(setOf(sampleRate)) }
        System.err.println("$TAG: " + "Spatial engine ready for $sampleRate Hz in ${(System.nanoTime() - started) / 1_000_000} ms")
      } catch (e: Throwable) {
        // Out of memory or anything else: the effect stays off, the music keeps playing.
        System.err.println("$TAG: Could not prepare the spatial engine: $e")
        reportFailure()
      } finally {
        if (preparingRate == sampleRate) preparingRate = 0
        update()
      }
    }
  }

  private const val RELEASE_AFTER_MS = 1500L
}
