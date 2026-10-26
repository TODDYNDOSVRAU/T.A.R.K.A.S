package com.example.data.systemaudio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SystemAudioStatus(
  val isCapturing: Boolean = false,
  val detectedApp: String? = null,
  val detectedTrackTitle: String? = null,
  val detectedArtist: String? = null,
  val currentRmsEnergy: Float = 0f,
  val isMediaProjectionSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
  val permissionGranted: Boolean = false,
  val errorMessage: String? = null
)

class SystemAudioCaptureManager(private val context: Context) {
  private val TAG = "SystemAudioCapture"

  private val _status = MutableStateFlow(SystemAudioStatus())
  val status: StateFlow<SystemAudioStatus> = _status.asStateFlow()

  private var audioRecord: AudioRecord? = null
  private var projection: MediaProjection? = null
  private var captureJob: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO + Job())

  val mediaProjectionManager: MediaProjectionManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
    context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
  } else null

  fun createScreenCaptureIntent(): Intent? {
    return mediaProjectionManager?.createScreenCaptureIntent()
  }

  fun startCaptureWithProjection(mediaProjection: MediaProjection) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      Log.w(TAG, "AudioPlaybackCapture requires Android 10 (API 29)+")
      return
    }

    try {
      stopCapture()
      projection = mediaProjection

      val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        .addMatchingUsage(AudioAttributes.USAGE_GAME)
        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        .build()

      val sampleRate = 44100
      val channelConfig = AudioFormat.CHANNEL_IN_STEREO
      val audioFormat = AudioFormat.ENCODING_PCM_16BIT
      val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
      val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

      val record = AudioRecord.Builder()
        .setAudioPlaybackCaptureConfig(config)
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(audioFormat)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .build()

      if (record.state != AudioRecord.STATE_INITIALIZED) {
        record.release()
        _status.value = _status.value.copy(isCapturing = false, errorMessage = "O Android recusou a captura de áudio dos outros apps.")
      } else {
        record.startRecording()
        audioRecord = record
        _status.value = _status.value.copy(
          isCapturing = true,
          permissionGranted = true,
          errorMessage = null
        )

        captureJob = scope.launch {
          val buffer = ShortArray(1024)
          while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
              var sum = 0L
              for (i in 0 until read) {
                sum += abs(buffer[i].toInt())
              }
              val avg = (sum.toFloat() / read) / Short.MAX_VALUE
              _status.value = _status.value.copy(currentRmsEnergy = avg.coerceIn(0f, 1f))
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error starting AudioPlaybackCapture", e)
      _status.value = _status.value.copy(
        isCapturing = false,
        errorMessage = "Não foi possível iniciar a captura de áudio neste aparelho."
      )
    }
  }

  /** Reports why capture is not running, instead of pretending it works. */
  fun markUnavailable(reason: String) {
    _status.value = _status.value.copy(isCapturing = false, currentRmsEnergy = 0f, errorMessage = reason)
  }

  fun stopCapture() {
    try {
      captureJob?.cancel()
      captureJob = null
      audioRecord?.let {
        if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
          it.stop()
        }
        it.release()
      }
      // Ending the projection also removes the system's "sharing" indicator.
      projection?.stop()
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping capture", e)
    } finally {
      audioRecord = null
      projection = null
      _status.value = _status.value.copy(isCapturing = false, currentRmsEnergy = 0f)
    }
  }
}
