package com.example.windows.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.model.Track
import com.example.ui.theme.Grid

/**
 * The geometry of the cards behind the art card of the Home corner. It is the fan of the queue stack on the Player page
 * ([Fan.Narrow], [StackTuning]) reduced to what a 192 square card needs: the playing cover keeps its size, the next tracks
 * sit slightly smaller behind it, peeking to the right and a little lower, each one veiled more than the one before.
 * Pure, so it can be tested without a window.
 */
internal object ArtStackMath {
  /** Back cards drawn at most: the two tracks that play next. */
  const val MaxBack = StackTuning.MaxBack
  private val fan = Fan.Narrow

  /** Width the back cards add to the right of the front card: the farthest edge of slot [MaxBack] when the fan is open. */
  val ReachDp: Float get() = rightEdge(MaxBack, true) - CompactMath.CardDp

  /** Scale of the card at [slot]: 1 for the playing one, `1 - 0.07 k` behind it. */
  fun scale(slot: Int): Float = if (slot <= 0) 1f else 1f - fan.s * minOf(slot, MaxBack)

  /** Horizontal shift of the card's centre from the front's, in dp; the fan opens [StackTuning.OpenFactor] wider while the pointer is over it. */
  fun dx(slot: Int, open: Boolean): Float {
    if (slot <= 0) return 0f
    val k = minOf(slot, MaxBack)
    val x = CompactMath.CardDp * fan.x * (if (k == 1) 1f else 1.9f)
    return if (open) x * StackTuning.OpenFactor else x
  }

  /** Vertical shift in dp: a smaller card sinks so the stack reads as sitting on one floor. */
  fun dy(slot: Int): Float = if (slot <= 0) 0f else CompactMath.CardDp * (1f - scale(slot)) * 0.42f

  /** Tilt of the open fan, in degrees. */
  fun rotation(slot: Int, open: Boolean): Float = if (open && slot > 0) 1.6f * minOf(slot, MaxBack) else 0f

  /** Share of the page background laid over the card: grows with the distance, 0.3 then 0.6. */
  fun veil(scale: Float): Float = ((1f - scale) / (2f * fan.s)).coerceIn(0f, 1f) * 0.6f

  /** Right edge, in dp from the front card's left edge, of the card at [slot] when the fan is open. */
  fun rightEdge(slot: Int, open: Boolean): Float = CompactMath.CardDp / 2f + dx(slot, open) + scale(slot) * CompactMath.CardDp / 2f

  /** The tracks to draw: the playing one, then the next [MaxBack]. */
  fun tracks(current: Track?, after: List<Track>): List<Track> = if (current == null) emptyList() else listOf(current) + after.take(MaxBack)
}

/**
 * The art card of the Home corner with the next tracks stacked behind it (the effect of the queue stack of the Player page,
 * which stays as it was). [front] is the existing card, drawn at its own 192 size on top; its lean toward the pointer is
 * untouched. Back cards are GPU-only (`graphicsLayer`), follow the queue by key so a track that moves up or back keeps its
 * card, and a click on one plays that track. [reduced] puts every card in place without motion.
 */
@Composable
internal fun ArtCardStack(hooks: PlayerHooks, assets: TrackAssets, reduced: Boolean, front: @Composable () -> Unit) {
  val ui by hooks.controller.ui.collectAsState()
  val nb = remember(ui) { hooks.controller.neighbors() }
  val tracks = remember(nb) { ArtStackMath.tracks(nb.current, nb.after) }
  val keys = remember(nb) { StackModel.keys(nb.current, nb.after.take(ArtStackMath.MaxBack)) }
  val curArt by assets.art.collectAsState()
  val covers = remember { mutableStateMapOf<String, ImageBitmap>() }
  val tried = remember { HashSet<String>() }
  LaunchedEffect(keys) {
    for (t in tracks.drop(1)) if (t.id !in covers && tried.add(t.id)) {
      val b = runCatching { loadCover(t) }.getOrNull()
      if (b != null) covers[t.id] = b
    }
    if (covers.size > 8) {
      val keep = tracks.mapTo(HashSet()) { it.id }
      covers.keys.filter { it !in keep }.forEach { covers.remove(it); tried.remove(it) }
    }
  }
  LaunchedEffect(curArt, nb.current?.id) { val c = nb.current; val a = curArt; if (c != null && a != null) covers[c.id] = a }

  val source = remember { MutableInteractionSource() }
  val hovered by source.collectIsHoveredAsState()
  val open = hovered && !reduced && tracks.size > 1
  val latest = rememberUpdatedState(hooks to tracks)
  val width = (CompactMath.CardDp + ArtStackMath.ReachDp.toInt() + 1).dp

  Box(Modifier.size(width, Grid.u192).testTag("art_stack")) {
    for ((slot, t) in tracks.withIndex()) key(keys[slot]) {
      BackCard(
        track = t, art = if (slot == 0) curArt else covers[t.id], slot = slot, open = open, reduced = reduced, source = source,
        onClick = { latest.value.let { (h, list) -> playCard(h, list, slot) } },
      )
    }
    Box(Modifier.size(Grid.u192).zIndex(20f).hoverable(source)) { front() }
  }
}

@Composable
private fun BackCard(track: Track, art: ImageBitmap?, slot: Int, open: Boolean, reduced: Boolean, source: MutableInteractionSource, onClick: () -> Unit) {
  val cs = MaterialTheme.colorScheme
  val shape = Grid.r24
  val move: AnimationSpec<Float> = if (reduced) snap() else spring(dampingRatio = 0.72f, stiffness = StackTuning.PoseK)
  val fade: AnimationSpec<Float> = if (reduced) snap() else tween(CompactMath.FadeMs, easing = FastOutSlowInEasing)
  // A card that has just joined the stack fades in; the one that is the playing track hides behind the front card.
  var arrived by remember { mutableStateOf(reduced) }
  LaunchedEffect(Unit) { arrived = true }
  val scale by animateFloatAsState(ArtStackMath.scale(slot), move, label = "artStackScale")
  val dx by animateFloatAsState(ArtStackMath.dx(slot, open), move, label = "artStackX")
  val dy by animateFloatAsState(ArtStackMath.dy(slot), move, label = "artStackY")
  val rot by animateFloatAsState(ArtStackMath.rotation(slot, open), move, label = "artStackR")
  val alpha by animateFloatAsState(if (slot == 0 || !arrived) 0f else 1f, fade, label = "artStackA")
  val live = slot > 0
  Box(
    Modifier.size(Grid.u192).zIndex(10f - slot)
      .graphicsLayer {
        translationX = dx * density; translationY = dy * density
        scaleX = scale; scaleY = scale; rotationZ = rot
        this.alpha = alpha
      }
      .then(
        if (live) Modifier.testTag("art_back_$slot").semantics { contentDescription = track.title; role = Role.Button }
        else Modifier.clearAndSetSemantics { }
      )
      .clip(shape)
      .background(cs.surfaceContainer)
      .border(Grid.hairline, cs.outlineVariant, shape)
      .then(if (live) Modifier.hoverable(source).pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick) else Modifier),
  ) {
    CardCover(track, art, Modifier.fillMaxSize(), shape)
    // Veiled like the cards behind on the Player page: the further back, the closer to the page.
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = ArtStackMath.veil(scale) }.background(cs.background))
  }
}
