package com.example.data.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object AudioSynthesizer {
  private const val TAG = "AudioSynthesizer"
  private const val SAMPLE_RATE = 44100
  private const val CHANNELS = 2
  private const val BITS_PER_SAMPLE = 16

  suspend fun ensureDemoTracksExist(context: Context): Map<String, String> = withContext(Dispatchers.IO) {
    val audioDir = File(context.cacheDir, "tarkas_audio")
    if (!audioDir.exists()) {
      audioDir.mkdirs()
    }

    val tracks = mapOf(
      "track_tarkus" to File(audioDir, "tarkus_eruption.wav"),
      "track_tubular" to File(audioDir, "tubular_bells.wav"),
      "track_debussy" to File(audioDir, "clair_de_lune.wav"),
      "track_satie" to File(audioDir, "gymnopedie.wav"),
      "track_sweep" to File(audioDir, "soundcheck_sweep.wav")
    )

    val resultMap = mutableMapOf<String, String>()

    for ((id, file) in tracks) {
      if (!file.exists() || file.length() < 1000) {
        try {
          when (id) {
            "track_tarkus" -> generateTarkusTheme(file)
            "track_tubular" -> generateTubularBells(file)
            "track_debussy" -> generateClairDeLune(file)
            "track_satie" -> generateGymnopedie(file)
            "track_sweep" -> generateAcousticSweep(file)
          }
          Log.i(TAG, "Generated demo audio: ${file.name} (${file.length()} bytes)")
        } catch (e: Exception) {
          Log.e(TAG, "Error generating $id", e)
        }
      }
      resultMap[id] = file.absolutePath
    }

    resultMap
  }

  // 1. Tarkus: Eruption Theme in 5/4 time signature (Keith Emerson style Moog riffs)
  private fun generateTarkusTheme(file: File) {
    val durationSec = 16
    val totalSamples = SAMPLE_RATE * durationSec
    val buffer = ShortArray(totalSamples * CHANNELS)

    // F frequencies: F3 (174.61), Ab3 (207.65), Bb3 (233.08), C4 (261.63), Eb4 (311.13), F4 (349.23)
    val riff = doubleArrayOf(174.61, 207.65, 233.08, 261.63, 311.13, 261.63, 233.08, 207.65, 174.61, 130.81)
    val noteDurationSamples = (SAMPLE_RATE * 0.28).toInt()

    var noteIndex = 0
    var noteSampleCount = 0

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val freq = riff[noteIndex % riff.size]

      // Moog style saw/square approximation with warmth
      val harmonic1 = sin(2.0 * PI * freq * t)
      val harmonic2 = 0.5 * sin(2.0 * PI * freq * 2.0 * t)
      val harmonic3 = 0.25 * sin(2.0 * PI * freq * 3.0 * t)
      // Analog bass undercurrent (F2 = 87.31 Hz)
      val bass = 0.6 * sin(2.0 * PI * 87.31 * t)

      val envelope = (1.0 - (noteSampleCount.toDouble() / noteDurationSamples)).coerceIn(0.05, 1.0)
      val sample = ((harmonic1 + harmonic2 + harmonic3) * 0.5 * envelope + bass * 0.4) * 0.65

      val pcmVal = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
      // Left and right panning modulation
      buffer[i * 2] = pcmVal
      buffer[i * 2 + 1] = (pcmVal * 0.9).toInt().toShort()

      noteSampleCount++
      if (noteSampleCount >= noteDurationSamples) {
        noteSampleCount = 0
        noteIndex++
      }
    }

    writeWavFile(file, buffer)
  }

  // 2. Tubular Bells: Hypnotic bell cascade & guitar ostinato
  private fun generateTubularBells(file: File) {
    val durationSec = 16
    val totalSamples = SAMPLE_RATE * durationSec
    val buffer = ShortArray(totalSamples * CHANNELS)

    val pattern = doubleArrayOf(220.0, 293.66, 329.63, 392.0, 440.0, 392.0, 329.63, 293.66, 261.63, 329.63, 392.0, 329.63)
    val noteDurationSamples = (SAMPLE_RATE * 0.22).toInt()

    var noteIndex = 0
    var noteSampleCount = 0

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val freq = pattern[noteIndex % pattern.size]

      // Tubular Bell resonance: non-harmonic high partials
      val bell1 = sin(2.0 * PI * freq * t)
      val bell2 = 0.35 * sin(2.0 * PI * (freq * 2.76) * t)
      val bell3 = 0.2 * sin(2.0 * PI * (freq * 5.4) * t)

      val envelope = Math.exp(-3.5 * (noteSampleCount.toDouble() / noteDurationSamples))
      val sample = (bell1 + bell2 + bell3) * envelope * 0.55

      val pcmLeft = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
      val pcmRight = ((sample * 0.85).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()

      buffer[i * 2] = pcmLeft
      buffer[i * 2 + 1] = pcmRight

      noteSampleCount++
      if (noteSampleCount >= noteDurationSamples) {
        noteSampleCount = 0
        noteIndex++
      }
    }

    writeWavFile(file, buffer)
  }

  // 3. Clair de Lune: Impressionist Piano Harmony
  private fun generateClairDeLune(file: File) {
    val durationSec = 18
    val totalSamples = SAMPLE_RATE * durationSec
    val buffer = ShortArray(totalSamples * CHANNELS)

    val chords = listOf(
      doubleArrayOf(277.18, 349.23, 415.30, 554.37), // Db maj7
      doubleArrayOf(246.94, 329.63, 392.00, 493.88), // Bbm7
      doubleArrayOf(220.00, 277.18, 329.63, 440.00), // Gb maj
      doubleArrayOf(207.65, 261.63, 311.13, 415.30)  // Ab7
    )
    val chordSamples = (SAMPLE_RATE * 4.5).toInt()

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val chordIdx = (i / chordSamples) % chords.size
      val chordFreqs = chords[chordIdx]
      val chordPos = (i % chordSamples).toDouble() / chordSamples

      var mix = 0.0
      chordFreqs.forEachIndexed { idx, freq ->
        val arpeggioDelay = idx * 0.35
        val localT = (t % 4.5) - arpeggioDelay
        if (localT > 0) {
          val env = Math.exp(-1.8 * localT)
          // Piano hammer harmonic structure
          val wave = sin(2.0 * PI * freq * t) + 0.3 * sin(2.0 * PI * freq * 2 * t) + 0.1 * sin(2.0 * PI * freq * 3 * t)
          mix += wave * env * 0.22
        }
      }

      val pcm = (mix.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
      buffer[i * 2] = pcm
      buffer[i * 2 + 1] = pcm
    }

    writeWavFile(file, buffer)
  }

  // 4. Gymnopédie No. 1: Warm ambient modal chords
  private fun generateGymnopedie(file: File) {
    val durationSec = 18
    val totalSamples = SAMPLE_RATE * durationSec
    val buffer = ShortArray(totalSamples * CHANNELS)

    // Gmaj7 to Dmaj7 modal alternation
    val bassNotes = doubleArrayOf(98.00, 146.83) // G2, D3
    val chords = listOf(
      doubleArrayOf(293.66, 369.99, 440.00), // B-D-F#
      doubleArrayOf(220.00, 277.18, 329.63)  // A-C#-E
    )
    val beatSamples = (SAMPLE_RATE * 3.0).toInt()

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val cycle = (i / beatSamples) % 2
      val bassFreq = bassNotes[cycle]
      val chordFreqs = chords[cycle]
      val beatPos = (i % beatSamples).toDouble() / beatSamples

      // Bass note on beat 1
      val bassEnv = Math.exp(-2.0 * beatPos)
      val bassTone = (sin(2.0 * PI * bassFreq * t) + 0.3 * sin(2.0 * PI * bassFreq * 2 * t)) * bassEnv * 0.45

      // Chord on beat 2 and 3
      var chordTone = 0.0
      val chordTime = (beatPos - 0.33).coerceAtLeast(0.0)
      if (beatPos > 0.33) {
        val chordEnv = Math.exp(-1.4 * chordTime * 3.0)
        chordFreqs.forEach { f ->
          chordTone += (sin(2.0 * PI * f * t) + 0.2 * sin(2.0 * PI * f * 2 * t)) * chordEnv * 0.18
        }
      }

      val total = (bassTone + chordTone) * 0.7
      val pcm = (total.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
      buffer[i * 2] = pcm
      buffer[i * 2 + 1] = pcm
    }

    writeWavFile(file, buffer)
  }

  // 5. Soundcheck Sweep: 20Hz-20kHz Acoustic Reference Suite
  private fun generateAcousticSweep(file: File) {
    val durationSec = 18
    val totalSamples = SAMPLE_RATE * durationSec
    val buffer = ShortArray(totalSamples * CHANNELS)

    for (i in 0 until totalSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val sample: Double

      when {
        // Section 1 (0 to 4s): 60 Hz Sub-Bass Tone with clean 120Hz harmonic
        t < 4.0 -> {
          sample = 0.7 * sin(2.0 * PI * 60.0 * t)
        }
        // Section 2 (4s to 8s): 1000 Hz Calibration Reference Tone
        t < 8.0 -> {
          sample = 0.5 * sin(2.0 * PI * 1000.0 * t)
        }
        // Section 3 (8s to 12s): 10,000 Hz High-Frequency Dispersion Tone
        t < 12.0 -> {
          sample = 0.4 * sin(2.0 * PI * 10000.0 * t)
        }
        // Section 4 (12s to 18s): Logarithmic Frequency Sweep (40Hz to 15,000Hz)
        else -> {
          val sweepT = (t - 12.0) / 6.0
          val fStart = 40.0
          val fEnd = 15000.0
          val currentFreq = fStart * Math.pow(fEnd / fStart, sweepT)
          sample = 0.5 * sin(2.0 * PI * currentFreq * t)
        }
      }

      val pcm = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
      buffer[i * 2] = pcm
      buffer[i * 2 + 1] = pcm
    }

    writeWavFile(file, buffer)
  }

  private fun writeWavFile(file: File, pcmData: ShortArray) {
    val byteDataSize = pcmData.size * 2
    val totalSize = 36 + byteDataSize

    FileOutputStream(file).use { out ->
      val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
      header.put("RIFF".toByteArray())
      header.putInt(totalSize)
      header.put("WAVE".toByteArray())
      header.put("fmt ".toByteArray())
      header.putInt(16) // Subchunk1Size for PCM
      header.putShort(1) // AudioFormat 1 = PCM
      header.putShort(CHANNELS.toShort())
      header.putInt(SAMPLE_RATE)
      header.putInt(SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8)) // ByteRate
      header.putShort((CHANNELS * (BITS_PER_SAMPLE / 8)).toShort()) // BlockAlign
      header.putShort(BITS_PER_SAMPLE.toShort())
      header.put("data".toByteArray())
      header.putInt(byteDataSize)

      out.write(header.array())

      val dataBuffer = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
      for (sample in pcmData) {
        dataBuffer.putShort(sample)
      }
      out.write(dataBuffer.array())
      out.flush()
    }
  }
}
