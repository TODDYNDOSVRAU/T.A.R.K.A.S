package com.example.ui.screens.audio

import android.content.ClipData
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.ui.components.highlightClickable
import com.example.ui.theme.AppText
import com.example.ui.theme.Space
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow

/*
 * Rows shared by the pages of the Áudio tab, drawn to sit inside an InsetGroup like the rows of
 * Design.kt: a slider with its label and value, and a command to copy.
 */

/**
 * A slider row: the label and the current value on one line, the slider under it. [onChange] runs on
 * every frame of a drag, so the sound follows the finger; [onChangeFinished] runs once when the finger
 * lifts, with the final value, which is where a caller should persist it.
 */
@Composable
internal fun SliderRow(
  label: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  onChangeFinished: ((Float) -> Unit)? = null,
  enabled: Boolean = true,
  testTag: String? = null
) {
  val colors = MaterialTheme.colorScheme
  val latest by rememberUpdatedState(value)
  Column(modifier = modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.m, bottom = Space.xs)) {
    Row(modifier = Modifier.fillMaxWidth()) {
      Text(label, style = AppText.Body, color = colors.onSurface, modifier = Modifier.weight(1f))
      Text(valueText, style = AppText.Body.copy(fontFeatureSettings = "tnum"), color = colors.onSurfaceVariant)
    }
    Slider(
      value = value.coerceIn(range.start, range.endInclusive),
      onValueChange = onChange,
      onValueChangeFinished = onChangeFinished?.let { done -> { done(latest) } },
      valueRange = range,
      enabled = enabled,
      colors = SliderDefaults.colors(
        thumbColor = colors.primary,
        activeTrackColor = colors.primary,
        inactiveTrackColor = colors.surfaceContainerHigh
      ),
      modifier = Modifier
        .semantics {
          contentDescription = label
          stateDescription = valueText
        }
        .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    )
  }
}

/** A slider on a logarithmic scale, for frequencies, which are heard in ratios, not steps. */
@Composable
internal fun LogSliderRow(
  label: String,
  valueText: String,
  value: Float,
  min: Float,
  max: Float,
  onChange: (Float) -> Unit
) {
  val span = log10(max / min)
  SliderRow(
    label = label,
    valueText = valueText,
    value = log10(value.coerceIn(min, max) / min) / span,
    range = 0f..1f,
    onChange = { t -> onChange(min * 10f.pow(t * span)) }
  )
}

/** A command to type on a computer, as a row; a tap copies it. */
@Composable
internal fun CommandRow(command: String) {
  val colors = MaterialTheme.colorScheme
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  var copied by remember { mutableStateOf(false) }
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .highlightClickable(onClickLabel = "Copiar o comando") {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Comando", command))) }
        copied = true
      }
      .padding(horizontal = Space.l, vertical = Space.m)
  ) {
    Text(command, style = AppText.Footnote.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = colors.onSurface)
    Text(
      if (copied) "Copiado" else "Toque para copiar",
      style = AppText.Footnote,
      color = colors.primary,
      modifier = Modifier.padding(top = Space.xs)
    )
  }
}
