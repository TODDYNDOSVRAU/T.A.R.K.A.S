package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
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
  LIQUID("liquid", "Liquid glass", "Vidro que curva o que passa por trás, com borda iluminada (Android 13 ou mais novo). Usa mais a GPU.");

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

  // Liquid glass bends what is behind it: refraction at the rim, a touch of chromatic aberration and
  // a lit edge. It needs Android 13 for the shaders and a rounded shape for the lens.
  val backdrop = LocalBackdrop.current
  if (style == SurfaceStyle.LIQUID && backdrop != null && shape is CornerBasedShape && Build.VERSION.SDK_INT >= 33) {
    return this.liquidGlass(shape, backdrop, isDark, colors.surface)
  }

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
    .glassFinish(frosted, isDark)
}

/** The screen content, recorded so liquid glass can refract it. Null when liquid glass is off or unsupported. */
val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Whether this device can draw real liquid glass (the lens shader needs Android 13). */
val liquidGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= 33

private fun Modifier.liquidGlass(shape: CornerBasedShape, backdrop: LayerBackdrop, isDark: Boolean, surface: Color): Modifier {
  // A thin tint keeps text readable over busy artwork without hiding the refraction.
  val tint = surface.copy(alpha = if (isDark) 0.42f else 0.50f)
  return this.drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
      vibrancy()
      // Enough blur that text passing underneath never competes with the bar's own labels.
      blur(9.dp.toPx())
      // Height of the bent rim, how far it bends, depth, and the colour fringe at the edge.
      lens(12.dp.toPx(), 22.dp.toPx(), true, true)
    },
    onDrawSurface = { drawRect(tint) }
  )
}

/**
 * Controls inside the page (search field, chips, round buttons). Glass belongs to what floats over the
 * content (the tab bar, the mini player); a control that sits on the page is a plain, quiet fill, the
 * way the platform draws its own buttons. Selected, it takes a light wash of the accent. The same in
 * every surface style, so pages look alike whatever the bars are made of.
 */
@Composable
fun Modifier.glassControl(shape: Shape, selected: Boolean = false): Modifier {
  val colors = MaterialTheme.colorScheme
  val isDark = colors.background.luminance() < 0.5f
  val fill = if (selected) colors.primary.copy(alpha = if (isDark) 0.18f else 0.14f) else colors.surfaceContainer
  return this.clip(shape).background(fill, shape)
}

/** Text and icon colour that reads on [glassControl]. */
@Composable
fun glassContentColor(selected: Boolean = false): Color {
  val colors = MaterialTheme.colorScheme
  return if (selected) colors.primary else colors.onSurface
}

/**
 * Reflection and glow shared by the blurred and in-page glass. No outline: the edge comes from
 * light, not a drawn border. Real liquid glass lights its own rim, so it does not use this.
 */
@Suppress("UNUSED_PARAMETER")
private fun Modifier.glassFinish(frosted: Boolean, isDark: Boolean): Modifier {
  // Only a faint lift at the top, so the capsule reads as a surface without a drawn outline or a
  // glossy reflection.
  val lift = Color.White.copy(alpha = if (isDark) 0.05f else 0.16f)
  return this.drawBehind {
    drawRect(Brush.verticalGradient(listOf(lift, Color.Transparent), endY = size.height * 0.5f))
  }
}

/** Rounded variant used by small floating controls. */
@Composable
fun Modifier.glassPill(cornerRadius: Dp = 24.dp): Modifier = glassSurface(RoundedCornerShape(cornerRadius))
