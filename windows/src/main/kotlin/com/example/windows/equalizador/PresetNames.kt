package com.example.windows.equalizador

import com.example.windows.res.Res
import com.example.windows.res.tr
import com.example.windows.res.trNow
import com.example.windows.res.audio_preset_name_acoustic_clarity
import com.example.windows.res.audio_preset_name_bass_punch
import com.example.windows.res.audio_preset_name_classical
import com.example.windows.res.audio_preset_name_electronic
import com.example.windows.res.audio_preset_name_flat
import com.example.windows.res.audio_preset_name_jazz
import com.example.windows.res.audio_preset_name_loudness
import com.example.windows.res.audio_preset_name_pop
import com.example.windows.res.audio_preset_name_prog_rock
import com.example.windows.res.audio_preset_name_speech
import com.example.windows.res.audio_preset_name_treble
import com.example.windows.res.audio_preset_name_vocal_presence
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.example.data.model.EqPreset

/**
 * The built-in presets keep a stable id (that is what is saved); their names live in resources so
 * they follow the app language. A preset the user made keeps the name the user typed.
 */
internal fun builtInPresetNameRes(id: String): org.jetbrains.compose.resources.StringResource? = when (id) {
  "preset_flat" -> Res.string.audio_preset_name_flat
  "preset_prog_rock" -> Res.string.audio_preset_name_prog_rock
  "preset_acoustic_clarity" -> Res.string.audio_preset_name_acoustic_clarity
  "preset_vocal_presence" -> Res.string.audio_preset_name_vocal_presence
  "preset_bass_punch" -> Res.string.audio_preset_name_bass_punch
  "preset_pop" -> Res.string.audio_preset_name_pop
  "preset_jazz" -> Res.string.audio_preset_name_jazz
  "preset_classical" -> Res.string.audio_preset_name_classical
  "preset_electronic" -> Res.string.audio_preset_name_electronic
  "preset_loudness" -> Res.string.audio_preset_name_loudness
  "preset_treble" -> Res.string.audio_preset_name_treble
  "preset_speech" -> Res.string.audio_preset_name_speech
  else -> null
}

/** The name to show for [this] in a composable. */
@Composable
internal fun EqPreset.localizedName(): String =
  if (isCustom) name else builtInPresetNameRes(id)?.let { stringResource(it) } ?: name

/** The same name, for code that has no composition (notices, toasts). */
fun EqPreset.localizedNameNow(): String =
  if (isCustom) name else builtInPresetNameRes(id)?.let { trNow(it) } ?: name
