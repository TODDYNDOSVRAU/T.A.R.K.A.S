package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import com.example.ui.theme.glassPill
import com.example.ui.theme.glassControl
import com.example.ui.theme.glassContentColor
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Track
import com.example.ui.theme.SpaceGrotesk
import com.example.ui.theme.pressClickable
import com.example.ui.theme.TarkasCyanPrimary
import com.example.ui.theme.TarkasSkySecondary
import com.example.ui.viewmodel.TarkasNavTab

/**
 * The app's one chip/pill: a capsule that follows the chosen surface style (solid, frosted, liquid).
 * Used for section tabs, quick actions and toggles so every balloon in the app looks the same.
 */
@Composable
fun GlassChip(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  enabled: Boolean = true
) {
  val content = glassContentColor(selected).copy(alpha = if (enabled) 1f else 0.4f)
  // A soft springy squeeze while pressed, read in the draw phase so it never recomposes.
  val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale = androidx.compose.animation.core.animateFloatAsState(
    targetValue = if (pressed) 0.95f else 1f,
    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 600f),
    label = "chipPress"
  )
  Row(
    modifier = modifier
      .graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
      }
      .heightIn(min = 44.dp)
      .glassControl(RoundedCornerShape(22.dp), selected = selected)
      .selectable(
        selected = selected,
        enabled = enabled,
        role = androidx.compose.ui.semantics.Role.Button,
        interactionSource = interaction,
        indication = null,
        onClick = onClick
      )
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    if (icon != null) {
      Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = label,
      color = content,
      fontSize = 15.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      softWrap = false
    )
  }
}

/** Large bold page title with a round gear button opening Ajustes. */
@Composable
fun TarkasLargeTitle(
  title: String,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  /** Position of this screen in the bar, so the title slides the same way as the page. */
  order: Int = 0
) {
  Row(
    modifier = modifier
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .fillMaxWidth()
      .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // The title lives outside the screen transition, so it fades through on the same timing as the
    // page below; otherwise the new title shows over the old page while that one is still leaving.
    val reduced = com.example.ui.theme.rememberReducedMotion()
    androidx.compose.animation.AnimatedContent(
      targetState = order to title,
      modifier = Modifier.weight(1f),
      transitionSpec = {
        com.example.ui.theme.titleTransition(initialState.first, targetState.first, reduced)
      },
      label = "title"
    ) { shown ->
      Text(
        text = shown.second,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onBackground
      )
    }
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .clickable(onClickLabel = "Abrir ajustes", onClick = onOpenSettings)
        .testTag("topbar_settings_button"),
      contentAlignment = Alignment.Center
    ) {
      // A gear on a round glass button, like the capsules; the brand mark here read as a logo.
      Box(
        modifier = Modifier
          .size(44.dp)
          .glassControl(CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Settings,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(22.dp)
        )
      }
    }
  }
}

/** Page section title, optionally with the "›" that Apple Music uses for titles that open a list. */
@Composable
fun TarkasSectionTitle(
  text: String,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null
) {
  Row(
    modifier = modifier
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = text,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp,
      letterSpacing = (-0.3).sp,
      color = MaterialTheme.colorScheme.onBackground
    )
    if (onClick != null) {
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
      )
    }
  }
}

/** The T.A.R.K.A.S. symbol: bar, diamond and forked tail, drawn on a 132x80 grid. */
@Composable
fun TarkasMark(
  modifier: Modifier = Modifier,
  symbolColor: Color = MaterialTheme.colorScheme.onBackground,
  accentColor: Color = MaterialTheme.colorScheme.primary
) {
  Canvas(modifier = modifier) {
    val k = size.width / 132f
    val stroke = 12f * k
    fun p(x: Float, y: Float) = Offset(x * k, y * k)

    // accent bar
    drawRect(accentColor, topLeft = p(0f, 34f), size = Size(29f * k, 12f * k))

    // diamond outline
    val diamond = androidx.compose.ui.graphics.Path().apply {
      moveTo(66f * k, 6f * k)
      lineTo(102f * k, 40f * k)
      lineTo(66f * k, 74f * k)
      lineTo(30f * k, 40f * k)
      close()
    }
    drawPath(
      diamond,
      symbolColor,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, join = androidx.compose.ui.graphics.StrokeJoin.Miter)
    )

    // tail: horizontal bar and two diagonals
    drawRect(symbolColor, topLeft = p(103f, 34f), size = Size(29f * k, 12f * k))
    drawLine(symbolColor, p(100f, 40f), p(126f, 8f), strokeWidth = stroke)
    drawLine(symbolColor, p(100f, 40f), p(126f, 72f), strokeWidth = stroke)
  }
}

@Composable
fun TarkasBottomNav(
  currentTab: TarkasNavTab,
  onTabSelected: (TarkasNavTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val items = listOf(
    // No "Música" tab: the player opens from the mini player, the only way in.
    Triple(TarkasNavTab.HOME, "Início", Icons.Default.PlayCircleFilled),
    Triple(TarkasNavTab.LIBRARY, "Biblioteca", Icons.Default.LibraryMusic),
    Triple(TarkasNavTab.AUDIO, "Áudio", Icons.Default.Equalizer),
    Triple(TarkasNavTab.ASSISTANT, "Assistente", Icons.Default.Psychology)
  )

  // A floating capsule of glass; the selected tab gets its own lighter capsule inside it.
  Row(
    modifier = modifier
      .fillMaxWidth()
      .glassPill(cornerRadius = 32.dp)
      .padding(horizontal = 6.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    items.forEach { (tab, label, icon) ->
      val selected = currentTab == tab
      val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "navTint"
      )
      val pill by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(260, easing = com.example.ui.theme.Motion.Settle),
        label = "navPill"
      )
      // The selected tab's icon pops up a little with a spring (scale only, no relayout).
      val pop by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
          dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
          stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "navPop"
      )
      val pillColor = MaterialTheme.colorScheme.onSurface
      Column(
        modifier = Modifier
          .weight(1f)
          .testTag("nav_tab_${tab.name.lowercase()}")
          .clip(RoundedCornerShape(26.dp))
          .drawBehind {
            if (pill > 0f) drawRoundRect(pillColor.copy(alpha = 0.10f * pill), cornerRadius = CornerRadius(26.dp.toPx()))
          }
          .selectable(
            selected = selected,
            role = androidx.compose.ui.semantics.Role.Tab,
            onClick = { onTabSelected(tab) }
          )
          .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = tint,
          modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
              scaleX = pop
              scaleY = pop
            }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = label,
          color = tint,
          fontSize = 11.sp,
          maxLines = 1,
          softWrap = false,
          fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
      }
    }
  }
}

@Composable
fun MiniPlayerBar(
  track: Track?,
  isPlaying: Boolean,
  onTogglePlayPause: () -> Unit,
  onNext: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (track == null) return

  // Its own floating capsule of glass, like the tab bar below it.
  Box(
    modifier = modifier
      .fillMaxWidth()
      .glassPill(cornerRadius = 30.dp)
      .pressClickable(pressedScale = 0.985f, onClick = onClick)
      .testTag("mini_player_bar")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TrackArtwork(track = track, shape = RoundedCornerShape(16.dp), iconSize = 24.dp, modifier = Modifier.size(46.dp))

      Spacer(modifier = Modifier.width(14.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = track.title,
          fontSize = 17.sp,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = track.artist,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      IconButton(
        onClick = onTogglePlayPause,
        modifier = Modifier
          .size(48.dp)
          .testTag("mini_player_play_pause")
      ) {
        Icon(
          imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(34.dp)
        )
      }

      IconButton(
        onClick = onNext,
        modifier = Modifier
          .size(48.dp)
          .testTag("mini_player_next")
      ) {
        Icon(
          imageVector = Icons.Default.FastForward,
          contentDescription = "Próxima Faixa",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(34.dp)
        )
      }
    }
  }
}

@Composable
fun ToolReportCard(
  report: com.example.data.model.ToolExecutionReport,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.background,
    modifier = modifier
      .fillMaxWidth()
      .testTag("tool_report_${report.toolName}")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (report.success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
        contentDescription = null,
        tint = if (report.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        modifier = Modifier.size(14.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Column {
        Text(
          text = "Ação: ${report.toolName}()",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = report.resultSummary,
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun CuriosityDetailDialog(
  curiosity: com.example.data.model.Curiosity,
  onDismiss: () -> Unit
) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text(
          text = "💡 Curiosidade da Faixa",
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "${curiosity.trackTitle} • ${curiosity.artist}",
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = curiosity.shortTrivia,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurface,
          lineHeight = 19.sp
        )

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = "FATOS HISTÓRICOS:",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = curiosity.documentedFacts,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 15.sp,
              modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )

            Text(
              text = "FONTES DOCUMENTADAS:",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            curiosity.sources.forEach { src ->
              Text(
                text = "• $src",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
              )
            }
          }
        }
      }
    },
    confirmButton = {
      androidx.compose.material3.Button(onClick = onDismiss) {
        Text("Fechar")
      }
    }
  )
}


