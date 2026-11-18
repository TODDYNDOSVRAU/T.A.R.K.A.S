package com.example.data.model

data class AudioProfile(
  val id: String,
  val name: String,
  val description: String,
  // 15 band gains in millibels (-1500 to +1500, corresponding to -15.0 dB to +15.0 dB)
  // All start at 0 dB by default as requested!
  val bandLevelsMb: List<Short> = List(15) { 0.toShort() },
  // Bass Boost strength (0 to 1000)
  val bassBoostStrength: Short = 0,
  // Virtualizer strength (0 to 1000)
  val virtualizerStrength: Short = 0,
  val isCustom: Boolean = false
) {
  companion object {
    // 15 Standard Frequency Centers across the 20Hz - 20kHz spectrum
    val BAND_FREQUENCIES = listOf(
      "25 Hz", "40 Hz", "63 Hz", "100 Hz", "160 Hz",
      "250 Hz", "400 Hz", "630 Hz", "1 kHz", "1.6 kHz",
      "2.5 kHz", "4 kHz", "6.3 kHz", "10 kHz", "16 kHz"
    )

    // Flat / Default profile: strictly 0 dB across all 15 bands
    val FLAT = AudioProfile(
      id = "preset_flat",
      name = "Flat",
      description = "Fully linear, transparent response. Every gain starts at 0 dB.",
      bandLevelsMb = List(15) { 0.toShort() },
      bassBoostStrength = 0,
      virtualizerStrength = 0
    )

    val PROG_ROCK = AudioProfile(
      id = "preset_prog_rock",
      name = "Prog Rock Master",
      description = "Firm bass at 63-100 Hz, warmth at 250 Hz, presence at 2.5 kHz and brightness at 10-16 kHz for Moog synths and guitars.",
      bandLevelsMb = listOf(
        200, 350, 450, 300, 150,
        100, 150, 250, 350, 450,
        400, 300, 250, 350, 400
      ).map { it.toShort() },
      bassBoostStrength = 280,
      virtualizerStrength = 220
    )

    val ACOUSTIC_CLARITY = AudioProfile(
      id = "preset_acoustic_clarity",
      name = "Acoustic Clarity",
      description = "Tames body resonances, focusing on crystalline fingerpicking and string harmonics.",
      bandLevelsMb = listOf(
        -250, -200, -100, 50, 100,
        150, 200, 300, 400, 450,
        400, 350, 300, 250, 150
      ).map { it.toShort() },
      bassBoostStrength = 0,
      virtualizerStrength = 150
    )

    val VOCAL_PRESENCE = AudioProfile(
      id = "preset_vocal_presence",
      name = "Vocal Presence",
      description = "Surgical lift in the range of human intelligibility (800 Hz to 4 kHz) for maximum vocal clarity.",
      bandLevelsMb = listOf(
        -150, -100, 0, 50, 100,
        200, 300, 450, 600, 500,
        450, 300, 150, 50, 0
      ).map { it.toShort() },
      bassBoostStrength = 40,
      virtualizerStrength = 100
    )

    val BASS_PUNCH = AudioProfile(
      id = "preset_bass_punch",
      name = "Bass Punch",
      description = "Full-bodied boost across the first four octaves without masking the high frequencies.",
      bandLevelsMb = listOf(
        600, 650, 550, 450, 300,
        150, 50, 0, 0, 0,
        100, 150, 200, 250, 300
      ).map { it.toShort() },
      bassBoostStrength = 600,
      virtualizerStrength = 120
    )

    val DEFAULT_PROFILES = listOf(
      FLAT,
      PROG_ROCK,
      ACOUSTIC_CLARITY,
      VOCAL_PRESENCE,
      BASS_PUNCH
    )
  }
}
