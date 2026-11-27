package com.example.windows.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.example.windows.base.LocalAppLifecycle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.exp

/**
 * "Seda líquida" (owner, 2026-11-27): a polished black silk in wide horizontal S folds, blue-white light threads on the
 * crests, a bronze reflection at the top left, deep black between the folds. The owner's GLSL ES 3.00 fragment shader,
 * ported to SkSL and drawn by Skia on the GPU behind the whole interface, slowly animated, lifted where the pointer is.
 *
 * Port notes (each one keeps the approved look, none adds to it):
 * - `gl_FragCoord` has y up from the bottom; Skia's coordinate has y down from the top. The shader flips it, so the
 *   bronze stays at the top left and the folds keep their direction. The pointer is passed with y up, as in the web.
 * - `pow(x, 2.0)` of a negative x is undefined in GLSL (drivers may return NaN); the intent is a square, written `x * x`.
 * - The unused `u_dpr`, `u_center` and `rx` are left out.
 * - The dither (±0.5/255) runs before the contrast limit, so it never pushes a pixel past it.
 *
 * Contrast (T.A.L. AA): [limit] softly caps the luminance of the dark silk (tanh knee, hue kept, the marble's method), to
 * the dark marble's ceiling, so secondary text keeps 4.5:1 over its brightest thread ([MAX_L_DARK]). The light theme
 * draws the same silk in pearl tones with a luminance floor ([MIN_L_LIGHT]). `limit = false` is the raw approved look,
 * used only by the evidence renders.
 */
object ShellSilk {
  val Sksl = """
    uniform float2 u_res;
    uniform float u_time;
    uniform float2 u_mouse;
    uniform float u_hover;
    uniform float3 u_c1;   // specular light (blue-white)
    uniform float3 u_c2;   // soft reflection (blue silver)
    uniform float3 u_c3;   // bronze reflection
    uniform float3 u_c4;   // deep black (light theme: the pearl body)
    uniform float u_light; // 0: ceiling (dark), 1: floor (light)
    uniform float u_limit; // luminance ceiling or floor; <= 0: no limit

    float height(float2 p, float t, float2 m) {
      // horizontal bands (~5 crests over the height) bent into an S by x
      float w = 2.1 * sin(p.x * 2.3 + t * 1.0 + 0.8 * sin(p.y * 2.0))
              + 1.2 * sin(p.x * 4.1 - p.y * 2.5 - t * 1.3)
              + 0.6 * sin(p.x * 7.0 + p.y * 3.0 + t * 0.9);
      float h = 0.075 * sin(p.y * 12.5 + w);
      h += 0.035 * sin(p.y * 6.0 - p.x * 1.2 + 0.8 * w - t * 0.7);
      float2 d = p - m;
      h += 0.10 * u_hover * exp(-dot(d, d) * 6.0);
      return h;
    }

    float3 toLin(float3 c) {
      c = clamp(c, 0.0, 1.0);
      return mix(c / 12.92, pow((c + 0.055) / 1.055, float3(2.4)), step(0.04045, c));
    }
    float3 toSrgb(float3 l) {
      l = clamp(l, 0.0, 1.0);
      return mix(l * 12.92, 1.055 * pow(l, float3(1.0 / 2.4)) - 0.055, step(0.0031308, l));
    }
    // tanh for x >= 0 (SkSL has none): 1 - 2 / (e^2x + 1).
    float knee(float x) { return 1.0 - 2.0 / (exp(2.0 * x) + 1.0); }

    half4 main(float2 xy) {
      float2 fc = float2(xy.x, u_res.y - xy.y);
      float asp = u_res.x / u_res.y;
      float2 p = (fc - 0.5 * u_res) / u_res.y;
      float2 m = (u_mouse - 0.5) * float2(asp, 1.0);
      float t = u_time * 0.12;
      p *= asp < 1.0 ? 1.6 : 1.0;
      m *= asp < 1.0 ? 1.6 : 1.0;

      float e = 0.003;
      float h = height(p, t, m);
      float2 g = float2(height(p + float2(e, 0.0), t, m) - h, height(p + float2(0.0, e), t, m) - h) / e;
      float3 n = normalize(float3(-g * 0.8, 1.0));

      float3 v = float3(0.0, 0.0, 1.0);
      float3 r = reflect(-v, n);

      // studio light: horizontal light bands reflected on the surface
      float ry = r.y;
      float b1 = (ry - 0.50) / 0.075;
      float b2 = (ry + 0.35) / 0.10;
      float band1 = exp(-b1 * b1);                                  // bright thread on the crests
      float band2 = exp(-b2 * b2) * 0.6;
      float soft  = smoothstep(0.35, 0.98, ry) * 0.05;              // wide soft sheen
      float spot  = pow(max(dot(r, normalize(float3(-0.35, 0.35, 0.85))), 0.0), 40.0);
      float spot2 = pow(max(dot(r, normalize(float3(0.45, -0.25, 0.85))), 0.0), 60.0);
      float f1 = 1.0 - max(n.z, 0.0);
      float fres = f1 * f1;

      float3 col = u_c4 * (0.6 + 0.9 * smoothstep(-0.7, 0.7, n.y));
      col += u_c2 * soft;
      col += mix(u_c2, u_c1, 0.6) * (0.80 * band1 + 0.30 * band2);
      col += u_c1 * (0.9 * spot + 0.6 * spot2);
      col += u_c1 * 0.08 * fres;
      // bronze reflection at the top left
      float bz = smoothstep(0.1, 0.9, -p.x * 0.9 + p.y * 1.1) * (0.35 + 0.65 * smoothstep(0.0, 0.7, ry));
      col += u_c3 * 0.20 * bz;

      col *= smoothstep(1.7, 0.35, length(p * float2(0.6, 0.9)));
      col = col / (1.0 + col * 0.35);          // rounds the highlights off instead of clipping
      col = pow(max(col, 0.0), float3(0.95));
      col += (fract(sin(dot(fc, float2(12.9898, 78.233))) * 43758.5453) - 0.5) / 255.0; // invisible dither, against banding

      if (u_limit > 0.0) {
        float3 lin = toLin(col);
        float L = dot(lin, float3(0.2126, 0.7152, 0.0722));
        if (u_light < 0.5) {
          if (L > 0.0) lin *= u_limit * knee(L / u_limit) / L;
        } else {
          // Light: the whole picture is lifted into [floor, 1] (affine), so the shading of the folds survives instead of
          // being pressed flat against the floor; every channel >= floor, so L >= floor.
          lin = u_limit + (1.0 - u_limit) * lin;
        }
        col = toSrgb(lin);
      }
      return half4(half3(clamp(col, 0.0, 1.0)), 1.0);
    }
  """.trimIndent()

  /** Built once for the process; null when Skia cannot compile it here (the backdrop then keeps the still marble). */
  val effect: RuntimeEffect? by lazy { runCatching { RuntimeEffect.makeForShader(Sksl) }.getOrNull() }
  val available: Boolean get() = effect != null

  // The owner's colours (dark): #f2f5ff light, #aab4cf blue silver, #8a5f36 bronze, #040406 black.
  val DarkC1 = Color(0xFFF2F5FF); val DarkC2 = Color(0xFFAAB4CF); val DarkC3 = Color(0xFF8A5F36); val DarkC4 = Color(0xFF040406)
  // Light theme: the same silk in pearl. White light, a pale blue silver, a lighter bronze, a mid body the floor lifts.
  val LightC1 = Color(0xFFFFFFFF); val LightC2 = Color(0xFFDDE2EE); val LightC3 = Color(0xFFB08A62); val LightC4 = Color(0xFF8E9096)

  /**
   * Luminance ceiling of the dark silk: the dark marble's 0.019, the same contract. Technical Gray `#8C8C8C` keeps
   * ≥ 4.5:1 on the brightest thread and ≥ 3:1 under the worst cover; Precision White keeps far more. The threads stay
   * visible as a dim sheen, not as the raw look's bright lines (see SilkShaderTest for the measured numbers).
   */
  const val MAX_L_DARK = ShellBackdrop.MARBLE_MAX_L_DARK
  /**
   * Luminance floor of the light silk (the light marble's 0.715): with the cover, `#080808` keeps ≥ 12:1 and `#616160`
   * about 3.7:1 (the BackdropTest contract for secondary text is ≥ 3:1).
   */
  const val MIN_L_LIGHT = ShellBackdrop.MARBLE_MIN_L_LIGHT

  /** The shader moves its time at 0.12 per second (as the web): slow enough that 30 updates a second read as smooth. */
  const val IDLE_FPS = 30
  internal const val IDLE_FRAME_NS = 1_000_000_000L / IDLE_FPS

  /** One configured builder; uniforms change per frame, the compiled effect never. */
  class Painter internal constructor(private val builder: RuntimeShaderBuilder) {
    private var last: Shader? = null
    fun shader(w: Float, h: Float, timeS: Float, mouseX: Float, mouseY: Float, hover: Float, dark: Boolean, limit: Boolean = true): Shader {
      val c = if (dark) arrayOf(DarkC1, DarkC2, DarkC3, DarkC4) else arrayOf(LightC1, LightC2, LightC3, LightC4)
      builder.uniform("u_res", w, h)
      builder.uniform("u_time", timeS)
      builder.uniform("u_mouse", mouseX, mouseY)
      builder.uniform("u_hover", hover)
      builder.uniform("u_c1", c[0].red, c[0].green, c[0].blue)
      builder.uniform("u_c2", c[1].red, c[1].green, c[1].blue)
      builder.uniform("u_c3", c[2].red, c[2].green, c[2].blue)
      builder.uniform("u_c4", c[3].red, c[3].green, c[3].blue)
      builder.uniform("u_light", if (dark) 0f else 1f)
      builder.uniform("u_limit", if (!limit) 0f else if (dark) MAX_L_DARK else MIN_L_LIGHT)
      // Skia objects are reference counted: a display list that still holds the previous shader keeps it alive.
      last?.close()
      return builder.makeShader().also { last = it }
    }
    fun close() { last?.close(); last = null }
  }

  fun painter(): Painter? = effect?.let { Painter(RuntimeShaderBuilder(it)) }

  /** The silk as opaque ARGB pixels, drawn by Skia's CPU raster (tests and evidence; the app draws it on the GPU). */
  fun renderPixels(w: Int, h: Int, timeS: Float, dark: Boolean, limit: Boolean = true,
                   mouseX: Float = 0.5f, mouseY: Float = 0.5f, hover: Float = 0f): IntArray {
    val p = painter() ?: error("SkSL did not compile")
    val surface = Surface.makeRasterN32Premul(w, h)
    val paint = Paint().apply { shader = p.shader(w.toFloat(), h.toFloat(), timeS, mouseX, mouseY, hover, dark, limit) }
    surface.canvas.drawRect(Rect.makeWH(w.toFloat(), h.toFloat()), paint)
    val bmp = org.jetbrains.skia.Bitmap().apply { allocN32Pixels(w, h) }
    surface.readPixels(bmp, 0, 0)
    val bytes = bmp.readPixels()!!
    p.close(); paint.close(); surface.close()
    // N32 on Windows is BGRA in memory.
    return IntArray(w * h) { i ->
      val b = bytes[i * 4].toInt() and 0xFF; val g = bytes[i * 4 + 1].toInt() and 0xFF; val r = bytes[i * 4 + 2].toInt() and 0xFF
      (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
  }

  // ---- Pointer: the surface lifts under it (u_mouse 0..1 with y up, u_hover 0..1), both smoothed ----

  /** Where the pointer is, set by the shell from every move it sees (never consumed). */
  object Pointer {
    @Volatile internal var targetX = 0.5f
    @Volatile internal var targetY = 0.5f
    @Volatile internal var targetHover = 0f
    internal var x = 0.5f; internal var y = 0.5f; internal var hover = 0f

    /** [fx], [fy]: the pointer as fractions of the window, y down (as Compose gives it). */
    fun at(fx: Float, fy: Float) { targetX = fx.coerceIn(0f, 1f); targetY = 1f - fy.coerceIn(0f, 1f); targetHover = 1f }
    fun left() { targetHover = 0f }
    fun reset() { targetX = 0.5f; targetY = 0.5f; targetHover = 0f; x = 0.5f; y = 0.5f; hover = 0f }

    /** Moves the smoothed values toward the targets (time constants 0.12 s position, 0.25 s lift); true while they still move. */
    internal fun step(dt: Float): Boolean {
      val kp = 1f - exp(-dt / 0.12f); val kh = 1f - exp(-dt / 0.25f)
      val nx = x + (targetX - x) * kp; val ny = y + (targetY - y) * kp; val nh = hover + (targetHover - hover) * kh
      val moving = abs(nx - x) > 1e-4f || abs(ny - y) > 1e-4f || abs(nh - hover) > 1e-4f
      x = nx; y = ny; hover = nh
      return moving
    }
  }

  // ---- Engineering: frame pacing of the silk, logged with TARKAS_SILK_STATS=1 ----

  object Stats {
    @Volatile var frames = 0L; private set
    @Volatile var meanFrameMs = 0.0; private set
    @Volatile var maxFrameMs = 0.0; private set
    @Volatile var meanDrawMs = 0.0; private set
    @Volatile var maxDrawMs = 0.0; private set
    private var sumFrame = 0.0; private var sumDraw = 0.0; private var draws = 0L; private var windowStart = 0L
    private val log = System.getenv("TARKAS_SILK_STATS") == "1"

    internal fun frame(intervalNs: Long, now: Long) {
      val ms = intervalNs / 1e6
      frames++; sumFrame += ms; meanFrameMs = sumFrame / frames; if (ms > maxFrameMs) maxFrameMs = ms
      if (windowStart == 0L) windowStart = now
      if (log && now - windowStart >= 10_000_000_000L) {
        System.err.println("silk frames=$frames meanFrame=%.2fms maxFrame=%.2fms meanDraw=%.3fms maxDraw=%.3fms"
          .format(meanFrameMs, maxFrameMs, meanDrawMs, maxDrawMs))
        windowStart = now
      }
    }
    internal fun draw(ns: Long) {
      val ms = ns / 1e6; draws++; sumDraw += ms; meanDrawMs = sumDraw / draws; if (ms > maxDrawMs) maxDrawMs = ms
    }
  }
}

/**
 * The silk filling its box. It moves while the window is in front and stops (on its last frame) when it is not, or when
 * the user reduces motion. Time advances in the frame loop and is read only in the draw lambda: no recomposition per frame.
 */
@Composable
internal fun SilkLayer(dark: Boolean, still: Boolean, modifier: Modifier = Modifier) {
  val painter = remember { ShellSilk.painter() } ?: return
  DisposableEffect(painter) { onDispose { painter.close() } }
  val resumed by LocalAppLifecycle.current.resumed.collectAsState()
  val time = remember { mutableFloatStateOf(0f) }
  val px = remember { mutableFloatStateOf(0.5f) }
  val py = remember { mutableFloatStateOf(0.5f) }
  val hover = remember { mutableFloatStateOf(0f) }
  LaunchedEffect(still, resumed) {
    if (still) { hover.floatValue = 0f; return@LaunchedEffect }
    if (!resumed) return@LaunchedEffect
    val p = ShellSilk.Pointer
    var last = -1L
    while (true) {
      // Asking for a frame makes the window draw, so the loop asks only as often as the silk needs: every vsync while
      // the lift follows the pointer, IDLE_FPS otherwise (it sleeps in between, the window stays still).
      val moving = withInfiniteAnimationFrameNanos { now ->
        val gap = if (last < 0) 0L else now - last
        if (gap > 0) ShellSilk.Stats.frame(gap, now)
        last = now
        val dt = (gap / 1e9f).coerceIn(0f, 0.25f)
        val m = p.step(dt)
        time.floatValue = time.floatValue + dt
        px.floatValue = p.x; py.floatValue = p.y; hover.floatValue = p.hover
        m
      }
      if (!moving) delay(ShellSilk.IDLE_FRAME_NS / 1_000_000L - 2)
    }
  }
  Spacer(modifier.fillMaxSize().drawBehind {
    val t0 = System.nanoTime()
    val sh = painter.shader(size.width, size.height, time.floatValue, px.floatValue, py.floatValue, hover.floatValue, dark)
    drawRect(ShaderBrush(sh))
    ShellSilk.Stats.draw(System.nanoTime() - t0)
  })
}
