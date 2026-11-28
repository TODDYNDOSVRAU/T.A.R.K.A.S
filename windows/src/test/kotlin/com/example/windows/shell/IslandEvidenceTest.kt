package com.example.windows.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.example.ui.theme.Grid
import com.example.ui.theme.TarkasTheme
import com.example.ui.viewmodel.TarkasNavTab
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Frames of the Dynamic Island for the owner to look at: at rest, the hover lift, opening, magnified under the mouse,
 * closing and a morph turned around mid-flight, in both themes at 150 % (the scale where an upscaled icon blurs most).
 * Written to build/island-evidence; the test only checks that every frame was written.
 */
@OptIn(ExperimentalComposeUiApi::class)
class IslandEvidenceTest {
  @Before @After fun clean() { SidePane.close(); ShellIsland.reset(); IslandActivity.reset() }

  private val scale = 1.5f
  private val wDp = 960
  private val hDp = 240

  private fun shoot(theme: String, dir: File) {
    val scene = ImageComposeScene((wDp * scale).toInt(), (hDp * scale).toInt(), Density(scale)) {
      TarkasTheme(themeMode = theme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(top = Grid.u24), contentAlignment = Alignment.TopCenter) {
          FloatingDock(current = TarkasNavTab.HOME, onSelect = {}, reduceMotion = false, compact = false)
        }
      }
    }
    // The scene is driven at 60 Hz like the window, so the frame loop sees the same steps it sees on screen; only the
    // named instants are written.
    var t = -1L
    /** Runs the frames up to [atMs], so an event sent next lands at that instant. */
    fun upTo(atMs: Long) {
      val end = atMs * 1_000_000L
      if (t >= 0L) while (end - t > 16_666_667L) { t += 16_666_667L; scene.render(t) }
    }
    fun frame(name: String, atMs: Long) {
      upTo(atMs)
      t = atMs * 1_000_000L
      val img = scene.render(t)
      val png = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
      File(dir, "$theme-$name.png").writeBytes(png)
    }
    frame("0-rest", 0)
    frame("0-rest", 16)
    // The resting pill under the mouse lifts (hover_lift: 198 x 54, 2 dp up), then settles back when it leaves.
    val pill = Offset(wDp / 2f * scale, (24f + 24f) * scale)
    val away = Offset(wDp / 2f * scale, (hDp - 12f) * scale)
    scene.sendPointerEvent(PointerEventType.Enter, pill)
    scene.sendPointerEvent(PointerEventType.Move, pill)
    for (ms in listOf(40L, 100L)) frame("1-lift-%03d".format(ms), 16 + ms)
    scene.sendPointerEvent(PointerEventType.Move, away)
    scene.sendPointerEvent(PointerEventType.Exit, away)
    ShellIsland.reset()
    frame("1-lift-300", 16 + 300)
    var start = 400L
    upTo(start)
    ShellIsland.send(IslandEvent.OPEN)
    for (ms in listOf(0L, 40L, 80L, 120L, 160L, 240L, 400L)) frame("2-open-%03d".format(ms), start + ms)
    // The middle slot (index 2 of 5) at rest: centre 2 x 48 + 24 = 120 dp from the dock's left edge.
    val dockLeft = (wDp - DockEngine.reservedWidth(DockPages.size)) / 2 + (DockEngine.reservedWidth(DockPages.size) - DockEngine.restWidth(DockPages.size)) / 2
    val p = Offset((dockLeft + DockEngine.restCentre(DockPages.size / 2)) * scale, (24f + 24f) * scale)
    scene.sendPointerEvent(PointerEventType.Enter, p)
    scene.sendPointerEvent(PointerEventType.Move, p)
    for (ms in listOf(40L, 120L, 600L)) frame("3-hover-%03d".format(ms), start + 400 + ms)
    // Esc with the mouse still on the magnified middle slot: the slots settle back as the pages fade, and the body
    // shrinks around them, so no grown circle is ever cut.
    start += 1000
    ShellIsland.send(IslandEvent.ESCAPE)
    for (ms in listOf(0L, 40L, 80L, 120L, 160L, 240L, 400L)) frame("4-close-%03d".format(ms), start + ms)
    scene.sendPointerEvent(PointerEventType.Move, away)
    scene.sendPointerEvent(PointerEventType.Exit, away)
    ShellIsland.reset()
    // Turned around mid-flight: opened, then Esc 60 ms in; the springs keep their momentum and head back.
    start += 1000
    upTo(start)
    ShellIsland.send(IslandEvent.OPEN)
    frame("5-reverse-060", start + 60)
    ShellIsland.send(IslandEvent.ESCAPE)
    for (ms in listOf(100L, 140L, 220L, 460L)) frame("5-reverse-%03d".format(ms), start + ms)
    scene.close()
  }

  @Test fun framesInBothThemes() {
    val dir = File("build/island-evidence").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    for (theme in listOf("dark", "light")) { ShellIsland.reset(); shoot(theme, dir) }
    val n = dir.listFiles()!!.size
    assertTrue("frames written: $n", n == 2 * (1 + 3 + 7 + 3 + 7 + 5))
  }
}
