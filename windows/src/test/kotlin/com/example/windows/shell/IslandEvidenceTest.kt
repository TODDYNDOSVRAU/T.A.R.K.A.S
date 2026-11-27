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
 * Frames of the Dynamic Island for the owner to look at: at rest, the open cascade (shape first, icons from the centre
 * out), settled, and magnified under the mouse, in both themes at 150 % (the scale where an upscaled icon blurs most).
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
    var t = 0L
    fun frame(name: String, atMs: Long) {
      t = atMs * 1_000_000L
      val img = scene.render(t)
      val png = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
      File(dir, "$theme-$name.png").writeBytes(png)
    }
    frame("0-rest", 0)
    frame("0-rest", 16)
    ShellIsland.send(IslandEvent.OPEN)
    val start = 32L
    for (ms in listOf(0L, 40L, 80L, 120L, 160L, 240L, 400L)) frame("1-open-%03d".format(ms), start + ms)
    // The middle slot (index 2 of 5) at rest: centre 2 x 48 + 24 = 120 dp from the dock's left edge.
    val dockLeft = (wDp - DockEngine.reservedWidth(DockPages.size)) / 2 + (DockEngine.reservedWidth(DockPages.size) - DockEngine.restWidth(DockPages.size)) / 2
    val p = Offset((dockLeft + DockEngine.restCentre(DockPages.size / 2)) * scale, (24f + 24f) * scale)
    scene.sendPointerEvent(PointerEventType.Enter, p)
    scene.sendPointerEvent(PointerEventType.Move, p)
    for (ms in listOf(40L, 120L, 600L)) frame("2-hover-%03d".format(ms), start + 400 + ms)
    scene.close()
  }

  @Test fun framesInBothThemes() {
    val dir = File("build/island-evidence").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    for (theme in listOf("dark", "light")) { ShellIsland.reset(); shoot(theme, dir) }
    val n = dir.listFiles()!!.size
    assertTrue("frames written: $n", n == 2 * (1 + 7 + 3))
  }
}
