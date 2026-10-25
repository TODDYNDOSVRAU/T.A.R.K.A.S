package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
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
import com.example.ui.viewmodel.TarkasNavTab

/**
 * Motion used across the app. Everything here moves only alpha and scale (drawn by the GPU
 * without re-measuring or re-laying out), so it stays smooth at 120 Hz and costs little battery.
 */
object Motion {
  /** Fast start, long soft landing: feels like the surface settles into place. */
  val Settle = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
  const val ScreenEnterMs = 330
  const val ScreenExitMs = 100
  /** Between Spring.StiffnessMediumLow and StiffnessMedium: settles in about a third of a second. */
  const val ScreenStiffness = 700f
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

/** Order of the screens left to right, so a tab switch slides the way the bar reads. */
private fun screenOrder(screen: Any): Int = when (screen) {
  TarkasNavTab.HOME -> 0
  TarkasNavTab.LIBRARY -> 1
  TarkasNavTab.AUDIO -> 2
  TarkasNavTab.ASSISTANT -> 3
  TarkasNavTab.SETTINGS -> 4
  else -> 5
}

/** Slides between tabs: an underdamped spring that lands with a small, soft overshoot. */
private val TabSlide = spring<IntOffset>(dampingRatio = 0.78f, stiffness = 380f)
private val TabScale = spring<Float>(dampingRatio = 0.78f, stiffness = 380f)

/**
 * Transition between screens. Tabs slide in the direction of the bar (right when moving right),
 * with the new screen settling on a soft spring while the old one drifts away and fades quickly.
 * Opening the full player zooms in; leaving it grows and fades away.
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

  val dir = if (screenOrder(to) >= screenOrder(from)) 1 else -1
  val enter = slideInHorizontally(TabSlide) { w -> dir * w / 5 } +
    fadeIn(tween(Motion.ScreenEnterMs - Motion.ScreenExitMs, delayMillis = Motion.ScreenExitMs / 2, easing = Motion.Settle)) +
    scaleIn(initialScale = 0.96f, animationSpec = TabScale)
  val exit = slideOutHorizontally(TabSlide) { w -> -dir * w / 8 } +
    fadeOut(tween(Motion.ScreenExitMs, easing = FastOutLinearInEasing))
  return (enter togetherWith exit).apply {
    // The new screen draws over the old one, so it never slips behind the page that is leaving.
    targetContentZIndex = 1f
  }
}

/**
 * Click with a small springy zoom-out while pressed, like tapping a card in Apple Music.
 * The scale is read inside graphicsLayer, so the press animation does not recompose anything.
 */
@Composable
fun Modifier.pressClickable(
  pressedScale: Float = 0.96f,
  onClickLabel: String? = null,
  onClick: () -> Unit
): Modifier {
  val interaction = remember { MutableInteractionSource() }
  val pressed = interaction.collectIsPressedAsState()
  val scale = animateFloatAsState(
    targetValue = if (pressed.value) pressedScale else 1f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
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
      onClick = onClick
    )
}
