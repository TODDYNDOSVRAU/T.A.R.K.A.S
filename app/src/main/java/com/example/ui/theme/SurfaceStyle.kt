package com.example.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** How floating surfaces (bottom bar, player buttons) are drawn. Chosen in Ajustes. */
enum class SurfaceStyle(val key: String, val label: String, val description: String) {
  SOLID("solid", "Sólido", "Cor do app, sem transparência. É o mais leve para a bateria."),
  FROSTED("frosted", "Vidro fosco", "Desfoca o que passa por trás da barra."),
  LIQUID("liquid", "Liquid glass", "Vidro desfocado com reflexo de luz em movimento, mais caro para a GPU.");

  companion object {
    fun fromKey(key: String?): SurfaceStyle = entries.firstOrNull { it.key == key } ?: SOLID
  }
}

val LocalSurfaceStyle = staticCompositionLocalOf { SurfaceStyle.SOLID }

/** The blur source: the screen content that floating surfaces blur. Null when blur is not available. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Height the floating bottom bar covers, so lists can scroll past it. */
val LocalBarInset = compositionLocalOf { 0.dp }

private val BlurFrosted: Dp = 24.dp
private val BlurLiquid: Dp = 34.dp

/**
 * Background for a floating surface, following the chosen [SurfaceStyle]. Without a blur source
 * (or when the style is solid) it falls back to the plain app surface colour.
 */
@Composable
fun Modifier.glassSurface(shape: Shape = RectangleShape): Modifier {
  val style = LocalSurfaceStyle.current
  val haze = LocalHazeState.current
  val colors = MaterialTheme.colorScheme
  val isDark = colors.background.luminance() < 0.5f

  if (style == SurfaceStyle.SOLID || haze == null) {
    return this.background(colors.surface, shape)
  }

  val frosted = style == SurfaceStyle.FROSTED
  val tintAlpha = when {
    frosted && isDark -> 0.58f
    frosted -> 0.62f
    isDark -> 0.34f
    else -> 0.42f
  }
  val hazeStyle = HazeStyle(
    backgroundColor = colors.surface,
    tint = HazeTint(colors.surface.copy(alpha = tintAlpha)),
    blurRadius = if (frosted) BlurFrosted else BlurLiquid,
    noiseFactor = if (frosted) 0.05f else 0.03f
  )

  return this
    .clip(shape)
    .hazeEffect(state = haze, style = hazeStyle)
    .glassFinish(shape, frosted, isDark, LocalGlassLight.current)
}

/**
 * The same glass look for controls that sit inside the page (search field, round buttons). They sit
 * on the plain page colour, so there is nothing to blur behind them: they keep the tint, reflection and
 * rim of the floating capsules without the cost of a blur. Solid style gives a plain filled control.
 */
@Composable
fun Modifier.glassControl(shape: Shape, selected: Boolean = false): Modifier {
  val style = LocalSurfaceStyle.current
  val colors = MaterialTheme.colorScheme
  val isDark = colors.background.luminance() < 0.5f

  if (style == SurfaceStyle.SOLID) {
    return this.clip(shape).background(if (selected) colors.primary else colors.surfaceVariant, shape)
  }
  val frosted = style == SurfaceStyle.FROSTED
  val fill = if (isDark) Color.White.copy(alpha = if (frosted) 0.09f else 0.07f)
  else Color.White.copy(alpha = if (frosted) 0.75f else 0.6f)
  val base = this
    .clip(shape)
    .background(fill, shape)
  if (selected) {
    // A selected glass control keeps its glass and takes the accent as a tint and an edge.
    return base
      .background(colors.primary.copy(alpha = if (isDark) 0.20f else 0.16f), shape)
      .border(1.2.dp, colors.primary.copy(alpha = 0.85f), shape)
  }
  return base
    // On a light page a white glass control would vanish, so a very faint dark edge keeps its shape.
    .then(if (isDark) Modifier else Modifier.border(0.6.dp, Color.Black.copy(alpha = 0.06f), shape))
    .glassFinish(shape, frosted, isDark, LocalGlassLight.current)
}

/** Text and icon colour that reads on [glassControl]: on solid, a selected control is an accent fill. */
@Composable
fun glassContentColor(selected: Boolean = false): Color {
  val colors = MaterialTheme.colorScheme
  return when {
    !selected -> colors.onSurface
    LocalSurfaceStyle.current == SurfaceStyle.SOLID -> colors.onPrimary
    else -> colors.primary
  }
}

/**
 * Phase (0 to 1) of the light that drifts across liquid glass. One clock for the whole app,
 * provided only while the liquid style is on and system animations are enabled.
 */
val LocalGlassLight = staticCompositionLocalOf<State<Float>?> { null }

@Composable
fun rememberGlassLight(active: Boolean): State<Float>? {
  if (!active) return null
  val transition = rememberInfiniteTransition(label = "glassLight")
  return transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(GLASS_LIGHT_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
    label = "glassLightPhase"
  )
}

private const val GLASS_LIGHT_PERIOD_MS = 7000

/**
 * Reflection and glow shared by every glass surface. No outline: the edge comes from light, not a
 * drawn border. Liquid glass adds a soft band of light that slowly crosses the surface ([light] is
 * read only while drawing, so it never recomposes).
 */
private fun Modifier.glassFinish(shape: Shape, frosted: Boolean, isDark: Boolean, light: State<Float>? = null): Modifier {
  if (frosted) {
    // Frosted glass: only a faint lift at the top, so the capsule reads without a hard edge.
    val lift = Color.White.copy(alpha = if (isDark) 0.05f else 0.18f)
    return this.drawBehind {
      drawRect(Brush.verticalGradient(listOf(lift, Color.Transparent), endY = size.height * 0.5f))
    }
  }
  val sheen = Color.White.copy(alpha = if (isDark) 0.09f else 0.30f)
  val glow = Color.White.copy(alpha = if (isDark) 0.06f else 0.20f)
  val band = Color.White.copy(alpha = if (isDark) 0.07f else 0.22f)
  return this.drawBehind {
    // Reflection on the upper half and a glow along the lower edge, like light through a lens.
    drawRect(Brush.verticalGradient(listOf(sheen, Color.Transparent), endY = size.height * 0.55f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, glow), startY = size.height * 0.72f, endY = size.height))
    light?.let {
      // The moving band enters past the left edge and leaves past the right one, so the loop
      // restarts out of sight.
      val w = size.width
      val x = -w * 0.9f + (w * 2.3f) * it.value
      drawRect(
        Brush.linearGradient(
          colors = listOf(Color.Transparent, band, Color.Transparent),
          start = Offset(x, 0f),
          end = Offset(x + w * 0.55f, size.height)
        )
      )
    }
  }
}

/** Rounded variant used by small floating controls. */
@Composable
fun Modifier.glassPill(cornerRadius: Dp = 24.dp): Modifier = glassSurface(RoundedCornerShape(cornerRadius))
