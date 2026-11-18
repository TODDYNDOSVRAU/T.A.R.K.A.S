package com.example.windows.geral

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText

// Desktop twins of the building blocks in geral/ui/CommonComponents.kt, on the T.A.L. grid (48 dp rows,
// 12 dp inset, 24 dp margin) and on MaterialTheme.colorScheme only.

/** Widest a settings page reads: 96 * 6 = 576 dp, about 70 characters of Body. */
val ReadingWidth = Grid.u384 + Grid.u192

fun Modifier.readingColumn(): Modifier = this.widthIn(max = ReadingWidth).fillMaxWidth().padding(horizontal = Grid.margin)

@Composable
fun GroupHeader(text: String) {
  Text(
    text.uppercase(), style = GridText.Label, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = Grid.inset, bottom = Grid.u6)
  )
}

@Composable
fun GroupFooter(text: String) {
  Text(
    text, style = GridText.Small, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = Grid.inset, end = Grid.inset, top = Grid.u6)
  )
}

@Composable
fun InsetGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Column(modifier.fillMaxWidth().clip(Grid.r12).background(MaterialTheme.colorScheme.surfaceContainer)) { content() }
}

@Composable
fun GroupDivider() {
  HorizontalDivider(Modifier.padding(start = Grid.inset), thickness = Grid.hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun ListRow(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  value: String? = null,
  chevron: Boolean = true,
  destructive: Boolean = false,
  onClick: (() -> Unit)? = null
) {
  val c = MaterialTheme.colorScheme
  Row(
    modifier.fillMaxWidth().heightIn(min = Grid.u48)
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
      .padding(horizontal = Grid.inset, vertical = Grid.u6),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = GridText.Body, color = if (destructive) c.error else c.onSurface)
      if (subtitle != null) Text(subtitle, style = GridText.Small, color = c.onSurfaceVariant)
    }
    if (value != null) Text(value, style = GridText.Body, color = c.onSurfaceVariant)
    if (chevron && onClick != null) Text("›", style = GridText.Title, color = c.onSurfaceVariant)
  }
}

@Composable
fun InfoRow(title: String, value: String, modifier: Modifier = Modifier) = ListRow(title, modifier, value = value, chevron = false)

@Composable
fun SwitchRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  enabled: Boolean = true
) {
  val c = MaterialTheme.colorScheme
  Row(
    modifier.fillMaxWidth().heightIn(min = Grid.u48)
      .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
      .padding(horizontal = Grid.inset, vertical = Grid.u6),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = GridText.Body, color = if (enabled) c.onSurface else c.onSurfaceVariant)
      if (subtitle != null) Text(subtitle, style = GridText.Small, color = c.onSurfaceVariant)
    }
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
  }
}

@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
  val c = MaterialTheme.colorScheme
  Row(
    modifier.fillMaxWidth().heightIn(min = Grid.u48)
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = Grid.inset, vertical = Grid.u6),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Grid.u12)
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = GridText.Body, color = c.onSurface)
      if (subtitle != null) Text(subtitle, style = GridText.Small, color = c.onSurfaceVariant)
    }
    Box(
      Modifier.size(Grid.icon).clip(CircleShape).border(Grid.hairline * 2, if (selected) c.primary else c.outline, CircleShape),
      contentAlignment = Alignment.Center
    ) { if (selected) Box(Modifier.size(Grid.u12).clip(CircleShape).background(c.primary)) }
  }
}

/** A row of equal segments (the three layers). */
@Composable
fun SegmentedTabs(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
  Row(
    modifier.fillMaxWidth().height(Grid.u48).clip(Grid.r12).background(MaterialTheme.colorScheme.surfaceContainer).padding(Grid.u6),
    horizontalArrangement = Arrangement.spacedBy(Grid.u6)
  ) { content() }
}

@Composable
fun androidx.compose.foundation.layout.RowScope.Segment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val c = MaterialTheme.colorScheme
  Box(
    modifier.weight(1f).fillMaxHeightOf().clip(Grid.r6)
      .background(if (selected) c.surfaceContainerHighest else c.surfaceContainer)
      .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(label, style = if (selected) GridText.BodyStrong else GridText.Body, color = if (selected) c.primary else c.onSurfaceVariant, textAlign = TextAlign.Center)
  }
}

private fun Modifier.fillMaxHeightOf(): Modifier = this.then(Modifier.height(Grid.u48 - Grid.u12))

@Composable
fun SubPageHeader(title: String, backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val c = MaterialTheme.colorScheme
  Column(modifier.fillMaxWidth().padding(horizontal = Grid.margin)) {
    Text(
      "‹ $backLabel", style = GridText.Body, color = c.primary,
      modifier = Modifier.heightIn(min = Grid.u48).clickable(role = Role.Button, onClick = onBack).padding(vertical = Grid.u12)
    )
    Text(title, style = GridText.Title, color = c.onSurface, modifier = Modifier.padding(bottom = Grid.u12))
  }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
  val c = MaterialTheme.colorScheme
  Box(
    modifier.fillMaxWidth().height(Grid.u48).clip(Grid.r12).background(c.surfaceContainer).padding(horizontal = Grid.inset),
    contentAlignment = Alignment.CenterStart
  ) {
    if (value.isEmpty()) Text(placeholder, style = GridText.Body, color = c.onSurfaceVariant)
    BasicTextField(
      value = value, onValueChange = onValueChange, singleLine = true,
      textStyle = GridText.Body.copy(color = c.onSurface), cursorBrush = SolidColor(c.primary),
      modifier = Modifier.fillMaxWidth()
    )
  }
}
