package com.example.windows.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Grid
import com.example.ui.theme.Shapes
import com.example.ui.theme.TarkasDarkColorScheme
import com.example.ui.theme.TarkasLightColorScheme
import com.example.windows.shell.LocalGlassBackdrop
import com.example.windows.shell.ShellBackdrop
import com.example.windows.shell.drawGlassSources
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathDirection
import org.jetbrains.skia.PathFillMode
import org.jetbrains.skia.RRect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Surface
import kotlin.math.ceil
import kotlin.math.max
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Paint as ComposePaint

/** The nine shadows of the border: two edge glows, two hairlines, two soft inner fills and three outer shadows. */
internal data class Shade(val inset: Boolean, val dx: Float, val dy: Float, val blur: Float, val spread: Float, val alpha: Float)

internal object GlassRim {
  /** CSS box-shadow semantics, in dp: inset shadows tint the inside of the edge, the others fall outside the pill. */
  val Shades = listOf(
    Shade(true, 1.5f, 1.5f, 0.5f, -1f, 0.05f),   // top left: almost faded
    Shade(true, -1.5f, -1.5f, 0.5f, -1f, 0.30f), // bottom right: the edge light, discreet
    Shade(true, 1f, 1f, 1f, -0.5f, 0.20f),       // hairline, top left
    Shade(true, -1f, -1f, 1f, -0.5f, 0.40f),     // hairline, bottom right: the stronger one
    Shade(true, 0f, 0f, 6f, 6f, 0.04f),          // soft inner fill
    Shade(true, 0f, 0f, 2f, 2f, 0.03f),          // soft inner fill, tight
    Shade(false, 0f, 0f, 12f, 0f, 0.02f),        // outer, wide and faint
    Shade(false, 0f, 0f, 6f, 0f, 0.04f),
    Shade(false, 0f, 0f, 2f, 0f, 0.08f),         // outer, tight
  )
  init { check(Shades.size == 9) }

  /** The bitmap is the pill plus this margin on every side (the widest outer shadow reaches 3 sigma = 36 dp). */
  val MarginDp = Grid.u48.value

  /**
   * The border, rasterised once per size, density and pair of colours: [glow] (the theme's text colour: white in the dark
   * theme, black in the light) for the inset shades, [shadow] (the scrim, black) for the outer ones. The pill sits
   * [MarginDp] in from the top left of the result.
   */
  fun bake(wDp: Float, hDp: Float, density: Float, glow: Color, shadow: Color): ImageBitmap {
    val m = MarginDp * density
    val w = wDp * density; val h = hDp * density
    val surface = Surface.makeRasterN32Premul(ceil(w + 2 * m).toInt(), ceil(h + 2 * m).toInt())
    val canvas = surface.canvas
    canvas.translate(m, m)
    val pill = RRect.makeXYWH(0f, 0f, w, h, h / 2f)
    for (s in Shades) {
      val sigma = s.blur / 2f * density
      val paint = Paint().apply {
        isAntiAlias = true
        color = (if (s.inset) glow else shadow).copy(alpha = s.alpha).toArgb()
        if (sigma > 0f) maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma, true)
      }
      canvas.save()
      if (s.inset) {
        canvas.clipRRect(pill, ClipMode.INTERSECT, true)
        val reach = (abs(s.dx) + abs(s.dy) + 3f * sigma / density + 4f) * density
        val outer = RRect.makeXYWH(-reach, -reach, w + 2 * reach, h + 2 * reach, h / 2f + reach)
        // The hole: the pill moved by the offset and shrunk by the spread (a negative spread grows it).
        val sp = s.spread * density
        val hole = RRect.makeXYWH(s.dx * density + sp, s.dy * density + sp, max(w - 2 * sp, 0f), max(h - 2 * sp, 0f), max(h / 2f - sp, 0f))
        val ring = Path().apply {
          fillMode = PathFillMode.EVEN_ODD
          addRRect(outer, PathDirection.CLOCKWISE, 0)
          addRRect(hole, PathDirection.CLOCKWISE, 0)
        }
        canvas.drawPath(ring, paint)
      } else {
        canvas.clipRRect(pill, ClipMode.DIFFERENCE, true)
        canvas.drawRRect(RRect.makeXYWH(s.dx * density, s.dy * density, w, h, h / 2f), paint)
      }
      canvas.restore()
    }
    return surface.makeImageSnapshot().toComposeImageBitmap()
  }

  private fun abs(f: Float) = if (f < 0f) -f else f
}

/** The highlight of an interactive glass: a soft round light baked once, drawn at the pointer (a translate and a texture, no allocation). */
internal object GlassGlow {
  fun bake(radiusPx: Float, color: Color): ImageBitmap {
    val d = ceil(radiusPx * 2f).toInt()
    val image = ImageBitmap(d, d)
    val paint = ComposePaint().apply {
      shader = RadialGradientShader(
        Offset(d / 2f, d / 2f), d / 2f,
        listOf(color.copy(alpha = GlassSpec.GlowPeak), color.copy(alpha = GlassSpec.GlowPeak * 0.35f), color.copy(alpha = 0f)),
        listOf(0f, 0.45f, 1f),
      )
    }
    ComposeCanvas(image).drawRect(Rect(0f, 0f, d.toFloat(), d.toFloat()), paint)
    return image
  }
}

/** The filter of one look: noise displacement then blur, or the blur alone when the shader cannot be built here. */
internal object GlassFx {
  /** Displacement map as one runtime shader: the noise is computed per pixel, so no map is stored or uploaded. */
  const val Sksl = """
    uniform shader content;
    uniform float freq;
    uniform float amount;
    uniform float seed;

    float2 grad(float2 i) {
      float h = fract(sin(dot(i, float2(127.1, 311.7)) + seed * 17.0) * 43758.5453);
      float a = h * 6.2831853;
      return float2(cos(a), sin(a));
    }

    float perlin(float2 p) {
      float2 i = floor(p);
      float2 f = fract(p);
      float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
      float a = dot(grad(i), f);
      float b = dot(grad(i + float2(1.0, 0.0)), f - float2(1.0, 0.0));
      float c = dot(grad(i + float2(0.0, 1.0)), f - float2(0.0, 1.0));
      float d = dot(grad(i + float2(1.0, 1.0)), f - float2(1.0, 1.0));
      return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    half4 main(float2 xy) {
      float2 q = xy * freq;
      float2 n = float2(perlin(q), perlin(q + float2(37.2, 91.7)));
      return content.eval(xy + n * amount);
    }
  """

  /** Built once for the process; null when this machine cannot compile it (the filter then blurs only). */
  private val effect: RuntimeEffect? by lazy { runCatching { RuntimeEffect.makeForShader(Sksl) }.getOrNull() }

  /** True when Skia can build the blur filter, the minimum for either glass style ([isGlassAvailable]). */
  val blurAvailable: Boolean by lazy {
    runCatching { ImageFilter.makeBlur(1f, 1f, FilterTileMode.CLAMP, null, null).asComposeRenderEffect() }.isSuccess
  }

  class Made(val effect: RenderEffect?, val refracts: Boolean)

  /** For a density in px per dp. A uniform change means a new filter, so this runs once per look and density, never per frame. */
  fun create(density: Float, look: GlassLook): Made {
    val sigma = look.blurDp * density
    val shader = runCatching {
      val builder = RuntimeShaderBuilder(effect ?: error("no shader"))
      builder.uniform("freq", GlassSpec.NoiseFreq / density)
      builder.uniform("amount", look.displaceDp * density / 2f)
      builder.uniform("seed", GlassSpec.NoiseSeed)
      ImageFilter.makeRuntimeShader(builder, "content", null)
    }.getOrNull()
    val chain = runCatching { ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP, shader, null) }.getOrNull()
    return Made(chain?.asComposeRenderEffect(), shader != null)
  }
}

/**
 * The mix of styles on screen: [from] to [to] by [t]. At rest `from == to`; a change of style moves [t] from 0 to 1, so
 * what is drawn is the exact blend of the two looks and never a glass with less alpha. Read in layer and draw lambdas only.
 */
internal class GlassBlend(initial: GlassWeights) {
  val t = Animatable(1f)
  var from by mutableStateOf(initial); private set
  var to by mutableStateOf(initial); private set

  fun regular() = mix(from.regular, to.regular, t.value)
  fun clear() = mix(from.clear, to.clear, t.value)
  fun none() = mix(from.none, to.none, t.value)
  fun now() = GlassWeights(regular(), clear(), none())

  /** At once, to [goal]. */
  suspend fun jump(goal: GlassWeights) { from = goal; to = goal; t.snapTo(1f) }

  /** Starts a blend from what is showing now (a change in the middle of another one is continuous) to [goal]. */
  suspend fun retarget(goal: GlassWeights) {
    val cur = now()
    from = cur; to = cur; t.snapTo(0f)
    to = goal
  }
}

/** What a test (or an accessibility tool) can read from a glass: the style it really shows, the mix, and whether the refraction runs. */
internal data class GlassProbe(val style: GlassStyle, val weights: GlassWeights, val refracts: Boolean, val available: Boolean)

internal val GlassProbeKey = SemanticsPropertyKey<() -> GlassProbe>("GlassProbe")
internal var SemanticsPropertyReceiver.glassProbe by GlassProbeKey

/**
 * `GlassView` of expo-glass-effect, for Compose Desktop: [content] over a copy of what is behind it, blurred and lightly
 * refracted by [effect] (`regular`: wide blur under the translucent scrim; `clear`: narrow blur, no scrim; `none`: the plain
 * translucent surface with a hairline, as the pill was before the glass), the glass border always, an optional [tint]
 * over it, and, when [interactive], growth and a light that follows the pointer. [scheme] forces a theme for the glass and
 * what it holds. The glass is never given an alpha (the page: opacity below 1 makes the effect vanish); [enabled] false
 * halves the border and, unless [dimContent] is false (the caller dims what it must and keeps its text legible), the content, and a fade is a change of style. Without [LocalGlassBackdrop] nothing is behind
 * the glass to copy: it shows its tint and its border.
 */
@Composable
internal fun GlassView(
  modifier: Modifier = Modifier,
  effect: GlassEffect = GlassEffect.Regular,
  tint: Color? = null,
  interactive: Boolean = false,
  scheme: GlassScheme = GlassScheme.Auto,
  enabled: Boolean = true,
  reduced: Boolean = false,
  dimContent: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  if (scheme == GlassScheme.Auto) {
    GlassBody(MaterialTheme.colorScheme, modifier, effect, tint, interactive, enabled, reduced, dimContent, content)
  } else {
    val forced = if (scheme == GlassScheme.Light) TarkasLightColorScheme else TarkasDarkColorScheme
    MaterialTheme(colorScheme = forced, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
      GlassBody(forced, modifier, effect, tint, interactive, enabled, reduced, dimContent, content)
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GlassBody(
  cs: ColorScheme,
  modifier: Modifier,
  effect: GlassEffect,
  tint: Color?,
  interactive: Boolean,
  enabled: Boolean,
  reduced: Boolean,
  dimContent: Boolean,
  content: @Composable BoxScope.() -> Unit,
) {
  val density = LocalDensity.current.density
  val backdrop = LocalGlassBackdrop.current
  val available = LocalGlassAvailable.current
  val reduceTransparency = LocalReduceTransparency.current
  val target = effectiveStyle(effect.style, available, reduceTransparency)

  // The change of style: an exact blend of looks over the style duration, at once when not animated, reduced or forced.
  val blend = remember { GlassBlend(GlassWeights.of(target)) }
  val instant = !effect.animate || reduced || reduceTransparency || !available
  LaunchedEffect(target, instant, effect.durationMs) {
    val goal = GlassWeights.of(target)
    if (instant) blend.jump(goal)
    else {
      if (goal != blend.to) blend.retarget(goal)
      if (blend.t.value < 1f) blend.t.animateTo(1f, tween(effect.durationMs, easing = GlassSpec.Curve))
    }
  }

  val hover = remember { MutableInteractionSource() }
  val hovered by hover.collectIsHoveredAsState()
  val grow by animateFloatAsState(
    if (interactive && enabled && hovered && !reduced) GlassSpec.HoverScale else 1f,
    tween(GlassSpec.HoverMs, easing = GlassSpec.Curve), label = "glassGrow",
  )
  var pointer by remember { mutableStateOf(Offset.Unspecified) }
  var pressed by remember { mutableStateOf(false) }
  val light by animateFloatAsState(
    if (interactive && enabled && hovered) (if (pressed) GlassSpec.GlowPress else GlassSpec.GlowHover) else 0f,
    if (reduced) snap() else tween(GlassSpec.HoverMs, easing = GlassSpec.Curve), label = "glassLight",
  )
  var focused by remember { mutableStateOf(false) }
  val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
  var origin by remember { mutableStateOf(Offset.Unspecified) }

  val regularFx = remember(density, available) { if (available) GlassFx.create(density, GlassSpec.Regular) else null }
  val clearFx = remember(density, available) { if (available) GlassFx.create(density, GlassSpec.Clear) else null }
  val glow = cs.onSurface; val shadow = cs.scrim
  val rim = remember(density, glow, shadow) {
    GlassRim.bake(CompactMath.WidthDp.toFloat(), CompactMath.HeightDp.toFloat(), density, glow, shadow)
  }
  // The pointer's light is the theme's light colour: white over the dark theme, the near-white surface over the light one.
  val lightColor = if (cs.background.luminance() < 0.5f) cs.onSurface else cs.surface
  val spot = remember(density, lightColor) { GlassGlow.bake(GlassSpec.GlowRadiusDp * density, lightColor) }
  val surface = cs.surface; val line = cs.onSurface; val ring = cs.primary
  val w = CompactMath.WidthDp.dp; val h = CompactMath.HeightDp.dp
  val refracts = (regularFx?.refracts ?: false) && (clearFx?.refracts ?: false)

  // The outer box keeps the layout size and the position the glass is measured from; only the inner one grows.
  Box(
    modifier.width(w).height(h).onGloballyPositioned { origin = it.positionInWindow() }
      .hoverable(hover).onFocusChanged { focused = it.hasFocus }
      .semantics { glassProbe = { GlassProbe(target, blend.now(), refracts, available) } },
  ) {
    Box(
      Modifier.fillMaxSize().graphicsLayer { scaleX = grow; scaleY = grow }
        .pointerInput(interactive) {
          if (interactive) awaitPointerEventScope {
            while (true) {
              val e = awaitPointerEvent(PointerEventPass.Initial)
              val c = e.changes.firstOrNull()
              if (c != null) { pointer = c.position; pressed = e.buttons.isPrimaryPressed }
            }
          }
        },
    ) {
      Box(Modifier.fillMaxSize().clip(Shapes.pill), contentAlignment = Alignment.Center) {
        if (backdrop != null) {
          // Bottom to top: regular, clear. The weights come out exact (see regularAlpha).
          if (regularFx != null) BackdropCopy(regularFx.effect, { regularAlpha(blend.regular(), blend.clear()) }, { grow }, { origin }, backdrop)
          if (clearFx != null) BackdropCopy(clearFx.effect, { clearAlpha(blend.clear()) }, { grow }, { origin }, backdrop)
        }
        Box(Modifier.fillMaxSize().drawBehind {
          drawRect(surface, alpha = ShellBackdrop.GLASS * scrimShare(blend.regular(), blend.clear(), blend.none()))
          if (tint != null) drawRect(tint, alpha = GlassSpec.TintAlpha)
        })
        if (interactive) {
          Box(Modifier.fillMaxSize().drawBehind {
            val p = pointer; val a = light
            if (a > 0.01f && p.isSpecified) drawImage(spot, p - Offset(spot.width / 2f, spot.height / 2f), alpha = a)
          })
        }
      }
      val edge = if (enabled) 1f else GlassSpec.DisabledAlpha
      Box(Modifier.fillMaxSize().drawBehind {
        // The glass border, always, and the hairline of the plain surface as the styles hand over to `none`.
        val none = blend.none()
        val m = GlassRim.MarginDp * density
        if (none < 1f) drawImage(rim, Offset(-m, -m), alpha = (1f - none) * edge)
        if (none > 0f) {
          val stroke = Grid.hairline.toPx()
          drawRoundRect(
            line.copy(alpha = HAIRLINE_ALPHA), Offset(stroke / 2f, stroke / 2f), Size(size.width - stroke, size.height - stroke),
            CornerRadius(size.height / 2f), style = Stroke(stroke), alpha = none * edge,
          )
        }
      })
      Box(
        (if (enabled || !dimContent) Modifier else Modifier.graphicsLayer { alpha = GlassSpec.DisabledAlpha }).fillMaxSize(),
        content = content,
      )
      Box(Modifier.fillMaxSize().drawBehind {
        if (focused && keyboard) {
          // A 2 dp ring one hairline outside the pill, in the accent: 13.4:1 on dark, 5.5:1 on light.
          val stroke = (Grid.hairline * 2).toPx(); val gap = Grid.hairline.toPx() + stroke / 2f
          drawRoundRect(
            ring, Offset(-gap, -gap), Size(size.width + 2 * gap, size.height + 2 * gap),
            CornerRadius(size.height / 2f + gap), style = Stroke(stroke),
          )
        }
      })
    }
  }
}

/** Alpha of the plain surface's hairline: the shell's own cards use the same 0.08-0.12 range for a border of text colour. */
private const val HAIRLINE_ALPHA = 0.12f

/**
 * One filtered copy of what is behind the glass, larger than the glass by the pad on every side (what the filter reads
 * must exist) and clipped by the caller. [alpha] is the layer's share of the mix (never the glass's own opacity: it is
 * the weight of this look in the blend of styles), [grow] undoes the growth of the pill so the picture behind stays put.
 */
@Composable
private fun BackdropCopy(effect: RenderEffect?, alpha: () -> Float, grow: () -> Float, origin: () -> Offset, backdrop: com.example.windows.shell.GlassBackdrop) {
  val w = CompactMath.WidthDp.dp; val h = CompactMath.HeightDp.dp
  Box(
    Modifier.requiredSize(w + Grid.u48 * 2, h + Grid.u48 * 2)
      .graphicsLayer { renderEffect = effect; this.alpha = alpha() }
      .drawBehind {
        backdrop.revision
        val o = origin()
        if (o.isSpecified && alpha() > 0f) {
          val pad = Grid.u48.toPx()
          val s = grow()
          // The pill grows, the picture behind it does not: undo the growth around the centre.
          if (s == 1f) drawGlassSources(backdrop, o - Offset(pad, pad))
          else scale(1f / s, 1f / s, pivot = center) { drawGlassSources(backdrop, o - Offset(pad, pad)) }
        }
      },
  )
}
