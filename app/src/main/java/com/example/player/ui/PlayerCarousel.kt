package com.example.ui.screens.player

import android.content.Context
import com.example.ui.theme.Grid
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.R
import com.example.data.model.Track
import com.example.data.playback.CoverNeighbors
import com.example.ui.theme.CoverCarousel
import com.example.ui.theme.Motion
import com.example.ui.theme.PlayerOrigin
import com.example.ui.theme.PlayerTravel
import com.example.ui.theme.Shapes
import com.example.ui.theme.Size
import com.example.ui.theme.TalBlack
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * The cover carousel. The current cover sits in the middle, as large as the space allows; the previous
 * track's cover rests behind it on the left and the next track's on the right, smaller, darker and
 * turned slightly away (see [CoverCarousel]). Paused, the middle cover eases back to 86% and its shadow
 * tightens; playing, it returns to full size, on a critically damped spring. Swiping follows the finger
 * 1:1 and the cover on the other side grows into the middle; a tap on a side cover goes to that track.
 * Only the middle cover travels to and from the mini player. The side covers wait behind it until the
 * player has opened and then slide out to their places ([spread]); when the player starts to close
 * (or is pulled down) they slide back behind it and are gone before the middle cover reaches the mini
 * player.
 */
@Composable
internal fun Artwork(
  track: Track?,
  neighbors: CoverNeighbors.Result,
  isPlaying: Boolean,
  scrubbing: Boolean,
  reduced: Boolean,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  closingByHand: () -> Boolean,
  /** Width over which the carousel fades out at its left and right edges. */
  edgeFade: Dp,
  modifier: Modifier = Modifier
) {
  // [isPlaying] here is "playing or a track on its way" (PlaybackController.wantsToPlay), so the
  // moment between two tracks does not read as a pause: the cover stays full size through the change.
  val playScale = animateFloatAsState(
    targetValue = PlayerTravel.coverScale(isPlaying, track != null, scrubbing),
    // Jelly: pausing and playing swing a little past the size and settle back.
    animationSpec = if (reduced) snap() else spring(dampingRatio = 0.8f, stiffness = 260f),
    label = "artScale"
  )
  // A track change gives the middle cover a light jelly: a small squash that settles at once. 1 at the
  // change, back to 0 with a springy settle; not on the first track shown, and not with reduced motion.
  val changePop = remember { Animatable(0f) }
  var firstTrack by remember { mutableStateOf(true) }
  LaunchedEffect(trackIdForPop(track)) {
    if (firstTrack) { firstTrack = false; return@LaunchedEffect }
    if (reduced) return@LaunchedEffect
    changePop.snapTo(1f)
    changePop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 420f, visibilityThreshold = 0.001f))
  }
  // Where the middle cover rests in the window, so it can travel from the mini player (or the song
  // that was tapped) to here as the player opens, and back as it closes.
  var resting by remember { mutableStateOf<Rect?>(null) }
  val haptics = LocalHapticFeedback.current
  val scope = rememberCoroutineScope()
  val currentNext by rememberUpdatedState(onNext)
  val currentPrevious by rememberUpdatedState(onPrevious)
  val currentReduced by rememberUpdatedState(reduced)

  // The neighbours are computed with the current track; until they arrive for a new one, the
  // carousel shows the track alone rather than the old track's neighbours.
  val shown = if (neighbors.current != null) neighbors else CoverNeighbors.Result(current = track)
  val canPrevious by rememberUpdatedState(shown.canGoPrevious)
  val canNext by rememberUpdatedState(shown.canGoNext)
  val carousel = remember { CarouselState() }
  val cards = remember(shown) { carousel.arrange(shown) }
  val center = carousel.center

  // ---- the side covers coming out from behind the middle one ----
  // One number for all of them (CoverCarousel.layoutAt): 0 behind the middle cover and unseen, 1 in
  // their places. It goes to 1 once the opening has landed (or at once when the player opens without
  // travelling) and to 0 as soon as the player closes or is pulled down; changing tracks leaves it alone.
  val spread = remember { Animatable(0f) }
  val currentClosingByHand by rememberUpdatedState(closingByHand)
  LaunchedEffect(Unit) {
    snapshotFlow {
      val p = PlayerOrigin.progress
      when {
        currentClosingByHand() -> false
        p == null -> true
        // Read on each frame of the closing spring, so it is seen on its first frame.
        PlayerOrigin.closing -> false
        else -> p.value >= SidesOutAt
      }
    }.distinctUntilChanged().collectLatest { out ->
      when {
        // Reduced motion: no movement, a plain fade (layoutAt keeps the slots and fades them).
        currentReduced -> spread.animateTo(if (out) 1f else 0f, tween(if (out) 200 else 100))
        // Out: response 0.35 s (stiffness (2π / 0.35)² ≈ 322) with a jelly spring: the covers swing past
        // their places by about 13% and settle back (CoverCarousel.MaxSpread allows it).
        out -> spread.animateTo(
          1f,
          spring(dampingRatio = Motion.JellyDamping, stiffness = Motion.JellyStiffness, visibilityThreshold = 0.001f)
        )
        // Back (pulled down): response 0.15 s ((2π / 0.15)² ≈ 1755), behind the middle cover within
        // about 150 ms. Closing itself hides them at once (spreadNow).
        else -> spread.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1755f, visibilityThreshold = 0.001f))
      }
    }
  }

  /**
   * Moves the camera to the card at [target] on a spring that starts at the finger's [velocity]
   * (steps per second). Response 0.3 s (stiffness (2π / 0.3)² ≈ 439), no overshoot; a flick may
   * bounce slightly (damping 0.8). Once it is all but there (CommitDistance), the track changes (one haptic was given
   * at the release); if the track does not change, it goes back to the centre.
   */
  fun settle(target: Int, velocity: Float, flick: Boolean) {
    carousel.job?.cancel()
    carousel.target = target
    carousel.job = scope.launch {
      var committed = false
      fun commitIfDue(at: Float) {
        if (committed || target == carousel.center || abs(at - target) > CommitDistance) return
        committed = true
        carousel.pending = target
        if (target > carousel.center) currentNext() else currentPrevious()
      }
      if (currentReduced) {
        carousel.camera.floatValue = target.toFloat()
        commitIfDue(target.toFloat())
      } else {
        val spec = spring<Float>(
          dampingRatio = if (flick) Motion.FlickDamping else Motion.SwipeDamping,
          stiffness = CarouselStiffness,
          visibilityThreshold = 0.001f
        )
        animate(carousel.camera.floatValue, target.toFloat(), velocity, spec) { v, vel ->
          carousel.camera.floatValue = v
          carousel.velocity.floatValue = vel
          commitIfDue(v)
        }
        carousel.velocity.floatValue = 0f
      }
      if (committed) {
        delay(700)
        if (carousel.center != target) {
          carousel.target = carousel.center
          if (currentReduced) carousel.camera.floatValue = carousel.center.toFloat()
          else animate(carousel.camera.floatValue, carousel.center.toFloat(), 0f, Motion.criticalSpatial(CarouselStiffness)) { v, vel ->
            carousel.camera.floatValue = v
            carousel.velocity.floatValue = vel
          }
        }
      }
    }
  }

  fun go(step: Int) {
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    settle(carousel.center + step, 0f, flick = false)
  }

  // The track changed by other means (the buttons, the end of a track, the queue): slide to it.
  LaunchedEffect(center) {
    if (!carousel.dragging && carousel.target != center) settle(center, 0f, flick = false)
  }

  val carouselContext = LocalContext.current
  BoxWithConstraints(modifier = modifier.bleedHorizontally(Grid.margin).carouselEdgeFade(edgeFade), contentAlignment = Alignment.Center) {
    // The middle cover keeps the size the single cover had, inside the page's margins
    // (CoverCarousel.centreSide); the side covers show in the margins and run off the screen.
    val density = LocalDensity.current
    val side: Dp = with(density) { CoverCarousel.centreSide(maxWidth.toPx(), maxHeight.toPx(), Grid.margin.toPx()).toDp() }
    val sidePx by rememberUpdatedState(with(density) { side.toPx() })
    val coverPx = with(density) { side.roundToPx() }
    SideEffect { CarouselCovers.sizePx = coverPx }
    val stepPx by rememberUpdatedState(CoverCarousel.stepPx(sidePx))
    val minTouch = with(LocalDensity.current) { Grid.u48.toPx() }
    Box(
      Modifier
        .size(side)
        .onGloballyPositioned { resting = it.boundsInWindow() }
    )
    Layout(
      content = {
        for (card in cards) {
          key(card.index) {
            CarouselCover(
              track = card.track,
              index = card.index,
              isCenter = card.index == center,
              carousel = carousel,
              side = side,
              coverPx = coverPx,
              playScale = playScale,
              changePop = changePop,
              spread = spread,
              resting = { resting },
              reduced = reduced
            )
          }
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(side)
        .semantics {
          contentDescription = track?.let { carouselContext.getString(R.string.player_screen_cover_of, it.title, it.artist) }
            ?: carouselContext.getString(R.string.player_screen_no_track)
          customActions = listOfNotNull(
            shown.previous?.let { p -> CustomAccessibilityAction(carouselContext.getString(R.string.player_screen_previous_named, p.title)) { go(-1); true } },
            shown.next?.let { n -> CustomAccessibilityAction(carouselContext.getString(R.string.player_screen_next_named, n.title)) { go(1); true } }
              ?: if (shown.nextUnknown) CustomAccessibilityAction(carouselContext.getString(R.string.player_screen_next)) { go(1); true } else null
          )
        }
        .pointerInput(Unit) {
          val tracker = VelocityTracker()
          // Where the finger has put the camera, before the rubber band; absolute, so a change of
          // centre in the middle of a drag (a swipe settling just before) does not move anything.
          var raw = 0f
          fun release(velocity: Float) {
            carousel.dragging = false
            val offset = carousel.camera.floatValue - carousel.center
            val step = CoverCarousel.targetStep(offset, velocity, canPrevious, canNext)
            if (step != 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            settle(carousel.center + step, velocity, flick = step != 0 && abs(velocity) > CoverCarousel.FlickVelocity)
          }
          detectHorizontalDragGestures(
            onDragStart = {
              // Caught in the middle of a settle: it goes on from where it is.
              carousel.job?.cancel()
              carousel.dragging = true
              tracker.resetTracking()
              raw = carousel.camera.floatValue
            },
            onDragEnd = { release(-tracker.calculateVelocity().x / stepPx) },
            onDragCancel = { release(0f) }
          ) { change, dx ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            raw -= dx / stepPx
            val c = carousel.center
            val low = if (canPrevious) -1f else 0f
            val high = if (canNext) 1f else 0f
            carousel.camera.floatValue = c + CoverCarousel.band(raw - c, low, high)
            carousel.velocity.floatValue = -tracker.calculateVelocity().x / stepPx
          }
        }
        .pointerInput(Unit) {
          detectTapGestures { pos ->
            // The side covers show only a strip; each side's target is that strip, at least 48 dp wide.
            val half = minOf(sidePx * playScale.value / 2f, size.width / 2f - minTouch)
            val mid = size.width / 2f
            // Not while the side covers are still behind the middle one.
            if (spread.value < 0.5f) return@detectTapGestures
            when {
              pos.x < mid - half && canPrevious -> go(-1)
              pos.x > mid + half && canNext -> go(1)
            }
          }
        }
        .testTag("music_artwork_carousel")
    ) { measurables, constraints ->
      val px = side.roundToPx()
      val placeables = measurables.map { it.measure(Constraints.fixed(px, px)) }
      layout(constraints.maxWidth, px) {
        // The card nearest the middle is drawn on top. Read here (placement), not in composition.
        val cam = carousel.camera.floatValue
        placeables.forEachIndexed { i, p ->
          p.place((constraints.maxWidth - px) / 2, 0, zIndex = -abs(cards[i].index - cam))
        }
      }
    }
  }
}

/**
 * How close (in steps) the settling cover must be to the middle for the track to change: the cover is
 * visibly there, without waiting for the spring's long tail.
 */
internal const val CommitDistance = 0.05f

/** Response 0.3 s for the carousel's springs: (2π / 0.3)². */
internal const val CarouselStiffness = 439f

/**
 * Opening progress at which the middle cover counts as landed and the side covers come out. The
 * opening spring (damping 0.88) passes it in its last few frames.
 */
internal const val SidesOutAt = 0.99f

/**
 * The covers of the carousel are loaded at one size, the middle cover's, in every slot (the side ones
 * are the same layout scaled down), and [request] is the only way they are asked for: shown or loaded
 * ahead, the same address at the same size makes the same memory-cache key, so a cover loaded ahead is
 * on screen in the first frame, without a crossfade (Coil fades only what was not in memory).
 */
object CarouselCovers {
  /** The middle cover's side in pixels, written by the player once it is laid out; 0 before. */
  @Volatile var sizePx: Int = 0

  fun request(context: Context, url: String, px: Int): ImageRequest =
    ImageRequest.Builder(context)
      .data(url)
      .size(px, px)
      // What ContentScale.Crop asks for; set here so a request made ahead decodes the same bitmap.
      .scale(Scale.FILL)
      // A cover smaller than this is not scaled up in memory.
      .precision(Precision.INEXACT)
      .memoryCacheKey("$url@$px")
      // Shown at once from memory while the request starts, so a cover loaded ahead never flashes the note.
      .placeholderMemoryCacheKey("$url@$px")
      .crossfade(220)
      .build()

  /** The middle cover's side before the player has been laid out: the width rule on the screen width. */
  fun estimatePx(screenWidthPx: Float, marginPx: Float): Int =
    CoverCarousel.centreSide(screenWidthPx, Float.MAX_VALUE, marginPx).roundToInt()
}

/**
 * Loads ahead the covers of up to [CoverNeighbors.Depth] tracks on each side of the current one (and
 * its own) as soon as they change, whether the player is open or not, so a cover arriving in the
 * middle or at a side is already in memory. Tracks without a cover are skipped (they show the note).
 */
@Composable
fun PrefetchCarouselCovers(neighbors: kotlinx.coroutines.flow.StateFlow<CoverNeighbors.Result>) {
  val n by neighbors.collectAsStateWithLifecycle()
  val context = LocalContext.current
  // The real pixel width (screenWidthDp is rounded down to whole dp, which would ask for a cover one
  // pixel off the one the player lays out, and miss the cache).
  val screenPx = LocalConfiguration.current.let { context.resources.displayMetrics.widthPixels.toFloat() }
  val marginPx = with(LocalDensity.current) { Grid.margin.toPx() }
  LaunchedEffect(n) {
    val px = CarouselCovers.sizePx.takeIf { it > 0 } ?: CarouselCovers.estimatePx(screenPx, marginPx)
    if (px <= 0) return@LaunchedEffect
    val loader = context.imageLoader
    // Nearest first, so the covers about to be seen are asked for first.
    val tracks = listOfNotNull(n.current) + n.after.take(1) + n.before.take(1) +
      n.after.drop(1).take(CoverNeighbors.Depth - 1) + n.before.drop(1).take(CoverNeighbors.Depth - 1)
    tracks.mapNotNull { it.artworkUrl }.distinct().forEach { url ->
      loader.enqueue(CarouselCovers.request(context, url, px))
    }
  }
}

/** Covers drawn on each side of the middle one; the third neighbour is only loaded ahead. */
internal const val Drawn = 2

/** A cover of the carousel: its place on the running index, and the track it shows. */
internal class CarouselCard(val index: Int, val track: Track?)

/**
 * The carousel's state while the player is open. [camera] is where the view is along the cards'
 * running index (see [CoverCarousel.rebaseCenter]); only placement and drawing read it, so dragging
 * recomposes nothing.
 */
internal class CarouselState {
  val camera = mutableFloatStateOf(0f)
  /** How fast the camera moves, in steps per second: the covers stretch with it (read in graphicsLayer only). */
  val velocity = mutableFloatStateOf(0f)
  /** The index of the current track's card. */
  var center = 0
  /** The index a settle is heading for. */
  var target = 0
  /** The index whose track was just asked for, until the track changes to it. */
  var pending: Int? = null
  var dragging = false
  var job: Job? = null
  private var cards: List<CarouselCard> = emptyList()
  private var arrangements = 0

  /** False for the covers composed with the player, which come out with the spread instead. */
  val fadeNewCards: Boolean get() = arrangements > 1

  /**
   * Places the current track and up to [Drawn] neighbours on each side (nearest first) around the card
   * that already shows it.
   */
  fun arrange(n: CoverNeighbors.Result): List<CarouselCard> {
    val c = if (arrangements == 0) 0 else CoverCarousel.rebaseCenter(
      cards.associate { it.index to (it.track?.id ?: "") }, center, pending, n.current?.id, camera.floatValue
    )
    // A cover that left past the second slot stays until it has faded out there (CoverCarousel.layoutAt).
    val leaving = cards.filter { abs(it.index - c) == Drawn + 1 }
    cards = leaving +
      n.before.take(Drawn).mapIndexed { i, t -> CarouselCard(c - 1 - i, t) }.reversed() +
      CarouselCard(c, n.current) +
      n.after.take(Drawn).mapIndexed { i, t -> CarouselCard(c + 1 + i, t) }
    if (pending == c) pending = null
    center = c
    arrangements++
    return cards
  }
}

/**
 * One cover. Every frame-to-frame change (place, size, turn, fade, scrim, shadow) is a layer property
 * read in the draw phase. The middle one also carries the travel to and from the mini player.
 */
@Composable
internal fun CarouselCover(
  track: Track?,
  index: Int,
  isCenter: Boolean,
  carousel: CarouselState,
  side: Dp,
  coverPx: Int,
  playScale: State<Float>,
  changePop: Animatable<Float, *>,
  spread: Animatable<Float, *>,
  resting: () -> Rect?,
  reduced: Boolean
) {
  /**
   * How far the side covers are out: the spread, and nothing from the first frame of closing, so the
   * middle cover never travels to the mini player with covers beside it (a pull down, which comes
   * before, slides them back with the spread itself).
   */
  fun spreadNow(): Float = if (PlayerOrigin.closing && PlayerOrigin.progress != null) 0f else spread.value
  // A cover that arrives at a side after the player opened fades in rather than popping in.
  val appear = remember { Animatable(if (carousel.fadeNewCards) 0f else 1f) }
  LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, tween(180)) }
  Box(
    modifier = Modifier
      .size(side)
      .graphicsLayer {
        val t = if (isCenter) coverTravel(resting(), reduced) else null
        if (t != null) {
          transformOrigin = TransformOrigin(0f, 0f)
          scaleX = t.scale
          scaleY = t.scale
          translationX = t.dx
          translationY = t.dy
        } else if (isCenter && !reduced && resting() == null && PlayerOrigin.progress != null) {
          // Not measured yet: stay hidden for the frame it takes, rather than flash at the resting place.
          alpha = 0f
        }
      }
      .graphicsLayer {
        val o = index - carousel.camera.floatValue
        val slot = CoverCarousel.layoutAt(o, reduced, spreadNow())
        val near = abs(o).coerceAtMost(1f)
        val t = if (isCenter) coverTravel(resting(), reduced) else null
        // While travelling the cover is the mini player's size, so it does not step back as well.
        val p = t?.progress ?: 1f
        val play = 1f + (playScale.value - 1f) * p
        // Pausing steps back only the middle cover; the sides stay where they are.
        val s = slot.scale * (play + (1f - play) * near)
        // Jelly: the faster the carousel moves the more the covers stretch along it and flatten, and
        // they come back as it settles. Not with reduced motion.
        val stretch = if (reduced) 0f else (abs(carousel.velocity.floatValue) / 5f).coerceIn(0f, 1f) * 0.04f * (1f - near)
        // Light jelly on a track change, the middle cover only: a little wider and flatter, settling back.
        val pop = if (isCenter) changePop.value * 0.035f * p else 0f
        scaleX = s * (1f + stretch + pop)
        scaleY = s * (1f - stretch * 0.6f - pop * 0.7f)
        translationX = slot.shift * size.width
        rotationY = slot.rotationY
        cameraDistance = 12f * density
        alpha = slot.alpha * appear.value
        // The shadow is softer and wider at full size, tighter when it steps back, absent while it is
        // still the small cover of the mini player, and only under the middle cover.
        shadowElevation = if (near >= 1f || track?.artworkUrl == null) 0f else
          (Grid.u6 + Grid.u12 * ((s - PlayerTravel.PausedScale) / (1f - PlayerTravel.PausedScale)).coerceIn(0f, 1f)).toPx() * p * (1f - near)
        shape = if (t != null) {
          RoundedCornerShape(PlayerTravel.cornerAt(Grid.u12.toPx(), Grid.u24.toPx(), t.progress, t.scale * s))
        } else {
          Grid.r24
        }
        clip = true
      }
      .background(Color.White.copy(alpha = 0.08f)),
    contentAlignment = Alignment.Center
  ) {
    // Decorative (contentDescription = null on purpose): placeholder; the cover as a whole is described by the title row
    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = OnPage.copy(alpha = 0.5f), modifier = Modifier.size(side * 0.25f))
    // Before the player is laid out the cover has no size (0 px), and Coil refuses a request of that size.
    track?.artworkUrl?.takeIf { coverPx > 0 }?.let { url ->
      val context = LocalContext.current
      // The same request in every slot and in the loading ahead (CarouselCovers), so a cover moving
      // to the middle keeps its image and one loaded ahead is shown from memory at once.
      val request = remember(url, coverPx) { CarouselCovers.request(context, url, coverPx) }
      AsyncImage(
        model = request,
        // Decorative (contentDescription = null on purpose): the cover; the track title and artist are read from the title row
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
      )
    }
    // Darker at the sides: the identity's black, by opacity.
    Box(
      Modifier
        .matchParentSize()
        .graphicsLayer { alpha = CoverCarousel.layoutAt(index - carousel.camera.floatValue, reduced, spreadNow()).scrim }
        .background(AbsoluteBlack)
    )
    // Glass finish at the sides: a faint lift at the top and a hairline rim.
    Box(
      Modifier
        .matchParentSize()
        .graphicsLayer { alpha = abs(index - carousel.camera.floatValue).coerceAtMost(1f) }
        .drawBehind {
          drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), endY = size.height * 0.5f))
          val w = Grid.hairline.toPx()
          val r = Grid.u24.toPx()
          drawRoundRect(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(w / 2f, w / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
            cornerRadius = CornerRadius(r),
            style = Stroke(width = w)
          )
        }
    )
  }
}

internal val AbsoluteBlack = TalBlack

/**
 * Fades the content to nothing over [width] at its left and right edges, so the side covers do not end
 * in a hard line at the screen's (or the half's) edge. The fade needs an offscreen layer, whose bounds
 * would cut the cover travelling to and from the mini player, so it is on only once the player has
 * opened and not while it closes.
 */
internal fun Modifier.carouselEdgeFade(width: Dp): Modifier {
  fun settled(): Boolean = PlayerOrigin.progress?.let { it.value >= 1f && !PlayerOrigin.closing } ?: true
  // The offscreen layer cuts at its bounds, and the middle cover's shadow reaches well past the carousel
  // above and below: the layer is made taller than the carousel (room on both sides), while the
  // carousel itself keeps its size in the page's layout.
  val room = Grid.u96
  return this
    .layout { measurable, c ->
      val extra = room.roundToPx() * 2
      val p = measurable.measure(
        c.copy(
          minHeight = if (c.minHeight > 0) c.minHeight + extra else 0,
          maxHeight = if (c.hasBoundedHeight) c.maxHeight + extra else c.maxHeight
        )
      )
      layout(p.width, (p.height - extra).coerceAtLeast(0)) { p.place(0, -extra / 2) }
    }
    .graphicsLayer {
      compositingStrategy = if (settled()) androidx.compose.ui.graphics.CompositingStrategy.Offscreen
      else androidx.compose.ui.graphics.CompositingStrategy.Auto
    }
    .layout { measurable, c ->
      val extra = room.roundToPx() * 2
      val p = measurable.measure(
        c.copy(
          minHeight = (c.minHeight - extra).coerceAtLeast(0),
          maxHeight = if (c.hasBoundedHeight) (c.maxHeight - extra).coerceAtLeast(0) else c.maxHeight
        )
      )
      layout(p.width, p.height + extra) { p.place(0, extra / 2) }
    }
    .drawWithContent {
      drawContent()
      if (settled()) {
        val f = (width.toPx() / size.width).coerceIn(0f, 0.5f)
        drawRect(
          brush = Brush.horizontalGradient(0f to Color.Transparent, f to Color.Black, (1f - f) to Color.Black, 1f to Color.Transparent),
          blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
        )
      }
    }
}
/**
 * Lets the content reach [x] past both sides of the padding around it (the carousel's side covers
 * reach the screen's edges), while it still takes only the padded width in the layout.
 */
internal fun Modifier.bleedHorizontally(x: Dp): Modifier = layout { measurable, constraints ->
  val extra = x.roundToPx() * 2
  val placeable = measurable.measure(
    constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
  )
  layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** The most the opening progress counts past 1 for the travelling cover (the spring overshoots about 8%). */
internal const val OvershootCap = 1.2f

/** What a travelling cover needs at this frame: its progress and the transform that puts it on its path. */
internal class CoverTravel(val progress: Float, val scale: Float, val dx: Float, val dy: Float)

/**
 * The cover's place on its way between the small cover it comes from (the mini player's, or the tapped
 * row's) and [resting], by the same progress that grows the player; towards the mini player when
 * closing. Null when it should simply sit at rest: fully open, reduced motion, or nothing to travel from.
 */
internal fun coverTravel(resting: Rect?, reduced: Boolean): CoverTravel? {
  val progress = PlayerOrigin.progress ?: return null
  if (reduced || resting == null || resting.width <= 0f) return null
  // Opening may overshoot (the jelly spring): the cover then goes a little past its place and comes
  // back. Closing never exceeds 1.
  val raw = progress.value
  val p = raw.coerceIn(0f, if (PlayerOrigin.closing) 1f else OvershootCap)
  if (p == 1f) return null
  val from = (if (PlayerOrigin.closing) PlayerOrigin.miniCover else PlayerOrigin.openingCover) ?: return null
  val sx = from.width + (resting.width - from.width) * p
  val left = from.left + (resting.left - from.left) * p
  val top = from.top + (resting.top - from.top) * p
  return CoverTravel(p.coerceAtMost(1f), sx / resting.width, left - resting.left, top - resting.top)
}
