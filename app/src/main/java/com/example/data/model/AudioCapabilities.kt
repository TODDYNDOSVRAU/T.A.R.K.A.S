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
  val systemDisclaimer: String = "Sem root, o equalizador processa só o áudio do T.A.R.K.A.S. Nos modos ADB e root, ele também se liga à sessão de áudio de cada app que está tocando, como o Poweramp Equalizer."
)
