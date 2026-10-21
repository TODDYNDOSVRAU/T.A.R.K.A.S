package com.example.data.model

data class AudioCapabilities(
  val deviceModel: String,
  val androidVersion: String,
  val apiLevel: Int,
  val activeRouteName: String,
  val activeRouteType: String,
  val sampleRateHz: Int,
  val channelCount: Int,
  val channelFormat: String,
  val isAudioSessionActive: Boolean,
  val audioSessionId: Int,
  val isHardwareEqualizerAvailable: Boolean,
  val isBassBoostAvailable: Boolean,
  val isVirtualizerAvailable: Boolean,
  val effectsCount: Int,
  val systemDisclaimer: String = "O Android isola o áudio de cada aplicativo por segurança (sandbox). Os efeitos de equalização e processamento operam estritamente sobre a sessão de áudio do Tarkas. Nenhum app Android tem autorização para equalizar globalmente o som de outros apps como Spotify ou YouTube Music sem modificações de root no sistema."
)
