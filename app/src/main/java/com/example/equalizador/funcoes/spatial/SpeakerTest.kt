package com.example.data.audio.spatial

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.os.Build
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Channel test of the virtual 5.1 room, like the channel test of a home theatre: four noise bursts that play
 * in one speaker only. The burst goes through the same [SurroundVirtualizer] and the same [SurroundFilters]
 * (measured HRTF and room) that render the Cinema preset, so the listener hears the speaker where the stage
 * draws it, not a pan approximation.
 */
object SpeakerTest {
  const val SAMPLE_RATE = 48_000
  /** Channel indices in the 7.1 order the stage uses (L R C LFE Ls Rs SBL SBR). */
  const val L = 0; const val R = 1; const val C = 2; const val LFE = 3; const val LS = 4; const val RS = 5
  const val SBL = 6; const val SBR = 7
  /** Interleaved channels of a test signal: the 7.1 room. */
  const val CHANNELS = 8

  private const val BURSTS = 4
  private const val BURST_S = 0.40
  private const val GAP_S = 0.20
  private const val TAIL_S = 0.70
  private const val FADE_S = 0.012
  /** Rendered level: same RMS for every speaker, so the test compares position, not loudness. */
  const val TARGET_RMS = 0.10
  const val PEAK_LIMIT = 0.70

  /**
   * The test signal of [channel] as 8 interleaved channels, only [channel] carrying it. The speakers get a
   * band-limited noise (250 Hz to 6 kHz: enough cues to place a source in front or behind); the LFE gets
   * a noise below 100 Hz.
   */
  fun burst(channel: Int, sampleRate: Int = SAMPLE_RATE, loudspeaker: Boolean = false): FloatArray {
    val burstN = (BURST_S * sampleRate).toInt()
    val gapN = (GAP_S * sampleRate).toInt()
    val tailN = (TAIL_S * sampleRate).toInt()
    val total = BURSTS * burstN + (BURSTS - 1) * gapN + tailN
    val out = FloatArray(total * CHANNELS)
    val rng = java.util.Random(7L + channel)
    // On a loudspeaker the bass band moves up to what a phone can play and the back pair gets a darker band.
    val (lo, hi) = when {
      channel == LFE -> if (loudspeaker) 90.0 to 300.0 else 25.0 to 100.0
      loudspeaker && (channel == SBL || channel == SBR) -> 250.0 to 2500.0
      else -> 250.0 to 6000.0
    }
    // One-pole high-pass and low-pass pairs, enough to confine the band; the exact shape does not matter.
    val aHp = kotlin.math.exp(-2 * PI * lo / sampleRate)
    val aLp = kotlin.math.exp(-2 * PI * hi / sampleRate)
    val fade = (FADE_S * sampleRate).toInt()
    var lp1 = 0.0; var lp2 = 0.0; var hp1 = 0.0
    for (b in 0 until BURSTS) {
      val start = b * (burstN + gapN)
      for (n in 0 until burstN) {
        val white = rng.nextDouble() * 2 - 1
        lp1 = (1 - aLp) * white + aLp * lp1
        lp2 = (1 - aLp) * lp1 + aLp * lp2
        hp1 = (1 - aHp) * lp2 + aHp * hp1
        val band = lp2 - hp1
        val env = when {
          n < fade -> 0.5 - 0.5 * kotlin.math.cos(PI * n / fade)
          n >= burstN - fade -> 0.5 - 0.5 * kotlin.math.cos(PI * (burstN - 1 - n) / fade)
          else -> 1.0
        }
        out[CHANNELS * (start + n) + channel] = (band * env).toFloat()
      }
    }
    return out
  }

  /**
   * Renders [channel] through the virtualizer to interleaved stereo at a fixed level. [filters] and [hrtf]
   * are the ones the Cinema stage uses.
   */
  fun render(channel: Int, filters: SurroundFilters, hrtf: SphericalHeadHrtf?): FloatArray {
    require(channel in 0 until CHANNELS) { "channel $channel" }
    require(filters.channels == CHANNELS) { "the speaker test needs the 7.1 filters" }
    val input = burst(channel, filters.sampleRate)
    val frames = input.size / CHANNELS
    val virt = SurroundVirtualizer(filters, headHrtf = hrtf)
    virt.targetIntensity = 1.0
    virt.reset(1.0)
    // Flush: the stage delays its output, so push silence behind the signal.
    val padded = FloatArray(input.size + CHANNELS * (2 * SurroundVirtualizer.LATENCY + 2048))
    System.arraycopy(input, 0, padded, 0, input.size)
    val pf = padded.size / CHANNELS
    val stereo = FloatArray(2 * (pf + 4096))
    var written = 0
    var at = 0
    val chunk = 1024
    val scratch = FloatArray(2 * (chunk + 4096))
    while (at < pf) {
      val n = minOf(chunk, pf - at)
      val w = virt.process(padded, CHANNELS * at, n, scratch)
      System.arraycopy(scratch, 0, stereo, 2 * written, 2 * w)
      written += w
      at += n
    }
    val lat = SurroundVirtualizer.LATENCY
    val keep = minOf(frames + lat / 2, written - lat)
    val out = FloatArray(2 * max(keep, 0))
    if (keep > 0) System.arraycopy(stereo, 2 * lat, out, 0, 2 * keep)
    return normalize(out)
  }

  /** Azimuth of each channel in degrees, 0 front, positive to the right: the stage's own layout. */
  private val AZIMUTH = doubleArrayOf(-30.0, 30.0, 0.0, 0.0, -90.0, 90.0, -150.0, 150.0)

  /**
   * The test for an output with no headphones: the same burst panned (constant power) to the side the
   * speaker stands on. A loudspeaker cannot place a source behind you, so front, side and back are told
   * apart by pan and tone: sides pan hard, the back pair is darker, the LFE is centred and bass-heavy.
   */
  fun renderLoudspeaker(channel: Int, sampleRate: Int = SAMPLE_RATE): FloatArray {
    require(channel in 0 until CHANNELS) { "channel $channel" }
    val src = burst(channel, sampleRate, loudspeaker = true)
    val frames = src.size / CHANNELS
    val pan = if (channel == LFE) 0.0 else sin(Math.toRadians(AZIMUTH[channel])).let { if (channel == SBL || channel == SBR) it * 0.6 else it }
    val theta = (pan + 1.0) * PI / 4.0
    val gl = kotlin.math.cos(theta).toFloat(); val gr = sin(theta).toFloat()
    val out = FloatArray(2 * frames)
    for (i in 0 until frames) {
      val v = src[CHANNELS * i + channel]
      out[2 * i] = v * gl
      out[2 * i + 1] = v * gr
    }
    return normalize(out)
  }

  /** Same RMS for every speaker, peak under [PEAK_LIMIT], never clipping. */
  internal fun normalize(x: FloatArray): FloatArray {
    var sum = 0.0; var count = 0; var peak = 0.0
    for (v in x) {
      val a = abs(v.toDouble())
      if (a > peak) peak = a
      if (a > 1e-4) { sum += a * a; count++ }
    }
    if (count == 0 || peak == 0.0) return x
    val rms = sqrt(sum / count)
    var g = TARGET_RMS / rms
    if (peak * g > PEAK_LIMIT) g = PEAK_LIMIT / peak
    return FloatArray(x.size) { (x[it] * g).toFloat() }
  }

  /** Left and right RMS of an interleaved stereo render, for the position checks. */
  fun earLevels(stereo: FloatArray): Pair<Double, Double> {
    var l = 0.0; var r = 0.0
    for (i in 0 until stereo.size / 2) {
      l += stereo[2 * i].toDouble() * stereo[2 * i]
      r += stereo[2 * i + 1].toDouble() * stereo[2 * i + 1]
    }
    return l to r
  }
}

/**
 * Plays one speaker test at a time. Starting another stops the one before it (its [AudioTrack] is released
 * and its highlight cleared); a generation counter makes a late render of a replaced test drop its result.
 */
object SpeakerTestPlayer {
  private const val TAG = "SpeakerTest"
  private val worker = Executors.newSingleThreadExecutor { r ->
    Thread(r, "tarkas-speaker-test").apply { isDaemon = true }
  }
  private val generation = java.util.concurrent.atomic.AtomicInteger(0)
  private val _active = MutableStateFlow<Int?>(null)
  /** The channel being tested (a [SpeakerTest] index), or null. */
  val active: StateFlow<Int?> = _active.asStateFlow()
  @Volatile private var track: AudioTrack? = null
  private val rendered = java.util.concurrent.ConcurrentHashMap<Int, FloatArray>()
  private const val LOUDSPEAKER_KEY = 100

  private fun isHeadphoneType(type: Int) = when (type) {
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_BLE_HEADSET -> true
    else -> false
  }

  /**
   * True when the sound of this test goes to a headset or headphones, where the binaural render holds. On
   * Android 13+ the route the system picks for these attributes is asked (the active one, a phone speaker
   * when nothing is plugged); before that the system always prefers a connected headset over the speaker,
   * so the connected outputs decide.
   */
  private fun onHeadphones(context: Context): Boolean {
    val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return try {
      if (Build.VERSION.SDK_INT >= 33) {
        am.getAudioDevicesForAttributes(TEST_ATTRIBUTES).let { routes -> routes.isNotEmpty() && routes.all { isHeadphoneType(it.type) } }
      } else {
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isHeadphoneType(it.type) }
      }
    } catch (_: Throwable) { false }
  }

  private val TEST_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

  @Volatile private var focusRequest: AudioFocusRequest? = null
  @Volatile private var legacyFocus: AudioManager.OnAudioFocusChangeListener? = null
  @Volatile private var audioManager: AudioManager? = null

  /** Takes a short audio focus so the music pauses during the test and resumes after it (API 26+ request, older call before). */
  @Suppress("DEPRECATION")
  private fun takeFocus(context: Context) {
    val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    audioManager = am
    if (focusRequest != null || legacyFocus != null) return
    try {
      if (Build.VERSION.SDK_INT >= 26) {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
          .setAudioAttributes(TEST_ATTRIBUTES)
          .setOnAudioFocusChangeListener { }
          .build()
        if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest = req
      } else {
        val l = AudioManager.OnAudioFocusChangeListener { }
        if (am.requestAudioFocus(l, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) legacyFocus = l
      }
    } catch (t: Throwable) { Log.w(TAG, "audio focus", t) }
  }

  @Suppress("DEPRECATION")
  private fun giveFocusBack() {
    try {
      val req = focusRequest
      if (req != null && Build.VERSION.SDK_INT >= 26) { focusRequest = null; audioManager?.abandonAudioFocusRequest(req) }
      val l = legacyFocus
      if (l != null) { legacyFocus = null; audioManager?.abandonAudioFocus(l) }
    } catch (_: Throwable) {}
  }

  fun play(context: Context, channel: Int) {
    takeFocus(context)
    val headphones = onHeadphones(context)
    val gen = generation.incrementAndGet()
    _active.value = channel
    worker.execute {
      releaseTrack()
      if (generation.get() != gen) return@execute
      try {
        val pcm = if (headphones) rendered[channel] ?: SpatialControl.renderSpeakerTests()?.let { rendered.putAll(it); it[channel] }
          else rendered.getOrPut(LOUDSPEAKER_KEY + channel) { SpeakerTest.renderLoudspeaker(channel) }
        if (pcm == null || generation.get() != gen) { finish(gen); return@execute }
        val t = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
          )
          .setAudioFormat(
            AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
              .setSampleRate(SpeakerTest.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
          )
          .setTransferMode(AudioTrack.MODE_STATIC)
          .setBufferSizeInBytes(pcm.size * 4)
          .build()
        val frames = pcm.size / 2
        t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
        t.setNotificationMarkerPosition(frames - 1)
        t.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
          override fun onMarkerReached(track: AudioTrack?) { finish(gen) }
          override fun onPeriodicNotification(track: AudioTrack?) {}
        })
        if (generation.get() != gen) { t.release(); return@execute }
        track = t
        t.play()
      } catch (e: Throwable) {
        Log.w(TAG, "Speaker test failed", e)
        finish(gen)
      }
    }
  }

  /** Stops the running test, if any. */
  fun stop() {
    generation.incrementAndGet()
    _active.value = null
    worker.execute { releaseTrack(); giveFocusBack() }
  }

  /** Frees the rendered tests (they are rebuilt on the next use). */
  fun clear() { rendered.clear() }

  private fun finish(gen: Int) {
    if (generation.get() == gen) { _active.value = null; giveFocusBack() }
  }

  private fun releaseTrack() {
    val t = track ?: return
    track = null
    try { t.pause(); t.flush() } catch (_: Throwable) {}
    try { t.release() } catch (_: Throwable) {}
  }
}
