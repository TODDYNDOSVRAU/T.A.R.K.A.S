package com.example.windows.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.theme.TarkasTheme
import com.example.windows.shell.Backdrop
import com.example.windows.shell.GlassBackdrop
import com.example.windows.shell.GlassPart
import com.example.windows.shell.LocalGlassBackdrop
import com.example.windows.shell.ShellBackdrop
import com.example.windows.shell.recordsGlass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Pixel proofs of the liquid glass on the pill, and the owner's evidence: the pill under the shell's wiring (the harness
 * below wires [GlassBackdrop] the way Shell.kt must), rendered in both themes into build/glass-render.
 */
@OptIn(ExperimentalComposeUiApi::class)
class LiquidGlassRenderTest {
  private class Shot(image: Image) {
    val w = image.width; val h = image.height
    private val bmp = Bitmap.makeFromImage(image)
    fun color(x: Int, y: Int): Color = Color(bmp.getColor(x, y))
    fun lum(x: Int, y: Int): Float = color(x, y).luminance()
    fun png(): ByteArray = Image.makeFromBitmap(bmp).encodeToData(EncodedImageFormat.PNG)!!.bytes
  }

  private fun t(n: Int) = Track("t$n", "A long track title number $n", "An artist name $n", "A$n", 200_000, "", "", true, "C:/m/t$n.mp3")
  private val list = (1..3).map(::t)

  private class Fixture {
    val controller = PlayerController(FakeOut(), MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
  }

  @Before @After fun reset() { ExpandedPlayer.close(); ExpandedPlayer.progress = 0f; ShellBackdrop.clear() }

  /** The window as the shell will build it: backdrop, page and corner, the first two recorded for the glass when [wired]. */
  @Composable
  private fun Window(wired: Boolean, page: @Composable BoxScope.() -> Unit, corner: @Composable () -> Unit) {
    val glass = remember { GlassBackdrop() }
    CompositionLocalProvider(LocalGlassBackdrop provides if (wired) glass else null) {
      Box(Modifier.fillMaxSize()) {
        Backdrop(if (wired) Modifier.recordsGlass(glass, GlassPart.Back) else Modifier)
        Box(Modifier.fillMaxSize().then(if (wired) Modifier.recordsGlass(glass, GlassPart.Page) else Modifier), content = page)
        Box(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 24.dp)) { corner() }
      }
    }
  }

  /** Renders the same scene until the marble (baked on another thread) is in, and returns the last frame. */
  private fun render(width: Int, height: Int, theme: String, content: @Composable () -> Unit): Shot {
    val scene = ImageComposeScene(width, height, Density(1f)) { TarkasTheme(themeMode = theme) { content() } }
    try {
      scene.render(0L); scene.render(16_000_000L)
      Thread.sleep(300)
      return Shot(scene.render(400_000_000L))
    } finally { scene.close() }
  }

  // The 480 x 240 scene: the pill is the 384 x 96 at x 24..408, y 120..216.
  private val sceneW = 480; private val sceneH = 240
  private val px0 = 24; private val py0 = 120

  private fun emptyPill(effect: GlassEffect = GlassEffect.Regular): @Composable () -> Unit = { GlassView(effect = effect, enabled = true, reduced = false) {} }

  /** A white upper part and a black lower part with the line between them through the middle of the pill (y = 168). */
  private val splitPage: @Composable BoxScope.() -> Unit = {
    Box(Modifier.fillMaxSize().background(Color.Black))
    Box(Modifier.fillMaxWidth().height(168.dp).background(Color.White))
  }

  /** The row where the column [x] passes from the bright side to the dark one, to a fraction of a pixel. */
  private fun crossing(s: Shot, x: Int, top: Int = 132, bottom: Int = 204): Float {
    val hi = s.lum(x, top); val lo = s.lum(x, bottom)
    val mid = (hi + lo) / 2f
    for (y in top until bottom) {
      val a = s.lum(x, y); val b = s.lum(x, y + 1)
      if (a >= mid && b < mid) return y + (a - mid) / (a - b)
    }
    return Float.NaN
  }

  @Test fun theBackdropIsRefractedNotJustTinted() {
    // Clear refracts the most (displacement 6 dp, half of it per axis); regular less (3 dp), under a wider blur: a touch, at the minimum.
    for ((effect, least) in listOf(GlassEffect.Clear to 1.5f, GlassEffect.Regular to 0.5f)) {
      val s = render(sceneW, sceneH, "dark") { Window(true, splitPage, emptyPill(effect)) }
      val rows = (px0 + 60..px0 + 324 step 12).map { crossing(s, it) }
      assertTrue("${effect.style}: the edge was lost in some column: $rows", rows.none { it.isNaN() })
      val spread = rows.max() - rows.min()
      // A straight edge would stay at 168 in every column; the displacement bends it along the pill.
      assertTrue("${effect.style}: the edge moved only $spread px along the pill: $rows", spread >= least)
    }
  }

  @Test fun theBackdropIsBlurredMoreInRegularThanInClear() {
    fun widest(effect: GlassEffect): Float {
      val s = render(sceneW, sceneH, "dark") { Window(true, splitPage, emptyPill(effect)) }
      return transitions(s).max()
    }
    val clear = widest(GlassEffect.Clear); val regular = widest(GlassEffect.Regular)
    assertTrue("clear's edge transition is never narrower than 2 px: $clear", clear >= 2f)
    assertTrue("regular ($regular px) blurs more than clear ($clear px)", regular > clear + 2f)
  }

  /** 10 to 90% widths of the page's edge, per column across the pill. */
  private fun transitions(s: Shot): List<Float> {
    val widths = (px0 + 60..px0 + 324 step 6).map { x ->
      val hi = s.lum(x, 132); val lo = s.lum(x, 204); val span = hi - lo
      var first = -1; var last = -1
      for (y in 132..204) {
        val v = (s.lum(x, y) - lo) / span
        if (v < 0.9f && first < 0) first = y
        if (v > 0.1f) last = y
      }
      (last - first).toFloat()
    }
    return widths
  }

  @Test fun recordingTheSourcesChangesNothingOutsideThePill() {
    val wired = render(sceneW, sceneH, "dark") { Window(true, splitPage, emptyPill()) }
    val plain = render(sceneW, sceneH, "dark") { Window(false, splitPage, emptyPill()) }
    // Everything above the pill, and the right of it (clear of the 36 dp shadow), is the same pixel for pixel.
    var different = 0
    for (y in 0 until py0 - 40 step 3) for (x in 0 until sceneW step 3) if (wired.color(x, y) != plain.color(x, y)) different++
    for (y in 0 until sceneH step 3) for (x in px0 + 384 + 40 until sceneW step 3) if (wired.color(x, y) != plain.color(x, y)) different++
    assertEquals(0, different)
  }

  @Test fun withoutTheWiringThePillIsTheTintAndTheBorder() {
    val plain = render(sceneW, sceneH, "dark") { Window(false, splitPage, emptyPill()) }
    // No copy of the page: the sharp edge of the page is not in the pill, which stays uniform in its middle.
    val a = plain.lum(px0 + 192, 140); val b = plain.lum(px0 + 192, 200)
    // The page still shows through the tint (55% surface) but the pill itself shows no refracted line, and the 4-px rim keeps working.
    assertTrue("the plain pill's middle is flat: $a $b", abs(a - b) < 0.5f)
    assertTrue(plain.color(px0 + 192, py0 + 96 - 2).alpha == 1f)
  }

  @Test fun theRimIsThereWithAndWithoutTheRefraction() {
    for (wired in listOf(true, false)) {
      val s = render(sceneW, sceneH, "dark") { Window(wired, { Box(Modifier.fillMaxSize().background(Color.Black)) }, emptyPill()) }
      // On a black page the dark theme's bottom edge light lifts the bottom edge above the top one.
      val bottom = s.lum(px0 + 192, py0 + 95); val top = s.lum(px0 + 192, py0)
      assertTrue("wired=$wired bottom $bottom top $top", bottom > top + 0.02f)
    }
  }

  @Test fun theDisabledPillHalvesTheContentAndTheBorderButNotTheGlass() {
    fun shot(enabled: Boolean) = render(sceneW, sceneH, "dark") {
      Window(true, { Box(Modifier.fillMaxSize().background(Color.Black)) }) {
        GlassView(enabled = enabled, reduced = false) { Box(Modifier.padding(start = 168.dp, top = 24.dp).height(48.dp).fillMaxWidth(0.25f).background(Color.White)) }
      }
    }
    val on = shot(true); val off = shot(false)
    // The content: a white block at 50% over the glass is clearly darker than at 100%.
    val x = px0 + 168 + 12; val y = py0 + 48
    assertTrue("content ${on.lum(x, y)} -> ${off.lum(x, y)}", on.lum(x, y) > off.lum(x, y) + 0.1f)
    // The rim: the bottom edge light is weaker at 50%.
    val rx = px0 + 192; val ry = py0 + 95
    assertTrue("rim ${on.lum(rx, ry)} -> ${off.lum(rx, ry)}", on.lum(rx, ry) > off.lum(rx, ry) + 0.01f)
    // The glass in between stays: the pixels at the left, away from the content and the edges, are the same.
    assertEquals(on.lum(px0 + 100, py0 + 60), off.lum(px0 + 100, py0 + 60), 0.02f)
  }

  @Test fun aCallerThatKeepsItsTextLegibleCanLeaveTheContentUndimmed() {
    fun shot(enabled: Boolean, dim: Boolean) = render(sceneW, sceneH, "dark") {
      Window(true, { Box(Modifier.fillMaxSize().background(Color.Black)) }) {
        GlassView(enabled = enabled, reduced = false, dimContent = dim) { Box(Modifier.padding(start = 168.dp, top = 24.dp).height(48.dp).fillMaxWidth(0.25f).background(Color.White)) }
      }
    }
    val on = shot(true, true); val idleKept = shot(false, false); val idleDimmed = shot(false, true)
    val x = px0 + 168 + 12; val y = py0 + 48
    assertEquals(on.lum(x, y), idleKept.lum(x, y), 0.01f)
    assertTrue(idleDimmed.lum(x, y) < idleKept.lum(x, y) - 0.1f)
    // The border still halves when disabled.
    val rx = px0 + 192; val ry = py0 + 95
    assertTrue(on.lum(rx, ry) > idleKept.lum(rx, ry) + 0.005f)
  }

  @Test fun thePillRepaintsWhenThePageBehindItChanges() {
    var bright by mutableStateOf(false)
    val scene = ImageComposeScene(sceneW, sceneH, Density(1f)) {
      TarkasTheme(themeMode = "dark") {
        Window(true, { Box(Modifier.fillMaxSize().background(if (bright) Color.White else Color.Black)) }, emptyPill())
      }
    }
    try {
      scene.render(0L); scene.render(16_000_000L); Thread.sleep(300)
      val dark = Shot(scene.render(400_000_000L)).lum(px0 + 192, py0 + 48)
      bright = true
      scene.render(416_000_000L)
      val light = Shot(scene.render(432_000_000L)).lum(px0 + 192, py0 + 48)
      assertTrue("a stale pill: $dark -> $light", light > dark + 0.1f)
    } finally { scene.close() }
  }

  // ---- Contrast: the pill's text over the marble, in both themes ----

  /** The pill's text region (title and artist, left of the buttons), in pill coordinates: x 84..228, y 12..48. */
  private fun textBackdrop(s: Shot, ox: Int, oy: Int, pick: (Float, Float) -> Float): Float {
    var v = Float.NaN
    for (y in oy + 12..oy + 48 step 2) for (x in ox + 84..ox + 228 step 4) {
      val l = s.lum(x, y); v = if (v.isNaN()) l else pick(v, l)
    }
    return v
  }

  private fun contrast(a: Float, b: Float) = (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)

  @Test fun theTextOfThePillKeepsAAOverTheMarbleInBothThemes() {
    for (effect in listOf(GlassEffect.Regular, GlassEffect.Clear, GlassEffect.None)) for (theme in listOf("dark", "light")) {
      var onSurface = Color.Unspecified; var onVariant = Color.Unspecified
      val s = render(sceneW, sceneH, theme) {
        onSurface = MaterialTheme.colorScheme.onSurface; onVariant = MaterialTheme.colorScheme.onSurfaceVariant
        Window(true, {}, emptyPill(effect))
      }
      val dark = theme == "dark"
      // The worst pixel of the glass behind the text: the brightest one in the dark theme, the darkest in the light.
      val worst = textBackdrop(s, px0, py0) { a, b -> if (dark) maxOf(a, b) else minOf(a, b) }
      val title = contrast(onSurface.luminance(), worst); val artist = contrast(onVariant.luminance(), worst)
      assertTrue("${effect.style} $theme: the title has $title over the glass (luminance $worst)", title >= 4.5)
      assertTrue("${effect.style} $theme: the artist has $artist over the glass (luminance $worst)", artist >= 4.5)
    }
  }

  @Test fun theGlassIsNoBrighterThanThePlainTintOverTheMarble() {
    // Refraction and blur move the marble around, they do not add light: the worst pixel is the plain pill's, give or take a rounding step.
    for (theme in listOf("dark", "light")) {
      val dark = theme == "dark"
      val pick = { a: Float, b: Float -> if (dark) maxOf(a, b) else minOf(a, b) }
      val wired = textBackdrop(render(sceneW, sceneH, theme) { Window(true, {}, emptyPill()) }, px0, py0, pick)
      val plain = textBackdrop(render(sceneW, sceneH, theme) { Window(false, {}, emptyPill()) }, px0, py0, pick)
      assertTrue("$theme: wired $wired vs plain $plain", if (dark) wired <= plain + 0.01f else wired >= plain - 0.01f)
    }
  }

  /** Reported, not asserted: the worst page a user can scroll under the corner (pure white in the dark theme, pure black in the light). */
  @Test fun evidenceTheTextOverTheWorstPage() {
    val dir = File("build/glass-render").apply { mkdirs() }
    val lines = ArrayList<String>()
    for (effect in listOf(GlassEffect.Regular, GlassEffect.Clear, GlassEffect.None)) for (theme in listOf("dark", "light")) {
      val dark = theme == "dark"
      var onSurface = Color.Unspecified; var onVariant = Color.Unspecified
      val s = render(sceneW, sceneH, theme) {
        onSurface = MaterialTheme.colorScheme.onSurface; onVariant = MaterialTheme.colorScheme.onSurfaceVariant
        Window(true, { Box(Modifier.fillMaxSize().background(if (dark) Color.White else Color.Black)) }, emptyPill(effect))
      }
      val worst = textBackdrop(s, px0, py0) { a, b -> if (dark) maxOf(a, b) else minOf(a, b) }
      lines += "${effect.style} $theme worst page: title %.2f, artist %.2f (backdrop luminance %.3f)".format(contrast(onSurface.luminance(), worst), contrast(onVariant.luminance(), worst), worst)
    }
    File(dir, "worst-page-contrast.txt").writeText(lines.joinToString(System.lineSeparator()))
  }

  // ---- The owner's evidence ----

  @Test fun evidenceThePillInBothThemes() {
    val dir = File("build/glass-render").apply { mkdirs(); listFiles()?.filter { it.extension == "png" }?.forEach { it.delete() } }
    for (theme in listOf("dark", "light")) {
      for ((name, page) in listOf("marble" to ({ }), "cards" to ({ }))) {
        val f = Fixture(); f.controller.play(list[0], list)
        val content: @Composable BoxScope.() -> Unit = if (name == "marble") ({}) else ({
          // A page with edges and cover-like blocks under the corner, so the refraction can be seen.
          Box(Modifier.fillMaxSize().padding(top = 616.dp)) {
            Box(Modifier.padding(start = 120.dp).height(184.dp).fillMaxWidth(0.22f).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
            Box(Modifier.padding(top = 88.dp).height(24.dp).fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
          }
        })
        val s = render(1280, 800, theme) { Window(true, content) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = false) } }
        File(dir, "$theme-$name.png").writeBytes(s.png())
      }
    }
    assertEquals(4, dir.listFiles()!!.count { it.extension == "png" })
  }
}
