package com.example.ui.screens.audio

import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.theme.AppText
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import java.util.Locale

/**
 * Reads a number typed by the user: "3,5" and "3.5" both work, and with [allowK] "1.5k" or "1,5 kHz"
 * means 1500. The result is held inside [min]..[max]; null when the text is not a finite number.
 */
internal fun parseNumberInput(text: String, min: Float, max: Float, allowK: Boolean = false): Float? {
  var t = text.trim().lowercase(Locale.ROOT).replace(',', '.').replace(" ", "")
  var scale = 1f
  if (allowK) {
    when {
      t.endsWith("khz") -> { t = t.removeSuffix("khz"); scale = 1000f }
      t.endsWith("k") -> { t = t.removeSuffix("k"); scale = 1000f }
      t.endsWith("hz") -> t = t.removeSuffix("hz")
    }
  }
  val v = t.toFloatOrNull()?.times(scale) ?: return null
  if (!v.isFinite()) return null
  return v.coerceIn(min, max)
}

/**
 * Types an exact value for a fader or a knob (dB, Hz, Q): the field, − and + for fine steps, and a
 * visible reset. [onApply] gets the value held inside the range.
 */
@Composable
internal fun NumberEntryDialog(
  title: String,
  unit: String,
  value: Float,
  min: Float,
  max: Float,
  step: Float,
  decimals: Int,
  defaultValue: Float?,
  resetLabel: String? = null,
  allowK: Boolean = false,
  onApply: (Float) -> Unit,
  onDismiss: () -> Unit
) {
  fun show(v: Float) = String.format(Locale.US, "%.${decimals}f", v)
  var text by remember { mutableStateOf(show(value)) }
  val parsed = parseNumberInput(text, min, max, allowK)
  val decreaseLabel = stringResource(R.string.audio_number_decrease)
  val increaseLabel = stringResource(R.string.audio_number_increase)
  val resetText = resetLabel ?: stringResource(R.string.audio_number_reset_zero)
  fun nudge(by: Float) {
    val base = parsed ?: value
    text = show((base + by).coerceIn(min, max))
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title, style = AppText.Title3) },
    text = {
      androidx.compose.foundation.layout.Column {
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          singleLine = true,
          isError = parsed == null,
          suffix = { Text(unit) },
          supportingText = {
            Text(
              if (parsed == null) stringResource(R.string.audio_number_invalid) else stringResource(R.string.audio_number_range, show(min), show(max), unit),
              style = AppText.Footnote
            )
          },
          keyboardOptions = KeyboardOptions(keyboardType = if (allowK) KeyboardType.Text else KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("number_entry_field")
        )
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = { nudge(-step) },
            modifier = Modifier.weight(1f).height(Size.touch).semantics { contentDescription = decreaseLabel }
          ) { Text("−") }
          OutlinedButton(
            onClick = { nudge(step) },
            modifier = Modifier.weight(1f).height(Size.touch).semantics { contentDescription = increaseLabel }
          ) { Text("+") }
          if (defaultValue != null) {
            OutlinedButton(
              onClick = { text = show(defaultValue) },
              modifier = Modifier.weight(1.4f).height(Size.touch).testTag("number_entry_reset")
            ) { Text(resetText, maxLines = 1) }
          }
        }
      }
    },
    confirmButton = {
      TextButton(enabled = parsed != null, onClick = { parsed?.let(onApply) }) {
        Text(stringResource(R.string.audio_action_apply), color = MaterialTheme.colorScheme.primary)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.audio_action_cancel)) } }
  )
}

/** What a readout opens when tapped: the range and the unit of the value it shows. */
internal class NumberEdit(
  val title: String,
  val unit: String,
  val value: Float,
  val min: Float,
  val max: Float,
  val step: Float,
  val decimals: Int,
  val defaultValue: Float?,
  val resetLabel: String? = null,
  val allowK: Boolean = false,
  val onApply: (Float) -> Unit
)

@Composable
internal fun NumberEntryFor(edit: NumberEdit, onDismiss: () -> Unit) = NumberEntryDialog(
  title = edit.title, unit = edit.unit, value = edit.value, min = edit.min, max = edit.max, step = edit.step,
  decimals = edit.decimals, defaultValue = edit.defaultValue, resetLabel = edit.resetLabel, allowK = edit.allowK,
  onApply = { edit.onApply(it); onDismiss() }, onDismiss = onDismiss
)
