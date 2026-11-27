package com.example.windows.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.example.data.db.AppDatabase
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.biblioteca.BibliotecaHost
import com.example.windows.equalizador.AudioHost
import com.example.windows.player.PlayerHost
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Owner evidence: the real Início (Biblioteca's HomeScreen, the Player's corner, the greeting), registered the same way
 * Main.kt does it, rendered in both themes into build/home-render. Writes PNGs only; asserts nothing about pixels.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ShellHomeRenderTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val savedSlots = Panels.slots.toMap()
  private val savedTitles = Panels.topTitle.toMap()
  private val savedCorner = Panels.corner
  private val savedTransport = Panels.transport
  private val savedSide = Panels.sidePlayer
  private val savedStack = Panels.queueStack

  @Before fun setUp() {
    Panels.slots.clear(); Panels.topTitle.clear()
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear()
    PlayerHost.register(Panels.slots)
    BibliotecaHost.register(Panels.slots)
    AudioHost.register(Panels.slots)
  }

  @After fun tearDown() {
    Panels.slots.clear(); Panels.slots.putAll(savedSlots)
    Panels.topTitle.clear(); Panels.topTitle.putAll(savedTitles)
    Panels.corner = savedCorner; Panels.transport = savedTransport
    Panels.sidePlayer = savedSide; Panels.queueStack = savedStack
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear()
    scope.cancel()
  }

  @Test fun realHomeInBothThemes() {
    val dir = File("build/home-render").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    for (theme in listOf("dark", "light")) {
      val state = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
      state.setThemeMode(theme); state.selectTab(TarkasNavTab.HOME)
      // 800: the window as the owner sees it. 1700: a tall window, so the whole Início fits without scrolling.
      for ((name, height) in listOf("full" to 800, "tall" to 1700)) {
        val scene = ImageComposeScene(1280, height, Density(1f)) { TarkasShell(state, BackDispatcher(), AppLifecycle()) }
        scene.render(0L); scene.render(16_000_000L)
        Thread.sleep(300) // the marble is baked on Dispatchers.Default
        File(dir, "$theme-home-$name.png").writeBytes(scene.render(400_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
      }
    }
    check(dir.listFiles()!!.size == 4)
  }
}
