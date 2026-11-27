package com.example.windows.shell

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The window background (owner, 2026-11-18 and Home v3 D7, 2026-11-27): a still marble of green and teal with a soft
 * cyan glow, and over it the current cover, blurred, tinting the wash. The player sets [set] when the track changes.
 * Every layer is baked once into a small bitmap and drawn as a scaled texture: a still backdrop costs nothing per frame.
 */
object ShellBackdrop {
  class Source(val art: ImageBitmap?, val tint: Color?)
  private val _source = MutableStateFlow(Source(null, null))
  val source: StateFlow<Source> = _source.asStateFlow()
  /** [art]: the track's real cover, or null. [tint]: its dominant colour (or `Track.colorHex`), or null for none. */
  fun set(art: ImageBitmap?, tint: Color?) { _source.value = Source(art, tint) }
  fun clear() = set(null, null)

  /** Long side of the baked backdrop, in pixels. Scaled up with bilinear filtering it reads as one soft wash. */
  const val SOFT_PX = 96
  /** Blur sigma at [SOFT_PX]: 5% of the side, the same share a 96 dp blur had over a 1152 dp window (about 83 px of 1728). */
  const val SOFT_SIGMA = 4.8f

  /**
   * The cover downscaled to [SOFT_PX] and blurred once (Skia, CPU, under 1 ms). Called off the UI thread per cover.
   * Edges clamp, like `Modifier.blur`'s rectangle treatment, so the wash has no dark rim.
   */
  fun soften(art: ImageBitmap): ImageBitmap {
    val src = org.jetbrains.skia.Image.makeFromBitmap(art.asSkiaBitmap())
    val scale = SOFT_PX.toFloat() / maxOf(src.width, src.height)
    val w = maxOf(1, (src.width * scale).toInt()); val h = maxOf(1, (src.height * scale).toInt())
    val small = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
    small.canvas.drawImageRect(src, org.jetbrains.skia.Rect.makeWH(src.width.toFloat(), src.height.toFloat()),
      org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()), org.jetbrains.skia.SamplingMode.LINEAR, null, true)
    return blurred(small.makeImageSnapshot(), SOFT_SIGMA)
  }

  private fun blurred(img: org.jetbrains.skia.Image, sigma: Float): ImageBitmap {
    val out = org.jetbrains.skia.Surface.makeRasterN32Premul(img.width, img.height)
    val paint = org.jetbrains.skia.Paint().apply {
      imageFilter = org.jetbrains.skia.ImageFilter.makeBlur(sigma, sigma, org.jetbrains.skia.FilterTileMode.CLAMP)
    }
    out.canvas.drawImage(img, 0f, 0f, paint)
    return out.makeImageSnapshot().toComposeImageBitmap()
  }

  // ---- Marble (D7: an exception for the Windows app, owner 2026-11-27) ----

  /** Marble size in pixels: 16:10, twice [SOFT_PX] so the veins survive the stretch to a 2560 px window. */
  const val MARBLE_W = SOFT_PX * 2
  const val MARBLE_H = 120
  /** Blur of the baked marble: the veins stay, the pixel grid does not. */
  const val MARBLE_SIGMA = 3f
  private const val SEED = 0x7A4C

  // Dark: Absolute Black through a deep green to teal veins; the cyan of the accent only as a faint glow.
  val MarbleDarkBase = Color(0xFF080808)
  val MarbleDarkGreen = Color(0xFF0A2A20)
  val MarbleDarkTeal = Color(0xFF0C3A3C)
  // Light: the paper of the theme, a faint green body and teal veins.
  val MarbleLightBase = Color(0xFFE9E9E7)
  val MarbleLightGreen = Color(0xFFD5E4DB)
  val MarbleLightTeal = Color(0xFFC4E0DF)
  val MarbleGlow = Color(0xFF7EE3EA)
  /** Centre of the glow, as fractions of the window, and its spread. */
  const val GLOW_X = 0.78f
  const val GLOW_Y = 0.18f
  const val GLOW_SPREAD = 0.10f

  /**
   * Luminance ceiling of the dark marble: 0.019 keeps Technical Gray `#8C8C8C` at ≥ 4.5:1 on its brightest pixel
   * ((0.262 + 0.05) / (0.019 + 0.05) = 4.52), so the marble alone never costs secondary text its AA contrast.
   */
  const val MARBLE_MAX_L_DARK = 0.019f
  /** Luminance floor of the light marble: 0.715 keeps the light gray `#616160` at ≥ 4.5:1 ((0.715 + 0.05) / (0.122 + 0.05)). */
  const val MARBLE_MIN_L_LIGHT = 0.715f

  private val marbles = arrayOfNulls<ImageBitmap>(2)

  /** The marble for the theme, baked on first use (a few ms on the CPU) and kept for the process. */
  fun marble(dark: Boolean): ImageBitmap = synchronized(marbles) {
    marbles[if (dark) 1 else 0] ?: bakeMarble(dark).also { marbles[if (dark) 1 else 0] = it }
  }
  fun cachedMarble(dark: Boolean): ImageBitmap? = synchronized(marbles) { marbles[if (dark) 1 else 0] }

  private fun bakeMarble(dark: Boolean): ImageBitmap {
    val px = marblePixels(dark)
    val bytes = ByteArray(px.size * 4)
    for (i in px.indices) {
      val c = px[i]
      bytes[i * 4] = (c and 0xFF).toByte(); bytes[i * 4 + 1] = (c shr 8 and 0xFF).toByte()
      bytes[i * 4 + 2] = (c shr 16 and 0xFF).toByte(); bytes[i * 4 + 3] = 0xFF.toByte()
    }
    val bmp = org.jetbrains.skia.Bitmap()
    bmp.allocPixels(org.jetbrains.skia.ImageInfo(MARBLE_W, MARBLE_H, org.jetbrains.skia.ColorType.BGRA_8888,
      org.jetbrains.skia.ColorAlphaType.OPAQUE))
    bmp.installPixels(bytes)
    return blurred(org.jetbrains.skia.Image.makeFromBitmap(bmp), MARBLE_SIGMA)
  }

  /**
   * The marble before the blur, as opaque ARGB, row by row. Domain-warped value noise (Inigo Quilez's warp: fbm of
   * the point pushed by two other fbm fields) gives the body; a sine of the warped field gives the veins. Deterministic:
   * the same pixels on every run and machine.
   */
  fun marblePixels(dark: Boolean): IntArray {
    val out = IntArray(MARBLE_W * MARBLE_H)
    val base = if (dark) MarbleDarkBase else MarbleLightBase
    val body = if (dark) MarbleDarkGreen else MarbleLightGreen
    val vein = if (dark) MarbleDarkTeal else MarbleLightTeal
    val glowShare = if (dark) 0.10f else 0.08f
    for (y in 0 until MARBLE_H) for (x in 0 until MARBLE_W) {
      val u = x / MARBLE_H.toFloat() * 1.5f; val v = y / MARBLE_H.toFloat() * 1.5f
      val q1 = fbm(u, v); val q2 = fbm(u + 5.2f, v + 1.3f)
      val r = fbm(u + 3f * q1, v + 3f * q2)
      val veinT = (1f - abs(sin(((u * 1.2f + v * 0.6f + 3f * r) * PI).toFloat()))).pow(3)
      val nx = x / (MARBLE_W - 1f) - GLOW_X; val ny = y / (MARBLE_H - 1f) - GLOW_Y
      val glow = exp(-(nx * nx + ny * ny) / GLOW_SPREAD)
      var c = mix(base, body, smooth(0.30f, 0.75f, r))
      c = mix(c, vein, veinT * 0.8f)
      c = mix(c, MarbleGlow, glow * glowShare + veinT * 0.03f)
      c = if (dark) capLuminance(c, MARBLE_MAX_L_DARK) else floorLuminance(c, MARBLE_MIN_L_LIGHT)
      out[y * MARBLE_W + x] = (0xFF shl 24) or (byte(c.red) shl 16) or (byte(c.green) shl 8) or byte(c.blue)
    }
    return out
  }

  /** Soft knee: luminance L becomes max * tanh(L / max), hue kept, so bright veins round off instead of clipping flat. */
  internal fun capLuminance(c: Color, max: Float): Color {
    val l = c.luminance(); if (l <= 0f) return c
    val k = (max * tanh(l / max)) / l
    return Color(lin2s(s2lin(c.red) * k), lin2s(s2lin(c.green) * k), lin2s(s2lin(c.blue) * k))
  }

  /** The mirror of [capLuminance] for the light marble: the distance to white is softly capped. */
  internal fun floorLuminance(c: Color, min: Float): Color {
    val d = 1f - c.luminance(); val dMax = 1f - min; if (d <= 0f) return c
    val k = (dMax * tanh(d / dMax)) / d
    fun lift(s: Float) = lin2s(1f - (1f - s2lin(s)) * k)
    return Color(lift(c.red), lift(c.green), lift(c.blue))
  }

  private fun s2lin(s: Float) = if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
  private fun lin2s(l: Float) = (if (l <= 0.0031308f) l * 12.92f else 1.055f * l.coerceIn(0f, 1f).pow(1f / 2.4f) - 0.055f).coerceIn(0f, 1f)
  private fun byte(f: Float) = (f.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
  private fun mix(a: Color, b: Color, t: Float): Color {
    val s = t.coerceIn(0f, 1f)
    return Color(a.red + (b.red - a.red) * s, a.green + (b.green - a.green) * s, a.blue + (b.blue - a.blue) * s)
  }
  private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

  private fun hash(x: Int, y: Int): Float {
    var h = x * 374761393 + y * 668265263 + SEED * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777215f
  }

  private fun noise(x: Float, y: Float): Float {
    val xi = floor(x).toInt(); val yi = floor(y).toInt()
    val fx = x - xi; val fy = y - yi
    val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
    val a = hash(xi, yi); val b = hash(xi + 1, yi); val c = hash(xi, yi + 1); val d = hash(xi + 1, yi + 1)
    return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy
  }

  /** Three octaves of [noise] (a fourth reads as blocks once stretched), normalised to 0..1. */
  private fun fbm(x: Float, y: Float): Float {
    var amp = 0.5f; var f = 1f; var sum = 0f
    repeat(3) { sum += amp * noise(x * f, y * f); f *= 2f; amp *= 0.5f }
    return sum / 0.875f
  }

  // ---- Cover over the marble ----

  /** Share of the cover layer (tint + blurred art) over the marble. Dark themes keep more of the cover than light ones. */
  fun coverShare(background: Color): Float = if (background.luminance() < 0.5f) COVER_DARK else COVER_LIGHT
  const val COVER_DARK = 0.12f
  const val COVER_LIGHT = 0.10f
  /** Alpha of the glass surfaces (side pane, corner, transport) over the backdrop. */
  const val GLASS = 0.55f

  /**
   * Worst-case text contrast: [fg] over [under] (the theme background, or the marble's extreme pixel) with a cover of
   * pure white (dark theme) or pure black (light theme) at [coverShare] on top: the brightest/darkest a cover can be.
   */
  fun worstContrast(fg: Color, background: Color, under: Color = background): Double {
    val a = coverShare(background)
    val extreme = if (background.luminance() < 0.5f) Color.White else Color.Black
    fun mix(x: Float, y: Float) = x * (1 - a) + y * a
    val bg = Color(mix(under.red, extreme.red), mix(under.green, extreme.green), mix(under.blue, extreme.blue))
    return contrast(fg, bg)
  }

  fun contrast(a: Color, b: Color): Double {
    val l1 = a.luminance().toDouble(); val l2 = b.luminance().toDouble()
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
  }
}

/**
 * The liquid silk (SilkBackdrop, owner 2026-11-27), then the cover's tint and blurred art at [ShellBackdrop.coverShare],
 * filling its box. [still] (reduce motion) holds the silk on one frame. Where Skia cannot compile the silk, the still
 * marble takes its place.
 */
@Composable
fun Backdrop(modifier: Modifier = Modifier, still: Boolean = false) {
  val c = MaterialTheme.colorScheme
  val dark = c.background.luminance() < 0.5f
  val src by ShellBackdrop.source.collectAsState()
  val tint by animateColorAsState(src.tint ?: Color.Transparent, tween(300))
  val silk = ShellSilk.available
  val marble by produceState(if (silk) null else ShellBackdrop.cachedMarble(dark), dark, silk) {
    if (!silk) value = withContext(Dispatchers.Default) { ShellBackdrop.marble(dark) }
  }
  Box(modifier.fillMaxSize().background(c.background)) {
    if (silk) SilkLayer(dark, still)
    else marble?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low) }
    // The cover layer is one group at a fixed share, so text contrast has a single worst case (BackdropTest).
    Box(Modifier.fillMaxSize().graphicsLayer {
      alpha = ShellBackdrop.coverShare(c.background); compositingStrategy = CompositingStrategy.Offscreen
    }) {
      Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = tint.alpha * 0.6f), Color.Transparent))))
      // The blur is baked once per cover into a small bitmap (soften); drawing it is a plain scaled texture.
      // Before 2.1.6 a live blur(96.dp) over the whole window ran again on every repaint (owner report: laggy, heavy).
      val soft by produceState<ImageBitmap?>(null, src.art) {
        val art = src.art
        value = if (art == null) null else withContext(Dispatchers.Default) { ShellBackdrop.soften(art) }
      }
      Crossfade(soft, animationSpec = tween(300)) { art ->
        if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
      }
    }
  }
}

/** Glass surface colour: the surface, translucent over the window background (silk or marble) and cover. */
@Composable
fun glass(): Color = MaterialTheme.colorScheme.surface.copy(alpha = ShellBackdrop.GLASS)
