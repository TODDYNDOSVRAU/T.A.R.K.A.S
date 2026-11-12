package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.TarkasNavTab

/**
 * Motion used across the app. Everything here moves only alpha and scale (drawn by the GPU
 * without re-measuring or re-laying out), so it stays smooth at 120 Hz and costs little battery.
 */
object Motion {
  /** Fast start, long soft landing: feels like the surface settles into place. */
  val Settle = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

  // Spring tokens. Spatial springs move and resize surfaces; they are damped enough (0.9 and up) that
  // any overshoot stays below a pixel, so motion reads as precise rather than bouncy. Effect springs
  // fade and recolour and are critically damped (a bouncing alpha looks like flicker). Presses and
  // anything that follows the finger use the critically damped spatial spring.
  const val FastSpatialDamping = 0.9f
  const val FastSpatialStiffness = 800f
  const val DefaultSpatialDamping = 0.9f
  const val DefaultSpatialStiffness = 380f
  const val SlowSpatialDamping = 1f
  const val SlowSpatialStiffness = 200f
  const val DefaultEffectsStiffness = 1600f
  /**
   * The full player growing out of what was tapped: a jelly spring (damping 0.62, about 8% overshoot).
   * The sheet itself is clamped to the screen, so what swings past is the travelling cover, which
   * grows a little beyond its place and settles back. Requested explicitly for the player; the one
   * spring in the app that bounces without a flick, and only when motion is not reduced.
   */
  const val ExpandDamping = 0.85f
  /** Settles in about 0.55 s: a large repositioning (the whole screen), slow enough to follow with the eye. */
  const val ExpandStiffness = 120f
  /** The jelly of the cover carousel: side covers coming out, a swipe landing, the cover stepping back on pause. */
  const val JellyDamping = 0.8f
  const val JellyStiffness = 200f
  /** Landing of a swipe: gentle (damping 0.7), and looser after a flick (0.5). */
  const val SwipeDamping = 0.85f
  const val FlickDamping = 0.7f
  /**
   * The player shrinking back into the mini player: critically damped (no overshoot at all) and
   * stiffer than the opening, so the shrink is below 2% left at ~0.4 s instead of ~0.6 s. It is still
   * a large repositioning (the whole screen to a capsule), which is why it may pass 300 ms; the mini
   * player fades in on the same progress (PlayerTravel.miniIn), so both are one movement.
   */
  const val CollapseDamping = 1f
  /**
   * The player opening and closing run on time, not a spring: a spring is fastest in its first frames,
   * which read as a jolt for a movement this large. Slow in, slow out: starts gently, glides through the
   * middle and lands softly, the same curve both ways so closing is the opening played back.
   */
  const val ExpandMillis = 620
  const val CollapseMillis = 520
  val PlayerEasing = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0f, 0.15f, 1f)
  const val CollapseStiffness = 130f

  /** Taps, toggles, small controls moving into place. */
  fun <T> fastSpatial() = spring<T>(dampingRatio = FastSpatialDamping, stiffness = FastSpatialStiffness)
  /** Most movement: cards settling, a surface moving into place. */
  fun <T> defaultSpatial() = spring<T>(dampingRatio = DefaultSpatialDamping, stiffness = DefaultSpatialStiffness)
  /** Large, unhurried movement. */
  fun <T> slowSpatial() = spring<T>(dampingRatio = SlowSpatialDamping, stiffness = SlowSpatialStiffness)
  /** Fades and colour changes: critically damped, never overshoots. */
  fun <T> defaultEffects() = spring<T>(dampingRatio = 1f, stiffness = DefaultEffectsStiffness)
  /**
   * Critically damped movement: what the finger let go of returning (a pulled page, a swiped cover)
   * and press feedback. Lands exactly, with no wobble.
   */
  fun <T> criticalSpatial(stiffness: Float = DefaultSpatialStiffness) = spring<T>(dampingRatio = 1f, stiffness = stiffness)

  /** How many items of a list enter one after another, and the gap between them. */
  const val StaggerItems = 8
  const val StaggerStepMs = 30L
}
/**
 * What the system and the user ask of motion and blur. The app follows the system: with animations
 * removed (animation scale zero, Samsung's "Remove animations") there is no boot sequence and screens
 * switch without movement; with blur and transparency reduced, the floating bars are solid.
 * "Reduzir animações" in Ajustes turns motion off in the app alone. Read again on every resume,
 * so a change in the system settings applies when the user comes back.
 */
object AppMotion {
  /** "Reduzir animações" in Ajustes. */
  var userReduced by mutableStateOf(false)
  /** The system animation scale is zero. */
  var systemReduced by mutableStateOf(false)
  /** The system asks for no blur (Samsung's "Reduce transparency and blur", or window blurs off). */
  var systemBlurOff by mutableStateOf(false)

  val reduced: Boolean get() = userReduced || systemReduced

  /** How the rows of the lists arrive while scrolling ("Animação das listas" in Ajustes). */
  var listAnimation by mutableStateOf(ListAnimation.SLIDE)

  fun readSystem(context: Context) {
    val resolver = context.contentResolver
    systemReduced = try {
      Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
      false
    }
    systemBlurOff = settingIsOn { Settings.System.getInt(resolver, "accessibility_reduce_transparency", 0) } ||
      settingIsOn { Settings.Global.getInt(resolver, "disable_window_blurs", 0) }
  }

  private inline fun settingIsOn(read: () -> Int): Boolean = try {
    read() == 1
  } catch (_: Exception) {
    false
  }
}

/** True when motion should be skipped: the system removed animations, or the user asked for less. */
@Composable
fun rememberReducedMotion(): Boolean = AppMotion.reduced

/**
 * Remotion's spring(): where a spring pulled from 0 to 1 is after [seconds]. The defaults are
 * Remotion's (damping 10, mass 1, stiffness 100), which overshoot; raise damping for calmer motion.
 * A pure function of time, so a single timeline value can drive many staggered springs.
 */
fun springAt(seconds: Float, damping: Float = 10f, mass: Float = 1f, stiffness: Float = 100f): Float {
  if (seconds <= 0f) return 0f
  val w0 = sqrt(stiffness / mass)
  val zeta = damping / (2f * sqrt(stiffness * mass))
  return if (zeta < 1f) {
    val wd = w0 * sqrt(1f - zeta * zeta)
    1f - exp(-zeta * w0 * seconds) * (cos(wd * seconds) + (zeta * w0 / wd) * sin(wd * seconds))
  } else {
    1f - exp(-w0 * seconds) * (1f + w0 * seconds)
  }
}

/**
 * Transition between screens. Tabs fade through: the old page is gone in 90 ms, then the new one fades
 * in over 200 ms. No scaling, so the page is not re-rasterised every frame. No sideways movement, as
 * Material advises for bottom tabs (a slide suggests the pages can be swiped), and quick, because
 * switching tabs is done all the time. Opening the full player grows it out of what was tapped (see
 * expandFromOrigin).
 */
fun screenTransition(from: Any, to: Any, reduced: Boolean = false): ContentTransform {
  if (reduced) return EnterTransition.None togetherWith ExitTransition.None

  // The full player grows out of what was tapped and shrinks back into the mini player
  // (expandFromOrigin draws that shape). The page underneath stays in place meanwhile: it fades
  // only once the player covers it, and is back before the player lands when closing.
  if (to == TarkasNavTab.MUSIC) {
    return (EnterTransition.None togetherWith fadeOut(tween(200, delayMillis = 260))).apply {
      targetContentZIndex = 1f
    }
  }
  if (from == TarkasNavTab.MUSIC) {
    return (fadeIn(tween(160)) togetherWith ExitTransition.None).apply {
      // The closing player stays on top of the page it returns to.
      targetContentZIndex = -1f
    }
  }

  // Alpha only: scaling a whole page re-rasterises it every frame, which dropped frames on the S23.
  val enter = fadeIn(tween(200, delayMillis = 90, easing = LinearOutSlowInEasing))
  val exit = fadeOut(tween(150, easing = FastOutLinearInEasing))
  return (enter togetherWith exit).apply {
    // The new screen draws over the old one, so it never slips behind the page that is leaving.
    targetContentZIndex = 1f
  }
}

/**
 * A page opened inside a tab (an open playlist, the equalizer's advanced page, the fone list): it
 * pushes in from the trailing side on a critically damped spring and lands exactly; going back slides
 * it away the other way. The page underneath moves a little and fades, so the two never cross.
 */
fun subPageTransition(forward: Boolean, reduced: Boolean): ContentTransform {
  if (reduced) return EnterTransition.None togetherWith ExitTransition.None
  val slide = Motion.criticalSpatial<IntOffset>(stiffness = 500f)
  val dir = if (forward) 1 else -1
  return (slideInHorizontally(slide) { w -> dir * w / 3 } + fadeIn(tween(200, delayMillis = 30))) togetherWith
    (slideOutHorizontally(slide) { w -> -dir * w / 8 } + fadeOut(tween(150)))
}

/**
 * Switching between sections of one page (chips like Playlists / Favoritos / Fila): a short slide in
 * the direction of the chip that was picked, with a quick crossfade. Critically damped.
 */
fun sectionTransition(fromIndex: Int, toIndex: Int, reduced: Boolean): ContentTransform {
  if (reduced) return EnterTransition.None togetherWith ExitTransition.None
  val dir = if (toIndex >= fromIndex) 1 else -1
  val slide = Motion.criticalSpatial<IntOffset>(stiffness = 600f)
  return (slideInHorizontally(slide) { w -> dir * w / 10 } + fadeIn(tween(180, delayMillis = 40))) togetherWith
    (slideOutHorizontally(slide) { w -> -dir * w / 14 } + fadeOut(tween(150)))
}

/**
 * The ways a row can arrive as it scrolls into view, like the list animations of the WhatsApp mods
 * (GB, WA Enhancer). Each one moves and fades the row in; none changes its layout.
 */
enum class ListAnimation(val key: String, val label: String) {
  NONE("none", "Nenhuma"),
  SLIDE("slide", "Deslizar"),
  FADE("fade", "Esmaecer"),
  ZOOM("zoom", "Zoom"),
  SIDE("side", "Lateral"),
  FLIP("flip", "Virar"),
  BOUNCE("bounce", "Quicar");

  companion object {
    fun fromKey(key: String?): ListAnimation = entries.firstOrNull { it.key == key } ?: SLIDE
  }
}

/**
 * A row of a scrolling list arrives with [AppMotion.listAnimation] every time it scrolls into view
 * (a lazy list composes it afresh then). The first [Motion.StaggerItems] rows of a list that just
 * opened come one after another; a row scrolled in later starts at once, so it never lags the finger.
 * Nothing moves with reduced motion or with "Nenhuma". The animation runs in graphicsLayer only, so
 * the scroll itself never recomposes.
 */
@Composable
fun Modifier.listEntrance(index: Int): Modifier {
  val style = AppMotion.listAnimation
  if (AppMotion.reduced || style == ListAnimation.NONE) return this
  // Saved with the row, so a row coming back into view is not delayed again; the animation itself
  // (below, not saved) plays on every entry.
  var staggered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
  val progress = remember(style) { androidx.compose.animation.core.Animatable(0f) }
  androidx.compose.runtime.LaunchedEffect(style) {
    if (!staggered && index < Motion.StaggerItems) kotlinx.coroutines.delay(index * Motion.StaggerStepMs)
    staggered = true
    progress.animateTo(
      1f,
      if (style == ListAnimation.BOUNCE) spring(dampingRatio = 0.45f, stiffness = 420f)
      else Motion.criticalSpatial(stiffness = 520f)
    )
  }
  return this.graphicsLayer {
    val p = progress.value
    val rest = 1f - p
    // A bounce overshoots past 1; the fade never does.
    alpha = p.coerceIn(0f, 1f)
    when (style) {
      ListAnimation.SLIDE -> translationY = rest * 32.dp.toPx()
      ListAnimation.FADE -> Unit
      ListAnimation.ZOOM -> {
        scaleX = 0.86f + 0.14f * p
        scaleY = 0.86f + 0.14f * p
      }
      ListAnimation.SIDE -> translationX = -rest * 48.dp.toPx()
      ListAnimation.FLIP -> {
        cameraDistance = 12f * density
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        rotationX = -70f * rest
      }
      ListAnimation.BOUNCE -> translationY = rest * 56.dp.toPx()
      ListAnimation.NONE -> Unit
    }
  }
}

/**
 * Click with a small zoom-out while pressed, like tapping a card in Apple Music. Critically damped:
 * the press is answered at once and released without a wobble. With reduced motion there is no
 * zoom. The scale is read inside graphicsLayer, so the press animation does not recompose anything.
 */
@Composable
fun Modifier.pressClickable(
  pressedScale: Float = 0.97f,
  onClickLabel: String? = null,
  role: Role? = Role.Button,
  onClick: () -> Unit
): Modifier {
  val interaction = remember { MutableInteractionSource() }
  val pressed = interaction.collectIsPressedAsState()
  val reduced = AppMotion.reduced
  val scale = animateFloatAsState(
    targetValue = if (pressed.value && !reduced) pressedScale else 1f,
    animationSpec = Motion.criticalSpatial(stiffness = 900f),
    label = "pressScale"
  )
  return this
    .graphicsLayer {
      scaleX = scale.value
      scaleY = scale.value
    }
    .clickable(
      interactionSource = interaction,
      indication = null,
      onClickLabel = onClickLabel,
      role = role,
      onClick = onClick
    )
}

/**
 * Decides whether decorative effects that run all the time (the moving cover behind the player) may keep
 * running. It is given the time between frames: [strikes] slow frames in a row (each longer than
 * [limitMs]) turn the effect off for good, until [reset]. One slow frame alone, such as the one where a
 * screen opens, is forgiven.
 */
class FrameGuard(private val limitMs: Float = 24f, private val strikes: Int = 3) {
  private var slowInARow = 0

  /** False once the effect has been switched off. */
  var allowed: Boolean = true
    private set

  fun onFrame(deltaMs: Float) {
    if (!allowed) return
    if (deltaMs > limitMs) {
      slowInARow++
      if (slowInARow >= strikes) allowed = false
    } else {
      slowInARow = 0
    }
  }

  fun reset() {
    slowInARow = 0
    allowed = true
  }
}
