package com.example.windows.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.example.ui.theme.studioCard
import com.example.ui.theme.TarkasDarkColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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
  /** The bottom-left cover: opens the panel, or closes it when it is already open. Playback is not touched. */
  fun toggle(): Boolean { _open.value = !_open.value; return _open.value }
  /** Last animation progress (0..1), written each frame; read by tests and the Engineering panel. */
  @Volatile var progress: Float = 0f
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

  /** dp taken by everything that is not the cover: back row, info block (96), seek, times, controls, volume, gaps. */
  const val FixedHeightDp = 374
  /** dp the right column (tabs and list) needs at least, plus side margins and the gap between columns. */
  const val RightMinDp = 192 + 24 + 24 * 2

  /** The largest cover of the grid chain (384, 192, 96) that fits both the height and the width; nothing overlaps or is cut. */
  fun coverDp(heightDp: Float, widthDp: Float): Int =
    listOf(384, 192, 96).firstOrNull { heightDp >= it + FixedHeightDp && widthDp >= it + RightMinDp } ?: 96
}

private val Decelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/** Full-area now-playing panel: big cover with controls on the left, Fila / Letras tabs on the right. */
@Composable
fun ExpandedPlayerPanel(hooks: PlayerHooks, assets: TrackAssets) {
  val open by ExpandedPlayer.open.collectAsState()
  val p = remember { Animatable(0f) }
  LaunchedEffect(open) { p.animateTo(if (open) 1f else 0f, tween(if (open) ExpandMath.OpenMs else ExpandMath.CloseMs, easing = Decelerate)) { ExpandedPlayer.progress = value } }
  // Only the true/false flips recompose the panel; the frames of the slide are read in the layer block.
  val shown by remember { derivedStateOf { ExpandMath.visible(p.value, ExpandedPlayer.open.value) } }
  if (!shown) return
  MaterialTheme(colorScheme = TarkasDarkColorScheme) {
  val cs = MaterialTheme.colorScheme
  val t by hooks.state.collectAsState()
  val ui by hooks.controller.ui.collectAsState()
  val lyrics by assets.lyrics.collectAsState()
  val art by assets.art.collectAsState()
  val tab by ExpandedPlayer.tab.collectAsState()
  val tint = t.track?.let { Color(it.colorHex or 0xFF000000) } ?: cs.primary
  BoxWithConstraints(
    Modifier.fillMaxSize().testTag("expanded_player")
      .graphicsLayer { alpha = ExpandMath.alpha(p.value); translationY = ExpandMath.translateY(p.value, size.height) }
      .background(cs.background)
      // The panel covers the area: clicks must not reach the screen below it.
      .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
  ) {
    val cover = ExpandMath.coverDp(maxHeight.value, maxWidth.value).dp
    val dir = trackDirection(ui, t.track?.id)
    val wide = maxWidth >= Grid.u384 * 2 + Grid.u192
    Column(Modifier.fillMaxSize().padding(horizontal = Grid.margin, vertical = Grid.u12), verticalArrangement = Arrangement.spacedBy(Grid.u12)) {
      Row(
        Modifier.height(Grid.u48).testTag("exp_close").clip(Grid.r12).clickable { ExpandedPlayer.close() }.padding(horizontal = Grid.u12),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Grid.u6)
      ) {
        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(Grid.icon), tint = cs.onSurface)
        Text(tr(Res.string.player_common_back), style = GridText.BodyStrong, color = cs.onSurface)
      }
      Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Grid.u24)) {
        // Left half: the cover.
        TrackSwap(Shown(t.track, art), dir, Modifier.weight(1f).fillMaxHeight()) { sh ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.testTag("exp_cover")) { Cover(sh.track, sh.track?.let { Color(it.colorHex or 0xFF000000) } ?: tint, sh.art, cover) }
          }
        }
        // Right half: title, progress, transport, volume (max 440 dp).
        Column(Modifier.weight(1f).widthIn(max = Grid.u384 + Grid.u48 + Grid.u6 * 1).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
          TrackSwap(Shown(t.track, art), dir, Modifier.fillMaxWidth().height(Grid.u96).testTag("exp_info")) { sh -> TrackInfo(t.copy(track = sh.track), ui.problem?.toString()) }
          Spacer(Modifier.height(Grid.u12))
          SeekSection(hooks, t, Grid.u48, Modifier.fillMaxWidth(), Modifier.wheelSeek(hooks).testTag("exp_seek"))
          Box(Modifier.testTag("exp_controls")) { Controls(hooks, t) }
          Row(Modifier.fillMaxWidth().height(Grid.u48), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VolumeUp, null, Modifier.size(Grid.icon), tint = cs.onSurfaceVariant)
            VolumeLine(t.volume, hooks::setVolume, Modifier.weight(1f).padding(horizontal = Grid.u12))
          }
        }
        // Side column: Fila / Letras, only when the window is wide enough.
        if (wide) Column(Modifier.weight(1f).fillMaxHeight().studioCard()) {
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
          else Box(Modifier.weight(1f).fillMaxWidth()) { QueuePane(hooks) }
        }
      }
    }
  }
  }
}

/** Mouse wheel over the scrub bar seeks by 5 s per notch. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun Modifier.wheelSeek(hooks: PlayerHooks): Modifier = onPointerEvent(PointerEventType.Scroll) { e ->
  val dy = e.changes.firstOrNull()?.scrollDelta?.y ?: 0f
  if (dy != 0f) hooks.seekBy(if (dy < 0f) SeekMath.WheelStepMs else -SeekMath.WheelStepMs)
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
    Box(Modifier.fillMaxWidth().height(Grid.u6).clip(Grid.r6).background(cs.onSurface.copy(alpha = 0.2f)))
    Box(Modifier.width(maxWidth * value.coerceIn(0f, 1f)).height(Grid.u6).clip(Grid.r6).background(cs.primary))
  }
}
