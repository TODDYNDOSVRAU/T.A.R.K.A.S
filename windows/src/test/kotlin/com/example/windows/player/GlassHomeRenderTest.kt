package com.example.windows.player

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import com.example.data.db.AppDatabase
import com.example.ui.theme.TarkasDarkColorScheme
import com.example.ui.theme.TarkasLightColorScheme
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.biblioteca.BibliotecaHost
import com.example.windows.equalizador.AudioHost
import com.example.windows.shell.IslandActivity
import com.example.windows.shell.Panels
import com.example.windows.shell.ShellBackdrop
import com.example.windows.shell.ShellIsland
import com.example.windows.shell.SidePane
import com.example.windows.shell.TarkasShell
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Owner evidence of the glass on the pill inside the real shell (Início, 1280 x 800, as ShellHomeRenderTest builds it):
 * `regular` and `clear` in both themes, into build/home-render as `glass-<style>-<theme>.png` (and a copy in
 * build/glass-render/home, because ShellHomeRenderTest clears build/home-render when it starts). Only its own files are
 * deleted. The pixel check proves the wired glass really differs between the styles over the shell's page.
 */
@OptIn(ExperimentalComposeUiApi::class)
class GlassHomeRenderTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val savedSlots = Panels.slots.toMap()
  private val savedTitles = Panels.topTitle.toMap()
  private val savedCorner = Panels.corner
  private val savedTransport = Panels.transport
  private val savedSide = Panels.sidePlayer
  private val savedStack = Panels.queueStack

  @Before fun setUp() {
    Panels.slots.clear(); Panels.topTitle.clear()
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear(); PillGlass.reset()
    PlayerHost.register(Panels.slots)
    BibliotecaHost.register(Panels.slots)
    AudioHost.register(Panels.slots)
  }

  @After fun tearDown() {
    Panels.slots.clear(); Panels.slots.putAll(savedSlots)
    Panels.topTitle.clear(); Panels.topTitle.putAll(savedTitles)
    Panels.corner = savedCorner; Panels.transport = savedTransport
    Panels.sidePlayer = savedSide; Panels.queueStack = savedStack
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear(); PillGlass.reset()
    scope.cancel()
  }

  private fun shot(theme: String, effect: GlassEffect): ByteArray {
    PillGlass.effect = effect
    val state = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
    state.setThemeMode(theme); state.selectTab(TarkasNavTab.HOME)
    val scene = ImageComposeScene(1280, 800, Density(1f)) { TarkasShell(state, BackDispatcher(), AppLifecycle()) }
    try {
      scene.render(0L); scene.render(16_000_000L)
      Thread.sleep(300) // the marble is baked on Dispatchers.Default
      scene.render(400_000_000L)
      return scene.render(416_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
    } finally { scene.close() }
  }

  private fun maxOf2(theme: String, a: Float, b: Float) = if (theme == "dark") maxOf(a, b) else minOf(a, b)

  @Test fun theGlassPillInTheRealShellInBothStylesAndThemes() {
    val dir = File("build/home-render").apply { mkdirs(); listFiles()?.filter { it.name.startsWith("glass-") }?.forEach { it.delete() } }
    val copy = File("build/glass-render/home").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val bytes = HashMap<String, ByteArray>()
    for (theme in listOf("dark", "light")) for ((name, effect) in listOf("regular" to GlassEffect.Regular, "clear" to GlassEffect.Clear, "none" to GlassEffect.None)) {
      val png = shot(theme, effect)
      bytes["$name-$theme"] = png
      File(dir, "glass-$name-$theme.png").writeBytes(png)
      File(copy, "glass-$name-$theme.png").writeBytes(png)
    }
    // The styles are really different pictures of the shell (the pill's region), not three copies of one.
    fun pill(png: ByteArray): androidx.compose.ui.graphics.ImageBitmap =
      Bitmap.makeFromImage(org.jetbrains.skia.Image.makeFromEncoded(png)).let { org.jetbrains.skia.Image.makeFromBitmap(it).toComposeImageBitmap() }
    for (theme in listOf("dark", "light")) {
      val r = pill(bytes["regular-$theme"]!!).toPixelMap(); val c = pill(bytes["clear-$theme"]!!).toPixelMap()
      var differs = 0
      for (y in 800 - 24 - 96..800 - 24) for (x in 24..24 + 384) if (r[x, y] != c[x, y]) differs++
      assertTrue("$theme: regular and clear draw the same pill ($differs px differ)", differs > 2000)
    }
    // Contrast of the idle pill's text over what the glass really shows in the shell: the title (onSurface) against the
    // brightest (dark) or darkest (light) pixel of the text-free strip above it, the times (onVariant) against the gaps beside them.
    val lines = ArrayList<String>(); val failures = ArrayList<String>()
    for (theme in listOf("dark", "light")) for (name in listOf("regular", "clear", "none")) {
      val cs = if (theme == "dark") TarkasDarkColorScheme else TarkasLightColorScheme
      val px = pill(bytes["$name-$theme"]!!).toPixelMap()
      fun worst(x0: Int, x1: Int, y0: Int, y1: Int): Float {
        var w = if (theme == "dark") 0f else 1f
        for (y in y0..y1) for (x in x0..x1) { val l = px[x, y].luminance(); w = if (theme == "dark") maxOf(w, l) else minOf(w, l) }
        return w
      }
      fun ratio(a: Float, b: Float) = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
      val title = ratio(cs.onSurface.luminance(), worst(100, 250, 684, 692))
      val times = ratio(cs.onSurfaceVariant.luminance(), maxOf2(theme, worst(86, 89, 746, 758), worst(272, 284, 746, 758)))
      lines += "$name $theme (shell, idle pill): title %.2f, times %.2f".format(title, times)
      if (title < 4.5f || times < 4.5f) failures += lines.last()
    }
    File(copy, "contrast-home.txt").writeText(lines.joinToString(System.lineSeparator()))
    assertTrue("AA 4.5 over the real shell: $failures", failures.isEmpty())
  }
}
