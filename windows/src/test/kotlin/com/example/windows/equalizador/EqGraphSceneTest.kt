package com.example.windows.equalizador

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.example.data.audio.loudness.LoudnessUi
import com.example.data.model.AudioCapabilities
import com.example.data.model.EqBand
import com.example.data.model.EqPresets
import com.example.data.model.EqSettings
import com.example.ui.theme.TarkasTheme
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The scene of the owner's 2026-11-27 video (Parametrico, one band at 1 kHz / 0 dB, Graves -12, Agudos +12) at
 * the Pre-amp values -15, -10, 0 and +13, in both themes. Runs only with `EQGRAPH_DIR=<folder>`. The offscreen
 * renderer is raster: it proves the clip and the geometry (curve inside the plot, axis row legible), not the GPU
 * checkerboard, which `docs/` records separately from a real-window capture.
 */
@OptIn(ExperimentalTestApi::class)
class EqGraphSceneTest {
  @Test fun videoSceneAtFourPreamps() {
    val dir = System.getenv("EQGRAPH_DIR") ?: System.getProperty("eqgraph.dir")
    assumeTrue(dir != null)
    val out = File(dir!!).also { it.mkdirs() }
    val scene = EqSettings().withLayout(true).copy(
      bands = listOf(EqBand(freqHz = 1000f, gainDb = 0f)), bassDb = -12f, trebleDb = 12f
    )
    for (theme in listOf("dark", "light")) for ((w, h) in listOf(1366 to 768, 1024 to 640)) for (pre in listOf(-15f, -10f, 0f, 13f)) {
      runDesktopComposeUiTest(width = w, height = h) {
        setContent {
          TarkasTheme(themeMode = theme) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
              AudioScreen(
                capabilities = AudioCapabilities(
                  deviceModel = "Windows PC", androidVersion = "Windows 11", apiLevel = 0, activeRouteName = "Speakers",
                  activeRouteType = AudioCapabilities.ROUTE_OTHER, sampleRateHz = 48_000, channelCount = 2, channelFormat = "stereo",
                  isAudioSessionActive = true, audioSessionId = 0, isHardwareEqualizerAvailable = false, isBassBoostAvailable = false,
                  isVirtualizerAvailable = false, effectsCount = 0
                ),
                eqSettings = scene.copy(preampDb = pre), presets = EqPresets.BUILT_IN, activePresetId = EqPresets.BUILT_IN.first().id, eqModified = true,
                spectrumBars = { emptyList() }, loudness = LoudnessUi(),
                onSetEnabled = {}, onUpdateEq = {}, onUpdateBand = { _, _ -> }, onSelectPreset = {}, onSavePresetAs = {},
                onOverwritePreset = {}, onRenamePreset = { _, _ -> }, onDeletePreset = {}, onReset = {}
              )
            }
          }
        }
        waitForIdle()
        mainClock.advanceTimeBy(1500)
        waitForIdle()
        val name = "$theme-${w}x$h-preamp${pre.toInt().toString().replace("-", "m")}.png"
        assertTrue(ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, name)))
      }
    }
  }
}
