package com.example.data.model

enum class CuriosityTiming(val label: String, val description: String) {
  END_OF_TRACK("Ao final da música", "A IA fala a curiosidade quando a faixa termina"),
  START_OF_TRACK("No início da música", "A curiosidade começa a tocar antes da música"),
  BOTH("No início e no final", "Apresentação antes e curiosidade ao término"),
  MANUAL_ONLY("Só na lâmpada", "Não fala sozinha; toque na lâmpada do player para ler a curiosidade")
}

enum class CuriosityDisplayMode(val label: String, val description: String) {
  VOICE_AND_BANNER("Voz + Banner no Player", "Fala a curiosidade com badge animado no player"),
  VOICE_ONLY("Apenas Voz da IA", "Apenas a narração de áudio sem elementos visuais extras")
}

enum class VoicePreset(val label: String, val pitch: Float, val speechRate: Float) {
  NATURAL("Natural / Padrão", 1.0f, 1.0f),
  CALM("Calma & Reflexiva", 0.9f, 0.92f),
  DYNAMIC("Dinâmica / Rádio DJ", 1.08f, 1.15f),
  DEEP("Grave & Solene", 0.8f, 0.95f),
  UPBEAT("Animada & Rápida", 1.15f, 1.22f)
}

data class CuriosityConfig(
  val enabled: Boolean = true,
  val timing: CuriosityTiming = CuriosityTiming.END_OF_TRACK,
  val displayMode: CuriosityDisplayMode = CuriosityDisplayMode.VOICE_AND_BANNER,
  val voicePreset: VoicePreset = VoicePreset.NATURAL,
  val pitch: Float = 1.0f,
  val speechRate: Float = 1.0f
)
