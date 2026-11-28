package com.example.windows.biblioteca

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/*
 * The cover art of the Glass Folder, ported letter by letter from the original generator (the React spec of the owner's
 * request): a seeded gradient noise folded twice, read as the height of a liquid surface, lit and tinted from a palette.
 * Hex colours live only here, in the recipes: they are pigments of the art, not interface colours.
 *
 * Numeric contract with the original (so the same recipe, seed and size give the same pixels): the noise tables and the
 * field grids are 32-bit floats (`Float32Array` there, `FloatArray` here), every other value is a double, the pixel store
 * clamps and rounds half to even (`Uint8ClampedArray`), and the integer maths wraps at 32 bits (`Math.imul`, `>>> 0`).
 * `StrictMath` is the fdlibm port V8 uses for sin, cos and pow, so the curves agree to the last bit.
 */

/** How the field is read: `streak` flattens the coordinates on one axis (a long exposure), `marble` keeps the folds. */
enum class ArtStyle { Streak, Marble }

/** One recipe of the art. The defaults are the original's (`?? 0.7`, `?? 3`, `?? 0.4`, `?? 0`). */
data class ArtRecipe(
  val style: ArtStyle,
  val colors: List<String>,
  val scale: Double = 0.7,
  val warp: Double = 3.0,
  val angle: Double = 0.0,
  val gloss: Double = 0.4,
  val veins: Double = 0.0,
)

/** The five named recipes of the original. */
enum class ArtPreset(val recipe: ArtRecipe) {
  /** A long exposure: pink, oxblood and steel pulled across the frame. */
  Aurora(
    ArtRecipe(
      ArtStyle.Streak,
      listOf("#f4efe9", "#e9c2b8", "#c2433f", "#2a1e2a", "#22395e", "#6f97c2", "#e5edf4"),
      scale = 0.7, warp = 1.4, angle = -0.62, gloss = 0.18, veins = 0.0,
    ),
  ),

  /** Red ink folding into near-black teal water. */
  Ember(
    ArtRecipe(
      ArtStyle.Marble,
      listOf("#07090c", "#16252c", "#3d5f68", "#1a1416", "#a3201a", "#ef4a2b", "#ff8a5c"),
      scale = 0.62, warp = 3.2, angle = -0.5, gloss = 0.55, veins = 0.6,
    ),
  ),

  /** Plum and rust, swirling. */
  Dusk(
    ArtRecipe(
      ArtStyle.Marble,
      listOf("#120c18", "#3e2850", "#8f6c9c", "#2b1a24", "#b3502f", "#eab08a", "#fbe7d2"),
      scale = 0.75, warp = 3.6, angle = 0.8, gloss = 0.5, veins = 0.5,
    ),
  ),

  /** Cold, pale, nearly white. */
  Glacier(
    ArtRecipe(
      ArtStyle.Streak,
      listOf("#f7f8fa", "#d9e2ea", "#9fb4c7", "#e8edf2", "#bccbd8", "#ffffff"),
      scale = 0.7, warp = 1.6, angle = 0.35, gloss = 0.25, veins = 0.0,
    ),
  ),

  /** Moss and brass. */
  Verdant(
    ArtRecipe(
      ArtStyle.Marble,
      listOf("#070b08", "#173024", "#4f7a5a", "#141812", "#a7843a", "#e9cf87", "#fbf3d6"),
      scale = 0.7, warp = 3.4, angle = 2.1, gloss = 0.5, veins = 0.5,
    ),
  ),
}

/** `#rgb` or `#rrggbb` to a triple. What cannot be read comes back as mid grey. */
internal fun hexToRgb(hex: String): IntArray {
  var h = hex.trim().removePrefix("#")
  if (h.length == 3) h = "" + h[0] + h[0] + h[1] + h[1] + h[2] + h[2]
  val ok = h.length == 6 && h.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
  val n = if (ok) h.toInt(16) else 0x808080
  return intArrayOf((n shr 16) and 255, (n shr 8) and 255, n and 255)
}

/** A small, fast, seeded pseudo-random generator (mulberry32). */
internal class Rng(seed: Long) {
  private var a: Int = seed.toInt()

  /** The next value in `[0, 1)`. */
  fun next(): Double {
    a += 0x6d2b79f5
    var t = a
    t = (t xor (t ushr 15)) * (t or 1)
    t = t xor (t + (t xor (t ushr 7)) * (t or 61))
    return (t xor (t ushr 14)).toUInt().toLong() / 4294967296.0
  }
}

/** A small, stable integer for a text, so the art of a file follows its name (FNV-1a over the UTF-16 units). */
internal fun hashString(s: String): Long {
  var h = 2166136261L.toInt()
  for (ch in s) h = (h xor ch.code) * 16777619
  return h.toUInt().toLong()
}

/** Seeded gradient noise summed in fractal octaves with a rotation between each one. */
internal class Fbm(seed: Long) {
  private val perm = IntArray(512)
  private val grad = FloatArray(512)

  init {
    val r = Rng(seed)
    val p = IntArray(256) { it }
    for (i in 255 downTo 1) {
      val j = Math.floor(r.next() * (i + 1)).toInt()
      val t = p[i]
      p[i] = p[j]
      p[j] = t
    }
    for (i in 0 until 512) perm[i] = p[i and 255]
    for (i in 0 until 256) {
      val a = r.next() * Math.PI * 2
      grad[i * 2] = StrictMath.cos(a).toFloat()
      grad[i * 2 + 1] = StrictMath.sin(a).toFloat()
    }
  }

  private fun noise(x: Double, y: Double): Double {
    val fx = Math.floor(x)
    val fy = Math.floor(y)
    val xf = x - fx
    val yf = y - fy
    val xi = fx.toLong().toInt() and 255
    val yi = fy.toLong().toInt() and 255
    val h00 = perm[xi + perm[yi]] * 2
    val h10 = perm[xi + 1 + perm[yi]] * 2
    val h01 = perm[xi + perm[yi + 1]] * 2
    val h11 = perm[xi + 1 + perm[yi + 1]] * 2
    val d00 = grad[h00] * xf + grad[h00 + 1] * yf
    val d10 = grad[h10] * (xf - 1) + grad[h10 + 1] * yf
    val d01 = grad[h01] * xf + grad[h01 + 1] * (yf - 1)
    val d11 = grad[h11] * (xf - 1) + grad[h11 + 1] * (yf - 1)
    val u = xf * xf * xf * (xf * (xf * 6 - 15) + 10)
    val v = yf * yf * yf * (yf * (yf * 6 - 15) + 10)
    return d00 + u * (d10 - d00) + v * (d01 - d00 + u * (d11 - d01 - d10 + d00))
  }

  fun sample(x0: Double, y0: Double, octaves: Int): Double {
    var x = x0
    var y = y0
    var sum = 0.0
    var amp = 0.5
    for (i in 0 until octaves) {
      sum += amp * noise(x, y)
      val nx = 1.6 * x + 1.2 * y + 7.1
      y = -1.2 * x + 1.6 * y + 3.3
      x = nx
      amp *= 0.5
    }
    return sum
  }
}

/**
 * The art as a job that can spread over several calls: [step] paints up to `rows` more field rows and answers true when the
 * image is ready. The field has its domain folded twice (each fold feeds the next), then is normalised by its own
 * percentiles so every seed covers the whole palette; the field is the height of a liquid surface, its slope gives a
 * normal, the normal gives the diffuse light and a tight specular, and fine streaks run along the contour lines.
 */
internal class ArtJob(width: Int, height: Int, private val recipe: ArtRecipe, seed: Long) {
  val width: Int = maxOf(1, width)
  val height: Int = maxOf(1, height)

  /** RGBA, 4 bytes a pixel, opaque. */
  val pixels = ByteArray(this.width * this.height * 4)

  private val w = this.width
  private val h = this.height
  private val stops: List<Pair<Double, IntArray>>
  private val fbm = Fbm(seed * 977 + 13)
  private val ox: Double
  private val oy: Double
  private val ca: Double
  private val sa: Double
  private val m = minOf(w, h)
  private val gw = (w + 1) / 2 + 3
  private val gh = (h + 1) / 2 + 3
  private val g = FloatArray(gw * gh)
  private val gq = FloatArray(gw * gh)
  private val f = FloatArray(w * h)
  private val q = FloatArray(w * h)
  private var row = 0
  private val streak = recipe.style == ArtStyle.Streak

  init {
    val colors = if (recipe.colors.size >= 2) recipe.colors else listOf("#111111", "#eeeeee")
    stops = colors.mapIndexed { i, c -> (i.toDouble() / (colors.size - 1)) to hexToRgb(c) }
    val r = Rng(seed + 1)
    ox = r.next() * 50
    oy = r.next() * 50
    val angle = recipe.angle + (r.next() - 0.5) * 0.5
    ca = StrictMath.cos(angle)
    sa = StrictMath.sin(angle)
  }

  private fun field(gy: Int) {
    for (gx in 0 until gw) {
      val u = ((gx - 1) * 2 - w / 2.0) / m
      val v = ((gy - 1) * 2 - h / 2.0) / m
      var px = (u * ca - v * sa) * recipe.scale
      var py = (u * sa + v * ca) * recipe.scale
      if (streak) {
        px *= 0.14
        py *= 2.2
      }
      px += ox
      py += oy
      val qx = fbm.sample(px, py, 3)
      val qy = fbm.sample(px + 5.2, py + 1.3, 3)
      val rx = fbm.sample(px + recipe.warp * qx + 1.7, py + recipe.warp * qy + 9.2, 3)
      val ry = fbm.sample(px + recipe.warp * qx + 8.3, py + recipe.warp * qy + 2.8, 3)
      g[gy * gw + gx] = fbm.sample(px + recipe.warp * rx, py + recipe.warp * ry, 4).toFloat()
      gq[gy * gw + gx] = StrictMath.sqrt(qx * qx + qy * qy).toFloat()
    }
  }

  /** Catmull-Rom at the middle of the segment, which keeps the slope continuous for the light. */
  private fun cr(a: Float, b: Float, c: Float, d: Float): Float =
    ((-a.toDouble() + 9.0 * b + 9.0 * c - d) / 16.0).toFloat()

  private fun upsample() {
    val t = FloatArray(w * gh)
    for (gy in 0 until gh) {
      val rr = gy * gw
      for (x in 0 until w) {
        val c = (x shr 1) + 1
        t[gy * w + x] = if (x and 1 == 1) cr(g[rr + c - 1], g[rr + c], g[rr + c + 1], g[rr + c + 2]) else g[rr + c]
      }
    }
    for (y in 0 until h) {
      val c = (y shr 1) + 1
      for (x in 0 until w) {
        f[y * w + x] =
          if (y and 1 == 1) cr(t[(c - 1) * w + x], t[c * w + x], t[(c + 1) * w + x], t[(c + 2) * w + x]) else t[c * w + x]
        val gx = (x shr 1) + 1
        val q0 = gq[c * gw + gx].toDouble()
        val qx = if (x and 1 == 1) (q0 + gq[c * gw + gx + 1]) / 2 else q0
        val q1 =
          if (y and 1 == 1) {
            if (x and 1 == 1) (gq[(c + 1) * gw + gx].toDouble() + gq[(c + 1) * gw + gx + 1]) / 2
            else gq[(c + 1) * gw + gx].toDouble()
          } else qx
        q[y * w + x] = ((qx + q1) / 2).toFloat()
      }
    }
  }

  /** `Uint8ClampedArray` store: clamp to 0..255, round half to even, NaN is 0. */
  private fun store(i: Int, v: Double) {
    val c = if (v.isNaN()) 0.0 else if (v < 0) 0.0 else if (v > 255) 255.0 else v
    pixels[i] = Math.rint(c).toInt().toByte()
  }

  private fun colorize() {
    // percentiles of a sample, not of a full sort: cheap and just as steady
    val stride = maxOf(1, Math.floor(f.size / 4096.0).toInt())
    val sample = ArrayList<Float>()
    var i0 = 0
    while (i0 < f.size) {
      sample.add(f[i0])
      i0 += stride
    }
    sample.sort()
    val lo = sample[Math.floor(sample.size * 0.02).toInt()].toDouble()
    val hi = sample[Math.floor(sample.size * 0.98).toInt()].toDouble()
    val span = hi - lo
    val k = 1.0 / (if (span == 0.0) 1.0 else span)
    val lx = -0.55
    val ly = -0.65
    val lz = 0.52
    val slope = k * m * 0.012
    for (y in 0 until h) {
      for (x in 0 until w) {
        val i = y * w + x
        var t = (f[i] - lo) * k
        t = if (t < 0) 0.0 else if (t > 1) 1.0 else t
        var j = 1
        while (j < stops.size - 1 && stops[j].first < t) j++
        val a = stops[j - 1]
        val b = stops[j]
        val den = b.first - a.first
        var s = (t - a.first) / (if (den == 0.0) 1.0 else den)
        s = if (s < 0) 0.0 else if (s > 1) 1.0 else s
        s = s * s * (3 - 2 * s)
        val gx = (f[if (x < w - 1) i + 1 else i].toDouble() - f[if (x > 0) i - 1 else i]) * slope
        val gy = (f[if (y < h - 1) i + w else i].toDouble() - f[if (y > 0) i - w else i]) * slope
        val nd = (-gx * lx - gy * ly + lz) / StrictMath.sqrt(gx * gx + gy * gy + 1)
        val spec = StrictMath.pow(if (nd > 0) nd else 0.0, 24.0) * recipe.gloss * 255
        val qi = q[i].toDouble()
        val silk = 1 + 0.035 * StrictMath.sin(t * 150 + qi * 24)
        val vein = recipe.veins * maxOf(0.0, 1 - Math.abs(qi - 0.42) * 14) * 0.6
        val lit = (0.82 + 0.28 * nd) * silk * (1 - vein)
        store(i * 4, (a.second[0] + (b.second[0] - a.second[0]) * s) * lit + spec)
        store(i * 4 + 1, (a.second[1] + (b.second[1] - a.second[1]) * s) * lit + spec)
        store(i * 4 + 2, (a.second[2] + (b.second[2] - a.second[2]) * s) * lit + spec)
        pixels[i * 4 + 3] = 255.toByte()
      }
    }
  }

  /** Paints up to [rows] more field rows. True when the image is ready. */
  fun step(rows: Int): Boolean {
    if (row >= gh) return true
    val end = minOf(gh, row + maxOf(1, rows))
    while (row < end) field(row++)
    if (row < gh) return false
    upsample()
    colorize()
    return true
  }
}

/** Where a sheet sits in the fan: -1 to 1 across the visible stack, 0 when there is only one. */
internal fun fanSlot(index: Int, count: Int): Double =
  if (count <= 1) 0.0 else index.toDouble() / (count - 1) * 2 - 1

/**
 * The painter of the page: every folder asks for its art here. The work runs on [Dispatchers.Default] (never the UI
 * thread), two jobs at a time at most, and the same recipe, seed and size is painted once and shared (a cache of the
 * finished bitmaps plus the paints in flight). The caller never waits for a frame: it shows the placeholder and swaps
 * in the bitmap when [paint] answers.
 */
internal object GlassArtPainter {
  /** What identifies one picture. */
  data class Key(val recipe: ArtRecipe, val seed: Long, val width: Int, val height: Int)

  private const val MAX_CACHED = 48

  /** Rows of the field per slice of work, so a big image never holds a worker for long. */
  private const val SLICE_ROWS = 8

  @OptIn(ExperimentalCoroutinesApi::class)
  private val workers: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)

  private val done = object : LinkedHashMap<Key, ImageBitmap>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, ImageBitmap>?) = size > MAX_CACHED
  }
  private val inFlight = ConcurrentHashMap<Key, CompletableDeferred<ImageBitmap>>()

  /** A finished picture, or null when it has not been painted yet. */
  fun cached(key: Key): ImageBitmap? = synchronized(done) { done[key] }

  /** Paints [key] (or joins the paint already running for it) and answers with the bitmap. */
  suspend fun paint(key: Key): ImageBitmap {
    cached(key)?.let { return it }
    val mine = CompletableDeferred<ImageBitmap>()
    val running = inFlight.putIfAbsent(key, mine)
    if (running != null) return running.await()
    try {
      val bitmap = withContext(workers) { render(key) }
      synchronized(done) { done[key] = bitmap }
      mine.complete(bitmap)
      return bitmap
    } catch (t: Throwable) {
      mine.completeExceptionally(t)
      throw t
    } finally {
      inFlight.remove(key)
    }
  }

  /** The bytes of [key] as RGBA, synchronously. Tests and the bitmap path share this. */
  internal fun pixels(key: Key): ByteArray {
    val job = ArtJob(key.width, key.height, key.recipe, key.seed)
    while (!job.step(SLICE_ROWS)) Unit
    return job.pixels
  }

  private fun render(key: Key): ImageBitmap {
    val w = maxOf(1, key.width)
    val h = maxOf(1, key.height)
    val bytes = pixels(key.copy(width = w, height = h))
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.OPAQUE))
    bitmap.installPixels(bytes)
    return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
  }
}
