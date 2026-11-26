package com.example.windows.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.theme.Grid
import com.example.ui.theme.Shapes
import com.example.ui.theme.TarkasTheme
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Validation of the NOVA UI player at the four desktop sizes in light and dark: the pill inside the dock's
 * 384x96 box, and the always-dark full player. Measures with the test's virtual clock (open and close time),
 * resizes the area with the panel open, and writes window-only captures (captureToImage of the stage, never the display).
 *
 * The stage is a Box inside one 2560x1440 test window; resizing means new constraints for the panel, as
 * the shell gives it when the OS window changes. A real OS window resize and real frame pacing are not covered.
 */
@OptIn(ExperimentalTestApi::class)
class PlayerValidationTest {
  private val sizes = listOf(1024 to 640, 1366 to 768, 1920 to 1080, 2560 to 1440)
  private val themes = listOf("light", "dark")
  private val outDir = File(System.getProperty("player.capture.dir") ?: "build/ui-evidence/player").also { it.mkdirs() }

  private fun track(n: Int) = Track(id = "t$n", title = "Track number $n with a fairly long title", artist = "Artist $n", album = "Album", durationMs = 200_000, genre = "", era = "", isLocalFile = true, filePath = "C:/x/t$n.wav")

  private class Fixture {
    val out = FakeOut()
    val controller = PlayerController(out, MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
  }

  @Before @After fun reset() { ExpandedPlayer.close(); ExpandedPlayer.progress = 0f }

  private fun SemanticsNode.r(): Rect = boundsInRoot

  private fun inside(inner: Rect, outer: Rect, what: String) {
    val e = 0.5f
    assertTrue("$what $inner is not inside $outer", inner.left >= outer.left - e && inner.top >= outer.top - e && inner.right <= outer.right + e && inner.bottom <= outer.bottom + e)
  }

  private fun apart(a: Rect, b: Rect, what: String) {
    assertFalse("$what overlap: $a and $b", a.left < b.right - 0.5f && b.left < a.right - 0.5f && a.top < b.bottom - 0.5f && b.top < a.bottom - 0.5f)
  }

  private fun png(name: String, node: androidx.compose.ui.test.SemanticsNodeInteraction) {
    val image = node.captureToImage().toAwtImage()
    assertTrue(ImageIO.write(image, "png", File(outDir, "$name.png")))
  }

  /** Advances the virtual clock one frame at a time until [done]; returns the milliseconds it took. */
  private fun androidx.compose.ui.test.ComposeUiTest.runFrames(limitMs: Int = 2000, done: () -> Boolean): Int {
    var ms = 0
    while (!done() && ms < limitMs) { mainClock.advanceTimeBy(16); ms += 16 }
    return ms
  }

  private fun luminance(rgb: Int): Double {
    val r = (rgb shr 16 and 255) / 255.0; val g = (rgb shr 8 and 255) / 255.0; val b = (rgb and 255) / 255.0
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }

  @Test fun miniPillAndFullPlayerAtEverySizeAndTheme() {
    val report = StringBuilder()
    for ((w, h) in sizes) for (theme in themes) runDesktopComposeUiTest(width = 2560, height = 1440) {
      val f = Fixture()
      f.controller.play(track(1), listOf(track(1), track(2), track(3)))
      var stageW by mutableStateOf(w); var stageH by mutableStateOf(h)
      setContent {
        TarkasTheme(themeMode = theme) {
          Box(Modifier.size(stageW.dp, stageH.dp).testTag("stage").background(MaterialTheme.colorScheme.background)) {
            // The dock: a 384x96 pill box, 12 above the bottom, exactly as the shell draws it.
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Grid.u12).size(Grid.u384, Grid.u96).clip(Shapes.pill).background(MaterialTheme.colorScheme.surfaceContainer)) {
              MiniPlayerBody(f.hooks, f.assets)
            }
            ExpandedPlayerPanel(f.hooks, f.assets)
          }
        }
      }
      mainClock.autoAdvance = true
      waitForIdle()
      val stage = onNodeWithTag("stage")

      // Pill, closed player: everything stays inside the 384x96 box.
      val mini = onNodeWithTag("mini_player").fetchSemanticsNode().r()
      assertEquals("pill width at ${w}x$h $theme", 384f, mini.width, 0.5f)
      assertEquals("pill height at ${w}x$h $theme", 96f, mini.height, 0.5f)
      val play = onNodeWithTag("mini_play", useUnmergedTree = true).fetchSemanticsNode().r()
      val prog = onNodeWithTag("mini_progress", useUnmergedTree = true).fetchSemanticsNode().r()
      inside(play, mini, "play"); inside(prog, mini, "progress"); apart(play, prog, "play/progress")
      assertEquals("prev/next hidden until hover", 0, onAllNodesWithTag("mini_prev").fetchSemanticsNodes().size)
      onNodeWithTag("mini_player").performMouseInput { moveTo(center) }
      waitForIdle()
      val prev = onNodeWithTag("mini_prev", useUnmergedTree = true).fetchSemanticsNode().r()
      val next = onNodeWithTag("mini_next", useUnmergedTree = true).fetchSemanticsNode().r()
      val play2 = onNodeWithTag("mini_play", useUnmergedTree = true).fetchSemanticsNode().r()
      inside(prev, mini, "prev"); inside(next, mini, "next"); apart(prev, play2, "prev/play"); apart(play2, next, "play/next")
      assertTrue("48 dp touch targets", prev.width >= 47.5f && play2.width >= 47.5f && next.width >= 47.5f && prev.height >= 47.5f)
      png("mini-${w}x$h-$theme", stage)
      onNodeWithTag("mini_player").performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(-50f, -50f)) }

      // Open: the click on the pill opens the panel; time it on the virtual clock.
      mainClock.autoAdvance = false
      ExpandedPlayer.progress = 0f
      onNodeWithTag("mini_player").performClick()
      val openMs = runFrames(1000) { ExpandedPlayer.progress >= 1f }
      assertTrue("open took ${openMs} ms at ${w}x$h $theme", openMs in 1..300)
      mainClock.autoAdvance = true
      waitForIdle()

      val panel = onNodeWithTag("expanded_player").fetchSemanticsNode().r()
      val area = stage.fetchSemanticsNode().r()
      assertEquals(area.width, panel.width, 0.5f); assertEquals(area.height, panel.height, 0.5f)
      val cover = onNodeWithTag("exp_cover").fetchSemanticsNode().r()
      val info = onNodeWithTag("exp_info").fetchSemanticsNode().r()
      val seek = onNodeWithTag("exp_seek").fetchSemanticsNode().r()
      val ctl = onNodeWithTag("exp_controls").fetchSemanticsNode().r()
      val close = onNodeWithTag("exp_close").fetchSemanticsNode().r()
      for ((n, b) in listOf("cover" to cover, "info" to info, "seek" to seek, "controls" to ctl, "close" to close)) inside(b, area, "$n at ${w}x$h $theme")
      listOf(cover.width, cover.height).forEach { assertTrue("cover ${cover.width} is on the grid", it in listOf(96f, 192f, 384f)) }
      assertEquals("cover is the size the math picks", ExpandMath.coverDp(h.toFloat(), w.toFloat()).toFloat(), cover.width, 1f)
      apart(cover, info, "cover/info"); apart(cover, seek, "cover/seek"); apart(cover, ctl, "cover/controls")
      assertTrue("info above seek", info.bottom <= seek.top + 0.5f)
      assertTrue("seek above controls", seek.bottom <= ctl.top + 0.5f)
      assertTrue("controls: 48 dp targets keep their height", ctl.height >= 47.5f)

      // Always dark: the panel is dark in the light theme too.
      val img = stage.captureToImage().toAwtImage()
      val lum = luminance(img.getRGB(w - 8, h - 8))
      assertTrue("panel background luminance $lum at ${w}x$h $theme", lum < 0.2)
      png("full-${w}x$h-$theme", stage)

      // Close timing.
      mainClock.autoAdvance = false
      ExpandedPlayer.close()
      val closeMs = runFrames(1000) { ExpandedPlayer.progress <= 0f }
      assertTrue("close took ${closeMs} ms at ${w}x$h $theme", closeMs in 1..260)
      mainClock.autoAdvance = true
      waitForIdle()
      assertEquals("panel removed after close", 0, onAllNodesWithTag("expanded_player").fetchSemanticsNodes().size)
      report.append("size=${w}x$h theme=$theme open_ms=$openMs close_ms=$closeMs cover=${cover.width.toInt()}\n")
    }
    File(outDir, "timing.txt").writeText(report.toString())
    println("PLAYER-VALIDATION\n$report")
  }

  @Test fun progressIsMonotonicAndSteppedPerFrame() = runDesktopComposeUiTest(width = 1366, height = 768) {
    val f = Fixture(); f.controller.play(track(1), listOf(track(1)))
    setContent { TarkasTheme(themeMode = "dark") { Box(Modifier.fillMaxSize()) { ExpandedPlayerPanel(f.hooks, f.assets) } } }
    mainClock.autoAdvance = false
    ExpandedPlayer.show()
    val samples = mutableListOf<Float>()
    var ms = 0
    while (ExpandedPlayer.progress < 1f && ms < 1000) { mainClock.advanceTimeBy(16); ms += 16; samples += ExpandedPlayer.progress }
    assertTrue("monotonic: $samples", samples.zipWithNext().all { (a, b) -> b >= a })
    assertTrue("the frames are not skipped: ${samples.size} samples in $ms ms", samples.size >= 12)
    assertTrue("no frame jumps more than 25%: $samples", samples.zipWithNext().all { (a, b) -> b - a <= 0.25f })
  }

  @Test fun resizingWithThePanelOpenKeepsEverythingInside() = runDesktopComposeUiTest(width = 2560, height = 1440) {
    val f = Fixture(); f.controller.play(track(1), listOf(track(1), track(2)))
    var stageW by mutableStateOf(2560); var stageH by mutableStateOf(1440)
    setContent {
      TarkasTheme(themeMode = "light") {
        Box(Modifier.size(stageW.dp, stageH.dp).testTag("stage")) { ExpandedPlayerPanel(f.hooks, f.assets) }
      }
    }
    ExpandedPlayer.show(); waitForIdle()
    val covers = mutableListOf<Int>()
    for ((w, h) in listOf(2560 to 1440, 1920 to 1080, 1366 to 768, 1024 to 640, 1366 to 768, 2560 to 1440)) {
      stageW = w; stageH = h; waitForIdle()
      val area = onNodeWithTag("stage").fetchSemanticsNode().r()
      assertEquals(w.toFloat(), area.width, 0.5f)
      val cover = onNodeWithTag("exp_cover").fetchSemanticsNode().r()
      val info = onNodeWithTag("exp_info").fetchSemanticsNode().r()
      val seek = onNodeWithTag("exp_seek").fetchSemanticsNode().r()
      val ctl = onNodeWithTag("exp_controls").fetchSemanticsNode().r()
      for ((n, b) in listOf("cover" to cover, "info" to info, "seek" to seek, "controls" to ctl)) inside(b, area, "$n at ${w}x$h")
      apart(cover, info, "cover/info"); apart(cover, ctl, "cover/controls")
      assertTrue(info.bottom <= seek.top + 0.5f && seek.bottom <= ctl.top + 0.5f)
      covers += cover.width.toInt()
      assertEquals("playback untouched by the resize", true, f.out.st.value.playing)
    }
    println("RESIZE covers=$covers")
    assertEquals("the cover follows the area both ways", listOf(384, 384, 384, 192, 384, 384), covers)
  }

  @Test fun chevronClosesAndPlaybackKeepsGoing() = runDesktopComposeUiTest(width = 1366, height = 768) {
    val f = Fixture(); f.controller.play(track(1), listOf(track(1)))
    setContent { TarkasTheme(themeMode = "dark") { Box(Modifier.fillMaxSize()) { ExpandedPlayerPanel(f.hooks, f.assets) } } }
    ExpandedPlayer.show(); waitForIdle()
    onNodeWithTag("exp_close").performClick()
    waitForIdle()
    assertFalse(ExpandedPlayer.open.value)
    assertEquals(0, onAllNodesWithTag("expanded_player").fetchSemanticsNodes().size)
    assertTrue("music still playing", f.out.st.value.playing)
  }
}
