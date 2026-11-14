package com.example.data.assistant

import com.example.data.net.Http
import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.data.audio.AudioEngineManager
import com.example.data.diagnostics.Diagnostics
import com.example.data.model.AssistantFailure
import com.example.data.model.AssistantMessage
import com.example.data.model.AudioProfile
import com.example.data.model.Curiosity
import com.example.data.model.EqPreset
import com.example.data.model.EqPresets
import com.example.data.model.LocalTimer
import com.example.data.model.MessageSender
import com.example.data.model.ToolExecutionReport
import com.example.data.model.Track
import com.example.data.model.VerificationStatus
import com.example.data.repository.CuriosityRepository
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** Raised when the online reply cannot be used. The reason decides the message the user sees. */
class AssistantFailureException(val failure: AssistantFailure, cause: Throwable? = null) : Exception(failure.name, cause)

/** Maps an HTTP status from the online service to the reason shown to the user. */
internal fun failureForHttpStatus(code: Int): AssistantFailure = when (code) {
  401, 403 -> AssistantFailure.KEY_REFUSED
  429 -> AssistantFailure.QUOTA
  else -> AssistantFailure.SERVER
}

/**
 * Reads the reply text from a generateContent response body.
 * Throws AssistantFailureException when there is no usable text, or when the answer was cut off
 * at the output limit: a partial answer is discarded rather than shown.
 */
internal fun geminiReplyText(body: String): String {
  val json = try {
    JSONObject(body)
  } catch (_: JSONException) {
    throw AssistantFailureException(AssistantFailure.NO_REPLY)
  }
  val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
    ?: throw AssistantFailureException(AssistantFailure.NO_REPLY)
  if (candidate.optString("finishReason") == "MAX_TOKENS") {
    throw AssistantFailureException(AssistantFailure.CUT_OFF)
  }
  val text = candidate.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
  if (text.isNullOrBlank()) throw AssistantFailureException(AssistantFailure.NO_REPLY)
  return text.trim()
}

/** Player actions the assistant can trigger. The app runs them and reports what really happened. */
enum class PlayerCommand { NEXT, PREVIOUS, PLAY, PAUSE, TOGGLE_SHUFFLE, CYCLE_REPEAT, FAVORITE }

/**
 * Maps a lowercase sentence to a player command and its tool name, or null.
 * Whole-word matches only, so "toca" inside "que música toca?" never triggers a command.
 */
internal fun playerCommandFor(lower: String): Pair<PlayerCommand, String>? {
  fun has(vararg words: String) = Regex("\\b(${words.joinToString("|")})\\b").containsMatchIn(lower)
  return when {
    has("próxima", "proxima", "próximo", "proximo", "pular") -> PlayerCommand.NEXT to "next_track"
    has("anterior", "voltar") -> PlayerCommand.PREVIOUS to "previous_track"
    has("pausar", "pause", "pausa") -> PlayerCommand.PAUSE to "pause"
    has("tocar", "toque", "continuar", "retomar", "play", "reproduzir") -> PlayerCommand.PLAY to "play"
    has("aleatório", "aleatorio", "embaralhar", "shuffle") -> PlayerCommand.TOGGLE_SHUFFLE to "toggle_shuffle"
    has("repetir", "repetição", "repeat") -> PlayerCommand.CYCLE_REPEAT to "cycle_repeat"
    has("favoritar", "curtir", "favorito") -> PlayerCommand.FAVORITE to "toggle_favorite"
    else -> null
  }
}

// Answers to a pending confirmation, compared after normalizeForSearch (lowercase, no accents).
private val CONFIRM_WORDS = setOf("sim", "s", "yes", "y", "ok", "confirmo", "confirmar", "confirm", "pode")
private val DECLINE_WORDS = setOf("nao", "n", "no", "cancelar", "cancela", "cancele", "cancel")

class AssistantEngine(
  // Reply texts come from the app's resources, so they follow the system language.
  private val context: Context,
  private val musicRepository: MusicRepository,
  private val curiosityRepository: CuriosityRepository,
  private val audioEngineManager: AudioEngineManager,
  private val onTimerCreated: (LocalTimer) -> Unit,
  private val onTimerCancelled: (String) -> Unit,
  private val onCuriosityUpdated: (Curiosity?) -> Unit = {},
  // Returns a short description of the result, or null when the command could not run.
  private val onPlayerCommand: suspend (PlayerCommand) -> String? = { null },
  // Loads an equalizer preset through the app, so the choice is saved like a tap on the screen.
  private val onEqPreset: (EqPreset) -> Unit = { audioEngineManager.applyEqSettings(it.settings) },
  // Moves the bass or treble tone control by a number of dB; returns the new value, clamped.
  private val onTone: (bass: Boolean, deltaDb: Float) -> Float = { bass, delta ->
    val s = audioEngineManager.eqSettings.value
    val r = com.example.data.model.EqSettings.TONE_RANGE_DB
    val next = if (bass) s.copy(bassDb = (s.bassDb + delta).coerceIn(-r, r)) else s.copy(trebleDb = (s.trebleDb + delta).coerceIn(-r, r))
    audioEngineManager.applyEqSettings(next)
    if (bass) next.bassDb else next.trebleDb
  },
  // Where the callbacks above run: they change screen state, so the app keeps them on the main thread.
  private val callbackContext: CoroutineContext = Dispatchers.Main.immediate,
  // What the assistant can change in the app. Only runs after the user confirms it.
  private val actions: AssistantActions = NoAssistantActions
) {
  private val TAG = "AssistantEngine"
  private val okHttpClient = Http.client

  // A state-changing request waiting for "sim" or "não". Only one at a time; a new request replaces it.
  private class PendingAction(
    val request: AssistantRequest,
    val label: String,
    val tracks: List<Track>,
    val question: String
  )

  @Volatile private var pending: PendingAction? = null

  val hasGeminiKey: Boolean
    get() {
      val key = try {
        BuildConfig.GEMINI_API_KEY
      } catch (_: Exception) {
        ""
      }
      return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

  suspend fun processUserMessage(
    userInput: String,
    useGeminiIfAvailable: Boolean = true
  ): AssistantMessage = withContext(Dispatchers.IO) {
    val trimmed = userInput.trim()

    // 0. A confirmation is answered before anything else, so "sim" never reaches the other rules.
    answerPending(trimmed)?.let { return@withContext it }

    // 1. Check for deterministic local commands first
    val localToolExecution = tryParseLocalTool(trimmed)
    if (localToolExecution != null) {
      return@withContext localToolExecution
    }

    // 2. If Gemini is available, ask it. A failure gets its own message; it is not hidden as "did not understand".
    if (useGeminiIfAvailable && hasGeminiKey) {
      return@withContext try {
        callGeminiApi(trimmed)
      } catch (e: AssistantFailureException) {
        Diagnostics.record(TAG, "online reply failed: ${e.failure.name}")
        failureMessage(e.failure)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Only the exception type is recorded: its message could carry text from the request.
        Diagnostics.record(TAG, "online reply failed: unexpected ${e.javaClass.simpleName}")
        failureMessage(AssistantFailure.SERVER)
      }
    }

    // 3. Fallback to intelligent local rules response
    generateLocalRuleResponse(trimmed)
  }

  // Parses natural language and direct tool commands
  private suspend fun tryParseLocalTool(input: String): AssistantMessage? {
    val lower = input.lowercase()

    // Command: HELP / AJUDA (Only here does it describe all its functions!)
    if (lower == "help" || lower == "ajuda" || lower == "?") {
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = context.getString(R.string.assistant_help_body)
      )
    }

    // State-changing requests (library, queue, sleep timer, spatial, volume): asked for confirmation first.
    assistantRequestFor(lower)?.let { request ->
      return requestReply(request)
    }

    // Player controls: run through the app and report the real result
    playerCommandFor(lower)?.let { (command, toolName) ->
      val result = onPlayerCommand(command)
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = result ?: context.getString(R.string.assistant_command_failed),
        toolReport = ToolExecutionReport(
          toolName = toolName,
          arguments = emptyMap(),
          success = result != null,
          resultSummary = result ?: context.getString(R.string.assistant_tool_no_track_available)
        )
      )
    }

    // Feature: Analisar a música em reprodução e pesquisar curiosidades
    if (lower.contains("analis") || lower.contains("pesquisar curiosidade") || lower.contains("curiosidade")) {
      val track = musicRepository.currentTrack.value
      if (track != null) {
        val curiosity = curiosityRepository.getCuriosityForTrack(track)
        // The callbacks touch view-model state and audio effects: they run on the main thread.
        withContext(callbackContext) { onCuriosityUpdated(curiosity) }

        val report = ToolExecutionReport(
          toolName = "analyze_and_research_music",
          arguments = mapOf("track" to track.title, "artist" to track.artist),
          success = curiosity != null,
          resultSummary = curiosity?.let {
            context.getString(R.string.assistant_tool_analysis_done, context.getString(it.status.labelRes))
          } ?: context.getString(R.string.assistant_tool_no_curiosity)
        )

        // Curiosities are shown only in the player and the notification, never in the chat, so the
        // answer points there instead of repeating the text.
        val siriStyleAnswer = if (curiosity != null) {
          context.getString(R.string.assistant_analysis_done, track.title, track.artist)
        } else {
          context.getString(R.string.assistant_analysis_none, track.title)
        }

        return AssistantMessage(
          id = UUID.randomUUID().toString(),
          sender = MessageSender.ASSISTANT,
          text = siriStyleAnswer,
          toolReport = report
        )
      } else {
        return AssistantMessage(
          id = UUID.randomUUID().toString(),
          sender = MessageSender.ASSISTANT,
          text = context.getString(R.string.assistant_no_track_analyze)
        )
      }
    }

    // Tool: get_current_track
    if (lower.contains("que música") || lower.contains("que musica") || lower.contains("faixa atual") || lower.contains("tocando agora")) {
      val track = musicRepository.currentTrack.value
      val report = ToolExecutionReport(
        toolName = "get_current_track",
        arguments = emptyMap(),
        success = track != null,
        resultSummary = track?.title ?: context.getString(R.string.assistant_tool_no_track)
      )
      val reply = if (track != null) {
        context.getString(R.string.assistant_now_playing_detail, track.title, track.artist, track.album)
      } else {
        context.getString(R.string.assistant_no_track_playing)
      }
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = reply,
        toolReport = report
      )
    }

    // Tool: get_audio_capabilities
    if (lower.contains("diagnóstico") || lower.contains("diagnostico") || lower.contains("hardware") || Regex("\\brota\\b").containsMatchIn(lower)) {
      val caps = audioEngineManager.capabilities.value
      val report = ToolExecutionReport(
        toolName = "get_audio_capabilities",
        arguments = emptyMap(),
        success = true,
        resultSummary = "${caps.activeRouteName} (${caps.sampleRateHz} Hz)"
      )
      val reply = context.getString(R.string.assistant_audio_route, caps.activeRouteName, caps.sampleRateHz.toString())
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = reply,
        toolReport = report
      )
    }

    // Tool: adjust_tone. "aumentar graves", "mais agudos", "menos grave", "abaixar os agudos".
    val toneMatch = Regex("""\b(aumentar|aumenta|mais|subir|sobe|diminuir|diminui|menos|abaixar|abaixa|reduzir|reduz|tirar)\b.*\b(graves?|agudos?)\b""").find(lower)
    if (toneMatch != null) {
      val up = toneMatch.groupValues[1] in setOf("aumentar", "aumenta", "mais", "subir", "sobe")
      val bass = toneMatch.groupValues[2].startsWith("grave")
      val now = withContext(callbackContext) { onTone(bass, if (up) 3f else -3f) }
      val name = context.getString(if (bass) R.string.assistant_band_bass else R.string.assistant_band_treble)
      val value = (if (now > 0) "+" else "") + String.format(java.util.Locale.US, "%.1f", now)
      val limit = com.example.data.model.EqSettings.TONE_RANGE_DB
      val atLimit = kotlin.math.abs(now) >= limit
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = if (atLimit) {
          context.getString(R.string.assistant_tone_at_limit, name, value)
        } else {
          context.getString(R.string.assistant_tone_set, name, value)
        },
        toolReport = ToolExecutionReport(
          toolName = "adjust_tone",
          arguments = mapOf("band" to name, "delta" to if (up) "+3" else "-3"),
          success = true,
          resultSummary = "$name $value dB"
        )
      )
    }

    // Tool: apply_audio_profile
    if (lower.contains("perfil") || lower.contains("equalizador") || lower.contains("preset") || Regex("\\bsom\\b").containsMatchIn(lower)) {
      fun preset(id: String) = EqPresets.BUILT_IN.firstOrNull { it.id == id }
      val target = when {
        lower.contains("flat") || lower.contains("linear") || lower.contains("neutro") -> EqPresets.FLAT
        lower.contains("prog") || lower.contains("rock") -> preset(AudioProfile.PROG_ROCK.id)
        lower.contains("acústic") || lower.contains("acustic") -> preset(AudioProfile.ACOUSTIC_CLARITY.id)
        lower.contains("podcast") -> preset("preset_speech")
        lower.contains("vocal") || lower.contains("voz") -> preset(AudioProfile.VOCAL_PRESENCE.id)
        lower.contains("grave") || lower.contains("bass") -> preset(AudioProfile.BASS_PUNCH.id)
        lower.contains("pop") -> preset("preset_pop")
        lower.contains("jazz") -> preset("preset_jazz")
        lower.contains("clássic") || lower.contains("classic") -> preset("preset_classical")
        lower.contains("eletr") || lower.contains("electr") -> preset("preset_electronic")
        lower.contains("loudness") -> preset("preset_loudness")
        lower.contains("agudo") -> preset("preset_treble")
        else -> null
      }
      if (target != null) {
        withContext(callbackContext) { onEqPreset(target) }
        val report = ToolExecutionReport(
          toolName = "apply_audio_profile",
          arguments = mapOf("profile" to target.name),
          success = true,
          resultSummary = context.getString(R.string.assistant_tool_preset_applied, target.name)
        )
        return AssistantMessage(
          id = UUID.randomUUID().toString(),
          sender = MessageSender.ASSISTANT,
          text = context.getString(R.string.assistant_eq_set, target.name),
          toolReport = report
        )
      }
    }

    // Tool: create_timer
    val timerRegex = Regex("""(?:timer|temporizador|alarme)\s*(?:de)?\s*(\d+)\s*(minutos?|min|m|segundos?|seg|s)?""", RegexOption.IGNORE_CASE)
    val timerMatch = timerRegex.find(lower)
    if (timerMatch != null) {
      val amount = timerMatch.groupValues[1].toIntOrNull() ?: 1
      val unit = timerMatch.groupValues[2].lowercase()
      val totalSeconds = when {
        unit.startsWith("s") -> amount
        else -> amount * 60
      }.coerceIn(5, 7200)

      val timerId = "timer_${System.currentTimeMillis()}"
      val label = context.getString(R.string.assistant_timer_label, totalSeconds / 60, totalSeconds % 60)
      val newTimer = LocalTimer(
        id = timerId,
        label = label,
        initialSeconds = totalSeconds,
        remainingSeconds = totalSeconds,
        isRunning = true
      )
      withContext(callbackContext) { onTimerCreated(newTimer) }

      val report = ToolExecutionReport(
        toolName = "create_timer",
        arguments = mapOf("duration_seconds" to totalSeconds.toString()),
        success = true,
        resultSummary = "${totalSeconds}s"
      )
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = context.getString(R.string.assistant_timer_set, newTimer.formattedTime()),
        toolReport = report
      )
    }

    // Tool: cancel_timer
    if (lower.contains("cancelar timer") || lower.contains("parar timer")) {
      onTimerCancelled("all")
      val report = ToolExecutionReport(
        toolName = "cancel_timer",
        arguments = emptyMap(),
        success = true,
        resultSummary = context.getString(R.string.assistant_tool_timers_cancelled)
      )
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = context.getString(R.string.assistant_timers_cancelled),
        toolReport = report
      )
    }

    return null
  }

  private fun reply(text: String, report: ToolExecutionReport? = null) = AssistantMessage(
    id = UUID.randomUUID().toString(),
    sender = MessageSender.ASSISTANT,
    text = text,
    toolReport = report
  )

  private fun minutesText(minutes: Int): String =
    context.resources.getQuantityString(R.plurals.assistant_minutes, minutes, minutes)

  private fun tracksText(count: Int): String =
    context.resources.getQuantityString(R.plurals.assistant_tracks_count, count, count)

  // Asks for confirmation. Nothing changes until the user answers "sim".
  private fun requestReply(request: AssistantRequest): AssistantMessage = when (request) {
    is AssistantRequest.PlayLibrary -> libraryRequest(request, request.query, asNext = null)
    is AssistantRequest.QueueLibrary -> libraryRequest(request, request.query, asNext = request.asNext)
    else -> {
      val question = simpleQuestion(request)
      pending = PendingAction(request, label = "", tracks = emptyList(), question = question)
      reply(question)
    }
  }

  private fun libraryRequest(request: AssistantRequest, query: String, asNext: Boolean?): AssistantMessage {
    val library = actions.library()
    val matches = findInLibrary(query, library.tracks, library.playlists)
    if (matches.isEmpty()) return reply(context.getString(R.string.assistant_library_none, query))
    if (matches.size > 1) {
      // Several matches: the user picks a name, the engine never guesses one.
      val options = matches.take(5).joinToString(", ") { it.label }
      return reply(context.getString(R.string.assistant_library_many, options))
    }
    val match = matches.single()
    val count = tracksText(match.tracks.size)
    val question = when (asNext) {
      null -> context.getString(R.string.assistant_confirm_play, match.label, count)
      true -> context.getString(R.string.assistant_confirm_queue_next, match.label, count)
      false -> context.getString(R.string.assistant_confirm_queue_end, match.label, count)
    }
    pending = PendingAction(request, match.label, match.tracks, question)
    return reply(question)
  }

  private fun simpleQuestion(request: AssistantRequest): String = when (request) {
    AssistantRequest.ClearQueue -> context.getString(R.string.assistant_confirm_clear_queue)
    is AssistantRequest.SleepIn -> context.getString(R.string.assistant_confirm_sleep, minutesText(request.minutes))
    AssistantRequest.SleepAtEndOfTrack -> context.getString(R.string.assistant_confirm_sleep_end)
    AssistantRequest.CancelSleep -> context.getString(R.string.assistant_confirm_cancel_sleep)
    is AssistantRequest.Spatial -> context.getString(
      if (request.on) R.string.assistant_confirm_spatial_on else R.string.assistant_confirm_spatial_off
    )
    is AssistantRequest.Volume -> context.getString(R.string.assistant_confirm_volume, request.percent)
    is AssistantRequest.PlayLibrary, is AssistantRequest.QueueLibrary -> ""
  }

  // Answers a waiting confirmation. Anything other than yes or no repeats the question and keeps it waiting.
  private suspend fun answerPending(input: String): AssistantMessage? {
    val current = pending ?: return null
    val answer = normalizeForSearch(input).trim().trimEnd('.', '!', '?').trim()
    return when (answer) {
      in CONFIRM_WORDS -> {
        pending = null
        // The app is changed on the main thread, like the other callbacks.
        reply(withContext(callbackContext) { execute(current) })
      }
      in DECLINE_WORDS -> {
        pending = null
        reply(context.getString(R.string.assistant_confirm_cancelled))
      }
      else -> reply(current.question + " " + context.getString(R.string.assistant_confirm_answer_hint))
    }
  }

  private fun execute(action: PendingAction): String {
    val ok: Boolean
    val done: String
    when (val r = action.request) {
      is AssistantRequest.PlayLibrary -> {
        ok = actions.playTracks(action.tracks)
        done = context.getString(R.string.assistant_done_play, action.label)
      }
      is AssistantRequest.QueueLibrary -> {
        ok = actions.queueTracks(action.tracks, r.asNext)
        done = context.getString(
          if (r.asNext) R.string.assistant_done_queue_next else R.string.assistant_done_queue_end,
          action.label
        )
      }
      AssistantRequest.ClearQueue -> {
        ok = actions.clearQueue()
        done = context.getString(R.string.assistant_done_clear_queue)
      }
      is AssistantRequest.SleepIn -> {
        ok = actions.sleepIn(r.minutes)
        done = context.getString(R.string.assistant_done_sleep, minutesText(r.minutes))
      }
      AssistantRequest.SleepAtEndOfTrack -> {
        ok = actions.sleepAtEndOfTrack()
        done = context.getString(R.string.assistant_done_sleep_end)
      }
      AssistantRequest.CancelSleep -> {
        ok = actions.cancelSleep()
        done = context.getString(R.string.assistant_done_cancel_sleep)
      }
      is AssistantRequest.Spatial -> {
        ok = actions.setSpatial(r.on)
        done = context.getString(if (r.on) R.string.assistant_done_spatial_on else R.string.assistant_done_spatial_off)
      }
      is AssistantRequest.Volume -> {
        ok = actions.setVolumePercent(r.percent)
        done = context.getString(R.string.assistant_done_volume, r.percent)
      }
    }
    return if (ok) done else context.getString(R.string.assistant_action_unavailable)
  }

  private fun generateLocalRuleResponse(input: String): AssistantMessage {
    val lower = input.lowercase()

    val responseText = when {
      Regex("\\b(olá|ola|oi)\\b").containsMatchIn(lower) -> context.getString(R.string.assistant_hello)
      else -> context.getString(R.string.assistant_not_understood)
    }

    return AssistantMessage(
      id = UUID.randomUUID().toString(),
      sender = MessageSender.ASSISTANT,
      text = responseText
    )
  }

  private fun failureMessage(failure: AssistantFailure) = AssistantMessage(
    id = UUID.randomUUID().toString(),
    sender = MessageSender.ASSISTANT,
    text = "",
    failure = failure
  )

  // Optional Gemini API execution. Throws AssistantFailureException with the reason when no reply arrives.
  private fun callGeminiApi(prompt: String): AssistantMessage {
    val apiKey = BuildConfig.GEMINI_API_KEY
    // processUserMessage only calls this when a key is set, so a blank key here is a build problem.
    if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") throw AssistantFailureException(AssistantFailure.KEY_REFUSED)

    val currentTrack = musicRepository.currentTrack.value

    val systemContext = """
      Você é um assistente de voz e áudio estilo Siri/Google Assistant no app Tarkas.
      Responda de forma direta, concisa (máximo 40 palavras), cortês e em português brasileiro.
      Não repita listas de comandos a menos que o usuário peça 'help' ou 'ajuda'.
      Nunca conte curiosidades, história ou fatos sobre músicas, artistas ou álbuns: isso só aparece no player do app.
      Se pedirem, diga que as curiosidades ficam no player.
      Música tocando agora: '${currentTrack?.title ?: "Nenhuma"}' (${currentTrack?.artist ?: ""}).
    """.trimIndent()

    // Key goes in a header, not the URL, so it never lands in logs or proxies.
    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    val payload = JSONObject().apply {
      put("systemInstruction", JSONObject().apply {
        put("parts", JSONArray().put(JSONObject().put("text", systemContext)))
      })
      put("contents", JSONArray().put(JSONObject().apply {
        put("role", "user")
        put("parts", JSONArray().put(JSONObject().put("text", prompt)))
      }))
      put("generationConfig", JSONObject().apply {
        put("temperature", 0.2)
        put("maxOutputTokens", 120)
      })
    }

    val body = payload.toString().toRequestBody("application/json".toMediaType())
    val request = Request.Builder().url(url).header("x-goog-api-key", apiKey).post(body).build()

    return try {
      okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw AssistantFailureException(failureForHttpStatus(response.code))
        val respBody = response.body?.string() ?: throw AssistantFailureException(AssistantFailure.NO_REPLY)
        AssistantMessage(
          id = UUID.randomUUID().toString(),
          sender = MessageSender.ASSISTANT,
          text = geminiReplyText(respBody)
        )
      }
    } catch (e: IOException) {
      throw AssistantFailureException(AssistantFailure.NO_CONNECTION, e)
    }
  }
}
