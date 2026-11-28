package com.example.windows.equalizador

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.withFrameNanos
import com.example.data.db.AppDatabase
import com.example.data.model.EqBand
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.shell.TarkasShell
import com.example.windows.state.TarkasState
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The owner's scene in a REAL window (the renderer the app really uses: Skiko's default, Direct3D on Windows),
 * the Pre-amp swept -15 .. +15 .. -15 on its own for 10 s while `ffmpeg -f gdigrab` records that window. The
 * offscreen tests are raster and cannot show a GPU defect. Runs only with `EQGRAPH_REAL_OUT=<file.mkv>`.
 * Needs `ffmpeg` on PATH and an unlocked interactive desktop; the window sits at the top-left corner, 1366 x 768
 * unless `EQGRAPH_REAL_SIZE=<w>x<h>` says otherwise (1918x986 is the owner's maximised window).
 */
class EqGraphRealWindowTest {
  @Test fun sweepPreampInARealWindow() {
    val outFile = System.getenv("EQGRAPH_REAL_OUT")
    assumeTrue(outFile != null)
    File(outFile!!).apply { parentFile?.mkdirs(); delete() }
    val title = "EQGRAPH-REAL"
    // EQGRAPH_REAL_SIZE=1918x986 matches the owner's maximised window; the default is 1366x768.
    val (winW, winH) = (System.getenv("EQGRAPH_REAL_SIZE") ?: "1366x768").split("x").map { it.toInt() }
    var ffmpeg: Process? = null
    application(exitProcessOnExit = false) {
      val state = remember { TarkasState(DesktopPlayer(), AppDatabase.inMemory()) }
      val audio = remember { AudioHost.audio(state) }
      val win = rememberWindowState(position = WindowPosition(0.dp, 0.dp), size = DpSize(winW.dp, winH.dp))
      Window(onCloseRequest = ::exitApplication, title = title, state = win, undecorated = true, resizable = false, alwaysOnTop = true) {
        remember {
          com.example.windows.shell.Panels.slots.clear()
          AudioHost.register(com.example.windows.shell.Panels.slots)
          state.setThemeMode("dark")
          state.selectTab(TarkasNavTab.AUDIO)
          audio.updateEq { s ->
            s.withLayout(true).copy(bands = listOf(EqBand(freqHz = 1000f, gainDb = 0f)), bassDb = -12f, trebleDb = 12f, preampDb = -15f)
          }
        }
        TarkasShell(state, BackDispatcher(), AppLifecycle())
        LaunchedEffect(Unit) {
          delay(2500) // window up, entrance finished
          ffmpeg = ProcessBuilder(
            "ffmpeg", "-y", "-loglevel", "error", "-f", "gdigrab", "-framerate", "30", "-draw_mouse", "0",
            "-offset_x", "0", "-offset_y", "0", "-video_size", "${winW}x$winH", "-i", "desktop", "-t", "11", "-c:v", "ffv1", outFile
          ).redirectErrorStream(true).redirectOutput(File("$outFile.log")).start()
          delay(800)
          val start = System.nanoTime()
          while (true) {
            val t = (System.nanoTime() - start) / 1e9
            if (t > 10.0) break
            // triangle: -15 -> +15 in 5 s, back in 5 s
            val k = if (t < 5.0) t / 5.0 else (10.0 - t) / 5.0
            val db = (-15.0 + 30.0 * k).toFloat()
            audio.updateEq { it.copy(preampDb = db) }
            withFrameNanos { }
          }
          delay(800)
          ffmpeg?.waitFor(5, TimeUnit.SECONDS)
          exitApplication()
        }
      }
    }
    ffmpeg?.takeIf { it.isAlive }?.destroy()
  }
}
