package com.example.ui.screens.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.example.data.model.Track
import com.example.ui.theme.FrameGuard
import com.example.ui.theme.PlayerOrigin
import com.example.ui.theme.TalBlack
import com.example.ui.theme.TalNeutralTint
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- artwork colour ----

/**
 * Shown while the artwork's colour is unknown and after it fails: a neutral grey from the identity
 * palette (Precision White at 12% over Absolute Black = #242424), with no hue of its own.
 */
internal val NeutralTint = TalNeutralTint

/**
 * The colour the page is tinted with. It comes from the artwork's pixels: [Track.colorHex] is not the
 * cover's colour (web results carry a fixed brand colour, Audius a hashed one, so a red cover used to
 * give a green page). colorHex is used only for tracks without artwork, where it is the designed colour.
 * While a new cover is read, the previous colour stays, so the page never flashes a wrong one.
 */
@Composable
internal fun rememberArtworkTint(track: Track?): Color {
  val context = LocalContext.current
  val url = track?.artworkUrl
  var tint by remember { mutableStateOf(url?.let { ArtworkColors.cached(it) }?.let { Color(it) } ?: NeutralTint) }
  LaunchedEffect(url, track?.colorHex) {
    tint = when {
      track == null -> NeutralTint
      url == null -> Color(track.colorHex)
      else -> ArtworkColors.cached(url)?.let { Color(it) }
        ?: ArtworkColors.extract(context, url)?.let { Color(it) }
        ?: NeutralTint
    }
  }
  return tint
}

/**
 * The cover as a 32 px bitmap, kept until the next one has loaded. Drawn scaled up with filtering it is
 * already a blur, so no blur effect runs on the GPU. Null for tracks without a cover.
 */
@Composable
internal fun rememberCoverBlur(track: Track?): ImageBitmap? {
  val context = LocalContext.current
  val url = track?.artworkUrl
  var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(url) {
    if (url == null) {
      bitmap = null
      return@LaunchedEffect
    }
    val request = ImageRequest.Builder(context).data(url).size(32).allowHardware(false).build()
    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return@LaunchedEffect
    bitmap = withContext(Dispatchers.Default) {
      ((drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap(32, 32)).asImageBitmap()
    }
  }
  return bitmap
}

/**
 * Seconds of drift for the cover behind the page, advanced on every frame while [running]. It
 * measures the time between frames and stops for good (see [FrameGuard]) when the device cannot keep up.
 */
@Composable
internal fun rememberCoverClock(running: Boolean): State<Float> {
  val seconds = remember { mutableFloatStateOf(0f) }
  // A frame is slow when it takes 1.5 refresh intervals or more, whatever the panel's rate is.
  val hz = LocalView.current.display?.refreshRate ?: 60f
  val guard = remember(hz) { FrameGuard(limitMs = 1500f / hz) }
  LaunchedEffect(running, guard) {
    if (!running) return@LaunchedEffect
    var last = 0L
    while (guard.allowed) {
      val now = withFrameNanos { it }
      if (last != 0L) {
        val delta = (now - last) / 1_000_000f
        guard.onFrame(delta)
        seconds.floatValue += delta / 1000f
      }
      last = now
    }
  }
  return seconds
}

internal val CoverPeriods = floatArrayOf(120f, 90f, 70f)
internal val CoverCentres = arrayOf(Offset(0.4f, 0.4f), Offset(0.6f, 0.5f), Offset(0.45f, 0.6f))
internal val CoverSaturation = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.7f) })

/** Three copies of [bitmap] turning at different speeds, under a veil of the identity's black. */
internal fun Modifier.coverBackdrop(bitmap: ImageBitmap?, seconds: State<Float>): Modifier = drawBehind {
  val image = bitmap ?: return@drawBehind
  // Large enough that the edges of the turning squares stay outside the page.
  val side = kotlin.math.hypot(size.width, size.height) * 1.5f
  clipRect {
    for (i in CoverPeriods.indices) {
      val centre = Offset(size.width * CoverCentres[i].x, size.height * CoverCentres[i].y)
      val direction = if (i == 1) -1f else 1f
      rotate(degrees = direction * 360f * seconds.value / CoverPeriods[i], pivot = centre) {
        drawImage(
          image = image,
          dstOffset = IntOffset((centre.x - side / 2).roundToInt(), (centre.y - side / 2).roundToInt()),
          dstSize = IntSize(side.roundToInt(), side.roundToInt()),
          alpha = 0.5f,
          colorFilter = CoverSaturation,
          filterQuality = FilterQuality.Low
        )
      }
    }
    drawRect(TalBlack.copy(alpha = 0.62f))
  }
}

/** The page's top colour: the tint, darkened until white text clears 4.5:1 with room for 70% white. */
internal fun pageTopFor(tint: Color): Color {
  var c = tint
  repeat(16) { if (c.luminance() > 0.08f) c = lerp(c, Color.Black, 0.15f) }
  // A black cover would make a hole; lift it to a dark grey with a trace of its hue.
  if (c.luminance() < 0.012f) c = lerp(c, NeutralTint, 0.6f)
  return c
}

/** Reads the dominant colour of artwork bitmaps, off the main thread, with a small cache by URL. */
internal object ArtworkColors {
  private val cache = object : LinkedHashMap<String, Int>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 48
  }

  fun cached(url: String): Int? = synchronized(cache) { cache[url] }

  suspend fun extract(context: Context, url: String): Int? {
    val request = ImageRequest.Builder(context)
      .data(url)
      .size(64)
      .allowHardware(false)
      .build()
    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
    val color = withContext(Dispatchers.Default) {
      val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: drawable.toBitmap(64, 64)
      dominantColor(bitmap)
    }
    synchronized(cache) { cache[url] = color }
    return color
  }

  /**
   * The cover's dominant hue: pixels are grouped by hue in 12 bins weighted by how vivid they are, and
   * the heaviest bin's average is the colour. Grey and near-black pixels do not vote. A cover that is
   * mostly grey gives its plain average instead.
   */
  fun dominantColor(bitmap: Bitmap): Int {
    val step = max(1, max(bitmap.width, bitmap.height) / 48)
    val w = FloatArray(12)
    val rs = FloatArray(12)
    val gs = FloatArray(12)
    val bs = FloatArray(12)
    var ar = 0f
    var ag = 0f
    var ab = 0f
    var n = 0
    var y = 0
    while (y < bitmap.height) {
      var x = 0
      while (x < bitmap.width) {
        val p = bitmap.getPixel(x, y)
        x += step
        if ((p ushr 24) < 128) continue
        val r = ((p shr 16) and 0xFF) / 255f
        val g = ((p shr 8) and 0xFF) / 255f
        val b = (p and 0xFF) / 255f
        ar += r; ag += g; ab += b; n++
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val sat = if (mx > 0f) (mx - mn) / mx else 0f
        if (sat < 0.2f || mx < 0.12f) continue
        val d = mx - mn
        val hue = when (mx) {
          r -> 60f * (((g - b) / d) % 6f)
          g -> 60f * ((b - r) / d + 2f)
          else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val bin = (hue / 30f).toInt().coerceIn(0, 11)
        val weight = sat * mx
        w[bin] += weight
        rs[bin] += r * weight
        gs[bin] += g * weight
        bs[bin] += b * weight
      }
      y += step
    }
    if (n == 0) return NeutralTint.toArgbInt()
    val best = w.indices.maxBy { w[it] }
    // Fewer than ~4% of pixels carry colour: the cover is grey, black or white; use its average.
    return if (w[best] < n * 0.04f) {
      argb(ar / n, ag / n, ab / n)
    } else {
      argb(rs[best] / w[best], gs[best] / w[best], bs[best] / w[best])
    }
  }

  private fun argb(r: Float, g: Float, b: Float): Int =
    (0xFF shl 24) or ((r * 255).roundToInt().coerceIn(0, 255) shl 16) or
      ((g * 255).roundToInt().coerceIn(0, 255) shl 8) or (b * 255).roundToInt().coerceIn(0, 255)

  private fun Color.toArgbInt(): Int = argb(red, green, blue)
}

/** What the page paints behind its content: the artwork tint, and the blurred cover that drifts over it. */
internal class PlayerBackdrop(val pageTop: Color, val blur: ImageBitmap?, val clock: State<Float>)

/**
 * The page colour is the artwork own, darkened until white text clears 4.5:1 (it eases over 600 ms,
 * or snaps with reduced motion). The cover, blurred, drifts slowly behind a heavy dark veil: it moves
 * only while a track with a cover plays and the frame guard allows it; otherwise (and with reduced
 * motion) it stands still.
 */
@Composable
internal fun rememberPlayerBackdrop(track: Track?, isPlaying: Boolean, reduced: Boolean): PlayerBackdrop {
  val tint = rememberArtworkTint(track)
  val colorSpec = if (reduced) snap<Color>() else tween<Color>(600, easing = FastOutSlowInEasing)
  val pageTop by animateColorAsState(pageTopFor(tint), colorSpec, label = "pageTop")
  // The container the player grows out of takes the same colour, so the opening has no seam.
  SideEffect { PlayerOrigin.tint = pageTop }
  val blur = rememberCoverBlur(track)
  val clock = rememberCoverClock(running = !reduced && isPlaying && blur != null)
  return PlayerBackdrop(pageTop, blur, clock)
}

internal fun Modifier.playerBackdrop(backdrop: PlayerBackdrop): Modifier =
  background(backdrop.pageTop).coverBackdrop(backdrop.blur, backdrop.clock)
