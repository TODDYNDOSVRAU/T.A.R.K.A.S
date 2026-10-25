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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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

/** True when the user turned system animations off (or to zero). Motion is then skipped. */
@Composable
fun rememberReducedMotion(): Boolean {
  val context = LocalContext.current
  return remember { isReducedMotion(context) }
}

private fun isReducedMotion(context: Context): Boolean = try {
  Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (_: Exception) {
  false
}

/**
 * Transition between screens: the new screen zooms in from slightly smaller while it fades in; the
 * old one fades out. Opening the full player zooms in more; leaving it grows and fades away.
 */
fun screenTransition(from: Any, to: Any, reduced: Boolean = false): ContentTransform {
  if (reduced) return EnterTransition.None togetherWith ExitTransition.None

  val enterScale = when {
    to == TarkasNavTab.MUSIC -> 0.88f
    to == "curiosity" -> 0.94f
    else -> 0.97f
  }
  val exitScale = if (from == TarkasNavTab.MUSIC) 1.06f else 0.98f

  // Fade through: the old screen is gone before the new one starts to show, so the two never
  // appear mixed, even when a slow phone drops frames during the switch. Scale rides a critically
  // damped spring, so a tap that interrupts the transition never jumps.
  val enter = fadeIn(tween(Motion.ScreenEnterMs - Motion.ScreenExitMs, delayMillis = Motion.ScreenExitMs, easing = Motion.Settle)) +
    scaleIn(initialScale = enterScale, animationSpec = spring(dampingRatio = 1f, stiffness = Motion.ScreenStiffness))
  val exit = fadeOut(tween(Motion.ScreenExitMs, easing = FastOutLinearInEasing)) +
    scaleOut(targetScale = exitScale, animationSpec = tween(Motion.ScreenExitMs, easing = FastOutLinearInEasing))
  return (enter togetherWith exit).apply {
    // The new screen draws over the old one, so the zoom-in never slips behind the fading page.
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
