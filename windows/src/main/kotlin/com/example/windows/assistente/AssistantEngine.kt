package com.example.windows.assistente

import com.example.data.model.AudioProfile
import com.example.data.model.EqPreset
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import com.example.data.model.Track
import com.example.windows.res.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.UUID

// Port of app/.../assistente/funcoes/AssistantEngine.kt. Same order of rules; the Android Context,
// repositories and callbacks became [AssistantHost], [AssistantTexts] and [GeminiTransport].

/** Raised when the online reply cannot be used. The reason decides the message the user sees. */
class AssistantFailureException(val failure: AssistantFailure, cause: Throwable? = null) : Exception(failure.name, cause)

internal fun failureForHttpStatus(code: Int): AssistantFailure = when (code) {
  401, 403 -> AssistantFailure.KEY_REFUSED
  429 -> AssistantFailure.QUOTA
  else -> AssistantFailure.SERVER
}

/** Reads the reply text from a generateContent body. A reply cut at the output limit is discarded. */
internal fun geminiReplyText(body: String): String {
  val json = try { JSONObject(body) } catch (_: JSONException) { throw AssistantFailureException(AssistantFailure.NO_REPLY) }
  val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
    ?: throw AssistantFailureException(AssistantFailure.NO_REPLY)
  if (candidate.optString("finishReason") == "MAX_TOKENS") throw AssistantFailureException(AssistantFailure.CUT_OFF)
  val text = candidate.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
  if (text.isNullOrBlank()) throw AssistantFailureException(AssistantFailure.NO_REPLY)
  return text.trim()
}

/** One POST to the online service. Returns the HTTP status and body; throws IOException when unreachable. */
fun interface GeminiTransport {
  fun post(apiKey: String, body: String): Pair<Int, String>
}

/** What the app lends the engine. */
interface AssistantHost : AssistantActions {
  fun currentTrack(): Track?
  /** Short description of the result, or null when the command could not run. */
  suspend fun playerCommand(command: PlayerCommand): String?
  fun eqSettings(): EqSettings
  fun applyEq(settings: EqSettings)
  /** Active output name and its sample rate in Hz. */
  fun audioRoute(): Pair<String, Int>
  fun createTimer(id: String, label: String, seconds: Int)
  fun cancelTimers()
  fun curiosityFor(track: Track): Curiosity?
  fun curiosityUpdated(curiosity: Curiosity?)
}

private val CONFIRM_WORDS = setOf("sim", "s", "yes", "y", "ok", "confirmo", "confirmar", "confirm", "pode")
private val DECLINE_WORDS = setOf("nao", "n", "no", "cancelar", "cancela", "cancele", "cancel")

class AssistantEngine(
  private val texts: AssistantTexts,
  private val host: AssistantHost,
  private val keys: ApiKeyStore,
  private val transport: GeminiTransport = HttpGeminiTransport
) {
  private class PendingAction(val request: AssistantRequest, val label: String, val tracks: List<Track>, val question: String)

  @Volatile private var pending: PendingAction? = null
  @Volatile private var eqBeforeChat: EqSettings? = null

  val hasGeminiKey: Boolean get() = keys.load() != null

  suspend fun processUserMessage(userInput: String, useGeminiIfAvailable: Boolean = true): AssistantMessage =
    withContext(Dispatchers.IO) {
      val trimmed = userInput.trim()
      answerPending(trimmed)?.let { return@withContext it }
      tryParseLocalTool(trimmed)?.let { return@withContext it }
      if (useGeminiIfAvailable && hasGeminiKey) {
        return@withContext try {
          callGeminiApi(trimmed)
        } catch (e: AssistantFailureException) {
          failureMessage(e.failure)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          failureMessage(AssistantFailure.SERVER)
        }
      }
      generateLocalRuleResponse(trimmed)
    }

  private fun t(res: StringResource, vararg a: Any) = texts.text(res, *a)

  private suspend fun tryParseLocalTool(input: String): AssistantMessage? {
    val lower = input.lowercase()
    if (lower == "help" || lower == "ajuda" || lower == "?") return reply(t(Res.string.assistant_help_body))

    if (lower == "desfazer" || lower == "undo") {
      val before = eqBeforeChat
      eqBeforeChat = null
      if (before == null) return reply(t(Res.string.assistant_undo_none))
      host.applyEq(before)
      return reply(t(Res.string.assistant_undo_done))
    }

    assistantRequestFor(lower)?.let { return requestReply(it) }

    playerCommandFor(lower)?.let { (command, toolName) ->
      val result = host.playerCommand(command)
      return reply(
        result ?: t(Res.string.assistant_command_failed),
        ToolExecutionReport(toolName, emptyMap(), result != null, result ?: t(Res.string.assistant_tool_no_track_available))
      )
    }

    if (lower.contains("analis") || lower.contains("pesquisar curiosidade") || lower.contains("curiosidade")) {
      val track = host.currentTrack() ?: return reply(t(Res.string.assistant_no_track_analyze))
      val curiosity = host.curiosityFor(track)
      host.curiosityUpdated(curiosity)
      val report = ToolExecutionReport(
        "analyze_and_research_music",
        mapOf("track" to track.title, "artist" to track.artist),
        curiosity != null,
        curiosity?.let { t(Res.string.assistant_tool_analysis_done, t(it.status.label)) }
          ?: t(Res.string.assistant_tool_no_curiosity)
      )
      val answer = if (curiosity != null) t(Res.string.assistant_analysis_done, track.title, track.artist)
      else t(Res.string.assistant_analysis_none, track.title)
      return reply(answer, report)
    }

    if (lower.contains("que música") || lower.contains("que musica") || lower.contains("faixa atual") || lower.contains("tocando agora")) {
      val track = host.currentTrack()
      val report = ToolExecutionReport("get_current_track", emptyMap(), track != null, track?.title ?: t(Res.string.assistant_tool_no_track))
      val text = if (track != null) t(Res.string.assistant_now_playing_detail, track.title, track.artist, track.album)
      else t(Res.string.assistant_no_track_playing)
      return reply(text, report)
    }

    if (lower.contains("diagnóstico") || lower.contains("diagnostico") || lower.contains("diagnostic") ||
      lower.contains("hardware") || Regex("\\brota\\b").containsMatchIn(lower)
    ) {
      val (route, rate) = host.audioRoute()
      return reply(
        t(Res.string.assistant_audio_route, route, rate.toString()),
        ToolExecutionReport("get_audio_capabilities", emptyMap(), true, "$route ($rate Hz)")
      )
    }

    val toneMatch = Regex("""\b(aumentar|aumenta|mais|subir|sobe|diminuir|diminui|menos|abaixar|abaixa|reduzir|reduz|tirar)\b.*\b(graves?|agudos?)\b""").find(lower)
    if (toneMatch != null) {
      val up = toneMatch.groupValues[1] in setOf("aumentar", "aumenta", "mais", "subir", "sobe")
      val bass = toneMatch.groupValues[2].startsWith("grave")
      val before = host.eqSettings()
      eqBeforeChat = before
      val r = EqSettings.TONE_RANGE_DB
      val delta = if (up) 3f else -3f
      val next = if (bass) before.copy(bassDb = (before.bassDb + delta).coerceIn(-r, r))
      else before.copy(trebleDb = (before.trebleDb + delta).coerceIn(-r, r))
      host.applyEq(next)
      val now = if (bass) next.bassDb else next.trebleDb
      val name = t(if (bass) Res.string.assistant_band_bass else Res.string.assistant_band_treble)
      val value = (if (now > 0) "+" else "") + String.format(Locale.US, "%.1f", now)
      val atLimit = kotlin.math.abs(now) >= r
      return reply(
        if (atLimit) t(Res.string.assistant_tone_at_limit, name, value) else t(Res.string.assistant_tone_set, name, value),
        ToolExecutionReport("adjust_tone", mapOf("band" to name, "delta" to if (up) "+3" else "-3"), true, "$name $value dB")
      )
    }

    if (lower.contains("perfil") || lower.contains("equalizador") || lower.contains("equalizer") ||
      lower.contains("preset") || Regex("\\bsom\\b").containsMatchIn(lower)
    ) {
      val target = eqPresetFor(lower)
      if (target != null) {
        eqBeforeChat = host.eqSettings()
        host.applyEq(target.settings)
        return reply(
          t(Res.string.assistant_eq_set, target.name),
          ToolExecutionReport("apply_audio_profile", mapOf("profile" to target.name), true, t(Res.string.assistant_tool_preset_applied, target.name))
        )
      }
    }

    val timerMatch = Regex("""(?:timer|temporizador|alarme)\s*(?:de)?\s*(\d+)\s*(minutos?|min|m|segundos?|seg|s)?""", RegexOption.IGNORE_CASE).find(lower)
    if (timerMatch != null) {
      val amount = timerMatch.groupValues[1].toIntOrNull() ?: 1
      val unit = timerMatch.groupValues[2].lowercase()
      val totalSeconds = (if (unit.startsWith("s")) amount else amount * 60).coerceIn(5, 7200)
      val label = t(Res.string.assistant_timer_label, totalSeconds / 60, totalSeconds % 60)
      host.createTimer("timer_${System.currentTimeMillis()}", label, totalSeconds)
      val clock = String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
      return reply(
        t(Res.string.assistant_timer_set, clock),
        ToolExecutionReport("create_timer", mapOf("duration_seconds" to totalSeconds.toString()), true, "${totalSeconds}s")
      )
    }

    if (lower.contains("cancelar timer") || lower.contains("parar timer")) {
      host.cancelTimers()
      return reply(
        t(Res.string.assistant_timers_cancelled),
        ToolExecutionReport("cancel_timer", emptyMap(), true, t(Res.string.assistant_tool_timers_cancelled))
      )
    }
    return null
  }

  private fun eqPresetFor(lower: String): EqPreset? {
    fun preset(id: String) = EqPresets.BUILT_IN.firstOrNull { it.id == id }
    return when {
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
  }

  private fun reply(text: String, report: ToolExecutionReport? = null) =
    AssistantMessage(UUID.randomUUID().toString(), MessageSender.ASSISTANT, text, toolReport = report)

  private fun minutesText(m: Int) = texts.plural(Res.plurals.assistant_minutes, m, m)
  private fun tracksText(n: Int) = texts.plural(Res.plurals.assistant_tracks_count, n, n)

  private fun requestReply(request: AssistantRequest): AssistantMessage = when (request) {
    is AssistantRequest.PlayLibrary -> libraryRequest(request, request.query, asNext = null)
    is AssistantRequest.QueueLibrary -> libraryRequest(request, request.query, asNext = request.asNext)
    else -> {
      val question = simpleQuestion(request)
      pending = PendingAction(request, "", emptyList(), question)
      reply(question)
    }
  }

  private fun libraryRequest(request: AssistantRequest, query: String, asNext: Boolean?): AssistantMessage {
    val library = host.library()
    val matches = findInLibrary(query, library.tracks, library.playlists)
    if (matches.isEmpty()) return reply(t(Res.string.assistant_library_none, query))
    if (matches.size > 1) return reply(t(Res.string.assistant_library_many, matches.take(5).joinToString(", ") { it.label }))
    val match = matches.single()
    val count = tracksText(match.tracks.size)
    val question = when (asNext) {
      null -> t(Res.string.assistant_confirm_play, match.label, count)
      true -> t(Res.string.assistant_confirm_queue_next, match.label, count)
      false -> t(Res.string.assistant_confirm_queue_end, match.label, count)
    }
    pending = PendingAction(request, match.label, match.tracks, question)
    return reply(question)
  }

  private fun simpleQuestion(request: AssistantRequest): String = when (request) {
    AssistantRequest.ClearQueue -> t(Res.string.assistant_confirm_clear_queue)
    is AssistantRequest.SleepIn -> t(Res.string.assistant_confirm_sleep, minutesText(request.minutes))
    AssistantRequest.SleepAtEndOfTrack -> t(Res.string.assistant_confirm_sleep_end)
    AssistantRequest.CancelSleep -> t(Res.string.assistant_confirm_cancel_sleep)
    is AssistantRequest.Spatial -> t(if (request.on) Res.string.assistant_confirm_spatial_on else Res.string.assistant_confirm_spatial_off)
    is AssistantRequest.Volume -> t(Res.string.assistant_confirm_volume, request.percent)
    is AssistantRequest.PlayLibrary, is AssistantRequest.QueueLibrary -> ""
  }

  private fun answerPending(input: String): AssistantMessage? {
    val current = pending ?: return null
    val answer = normalizeForSearch(input).trim().trimEnd('.', '!', '?').trim()
    return when (answer) {
      in CONFIRM_WORDS -> { pending = null; reply(execute(current)) }
      in DECLINE_WORDS -> { pending = null; reply(t(Res.string.assistant_confirm_cancelled)) }
      else -> reply(current.question + " " + t(Res.string.assistant_confirm_answer_hint))
    }
  }

  private fun execute(action: PendingAction): String {
    val ok: Boolean
    val done: String
    when (val r = action.request) {
      is AssistantRequest.PlayLibrary -> { ok = host.playTracks(action.tracks); done = t(Res.string.assistant_done_play, action.label) }
      is AssistantRequest.QueueLibrary -> {
        ok = host.queueTracks(action.tracks, r.asNext)
        done = t(if (r.asNext) Res.string.assistant_done_queue_next else Res.string.assistant_done_queue_end, action.label)
      }
      AssistantRequest.ClearQueue -> { ok = host.clearQueue(); done = t(Res.string.assistant_done_clear_queue) }
      is AssistantRequest.SleepIn -> { ok = host.sleepIn(r.minutes); done = t(Res.string.assistant_done_sleep, minutesText(r.minutes)) }
      AssistantRequest.SleepAtEndOfTrack -> { ok = host.sleepAtEndOfTrack(); done = t(Res.string.assistant_done_sleep_end) }
      AssistantRequest.CancelSleep -> { ok = host.cancelSleep(); done = t(Res.string.assistant_done_cancel_sleep) }
      is AssistantRequest.Spatial -> {
        ok = host.setSpatial(r.on)
        done = t(if (r.on) Res.string.assistant_done_spatial_on else Res.string.assistant_done_spatial_off)
      }
      is AssistantRequest.Volume -> { ok = host.setVolumePercent(r.percent); done = t(Res.string.assistant_done_volume, r.percent) }
    }
    return if (ok) done else t(Res.string.assistant_action_unavailable)
  }

  private fun generateLocalRuleResponse(input: String): AssistantMessage {
    val lower = input.lowercase()
    return reply(
      if (Regex("\\b(olá|ola|oi)\\b").containsMatchIn(lower)) t(Res.string.assistant_hello) else t(Res.string.assistant_not_understood)
    )
  }

  private fun failureMessage(failure: AssistantFailure) =
    AssistantMessage(UUID.randomUUID().toString(), MessageSender.ASSISTANT, "", failure = failure)

  private suspend fun callGeminiApi(prompt: String): AssistantMessage {
    val apiKey = keys.load() ?: throw AssistantFailureException(AssistantFailure.KEY_REFUSED)
    val currentTrack = host.currentTrack()
    val systemContext = """
      Você é um assistente de voz e áudio estilo Siri/Google Assistant no app Tarkas.
      Responda de forma direta, concisa (máximo 40 palavras), cortês e em português brasileiro.
      Não repita listas de comandos a menos que o usuário peça 'help' ou 'ajuda'.
      Nunca conte curiosidades, história ou fatos sobre músicas, artistas ou álbuns: isso só aparece no player do app.
      Se pedirem, diga que as curiosidades ficam no player.
      Para pedidos de controle do player (próxima, anterior, pausar, tocar, aleatório, repetir, favoritar), chame a função correspondente.
      Música tocando agora: '${currentTrack?.title ?: "Nenhuma"}' (${currentTrack?.artist ?: ""}).
    """.trimIndent()

    val contents = JSONArray().put(geminiUserTurn(prompt))
    val first = postGemini(apiKey, geminiRequestJson(systemContext, contents))
    val toolName = geminiFunctionCall(first) ?: return reply(geminiReplyText(first))

    val command = geminiPlayerCommand(toolName)
    val result = if (command != null) host.playerCommand(command) else null
    contents.put(geminiFunctionCallTurn(toolName))
    contents.put(geminiFunctionResponseTurn(toolName, result ?: "failed"))
    val second = postGemini(apiKey, geminiRequestJson(systemContext, contents))
    return reply(
      geminiReplyText(second),
      ToolExecutionReport(toolName, emptyMap(), result != null, result ?: t(Res.string.assistant_tool_no_track_available))
    )
  }

  private fun postGemini(apiKey: String, payload: JSONObject): String {
    val (code, body) = try {
      transport.post(apiKey, payload.toString())
    } catch (e: IOException) {
      throw AssistantFailureException(AssistantFailure.NO_CONNECTION, e)
    }
    if (code !in 200..299) throw AssistantFailureException(failureForHttpStatus(code))
    return body
  }
}
