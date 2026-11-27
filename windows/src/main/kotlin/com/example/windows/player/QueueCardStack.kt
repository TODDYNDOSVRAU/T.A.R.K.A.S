package com.example.windows.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.model.Track
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import com.example.windows.biblioteca.CoverLoader
import com.example.windows.biblioteca.displayArtist
import com.example.windows.res.Res
import com.example.windows.res.stack_announce
import com.example.windows.res.stack_empty
import com.example.windows.res.stack_label
import com.example.windows.res.stack_status_next
import com.example.windows.res.stack_status_playing
import com.example.windows.res.tr
import com.example.windows.state.TarkasState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/**
 * The player's stack of queue cards for the Home. The front card is the track playing; the cards behind it are what
 * Next would play, in that order, read from the real queue (never a copy). A throw to the left plays the next track,
 * a throw to the right the previous one, a click on a card behind plays that track. Fills the room the Home gives it.
 */
@Composable
fun QueueCardStack(state: TarkasState, modifier: Modifier = Modifier) {
  val hooks = PlayerHost.hooks(state)
  val assets = PlayerHost.assets(state)
  val reduced by state.reduceMotion.collectAsState()
  QueueCardStackBody(hooks, assets, reduced, modifier)
}

/** The registration the shell fills [com.example.windows.shell.Panels.queueStack] with. */
internal val QueueStackPanel = com.example.windows.shell.PanelContent { s -> QueueCardStack(s) }

/** Without the shell state, so it can be tested with plain hooks; a test passes its own [engine] to read and drive the cards. */
@Composable
internal fun QueueCardStackBody(
  hooks: PlayerHooks,
  assets: TrackAssets,
  reduced: Boolean,
  modifier: Modifier = Modifier,
  engine: QueueStackEngine = remember { QueueStackEngine() },
) {
  val cs = MaterialTheme.colorScheme
  val ui by hooks.controller.ui.collectAsState()
  val nb = remember(ui) { hooks.controller.neighbors() }
  val cur = nb.current
  val tracks = remember(nb) { if (cur == null) emptyList() else listOf(cur) + nb.after }
  val keys = remember(nb) { StackModel.keys(cur, nb.after) }
  // Kept by key so a card that is fading out still has its text.
  val registry = remember { HashMap<String, Track>() }
  keys.forEachIndexed { i, k -> registry[k] = tracks[i] }
  if (registry.size > 24) registry.keys.retainAll(keys.toSet())
  engine.canNext = nb.canGoNext
  engine.canPrev = nb.canGoPrevious
  engine.reduced = reduced
  val covers = remember { mutableStateMapOf<String, ImageBitmap>() }
  val tried = remember { HashSet<String>() }
  val curArt by assets.art.collectAsState()
  LaunchedEffect(curArt, cur?.id) { if (cur != null && curArt != null) covers[cur.id] = curArt!! }
  LaunchedEffect(keys) {
    for (t in tracks) if (t.id !in covers && tried.add(t.id)) {
      val b = runCatching { loadCover(t) }.getOrNull()
      if (b != null) covers[t.id] = b
    }
    if (covers.size > 16) {
      val keep = tracks.mapTo(HashSet()) { it.id }
      covers.keys.filter { it !in keep }.take(covers.size - 16).forEach { covers.remove(it); tried.remove(it) }
    }
  }
  LaunchedEffect(keys) { engine.sync(keys) }
  FrameLoop(engine)

  val latest by rememberUpdatedState(hooks to tracks)
  val announce = cur?.let {
    val list = ui.list
    val pos = list.indexOfFirst { t -> t.id == it.id } + 1
    tr(Res.string.stack_announce, it.title, if (pos > 0) pos else 1, max(list.size, 1))
  }.orEmpty()
  val label = tr(Res.string.stack_label)

  BoxWithConstraints(modifier.fillMaxSize().testTag("queue_stack")) {
    if (cur == null) {
      Box(Modifier.fillMaxSize().padding(Grid.u12), contentAlignment = Alignment.Center) {
        Text(
          tr(Res.string.stack_empty), style = GridText.Small, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
          modifier = Modifier.testTag("queue_stack_empty"),
        )
      }
    } else {
    val fit = remember(maxWidth, maxHeight) { StackLayout.fit(maxWidth.value, maxHeight.value) }
    val fan = StackLayout.fanFor(maxWidth.value)
    remember(fit, fan) { engine.layout(fit.w.toFloat(), fit.h.toFloat(), fan, fit.back); 0 }
    remember(Unit) { engine.sync(keys); 0 }
    val frontCx = fit.w * (0.5f + StackLayout.LeftShift)
    var overBack by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val cards = engine.cards

    Box(
      Modifier.align(Alignment.Center).size(fit.totalW.dp, fit.h.dp).testTag("queue_stack_stage")
        .semantics { contentDescription = label + ": " + announce; liveRegion = LiveRegionMode.Polite }
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .onKeyEvent { e ->
          if (e.type != KeyEventType.KeyDown) false
          else when (e.key) {
            Key.DirectionRight -> if (engine.canNext) { latest.first.next(); true } else false
            Key.DirectionLeft -> if (engine.canPrev) { latest.first.prev(); true } else false
            else -> false
          }
        }
        .pointerHoverIcon(if (overBack) PointerIcon.Hand else PointerIcon.Default)
        .pointerInput(engine) {
          val d = density
          val speed = ThrowVelocity()
          awaitPointerEventScope {
            var slot = -1
            var downX = 0f; var downY = 0f
            var dragged = false
            while (true) {
              val ev = awaitPointerEvent()
              val ch = ev.changes.firstOrNull() ?: continue
              val px = ch.position.x / d - frontCx
              val py = ch.position.y / d - engine.h / 2f
              when (ev.type) {
                PointerEventType.Enter, PointerEventType.Move -> {
                  if (slot == 0) {
                    val dx = px - downX; val dy = py - downY
                    if (!dragged && abs(dx) >= StackTuning.DragStartDp && abs(dx) > abs(dy)) {
                      dragged = true; speed.reset(); engine.beginDrag()
                    }
                    if (dragged) {
                      speed.add(ch.uptimeMillis, dx)
                      engine.drag(dx, dy, speed.release(ch.uptimeMillis))
                      ch.consume()
                    }
                  }
                  engine.hoverMove(px, py)
                  val b = slot < 0 && engine.hitBack(px, py) > 0
                  if (b != overBack) overBack = b
                }
                PointerEventType.Exit -> if (slot < 0) { engine.hoverExit(); overBack = false }
                PointerEventType.Press -> if (slot < 0 && ev.buttons.isPrimaryPressed) {
                  if (engine.hitFront(px, py)) {
                    if (engine.canGrab()) { slot = 0; downX = px; downY = py; dragged = false; engine.press(true) }
                  } else {
                    val b = engine.hitBack(px, py)
                    if (b > 0) { slot = b; downX = px; downY = py; dragged = false }
                  }
                }
                PointerEventType.Release -> if (slot >= 0) {
                  if (slot == 0) {
                    if (dragged) {
                      when (engine.release(speed.release(ch.uptimeMillis))) {
                        Release.Next -> latest.first.next()
                        Release.Prev -> latest.first.prev()
                        Release.None -> {}
                      }
                      ch.consume()
                    } else engine.press(false)
                  } else if (engine.hitBack(px, py) == slot) {
                    playCard(latest.first, latest.second, slot)
                  }
                  slot = -1; dragged = false
                }
                else -> {}
              }
            }
          }
        },
    ) {
      for (c in cards) key(c.key) {
        val track = registry[c.key]
        if (track != null) {
          val z = if (c.flying) 50f else if (c.leaving) 0f else (10 - c.slot).toFloat()
          val front = c.slot == 0 && !c.leaving
          StackCardView(
            c, engine, track, covers[track.id], fit.w, fit.h, fan, reduced, focused && front,
            Modifier.align(Alignment.CenterStart).padding(start = (StackLayout.LeftShift * fit.w).dp).zIndex(z),
          )
        }
      }
    }
    }
  }
}

/** The click on a card behind: the queue's own item when it is one of "A seguir", else that track of the list. */
internal fun playCard(hooks: PlayerHooks, tracks: List<Track>, slot: Int) {
  val track = tracks.getOrNull(slot) ?: return
  val c = hooks.controller
  val k = slot - 1
  if (k < c.ui.value.queue.size) c.playQueueItem(k) else c.play(track)
}

/** Runs the engine's frames while something moves and sleeps when everything is at rest or the stack leaves the screen. */
@Composable
private fun FrameLoop(engine: QueueStackEngine) {
  LaunchedEffect(engine) {
    val wake = Channel<Unit>(Channel.CONFLATED)
    engine.waker = { wake.trySend(Unit) }
    wake.trySend(Unit)
    try {
      while (true) {
        wake.receive()
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
          val now = androidx.compose.runtime.withFrameNanos { it }
          val rest = engine.frame((now - last) / 1_000_000_000f)
          last = now
          if (rest) break
        }
      }
    } finally {
      engine.waker = null
    }
  }
}

private suspend fun loadCover(t: Track): ImageBitmap? = when (val p = CoverSource.pick(t)) {
  is CoverSource.Remote -> CoverLoader.load(p.url) ?: t.filePath?.let { f -> withContext(Dispatchers.IO) { CoverArt.load(File(f)) } }
  is CoverSource.Local -> withContext(Dispatchers.IO) { CoverArt.load(p.file) }
  CoverSource.None -> null
}

@Composable
private fun StackCardView(
  c: StackCard,
  engine: QueueStackEngine,
  track: Track,
  art: ImageBitmap?,
  w: Int,
  h: Int,
  fan: Fan,
  reduced: Boolean,
  focus: Boolean,
  modifier: Modifier,
) {
  val cs = MaterialTheme.colorScheme
  val slot = c.slot
  val front = slot == 0
  val shape = if (w >= 192) Grid.r24 else Grid.r12
  val shown = !c.entrance || c.lead || reduced
  val sem = if (front && !c.leaving) Modifier.testTag("stack_card_0") else Modifier.clearAndSetSemantics { semanticsTestTag = "stack_card_$slot" }
  Box(
    modifier.size(w.dp, h.dp)
      .graphicsLayer {
        engine.tick
        translationX = c.x.x * density; translationY = c.y.x * density
        scaleX = c.s.x; scaleY = c.s.x
        rotationZ = c.r.x; rotationX = c.rx.x; rotationY = c.ry.x
        cameraDistance = 16f * density
        alpha = c.o.coerceIn(0f, 1f)
      }
      .then(sem)
      .clip(shape)
      .background(cs.surfaceContainer)
      .border(Grid.hairline, if (focus) cs.primary else cs.outlineVariant, shape),
  ) {
    if (w < 192) {
      CardCover(track, art, Modifier.fillMaxSize(), Grid.r12)
    } else {
      Column(Modifier.fillMaxSize().padding(Grid.inset)) {
        CardCover(track, art, Modifier.size((w - 24).dp), Grid.r12)
        Spacer(Modifier.height(Grid.u6))
        CardText(track, if (front && !c.leaving) tr(Res.string.stack_status_playing) else tr(Res.string.stack_status_next), front, shown, reduced)
      }
    }
    // The cards behind are veiled the further back they sit, by the same graphics layer as the rest of the motion.
    if (!front) Box(
      Modifier.fillMaxSize().graphicsLayer {
        engine.tick
        alpha = ((1f - c.s.x) / (2f * fan.s)).coerceIn(0f, 1f) * 0.6f
      }.background(cs.background),
    )
  }
}

@Composable
private fun CardCover(track: Track, art: ImageBitmap?, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
  if (art != null) {
    Image(art, null, modifier.clip(shape), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
  } else {
    val tint = Color(track.colorHex or 0xFF000000)
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.9f), tint.copy(alpha = 0.35f)))), contentAlignment = Alignment.Center) {
      Icon(Icons.Filled.MusicNote, null, Modifier.size(Grid.u48), tint = MaterialTheme.colorScheme.onPrimary)
    }
  }
}

private fun revealSpec(reduced: Boolean, delay: Int): AnimationSpec<Float> =
  if (reduced) tween(0) else tween(300, delay, FastOutSlowInEasing)

private fun Modifier.rise(a: Float): Modifier = graphicsLayer { alpha = a; translationY = (1f - a) * 6.dp.toPx() }

/** Status, title, artist and album, each line arriving a little after the one above on the front card. */
@Composable
private fun CardText(track: Track, status: String, front: Boolean, shown: Boolean, reduced: Boolean) {
  val cs = MaterialTheme.colorScheme
  val target = if (shown) 1f else 0f
  val a0 by animateFloatAsState(target, revealSpec(reduced, 0), label = "stack-status")
  val a1 by animateFloatAsState(target, revealSpec(reduced, 60), label = "stack-title")
  val a2 by animateFloatAsState(target, revealSpec(reduced, 120), label = "stack-artist")
  val a3 by animateFloatAsState(target, revealSpec(reduced, 180), label = "stack-album")
  Column(Modifier.fillMaxWidth()) {
    Text(
      status, style = GridText.Label, color = if (front) cs.primary else cs.onSurfaceVariant, maxLines = 1,
      modifier = Modifier.rise(a0).semantics { liveRegion = LiveRegionMode.Polite }.testTag("stack_status"),
    )
    Text(track.title, style = GridText.BodyStrong, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.rise(a1).testTag("stack_title"))
    Text(displayArtist(track.artist), style = GridText.Small, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.rise(a2).testTag("stack_artist"))
    Text(track.album, style = GridText.Small, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.rise(a3).testTag("stack_album"))
  }
}
