package com.example.data.assistant

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.model.AssistantMessage
import com.example.data.model.MessageSender
import java.util.UUID

/**
 * Reads an assistant reply aloud. It has its own engine: the curiosity narration of the player uses another
 * one and must not be cut off by a chat reply. The engine starts on the first reply that is read aloud, so
 * users who only type never load it.
 */
class AssistantSpeaker(context: Context) {

  private val appContext = context.applicationContext
  private var tts: TextToSpeech? = null
  @Volatile private var ready = false
  // A reply that came before the engine was ready; it is read once the engine starts.
  private var pending: String? = null

  /** Reads [text], replacing whatever is being read. Blank text and a missing engine are ignored. */
  fun speak(text: String) {
    if (text.isBlank()) return
    if (tts == null) {
      try {
        tts = TextToSpeech(appContext) { status -> onInit(status) }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to start TextToSpeech", e)
        return
      }
    }
    if (!ready) {
      pending = text
      return
    }
    say(text)
  }

  /** Stops what is being read and drops a reply still waiting for the engine. */
  fun stop() {
    pending = null
    try {
      tts?.stop()
    } catch (_: Exception) {}
  }

  fun shutdown() {
    stop()
    try {
      tts?.shutdown()
    } catch (_: Exception) {}
    tts = null
    ready = false
  }

  private fun onInit(status: Int) {
    val engine = tts ?: return
    if (status != TextToSpeech.SUCCESS) {
      Log.w(TAG, "TextToSpeech init failed with status: $status")
      pending = null
      return
    }
    // The voice reads in the language the app shows, as the curiosity narration does.
    val locale = appContext.resources.configuration.locales[0]
    val result = engine.setLanguage(locale)
    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
      Log.w(TAG, "No voice for $locale; using the engine default")
    }
    engine.setAudioAttributes(
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    )
    ready = true
    pending?.let {
      pending = null
      say(it)
    }
  }

  private fun say(text: String) {
    try {
      tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "assistant_${UUID.randomUUID()}")
    } catch (e: Exception) {
      Log.e(TAG, "Error reading the reply aloud", e)
    }
  }

  private companion object {
    const val TAG = "AssistantSpeaker"
  }
}

/** The reply as it is read aloud: the bold and italic markers are dropped so the voice does not say them. */
internal fun speakableReply(text: String): String =
  text.replace("*", "").replace(Regex("\\s+"), " ").trim()

/** Only a reply to a question asked by voice is read aloud, and only a real reply, never an error line. */
internal fun shouldSpeakReply(askedByVoice: Boolean, reply: AssistantMessage): Boolean =
  askedByVoice &&
    reply.sender == MessageSender.ASSISTANT &&
    reply.failure == null &&
    speakableReply(reply.text).isNotEmpty()
