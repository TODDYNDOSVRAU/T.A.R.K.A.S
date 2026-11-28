package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.data.db.AppDatabase
import com.example.data.model.Track
import com.example.ui.theme.TarkasTheme
import com.example.windows.audio.DesktopPlayer
import com.example.windows.player.FakeOut
import com.example.windows.player.MemoryPrefs
import com.example.windows.player.PlayerController
import com.example.windows.shell.CornerInsets
import com.example.windows.shell.LocalCornerInsets
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * The Início has ONE left edge: the hero, "Continuar ouvindo", every other carousel, the catalogues and the about card
 * all start at the same x, whatever the window width and whether or not the player's corner card is drawn.
 */
@OptIn(ExperimentalTestApi::class)
class HomeAlignmentTest {
  private val corner = CornerInsets(432.dp, 324.dp, true)
  private val noCard = CornerInsets(432.dp, 120.dp, false)

  private fun seeded(): BibliotecaState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val app = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
    val ctl = PlayerController(FakeOut(), MemoryPrefs(), scope)
    val lib = BibliotecaState(app, ctl, scope, dataDir = Files.createTempDirectory("tal-align").toFile())
    (1..12).map { i ->
      Track(id = "al$i", title = "Faixa $i", artist = "Artista ${i % 3}", album = "Álbum ${i % 4}", durationMs = 180_000L, genre = "Rock", era = "2020s",
        isLocalFile = true, filePath = "C:/nao-existe/al$i.mp3")
    }.forEach { lib.store.addPicked(it) }
    Thread.sleep(400)
    return lib
  }

  /** The left edge (dp = px here) of every section the page draws, top to bottom. */
  private fun lefts(width: Int, insets: CornerInsets): List<Float> {
    val lib = seeded()
    var out = emptyList<Float>()
    runDesktopComposeUiTest(width, 4000) {
      setContent {
        TarkasTheme(themeMode = "dark") {
          CompositionLocalProvider(LocalCornerInsets provides insets) {
            Box(Modifier.requiredSize(width.dp, 4000.dp)) { HomeScreen(lib, Modifier.fillMaxSize()) }
          }
        }
      }
      waitForIdle(); Thread.sleep(1200); waitForIdle()
      val nodes = onAllNodesWithTag(TAG_SECTION)
      out = nodes.fetchSemanticsNodes().indices.map { nodes[it].getBoundsInRoot().left.value }
    }
    return out
  }

  private fun assertOneEdge(width: Int, insets: CornerInsets, expected: Float) {
    val l = lefts(width, insets)
    assertTrue("at $width the page must draw at least the hero and a carousel, got ${l.size}", l.size >= 2)
    l.forEach { assertEquals("section left edge at width $width", expected, it, 0.5f) }
    assertEquals("all sections share one edge at width $width", 1, l.map { Math.round(it) }.toSet().size)
  }

  @Test fun narrowWindowEverySectionStartsAtTheMargin() = assertOneEdge(900, corner, 24f)

  @Test fun mediumWindowEverySectionStartsAfterTheCornerLane() {
    assertOneEdge(1024, corner, 432f)
    assertOneEdge(1366, corner, 432f)
    assertOneEdge(1917, corner, 432f)
  }

  @Test fun wideWindowEverySectionStartsAtTheCentredColumn() {
    val w = 2100
    assertOneEdge(w, corner, (w - 1152) / 2f)
  }

  @Test fun withoutTheCornerCardEverySectionStartsAtTheMarginAtAnyWidth() {
    for (w in listOf(1366, 1917, 2100)) assertOneEdge(w, noCard, 24f)
  }

  /** Evidence only: with TAL_EVIDENCE=<folder> the Inicio is rendered with the corner lane, in both themes, at two widths. */
  @Test fun evidenceRenders() {
    val dir = System.getenv("TAL_EVIDENCE")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
    dir.mkdirs()
    for (mode in listOf("dark", "light")) for ((w, h) in listOf(1917 to 1000, 1366 to 800)) {
      val lib = seeded()
      runDesktopComposeUiTest(w, h) {
        setContent {
          TarkasTheme(themeMode = mode) {
            CompositionLocalProvider(LocalCornerInsets provides corner) {
              Box(Modifier.requiredSize(w.dp, h.dp).background(MaterialTheme.colorScheme.background).testTag("root")) {
                HomeScreen(lib, Modifier.fillMaxSize())
                // Stands in for the player art card, only to show the gutter it owns.
                Box(Modifier.requiredSize(384.dp, 324.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest).align(androidx.compose.ui.Alignment.BottomStart))
              }
            }
          }
        }
        waitForIdle(); Thread.sleep(1500); waitForIdle()
        ImageIO.write(onNodeWithTag("root").captureToImage().toAwtImage(), "png", File(dir, "inicio-edge-${w}x$h-$mode.png"))
      }
    }
  }
}
