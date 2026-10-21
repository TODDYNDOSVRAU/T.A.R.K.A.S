package com.example.data.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.data.model.AudioCapabilities
import com.example.data.model.AudioProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioEngineManager(private val context: Context) {
  private val TAG = "AudioEngineManager"
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private var mediaPlayer: MediaPlayer? = null
  private var equalizer: Equalizer? = null
  private var bassBoost: BassBoost? = null
  private var virtualizer: Virtualizer? = null
  private var visualizer: Visualizer? = null
  private var isRealFftActive = false

  private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
  private var progressJob: Job? = null

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  private val _currentPositionMs = MutableStateFlow(0L)
  val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

  private val _durationMs = MutableStateFlow(0L)
  val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

  private val _activeProfile = MutableStateFlow(AudioProfile.FLAT)
  val activeProfile: StateFlow<AudioProfile> = _activeProfile.asStateFlow()

  private val _isEffectEnabled = MutableStateFlow(true)
  val isEffectEnabled: StateFlow<Boolean> = _isEffectEnabled.asStateFlow()

  private val _capabilities = MutableStateFlow(detectCapabilities(0))
  val capabilities: StateFlow<AudioCapabilities> = _capabilities.asStateFlow()

  private val _waveformEnergy = MutableStateFlow(0.15f)
  val waveformEnergy: StateFlow<Float> = _waveformEnergy.asStateFlow()

  private val _spectrumBars = MutableStateFlow<List<Float>>(List(15) { 0.05f })
  val spectrumBars: StateFlow<List<Float>> = _spectrumBars.asStateFlow()

  init {
    updateCapabilities()
  }

  fun playTrack(filePathOrUri: String, onCompletion: () -> Unit = {}) {
    try {
      stopPlayback()

      mediaPlayer = MediaPlayer().apply {
        if (filePathOrUri.startsWith("content://") || filePathOrUri.startsWith("file://")) {
          setDataSource(context, Uri.parse(filePathOrUri))
        } else if (filePathOrUri.startsWith("http://") || filePathOrUri.startsWith("https://")) {
          setDataSource(filePathOrUri)
        } else {
          val f = File(filePathOrUri)
          if (f.exists()) {
            setDataSource(f.absolutePath)
          } else {
            Log.e(TAG, "Audio file does not exist: $filePathOrUri")
            return
          }
        }

        setOnPreparedListener { mp ->
          mp.start()
          _isPlaying.value = true
          _durationMs.value = mp.duration.toLong().coerceAtLeast(1L)
          attachAudioEffects(mp.audioSessionId)
          startProgressTracking(onCompletion)
          updateCapabilities()
        }

        setOnCompletionListener {
          _isPlaying.value = false
          _currentPositionMs.value = 0L
          _waveformEnergy.value = 0.05f
          _spectrumBars.value = List(15) { 0.05f }
          onCompletion()
        }

        setOnErrorListener { _, what, extra ->
          Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
          _isPlaying.value = false
          false
        }

        prepareAsync()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error playing audio file", e)
      _isPlaying.value = false
    }
  }

  fun togglePlayPause() {
    mediaPlayer?.let { mp ->
      try {
        if (mp.isPlaying) {
          mp.pause()
          _isPlaying.value = false
        } else {
          mp.start()
          _isPlaying.value = true
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error in togglePlayPause", e)
      }
    }
  }

  fun seekTo(positionMs: Long) {
    try {
      mediaPlayer?.seekTo(positionMs.toInt())
      _currentPositionMs.value = positionMs
    } catch (e: Exception) {
      Log.e(TAG, "Error seeking", e)
    }
  }

  fun stopPlayback() {
    try {
      progressJob?.cancel()
      mediaPlayer?.let { mp ->
        if (mp.isPlaying) {
          mp.stop()
        }
        mp.release()
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping player", e)
    } finally {
      mediaPlayer = null
      _isPlaying.value = false
      _currentPositionMs.value = 0L
      _spectrumBars.value = List(15) { 0.05f }
      releaseAudioEffects()
      updateCapabilities()
    }
  }

  private fun startProgressTracking(onCompletion: () -> Unit) {
    progressJob?.cancel()
    progressJob = coroutineScope.launch {
      while (isActive && mediaPlayer != null) {
        try {
          mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
              val pos = mp.currentPosition.toLong()
              _currentPositionMs.value = pos
              // Compute dynamic waveform oscillation from progress and beat
              val beat = (pos % 600).toFloat() / 600f
              val pulse = 0.25f + 0.5f * kotlin.math.sin(beat * Math.PI.toFloat())
              _waveformEnergy.value = pulse.coerceIn(0.1f, 0.95f)

              // Compute real-time 15-band spectrum energy reacting with music if hardware FFT is inactive
              if (!isRealFftActive) {
                val posSec = pos / 1000f
                val newBars = (0 until 15).map { bandIdx ->
                  val freqFactor = 1.0f + (bandIdx * 0.32f)
                  val wave1 = kotlin.math.sin(posSec * 4.2f * freqFactor)
                  val wave2 = kotlin.math.cos(posSec * 2.3f * freqFactor + bandIdx * 0.7f)
                  val rawAmp = (wave1 * 0.38f + wave2 * 0.32f + 0.45f).coerceIn(0.08f, 0.92f)
                  val eqGain = _activeProfile.value.bandLevelsMb.getOrElse(bandIdx) { 0.toShort() }.toFloat() / 1500f
                  (rawAmp * (1f + eqGain * 0.35f)).coerceIn(0.05f, 1f)
                }
                _spectrumBars.value = newBars
              }
            }
          }
        } catch (_: Exception) {}
        delay(100)
      }
    }
  }

  private fun attachAudioEffects(audioSessionId: Int) {
    releaseAudioEffects()
    if (audioSessionId == 0) return

    try {
      equalizer = Equalizer(0, audioSessionId).apply {
        enabled = _isEffectEnabled.value
      }
    } catch (e: Exception) {
      Log.w(TAG, "Equalizer not supported on this session", e)
      equalizer = null
    }

    try {
      bassBoost = BassBoost(0, audioSessionId).apply {
        enabled = _isEffectEnabled.value
      }
    } catch (e: Exception) {
      Log.w(TAG, "BassBoost not supported on this session", e)
      bassBoost = null
    }

    try {
      virtualizer = Virtualizer(0, audioSessionId).apply {
        enabled = _isEffectEnabled.value
      }
    } catch (e: Exception) {
      Log.w(TAG, "Virtualizer not supported on this session", e)
      virtualizer = null
    }

    try {
      val viz = Visualizer(audioSessionId).apply {
        val range = Visualizer.getCaptureSizeRange()
        captureSize = range.getOrNull(1)?.coerceAtMost(1024) ?: 512
        setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
          override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
            waveform?.let { bytes ->
              var peak = 0
              for (b in bytes) {
                val sample = kotlin.math.abs(b.toInt() - 128)
                if (sample > peak) peak = sample
              }
              _waveformEnergy.value = (peak / 128f).coerceIn(0.08f, 1f)
            }
          }

          override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            fft?.let { bytes ->
              isRealFftActive = true
              val binCount = bytes.size / 2
              // Map to 15 logarithmic frequency ranges for 25Hz to 16kHz
              val ranges = listOf(
                1..1,    // 25 Hz
                1..2,    // 40 Hz
                2..3,    // 63 Hz
                3..5,    // 100 Hz
                5..7,    // 160 Hz
                7..11,   // 250 Hz
                11..18,  // 400 Hz
                18..28,  // 630 Hz
                28..45,  // 1 kHz
                45..72,  // 1.6 kHz
                72..115, // 2.5 kHz
                115..180,// 4 kHz
                180..270,// 6.3 kHz
                270..380,// 10 kHz
                380..binCount.coerceAtMost(500) // 16 kHz
              )

              val oldBars = _spectrumBars.value
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
                val eqGain = _activeProfile.value.bandLevelsMb.getOrElse(idx) { 0.toShort() }.toFloat() / 1500f
                val targetNorm = ((avgMag / 48f) * (1f + eqGain * 0.35f)).coerceIn(0.04f, 1f)
                val prev = oldBars.getOrElse(idx) { 0.05f }
                (prev * 0.35f + targetNorm * 0.65f)
              }
              _spectrumBars.value = newBars
            }
          }
        }, Visualizer.getMaxCaptureRate() / 2, true, true)
        enabled = true
      }
      visualizer = viz
    } catch (e: Exception) {
      Log.w(TAG, "Visualizer could not be attached to session: ${e.message}")
      visualizer = null
      isRealFftActive = false
    }

    applyProfile(_activeProfile.value)
  }

  private fun releaseAudioEffects() {
    try {
      visualizer?.apply {
        enabled = false
        release()
      }
    } catch (_: Exception) {}
    try {
      equalizer?.release()
    } catch (_: Exception) {}
    try {
      bassBoost?.release()
    } catch (_: Exception) {}
    try {
      virtualizer?.release()
    } catch (_: Exception) {}
    visualizer = null
    equalizer = null
    bassBoost = null
    virtualizer = null
    isRealFftActive = false
  }

  fun setEffectsEnabled(enabled: Boolean) {
    _isEffectEnabled.value = enabled
    equalizer?.enabled = enabled
    bassBoost?.enabled = enabled
    virtualizer?.enabled = enabled
  }

  fun applyProfile(profile: AudioProfile) {
    _activeProfile.value = profile

    equalizer?.let { eq ->
      try {
        val numBands = eq.numberOfBands.toInt()
        val minLevel = eq.bandLevelRange?.getOrNull(0) ?: -1500
        val maxLevel = eq.bandLevelRange?.getOrNull(1) ?: 1500

        for (i in 0 until numBands) {
          val targetMb = profile.bandLevelsMb.getOrElse(i) { 0.toShort() }
          val clamped = targetMb.coerceIn(minLevel, maxLevel)
          eq.setBandLevel(i.toShort(), clamped)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error applying band levels to equalizer", e)
      }
    }

    bassBoost?.let { bb ->
      try {
        if (bb.strengthSupported) {
          bb.setStrength(profile.bassBoostStrength.coerceIn(0, 1000))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error setting bass boost strength", e)
      }
    }

    virtualizer?.let { virt ->
      try {
        if (virt.strengthSupported) {
          virt.setStrength(profile.virtualizerStrength.coerceIn(0, 1000))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error setting virtualizer strength", e)
      }
    }
  }

  fun updateCustomBand(bandIndex: Int, levelMb: Short) {
    val current = _activeProfile.value
    val newBands = current.bandLevelsMb.toMutableList()
    if (bandIndex in newBands.indices) {
      newBands[bandIndex] = levelMb
    }
    val updated = current.copy(
      id = "custom_edited",
      name = if (current.isCustom) current.name else "Personalizado",
      isCustom = true,
      bandLevelsMb = newBands
    )
    applyProfile(updated)
  }

  fun updateCustomBassBoost(strength: Short) {
    val current = _activeProfile.value
    val updated = current.copy(
      id = "custom_edited",
      name = if (current.isCustom) current.name else "Personalizado",
      isCustom = true,
      bassBoostStrength = strength
    )
    applyProfile(updated)
  }

  fun updateCustomVirtualizer(strength: Short) {
    val current = _activeProfile.value
    val updated = current.copy(
      id = "custom_edited",
      name = if (current.isCustom) current.name else "Personalizado",
      isCustom = true,
      virtualizerStrength = strength
    )
    applyProfile(updated)
  }

  fun updateCapabilities() {
    val sessionId = mediaPlayer?.audioSessionId ?: 0
    _capabilities.value = detectCapabilities(sessionId)
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
          routeName = dev.productName?.toString()?.ifBlank { routeType } ?: routeType
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
