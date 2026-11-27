package com.example.windows.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.db.AppDatabase
import com.example.data.model.Track
import com.example.ui.theme.LocalBarInset
import com.example.ui.theme.TarkasTheme
import com.example.windows.audio.DesktopPlayer
import com.example.windows.player.FakeOut
import com.example.windows.player.MemoryPrefs
import com.example.windows.player.PlayerController
import com.example.windows.shell.ShellBackdrop
import com.example.windows.state.TarkasState
import java.nio.file.Files
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Biblioteca against the shell's page contract (win-geral's page review, 2026-11-27): the 24 dp side margin on both
 * edges, the scroll ends clear of the corner player, one column of counts, "Unknown artist" translated at display time,
 * and cards that are glass over the silk (with the text still at AA on the silk's worst pixel).
 */
@OptIn(ExperimentalTestApi::class)
class LibraryPageLayoutTest {
  private val unknown = LibraryScanner.UNKNOWN_ARTIST

  private fun seeded(): BibliotecaState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val app = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
    val ctl = PlayerController(FakeOut(), MemoryPrefs(), scope)
    val lib = BibliotecaState(app, ctl, scope, dataDir = Files.createTempDirectory("tal-layout").toFile())
    val artists = listOf("Marisa Alves", "The Long Walk", "Nora Quinn", unknown)
    val tracks = (1..30).map { i ->
      Track(
        id = "pl$i", title = "Faixa de teste $i", artist = artists[i % artists.size], album = "Álbum ${i % 6}",
        durationMs = 180_000L + i * 1000, genre = listOf("Rock", "Jazz", "Pop")[i % 3], era = "2020s",
        isLocalFile = true, filePath = "C:/nao-existe/pl$i.mp3"
      )
    }
    tracks.forEach { lib.store.addPicked(it) }
    Thread.sleep(400)
    lib.store.createPlaylist("Para correr", tracks.take(8))
    lib.store.toggleFavorite(tracks[1])
    Thread.sleep(400)
    return lib
  }

  private fun page(lib: BibliotecaState, w: Int, h: Int, mode: String = "dark", inset: Dp = 0.dp, backdrop: Color? = null, block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
    runDesktopComposeUiTest(w, h) {
      setContent {
        TarkasTheme(themeMode = mode) {
          CompositionLocalProvider(LocalBarInset provides inset) {
            Box(Modifier.requiredSize(w.dp, h.dp).background(backdrop ?: MaterialTheme.colorScheme.background).testTag("root")) { LibraryScreen(lib, Modifier) }
          }
        }
      }
      waitForIdle(); Thread.sleep(1200); waitForIdle()
      block()
    }

  /** Both cards sit on the 24 dp margin: the sidebar's left edge is the title column, the main card ends 24 dp from the window. */
  @Test fun cardsKeepTheSideMargin() {
    val lib = seeded()
    page(lib, 1280, 800) {
      val sources = onNodeWithTag(TAG_SOURCES).fetchSemanticsNode().boundsInRoot
      val main = onNodeWithTag(TAG_MAIN).fetchSemanticsNode().boundsInRoot
      assertEquals("sidebar left (24 dp margin)", 24f, sources.left, 0.5f)
      assertEquals("main card right (24 dp margin)", 1280f - 24f, main.right, 0.5f)
      assertEquals("gap between the cards (12 dp)", 12f, main.left - sources.right, 0.5f)
      assertEquals("cards top (12 dp)", 12f, sources.top, 0.5f)
      assertEquals("cards bottom (12 dp)", 800f - 12f, sources.bottom, 0.5f)
    }
  }

  /** The table scrolls its last row above the corner player's inset; without the inset it reaches the card's end. */
  @Test fun tableEndClearsTheCorner() {
    val lib = seeded()
    for ((inset, label) in listOf(120.dp to "with the corner", 0.dp to "without the corner")) page(lib, 1280, 800, inset = inset) {
      onNodeWithTag(TAG_ROWS).performScrollToIndex(29); waitForIdle()
      val card = onNodeWithTag(TAG_MAIN).fetchSemanticsNode().boundsInRoot
      val rows = onAllNodesWithText("Faixa de teste", substring = true).fetchSemanticsNodes()
      assertTrue("$label: rows are on screen", rows.isNotEmpty())
      val lastBottom = rows.maxOf { it.boundsInRoot.bottom }
      val allowed = card.bottom - inset.value
      assertTrue("$label: last row bottom $lastBottom must be <= $allowed", lastBottom <= allowed + 1f)
      // A row's text ends a little above its own bottom: the end of the list is within one row of the allowed line.
      assertTrue("$label: the list really reaches the allowed line (bottom $lastBottom, allowed $allowed)", lastBottom >= allowed - 60f)
    }
  }

  /** The sidebar scrolls its last row above the corner player too (the window is short enough for it to scroll). */
  @Test fun sidebarEndClearsTheCorner() {
    val lib = seeded()
    page(lib, 1280, 480, inset = 120.dp) {
      val node = onNodeWithTag(TAG_SOURCES)
      node.performScrollToIndex(18); waitForIdle()
      val card = onNodeWithTag(TAG_SOURCES).fetchSemanticsNode().boundsInRoot
      val lastBottom = node.fetchSemanticsNode().children.maxOf { it.boundsInRoot.bottom }
      assertTrue("sidebar last row bottom $lastBottom must be <= ${card.bottom - 120f}", lastBottom <= card.bottom - 120f + 1f)
    }
  }

  /** Every count (header or row) ends at one x: tabular, right-aligned, one column. */
  @Test fun countsShareOneColumn() {
    val lib = seeded()
    page(lib, 1280, 800) {
      val rights = onAllNodesWithTag(TAG_COUNT, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.right }
      assertTrue("at least the 3 sets, the playlists header, a playlist, the 3 automatic lists and the 3 group headers (found ${rights.size})", rights.size >= 11)
      val first = rights.first()
      rights.forEachIndexed { i, r -> assertEquals("count #$i right edge", first, r, 0.5f) }
      // The column ends 24 dp (the trailing slot) before the card's right edge.
      val card = onNodeWithTag(TAG_SOURCES).fetchSemanticsNode().boundsInRoot
      assertEquals("counts end one trailing slot before the card edge", card.right - 24f, first, 0.5f)
    }
  }

  /** "Unknown artist" is a stored key; the screens say "Artista desconhecido" in Portuguese and keep the key in English. */
  @Test fun unknownArtistIsTranslatedOnScreen() {
    val saved = Locale.getDefault()
    try {
      val lib = seeded()
      Locale.setDefault(Locale.forLanguageTag("pt-BR"))
      assertEquals("Artista desconhecido", displayArtist(unknown))
      assertEquals("Marisa Alves", displayArtist("Marisa Alves"))
      page(lib, 1280, 800) {
        assertTrue("pt: the table shows the translated name", onAllNodesWithText("Artista desconhecido", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue("pt: the key itself is not shown", onAllNodesWithText(unknown, substring = true).fetchSemanticsNodes().isEmpty())
      }
      Locale.setDefault(Locale.ENGLISH)
      assertEquals("Unknown artist", displayArtist(unknown))
      // What is stored, grouped and sorted on never changes.
      assertEquals("Unknown artist", unknown)
      assertTrue(lib.library.value.any { it.artist == unknown })
    } finally {
      Locale.setDefault(saved)
    }
  }

  /** The text on a glass card holds AA on the silk's worst pixel (the brightest dark silk, the darkest light silk) plus a white/black cover. */
  @Test fun textOnGlassKeepsContrast() {
    for (mode in listOf("dark", "light")) runDesktopComposeUiTest(200, 100) {
      var results = emptyList<Pair<String, Double>>()
      setContent {
        TarkasTheme(themeMode = mode) {
          val c = MaterialTheme.colorScheme
          val dark = c.background.luminance() < 0.5f
          val silkL = if (dark) ShellBackdrop.MARBLE_MAX_L_DARK.toDouble() else ShellBackdrop.MARBLE_MIN_L_LIGHT.toDouble()
          val g = srgb(silkL).toFloat()
          val share = ShellBackdrop.coverShare(c.background)
          val extreme = if (dark) 1f else 0f
          val mixed = g * (1 - share) + extreme * share
          val under = Color(mixed, mixed, mixed)
          results = listOf(
            "onSurface" to GlassCards.contrast(c.onSurface, c.surface, under),
            "onSurfaceVariant" to GlassCards.contrast(c.onSurfaceVariant, c.surface, under),
            "primary(selected label)" to GlassCards.contrast(
              legibleOn(c.primary, c.primary.copy(alpha = 0.16f).compositeOver(c.surface.copy(alpha = GlassCards.READING_ALPHA).compositeOver(under)), c.onSurface),
              c.primary.copy(alpha = 0.16f).compositeOver(c.surface.copy(alpha = GlassCards.READING_ALPHA).compositeOver(under)), under, alpha = 1f
            )
          )
        }
      }
      waitForIdle()
      println("GLASS-CONTRAST $mode " + results.joinToString { "${it.first}=${"%.2f".format(it.second)}" })
      assertTrue(results.isNotEmpty())
      results.forEach { (n, r) -> assertTrue("$mode $n on glass over the worst silk: $r", r >= 4.5) }
    }
  }

  /** The cards are translucent: over a magenta backdrop the card's pixel is the surface at the glass alpha, not the opaque card colour. */
  @Test fun cardsLetTheBackdropThrough() {
    val lib = seeded()
    val backdrop = Color(0xFFFF00FF)
    var surface = Color.Unspecified
    var opaque = Color.Unspecified
    runDesktopComposeUiTest(1280, 800) {
      setContent {
        TarkasTheme(themeMode = "dark") {
          surface = MaterialTheme.colorScheme.surface; opaque = MaterialTheme.colorScheme.surfaceContainer
          Box(Modifier.requiredSize(1280.dp, 800.dp).background(backdrop).testTag("root")) { LibraryScreen(lib, Modifier) }
        }
      }
      waitForIdle(); Thread.sleep(1200); waitForIdle()
      val img = onNodeWithTag("root").captureToImage().toAwtImage()
      // An empty stretch of the sidebar card, low and well inside it (its rows end above y = 600).
      val px = img.getRGB(60, 760)
      val got = Color(px)
      val want = surface.copy(alpha = GlassCards.READING_ALPHA).compositeOver(backdrop)
      fun d(a: Float, b: Float) = abs(a - b) * 255f
      assertTrue("pixel $got must be the glass $want (surface over the backdrop)", d(got.red, want.red) < 3f && d(got.green, want.green) < 3f && d(got.blue, want.blue) < 3f)
      assertNotEquals("not the opaque card", opaque.toArgb(), got.toArgb())
    }
  }

  private fun srgb(lin: Double): Double = if (lin <= 0.0031308) 12.92 * lin else 1.055 * lin.pow(1 / 2.4) - 0.055
}
