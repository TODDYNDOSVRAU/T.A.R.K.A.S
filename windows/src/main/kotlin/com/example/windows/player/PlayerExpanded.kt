package com.example.windows.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.res.Res
import com.example.windows.res.lyrics_title
import com.example.windows.res.player_common_back
import com.example.windows.res.queue_header
import com.example.windows.res.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the expanded player is open. Esc (shell Back) closes it before the side pane. */
object ExpandedPlayer {
  private val _open = MutableStateFlow(false)
  val open: StateFlow<Boolean> = _open.asStateFlow()
  /** The tab shown on the right: Fila or Letras. */
  val tab = MutableStateFlow(PlayerPane.QUEUE)
  fun show() { _open.value = true }
  fun close(): Boolean { val was = _open.value; _open.value = false; return was }
}

/** Numbers of the slide-in, apart from the UI so they can be tested. */
object ExpandMath {
  const val OpenMs = 250
  const val CloseMs = 200
  /** Fraction of the height the panel rises from. */
  const val Rise = 0.08f
  fun alpha(p: Float): Float = p.coerceIn(0f, 1f)
  fun translateY(p: Float, heightPx: Float): Float = (1f - p.coerceIn(0f, 1f)) * heightPx * Rise
  /** The panel is drawn only while it is open or still moving. */
  fun visible(p: Float, open: Boolean): Boolean = open || p > 0.001f
}

private val Decelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/** Full-area now-playing panel: big cover with controls on the left, Fila / Letras tabs on the right. */
@Composable
fun ExpandedPlayerPanel(hooks: PlayerHooks, assets: TrackAssets) {
  val open by ExpandedPlayer.open.collectAsState()
  val p = remember { Animatable(0f) }
  LaunchedEffect(open) { p.animateTo(if (open) 1f else 0f, tween(if (open) ExpandMath.OpenMs else ExpandMath.CloseMs, easing = Decelerate)) }
  if (!ExpandMath.visible(p.value, open)) return
  val cs = MaterialTheme.colorScheme
  val t by hooks.state.collectAsState()
  val ui by hooks.controller.ui.collectAsState()
  val lyrics by assets.lyrics.collectAsState()
  val art by assets.art.collectAsState()
  val tab by ExpandedPlayer.tab.collectAsState()
  val tint = t.track?.let { Color(it.colorHex or 0xFF000000) } ?: cs.primary
  BoxWithConstraints(
    Modifier.fillMaxSize()
      .graphicsLayer { alpha = ExpandMath.alpha(p.value); translationY = ExpandMath.translateY(p.value, size.height) }
      .background(cs.background.copy(alpha = 0.72f))
      // The panel covers the area: clicks must not reach the screen below it.
      .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
  ) {
    val cover = if (maxHeight >= Grid.u384 + Grid.u192) Grid.u384 else Grid.u192
    Column(Modifier.fillMaxSize().padding(horizontal = Grid.margin, vertical = Grid.u12), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
      Row(
        Modifier.height(Grid.u48).clip(Grid.r12).clickable { ExpandedPlayer.close() }.padding(horizontal = Grid.u12),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u6)
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(Grid.icon), tint = cs.onSurface)
        Text(tr(Res.string.player_common_back), style = GridText.BodyStrong, color = cs.onSurface)
      }
      Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u24)) {
        Column(Modifier.width(cover).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
          Cover(t.track, tint, art, cover)
          TrackInfo(t, ui.problem?.toString())
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
            WaveformSeek(t.peaks, t.fraction, t.durationMs > 0, hooks::seekFraction, Modifier.fillMaxWidth().height(Grid.u48))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(SeekMath.label(t.positionMs), style = GridText.Label, color = cs.onSurfaceVariant)
              Text(SeekMath.label(t.durationMs), style = GridText.Label, color = cs.onSurfaceVariant)
            }
            Controls(hooks, t)
            Row(Modifier.fillMaxWidth().height(Grid.u48), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Filled.VolumeUp, null, Modifier.size(Grid.icon), tint = cs.onSurfaceVariant)
              VolumeLine(t.volume, hooks::setVolume, Modifier.weight(1f).padding(horizontal = Grid.u12))
            }
          }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
          Row(Modifier.fillMaxWidth().height(Grid.u48), horizontalArrangement = Arrangement.spacedBy(Grid.u12), verticalAlignment = Alignment.CenterVertically) {
            for ((k, label) in listOf(PlayerPane.QUEUE to tr(Res.string.queue_header), PlayerPane.LYRICS to tr(Res.string.lyrics_title))) {
              val on = tab == k
              Text(
                label, style = if (on) GridText.BodyStrong else GridText.Body,
                color = if (on) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.clip(Grid.r12).clickable { ExpandedPlayer.tab.value = k }.padding(horizontal = Grid.u12, vertical = Grid.u12),
              )
            }
          }
          if (tab == PlayerPane.LYRICS) LyricsView(lyrics, t, hooks, Modifier.weight(1f).fillMaxWidth())
          else androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) { QueuePane(hooks) }
        }
      }
    }
  }
}

@Composable
private fun VolumeLine(value: Float, onSet: (Float) -> Unit, modifier: Modifier) {
  val cs = MaterialTheme.colorScheme
  BoxWithConstraints(
    modifier.height(Grid.u24)
      .pointerInput(Unit) { detectTapGestures { onSet((it.x / size.width).coerceIn(0f, 1f)) } }
      .pointerInput(Unit) { detectDragGestures { ch, _ -> onSet((ch.position.x / size.width).coerceIn(0f, 1f)) } },
    contentAlignment = Alignment.CenterStart,
  ) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(Grid.u6).clip(Grid.r6).background(cs.onSurface.copy(alpha = 0.2f)))
    androidx.compose.foundation.layout.Box(Modifier.width(maxWidth * value.coerceIn(0f, 1f)).height(Grid.u6).clip(Grid.r6).background(cs.primary))
  }
}
