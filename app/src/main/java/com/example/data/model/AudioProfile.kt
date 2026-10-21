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
      name = "Flat (Linear 0 dB)",
      description = "Resposta 100% linear e transparente. Todos os ganhos iniciam em 0 dB.",
      bandLevelsMb = List(15) { 0.toShort() },
      bassBoostStrength = 0,
      virtualizerStrength = 0
    )

    val PROG_ROCK = AudioProfile(
      id = "preset_prog_rock",
      name = "Prog Rock Master",
      description = "Graves firmes em 63-100Hz, calor em 250Hz, presença em 2.5kHz e brilho em 10-16kHz para sintetizadores Moog e guitarras.",
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
      name = "Clareza Acústica",
      description = "Atenuação de ressonâncias de caixa com foco em dedilhados cristalinos e harmônicos de cordas.",
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
      name = "Presença Vocal",
      description = "Destaque cirúrgico na faixa de inteligibilidade humana (800Hz a 4kHz) para máxima nitidez de vozes.",
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
      name = "Impacto de Graves",
      description = "Reforço encorpado nas 4 primeiras oitavas sem mascaramento das altas frequências.",
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
