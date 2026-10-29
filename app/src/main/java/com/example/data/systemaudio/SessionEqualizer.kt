package com.example.data.systemaudio

import android.media.AudioFormat
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqSettings
import kotlin.math.pow

/**
 * The equalizer curve applied to other apps' audio sessions, with Android's own effects
 * (DynamicsProcessing on Android 9+, the 5-band Equalizer before). The app's parametric curve is
 * sampled at 31 third-octave bands, so Q, shelves and pass filters carry over; the limiter carries
 * over too. Balance and stereo width need the app's own processor and apply only to its playback.
 */
class SessionEqualizer {
  private val effects = mutableMapOf<Int, AudioEffect>()
  private val virtualizers = mutableMapOf<Int, Virtualizer>()
  private var settings = EqSettings()
  private var spatialStrength = 0

  /** True once a device refused the Virtualizer: there is no spatial for other apps here. */
  var spatialUnsupported = false; private set
  /** Sessions that carry the spatial effect right now. */
  val spatialSessions: Int get() = virtualizers.size

  val sessions: Set<Int> get() = effects.keys

  fun attach(sessionId: Int): Boolean {
    if (sessionId in effects) return true
    val effect = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) newDynamics(sessionId) else Equalizer(PRIORITY, sessionId)
    } catch (e: Exception) {
      // The session ended between reading it and attaching, or the device refuses this session.
      Log.w(TAG, "Cannot attach to session $sessionId: ${e.javaClass.simpleName}")
      return false
    }
    effects[sessionId] = effect
    apply(effect)
    applySpatial(sessionId)
    return true
  }

  fun detach(sessionId: Int) {
    effects.remove(sessionId)?.let { runCatching { it.release() } }
    virtualizers.remove(sessionId)?.let { runCatching { it.release() } }
  }

  /** Keeps only [alive] attached: sessions that ended are released. */
  fun retainOnly(alive: Set<Int>) {
    (effects.keys - alive).forEach(::detach)
  }

  fun update(newSettings: EqSettings) {
    settings = newSettings
    effects.values.forEach(::apply)
  }

  /** [strength] 0 turns the spatial effect off on every session; 1..1000 is the Virtualizer strength. */
  fun setSpatial(strength: Int) {
    spatialStrength = strength.coerceIn(0, 1000)
    effects.keys.toList().forEach(::applySpatial)
  }

  private fun applySpatial(sessionId: Int) {
    if (spatialStrength == 0) {
      virtualizers.remove(sessionId)?.let { runCatching { it.release() } }
      return
    }
    try {
      val virt = virtualizers.getOrPut(sessionId) { Virtualizer(PRIORITY, sessionId) }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Binaural where the device can; otherwise the effect picks its own mode.
        runCatching {
          if (virt.canVirtualize(AudioFormat.CHANNEL_OUT_STEREO, Virtualizer.VIRTUALIZATION_MODE_BINAURAL)) {
            virt.forceVirtualizationMode(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
          }
        }
      }
      if (virt.strengthSupported) virt.setStrength(spatialStrength.toShort())
      virt.enabled = true
    } catch (e: Exception) {
      Log.w(TAG, "No spatial effect for session $sessionId: ${e.javaClass.simpleName}")
      virtualizers.remove(sessionId)?.let { runCatching { it.release() } }
      spatialUnsupported = true
    }
  }

  fun releaseAll() {
    effects.values.forEach { runCatching { it.release() } }
    effects.clear()
    virtualizers.values.forEach { runCatching { it.release() } }
    virtualizers.clear()
  }

  private fun apply(effect: AudioEffect) {
    try {
      when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && effect is DynamicsProcessing -> applyDynamics(effect)
        effect is Equalizer -> applyLegacy(effect)
      }
      effect.enabled = settings.enabled
    } catch (e: Exception) {
      Log.w(TAG, "Could not update an effect: ${e.javaClass.simpleName}")
    }
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
  private fun newDynamics(sessionId: Int): DynamicsProcessing {
    val config = DynamicsProcessing.Config.Builder(
      DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
      2,
      true, BAND_CENTERS.size,
      false, 0,
      false, 0,
      true
    ).build()
    return DynamicsProcessing(PRIORITY, sessionId, config)
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
  private fun applyDynamics(dp: DynamicsProcessing) {
    BAND_CENTERS.forEachIndexed { i, center ->
      val gain = EqDsp.responseDb(settings, center).toFloat().coerceIn(-MAX_DB, MAX_DB)
      // A band's cutoff is its upper edge: half a third-octave above the centre.
      dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, (center * EDGE).toFloat(), gain))
    }
    dp.setLimiterAllChannelsTo(
      DynamicsProcessing.Limiter(
        true, settings.limiterEnabled, 0,
        1f, 60f, 10f, settings.limiterCeilingDb, 0f
      )
    )
  }

  private fun applyLegacy(eq: Equalizer) {
    val range = eq.bandLevelRange
    for (band in 0 until eq.numberOfBands) {
      val center = eq.getCenterFreq(band.toShort()) / 1000.0
      val milliBel = (EqDsp.responseDb(settings, center) * 100).toInt()
        .coerceIn(range[0].toInt(), range[1].toInt())
      eq.setBandLevel(band.toShort(), milliBel.toShort())
    }
  }

  private companion object {
    const val TAG = "SessionEqualizer"
    /** Above normal, so this curve wins over a player's own built-in equalizer. */
    const val PRIORITY = 1000
    const val MAX_DB = 24f
    val EDGE = 2.0.pow(1.0 / 6.0)
    /** ISO third-octave centres, 20 Hz to 20 kHz. */
    val BAND_CENTERS = listOf(
      20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0,
      630.0, 800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0,
      10000.0, 12500.0, 16000.0, 20000.0
    )
  }
}
