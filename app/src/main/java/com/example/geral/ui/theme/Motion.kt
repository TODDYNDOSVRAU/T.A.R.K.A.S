package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
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
   * middle and lands softly, the same curve both ways so closing is the opening played back. Durations
   * sit inside the identity's 150–300 ms (owner's request, 2026-11-13; they were 620 and 520 ms).
   */
  const val ExpandMillis = 300
  const val CollapseMillis = 260
  val PlayerEasing = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0f, 0.15f, 1f)
  const val CollapseStiffness = 130f

  /** Fades and colour changes: critically damped, never overshoots. */
  fun <T> defaultEffects() = spring<T>(dampingRatio = 1f, stiffness = DefaultEffectsStiffness)
  /**
   * Critically damped movement: what the finger let go of returning (a pulled page, a swiped cover)
   * and press feedback. Lands exactly, with no wobble.
   */
  fun <T> criticalSpatial(stiffness: Float = DefaultSpatialStiffness) = spring<T>(dampingRatio = 1f, stiffness = stiffness)

  /**
   * The "gel" spring the owner tuned on the S23 (damping 0.62, stiffness 348): spatial movement that
   * arrives or settles (position, offset, slide, scale, size, rotation) overshoots about 8% and comes
   * to rest. Fades and colours do not use it (an alpha that wobbles flickers), and what follows the
   * finger or is pressed stays critical. Wherever progress feeds a lerp or a clamp, p may reach ~1.08.
   */
  const val GelDamping = 0.62f
  const val GelStiffness = 348f

  /** Movement that arrives or settles: the gel spring. */
  fun <T> gel(visibilityThreshold: T? = null) = spring(GelDamping, GelStiffness, visibilityThreshold)

  /** What leaves the screen or disappears: same stiffness, critically damped, never comes back inside. */
  fun <T> gelExit() = spring<T>(dampingRatio = 1f, stiffness = GelStiffness)

  // Apple Fluid Interfaces (iOS native) spatial spring tokens: crisp, responsive, almost zero rebound.
  const val FluidDamping = 0.85f
  const val FluidStiffness = 480f
  const val FluidFastDamping = 0.86f
  const val FluidFastStiffness = 620f
  const val FluidExitStiffness = 520f

  /** Primary iOS-style spatial spring for screens, sheets, switches and controls. */
  fun <T> fluid(visibilityThreshold: T? = null) = spring(FluidDamping, FluidStiffness, visibilityThreshold)

  /** Snappier iOS-style spatial spring for popovers, chips, floating bars and micro-interactions. */
  fun <T> fluidFast(visibilityThreshold: T? = null) = spring(FluidFastDamping, FluidFastStiffness, visibilityThreshold)

  /** Critically damped exit spring: leaves briskly without overshooting back into view. */
  fun <T> fluidExit() = spring<T>(dampingRatio = 1f, stiffness = FluidExitStiffness)

  /**
   * Apple UIScrollView asymptotic rubber-band resistance when dragging [overshoot] past a limit:
   * `f(x) = (x * d * c) / (d + c * x)`. Preserves sign and smoothly saturates toward [limit].
   */
  fun rubberBand(overshoot: Float, limit: Float, coefficient: Float = 0.55f): Float {
    if (overshoot == 0f || limit <= 0f) return 0f
    val x = abs(overshoot)
    return sign(overshoot) * (x * limit * coefficient) / (limit + coefficient * x)
  }

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
 * A page opened inside a tab (an open playlist, the equalizer's advanced page, the fone list): it
 * pushes in from the trailing side on the gel spring (a small overshoot) and leaves critically damped; going back slides
 * it away the other way. The page underneath moves a little and fades, so the two never cross.
 */
fun subPageTransition(forward: Boolean, reduced: Boolean): ContentTransform {
  if (reduced) return fadeIn(tween(120)) togetherWith fadeOut(tween(100))
  val dir = if (forward) 1 else -1
  return (slideInHorizontally(Motion.fluid()) { w -> dir * w / 3 } + fadeIn(Motion.defaultEffects())) togetherWith
    (slideOutHorizontally(Motion.fluidExit()) { w -> -dir * w / 8 } + fadeOut(Motion.defaultEffects()))
}

/**
 * Switching between sections of one page (chips like Playlists / Favoritos / Fila): a short slide in
 * the direction of the chip that was picked, with a quick crossfade. Fluid in, critical out.
 */
fun sectionTransition(fromIndex: Int, toIndex: Int, reduced: Boolean): ContentTransform {
  if (reduced) return fadeIn(tween(120)) togetherWith fadeOut(tween(100))
  val dir = if (toIndex >= fromIndex) 1 else -1
  return (slideInHorizontally(Motion.fluidFast()) { w -> dir * w / 10 } + fadeIn(Motion.defaultEffects())) togetherWith
    (slideOutHorizontally(Motion.fluidExit()) { w -> -dir * w / 14 } + fadeOut(Motion.defaultEffects()))
}

/**
 * The ways a row can arrive as it scrolls into view, like the list animations of the WhatsApp mods
 * (GB, WA Enhancer). Each one moves and fades the row in; none changes its layout.
 */
enum class ListAnimation(val key: String, @androidx.annotation.StringRes val labelRes: Int) {
  NONE("none", com.example.R.string.list_anim_none),
  SLIDE("slide", com.example.R.string.list_anim_slide),
  FADE("fade", com.example.R.string.list_anim_fade),
  ZOOM("zoom", com.example.R.string.list_anim_zoom),
  SIDE("side", com.example.R.string.list_anim_side),
  FLIP("flip", com.example.R.string.list_anim_flip),
  BOUNCE("bounce", com.example.R.string.list_anim_bounce);

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
  // Plain primitive array avoids SaveableStateRegistry registration and prevents a secondary
  // MutableState recomposition on every row 1 frame after it scrolls into view.
  val staggered = remember { booleanArrayOf(false) }
  val progress = remember(style) { Animatable(0f) }
  LaunchedEffect(style) {
    if (!staggered[0] && index < Motion.StaggerItems) kotlinx.coroutines.delay(index * Motion.StaggerStepMs)
    staggered[0] = true
    progress.animateTo(
      1f,
      if (style == ListAnimation.BOUNCE) spring(dampingRatio = 0.55f, stiffness = 520f, visibilityThreshold = 0.005f)
      else Motion.fluidFast(visibilityThreshold = 0.005f)
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
 * Zero-recomposition GPU press scale driven by [interaction]: compresses immediately on press
 * (critically damped, stiffness 950) and springs back on release using [Motion.fluidFast].
 * Reads [Animatable.value] strictly inside [graphicsLayer], so tapping never triggers recomposition.
 */
@Composable
fun Modifier.tactilePressScale(
  interaction: MutableInteractionSource,
  pressedScale: Float = 0.96f
): Modifier {
  val reduced = AppMotion.reduced
  val scale = remember { Animatable(1f) }
  LaunchedEffect(interaction, reduced, pressedScale) {
    if (reduced) {
      scale.snapTo(1f)
      return@LaunchedEffect
    }
    var pressCount = 0
    var animJob: Job? = null
    interaction.interactions.collect { ev ->
      when (ev) {
        is PressInteraction.Press -> {
          pressCount++
          animJob?.cancel()
          animJob = launch { scale.animateTo(pressedScale, Motion.criticalSpatial(stiffness = 950f)) }
        }
        is PressInteraction.Release, is PressInteraction.Cancel -> {
          pressCount = (pressCount - 1).coerceAtLeast(0)
          if (pressCount == 0) {
            animJob?.cancel()
            animJob = launch { scale.animateTo(1f, Motion.fluidFast(visibilityThreshold = 0.001f)) }
          }
        }
      }
    }
  }
  return this.graphicsLayer {
    val s = scale.value
    scaleX = s
    scaleY = s
  }
}

/**
 * Click with a subtle tactile zoom-out while pressed, like tapping a card in Apple Music.
 * Driven strictly in [graphicsLayer] (0 recompositions per press/release).
 */
@Composable
fun Modifier.pressClickable(
  pressedScale: Float = 0.96f,
  onClickLabel: String? = null,
  role: Role? = Role.Button,
  onClick: () -> Unit
): Modifier {
  val interaction = remember { MutableInteractionSource() }
  return this
    .tactilePressScale(interaction, pressedScale = pressedScale)
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
