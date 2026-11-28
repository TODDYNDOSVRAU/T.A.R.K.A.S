package com.example.windows.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Grid
import com.example.ui.theme.TarkasTheme
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Glass Folder's rules (no window) and, when TAL_EVIDENCE names a folder, its pictures in four states and two themes. */
@OptIn(ExperimentalTestApi::class)
class GlassFolderTest {
  // ---- the rules ----

  @Test
  fun `the three poses are those of the original and open wins over hover`() {
    assertEquals(GlassPose(0f, 42f, 0f, 0f, 15f), GlassPoses.at(0f))
    assertEquals(GlassPose(-11f, -34f, 8f, 1.4f, 10f), GlassPoses.at(1f))
    assertEquals(GlassPose(-24f, -96f, 40f, 5f, 30f), GlassPoses.at(2f))
    assertEquals(2f, GlassPoses.target(open = true, hovered = true, focusVisible = true), 0f)
    assertEquals(1f, GlassPoses.target(open = false, hovered = true, focusVisible = false), 0f)
    assertEquals(1f, GlassPoses.target(open = false, hovered = false, focusVisible = true), 0f)
    assertEquals(0f, GlassPoses.target(open = false, hovered = false, focusVisible = false), 0f)
  }

  @Test
  fun `a pose past its end keeps going, which is the overshoot of the spring`() {
    val over = GlassPoses.between(GlassPoses.Rest, GlassPoses.Open, 1.1f)
    assertTrue("hinge goes past -24", over.hinge < -24f)
    assertTrue("lift goes past -96", over.lift < -96f)
  }

  @Test
  fun `the spring curve overshoots one and the settle curve does not`() {
    var peak = 0f
    for (i in 0..100) peak = maxOf(peak, GfSpring.transform(i / 100f))
    assertTrue("spring peak $peak", peak > 1.01f)
    assertEquals(1f, GfSpring.transform(1f), 1e-4f)
    for (i in 0..100) assertTrue(GfEase.transform(i / 100f) <= 1.0001f)
  }

  @Test
  fun `sheet placement follows the original transform`() {
    val rest = sheetPlacement(GlassPoses.Rest, slot = 0f, depth = 0, jx = 1.5f, jr = -0.5f, hover = 0f)
    assertEquals(SheetPlacement(1.5f, 42f, -0.5f), rest)
    val open = sheetPlacement(GlassPoses.Open, slot = 1f, depth = 2, jx = 0f, jr = 0f, hover = 0f)
    assertEquals(SheetPlacement(40f, -96f - 2 * 30f, 5f), open)
    val lifted = sheetPlacement(GlassPoses.Open, slot = 1f, depth = 0, jx = 0f, jr = 1f, hover = 1f)
    assertEquals("extra lift of 18", -96f - 18f, lifted.ty, 1e-4f)
    assertEquals("rotation is slot*fan*0.6", 3f, lifted.rotation, 1e-4f)
  }

  @Test
  fun `jitter is stable per sheet and differs between sheets`() {
    assertEquals(sheetJitter("Pasta", 0), sheetJitter("Pasta", 0))
    assertNotEquals(sheetJitter("Pasta", 0), sheetJitter("Arquivos", 1))
    val (jx, jr) = sheetJitter("Pasta", 0)
    assertTrue(jx in -5f..5f)
    assertTrue(jr in -1.6f..1.6f)
  }

  @Test
  fun `only the last four sheets show and the headroom grows with them`() {
    assertEquals(0 until 0, visibleSheets(0))
    assertEquals(0 until 2, visibleSheets(2))
    assertEquals(2 until 6, visibleSheets(6))
    assertEquals(54f, headroomFor(0), 0f)
    assertEquals(54f, headroomFor(2), 0f)
    assertEquals(108f, headroomFor(9), 0f)
  }

  @Test
  fun `the entrance runs back, sheets, glass, key in that order and ends by TOTAL_MS`() {
    assertEquals(0f, GlassEntrance.linear(0f, GlassEntrance.FRONT_DELAY, GlassEntrance.FRONT_MS), 0f)
    assertEquals(1f, GlassEntrance.linear(GlassEntrance.TOTAL_MS.toFloat(), GlassEntrance.KEY_DELAY, GlassEntrance.KEY_SCALE_MS), 0f)
    assertEquals(1f, GlassEntrance.linear(GlassEntrance.TOTAL_MS.toFloat(), GlassEntrance.sheetDelay(3), GlassEntrance.SHEET_MS), 0f)
    assertEquals(180f, GlassEntrance.sheetDelay(0), 0f)
    assertEquals(450f, GlassEntrance.sheetDelay(3), 0f)
  }

  @Test
  fun `a click opens, a second click closes, and either closes the menu`() {
    val open = toggleOpen(FolderInteraction())
    assertTrue(open.open)
    assertFalse(toggleOpen(open).open)
    assertFalse(toggleOpen(FolderInteraction(open = true, menuOpen = true)).menuOpen)
  }

  @Test
  fun `Esc closes the menu first, then the folder, and is not ours when nothing is open`() {
    val both = FolderInteraction(open = true, menuOpen = true)
    val one = escape(both)
    assertEquals(FolderInteraction(open = true, menuOpen = false), one.state)
    assertEquals(FocusTarget.Key, one.focus)
    val two = escape(one.state)
    assertEquals(FolderInteraction(), two.state)
    assertEquals(FocusTarget.Hit, two.focus)
    assertFalse(escapeIsOurs(FolderInteraction()))
    assertEquals(FocusTarget.None, escape(FolderInteraction()).focus)
  }

  @Test
  fun `a press outside closes the menu first and the folder only when the menu is closed`() {
    val both = FolderInteraction(open = true, menuOpen = true)
    assertEquals(FolderInteraction(open = true), pressAt(both, insideFolder = false, insideMenuOrKey = false))
    assertEquals(both, pressAt(both, insideFolder = true, insideMenuOrKey = true))
    assertEquals(FolderInteraction(), pressAt(FolderInteraction(open = true), insideFolder = false, insideMenuOrKey = false))
    assertEquals(FolderInteraction(open = true), pressAt(FolderInteraction(open = true), insideFolder = true, insideMenuOrKey = false))
  }

  @Test
  fun `the width is the biggest step of the 2x chain that fits`() {
    assertEquals(384f, glassFolderWidth(1000f, 1000f), 0f)
    assertEquals(192f, glassFolderWidth(300f, 1000f), 0f)
    assertEquals(192f, glassFolderWidth(1000f, 300f), 0f)
    assertEquals(96f, glassFolderWidth(100f, 100f), 0f)
    assertEquals(96f, glassFolderWidth(10f, 10f), 0f)
  }

  @Test
  fun `saturate one is the identity and zero is grey`() {
    val id = saturationMatrix(1f)
    val eye = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
    for (i in eye.indices) assertEquals("m[$i]", eye[i], id[i], 1e-5f)
    val grey = saturationMatrix(0f)
    assertEquals(grey[0], grey[5], 1e-6f)
    assertEquals(grey[1], grey[6], 1e-6f)
    assertEquals(grey[2], grey[12], 1e-6f)
  }

  // ---- the pictures ----

  private val dir: File? = System.getenv("TAL_EVIDENCE")?.takeIf { it.isNotBlank() }?.let(::File)

  private fun sheets() = listOf(
    FolderSheet("Pasta de músicas", "Escolher", ArtPreset.Aurora),
    FolderSheet("Arquivos de áudio", "mp3 · flac · wav", ArtPreset.Ember),
  )

  private fun shoot(
    name: String, mode: String, w: Int, h: Int, fontScale: Float = 1f, widthDp: Float = 384f,
    sheets: List<FolderSheet> = sheets(), label: String = "Biblioteca", meta: String = "2 pastas",
    act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null,
  ) = runDesktopComposeUiTest(w, h) {
    setContent {
      val d = LocalDensity.current
      androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
        TarkasTheme(themeMode = mode) {
          Box(Modifier.requiredSize(w.dp, h.dp).background(MaterialTheme.colorScheme.background).testTag("root"), contentAlignment = Alignment.Center) {
            GlassFolder(label, meta, sheets, onSheet = {}, width = widthDp.dp, menu = emptyList())
          }
        }
      }
    }
    waitForIdle()
    mainClock.advanceTimeBy(2200)
    waitForIdle()
    act?.invoke(this)
    mainClock.advanceTimeBy(1600)
    waitForIdle()
    val img = onNodeWithTag("root").captureToImage().toAwtImage()
    assertTrue(img.width > 0 && img.height > 0)
    dir?.let { ImageIO.write(img, "png", File(it.also(File::mkdirs), "$name-$mode.png")) }
  }

  @Test fun rest() { for (m in listOf("light", "dark")) shoot("glass-rest", m, 560, 560) }

  @Test fun hover() {
    for (m in listOf("light", "dark")) shoot("glass-hover", m, 560, 560) {
      onNodeWithTag("glass-folder-hit").performMouseInput { moveTo(center) }
    }
  }

  @Test fun open() {
    for (m in listOf("light", "dark")) shoot("glass-open", m, 560, 560) {
      onNodeWithTag("glass-folder-hit").performClick()
    }
  }

  @Test fun openOneSheetHovered() {
    shoot("glass-open-sheet-hover", "dark", 560, 560) {
      onNodeWithTag("glass-folder-hit").performClick()
      mainClock.advanceTimeBy(1400)
      onNodeWithTag("glass-folder-sheet-1").performMouseInput { moveTo(Offset(40f, 20f)) }
    }
  }

  @Test fun entranceMidway() {
    runDesktopComposeUiTest(560, 560) {
      setContent {
        TarkasTheme(themeMode = "dark") {
          Box(Modifier.requiredSize(560.dp, 560.dp).background(MaterialTheme.colorScheme.background).testTag("root"), contentAlignment = Alignment.Center) {
            GlassFolder("Biblioteca", "2 pastas", sheets(), onSheet = {})
          }
        }
      }
      waitForIdle()
      mainClock.advanceTimeBy(600)
      waitForIdle()
      val img = onNodeWithTag("root").captureToImage().toAwtImage()
      dir?.let { ImageIO.write(img, "png", File(it.also(File::mkdirs), "glass-entrance-600ms-dark.png")) }
    }
  }

  @Test fun narrow192() { shoot("glass-192", "light", 320, 360, widthDp = 192f) }

  @Test fun fontScale200() {
    shoot("glass-font200", "light", 560, 560, fontScale = 2f, label = "Biblioteca com um nome muito comprido", meta = "128 pastas observadas")
  }

  @Test fun emptyFolder() { shoot("glass-empty", "dark", 560, 560, sheets = emptyList(), meta = "0 itens") }

  @Test fun fourSheetsOpen() {
    val four = listOf("A", "B", "C", "D").mapIndexed { i, n -> FolderSheet("Folha $n", "$i", ArtPreset.entries[i]) }
    shoot("glass-four-open", "dark", 560, 620, sheets = four) { onNodeWithTag("glass-folder-hit").performClick() }
  }

  @Test fun gridWidthsAreChainSteps() {
    assertEquals(Grid.u384, 384.dp)
  }
}
