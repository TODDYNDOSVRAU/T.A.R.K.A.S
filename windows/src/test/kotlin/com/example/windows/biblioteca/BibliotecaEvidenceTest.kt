package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.AppDatabase
import com.example.data.model.Track
import com.example.ui.theme.TarkasTheme
import com.example.windows.audio.DesktopPlayer
import com.example.windows.player.FakeOut
import com.example.windows.player.PlayerController
import com.example.windows.state.TarkasState
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Evidence for Início and Biblioteca: the window area only (compose ui-test captureToImage, no screen grab, no sound)
 * at four window sizes in both themes. Runs only when the environment variable TAL_EVIDENCE names a folder.
 */
@OptIn(ExperimentalTestApi::class)
class BibliotecaEvidenceTest {
  private val sizes = listOf(1024 to 640, 1366 to 768, 1920 to 1080, 2560 to 1440)
  private val dir: File? = System.getenv("TAL_EVIDENCE")?.takeIf { it.isNotBlank() }?.let(::File)

  private fun seeded(): BibliotecaState {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val app = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
    val ctl = PlayerController(FakeOut(), com.example.windows.player.MemoryPrefs(), scope)
    val tmp = Files.createTempDirectory("tal-ev").toFile()
    val lib = BibliotecaState(app, ctl, scope, dataDir = tmp)
    val artists = listOf("Marisa Alves", "The Long Walk", "Nora Quinn", "Cais do Porto", "Eletrofonia")
    val tracks = (1..30).map { i ->
      Track(
        id = "ev$i", title = "Faixa de teste número $i com título longo", artist = artists[i % artists.size],
        album = "Álbum ${i % 6}", durationMs = 180_000L + i * 1000, genre = listOf("Rock", "Jazz", "Pop")[i % 3], era = "2020s",
        isLocalFile = true, filePath = "C:/nao-existe/ev$i.mp3"
      )
    }
    tracks.forEach { lib.store.addPicked(it) }
    Thread.sleep(400)
    lib.store.createPlaylist("Para correr", tracks.take(8))
    lib.store.toggleFavorite(tracks[1]); lib.store.toggleFavorite(tracks[2])
    Thread.sleep(400)
    return lib
  }

  private fun contrast(a: Color, b: Color) = contrastRatio(a, b)

  /** Stands in for the player's queue stack (win-player's), only to show where the Home gives it room. */
  @androidx.compose.runtime.Composable
  private fun QueueStandIn() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).testTag("queue-stack"))
  }

  private fun shoot(name: String, w: Int, h: Int, mode: String, lib: BibliotecaState, act: (androidx.compose.ui.test.ComposeUiTest.() -> Unit)? = null, screen: @androidx.compose.runtime.Composable (BibliotecaState) -> Unit) = runDesktopComposeUiTest(w, h) {
    val checks = mutableListOf<Pair<String, Double>>()
    setContent {
      TarkasTheme(themeMode = mode) {
        val c = MaterialTheme.colorScheme
        val card = c.surfaceContainer
        checks += "onSurface/card" to contrast(c.onSurface, card)
        checks += "onSurfaceVariant/card" to contrast(c.onSurfaceVariant, card)
        val sel = c.primary.copy(alpha = 0.16f).compositeOver(card)
        checks += "selectedLabel/selected" to contrast(legibleOn(c.primary, sel, c.onSurface), sel)
        checks += "primary/card(raw)" to contrast(c.primary, card)
        Box(Modifier.requiredSize(w.dp, h.dp).background(c.background).testTag("root")) { screen(lib) }
      }
    }
    waitForIdle()
    Thread.sleep(1500)
    waitForIdle()
    act?.invoke(this)
    waitForIdle()
    Thread.sleep(400)
    val img = onNodeWithTag("root").captureToImage().toAwtImage()
    assertTrue("capture is ${img.width}x${img.height}", img.width > 0 && img.height > 0)
    dir?.let { ImageIO.write(img, "png", File(it.also(File::mkdirs), "$name-${w}x$h-$mode.png")) }
    checks.forEach { (n, r) -> assertTrue("$name $mode $n contrast $r", r >= 4.5) }
    println("EVIDENCE $name ${w}x$h $mode ${img.width}x${img.height} " + checks.joinToString { "${it.first}=${"%.2f".format(it.second)}" })
  }

  @Test fun inicioAndBiblioteca() {
    if (dir == null) return
    val lib = seeded()
    for ((w, h) in sizes) for (mode in listOf("light", "dark")) {
      shoot("inicio", w, h, mode, lib) { HomeScreen(it, Modifier, queueStack = { QueueStandIn() }) }
      // The 350 ms debounce runs on the test clock, so the clock is moved past it before the capture.
      shoot("inicio-busca", w, h, mode, lib, act = { mainClock.advanceTimeBy(800) }) { HomeScreen(it, Modifier, query = "faixa", queueStack = { QueueStandIn() }) }
      shoot("biblioteca", w, h, mode, lib) { LibraryScreen(it, Modifier) }
      shoot("detalhe", w, h, mode, lib, act = {
        onNodeWithText("Para correr").performClick(); waitForIdle()
        onAllNodesWithText("Faixa de teste número 1 com título longo")[0].performClick(); waitForIdle()
      }) { LibraryScreen(it, Modifier) }
    }
  }

  /** The same screens with nothing imported: they must read as a state of their own, not as a blank page with one button. */
  @Test fun emptyLibrary() {
    if (dir == null) return
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val app = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
    app.settings.put(BibliotecaState.SCOPE, BibliotecaState.KEY_ROOTS, "")
    val ctl = PlayerController(FakeOut(), com.example.windows.player.MemoryPrefs(), scope)
    val lib = BibliotecaState(app, ctl, scope, dataDir = Files.createTempDirectory("tal-ev-empty").toFile())
    Thread.sleep(600)
    for ((w, h) in sizes) for (mode in listOf("light", "dark")) {
      shoot("inicio-vazio", w, h, mode, lib) { HomeScreen(it, Modifier, queueStack = { QueueStandIn() }) }
      shoot("biblioteca-vazia", w, h, mode, lib) { LibraryScreen(it, Modifier) }
    }
  }

  /** Backspace in the library closes the details pane of a playlist and returns the table to the full width. */
  @Test fun backspaceClosesDetail() {
    val lib = seeded()
    runDesktopComposeUiTest(1366, 768) {
      setContent { TarkasTheme(themeMode = "dark") { Box(Modifier.requiredSize(1366.dp, 768.dp).testTag("root")) { LibraryScreen(lib, Modifier) } } }
      waitForIdle(); Thread.sleep(1200); waitForIdle()
      onNodeWithText("Para correr").performClick(); waitForIdle()
      onAllNodesWithText("Faixa de teste número 1 com título longo")[0].performClick(); waitForIdle()
      fun open() = onAllNodesWithText("Arquivo").fetchSemanticsNodes().size + onAllNodesWithText("File").fetchSemanticsNodes().size
      assertTrue("details pane should be open after selecting a row", open() > 0)
      onNodeWithTag("root").performKeyInput { pressKey(Key.Backspace) }
      waitForIdle()
      assertTrue("Backspace should close the details pane", open() == 0)
    }
  }
}
