package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.theme.AppText
import com.example.ui.theme.Radius
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.Space

/*
 * The app's building blocks. Every screen is made of these, so a row, a group, a button or a header
 * looks and behaves the same everywhere: same type role, same height, same press feedback, same
 * accessibility semantics. Colours come from the theme, sizes from Tokens.kt.
 */

// ---- press feedback ----

/**
 * Click with the platform's quiet highlight: the surface darkens slightly while pressed, instead of a
 * ripple or a bounce. Used by rows and list cells.
 */
fun Modifier.highlightClickable(
  enabled: Boolean = true,
  role: Role = Role.Button,
  onClickLabel: String? = null,
  onClick: () -> Unit
): Modifier = composed {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val overlay by animateColorAsState(
    targetValue = if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
    // In at once, out gently: the highlight answers the finger immediately.
    animationSpec = tween(if (pressed) 0 else 220),
    label = "highlight"
  )
  this
    .clickable(
      interactionSource = interaction,
      indication = null,
      enabled = enabled,
      role = role,
      onClickLabel = onClickLabel,
      onClick = onClick
    )
    .background(overlay)
}

// ---- headers ----

/** A section's title inside a page, with an optional action at the trailing edge ("Ver tudo"). */
@Composable
fun SectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: () -> Unit = {}
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = title,
      style = AppText.Title2,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier
        .weight(1f)
        .semantics { heading() }
    )
    if (actionLabel != null) TextAction(actionLabel, onAction)
  }
}

/** The small label above a group of rows. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = AppText.Footnote,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier
      .padding(start = Space.page + Space.l, end = Space.page, bottom = Space.s)
      .semantics { heading() }
  )
}

/** The note under a group of rows, explaining what its setting does. */
@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = AppText.Footnote,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(start = Space.page + Space.l, end = Space.page + Space.l, top = Space.s)
  )
}

/**
 * The top of a sub-page: a back control naming where it goes, then the page's large title. Back is a
 * real button (48 dp, announced as "Voltar para …").
 */
@Composable
fun SubPageHeader(
  title: String,
  backLabel: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  trailing: @Composable RowScope.() -> Unit = {}
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = Space.s, end = Space.s),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier
          .heightIn(min = Size.touch)
          .clip(Shapes.s)
          .clickable(role = Role.Button, onClickLabel = "Voltar para $backLabel", onClick = onBack)
          .padding(horizontal = Space.s),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          Icons.AutoMirrored.Rounded.ArrowBackIos,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp)
        )
        Text(backLabel, style = AppText.Body, color = MaterialTheme.colorScheme.primary, maxLines = 1)
      }
      Spacer(Modifier.weight(1f))
      trailing()
    }
    Text(
      text = title,
      style = AppText.LargeTitle,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier
        .padding(start = Space.page, end = Space.page, top = Space.xs, bottom = Space.m)
        .semantics { heading() }
    )
  }
}

// ---- grouped lists ----

/**
 * A rounded group of rows on the page, the way grouped settings lists are drawn. Put [ListRow]s,
 * [SwitchRow]s or [RadioRow]s inside, separated by [GroupDivider].
 */
@Composable
fun InsetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = Space.page)
      .clip(Shapes.m)
      .background(MaterialTheme.colorScheme.surface),
    content = content
  )
}

/** Hairline between two rows of a group, inset to where the row's text starts. */
@Composable
fun GroupDivider(inset: Dp = Space.l) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = inset)
      .height(0.5.dp)
      .background(MaterialTheme.colorScheme.outline)
  )
}

/** A full-width hairline. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(0.5.dp)
      .background(MaterialTheme.colorScheme.outline)
  )
}

/** A rounded square holding a row's icon, in the accent or a colour of its own. */
@Composable
fun RowIcon(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
  Box(
    modifier = Modifier
      .size(30.dp)
      .clip(RoundedSquare)
      .background(tint.copy(alpha = 0.16f)),
    contentAlignment = Alignment.Center
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
  }
}

private val RoundedSquare = androidx.compose.foundation.shape.RoundedCornerShape(7.dp)

@Composable
private fun RowLayout(
  title: String,
  subtitle: String?,
  icon: ImageVector?,
  iconTint: Color,
  titleColor: Color,
  modifier: Modifier,
  trailing: @Composable RowScope.() -> Unit
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = Size.row)
      .padding(horizontal = Space.l, vertical = Space.s),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      RowIcon(icon, iconTint)
      Spacer(Modifier.width(Space.m))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = AppText.Body, color = titleColor)
      if (subtitle != null) {
        Text(subtitle, style = AppText.Footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    trailing()
  }
}

/**
 * A row that opens something or runs an action. [value] shows the current choice at the trailing
 * edge; [chevron] says it leads to another page; [destructive] colours it as an irreversible action.
 */
@Composable
fun ListRow(
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  iconTint: Color = MaterialTheme.colorScheme.primary,
  value: String? = null,
  chevron: Boolean = true,
  destructive: Boolean = false,
  enabled: Boolean = true
) {
  val colors = MaterialTheme.colorScheme
  RowLayout(
    title = title,
    subtitle = subtitle,
    icon = icon,
    iconTint = if (destructive) colors.error else iconTint,
    titleColor = when {
      !enabled -> colors.onSurface.copy(alpha = 0.38f)
      destructive -> colors.error
      else -> colors.onSurface
    },
    modifier = modifier.highlightClickable(enabled = enabled, onClick = onClick)
  ) {
    if (value != null) {
      Text(
        value,
        style = AppText.Body,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = Space.s)
      )
    }
    if (chevron) {
      Icon(
        Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
          .padding(start = Space.xs)
          .size(Size.iconSmall)
      )
    }
  }
}

/** A row with a text that only informs (version, cache size). */
@Composable
fun InfoRow(title: String, value: String, modifier: Modifier = Modifier) {
  RowLayout(
    title = title,
    subtitle = null,
    icon = null,
    iconTint = Color.Unspecified,
    titleColor = MaterialTheme.colorScheme.onSurface,
    modifier = modifier.semantics(mergeDescendants = true) {}
  ) {
    Text(value, style = AppText.Body, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
  }
}

/** A setting that is on or off. The whole row toggles; the switch only shows the state. */
@Composable
fun SwitchRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  enabled: Boolean = true
) {
  val colors = MaterialTheme.colorScheme
  RowLayout(
    title = title,
    subtitle = subtitle,
    icon = icon,
    iconTint = colors.primary,
    titleColor = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
    modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
  ) {
    Spacer(Modifier.width(Space.m))
    Switch(
      checked = checked,
      onCheckedChange = null,
      enabled = enabled,
      colors = SwitchDefaults.colors(
        checkedTrackColor = colors.primary,
        checkedThumbColor = colors.onPrimary,
        uncheckedTrackColor = colors.surfaceContainerHigh,
        uncheckedBorderColor = Color.Transparent,
        uncheckedThumbColor = colors.onSurfaceVariant
      )
    )
  }
}

/** One choice among several. The chosen one carries a check mark, as in the platform's own lists. */
@Composable
fun RadioRow(
  title: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null
) {
  val colors = MaterialTheme.colorScheme
  RowLayout(
    title = title,
    subtitle = subtitle,
    icon = icon,
    iconTint = colors.primary,
    titleColor = colors.onSurface,
    modifier = modifier
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
  ) {
    Box(Modifier.size(Size.icon), contentAlignment = Alignment.Center) {
      if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
    }
  }
}

// ---- buttons ----

/** The one main action of a screen or a sheet: filled with the accent, full width by default. */
@Composable
fun PrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true
) {
  val colors = MaterialTheme.colorScheme
  ButtonLayout(
    text = text,
    icon = icon,
    container = if (enabled) colors.primary else colors.surfaceContainerHigh,
    content = if (enabled) colors.onPrimary else colors.onSurface.copy(alpha = 0.38f),
    enabled = enabled,
    onClick = onClick,
    modifier = modifier
  )
}

/** A secondary action: a quiet fill with the accent's text, like Play and Shuffle above a list. */
@Composable
fun TonalButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  destructive: Boolean = false
) {
  val colors = MaterialTheme.colorScheme
  ButtonLayout(
    text = text,
    icon = icon,
    container = colors.surfaceContainer,
    content = when {
      !enabled -> colors.onSurface.copy(alpha = 0.38f)
      destructive -> colors.error
      else -> colors.primary
    },
    enabled = enabled,
    onClick = onClick,
    modifier = modifier
  )
}

@Composable
private fun ButtonLayout(
  text: String,
  icon: ImageVector?,
  container: Color,
  content: Color,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier
) {
  Row(
    modifier = modifier
      .heightIn(min = 50.dp)
      .clip(Shapes.m)
      .background(container)
      .highlightClickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = Space.l, vertical = Space.m),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(Size.iconSmall))
      Spacer(Modifier.width(Space.s))
    }
    Text(text, style = AppText.Headline, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** A text-only action in the accent colour ("Ver tudo", "Editar", "Limpar"), 48 dp tall. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  Box(
    modifier = modifier
      .heightIn(min = Size.touch)
      .clip(Shapes.s)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = Space.s),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text,
      style = AppText.Body,
      color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
      maxLines = 1
    )
  }
}

/** A round icon button with a label for screen readers. 48 dp touch, [size] visible. */
@Composable
fun IconAction(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color = LocalContentColor.current,
  filled: Boolean = false,
  size: Dp = 40.dp
) {
  Box(
    modifier = modifier
      .size(maxOf(size, Size.touch))
      .clip(androidx.compose.foundation.shape.CircleShape)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { this.contentDescription = contentDescription },
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .size(size)
        .clip(androidx.compose.foundation.shape.CircleShape)
        .then(if (filled) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Size.icon))
    }
  }
}

/**
 * A filter among a few views of one list (Músicas, Playlists, Favoritos). Announced as a tab; the
 * selected one is filled with the accent wash.
 */
@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Box(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .heightIn(min = 34.dp)
        .clip(Shapes.pill)
        .background(if (selected) colors.onSurface else colors.surfaceContainer)
        .padding(horizontal = 14.dp, vertical = 7.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        label,
        style = AppText.Subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        color = if (selected) colors.background else colors.onSurface,
        maxLines = 1
      )
    }
  }
}

// ---- search ----

/** The search field: a quiet rounded fill with the magnifier, and a clear button once there is text. */
@Composable
fun SearchField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  onSearch: () -> Unit = {}
) {
  val colors = MaterialTheme.colorScheme
  val textStyle: TextStyle = AppText.Body.copy(color = colors.onSurface)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 44.dp)
      .clip(Shapes.s)
      .background(colors.surfaceContainer)
      .padding(start = Space.m, end = Space.xs),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(Size.iconSmall))
    Spacer(Modifier.width(Space.s))
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
      if (value.isEmpty()) {
        Text(placeholder, style = AppText.Body, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier
          .fillMaxWidth()
          .semantics { contentDescription = placeholder }
      )
    }
    if (value.isNotEmpty()) {
      IconAction(
        icon = Icons.Rounded.Cancel,
        contentDescription = "Limpar busca",
        onClick = { onValueChange("") },
        tint = colors.onSurfaceVariant,
        size = 32.dp
      )
    } else {
      Spacer(Modifier.width(Space.s))
    }
  }
}

// ---- tracks ----

/**
 * A song in a list: artwork, title, artist line, and a "more" button. The current track shows its
 * title in the accent with a small equaliser on the artwork while it plays.
 */
@Composable
fun TrackRow(
  track: Track,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isCurrent: Boolean = false,
  isPlaying: Boolean = false,
  onMore: (() -> Unit)? = null,
  trailing: @Composable RowScope.() -> Unit = {}
) {
  val colors = MaterialTheme.colorScheme
  Row(
    modifier = modifier
      .fillMaxWidth()
      .highlightClickable(onClickLabel = "Tocar ${track.title}", onClick = onClick)
      .padding(start = Space.page, end = if (onMore != null) Space.xs else Space.page, top = 6.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    TrackArtwork(
      track = track,
      shape = Shapes.xs,
      iconSize = 22.dp,
      playing = isCurrent && isPlaying,
      modifier = Modifier.size(Size.rowArtwork)
    )
    Spacer(Modifier.width(Space.m))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        track.title,
        style = AppText.Body,
        color = if (isCurrent) colors.primary else colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        subtitle,
        style = AppText.Subhead,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    trailing()
    if (onMore != null) {
      IconAction(
        icon = Icons.Rounded.MoreHoriz,
        contentDescription = "Mais opções para ${track.title}",
        onClick = onMore,
        tint = colors.onSurfaceVariant
      )
    }
  }
}

/** Provides [color] as the content colour for icons placed inside a custom container. */
@Composable
fun WithContentColor(color: Color, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalContentColor provides color, content = content)
}

/** Corner radius of a group, exposed for screens that draw their own grouped surfaces. */
val GroupRadius: Dp get() = Radius.m
