package com.example.windows.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.example.data.db.AppDatabase
import com.example.ui.theme.Grid
import com.example.ui.theme.TarkasTheme
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Wave 0 frames for the owner: the whole shell on the marble (Ilha centred, Ajustes at the top right, corner at the
 * bottom left with the card room on Início only) in both themes, and the collapsed Ilha while music plays.
 * Written to build/shell-evidence; the area panels are placeholders here, they belong to their sessions.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ShellEvidenceTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val slots = Panels.slots.toMap(); private val titles = Panels.topTitle.toMap()
  private val corner = Panels.corner; private val transport = Panels.transport

  @Before fun setUp() {
    Panels.slots.clear(); Panels.topTitle.clear(); Panels.corner = null
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear()
    Panels.topTitle[TarkasNavTab.HOME] = PanelContent { Text("Boa noite", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground) }
    Panels.transport = PanelContent {
      Box(Modifier.fillMaxSize().padding(Grid.u24), contentAlignment = Alignment.CenterStart) { Text("mini-player (win-player)", color = MaterialTheme.colorScheme.onSurface) }
    }
  }

  @After fun tearDown() {
    Panels.slots.clear(); Panels.slots.putAll(slots); Panels.topTitle.clear(); Panels.topTitle.putAll(titles)
    Panels.corner = corner; Panels.transport = transport
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear()
    scope.cancel()
  }

  private fun write(scene: ImageComposeScene, file: File) {
    scene.render(0L); scene.render(16_000_000L)
    Thread.sleep(300) // the marble is baked on Dispatchers.Default
    file.writeBytes(scene.render(400_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
  }

  @Test fun shellAndPlayingIslandInBothThemes() {
    val dir = File("build/shell-evidence").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    for (theme in listOf("dark", "light")) {
      for (tab in listOf(TarkasNavTab.HOME, TarkasNavTab.LIBRARY)) {
        val state = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
        state.setThemeMode(theme); state.selectTab(tab)
        write(ImageComposeScene(1280, 800, Density(1f)) { TarkasShell(state, BackDispatcher(), AppLifecycle()) },
          File(dir, "$theme-shell-${tab.name.lowercase()}.png"))
      }
      // The collapsed Ilha while playing: five bars around the play position, the output icon, the live dot.
      // A loud passage: neighbouring buckets differ, as in a real waveform (0.4 of the track lands on buckets 157..161).
      IslandActivity.peaks = MutableStateFlow(ByteArray(400) { listOf(150, 230, 120, 255, 180)[it % 5].toByte() })
      IslandActivity.output = MutableStateFlow(TarkasNavTab.AUDIO.icon())
      write(ImageComposeScene(720, 144, Density(1.5f)) {
        TarkasTheme(themeMode = theme) {
          Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = Grid.u12), contentAlignment = Alignment.TopCenter) {
            FloatingDock(TarkasNavTab.HOME, {}, reduceMotion = false, compact = false, playing = true, fraction = { 0.4f })
          }
        }
      }, File(dir, "$theme-island-playing.png"))
      IslandActivity.reset()
    }
    assertEquals(6, dir.listFiles()!!.size)
  }
}
