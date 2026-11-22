package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How floating surfaces (bottom bar, player buttons) are drawn. Chosen in Ajustes. */
enum class SurfaceStyle(val key: String, @androidx.annotation.StringRes val labelRes: Int) {
  SOLID("solid", com.example.R.string.settings_surface_solid),
  FROSTED("frosted", com.example.R.string.settings_surface_frosted),
  LIQUID("liquid", com.example.R.string.settings_surface_liquid);

  companion object {
    fun fromKey(key: String?): SurfaceStyle = entries.firstOrNull { it.key == key } ?: SOLID
  }
}

val LocalSurfaceStyle = staticCompositionLocalOf { SurfaceStyle.SOLID }

/** Default opacity for [SurfaceStyle.LIQUID]: 0f = 100% transparent, 1f = 100% opaque. */
const val DefaultLiquidGlassOpacity = 0.25f

/** Current liquid glass opacity (0f..1f), chosen in Ajustes when Liquid glass is active. */
val LocalLiquidGlassOpacity = staticCompositionLocalOf { DefaultLiquidGlassOpacity }

/** The blur source: the screen content that floating surfaces blur. Null when blur is not available. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Height the floating bottom bar covers, so lists can scroll past it. */
val LocalBarInset = compositionLocalOf { 0.dp }

private val BlurFrosted: Dp = 24.dp
private val BlurLiquid: Dp = 34.dp

/**
 * Background for a floating surface, following the chosen [SurfaceStyle]. Without a blur source
 * (or when the style is solid) it falls back to the plain app surface colour.
 *
 * When [interactive] is true and [SurfaceStyle.LIQUID] is active, touches illuminate the glass
 * under the finger with a radial specular highlight and a subtle spring swell (matching iOS 26
 * Liquid Glass / `expo-glass-effect` `isInteractive`).
 */
@Composable
fun Modifier.glassSurface(shape: Shape = RectangleShape, interactive: Boolean = false): Modifier {
  val style = LocalSurfaceStyle.current
  val haze = LocalHazeState.current
  val colors = MaterialTheme.colorScheme
  val isDark = colors.background.luminance() < 0.5f

  // Liquid glass bends what is behind it: refraction at the rim, a touch of chromatic aberration and
  // a lit specular edge. It needs Android 13 for the AGSL shaders and a rounded shape for the lens.
  val backdrop = LocalBackdrop.current
  if (style == SurfaceStyle.LIQUID && backdrop != null && shape is CornerBasedShape && Build.VERSION.SDK_INT >= 33) {
    return this.liquidGlass(shape, backdrop, isDark, colors.surface, interactive)
  }

  if (style == SurfaceStyle.SOLID || haze == null) {
    return this.background(colors.surface, shape)
  }

  val frosted = style == SurfaceStyle.FROSTED
  val tintAlpha = when {
    frosted && isDark -> 0.58f
    frosted -> 0.62f
    isDark -> 0.34f
    else -> 0.8f
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

/**
 * Mutable touch state read only in draw/layer phases so dragging across a liquid glass capsule
 * triggers zero Compose recompositions and never re-creates [drawBackdrop].
 */
private class LiquidTouchState {
  val glow = Animatable(0f)
  val scale = Animatable(1f)
  var x = 0f
  var y = 0f
  var hasPoint = false
  var glowJob: Job? = null
  var scaleJob: Job? = null
}

private val LiquidLensHeight: Dp = 16.dp
private val LiquidLensAmount: Dp = 32.dp
private const val LiquidMinBlurDp = 2.5f
private const val LiquidMaxBlurDp = 12f

@Composable
private fun Modifier.liquidGlass(
  shape: CornerBasedShape,
  backdrop: LayerBackdrop,
  isDark: Boolean,
  surface: Color,
  interactive: Boolean
): Modifier {
  // 0f = 100% transparent veil (0f alpha) with minimal 2.5 dp optical blur so background text
  // loses sharp edges without fogging the glass; 1f = 100% opaque.
  val opacity = LocalLiquidGlassOpacity.current.coerceIn(0f, 1f)
  val blurDp = (LiquidMinBlurDp + opacity * (LiquidMaxBlurDp - LiquidMinBlurDp)).dp
  val tint = surface.copy(alpha = opacity)
  val sheenTop = Color.White.copy(alpha = (if (isDark) 0.08f else 0.28f) * (1f - opacity * 0.7f))
  val touchGlowColor = Color.White.copy(alpha = if (isDark) 0.18f else 0.36f)
  val rimHighlight = remember(isDark) {
    Highlight(
      width = 1.dp,
      blurRadius = 0.5.dp,
      alpha = if (isDark) 0.60f else 0.85f,
      style = HighlightStyle.Default
    )
  }

  if (!interactive) {
    return this.drawBackdrop(
      backdrop = backdrop,
      shape = { shape },
      effects = {
        vibrancy()
        if (blurDp > 0.1.dp) blur(blurDp.toPx())
        lens(LiquidLensHeight.toPx(), LiquidLensAmount.toPx(), true, true)
      },
      highlight = { rimHighlight },
      shadow = null,
      innerShadow = null,
      onDrawSurface = {
        if (opacity > 0.001f) drawRect(tint)
        drawRect(Brush.verticalGradient(listOf(sheenTop, Color.Transparent), endY = size.height * 0.45f))
      }
    )
  }

  val reduced = rememberReducedMotion()
  val scope = rememberCoroutineScope()
  val touch = remember { LiquidTouchState() }

  // Non-consuming pointer observer on Initial pass; animates only Animatable values read in draw/layer.
  val touchObserver = Modifier.pointerInput(reduced) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      touch.x = down.position.x
      touch.y = down.position.y
      touch.hasPoint = true
      touch.glowJob?.cancel()
      touch.glowJob = scope.launch { touch.glow.animateTo(1f, Motion.defaultEffects()) }
      if (!reduced) {
        touch.scaleJob?.cancel()
        touch.scaleJob = scope.launch {
          touch.scale.animateTo(1.016f, Motion.criticalSpatial(Motion.FastSpatialStiffness))
        }
      }
      try {
        while (true) {
          val event = awaitPointerEvent(pass = PointerEventPass.Initial)
          val active = event.changes.firstOrNull { it.pressed }
          if (active != null) {
            touch.x = active.position.x
            touch.y = active.position.y
          } else {
            break
          }
        }
      } finally {
        touch.glowJob?.cancel()
        touch.glowJob = scope.launch { touch.glow.animateTo(0f, Motion.defaultEffects()) }
        if (!reduced) {
          touch.scaleJob?.cancel()
          touch.scaleJob = scope.launch { touch.scale.animateTo(1f, Motion.gel()) }
        }
      }
    }
  }

  return this
    .then(touchObserver)
    .drawBackdrop(
      backdrop = backdrop,
      shape = { shape },
      effects = {
        vibrancy()
        if (blurDp > 0.1.dp) blur(blurDp.toPx())
        // Height of the bent rim, how far it bends, depth curvature, and subtle edge dispersion.
        lens(LiquidLensHeight.toPx(), LiquidLensAmount.toPx(), true, true)
      },
      highlight = { rimHighlight },
      shadow = null,
      innerShadow = null,
      layerBlock = {
        val s = touch.scale.value
        if (s != 1f) {
          scaleX = s
          scaleY = s
        }
      },
      onDrawSurface = {
        if (opacity > 0.001f) drawRect(tint)
        drawRect(Brush.verticalGradient(listOf(sheenTop, Color.Transparent), endY = size.height * 0.45f))
        val g = touch.glow.value
        if (g > 0.005f && touch.hasPoint) {
          val radius = maxOf(size.width, size.height) * 0.55f
          drawRect(
            Brush.radialGradient(
              colors = listOf(touchGlowColor.copy(alpha = touchGlowColor.alpha * g), Color.Transparent),
              center = Offset(touch.x, touch.y),
              radius = radius.coerceAtLeast(1f)
            )
          )
        }
      }
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
fun Modifier.glassPill(cornerRadius: Dp = Radius.xl, interactive: Boolean = false): Modifier =
  glassSurface(RoundedCornerShape(cornerRadius), interactive = interactive)

/**
 * Real AGSL Liquid Glass lens (`drawBackdrop`) for small interactive pills (switches, sliders)
 * floating over a local [LayerBackdrop] of their own track — refracts the coloured track through
 * the pill like Apple iOS 26 `UIGlassEffect` without recording the whole screen twice.
 */
@Composable
fun Modifier.liquidLensPill(
  backdrop: Backdrop,
  isDark: Boolean,
  trackAccent: Color? = null
): Modifier {
  if (Build.VERSION.SDK_INT < 33) return this
  val rim = remember(isDark) {
    Highlight(
      width = 1.dp,
      blurRadius = 0.5.dp,
      alpha = if (isDark) 0.72f else 0.94f,
      style = HighlightStyle.Default
    )
  }
  val sheenTop = Color.White.copy(alpha = if (isDark) 0.16f else 0.30f)
  val sheenColors = remember(isDark) { listOf(sheenTop, Color.Transparent) }
  val accentBrush = remember(trackAccent) {
    trackAccent?.let { Brush.horizontalGradient(listOf(it.copy(alpha = 0.45f), Color.Transparent)) }
  }
  return this.drawBackdrop(
    backdrop = backdrop,
    shape = { RoundedCornerShape(50) },
    effects = {
      vibrancy()
      lens(6.dp.toPx(), 14.dp.toPx(), true, true)
    },
    highlight = { rim },
    shadow = null,
    innerShadow = null,
    onDrawSurface = {
      drawRect(Brush.verticalGradient(sheenColors, endY = size.height * 0.5f))
      if (accentBrush != null) {
        val strokePx = 1.dp.toPx()
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(
          brush = accentBrush,
          topLeft = Offset(strokePx / 2f, strokePx / 2f),
          size = androidx.compose.ui.geometry.Size(
            (size.width - strokePx).coerceAtLeast(0f),
            (size.height - strokePx).coerceAtLeast(0f)
          ),
          cornerRadius = r,
          style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx)
        )
      }
    }
  )
}

/**
 * Real AGSL Liquid Glass (`drawBackdrop`) for in-page controls (chips, segmented tabs, buttons)
 * when [SurfaceStyle.LIQUID] is active on API 33+. Uses a lightweight [rememberCanvasBackdrop]
 * matching the page surface so every Liquid Glass element in the app shares the exact same
 * `vibrancy + blur + lens + Highlight` shader pipeline without duplicating full-screen layers.
 */
@Composable
fun Modifier.liquidChrome(
  shape: Shape = RoundedCornerShape(Grid.u24),
  selected: Boolean = false,
  tint: Color? = null
): Modifier {
  val style = LocalSurfaceStyle.current
  val colors = MaterialTheme.colorScheme
  val isDark = colors.background.luminance() < 0.5f
  if (style != SurfaceStyle.LIQUID || !liquidGlassSupported) return this

  val pageBg = colors.background
  val canvasBackdrop = rememberCanvasBackdrop { drawRect(pageBg) }
  val glassShape = remember(shape) { shape as? CornerBasedShape ?: RoundedCornerShape(50) }
  val baseTint = tint ?: when {
    selected && isDark -> Color.White.copy(alpha = 0.16f)
    selected -> Color.White.copy(alpha = 0.68f)
    isDark -> Color.White.copy(alpha = 0.06f)
    else -> Color.White.copy(alpha = 0.36f)
  }
  val sheen = Color.White.copy(alpha = if (isDark) (if (selected) 0.16f else 0.08f) else (if (selected) 0.45f else 0.28f))
  val rim = remember(isDark, selected) {
    Highlight(
      width = 1.dp,
      blurRadius = 0.5.dp,
      alpha = if (isDark) (if (selected) 0.72f else 0.42f) else (if (selected) 0.94f else 0.74f),
      style = HighlightStyle.Default
    )
  }

  return this.drawBackdrop(
    backdrop = canvasBackdrop,
    shape = { glassShape },
    effects = {
      vibrancy()
      blur(2.5.dp.toPx())
      lens(8.dp.toPx(), 16.dp.toPx(), true, true)
    },
    highlight = { rim },
    shadow = null,
    innerShadow = null,
    onDrawSurface = {
      drawRect(baseTint)
      drawRect(Brush.verticalGradient(listOf(sheen, Color.Transparent), endY = size.height * 0.5f))
    }
  )
}

/**
 * Draws an Apple iOS 26-style oblong Liquid Glass pill thumb: ultra-clear interior so the
 * underlying track shows cleanly through the lens, with crisp specular rim and refracted
 * [trackAccent] edge tint.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiquidGlassThumb(
  center: Offset,
  radius: Float,
  isDark: Boolean,
  trackAccent: Color? = null,
  widthRatio: Float = 1.55f
) {
  val h = radius * 2f
  val w = h * widthRatio
  val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)
  val pillSize = androidx.compose.ui.geometry.Size(w, h)
  val r = androidx.compose.ui.geometry.CornerRadius(h / 2f)
  val strokePx = 1.15.dp.toPx()

  // Soft contact shadow around the glass lens
  drawRoundRect(
    color = Color.Black.copy(alpha = if (isDark) 0.34f else 0.12f),
    topLeft = Offset(topLeft.x, topLeft.y + 1.5.dp.toPx()),
    size = pillSize,
    cornerRadius = r
  )

  // Ultra-clear glass interior so the track beneath stays visible through the pill
  drawRoundRect(
    brush = Brush.verticalGradient(
      colors = listOf(
        Color.White.copy(alpha = if (isDark) 0.22f else 0.34f),
        Color.White.copy(alpha = if (isDark) 0.04f else 0.06f),
        Color.White.copy(alpha = if (isDark) 0.14f else 0.24f)
      ),
      startY = topLeft.y,
      endY = topLeft.y + h
    ),
    topLeft = topLeft,
    size = pillSize,
    cornerRadius = r
  )

  // Refracted accent colour catching the inner rim (like the blue/green rim in Apple's Liquid Glass)
  if (trackAccent != null) {
    drawRoundRect(
      brush = Brush.horizontalGradient(
        colors = listOf(
          trackAccent.copy(alpha = if (isDark) 0.55f else 0.65f),
          trackAccent.copy(alpha = 0.08f),
          Color.Transparent
        ),
        startX = topLeft.x,
        endX = topLeft.x + w
      ),
      topLeft = Offset(topLeft.x + strokePx / 2f, topLeft.y + strokePx / 2f),
      size = androidx.compose.ui.geometry.Size((w - strokePx).coerceAtLeast(0f), (h - strokePx).coerceAtLeast(0f)),
      cornerRadius = r,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx * 1.35f)
    )
  }

  // Crisp specular outer glass rim
  drawRoundRect(
    brush = Brush.verticalGradient(
      colors = listOf(
        Color.White.copy(alpha = if (isDark) 0.88f else 0.96f),
        Color.White.copy(alpha = if (isDark) 0.22f else 0.38f),
        if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.16f)
      ),
      startY = topLeft.y,
      endY = topLeft.y + h
    ),
    topLeft = Offset(topLeft.x + strokePx / 2f, topLeft.y + strokePx / 2f),
    size = androidx.compose.ui.geometry.Size((w - strokePx).coerceAtLeast(0f), (h - strokePx).coerceAtLeast(0f)),
    cornerRadius = r,
    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx)
  )
}

/** Draws a vertical pill Liquid Glass handle for the EQ faders (clear interior + refracted rim). */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiquidGlassPillThumb(
  topLeft: Offset,
  size: androidx.compose.ui.geometry.Size,
  isDark: Boolean,
  accent: Color? = null
) {
  val r = androidx.compose.ui.geometry.CornerRadius(size.width / 2f)
  val strokePx = 1.1.dp.toPx()
  drawRoundRect(
    color = Color.Black.copy(alpha = if (isDark) 0.32f else 0.12f),
    topLeft = Offset(topLeft.x, topLeft.y + 1.5.dp.toPx()),
    size = size,
    cornerRadius = r
  )
  // Ultra-clear glass body so the intensity-coloured fader track shows through
  drawRoundRect(
    brush = Brush.verticalGradient(
      colors = listOf(
        Color.White.copy(alpha = if (isDark) 0.26f else 0.42f),
        Color.White.copy(alpha = if (isDark) 0.05f else 0.08f),
        Color.White.copy(alpha = if (isDark) 0.16f else 0.28f)
      ),
      startY = topLeft.y,
      endY = topLeft.y + size.height
    ),
    topLeft = topLeft,
    size = size,
    cornerRadius = r
  )
  if (accent != null) {
    drawRoundRect(
      color = accent.copy(alpha = 0.55f),
      topLeft = Offset(topLeft.x + strokePx / 2f, topLeft.y + strokePx / 2f),
      size = androidx.compose.ui.geometry.Size(
        (size.width - strokePx).coerceAtLeast(0f),
        (size.height - strokePx).coerceAtLeast(0f)
      ),
      cornerRadius = r,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx * 1.2f)
    )
  }
  drawRoundRect(
    brush = Brush.verticalGradient(
      colors = listOf(
        Color.White.copy(alpha = if (isDark) 0.88f else 0.98f),
        Color.White.copy(alpha = if (isDark) 0.24f else 0.40f),
        if (isDark) Color.White.copy(alpha = 0.52f) else Color.Black.copy(alpha = 0.16f)
      ),
      startY = topLeft.y,
      endY = topLeft.y + size.height
    ),
    topLeft = Offset(topLeft.x + strokePx / 2f, topLeft.y + strokePx / 2f),
    size = androidx.compose.ui.geometry.Size(
      (size.width - strokePx).coerceAtLeast(0f),
      (size.height - strokePx).coerceAtLeast(0f)
    ),
    cornerRadius = r,
    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx)
  )
}
