package com.example.windows.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TarkasAmoledColorScheme
import com.example.ui.theme.TarkasDarkColorScheme
import com.example.ui.theme.TarkasLightColorScheme
import com.example.ui.theme.TarkasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The labels of the two sheets: where they may stand (the part of the sheet behind that the sheets in front leave
 * uncovered), which words they show there, and that their colours are the scheme's, so no art can make them unreadable.
 */
class SheetLabelTest {
  private val pad = 6f
  private val height = 24f
  private val zero = List(4) { 0f to 0f }

  // ---- geometry ----

  @Test
  fun `a sheet with nothing in front has all the width but the two margins`() {
    val room = openLabelRoom(1, 2, zero, pad, pad, height)
    assertEquals(GF_SHEET_W - 2 * pad, room, 0.01f)
  }

  @Test
  fun `the sheet behind has less room than the one in front, and still some`() {
    val back = openLabelRoom(0, 2, zero, pad, pad, height)
    val front = openLabelRoom(1, 2, zero, pad, pad, height)
    assertTrue("back $back < front $front", back < front)
    assertTrue("the back label has room for a short word: $back", back >= 60f)
  }

  @Test
  fun `no point of the room touches the sheet in front, with the pointer on it or not`() {
    for (count in 2..4) for (v in 0 until count - 1) {
      val room = openLabelRoom(v, count, zero, pad, pad, height)
      val self = sheetPlacement(GlassPoses.Open, fanSlot(v, count).toFloat(), count - 1 - v, 0f, 0f, 0f)
      for (hoverFront in listOf(0f, 1f)) for (u in v + 1 until count) {
        val front = sheetPlacement(GlassPoses.Open, fanSlot(u, count).toFloat(), count - 1 - u, 0f, 0f, hoverFront)
        var x = pad
        while (x <= pad + room) {
          var y = pad
          while (y <= pad + height) {
            val (sx, sy) = sheetToStage(self, x, y)
            assertFalse("count=$count v=$v front=$u hover=$hoverFront at ($x,$y)", sheetCovers(front, sx, sy))
            y += 2f
          }
          x += 2f
        }
      }
    }
  }

  @Test
  fun `the room is the widest rectangle, one more unit would touch the sheet in front`() {
    val self = sheetPlacement(GlassPoses.Open, -1f, 1, 0f, 0f, 0f)
    val front = sheetPlacement(GlassPoses.Open, 1f, 0, 0f, 0f, 1f)
    val room = openLabelRoom(0, 2, zero, pad, pad, height)
    var touches = false
    for (i in 0..4) {
      val (sx, sy) = sheetToStage(self, pad + room + 3f, pad + height * i / 4f)
      if (sheetCovers(front, sx, sy)) touches = true
    }
    assertTrue("three units further the rectangle meets the front sheet", touches)
  }

  @Test
  fun `a sheet that is turned and shifted maps to the stage and back`() {
    val p = SheetPlacement(tx = 17f, ty = -42f, rotation = 5f)
    for ((x, y) in listOf(0f to 0f, GF_SHEET_W to 0f, 100f to 60f, GF_SHEET_W to GF_SHEET_H)) {
      val (sx, sy) = sheetToStage(p, x, y)
      assertTrue("($x,$y) is on its own sheet", sheetCovers(p, sx, sy, margin = 0.01f))
    }
    val (ox, oy) = sheetToStage(p, -30f, -30f)
    assertFalse("a point off the sheet is not covered", sheetCovers(p, ox, oy))
  }

  @Test
  fun `the room follows the spread, a wider fan leaves more of the sheet behind`() {
    val narrow = GlassPoses.Open.copy(spread = 20f)
    val wide = GlassPoses.Open.copy(spread = 60f)
    fun room(pose: GlassPose): Float {
      val self = sheetPlacement(pose, -1f, 1, 0f, 0f, 0f)
      val front = sheetPlacement(pose, 1f, 0, 0f, 0f, 1f)
      return labelRoom(self, listOf(front), pad, pad, height)
    }
    assertTrue(room(narrow) < room(GlassPoses.Open))
    assertTrue(room(GlassPoses.Open) < room(wide))
  }

  // ---- the words ----

  @Test
  fun `the full name shows when it fits, else the short one, else the short one cut`() {
    assertEquals(SheetLabelChoice(useShort = false, showMeta = false), chooseSheetLabel(100, 40, 60, 12, 120))
    assertEquals(SheetLabelChoice(useShort = false, showMeta = true), chooseSheetLabel(100, 40, 60, 12, 172))
    assertEquals(SheetLabelChoice(useShort = true, showMeta = false), chooseSheetLabel(100, 40, 60, 12, 80))
    assertEquals(SheetLabelChoice(useShort = true, showMeta = true), chooseSheetLabel(100, 40, 20, 12, 80))
    assertEquals(SheetLabelChoice(useShort = false, showMeta = false), chooseSheetLabel(100, 40, 60, 12, 30))
  }

  @Test
  fun `a sheet without a short name never switches`() {
    assertEquals(SheetLabelChoice(useShort = false, showMeta = false), chooseSheetLabel(100, null, 60, 12, 60))
  }

  @Test
  fun `the name is dropped last, the meta goes before it`() {
    for (inner in 0..300) {
      val c = chooseSheetLabel(100, 40, 60, 12, inner)
      if (c.showMeta) assertTrue("meta at $inner needs the name too", (if (c.useShort) 40 else 100) + 12 + 60 <= inner)
    }
  }

  @Test
  fun `the sheets carry a full and a short name in the language of the system`() {
    val sheets = pickerSheets()
    assertEquals(2, sheets.size)
    for (s in sheets) {
      assertNotNull("${s.name} has a short name", s.shortName)
      assertTrue("${s.shortName} is shorter than ${s.name}", s.shortName!!.length < s.name.length)
      assertFalse("the short name is one word", s.shortName!!.contains(' '))
    }
  }

  // ---- the filter says Áudio ----

  @Test
  fun `in Portuguese the file dialog filter reads Áudio and lists every extension`() {
    val before = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("pt-BR"))
      val f = NativePicker.request(PickMode.Files).filters
      assertEquals("Áudio (*.mp3;*.flac;*.wav)" to "*.mp3;*.flac;*.wav", f[0])
      assertEquals("Todos os arquivos (*.*)" to "*.*", f[1])
      assertEquals("Escolher arquivos de áudio", NativePicker.request(PickMode.Files).title)
    } finally { Locale.setDefault(before) }
  }

  @Test
  fun `in English the same filter reads Audio`() {
    val before = Locale.getDefault()
    try {
      Locale.setDefault(Locale.ENGLISH)
      assertEquals("Audio (*.mp3;*.flac;*.wav)", NativePicker.request(PickMode.Files).filters[0].first)
    } finally { Locale.setDefault(before) }
  }

  @Test
  fun `the folder request has no filter`() {
    assertTrue(NativePicker.request(PickMode.Folders).filters.isEmpty())
  }

  // ---- the rendered labels are whole ----

  /** Opens the folder with the picker's sheets and returns, per sheet, the texts of its label with whether each was cut. */
  @OptIn(ExperimentalTestApi::class)
  private fun renderedLabels(widthDp: Float, mode: String, hoverFront: Boolean = false): List<List<Pair<String, Boolean>>> {
    val out = mutableListOf<List<Pair<String, Boolean>>>()
    runDesktopComposeUiTest(900, 900) {
      setContent {
        TarkasTheme(themeMode = mode) {
          Box(Modifier.requiredSize(900.dp, 900.dp).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            GlassFolder("Biblioteca", "Nenhuma pasta", pickerSheets(), onSheet = {}, width = widthDp.dp, menu = emptyList())
          }
        }
      }
      waitForIdle()
      mainClock.advanceTimeBy(2200)
      waitForIdle()
      onNodeWithTag("glass-folder-hit").performClick()
      mainClock.advanceTimeBy(1600)
      waitForIdle()
      if (hoverFront) {
        onNodeWithTag("glass-folder-sheet-1").performMouseInput { moveTo(Offset(40f, 20f)) }
        mainClock.advanceTimeBy(800)
        waitForIdle()
      }
      for (i in 0..1) {
        val nodes = onAllNodes(
          hasAnyAncestor(hasTestTag("glass-folder-label-$i")) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
          useUnmergedTree = true,
        ).fetchSemanticsNodes()
        out += nodes.map { n ->
          val results = mutableListOf<TextLayoutResult>()
          n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
          val r = results.first()
          // Cut when the space it was given is narrower than the line it wants (the ellipsis flags of the semantic result are not reliable).
          val cut = r.size.width + 0.5f < r.multiParagraph.maxIntrinsicWidth || r.lineCount > 1
          println("LABEL $i \"${r.layoutInput.text.text}\" given=${r.size.width} wants=${r.multiParagraph.maxIntrinsicWidth} cut=$cut")
          r.layoutInput.text.text to cut
        }
      }
    }
    return out
  }

  @Test
  fun `both labels are whole at every step of the chain that the page uses, in both themes`() {
    val before = Locale.getDefault()
    try {
      Locale.setDefault(Locale.forLanguageTag("pt-BR"))
      for (w in listOf(384f, 192f)) for (m in listOf("light", "dark")) for (hover in listOf(false, true)) {
        val labels = renderedLabels(w, m, hover)
        for ((i, texts) in labels.withIndex()) {
          assertTrue("sheet $i has a name at $w $m", texts.isNotEmpty())
          for ((text, cut) in texts) assertFalse("sheet $i \"$text\" is cut at ${w}dp $m hover=$hover", cut)
        }
      }
    } finally { Locale.setDefault(before) }
  }

  // ---- contrast: the scheme's, never the art's ----

  private fun on(fg: Color, bg: Color) = contrastRatio(fg.compositeOver(bg), bg)

  private val schemes: List<Pair<String, ColorScheme>> = listOf(
    "light" to TarkasLightColorScheme, "dark" to TarkasDarkColorScheme, "amoled" to TarkasAmoledColorScheme,
  )

  @Test
  fun `the name and the meta read at WCAG AA on the pill in every scheme`() {
    for ((n, cs) in schemes) {
      val name = on(cs.onSurface, cs.surface)
      val meta = on(cs.onSurfaceVariant, cs.surface)
      println("CONTRAST pill $n name=${"%.2f".format(name)} meta=${"%.2f".format(meta)}")
      assertTrue("$n name $name", name >= 7.0)
      assertTrue("$n meta $meta", meta >= 4.5)
    }
  }
}
