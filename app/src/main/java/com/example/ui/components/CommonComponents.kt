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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Track
import com.example.ui.theme.SpaceGrotesk
import com.example.ui.theme.TarkasCyanPrimary
import com.example.ui.theme.TarkasSkySecondary
import com.example.ui.viewmodel.TarkasNavTab

/** Large bold page title with the profile-style round button (the T.A.R.K.A.S. mark) opening Ajustes. */
@Composable
fun TarkasLargeTitle(
  title: String,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .background(MaterialTheme.colorScheme.background)
      .statusBarsPadding()
      .fillMaxWidth()
      .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = title,
      modifier = Modifier.weight(1f),
      fontWeight = FontWeight.Bold,
      fontSize = 34.sp,
      letterSpacing = (-0.5).sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      color = MaterialTheme.colorScheme.onBackground
    )
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .clickable(onClickLabel = "Abrir ajustes", onClick = onOpenSettings)
        .testTag("topbar_settings_button"),
      contentAlignment = Alignment.Center
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        TarkasMark(
          modifier = Modifier.size(width = 22.dp, height = 14.dp),
          symbolColor = MaterialTheme.colorScheme.primary
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
  Column(modifier = modifier) {
  androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
  NavigationBar(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    tonalElevation = 0.dp
  ) {
    val items = listOf(
      Triple(TarkasNavTab.HOME, "Início", Icons.Default.PlayCircleFilled),
      Triple(TarkasNavTab.MUSIC, "Música", Icons.Default.MusicNote),
      Triple(TarkasNavTab.LIBRARY, "Biblioteca", Icons.Default.LibraryMusic),
      Triple(TarkasNavTab.AUDIO, "Áudio", Icons.Default.Equalizer),
      Triple(TarkasNavTab.ASSISTANT, "Assistente", Icons.Default.Psychology)
    )

    items.forEach { (tab, label, icon) ->
      val selected = currentTab == tab
      NavigationBarItem(
        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
        selected = selected,
        onClick = { onTabSelected(tab) },
        icon = {
          Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp)
          )
        },
        label = {
          Text(
            text = label,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
          )
        },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = MaterialTheme.colorScheme.primary,
          selectedTextColor = MaterialTheme.colorScheme.primary,
          indicatorColor = Color.Transparent,
          unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
          unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
      )
    }
  }
  }
}

@Composable
fun MiniPlayerBar(
  track: Track?,
  isPlaying: Boolean,
  waveformEnergy: Float,
  onTogglePlayPause: () -> Unit,
  onNext: () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (track == null) return

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("mini_player_bar"),
    color = MaterialTheme.colorScheme.surface
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(Brush.verticalGradient(listOf(Color(track.colorHex), Color(track.colorHex).copy(alpha = 0.6f)))),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.MusicNote,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(24.dp)
        )
      }

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
fun WaveformVisualizer(
  energy: Float,
  barCount: Int = 18,
  modifier: Modifier = Modifier,
  barColor: Color = MaterialTheme.colorScheme.primary
) {
  val infiniteTransition = rememberInfiniteTransition(label = "WaveformAnimation")
  val pulse by infiniteTransition.animateFloat(
    initialValue = 0.3f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(400, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse"
  )

  Canvas(modifier = modifier) {
    val totalWidth = size.width
    val totalHeight = size.height
    val barWidth = (totalWidth / barCount) * 0.65f
    val gap = (totalWidth / barCount) * 0.35f

    for (i in 0 until barCount) {
      val centerDist = kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)
      val waveFactor = kotlin.math.sin(i * 0.8 + pulse * Math.PI).toFloat()
      val heightFraction = ((1f - centerDist * 0.5f) * energy * (0.4f + 0.6f * kotlin.math.abs(waveFactor))).coerceIn(0.12f, 1f)
      val barHeight = totalHeight * heightFraction

      val x = i * (barWidth + gap)
      val y = (totalHeight - barHeight) / 2f

      drawRoundRect(
        color = barColor,
        topLeft = Offset(x, y),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
      )
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


