package com.example.windows.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.Grid

/**
 * The contract of Expo's `expo-glass-effect` (SDK 58: `GlassView`, `GlassContainer`, `isGlassEffectAPIAvailable`),
 * translated to Compose Desktop. The Expo page describes Apple's Liquid Glass (iOS 26); nothing of its code is used, only
 * its vocabulary and rules, kept pure here so they can be tested without a window:
 *
 *  - `glassEffectStyle`: `regular` (adaptive, more blur and legibility), `clear` (more transparent, the backdrop shows),
 *    `none` (no glass: an ordinary surface); as an object `{ style, animate, animationDuration }` the change of style is animated.
 *  - `tintColor`, `isInteractive`, `colorScheme` (`auto` follows the app theme, `light` and `dark` force it).
 *  - `GlassContainer.spacing`: glasses closer than that merge.
 *  - Availability: check before use, fall back to a plain view when the platform cannot do it.
 *  - Opacity below 1 on the glass or its parents makes the effect vanish, so the glass is never faded: a fade is the
 *    animated change of style (glass to `none`).
 */
enum class GlassStyle { Regular, Clear, None }

/** `colorScheme`: [Auto] follows `MaterialTheme`; [Light] and [Dark] force the T.A.L. scheme of that name for the glass and its content. */
enum class GlassScheme { Auto, Light, Dark }

/**
 * `glassEffectStyle` as an object. [animate] false (the default, as in the page) changes style at once; true interpolates
 * over [animationMs], null meaning the T.A.L. default [GlassSpec.StyleMs].
 */
data class GlassEffect(val style: GlassStyle = GlassStyle.Regular, val animate: Boolean = false, val animationMs: Int? = null) {
  val durationMs: Int get() = animationMs ?: GlassSpec.StyleMs
  companion object {
    val Regular = GlassEffect(GlassStyle.Regular)
    val Clear = GlassEffect(GlassStyle.Clear)
    val None = GlassEffect(GlassStyle.None)
  }
}

/**
 * What one style looks like. [blurDp] is the sigma of the blur of the backdrop copy, [displaceDp] the displacement of its
 * light refraction at full noise (the copy moves at most half of it per axis), [scrim] the share of the shell's glass alpha
 * ([com.example.windows.shell.ShellBackdrop.GLASS]) kept as the tint over it: `regular` carries the translucent scrim,
 * `clear` none, `none` is the plain translucent surface.
 */
internal data class GlassLook(val blurDp: Float, val displaceDp: Float, val scrim: Float)

internal object GlassSpec {
  /**
   * The owner asked for the glass at its minimum (2026-11-27): a touch of blur and refraction, a lighter scrim, a thin border.
   * Regular: 3 dp blur, 3 dp refraction, 60% of the shell's glass alpha as the scrim. Clear: 1.5 dp blur, 6 dp refraction, no scrim.
   */
  val Regular = GlassLook(blurDp = Grid.u6.value / 2f, displaceDp = Grid.u6.value / 2f, scrim = 0.6f)
  val Clear = GlassLook(blurDp = Grid.u6.value / 4f, displaceDp = Grid.u6.value, scrim = 0f)

  /** Noise of the refraction: cycles per dp and seed. */
  const val NoiseFreq = 0.05f
  const val NoiseSeed = 1f

  /** Growth under the pointer: 5% in 300 ms on cubic-bezier(.4, 0, .2, 1). Same curve and time for the change of style. */
  const val HoverScale = 1.05f
  const val HoverMs = 300
  const val StyleMs = 300
  val Curve = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

  /** Text and border of a disabled pill; the glass itself stays (never alpha on the glass). */
  const val DisabledAlpha = 0.5f

  /** The tint colour lies over the glass at this alpha: enough to read as a tint, light enough to keep the text AA. */
  const val TintAlpha = 0.08f

  /** The highlight that follows the pointer (interactive glass): radius and peak alpha at its centre. */
  val GlowRadiusDp = Grid.u96.value
  const val GlowPeak = 0.10f
  /** Share of the highlight while the pointer is down, against hovering. */
  const val GlowHover = 0.6f
  const val GlowPress = 1f

  /** The two glass looks, in the order of [GlassStyle]. */
  val Looks = listOf(Regular, Clear)

  /**
   * The backdrop copy is drawn this much larger than the pill on every side. The farthest a sample can come from is the
   * displacement of the unit-gradient noise (within 0.7071 per axis of half of [GlassLook.displaceDp]) plus the blur's
   * 3 sigma; the larger look decides and must fit.
   */
  val PadDp = Grid.u48.value
  fun reachDp(look: GlassLook): Float = look.displaceDp / 2f + 3f * look.blurDp
  val MaxReachDp: Float get() = Looks.maxOf(::reachDp)
}

/**
 * How much of each style is showing, summing to 1: one style at rest, a mix while the style changes. The glass of the
 * mix is the exact blend of the styles, so a half-way change is half of each, never a faded glass.
 */
internal data class GlassWeights(val regular: Float, val clear: Float, val none: Float) {
  companion object {
    fun of(style: GlassStyle) = when (style) {
      GlassStyle.Regular -> GlassWeights(1f, 0f, 0f)
      GlassStyle.Clear -> GlassWeights(0f, 1f, 0f)
      GlassStyle.None -> GlassWeights(0f, 0f, 1f)
    }
  }
}

internal fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/**
 * The two backdrop layers are composited bottom to top: regular, then clear. For the weights to come out exact
 * (clear * [clearAlpha] + regular * [regularAlpha] * (1 - [clearAlpha]) + what is behind for the rest) the clear layer
 * takes its own weight and the regular one what remains of the cover it leaves free.
 */
internal fun clearAlpha(clear: Float): Float = clear.coerceIn(0f, 1f)
internal fun regularAlpha(regular: Float, clear: Float): Float = if (clear >= 1f) 0f else (regular / (1f - clear)).coerceIn(0f, 1f)

/** The share of the shell's glass alpha the tint carries for these weights: regular and none carry all of it, clear none. */
internal fun scrimShare(regular: Float, clear: Float, none: Float): Float =
  regular * GlassSpec.Regular.scrim + clear * GlassSpec.Clear.scrim + none

/**
 * What the pill really shows: the requested style, unless glass is unavailable here or the user reduced transparency,
 * and then `none`: the plain translucent surface with a hairline, as the pill was before the glass.
 */
internal fun effectiveStyle(requested: GlassStyle, available: Boolean, reduceTransparency: Boolean): GlassStyle =
  if (!available || reduceTransparency) GlassStyle.None else requested

/**
 * `isGlassEffectAPIAvailable()`: true when this machine can build the filter the glass needs (Skia's blur, the minimum for
 * both glass styles; the refraction shader on top of it degrades to blur alone, see [GlassFx.Made.refracts]).
 */
fun isGlassAvailable(): Boolean = GlassFx.blurAvailable

/** Provided in tests (and by a shell that wants to force it); by default what this machine can do. */
internal val LocalGlassAvailable = staticCompositionLocalOf { isGlassAvailable() }

/**
 * The Windows setting "Transparency effects" off, or the app's own "Reduce transparency" (a string exists in
 * strings_player.xml, but the app has no such state yet): the glass becomes `none`. The shell provides it when it exists;
 * until then it is false.
 */
val LocalReduceTransparency = staticCompositionLocalOf { false }

/**
 * `GlassContainer`: glasses whose edges are closer than [spacing] merge. Pure geometry so it can be tested; only one glass
 * lives on screen today, so nothing draws a merge yet.
 */
object GlassContainerModel {
  /** The shortest distance between two rectangles (0 when they touch or overlap). */
  fun gap(a: Rect, b: Rect): Float {
    val dx = maxOf(0f, maxOf(a.left, b.left) - minOf(a.right, b.right))
    val dy = maxOf(0f, maxOf(a.top, b.top) - minOf(a.bottom, b.bottom))
    return kotlin.math.hypot(dx, dy)
  }

  /** True when [a] and [b] are closer than [spacing]. */
  fun merges(a: Rect, b: Rect, spacing: Float): Boolean = gap(a, b) < spacing

  /** The groups of glasses that merge, directly or through a neighbour, as lists of indices in the order given. */
  fun groups(glasses: List<Rect>, spacing: Float): List<List<Int>> {
    val parent = IntArray(glasses.size) { it }
    fun find(i: Int): Int { var r = i; while (parent[r] != r) r = parent[r]; var c = i; while (parent[c] != r) { val n = parent[c]; parent[c] = r; c = n }; return r }
    for (i in glasses.indices) for (j in i + 1 until glasses.size) if (merges(glasses[i], glasses[j], spacing)) parent[find(j)] = find(i)
    return glasses.indices.groupBy(::find).values.toList()
  }
}

/**
 * The glass of the mini-player pill, the one glass on screen. The style is `regular` unless a setting (or a test) says
 * otherwise; with a change of style animated by [GlassEffect.animate] when it is set from outside.
 */
internal object PillGlass {
  var effect: GlassEffect by mutableStateOf(GlassEffect.Regular)
  var tint: Color? by mutableStateOf<Color?>(null)
  fun reset() { effect = GlassEffect.Regular; tint = null }
}

/**
 * The pill's secondary text (artist line, elapsed and total). The pill's glass is the surface at 0.55 x the style's scrim
 * (0.33 regular, 0 clear) over the silk, so `onSurfaceVariant` falls under AA on the silk's worst pixel (3.81 / 3.08 dark,
 * 4.18 / 3.63 light, measured by PillWorstSilkContrastTest). The glass stays at the owner's minimum; the text takes the base
 * colour at [ALPHA], which holds 4.5:1 in every style and both themes and still reads below the title (alpha 1).
 */
internal object PillText {
  const val ALPHA = 0.72f
  fun secondary(cs: androidx.compose.material3.ColorScheme): Color = cs.onSurface.copy(alpha = ALPHA)
}
