package com.example.windows.assistente

import com.example.windows.res.*
import com.example.windows.state.SettingsStore
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import java.util.UUID

/** Reply texts from the Compose resources, in the language the app shows now. */
object ResourceTexts : AssistantTexts {
  override fun text(res: StringResource, vararg args: Any) = com.example.windows.res.trNow(res, *args)
  override fun plural(res: PluralStringResource, quantity: Int, vararg args: Any) =
    com.example.windows.res.trPluralNow(res, quantity, *args)
}

/** One countdown the assistant created. [endsAtMs] is wall-clock time, so it survives a recomposition. */
data class LocalTimer(val id: String, val label: String, val endsAtMs: Long, val completed: Boolean = false) {
  fun remainingSeconds(now: Long): Long = ((endsAtMs - now) / 1000).coerceAtLeast(0)
  fun formattedTime(now: Long): String {
    val s = remainingSeconds(now)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
  }
}

/** The speech side of the assistant, replaceable in tests. */
interface Voice {
  fun speak(text: String, pitch: Float, rate: Float): SpeechHandle
  fun listen(): ListenResult
}

object SapiVoice : Voice {
  override fun speak(text: String, pitch: Float, rate: Float) = Sapi.speak(text, pitch, rate)
  override fun listen(): ListenResult = Sapi.listen()
}

internal fun failureText(texts: AssistantTexts, failure: AssistantFailure): String = texts.text(
  when (failure) {
    AssistantFailure.NO_CONNECTION -> Res.string.assistant_error_no_connection
    AssistantFailure.KEY_REFUSED -> Res.string.assistant_error_key_refused
    AssistantFailure.QUOTA -> Res.string.assistant_error_quota
    AssistantFailure.CUT_OFF -> Res.string.assistant_error_cut_off
    AssistantFailure.NO_REPLY -> Res.string.assistant_error_no_reply
    AssistantFailure.SERVER -> Res.string.assistant_error_server
  }
)

/**
 * State of the Assistente tab and of the curiosity settings page. It takes [TarkasState] for the settings table
 * and [host] for what the assistant may change in the player, library and equalizer.
 */
class AssistenteState(
  val app: TarkasState,
  val host: AssistantHost,
  val keys: ApiKeyStore = ApiKeyStore(),
  private val texts: AssistantTexts = ResourceTexts,
  transport: GeminiTransport = HttpGeminiTransport,
  private val voice: Voice = SapiVoice,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
  private val clock: () -> Long = System::currentTimeMillis
) {
  private val engine = AssistantEngine(texts, host, keys, transport)
  private val store: SettingsStore get() = app.settings

  private val _messages = MutableStateFlow(
    store.get(SCOPE, KEY_HISTORY)?.let(::assistantHistoryFromJson).orEmpty()
  )
  val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

  private val _processing = MutableStateFlow(false)
  val processing: StateFlow<Boolean> = _processing.asStateFlow()

  private val _hasKey = MutableStateFlow(engine.hasGeminiKey)
  val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

  private val _onlineAi = MutableStateFlow(store.get(SCOPE, KEY_ONLINE) != "false")
  val onlineAi: StateFlow<Boolean> = _onlineAi.asStateFlow()

  private val _listening = MutableStateFlow(false)
  val listening: StateFlow<Boolean> = _listening.asStateFlow()

  private val _voiceError = MutableStateFlow<String?>(null)
  val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

  private val _timers = MutableStateFlow<List<LocalTimer>>(emptyList())
  val timers: StateFlow<List<LocalTimer>> = _timers.asStateFlow()

  private val _config = MutableStateFlow(loadConfig())
  val config: StateFlow<CuriosityConfig> = _config.asStateFlow()

  private val _speaking = MutableStateFlow(false)
  val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
  private val _spokenText = MutableStateFlow<String?>(null)
  val spokenText: StateFlow<String?> = _spokenText.asStateFlow()

  private var speech: SpeechHandle? = null
  private var speechJob: Job? = null

  // ---- chat ----

  fun send(input: String, byVoice: Boolean = false) {
    val text = input.trim()
    if (text.isEmpty() || _processing.value) return
    appendMessage(AssistantMessage(UUID.randomUUID().toString(), MessageSender.USER, text, clock()))
    _processing.value = true
    scope.launch {
      val reply = try {
        engine.processUserMessage(text, useGeminiIfAvailable = _onlineAi.value)
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (_: Exception) {
        AssistantMessage(UUID.randomUUID().toString(), MessageSender.SYSTEM, failureText(texts, AssistantFailure.SERVER), clock(), failure = AssistantFailure.SERVER)
      }
      appendMessage(reply)
      _processing.value = false
      if (shouldSpeakReply(byVoice, reply)) speakNow(reply.text, 1f, 1f, test = false)
    }
  }

  /** Sends the user message that failed again: the failed reply is removed first. */
  fun retry(replyId: String) {
    val list = _messages.value
    val index = list.indexOfFirst { it.id == replyId }
    if (index <= 0) return
    val question = list.subList(0, index).lastOrNull { it.sender == MessageSender.USER } ?: return
    _messages.value = list.filterIndexed { i, _ -> i != index && i != list.indexOf(question) }
    persist()
    send(question.text)
  }

  fun clearConversation() {
    _messages.value = emptyList()
    persist()
  }

  private fun appendMessage(message: AssistantMessage) {
    _messages.update { (it + message).takeLast(ASSISTANT_HISTORY_LIMIT) }
    persist()
  }

  private fun persist() = store.put(SCOPE, KEY_HISTORY, assistantHistoryToJson(_messages.value))

  fun setOnlineAi(on: Boolean) {
    _onlineAi.value = on
    store.put(SCOPE, KEY_ONLINE, on.toString())
  }

  // ---- key (never in the build: it lives encrypted in the user profile) ----

  /** Returns false when the text is not a usable key. */
  fun saveKey(key: String): Boolean = try {
    keys.save(key)
    _hasKey.value = engine.hasGeminiKey
    true
  } catch (_: Exception) {
    false
  }

  fun clearKey() {
    keys.clear()
    _hasKey.value = engine.hasGeminiKey
  }

  // ---- voice ----

  /** Listens once, on the user's request, and sends what was heard. */
  fun listenAndSend() {
    if (_listening.value) return
    _voiceError.value = null
    _listening.value = true
    scope.launch {
      val result = withContext(Dispatchers.IO) { voice.listen() }
      _listening.value = false
      when (result) {
        is ListenResult.Heard -> send(result.text, byVoice = true)
        ListenResult.Nothing -> Unit
        ListenResult.Unavailable -> _voiceError.value = texts.text(Res.string.assistant_voice_unavailable)
        ListenResult.Failed -> _voiceError.value = texts.text(Res.string.assistant_voice_failed)
      }
    }
  }

  fun stopSpeech() {
    speech?.stop()
    speech = null
    _speaking.value = false
    _spokenText.value = null
  }

  fun testVoice() {
    val c = _config.value
    speakNow(texts.text(Res.string.assistant_spoken_test), c.pitch, c.speechRate, test = true)
  }

  /** The spoken text of a curiosity, framed like the Android intro. */
  fun spokenCuriosity(c: Curiosity, atStart: Boolean): String {
    val body = spokenTrivia(c.shortTrivia)
    return if (atStart) texts.text(Res.string.assistant_spoken_intro, c.trackTitle, c.artist, body)
    else texts.text(Res.string.assistant_spoken_outro, c.trackTitle, body, c.artist)
  }

  private fun speakNow(text: String, pitch: Float, rate: Float, test: Boolean) {
    stopSpeech()
    val handle = voice.speak(text, pitch, rate)
    speech = handle
    _speaking.value = true
    _spokenText.value = if (test) text else null
    speechJob = scope.launch(Dispatchers.IO) {
      handle.await()
      if (speech === handle) {
        speech = null
        _speaking.value = false
        _spokenText.value = null
      }
    }
  }

  // ---- timers (called by the host) ----

  fun addTimer(id: String, label: String, seconds: Int) {
    val timer = LocalTimer(id, label, clock() + seconds * 1000L)
    _timers.update { it + timer }
    scope.launch {
      delay(seconds * 1000L)
      _timers.update { list -> list.map { if (it.id == id) it.copy(completed = true) else it } }
    }
  }

  fun cancelTimer(id: String) = _timers.update { list -> list.filterNot { it.id == id } }
  fun cancelAllTimers() { _timers.value = emptyList() }

  // ---- curiosity settings ----

  fun setConfig(config: CuriosityConfig) {
    _config.value = config
    store.put(SCOPE, KEY_CONFIG, configToJson(config))
  }

  private fun loadConfig(): CuriosityConfig = store.get(SCOPE, KEY_CONFIG)?.let(::configFromJson) ?: CuriosityConfig()

  companion object {
    const val SCOPE = "assistente"
    const val KEY_HISTORY = "history"
    const val KEY_ONLINE = "online_ai"
    const val KEY_CONFIG = "curiosity_config"
  }
}

internal fun configToJson(c: CuriosityConfig): String = org.json.JSONObject()
  .put("enabled", c.enabled).put("timing", c.timing.name).put("display", c.displayMode.name)
  .put("preset", c.voicePreset.name).put("pitch", c.pitch.toDouble()).put("rate", c.speechRate.toDouble()).toString()

/** A stored config that is unreadable gives null, so the defaults apply. Unknown names fall back per field. */
internal fun configFromJson(json: String): CuriosityConfig? = try {
  val o = org.json.JSONObject(json)
  val d = CuriosityConfig()
  CuriosityConfig(
    enabled = o.optBoolean("enabled", d.enabled),
    timing = CuriosityTiming.entries.firstOrNull { it.name == o.optString("timing") } ?: d.timing,
    displayMode = CuriosityDisplayMode.entries.firstOrNull { it.name == o.optString("display") } ?: d.displayMode,
    voicePreset = VoicePreset.entries.firstOrNull { it.name == o.optString("preset") } ?: d.voicePreset,
    pitch = o.optDouble("pitch", d.pitch.toDouble()).toFloat().coerceIn(0.5f, 2f),
    speechRate = o.optDouble("rate", d.speechRate.toDouble()).toFloat().coerceIn(0.5f, 2f)
  )
} catch (_: Exception) {
  null
}
