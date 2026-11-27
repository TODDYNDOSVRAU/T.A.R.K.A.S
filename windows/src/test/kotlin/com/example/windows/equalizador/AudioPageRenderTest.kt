package com.example.windows.equalizador

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.example.data.db.AppDatabase
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import com.example.windows.shell.IslandActivity
import com.example.windows.shell.PanelContent
import com.example.windows.shell.Panels
import com.example.windows.shell.ShellBackdrop
import com.example.windows.shell.ShellIsland
import com.example.windows.shell.SidePane
import com.example.windows.shell.TarkasShell
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Evidence for the Audio page through the real shell, captured SETTLED (the band entrance finished, 1.5 s of scene
 * time) with a stand-in corner pill drawn, at the sizes the owner uses, in both themes. Runs only with `AUDIO_PAGE_DIR=<folder>`.
 */
@OptIn(ExperimentalComposeUiApi::class)
class AudioPageRenderTest {
  @Test fun settledAudioPage() {
    val dir = System.getenv("AUDIO_PAGE_DIR")
    assumeTrue(dir != null)
    val out = File(dir!!).apply { mkdirs() }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val saved = Panels.slots.toMap()
    val savedTransport = Panels.transport
    // A stand-in for the corner mini-player (the shell sizes and places it): magenta, so any overlap shows.
    Panels.transport = PanelContent { Box(Modifier.fillMaxSize().background(Color.Magenta.copy(alpha = 0.6f))) }
    try {
      for (theme in listOf("dark", "light")) {
        val state = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
        Panels.slots.clear(); Panels.topTitle.clear()
        SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear()
        AudioHost.register(Panels.slots)
        state.setThemeMode(theme)
        state.selectTab(TarkasNavTab.AUDIO)
        for ((w, h) in listOf(1280 to 800, 1024 to 640, 1366 to 768)) {
          val scene = ImageComposeScene(w, h, Density(1f)) { TarkasShell(state, BackDispatcher(), AppLifecycle()) }
          scene.render(0L); scene.render(16_000_000L)
          Thread.sleep(300)
          var t = 200_000_000L
          while (t <= 1_500_000_000L) { scene.render(t); t += 100_000_000L }
          File(out, "$theme-audio-${w}x$h.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes)
          scene.close()
        }
      }
    } finally {
      Panels.slots.clear(); Panels.slots.putAll(saved); Panels.transport = savedTransport; SidePane.close(); ShellIsland.reset(); IslandActivity.reset(); ShellBackdrop.clear()
      scope.cancel()
    }
  }
}
