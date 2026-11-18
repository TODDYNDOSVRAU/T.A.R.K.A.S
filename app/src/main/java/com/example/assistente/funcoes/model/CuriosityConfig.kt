package com.example.data.model

// The words for each option are resources, used by the settings screens. The enums hold identity and values only.
enum class CuriosityTiming {
  END_OF_TRACK,
  START_OF_TRACK,
  BOTH,
  MANUAL_ONLY
}

enum class CuriosityDisplayMode {
  VOICE_AND_BANNER,
  VOICE_ONLY
}

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
