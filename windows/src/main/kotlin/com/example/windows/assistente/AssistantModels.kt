package com.example.windows.assistente

import com.example.windows.res.*
import org.jetbrains.compose.resources.StringResource
import java.util.concurrent.atomic.AtomicLong

enum class MessageSender { USER, ASSISTANT, TOOL, SYSTEM }

data class ToolExecutionReport(
  val toolName: String,
  val arguments: Map<String, String>,
  val success: Boolean,
  val resultSummary: String
)

/** Why the online reply did not reach the screen. The text is resolved by [AssistantTexts]. */
enum class AssistantFailure(val textKey: String, val retryable: Boolean) {
  NO_CONNECTION("assistant_error_no_connection", true),
  KEY_REFUSED("assistant_error_key_refused", false),
  QUOTA("assistant_error_quota", true),
  CUT_OFF("assistant_error_cut_off", true),
  NO_REPLY("assistant_error_no_reply", true),
  SERVER("assistant_error_server", true)
}

data class AssistantMessage(
  val id: String,
  val sender: MessageSender,
  val text: String,
  val timestampMs: Long = System.currentTimeMillis(),
  val toolReport: ToolExecutionReport? = null,
  val failure: AssistantFailure? = null
)

enum class PlayerCommand { NEXT, PREVIOUS, PAUSE, PLAY, TOGGLE_SHUFFLE, CYCLE_REPEAT, FAVORITE }

enum class VerificationStatus(val label: org.jetbrains.compose.resources.StringResource) {
  VERIFIED_FACT(com.example.windows.res.Res.string.assistant_curiosity_status_verified),
  HISTORICAL_ANALYSIS(com.example.windows.res.Res.string.assistant_curiosity_status_historical),
  TECHNICAL_SPEC(com.example.windows.res.Res.string.assistant_curiosity_status_technical)
}

data class Curiosity(
  val trackId: String,
  val trackTitle: String,
  val artist: String,
  val shortTrivia: String,
  val wordCount: Int,
  val documentedFacts: String,
  val sources: List<String>,
  val status: VerificationStatus,
  val verifiedDate: String
)

enum class CuriosityTiming { END_OF_TRACK, START_OF_TRACK, BOTH, MANUAL_ONLY }

enum class CuriosityDisplayMode { VOICE_AND_BANNER, VOICE_ONLY }

enum class VoicePreset(val pitch: Float, val speechRate: Float) {
  NATURAL(1.0f, 1.0f),
  CALM(0.9f, 0.92f),
  DYNAMIC(1.08f, 1.15f),
  DEEP(0.8f, 0.95f),
  UPBEAT(1.15f, 1.22f)
}

data class CuriosityConfig(
  val enabled: Boolean = true,
  val timing: CuriosityTiming = CuriosityTiming.END_OF_TRACK,
  val displayMode: CuriosityDisplayMode = CuriosityDisplayMode.VOICE_AND_BANNER,
  val voicePreset: VoicePreset = VoicePreset.NATURAL,
  val pitch: Float = 1.0f,
  val speechRate: Float = 1.0f
)
