package com.example.data.systemaudio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import com.example.data.audio.dsp.EqDsp
import com.example.data.model.EqSettings
import kotlin.math.abs

/**
 * The equalizer curve applied to other apps' audio sessions, with Android's own effects
 * (DynamicsProcessing on Android 9+, the 5-band Equalizer before). The app's parametric curve is
 * laid onto DynamicsProcessing's FFT bins ([DpCurve]): a bin per band in the bass and fractions of an
 * octave above, so Q, shelves and pass filters carry over; the limiter carries over too. The preamp
 * and the balance go in the effect's input gain per channel. Stereo width needs the app's own
 * processor and applies only to its playback.
 */
class SessionEqualizer {
  private val effects = mutableMapOf<Int, AudioEffect>()
  /** The band gains last sent to each DynamicsProcessing, so an update sends only the bands that moved. */
  private val sent = mutableMapOf<Int, FloatArray>()
  /** The curve for the current settings, laid onto the bands; built once per update for every session. */
  private var curve: FloatArray? = null
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
    apply(sessionId, effect)
    applySpatial(sessionId)
    return true
  }

  fun detach(sessionId: Int) {
    sent.remove(sessionId)
    effects.remove(sessionId)?.let { runCatching { it.release() } }
    virtualizers.remove(sessionId)?.let { runCatching { it.release() } }
  }

  /** Keeps only [alive] attached: sessions that ended are released. */
  fun retainOnly(alive: Set<Int>) {
    (effects.keys - alive).forEach(::detach)
  }

  fun update(newSettings: EqSettings) {
    settings = newSettings
    curve = null
    effects.forEach { (id, effect) -> apply(id, effect) }
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
    sent.clear()
    virtualizers.values.forEach { runCatching { it.release() } }
    virtualizers.clear()
  }

  private fun apply(sessionId: Int, effect: AudioEffect) {
    try {
      when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && effect is DynamicsProcessing -> applyDynamics(sessionId, effect)
        effect is Equalizer -> applyLegacy(effect)
      }
      effect.enabled = settings.enabled
    } catch (e: Exception) {
      Log.w(TAG, "Could not update an effect: ${e.javaClass.simpleName}")
    }
  }

  /**
   * A DynamicsProcessing with the band layout of [DpCurve]. The first one also finds how many bands the
   * device takes: some (Android 15 AIDL effects) accept fewer than asked, which shows when the last band
   * cannot be read back. The layout then gets coarser, and every later effect uses it.
   */
  @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
  private fun newDynamics(sessionId: Int): DynamicsProcessing {
    var lastError: Exception? = null
    for (limit in BAND_LIMITS.filter { it <= maxBands }) {
      val stops = DpCurve.layout(sampleRate, block, limit)
      val config = DynamicsProcessing.Config.Builder(
        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
        2,
        true, stops.size,
        false, 0,
        false, 0,
        true
      ).setPreferredFrameDuration(DpCurve.FRAME_MS).build()
      val dp = try {
        DynamicsProcessing(PRIORITY, sessionId, config)
      } catch (e: Exception) {
        lastError = e
        continue
      }
      val accepted = runCatching { dp.getPreEqBandByChannelIndex(0, stops.size - 1) }.isSuccess
      if (accepted) {
        if (limit != maxBands || layout == null) Log.i(TAG, "Other apps' curve: ${stops.size} bands, $block-sample blocks at $sampleRate Hz")
        maxBands = limit
        layout = stops
        return dp
      }
      runCatching { dp.release() }
      lastError = IllegalStateException("${stops.size} bands refused")
    }
    throw lastError ?: IllegalStateException("No DynamicsProcessing")
  }

  /**
   * The current curve on the current layout, without the preamp (it goes in the input gain): computed
   * once per settings change, for all sessions.
   */
  private fun curveGains(stops: IntArray): FloatArray {
    curve?.takeIf { it.size == stops.size }?.let { return it }
    val preamp = EqDsp.totalPreampDb(settings).toDouble()
    return DpCurve.bandGains(stops, sampleRate, block) { f -> EqDsp.responseDb(settings, f, sampleRate) - preamp }
      .also { g -> for (i in g.indices) g[i] = g[i].coerceIn(-MAX_DB, MAX_DB) }
      .also { curve = it }
  }

  @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
  private fun applyDynamics(sessionId: Int, dp: DynamicsProcessing) {
    val stops = layout ?: return
    val gains = curveGains(stops)
    val input = DpCurve.inputGainsDb(EqDsp.totalPreampDb(settings), settings.balance)
    dp.setInputGainbyChannel(0, input[0])
    dp.setInputGainbyChannel(1, input[1])
    val last = sent[sessionId]
    for (i in stops.indices) {
      // During a drag most bands do not move: only the ones that changed are sent (a call each).
      if (last != null && abs(last[i] - gains[i]) < RESEND_DB) continue
      dp.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, DpCurve.cutoffHz(stops[i], sampleRate, block), gains[i]))
    }
    sent[sessionId] = gains.copyOf()
    // Close to a brick wall (ratio 50: 6 dB over the ceiling comes out 0.12 dB over), fast to let go.
    dp.setLimiterAllChannelsTo(
      DynamicsProcessing.Limiter(
        true, settings.limiterEnabled, 0,
        1f, 75f, 50f, settings.limiterCeilingDb, 0f
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
    /** A band is sent again only when its gain moved at least this much. */
    const val RESEND_DB = 0.05f
    /** Band counts tried, most first. 256 is beyond what the finest layout needs at 48 kHz. */
    val BAND_LIMITS = intArrayOf(256, 128, 64, 32)

    /** The rate effects run at (the output mixer's); the bins depend on it. */
    val sampleRate: Int = runCatching { AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) }
      .getOrDefault(48000).takeIf { it in 8000..384000 } ?: 48000
    val block: Int = DpCurve.blockSize(sampleRate)
    /** What the device took; found on the first effect and kept for the process. */
    @Volatile var maxBands = BAND_LIMITS.first()
    @Volatile var layout: IntArray? = null
  }
}
