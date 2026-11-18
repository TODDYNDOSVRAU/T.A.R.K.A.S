package com.example.ui.screens.audio

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.data.model.EqPreset

/**
 * The built-in presets keep a stable id (that is what is saved); their names live in resources so
 * they follow the app language. A preset the user made keeps the name the user typed.
 */
@StringRes
internal fun builtInPresetNameRes(id: String): Int? = when (id) {
  "preset_flat" -> R.string.audio_preset_name_flat
  "preset_prog_rock" -> R.string.audio_preset_name_prog_rock
  "preset_acoustic_clarity" -> R.string.audio_preset_name_acoustic_clarity
  "preset_vocal_presence" -> R.string.audio_preset_name_vocal_presence
  "preset_bass_punch" -> R.string.audio_preset_name_bass_punch
  "preset_pop" -> R.string.audio_preset_name_pop
  "preset_jazz" -> R.string.audio_preset_name_jazz
  "preset_classical" -> R.string.audio_preset_name_classical
  "preset_electronic" -> R.string.audio_preset_name_electronic
  "preset_loudness" -> R.string.audio_preset_name_loudness
  "preset_treble" -> R.string.audio_preset_name_treble
  "preset_speech" -> R.string.audio_preset_name_speech
  else -> null
}

/** The name to show for [this] in a composable. */
@Composable
internal fun EqPreset.localizedName(): String =
  if (isCustom) name else builtInPresetNameRes(id)?.let { stringResource(it) } ?: name

/** The same name, for code that has a [Context] and no composition (notices, toasts). */
fun EqPreset.localizedName(context: Context): String =
  if (isCustom) name else builtInPresetNameRes(id)?.let { context.getString(it) } ?: name
