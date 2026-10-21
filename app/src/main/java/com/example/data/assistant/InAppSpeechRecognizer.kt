package com.example.data.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * In-App Speech Recognizer that captures speech directly without opening
 * any system Google Voice Search popup dialogs or activity launchers.
 */
class InAppSpeechRecognizer(
  private val context: Context,
  private val onSpeechResult: (String) -> Unit
) : RecognitionListener {

  private val TAG = "InAppSpeechRecognizer"
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  private var recognizer: SpeechRecognizer? = null

  private val _isListening = MutableStateFlow(false)
  val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

  private val _rmsDb = MutableStateFlow(0f)
  val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

  private val _partialText = MutableStateFlow<String?>(null)
  val partialText: StateFlow<String?> = _partialText.asStateFlow()

  init {
    scope.launch {
      initRecognizer()
    }
  }

  private fun initRecognizer() {
    try {
      if (SpeechRecognizer.isRecognitionAvailable(context)) {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
          setRecognitionListener(this@InAppSpeechRecognizer)
        }
      } else {
        Log.w(TAG, "Speech recognition service not available on device")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error initializing SpeechRecognizer", e)
    }
  }

  fun toggleListening() {
    if (_isListening.value) {
      stopListening()
    } else {
      startListening()
    }
  }

  fun startListening() {
    try {
      if (recognizer == null) {
        initRecognizer()
      }
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("pt", "BR"))
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
      }
      recognizer?.startListening(intent)
      _isListening.value = true
      _partialText.value = null
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start listening", e)
      _isListening.value = false
    }
  }

  fun stopListening() {
    try {
      recognizer?.stopListening()
    } catch (_: Exception) {}
    _isListening.value = false
    _rmsDb.value = 0f
  }

  fun cancel() {
    try {
      recognizer?.cancel()
    } catch (_: Exception) {}
    _isListening.value = false
    _rmsDb.value = 0f
    _partialText.value = null
  }

  fun destroy() {
    try {
      recognizer?.destroy()
    } catch (_: Exception) {}
    recognizer = null
    _isListening.value = false
  }

  override fun onReadyForSpeech(params: Bundle?) {
    _isListening.value = true
  }

  override fun onBeginningOfSpeech() {
    _isListening.value = true
  }

  override fun onRmsChanged(rmsdB: Float) {
    // rmsdB is typically between -2dB and 10dB
    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
    _rmsDb.value = normalized
  }

  override fun onBufferReceived(buffer: ByteArray?) {}

  override fun onEndOfSpeech() {
    _isListening.value = false
    _rmsDb.value = 0f
  }

  override fun onError(error: Int) {
    Log.d(TAG, "SpeechRecognizer error: $error")
    _isListening.value = false
    _rmsDb.value = 0f
    // Re-create if server died or client disconnected
    if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED || error == SpeechRecognizer.ERROR_CLIENT) {
      initRecognizer()
    }
  }

  override fun onResults(results: Bundle?) {
    _isListening.value = false
    _rmsDb.value = 0f
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    val query = matches?.firstOrNull()?.trim()
    if (!query.isNullOrBlank()) {
      _partialText.value = query
      onSpeechResult(query)
    }
  }

  override fun onPartialResults(partialResults: Bundle?) {
    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    val partial = matches?.firstOrNull()?.trim()
    if (!partial.isNullOrBlank()) {
      _partialText.value = partial
    }
  }

  override fun onEvent(eventType: Int, params: Bundle?) {}
}
