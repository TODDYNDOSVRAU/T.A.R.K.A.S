package com.example.data.assistant

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.Curiosity
import com.example.data.model.CuriosityConfig
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class CuriositySpeechManager(
  private val context: Context
) : TextToSpeech.OnInitListener {

  private val TAG = "CuriositySpeechManager"
  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  private var tts: TextToSpeech? = null
  private var isInitialized = false

  private val _isSpeaking = MutableStateFlow(false)
  val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

  private val _currentSpokenText = MutableStateFlow<String?>(null)
  val currentSpokenText: StateFlow<String?> = _currentSpokenText.asStateFlow()

  private val _speechStage = MutableStateFlow<String?>(null) // "intro", "outro", "test"
  val speechStage: StateFlow<String?> = _speechStage.asStateFlow()

  private var activeCompletionCallback: (() -> Unit)? = null

  init {
    try {
      tts = TextToSpeech(context.applicationContext, this)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to instantiate TextToSpeech", e)
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      tts?.let { engine ->
        val ptLocale = Locale("pt", "BR")
        val langResult = engine.setLanguage(ptLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
          engine.language = Locale.getDefault()
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {
            scope.launch {
              _isSpeaking.value = true
            }
          }

          override fun onDone(utteranceId: String?) {
            scope.launch {
              _isSpeaking.value = false
              _currentSpokenText.value = null
              _speechStage.value = null
              val callback = activeCompletionCallback
              activeCompletionCallback = null
              callback?.invoke()
            }
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            scope.launch {
              _isSpeaking.value = false
              _currentSpokenText.value = null
              _speechStage.value = null
              val callback = activeCompletionCallback
              activeCompletionCallback = null
              callback?.invoke()
            }
          }

          override fun onError(utteranceId: String?, errorCode: Int) {
            scope.launch {
              _isSpeaking.value = false
              _currentSpokenText.value = null
              _speechStage.value = null
              val callback = activeCompletionCallback
              activeCompletionCallback = null
              callback?.invoke()
            }
          }
        })
      }
      isInitialized = true
    } else {
      Log.w(TAG, "TextToSpeech init failed with status: $status")
      isInitialized = false
    }
  }

  /**
   * Generates a short, basic, and listener-relevant phrase from the curiosity.
   * Keeps it under 2 punchy sentences so it's smooth and engaging.
   */
  fun formatSpokenCuriosity(
    track: Track,
    curiosity: Curiosity,
    isIntro: Boolean
  ): String {
    // Extract first 1 or 2 clean sentences from the trivia
    val rawTrivia = curiosity.shortTrivia.trim()
    val cleanTrivia = if (rawTrivia.isNotBlank() && !rawTrivia.contains("Não encontrei")) {
      val sentences = rawTrivia.split(Regex("(?<=[.!?])\\s+"))
      when {
        sentences.size >= 2 -> "${sentences[0]} ${sentences[1]}"
        sentences.isNotEmpty() -> sentences[0]
        else -> rawTrivia
      }
    } else {
      "Não encontrei uma curiosidade verificada sobre esta faixa."
    }

    return if (isIntro) {
      "Curiosidade sobre ${track.title}, de ${track.artist}: $cleanTrivia Aproveite o som!"
    } else {
      "Você sabia sobre ${track.title}? $cleanTrivia Essa foi ${track.title}, de ${track.artist}."
    }
  }

  /**
   * Speaks the curiosity via TextToSpeech engine.
   * If TTS is not ready or disabled, immediately triggers onDone.
   */
  fun speakCuriosity(
    track: Track,
    curiosity: Curiosity,
    isIntro: Boolean,
    config: CuriosityConfig,
    onDone: () -> Unit
  ) {
    if (!config.enabled) {
      onDone()
      return
    }

    val spokenText = formatSpokenCuriosity(track, curiosity, isIntro)
    _currentSpokenText.value = spokenText
    _speechStage.value = if (isIntro) "intro" else "outro"

    if (!isInitialized || tts == null) {
      Log.w(TAG, "TTS not initialized, skipping vocalization")
      _currentSpokenText.value = null
      _speechStage.value = null
      onDone()
      return
    }

    activeCompletionCallback = onDone

    try {
      tts?.apply {
        setPitch(config.pitch.coerceIn(0.5f, 2.0f))
        setSpeechRate(config.speechRate.coerceIn(0.5f, 2.0f))

        val params = Bundle()
        val utteranceId = "curiosity_${UUID.randomUUID()}"
        speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error triggering speak", e)
      _isSpeaking.value = false
      _currentSpokenText.value = null
      _speechStage.value = null
      activeCompletionCallback = null
      onDone()
    }
  }

  /**
   * Test speech sample so user can preview pitch and speech rate.
   */
  fun testVoiceSample(
    config: CuriosityConfig,
    onDone: () -> Unit = {}
  ) {
    val sampleText = "Olá! Eu sou a voz do Tarkas. Vou apresentar curiosidades curtas e fatos interessantes sobre as suas músicas favoritas."
    _currentSpokenText.value = sampleText
    _speechStage.value = "test"

    if (!isInitialized || tts == null) {
      _currentSpokenText.value = null
      _speechStage.value = null
      onDone()
      return
    }

    activeCompletionCallback = onDone

    try {
      tts?.apply {
        setPitch(config.pitch.coerceIn(0.5f, 2.0f))
        setSpeechRate(config.speechRate.coerceIn(0.5f, 2.0f))

        val params = Bundle()
        val utteranceId = "test_voice_${UUID.randomUUID()}"
        speak(sampleText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error playing test voice", e)
      _isSpeaking.value = false
      _currentSpokenText.value = null
      _speechStage.value = null
      activeCompletionCallback = null
      onDone()
    }
  }

  fun stopSpeech() {
    try {
      tts?.stop()
    } catch (_: Exception) {}

    _isSpeaking.value = false
    _currentSpokenText.value = null
    _speechStage.value = null

    val callback = activeCompletionCallback
    activeCompletionCallback = null
    callback?.invoke()
  }

  fun shutdown() {
    try {
      tts?.stop()
      tts?.shutdown()
    } catch (_: Exception) {}
    tts = null
    isInitialized = false
  }
}
