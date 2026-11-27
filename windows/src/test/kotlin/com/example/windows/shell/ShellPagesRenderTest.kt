package com.example.windows.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.example.data.db.AppDatabase
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.assistente.AssistentePanels
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.biblioteca.BibliotecaHost
import com.example.windows.equalizador.AudioHost
import com.example.windows.geral.GeralPanels
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
 * Owner evidence: every page of the dock (Início, Biblioteca, Música, Áudio, Assistente, Ajustes), registered the way
 * Main.kt does it and rendered through the real shell in both themes at the owner's window size, into
 * build/pages-render. Writes PNGs only; asserts nothing about pixels.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ShellPagesRenderTest {
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
  }

  @After fun tearDown() {
    Panels.slots.clear(); Panels.slots.putAll(savedSlots)
    Panels.topTitle.clear(); Panels.topTitle.putAll(savedTitles)
    Panels.corner = savedCorner; Panels.transport = savedTransport
    Panels.sidePlayer = savedSide; Panels.queueStack = savedStack
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear()
    scope.cancel()
  }

  @Test fun everyPageInBothThemes() {
    val dir = File("build/pages-render").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
    val tabs = System.getenv("TARKAS_PAGES")?.split(',')?.map { TarkasNavTab.valueOf(it.trim()) } ?: NavOrder
    for (theme in listOf("dark", "light")) {
      val state = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
      Panels.slots.clear(); Panels.topTitle.clear()
      PlayerHost.register(Panels.slots)
      BibliotecaHost.register(Panels.slots)
      AudioHost.register(Panels.slots)
      AssistentePanels.create(state).install(Panels.slots)
      GeralPanels.create(state).install()
      state.setThemeMode(theme)
      for (tab in tabs) {
        SidePane.close(); state.selectTab(tab)
        val scene = ImageComposeScene(1280, 800, Density(1f)) { TarkasShell(state, BackDispatcher(), AppLifecycle()) }
        scene.render(0L); scene.render(16_000_000L)
        Thread.sleep(300) // backgrounds and pages that load on Dispatchers.Default
        scene.render(200_000_000L); scene.render(600_000_000L)
        // Capture once entrances have settled (the Áudio bands stagger in; 600 ms caught them mid-flight).
        File(dir, "$theme-${tab.name.lowercase()}.png").writeBytes(scene.render(1_500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
      }
    }
    check(dir.listFiles()!!.size == tabs.size * 2)
  }
}
