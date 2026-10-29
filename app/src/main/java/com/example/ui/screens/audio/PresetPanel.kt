package com.example.ui.screens.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.audio.dsp.EqDsp
import com.example.data.autoeq.AutoEqEntry
import com.example.data.model.DeviceCorrection
import com.example.data.model.EqPreset
import com.example.data.model.EqSettings
import com.example.ui.components.FilterChip
import com.example.ui.components.GroupDivider
import com.example.ui.components.SearchField
import com.example.ui.components.TextAction
import com.example.ui.components.highlightClickable
import com.example.ui.theme.AppText
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space
import com.example.ui.viewmodel.DeviceSearchState

private enum class PresetFilter(val label: String) { ALL("Todas"), MINE("Minhas"), BUILT_IN("Embutidas"), FONES("Fones (AutoEq)") }

/** Size of the curve thumbnail in a preset row, and the leading slot of every row in the list. */
private val THUMB_W = 64.dp
private val THUMB_H = 32.dp
private const val THUMB_POINTS = 48

/**
 * The content of the preset sheet: one searchable list of everything that shapes the sound. The
 * user's presets, the built-in ones (each with a thumbnail of its curve) and the measured fones of
 * AutoEq. A preset replaces the user's curve; a fone is a correction layer underneath it, so it
 * stays when presets change.
 */
@Composable
internal fun PresetPanel(
  presets: List<EqPreset>,
  activePresetId: String,
  device: DeviceCorrection?,
  deviceSearch: DeviceSearchState,
  onSearchDevices: (String, String?) -> Unit,
  onRetryDevices: () -> Unit,
  onPickPreset: (EqPreset) -> Unit,
  onPickDevice: (AutoEqEntry) -> Unit,
  onClearDevice: () -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = MaterialTheme.colorScheme
  var filter by rememberSaveable { mutableStateOf(PresetFilter.ALL) }
  val query = deviceSearch.query
  val words = query.trim().lowercase()
  val mine = presets.filter { it.isCustom && (words.isEmpty() || words in it.name.lowercase()) }
  val builtIn = presets.filter { !it.isCustom && (words.isEmpty() || words in it.name.lowercase()) }
  val showMine = filter == PresetFilter.ALL || filter == PresetFilter.MINE
  val showBuiltIn = filter == PresetFilter.ALL || filter == PresetFilter.BUILT_IN
  val showFones = filter == PresetFilter.ALL || filter == PresetFilter.FONES
  // With no search, "Todas" lists only a taste of the fones; the Fones filter lists them all.
  val fones = if (filter == PresetFilter.ALL && words.isEmpty()) deviceSearch.results.take(12) else deviceSearch.results
  val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Column(modifier = modifier.fillMaxWidth().testTag("eq_preset_panel")) {
    Text(
      "Predefinições",
      style = AppText.Title3,
      color = colors.onSurface,
      modifier = Modifier
        .padding(horizontal = Space.page)
        .padding(bottom = Space.m)
        .semantics { heading() }
    )
    SearchField(
      value = query,
      onValueChange = { onSearchDevices(it, deviceSearch.source) },
      placeholder = "Pesquisar estilo ou fone",
      modifier = Modifier.padding(horizontal = Space.page).testTag("device_search_input")
    )

    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(Space.xs),
      contentPadding = PaddingValues(horizontal = Space.page - Space.xs, vertical = Space.xs)
    ) {
      items(PresetFilter.entries) { f ->
        FilterChip(label = f.label, selected = filter == f, onClick = { filter = f }, modifier = Modifier.testTag("preset_filter_${f.name}"))
      }
    }
    if (filter == PresetFilter.FONES && deviceSearch.sources.isNotEmpty()) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        contentPadding = PaddingValues(start = Space.page - Space.xs, end = Space.page - Space.xs, bottom = Space.xs)
      ) {
        item { FilterChip(label = "Todas as medições", selected = deviceSearch.source == null, onClick = { onSearchDevices(query, null) }) }
        items(deviceSearch.sources.take(10)) { s ->
          FilterChip(label = s, selected = deviceSearch.source == s, onClick = { onSearchDevices(query, s) })
        }
      }
    }

    // The fone in use, removable here
    if (device != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = Space.page, vertical = Space.xs)
          .clip(Shapes.m)
          .background(colors.surfaceContainer)
          .padding(start = Space.l, end = Space.xs, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.primary, modifier = Modifier.size(Size.iconSmall))
        Spacer(Modifier.width(Space.m))
        Column(modifier = Modifier.weight(1f)) {
          Text(device.name, style = AppText.Body, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            "Correção de ${device.source} · ${device.bands.size} filtros",
            style = AppText.Footnote,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        TextAction("Remover", onClearDevice)
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
      contentPadding = PaddingValues(bottom = Space.xl + navBar)
    ) {
      if (showMine && mine.isNotEmpty()) {
        item(key = "h_mine") { ListSection("Minhas") }
        items(mine, key = { "p_" + it.id }) { p ->
          PresetRow(p.name, "Minha predefinição", p.settings, selected = p.id == activePresetId, onClick = { onPickPreset(p) }, modifier = Modifier.animateItem())
        }
      }
      if (showBuiltIn && builtIn.isNotEmpty()) {
        item(key = "h_builtin") { ListSection("Embutidas") }
        items(builtIn, key = { "p_" + it.id }) { p ->
          PresetRow(p.name, "Embutida", p.settings, selected = p.id == activePresetId, onClick = { onPickPreset(p) }, modifier = Modifier.animateItem())
        }
      }
      if (showFones) {
        item(key = "h_fones") {
          Column {
            ListSection("Fones · AutoEq")
            when {
              deviceSearch.loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s)
              ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Space.m))
                Text("Baixando a lista de fones…", style = AppText.Footnote, color = colors.onSurfaceVariant)
              }
              deviceSearch.error != null && deviceSearch.total == 0 -> Column(modifier = Modifier.padding(horizontal = Space.page)) {
                Text(deviceSearch.error, style = AppText.Footnote, color = colors.error)
                TextAction("Tentar de novo", onRetryDevices)
              }
              else -> {
                if (deviceSearch.error != null) {
                  Text(deviceSearch.error, style = AppText.Footnote, color = colors.error, modifier = Modifier.padding(horizontal = Space.page))
                }
                Text(
                  if (words.isEmpty()) "${deviceSearch.total} fones medidos. Pesquise o modelo do seu." else "${deviceSearch.results.size} fone(s)",
                  style = AppText.Footnote,
                  color = colors.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = Space.page, vertical = Space.xxs)
                )
              }
            }
          }
        }
        items(fones, key = { "d_" + it.path }) { entry ->
          val selected = device != null && device.name == entry.name && device.source == entry.source
          FoneRow(entry, selected, applying = deviceSearch.applyingPath == entry.path, onClick = { onPickDevice(entry) }, modifier = Modifier.animateItem())
        }
        item(key = "credit") {
          Text(
            "Curvas do projeto AutoEq (Jaakko Pasanen, licença MIT), a partir de medições de oratory1990, crinacle, Rtings e outros. " +
              "Precisa de internet só para baixar.",
            style = AppText.Caption1,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.m)
          )
        }
      }
    }
  }
}

@Composable
private fun ListSection(title: String) {
  Text(
    title,
    style = AppText.Footnote,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .padding(start = Space.page, end = Space.page, top = Space.l, bottom = Space.xs)
      .semantics { heading() }
  )
}

/** A row of the sheet: a leading picture, two lines of text and a check when it is in use. */
@Composable
private fun PickRow(
  title: String,
  subtitle: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier,
  leading: @Composable () -> Unit,
  trailing: @Composable () -> Unit = {
    if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
  }
) {
  val colors = MaterialTheme.colorScheme
  Column(modifier = modifier) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = Size.row)
        .highlightClickable(role = Role.RadioButton, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = Space.page, vertical = Space.s),
      verticalAlignment = Alignment.CenterVertically
    ) {
      leading()
      Spacer(Modifier.width(Space.m))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = AppText.Body, color = if (selected) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = AppText.Footnote, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Box(modifier = Modifier.size(Size.icon), contentAlignment = Alignment.Center) { trailing() }
    }
    GroupDivider(inset = Space.page + THUMB_W + Space.m)
  }
}

/** A preset with a thumbnail of the curve it makes. */
@Composable
private fun PresetRow(name: String, subtitle: String, settings: EqSettings, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  PickRow(name, subtitle, selected, onClick, modifier, leading = { CurveThumb(settings) })
}

@Composable
private fun FoneRow(entry: AutoEqEntry, selected: Boolean, applying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  PickRow(
    title = entry.name,
    subtitle = "${entry.source} · ${entry.form}",
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    leading = {
      Box(
        modifier = Modifier.size(width = THUMB_W, height = THUMB_H).clip(Shapes.xs).background(colors.surfaceContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Size.iconSmall))
      }
    },
    trailing = {
      when {
        applying -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        selected -> Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary)
      }
    }
  )
}

/** A small drawing of a preset's curve, in the accent. */
@Composable
private fun CurveThumb(settings: EqSettings) {
  val colors = MaterialTheme.colorScheme
  val points = remember(settings) {
    val user = settings.copy(enabled = true, device = null)
    FloatArray(THUMB_POINTS) { i -> EqDsp.responseDb(user, xToFreq(i / (THUMB_POINTS - 1f)).toDouble()).toFloat() }
  }
  val zero = colors.onSurface.copy(alpha = 0.16f)
  val line = colors.primary
  Canvas(modifier = Modifier.size(width = THUMB_W, height = THUMB_H).clip(Shapes.xs).background(colors.surfaceContainer)) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val range = 12f
    drawLine(zero, Offset(0f, mid), Offset(w, mid), 1f)
    val path = Path()
    points.forEachIndexed { i, db ->
      val x = i / (THUMB_POINTS - 1f) * w
      val y = mid - (db.coerceIn(-range, range) / range) * (mid - 3.dp.toPx())
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, line, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
  }
}
