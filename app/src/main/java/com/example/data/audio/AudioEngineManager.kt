package com.example.data.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import android.media.audiofx.AudioEffect
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.data.audio.dsp.EqDsp
import com.example.data.audio.dsp.ParametricEqAudioProcessor
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Plays tracks with ExoPlayer and runs every sample through our own processing chain ([EqDsp]):
 * parametric bands with adjustable Q, bass/treble shelves, preamp, stereo width, balance and a
 * limiter. Android's built-in Equalizer effect has fixed bands and no Q, so it is no longer used.
 */
@OptIn(UnstableApi::class)
class AudioEngineManager(private val context: Context) {
  private val TAG = "AudioEngineManager"

  private companion object {
    // Volume while another app needs a moment of the audio focus (a navigation prompt, for example).
    const val DUCK_VOLUME = 0.25f
    /** How long the focus is kept after a track ends or stops, for what comes next. */
    const val FOCUS_HOLD_MS = 4000L

  }
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private val eqDsp = EqDsp()
  private val eqProcessor = ParametricEqAudioProcessor(eqDsp)
  // Tarkas Spatial comes first: it turns stereo into binaural, then the equalizer and the fone
  // correction shape the result.
  private val spatialProcessor = com.example.data.audio.spatial.SpatialAudioProcessor()

  private var player: ExoPlayer? = null
  private var audioSessionId = 0
  private var virtualizer: Virtualizer? = null
  private var visualizer: Visualizer? = null
  private var isRealFftActive = false
  private var effectsAttached = false

  // Set by playTrack; consumed when the new item is ready, so focus is asked only once per track.
  private var pendingStart = false
  private var completionCallback: () -> Unit = {}
  /** Called when the track cannot play; true when the cause is the network. */
  private var errorCallback: (network: Boolean) -> Unit = {}

  private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
  private var progressJob: Job? = null

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _currentPositionMs = MutableStateFlow(0L)
  val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

  private val _durationMs = MutableStateFlow(0L)
  val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

  private val _eqSettings = MutableStateFlow(EqSettings())
  val eqSettings: StateFlow<EqSettings> = _eqSettings.asStateFlow()

  private val _capabilities = MutableStateFlow(detectCapabilities(0))
  val capabilities: StateFlow<AudioCapabilities> = _capabilities.asStateFlow()

  private val _spectrumBars = MutableStateFlow<List<Float>>(List(15) { 0.05f })
  val spectrumBars: StateFlow<List<Float>> = _spectrumBars.asStateFlow()

  init {
    updateCapabilities()
  }

  // --- audio focus, noisy-output handling ---

  private val playbackAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

  private val focusPolicy = AudioFocusPolicy()
  private var focusRequest: AudioFocusRequest? = null

  private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
    // Focus callbacks can arrive on any thread; the player is driven from the main one.
    coroutineScope.launch { handleFocusChange(change) }
  }

  private val noisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      // Headphones unplugged or Bluetooth disconnected: stop blasting the speaker.
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && _isPlaying.value) {
        pauseByUser()
      }
    }
  }
  private var noisyReceiverRegistered = false

  private fun requestFocus(): Boolean {
    focusRelease?.cancel()
    focusRelease = null
    val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(playbackAttributes)
        .setOnAudioFocusChangeListener(focusListener)
        .build()
      focusRequest = request
      audioManager.requestAudioFocus(request)
    } else {
      @Suppress("DEPRECATION")
      audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }
    val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    if (granted) focusPolicy.reset()
    return granted
  }

  /** Pending release of the focus after a track ended; a new track that starts in time keeps it. */
  private var focusRelease: Job? = null

  /**
   * Keeps the focus for a few seconds after a track ends. Giving it back at once lets a paused app (a
   * podcast, a video) resume over the narration or the next track that is about to start.
   */
  private fun releaseFocusSoon() {
    focusRelease?.cancel()
    focusRelease = coroutineScope.launch {
      delay(FOCUS_HOLD_MS)
      if (!_isPlaying.value && !pendingStart) abandonFocus()
    }
  }

  private fun abandonFocus() {
    focusRelease?.cancel()
    focusRelease = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    } else {
      @Suppress("DEPRECATION")
      audioManager.abandonAudioFocus(focusListener)
    }
    focusRequest = null
    focusPolicy.reset()
  }

  private fun registerNoisyReceiver() {
    if (noisyReceiverRegistered) return
    ContextCompat.registerReceiver(
      context,
      noisyReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
    noisyReceiverRegistered = true
  }

  private fun unregisterNoisyReceiver() {
    if (!noisyReceiverRegistered) return
    try {
      context.unregisterReceiver(noisyReceiver)
    } catch (_: Exception) {
    }
    noisyReceiverRegistered = false
  }

  private fun handleFocusChange(change: Int) {
    val p = player ?: return
    try {
      when (focusPolicy.onChange(change, _isPlaying.value)) {
        AudioFocusPolicy.Action.PAUSE_AND_ABANDON -> {
          p.pause()
          _isPlaying.value = false
          abandonFocus()
          unregisterNoisyReceiver()
        }
        AudioFocusPolicy.Action.PAUSE -> {
          p.pause()
          _isPlaying.value = false
        }
        AudioFocusPolicy.Action.DUCK -> p.volume = DUCK_VOLUME
        AudioFocusPolicy.Action.RESUME -> {
          p.volume = 1f
          p.play()
          _isPlaying.value = true
        }
        AudioFocusPolicy.Action.UNDUCK -> p.volume = 1f
        AudioFocusPolicy.Action.NONE -> Unit
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error handling audio focus change $change", e)
    }
  }

  /** Pause that the user (or a noisy-output event) asked for: give the focus back too. */
  private fun pauseByUser() {
    val p = player ?: return
    try {
      p.pause()
    } catch (e: Exception) {
      Log.e(TAG, "Error pausing", e)
    }
    _isPlaying.value = false
    abandonFocus()
    unregisterNoisyReceiver()
  }

  // --- player ---

  private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      // The position is read ten times a second only while something plays.
      if (isPlaying) {
        if (!effectsAttached) attachAudioEffects()
        startProgressTracking()
      } else {
        progressJob?.cancel()
        player?.let { _currentPositionMs.value = it.currentPosition.coerceAtLeast(0L) }
      }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      val p = player ?: return
      if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || p.currentMediaItemIndex == 0) return
      // The queued next track started with no gap: drop the finished one so the playlist is
      // always [playing, next].
      p.removeMediaItems(0, p.currentMediaItemIndex)
      _durationMs.value = p.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(1L) ?: 0L
      _currentPositionMs.value = 0L
      val advanced = upNextAdvanced
      upNextAdvanced = null
      advanced?.invoke()
    }

    override fun onPlaybackStateChanged(state: Int) {
      val p = player ?: return
      when (state) {
        Player.STATE_READY -> {
          if (p.duration != C.TIME_UNSET) _durationMs.value = p.duration.coerceAtLeast(1L)
          if (pendingStart) {
            pendingStart = false
            // No focus (for example during a phone call): do not start talking over it.
            if (!requestFocus()) {
              Log.w(TAG, "Audio focus denied; not starting playback")
              stopPlayback()
              return
            }
            p.volume = 1f
            p.play()
            registerNoisyReceiver()
            _isPlaying.value = true
            updateCapabilities()
          }
        }
        Player.STATE_ENDED -> {
          // The focus is held a moment longer: the next track or a narration usually follows.
          releaseFocusSoon()
          unregisterNoisyReceiver()
          _isPlaying.value = false
          _currentPositionMs.value = 0L
          _spectrumBars.value = List(15) { 0.05f }
          completionCallback()
        }
        else -> Unit
      }
    }

    override fun onPlayerError(error: PlaybackException) {
      Log.e(TAG, "Player error: ${error.errorCodeName}", error)
      val network = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
      stopPlayback()
      errorCallback(network)
    }
  }

  /** One player for the whole session; its audio sink runs our processing chain. */
  private fun ensurePlayer(): ExoPlayer {
    player?.let { return it }
    val renderers = object : DefaultRenderersFactory(context) {
      override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
      ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf(spatialProcessor, eqProcessor))
        .build()
    }
    val media3Attributes = androidx.media3.common.AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .build()
    // A fixed session id, so the visualizer and virtualizer can attach before the first sample plays.
    audioSessionId = Util.generateAudioSessionIdV21(context)
    return ExoPlayer.Builder(context, renderers)
      // Focus is handled here (AudioFocusPolicy), as before, so ExoPlayer must not also handle it.
      .setAudioAttributes(media3Attributes, false)
      .setWakeMode(C.WAKE_MODE_NETWORK)
      .build()
      .also {
        it.setAudioSessionId(audioSessionId)
        it.addListener(playerListener)
        player = it
      }
  }

  /** A playable URI for a file path or a URI string; null for a local file that no longer exists. */
  private fun uriFor(filePathOrUri: String): Uri? {
    val isRemoteOrContent = filePathOrUri.startsWith("content://") || filePathOrUri.startsWith("file://") ||
      filePathOrUri.startsWith("http://") || filePathOrUri.startsWith("https://")
    // Validate before handing it to the player, so a missing file never starts a broken load.
    if (!isRemoteOrContent && !File(filePathOrUri).exists()) return null
    return if (isRemoteOrContent) Uri.parse(filePathOrUri) else Uri.fromFile(File(filePathOrUri))
  }

  /**
   * Loads a track and, with [autoStart], plays it as soon as it is ready. Without it (a track restored at
   * launch) it waits at [startPositionMs] for play.
   */
  fun playTrack(
    filePathOrUri: String,
    onCompletion: () -> Unit = {},
    onError: (network: Boolean) -> Unit = {},
    startPositionMs: Long = 0L,
    autoStart: Boolean = true
  ) {
    try {
      // Between two tracks the focus and the headphones receiver are kept: the next one starts at once.
      resetPlayer()
      val uri = uriFor(filePathOrUri)
      if (uri == null) {
        Log.e(TAG, "Audio file does not exist: $filePathOrUri")
        onError(false)
        return
      }

      val p = ensurePlayer()
      completionCallback = onCompletion
      errorCallback = onError
      upNextAdvanced = null
      pendingStart = autoStart
      eqDsp.update(processedSettings())
      p.setMediaItem(MediaItem.fromUri(uri), startPositionMs.coerceAtLeast(0L))
      _currentPositionMs.value = startPositionMs.coerceAtLeast(0L)
      p.prepare()
      if (!autoStart) {
        abandonFocus()
        unregisterNoisyReceiver()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error playing audio file", e)
      _isPlaying.value = false
      onError(false)
    }
  }

  /** Runs when the queued next track (see [setUpNext]) took over with no gap. */
  private var upNextAdvanced: (() -> Unit)? = null

  /**
   * Queues [filePathOrUri] right after the playing track, so the player moves to it with no gap
   * (gapless). Null removes it, e.g. when the queue changed or the next song needs a narration first.
   * [onAdvanced] runs once the player has moved on to it.
   */
  fun setUpNext(filePathOrUri: String?, onAdvanced: () -> Unit) {
    val p = player ?: return
    try {
      if (p.mediaItemCount > 1) p.removeMediaItems(1, p.mediaItemCount)
      upNextAdvanced = null
      val uri = filePathOrUri?.let(::uriFor) ?: return
      if (p.mediaItemCount == 0) return
      p.addMediaItem(MediaItem.fromUri(uri))
      upNextAdvanced = onAdvanced
    } catch (e: Exception) {
      Log.w(TAG, "Could not queue the next track", e)
    }
  }

  /** New callbacks for the track that just took over (after a gapless move). */
  fun updateCallbacks(onCompletion: () -> Unit, onError: (network: Boolean) -> Unit) {
    completionCallback = onCompletion
    errorCallback = onError
  }

  /** True when a track is loaded (playing or paused), so play can resume it. */
  val hasLoadedTrack: Boolean
    get() = player?.let { it.playbackState != Player.STATE_IDLE && it.playbackState != Player.STATE_ENDED } == true

  /** Stops the player and empties it, keeping the audio focus. */
  private fun resetPlayer() {
    try {
      progressJob?.cancel()
      pendingStart = false
      upNextAdvanced = null
      player?.let {
        it.stop()
        it.clearMediaItems()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error resetting player", e)
    }
    _isPlaying.value = false
    _currentPositionMs.value = 0L
    _durationMs.value = 0L
  }

  /** Fades the volume out over [durationMs] and pauses: the sleep timer's end. */
  suspend fun fadeOutAndPause(durationMs: Long) {
    val p = player ?: return
    if (!_isPlaying.value) return
    val steps = 30
    try {
      for (i in steps downTo 0) {
        p.volume = i / steps.toFloat()
        delay(durationMs / steps)
      }
      pauseByUser()
    } finally {
      // Also when the timer is cancelled halfway through the fade.
      p.volume = 1f
    }
  }

  /** Attaches the live spectrum again, after the microphone permission it needs was granted. */
  fun refreshVisualizer() {
    if (player != null && _isPlaying.value) attachAudioEffects()
  }

  fun pause() {
    if (_isPlaying.value) pauseByUser()
  }

  fun togglePlayPause() {
    val p = player ?: return
    try {
      if (_isPlaying.value) {
        pauseByUser()
      } else if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
        Log.w(TAG, "Nothing loaded to resume")
      } else if (p.playbackState == Player.STATE_BUFFERING && !p.playWhenReady) {
        // Still loading a restored track: start as soon as it is ready.
        pendingStart = true
      } else if (requestFocus()) {
        p.volume = 1f
        p.play()
        registerNoisyReceiver()
        _isPlaying.value = true
      } else {
        Log.w(TAG, "Audio focus denied; staying paused")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error in togglePlayPause", e)
    }
  }

  fun seekTo(positionMs: Long) {
    try {
      player?.seekTo(positionMs)
      _currentPositionMs.value = positionMs
    } catch (e: Exception) {
      Log.e(TAG, "Error seeking", e)
    }
  }

  fun stopPlayback() {
    try {
      progressJob?.cancel()
      pendingStart = false
      upNextAdvanced = null
      player?.let {
        it.stop()
        it.clearMediaItems()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping player", e)
    } finally {
      _isPlaying.value = false
      // Stopping is usually a switch to another track, a web track or a narration: hold the focus briefly.
      releaseFocusSoon()
      unregisterNoisyReceiver()
      _currentPositionMs.value = 0L
      _durationMs.value = 0L
      _spectrumBars.value = List(15) { 0.05f }
      releaseAudioEffects()
      updateCapabilities()
    }
  }

  /** Frees the player, its audio track and session; the manager is not used after this. */
  fun release() {
    stopPlayback()
    releaseAudioEffects()
    runCatching { player?.release() }
    player = null
    coroutineScope.cancel()
  }

  private fun startProgressTracking() {
    progressJob?.cancel()
    progressJob = coroutineScope.launch {
      while (isActive && player != null) {
        try {
          player?.let { p ->
            if (p.isPlaying) {
              val pos = p.currentPosition
              _currentPositionMs.value = pos
              if (p.duration != C.TIME_UNSET) _durationMs.value = p.duration.coerceAtLeast(1L)
              // Without a real FFT (no RECORD_AUDIO or unsupported device) show a flat floor,
              // never a fake animated spectrum.
              if (!isRealFftActive) {
                _spectrumBars.value = List(15) { 0.05f }
              }
            }
          }
        } catch (_: Exception) {}
        delay(100)
      }
    }
  }

  private fun attachAudioEffects() {
    releaseAudioEffects()
    val sessionId = audioSessionId
    if (sessionId == 0) return
    effectsAttached = true

    try {
      virtualizer = Virtualizer(0, sessionId).apply { enabled = _eqSettings.value.enabled }
    } catch (e: Exception) {
      Log.w(TAG, "Virtualizer not supported on this session", e)
      virtualizer = null
    }
    applyVirtualizer()

    try {
      val viz = Visualizer(sessionId).apply {
        val range = Visualizer.getCaptureSizeRange()
        captureSize = range.getOrNull(1)?.coerceAtMost(1024) ?: 512
        setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
          override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

          override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            fft?.let { bytes ->
              isRealFftActive = true
              val binCount = bytes.size / 2
              // Map to 15 logarithmic frequency ranges for 25Hz to 16kHz
              val ranges = listOf(
                1..1, 1..2, 2..3, 3..5, 5..7, 7..11, 11..18, 18..28,
                28..45, 45..72, 72..115, 115..180, 180..270, 270..380,
                380..binCount.coerceAtMost(500)
              )
              val oldBars = _spectrumBars.value
              // The visualizer sees the processed output, so the bars already include the EQ.
              val newBars = ranges.mapIndexed { idx, range ->
                var sumMag = 0f
                var count = 0
                for (k in range) {
                  if (k * 2 + 1 < bytes.size) {
                    val r = bytes[k * 2].toFloat()
                    val i = bytes[k * 2 + 1].toFloat()
                    sumMag += kotlin.math.sqrt(r * r + i * i)
                    count++
                  }
                }
                val avgMag = if (count > 0) sumMag / count else 0f
                val targetNorm = (avgMag / 48f).coerceIn(0.04f, 1f)
                val prev = oldBars.getOrElse(idx) { 0.05f }
                (prev * 0.35f + targetNorm * 0.65f)
              }
              _spectrumBars.value = newBars
            }
          }
        }, Visualizer.getMaxCaptureRate() / 2, false, true)
        enabled = true
      }
      visualizer = viz
    } catch (e: Exception) {
      Log.w(TAG, "Visualizer could not be attached to session: ${e.message}")
      visualizer = null
      isRealFftActive = false
    }
  }

  private fun releaseAudioEffects() {
    try {
      visualizer?.apply {
        enabled = false
        release()
      }
    } catch (_: Exception) {}
    try {
      virtualizer?.release()
    } catch (_: Exception) {}
    visualizer = null
    virtualizer = null
    isRealFftActive = false
    effectsAttached = false
  }

  // --- processing chain ---

  /** Applies the whole equalizer state. Takes effect on the next audio buffer (a few ms). */
  fun applyEqSettings(settings: EqSettings) {
    _eqSettings.value = settings
    eqDsp.update(processedSettings())
    applyVirtualizer()
  }

  /** The root equalizer on the output mix processes this player too; then its own curve is bypassed. */
  private var coveredByOutputMix = false

  fun setCoveredByOutputMix(covered: Boolean) {
    coveredByOutputMix = covered
    eqDsp.update(processedSettings())
  }

  /**
   * With root, the system-wide equalizer already shapes the output mix, so the curve here goes flat to
   * avoid applying it twice. Balance, width and the limiter stay: the system equalizer has none of them.
   */
  private fun processedSettings(): EqSettings {
    val s = _eqSettings.value
    if (!coveredByOutputMix || !s.enabled) return s
    return s.copy(
      bands = s.bands.map { it.copy(gainDb = 0f) },
      paramBands = s.paramBands.map { it.copy(gainDb = 0f) },
      preampDb = 0f,
      bassDb = 0f,
      trebleDb = 0f,
      device = null,
      autoPreamp = false
    )
  }


  private fun applyVirtualizer() {
    val s = _eqSettings.value
    virtualizer?.let { virt ->
      try {
        virt.enabled = s.enabled && s.virtualizerStrength > 0
        if (virt.strengthSupported) virt.setStrength(s.virtualizerStrength.coerceIn(0, 1000).toShort())
      } catch (e: Exception) {
        Log.e(TAG, "Error setting virtualizer strength", e)
      }
    }
  }

  fun updateCapabilities() {
    _capabilities.value = detectCapabilities(if (player != null) audioSessionId else 0)
  }

  private fun friendlyRouteName(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fone com fio"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Fone Bluetooth"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "Áudio USB"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Auricular do aparelho"
    else -> "Alto-falante do aparelho"
  }

  private fun detectCapabilities(sessionId: Int): AudioCapabilities {
    var routeName = "Alto-falante do Dispositivo"
    var routeType = "SPEAKER"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      try {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val activeDevice = devices.firstOrNull { d ->
          d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
              d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
              d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
              d.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
              d.type == AudioDeviceInfo.TYPE_USB_HEADSET
        } ?: devices.firstOrNull()

        activeDevice?.let { dev ->
          routeType = when (dev.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "FONE CABEADO (3.5mm)"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH A2DP"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "ÁUDIO DIGITAL USB"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "ALTO-FALANTE INTEGRADO"
            else -> "SAÍDA PADRÃO"
          }
          // Built-in outputs report the phone's model code as their product name (e.g. "sdk_gphone64_x86_64"),
          // which means nothing to a listener; only external devices get their own name.
          val external = dev.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
            dev.type != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
          routeName = if (external) {
            dev.productName?.toString()?.ifBlank { null } ?: friendlyRouteName(dev.type)
          } else {
            friendlyRouteName(dev.type)
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Error detecting audio devices", e)
      }
    }

    val sampleRateStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
    val sampleRate = sampleRateStr?.toIntOrNull() ?: 44100

    var effectsCount = 0
    var isEqAvail = false
    var isBbAvail = false
    var isVirtAvail = false

    try {
      val descriptorList = AudioEffect.queryEffects()
      effectsCount = descriptorList?.size ?: 0
      descriptorList?.forEach { desc ->
        when (desc.type) {
          AudioEffect.EFFECT_TYPE_EQUALIZER -> isEqAvail = true
          AudioEffect.EFFECT_TYPE_BASS_BOOST -> isBbAvail = true
          AudioEffect.EFFECT_TYPE_VIRTUALIZER -> isVirtAvail = true
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error querying system audio effects", e)
      isEqAvail = true
      isBbAvail = true
      isVirtAvail = true
    }

    return AudioCapabilities(
      deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
      androidVersion = "Android ${Build.VERSION.RELEASE}",
      apiLevel = Build.VERSION.SDK_INT,
      activeRouteName = routeName,
      activeRouteType = routeType,
      sampleRateHz = sampleRate,
      channelCount = 2,
      channelFormat = "2.0 Estéreo (PCM 16-bit)",
      isAudioSessionActive = sessionId != 0 && (_isPlaying.value),
      audioSessionId = sessionId,
      isHardwareEqualizerAvailable = isEqAvail,
      isBassBoostAvailable = isBbAvail,
      isVirtualizerAvailable = isVirtAvail,
      effectsCount = effectsCount
    )
  }
}
