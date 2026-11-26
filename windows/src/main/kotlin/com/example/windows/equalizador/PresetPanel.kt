package com.example.windows.equalizador

import com.example.windows.res.*
import com.example.windows.res.Res
import com.example.windows.res.tr
import com.example.windows.res.trPlural
import com.example.windows.res.audio_action_remove
import com.example.windows.res.audio_action_retry
import com.example.windows.res.audio_action_view
import com.example.windows.res.audio_correction_search_hint
import com.example.windows.res.audio_device_credit
import com.example.windows.res.audio_device_downloading
import com.example.windows.res.audio_device_for_output
import com.example.windows.res.audio_device_suggest_sub
import com.example.windows.res.audio_device_suggest_title
import com.example.windows.res.audio_empty_hint
import com.example.windows.res.audio_empty_mine_hint
import com.example.windows.res.audio_empty_mine_title
import com.example.windows.res.audio_empty_title
import com.example.windows.res.audio_filter_all
import com.example.windows.res.audio_filter_all_sources
import com.example.windows.res.audio_filter_builtin
import com.example.windows.res.audio_filter_mine
import com.example.windows.res.audio_form_earbud
import com.example.windows.res.audio_form_in_ear
import com.example.windows.res.audio_form_over_ear
import com.example.windows.res.audio_preset_kind_builtin
import com.example.windows.res.audio_preset_kind_mine
import com.example.windows.res.audio_presets_search_hint
import com.example.windows.res.audio_search_clear
import com.example.windows.res.audio_section_builtin
import com.example.windows.res.audio_section_mine
import com.example.windows.res.audio_tab_correction
import com.example.windows.res.audio_tab_eq
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import com.example.windows.equalizador.AudioMotion
import com.example.ui.theme.Grid
import com.example.windows.equalizador.DeviceSearchState

/** The two pages of the sheet: the user's equalizer curves, and the fone correction under them. */
internal enum class PresetTab(val labelRes: org.jetbrains.compose.resources.StringResource) {
  EQ(Res.string.audio_tab_eq),
  CORRECTION(Res.string.audio_tab_correction)
}

// The label is a string resource id: the chip text is resolved where it is drawn.
private enum class PresetFilter(val labelRes: org.jetbrains.compose.resources.StringResource) {
  ALL(Res.string.audio_filter_all),
  MINE(Res.string.audio_filter_mine),
  BUILT_IN(Res.string.audio_filter_builtin)
}

/** Size of the curve thumbnail in a preset row, and the leading slot of every row in the list. */
private val THUMB_W = Grid.u48
private val THUMB_H = Grid.u24
private const val THUMB_POINTS = 48

/**
 * The content of the preset sheet, in two tabs. EQ: the user's presets and the built-in ones, each
 * with a thumbnail of its curve; a preset replaces the user's curve. Correction: the measured fones
 * of AutoEq; a fone is a correction layer underneath the curve, so it stays when presets change.
 * [initialTab] is the page the sheet opens on (the Correction pill opens the second).
 */
@Composable
internal fun PresetPanel(
  presets: List<EqPreset>,
  activePresetId: String,
  device: DeviceCorrection?,
  /** The output the correction is kept for, when each output keeps its own; null otherwise. */
  deviceOutput: String?,
  /** (search, fone name) when AutoEq has corrections for the fone playing now and none is chosen. */
  suggestion: Pair<String, String>?,
  deviceSearch: DeviceSearchState,
  onSearchDevices: (String, String?) -> Unit,
  onRetryDevices: () -> Unit,
  onPickPreset: (EqPreset) -> Unit,
  onPickDevice: (AutoEqEntry) -> Unit,
  onClearDevice: () -> Unit,
  modifier: Modifier = Modifier,
  initialTab: PresetTab = PresetTab.EQ,
  /** Tallest the lists grow before they scroll; the desktop pane passes what its window leaves. */
  listMax: Dp = EqSize.presetListMax
) {
  var tab by rememberSaveable { mutableStateOf(initialTab) }
  Column(modifier = modifier.fillMaxWidth().testTag("eq_preset_panel")) {
    TabSwitch(selected = tab, onSelect = { tab = it })
    // Without the stretch: pulling a list past its end must not make the sheet look like it bounces.
    // The lists keep what is left of a pull so the sheet does not follow it down and snap back.
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
      Box(modifier = Modifier.nestedScroll(keepLeftoverScroll)) {
      when (tab) {
        PresetTab.EQ -> EqTab(presets, activePresetId, onPickPreset, listMax)
        PresetTab.CORRECTION -> CorrectionTab(
          device = device,
          deviceOutput = deviceOutput,
          suggestion = suggestion,
          deviceSearch = deviceSearch,
          onSearchDevices = onSearchDevices,
          onRetryDevices = onRetryDevices,
          onPickDevice = onPickDevice,
          onClearDevice = onClearDevice,
          listMax = listMax
        )
      }
      }
    }
  }
}

/** Two equal segments at the top of the sheet. */
@Composable
private fun TabSwitch(selected: PresetTab, onSelect: (PresetTab) -> Unit) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = Grid.margin)
      .padding(bottom = Grid.u12)
      .clip(Grid.r12)
      .background(colors.surfaceContainer)
      .padding(Grid.u6),
    horizontalArrangement = Arrangement.spacedBy(Grid.u6)
  ) {
    PresetTab.entries.forEach { t ->
      val on = t == selected
      Box(
        modifier = Modifier
          .weight(1f)
          .heightIn(min = Grid.u48 - Grid.u12)
          .clip(Grid.r6)
          .background(if (on) colors.primary else colors.surfaceContainer)
          .highlightClickable(role = Role.Tab, onClick = { onSelect(t) })
          .semantics { this.selected = on }
          .testTag("preset_tab_${t.name}"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          stringResource(t.labelRes),
          style = EqType.Body,
          color = if (on) colors.onPrimary else colors.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = Grid.u12, vertical = Grid.u6)
        )
      }
    }
  }
}

/** The user's presets and the built-in ones, with a search that filters them by name. */
@Composable
private fun EqTab(presets: List<EqPreset>, activePresetId: String, onPickPreset: (EqPreset) -> Unit, listMax: Dp) {
  val colors = MaterialTheme.colorScheme
  var filter by rememberSaveable { mutableStateOf(PresetFilter.ALL) }
  var query by rememberSaveable { mutableStateOf("") }
  val words = query.trim().lowercase()
  val names = presets.associate { it.id to it.localizedName() }
  val mine = presets.filter { it.isCustom && (words.isEmpty() || words in names.getValue(it.id).lowercase()) }
  val builtIn = presets.filter { !it.isCustom && (words.isEmpty() || words in names.getValue(it.id).lowercase()) }
  val showMine = filter == PresetFilter.ALL || filter == PresetFilter.MINE
  val showBuiltIn = filter == PresetFilter.ALL || filter == PresetFilter.BUILT_IN
  val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Column(modifier = Modifier.fillMaxWidth()) {
    SearchField(
      value = query,
      onValueChange = { query = it },
      placeholder = stringResource(Res.string.audio_presets_search_hint),
      modifier = Modifier.padding(horizontal = Grid.margin).testTag("preset_search_input")
    )
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(Grid.u12),
      contentPadding = PaddingValues(horizontal = Grid.margin, vertical = Grid.u12)
    ) {
      items(PresetFilter.entries) { f ->
        FilterChip(label = stringResource(f.labelRes), selected = filter == f, onClick = { filter = f }, modifier = Modifier.testTag("preset_filter_${f.name}"))
      }
    }
    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = listMax),
      contentPadding = PaddingValues(bottom = Grid.u24 + navBar)
    ) {
      val nothing = (!showMine || mine.isEmpty()) && (!showBuiltIn || builtIn.isEmpty())
      if (nothing && (words.isNotEmpty() || filter == PresetFilter.MINE)) {
        item(key = "empty") {
          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Grid.margin, vertical = Grid.u12).testTag("preset_empty")) {
            Text(
              if (words.isNotEmpty()) tr(Res.string.audio_empty_title, query.trim()) else stringResource(Res.string.audio_empty_mine_title),
              style = EqType.BodyStrong,
              color = colors.onSurface
            )
            Text(
              stringResource(if (words.isNotEmpty()) Res.string.audio_empty_hint else Res.string.audio_empty_mine_hint),
              style = EqType.Label,
              color = colors.onSurfaceVariant
            )
            if (words.isNotEmpty()) {
              TextAction(stringResource(Res.string.audio_search_clear), onClick = { query = "" })
            }
          }
        }
      }
      if (showMine && mine.isNotEmpty()) {
        item(key = "h_mine") { ListSection(stringResource(Res.string.audio_section_mine)) }
        items(mine, key = { "p_" + it.id }) { p ->
          PresetRow(p.name, stringResource(Res.string.audio_preset_kind_mine), p.settings, selected = p.id == activePresetId, onClick = { onPickPreset(p) }, modifier = Modifier.animateItem(placementSpec = AudioMotion.criticalSpatial<IntOffset>()))
        }
      }
      if (showBuiltIn && builtIn.isNotEmpty()) {
        item(key = "h_builtin") { ListSection(stringResource(Res.string.audio_section_builtin)) }
        items(builtIn, key = { "p_" + it.id }) { p ->
          PresetRow(names.getValue(p.id), stringResource(Res.string.audio_preset_kind_builtin), p.settings, selected = p.id == activePresetId, onClick = { onPickPreset(p) }, modifier = Modifier.animateItem(placementSpec = AudioMotion.criticalSpatial<IntOffset>()))
        }
      }
    }
  }
}

/** The fone in use, the measured fones of AutoEq (searchable by name and by source) and the credit. */
@Composable
private fun CorrectionTab(
  device: DeviceCorrection?,
  deviceOutput: String?,
  suggestion: Pair<String, String>?,
  deviceSearch: DeviceSearchState,
  onSearchDevices: (String, String?) -> Unit,
  onRetryDevices: () -> Unit,
  onPickDevice: (AutoEqEntry) -> Unit,
  onClearDevice: () -> Unit,
  listMax: Dp
) {
  val colors = MaterialTheme.colorScheme
  val query = deviceSearch.query
  val words = query.trim().lowercase()
  val fones = deviceSearch.results
  val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Column(modifier = Modifier.fillMaxWidth()) {
    SearchField(
      value = query,
      onValueChange = { onSearchDevices(it, deviceSearch.source) },
      placeholder = stringResource(Res.string.audio_correction_search_hint),
      modifier = Modifier.padding(horizontal = Grid.margin).testTag("device_search_input")
    )
    if (deviceSearch.sources.isNotEmpty()) {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Grid.u12),
        contentPadding = PaddingValues(horizontal = Grid.margin, vertical = Grid.u12)
      ) {
        item { FilterChip(label = stringResource(Res.string.audio_filter_all_sources), selected = deviceSearch.source == null, onClick = { onSearchDevices(query, null) }) }
        items(deviceSearch.sources.take(10)) { s ->
          FilterChip(label = s, selected = deviceSearch.source == s, onClick = { onSearchDevices(query, s) })
        }
      }
    } else {
      Spacer(Modifier.height(Grid.u12))
    }

    // The fone in use, removable here
    if (device != null) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = Grid.margin, vertical = Grid.u6)
          .heightIn(min = Grid.u48)
          .clip(Grid.r12)
          .background(colors.surfaceContainer)
          .padding(start = Grid.inset),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.primary, modifier = Modifier.size(Grid.icon))
        Spacer(Modifier.width(Grid.u12))
        Column(modifier = Modifier.weight(1f)) {
          Text(device.name, style = EqType.Body, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            listOfNotNull(
              deviceOutput?.let { tr(Res.string.audio_device_for_output, it) },
              device.source,
              trPlural(Res.plurals.audio_device_filters, device.bands.size, device.bands.size)
            ).joinToString(" · "),
            style = EqType.Label,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        TextAction(stringResource(Res.string.audio_action_remove), onClearDevice)
      }
    } else if (suggestion != null && query != suggestion.first) {
      // The fone playing now has corrections in AutoEq: one tap lists them.
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = Grid.margin, vertical = Grid.u6)
          .heightIn(min = Grid.u48)
          .clip(Grid.r12)
          .background(colors.surfaceContainer)
          .padding(start = Grid.inset)
          .testTag("device_suggestion"),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.primary, modifier = Modifier.size(Grid.icon))
        Spacer(Modifier.width(Grid.u12))
        Column(modifier = Modifier.weight(1f)) {
          Text(tr(Res.string.audio_device_suggest_title, suggestion.second), style = EqType.Body, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            tr(Res.string.audio_device_suggest_sub, suggestion.first),
            style = EqType.Label,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        TextAction(stringResource(Res.string.audio_action_view), onClick = { onSearchDevices(suggestion.first, null) })
      }
    }

    // The list can hold thousands of fones: its rows do not animate their place, so a new search or a
    // status line appearing above them moves them at once instead of springing them all.
    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = listMax),
      contentPadding = PaddingValues(bottom = Grid.u24 + navBar)
    ) {
      val nothing = !deviceSearch.loading && deviceSearch.error == null && fones.isEmpty()
      if (nothing && words.isNotEmpty()) {
        item(key = "empty") {
          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Grid.margin, vertical = Grid.u12).testTag("preset_empty")) {
            Text(tr(Res.string.audio_empty_title, query.trim()), style = EqType.BodyStrong, color = colors.onSurface)
            Text(stringResource(Res.string.audio_empty_hint), style = EqType.Label, color = colors.onSurfaceVariant)
            TextAction(stringResource(Res.string.audio_search_clear), onClick = { onSearchDevices("", deviceSearch.source) })
          }
        }
      }
      item(key = "status") {
        when {
          deviceSearch.loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u12)
          ) {
            CircularProgressIndicator(modifier = Modifier.size(Grid.icon), strokeWidth = EqSize.lineFine)
            Spacer(Modifier.width(Grid.u12))
            Text(stringResource(Res.string.audio_device_downloading), style = EqType.Label, color = colors.onSurfaceVariant)
          }
          deviceSearch.error != null && deviceSearch.total == 0 -> Column(modifier = Modifier.padding(horizontal = Grid.margin)) {
            Text(deviceSearch.error, style = EqType.Label, color = colors.error)
            TextAction(stringResource(Res.string.audio_action_retry), onRetryDevices)
          }
          else -> Column {
            if (deviceSearch.error != null) {
              Text(deviceSearch.error, style = EqType.Label, color = colors.error, modifier = Modifier.padding(horizontal = Grid.margin))
            }
            Text(
              if (words.isEmpty() && deviceSearch.onlineOnly > 0) {
                val nf = java.text.NumberFormat.getIntegerInstance()
                trPlural(Res.plurals.audio_device_total_online, deviceSearch.total, deviceSearch.total, nf.format(deviceSearch.total), nf.format(deviceSearch.total - deviceSearch.onlineOnly), nf.format(deviceSearch.onlineOnly))
              } else if (words.isEmpty()) trPlural(Res.plurals.audio_device_total, deviceSearch.total, deviceSearch.total, java.text.NumberFormat.getIntegerInstance().format(deviceSearch.total))
              else trPlural(Res.plurals.audio_device_results, fones.size, fones.size, java.text.NumberFormat.getIntegerInstance().format(fones.size)),
              style = EqType.Label,
              color = colors.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u6)
            )
          }
        }
      }
      items(fones, key = { "d_" + it.path }) { entry ->
        val selected = device != null && device.name == entry.name && device.source == entry.source
        FoneRow(entry, selected, applying = deviceSearch.applyingPath == entry.path, onClick = { onPickDevice(entry) })
      }
      item(key = "credit") {
        Text(
          stringResource(Res.string.audio_device_credit),
          style = EqType.Label,
          color = colors.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = Grid.margin, vertical = Grid.u12)
        )
      }
    }
  }
}

@Composable
private fun ListSection(title: String) {
  Text(
    title,
    style = EqType.Label,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .padding(start = Grid.margin, end = Grid.margin, top = Grid.u12, bottom = Grid.u6)
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
        .heightIn(min = Grid.u48)
        .highlightClickable(role = Role.RadioButton, onClick = onClick)
        .semantics { this.selected = selected }
        .padding(horizontal = Grid.margin),
      verticalAlignment = Alignment.CenterVertically
    ) {
      leading()
      Spacer(Modifier.width(Grid.u12))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = EqType.Body, color = if (selected) colors.primary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = EqType.Label, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Box(modifier = Modifier.size(Grid.icon), contentAlignment = Alignment.Center) { trailing() }
    }
    GroupDivider(inset = Grid.margin + THUMB_W + Grid.u12)
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
    subtitle = "${entry.source} · ${formLabel(entry.form)}",
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    leading = {
      Box(
        modifier = Modifier.size(width = THUMB_W, height = THUMB_H).clip(Grid.r6).background(colors.surfaceContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Grid.icon))
      }
    },
    trailing = {
      when {
        applying -> CircularProgressIndicator(modifier = Modifier.size(Grid.icon), strokeWidth = EqSize.lineFine)
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
  Canvas(modifier = Modifier.size(width = THUMB_W, height = THUMB_H).clip(Grid.r6).background(colors.surfaceContainer)) {
    val w = size.width
    val h = size.height
    val mid = h / 2f
    val range = 12f
    drawLine(zero, Offset(0f, mid), Offset(w, mid), 1f)
    val path = Path()
    points.forEachIndexed { i, db ->
      val x = i / (THUMB_POINTS - 1f) * w
      val y = mid - (db.coerceIn(-range, range) / range) * (mid - EqSize.knobInset.toPx())
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, line, style = Stroke(width = EqSize.lineFine.toPx(), cap = StrokeCap.Round))
  }
}

/** AutoEq's form factor comes from the data layer in fixed words; the screen says it in the interface language. */
@Composable
private fun formLabel(form: String): String = stringResource(
  when (form) {
    "Intra-auricular" -> Res.string.audio_form_in_ear
    "Earbud" -> Res.string.audio_form_earbud
    else -> Res.string.audio_form_over_ear
  }
)

/** Absorbs the scroll a list could not use, so the sheet around it never moves with the list. */
private val keepLeftoverScroll = object : NestedScrollConnection {
  override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
    Offset(0f, available.y)

  override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
    Velocity(0f, available.y)
}
