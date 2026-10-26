package com.example.data.assistant

import android.util.Log
import com.example.BuildConfig
import com.example.data.audio.AudioEngineManager
import com.example.data.model.AssistantMessage
import com.example.data.model.AudioProfile
import com.example.data.model.Curiosity
import com.example.data.model.EqPreset
import com.example.data.model.EqPresets
import com.example.data.model.LocalTimer
import com.example.data.model.MessageSender
import com.example.data.model.ToolExecutionReport
import com.example.data.model.VerificationStatus
import com.example.data.repository.CuriosityRepository
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

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

class AssistantEngine(
  private val musicRepository: MusicRepository,
  private val curiosityRepository: CuriosityRepository,
  private val audioEngineManager: AudioEngineManager,
  private val onTimerCreated: (LocalTimer) -> Unit,
  private val onTimerCancelled: (String) -> Unit,
  private val onCuriosityUpdated: (Curiosity?) -> Unit = {},
  // Returns a short description of the result, or null when the command could not run.
  private val onPlayerCommand: suspend (PlayerCommand) -> String? = { null },
  // Loads an equalizer preset through the app, so the choice is saved like a tap on the screen.
  private val onEqPreset: (EqPreset) -> Unit = { audioEngineManager.applyEqSettings(it.settings) }
) {
  private val TAG = "AssistantEngine"
  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

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

    // 1. Check for deterministic local commands first
    val localToolExecution = tryParseLocalTool(trimmed)
    if (localToolExecution != null) {
      return@withContext localToolExecution
    }

    // 2. If Gemini is available, query Gemini with tool instructions
    if (useGeminiIfAvailable && hasGeminiKey) {
      try {
        val geminiResponse = callGeminiApi(trimmed)
        if (geminiResponse != null) {
          return@withContext geminiResponse
        }
      } catch (e: Exception) {
        Log.e(TAG, "Gemini call failed, fallback to local", e)
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
      val helpText = """
        **Comandos Disponíveis:**
        • **"analisar música"**: pesquisa curiosidades e fatos da faixa em execução
        • **"diagnóstico"**: inspeciona rotas e hardware de áudio
        • **"equalizador flat"** (ou rock, pop, jazz, vocal, graves, agudos…): aplica um preset
        • **"timer de [X] minutos"**: inicia temporizador local
        • **"que música toca?"**: exibe a faixa atual
        • **"próxima"**, **"anterior"**, **"pausar"**, **"tocar"**: controlam o player
        • **"aleatório"**, **"repetir"**, **"favoritar"**: ligam o modo ou guardam a faixa
      """.trimIndent()
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = helpText
      )
    }

    // Player controls: run through the app and report the real result
    playerCommandFor(lower)?.let { (command, toolName) ->
      val result = onPlayerCommand(command)
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = result ?: "Não consegui executar isso agora. Escolha uma faixa primeiro.",
        toolReport = ToolExecutionReport(
          toolName = toolName,
          arguments = emptyMap(),
          success = result != null,
          resultSummary = result ?: "Nenhuma faixa disponível"
        )
      )
    }

    // Feature: Analisar a música em reprodução e pesquisar curiosidades
    if (lower.contains("analis") || lower.contains("pesquisar curiosidade") || lower.contains("curiosidade")) {
      val track = musicRepository.currentTrack.value
      if (track != null) {
        val curiosity = curiosityRepository.getCuriosityForTrack(track)
        onCuriosityUpdated(curiosity)

        val report = ToolExecutionReport(
          toolName = "analyze_and_research_music",
          arguments = mapOf("track" to track.title, "artist" to track.artist),
          success = curiosity != null,
          resultSummary = curiosity?.let { "Análise concluída: ${it.status.label}" } ?: "Sem curiosidade verificada"
        )

        // Curiosities are shown only in the player and the notification, never in the chat, so the
        // answer points there instead of repeating the text.
        val siriStyleAnswer = if (curiosity != null) {
          "Analisei **${track.title}** (${track.artist}). A curiosidade fica no player: toque no mini player acima da barra e depois na lâmpada."
        } else {
          "Ainda não tenho uma curiosidade verificada sobre **${track.title}**."
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
          text = "Nenhuma música está tocando no momento para ser analisada."
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
        resultSummary = track?.title ?: "Nenhuma faixa"
      )
      val reply = if (track != null) {
        "Tocando **${track.title}** por **${track.artist}** (${track.album})."
      } else {
        "Nenhuma faixa em reprodução."
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
      val reply = "Rota ativa: **${caps.activeRouteName}** (${caps.sampleRateHz} Hz, Estéreo 2.0). Efeitos operam na sessão interna do Tarkas."
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = reply,
        toolReport = report
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
        onEqPreset(target)
        val report = ToolExecutionReport(
          toolName = "apply_audio_profile",
          arguments = mapOf("profile" to target.name),
          success = true,
          resultSummary = "Preset ${target.name} ativado no equalizador"
        )
        return AssistantMessage(
          id = UUID.randomUUID().toString(),
          sender = MessageSender.ASSISTANT,
          text = "Equalizador ajustado para **${target.name}**.",
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
      val label = "Timer de ${totalSeconds / 60}m ${totalSeconds % 60}s"
      val newTimer = LocalTimer(
        id = timerId,
        label = label,
        initialSeconds = totalSeconds,
        remainingSeconds = totalSeconds,
        isRunning = true
      )
      onTimerCreated(newTimer)

      val report = ToolExecutionReport(
        toolName = "create_timer",
        arguments = mapOf("duration_seconds" to totalSeconds.toString()),
        success = true,
        resultSummary = "${totalSeconds}s"
      )
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = "Timer definido para **${newTimer.formattedTime()}**.",
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
        resultSummary = "Timers cancelados"
      )
      return AssistantMessage(
        id = UUID.randomUUID().toString(),
        sender = MessageSender.ASSISTANT,
        text = "Temporizadores cancelados.",
        toolReport = report
      )
    }

    return null
  }

  private fun generateLocalRuleResponse(input: String): AssistantMessage {
    val lower = input.lowercase()

    val responseText = when {
      Regex("\\b(olá|ola|oi)\\b").containsMatchIn(lower) -> {
        "Olá! Em que posso ajudar com sua música ou áudio hoje?"
      }
      lower.contains("tarkus") -> {
        "Tarkus é o clássico de 1971 do Emerson, Lake & Palmer. Uma suíte épica em compasso 5/4 com sintetizadores analógicos Moog."
      }
      else -> {
        "Comando recebido. Diga ou digite **help** para ver o que posso fazer."
      }
    }

    return AssistantMessage(
      id = UUID.randomUUID().toString(),
      sender = MessageSender.ASSISTANT,
      text = responseText
    )
  }

  // Optional Gemini API execution
  private fun callGeminiApi(prompt: String): AssistantMessage? {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") return null

    val currentTrack = musicRepository.currentTrack.value

    val systemContext = """
      Você é um assistente de voz e áudio estilo Siri/Google Assistant no app Tarkas.
      Responda de forma direta, concisa (máximo 40 palavras), cortês e em português brasileiro.
      Não repita listas de comandos a menos que o usuário peça 'help' ou 'ajuda'.
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

    val respBody = okHttpClient.newCall(request).execute().use { response ->
      if (!response.isSuccessful) return null
      response.body?.string()
    } ?: return null
    val jsonResp = JSONObject(respBody)
    val candidates = jsonResp.optJSONArray("candidates") ?: return null
    if (candidates.length() == 0) return null

    val content = candidates.getJSONObject(0).optJSONObject("content")
    val parts = content?.optJSONArray("parts")
    val text = parts?.getJSONObject(0)?.optString("text")

    if (text.isNullOrBlank()) return null

    return AssistantMessage(
      id = UUID.randomUUID().toString(),
      sender = MessageSender.ASSISTANT,
      text = text.trim()
    )
  }
}
