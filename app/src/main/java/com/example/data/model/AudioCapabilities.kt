package com.example.data.model

data class AudioCapabilities(
  val deviceModel: String,
  val androidVersion: String,
  val apiLevel: Int,
  val activeRouteName: String,
  /** One of the ROUTE_* ids: a code to compare against, never shown. */
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
  /** The playing track's format ("FLAC · 96 kHz · 24 bits · estéreo"), or null with nothing loaded. */
  val trackFormat: String? = null,
  /** The output's highest rate is below the track's: Android lowers the rate before it plays. */
  val outputBelowTrackRate: Boolean = false,
  /** The output's highest rate, when it reports one. */
  val outputMaxRateHz: Int? = null
) {
  companion object {
    const val ROUTE_WIRED = "WIRED"
    const val ROUTE_BLUETOOTH = "BLUETOOTH"
    const val ROUTE_USB = "USB"
    const val ROUTE_BUILTIN_SPEAKER = "BUILTIN_SPEAKER"
    const val ROUTE_OTHER = "OTHER"
  }
}
