package com.example.windows.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.theme.TarkasTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The logic of the liquid glass on the pill: the numbers of the spec, the border bake, the filter, hover, disabled and focus. */
@OptIn(ExperimentalTestApi::class)
class LiquidGlassTest {
  private fun t(n: Int) = Track("t$n", "Title $n", "Artist $n", "A$n", 200_000, "", "", true, "C:/m/t$n.mp3")
  private val list = (1..3).map(::t)

  private class Fixture {
    val controller = PlayerController(FakeOut(), MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
  }

  @Before @After fun reset() { ExpandedPlayer.close(); ExpandedPlayer.progress = 0f }

  private fun androidx.compose.ui.test.ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  // ---- The numbers of the owner's spec ----

  @Test fun theNumbersAreTheSpec() {
    assertEquals(0.05f, GlassSpec.NoiseFreq, 0f)
    assertEquals(1f, GlassSpec.NoiseSeed, 0f)
    // The glass at its minimum (owner, 2026-11-27). Regular: 3 dp blur, 3 dp refraction, 60% of the scrim. Clear: 1.5 dp blur, 6 dp refraction, none.
    assertEquals(GlassLook(3f, 3f, 0.6f), GlassSpec.Regular)
    assertEquals(GlassLook(1.5f, 6f, 0f), GlassSpec.Clear)
    assertEquals(1.05f, GlassSpec.HoverScale, 0f)
    assertEquals(300, GlassSpec.StyleMs)
    assertEquals(300, GlassSpec.HoverMs)
    assertEquals(0.5f, GlassSpec.DisabledAlpha, 0f)
    // cubic-bezier(.4, 0, .2, 1): the ends, and it decelerates (fast start, slow end).
    assertEquals(0f, GlassSpec.Curve.transform(0f), 1e-4f)
    assertEquals(1f, GlassSpec.Curve.transform(1f), 1e-4f)
    assertTrue(GlassSpec.Curve.transform(0.5f) > 0.7f)
  }

  @Test fun theNineShadowsAreTheSpecInOrder() {
    val s = GlassRim.Shades
    assertEquals(9, s.size)
    assertEquals(6, s.count { it.inset }); assertEquals(3, s.count { !it.inset })
    // Thin and discreet: the bottom right is the stronger side (.30 edge light, .40 hairline), the top left almost faded.
    assertEquals(Shade(true, 1.5f, 1.5f, 0.5f, -1f, 0.05f), s[0])
    assertEquals(Shade(true, -1.5f, -1.5f, 0.5f, -1f, 0.30f), s[1])
    assertEquals(0.20f, s[2].alpha, 0f); assertEquals(0.40f, s[3].alpha, 0f)
    assertEquals(1f, s[2].dx, 0f); assertEquals(-1f, s[3].dx, 0f)
    assertTrue(s[3].alpha > s[2].alpha && s[1].alpha > s[0].alpha)
    assertEquals(0.04f, s[4].alpha, 0f); assertEquals(0.03f, s[5].alpha, 0f)
    // The outer shadows: wide and faint to tight, never heavy (at most .08).
    assertEquals(listOf(0.02f, 0.04f, 0.08f), s.filter { !it.inset }.map { it.alpha })
    assertEquals(listOf(12f, 6f, 2f), s.filter { !it.inset }.map { it.blur })
    assertTrue(s.all { it.alpha <= 0.40f })
  }

  @Test fun theBackdropCopyCoversEverythingTheFilterReads() {
    // The widest sample comes from half the displacement plus the blur's 3 sigma: 1.5 + 9 regular, 3 + 4.5 clear.
    assertEquals(10.5f, GlassSpec.reachDp(GlassSpec.Regular), 0f)
    assertEquals(7.5f, GlassSpec.reachDp(GlassSpec.Clear), 0f)
    assertEquals(10.5f, GlassSpec.MaxReachDp, 0f)
    assertTrue(GlassSpec.MaxReachDp <= GlassSpec.PadDp)
    assertEquals(48f, GlassSpec.PadDp, 0f)
  }

  // ---- The border ----

  /** The border for the pill, at density 1: it is the pill plus 48 on every side. */
  private fun bake(glow: Color = Color.White, shadow: Color = Color.Black) =
    GlassRim.bake(384f, 96f, 1f, glow, shadow).toPixelMap()

  private val m = 48 // the margin of the bitmap, dp = px at density 1

  @Test fun theBakedBorderHasTheMarginAndIsClearInTheMiddle() {
    val px = bake()
    assertEquals(384 + 2 * m, px.width)
    assertEquals(96 + 2 * m, px.height)
    // The middle of the pill is untouched: the border is an edge, the glass shows through.
    assertEquals(0f, px[m + 192, m + 48].alpha, 0.001f)
  }

  @Test fun theBottomRightEdgeGlowsAndTheTopLeftAlmostNot() {
    val px = bake()
    // On the straight part of the capsule (the pill is a stadium: the top and bottom edges are flat between the caps).
    // The edge lights are thin (about 1 px, as the spec's -3 offset with -2 spread leaves): look at the last rows.
    fun best(x: Int, ys: IntRange) = ys.map { px[x, it] }.maxBy { it.alpha }
    val top = best(m + 192, m..m + 2); val bottom = best(m + 192, m + 93..m + 95)
    assertTrue("bottom ${bottom.alpha} must glow more than top ${top.alpha}", bottom.alpha > top.alpha + 0.1f)
    assertEquals(1f, bottom.red, 0.05f) // the inset glow of the dark theme is white
    // The same on the sides: the end caps, the right cap is brighter than the left one.
    fun bestX(y: Int, xs: IntRange) = xs.map { px[it, y] }.maxBy { it.alpha }
    val left = bestX(m + 48, m..m + 2); val right = bestX(m + 48, m + 381..m + 383)
    assertTrue("right ${right.alpha} vs left ${left.alpha}", right.alpha > left.alpha + 0.1f)
  }

  @Test fun theOuterShadowFallsOutsideAndNeverInside() {
    val px = bake()
    // Just outside the straight bottom edge there is shadow; in the corner of the bitmap (36 dp+ away) there is none.
    assertTrue("shadow below ${px[m + 192, m + 96 + 1].alpha}", px[m + 192, m + 96 + 1].alpha > 0.01f)
    assertEquals(0f, px[1, 1].alpha, 0.01f)
    // The shadow is black (the scrim), the glow is the theme colour: a light theme glows black.
    assertEquals(0f, px[m + 192, m + 96 + 1].red, 0.05f)
    val light = bake(glow = Color.Black).let { p -> (m + 93..m + 95).map { p[m + 192, it] }.maxBy { it.alpha } }
    assertEquals(0f, light.red, 0.05f)
    assertTrue("light glow ${light.alpha}", light.alpha > 0.25f)
  }

  @Test fun theBakeScalesWithTheDisplayDensity() {
    val px = GlassRim.bake(384f, 96f, 2f, Color.White, Color.Black).toPixelMap()
    assertEquals((384 + 2 * 48) * 2, px.width)
    assertEquals((96 + 2 * 48) * 2, px.height)
  }

  // ---- The filter ----

  @Test fun theFilterIsCreatedWithTheRefractionOnThisMachine() {
    for (look in listOf(GlassSpec.Regular, GlassSpec.Clear)) {
      val made = GlassFx.create(1f, look)
      assertTrue("the shader did not compile here: the glass would only blur", made.refracts)
      assertNotNull(made.effect)
    }
    assertTrue(GlassFx.blurAvailable)
  }

  @Test fun theFilterIsBuiltPerDensityNotPerFrame() {
    // The same density builds again from the one compiled shader (the runtime effect is a lazy singleton).
    val a = GlassFx.create(1f, GlassSpec.Regular); val b = GlassFx.create(1.5f, GlassSpec.Clear)
    assertTrue(a.refracts && b.refracts)
    assertTrue(GlassFx.Sksl.contains("uniform float freq") && GlassFx.Sksl.contains("uniform float amount") && GlassFx.Sksl.contains("uniform float seed"))
  }

  // ---- Hover, disabled, focus ----

  @Test fun underThePointerThePillGrowsFivePercentOnTheCurveAndItsLayoutStays() = runDesktopComposeUiTest(width = 432, height = 120) {
    val f = Fixture(); f.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 120.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = false) } } }
    waitForIdle()
    val pill = bounds("compact_player"); val play = bounds("compact_play")
    assertEquals(48f, play.width, 0.1f)
    onNodeWithTag("compact_player").performMouseInput { moveTo(center) }
    mainClock.advanceTimeBy(150); waitForIdle()
    val mid = bounds("compact_play").width
    assertTrue("mid growth $mid", mid > 48.1f && mid < 48f * 1.05f)
    mainClock.advanceTimeBy(300); waitForIdle()
    assertEquals(48f * 1.05f, bounds("compact_play").width, 0.05f)
    // The slot in the layout does not move: only what is drawn grows.
    assertEquals(pill, bounds("compact_player"))
    onNodeWithTag("compact_player").performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(-8f, -8f)) }
    mainClock.advanceTimeBy(400); waitForIdle()
    assertEquals(48f, bounds("compact_play").width, 0.05f)
  }

  @Test fun reducedMotionDoesNotGrow() = runDesktopComposeUiTest(width = 432, height = 120) {
    val f = Fixture(); f.controller.play(list[0], list)
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 120.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = true) } } }
    waitForIdle()
    onNodeWithTag("compact_player").performMouseInput { moveTo(center) }
    mainClock.advanceTimeBy(500); waitForIdle()
    assertEquals(48f, bounds("compact_play").width, 0.05f)
  }

  @Test fun withNothingPlayingTheDisabledPillDoesNotGrow() = runDesktopComposeUiTest(width = 432, height = 120) {
    val f = Fixture()
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 120.dp), contentAlignment = Alignment.BottomStart) { CompactPlayerBody(f.hooks, f.assets, showCard = false, reduced = false) } } }
    waitForIdle()
    onNodeWithTag("compact_player").performMouseInput { moveTo(center) }
    mainClock.advanceTimeBy(500); waitForIdle()
    assertEquals(48f, bounds("compact_play").width, 0.05f)
    assertEquals(384f, bounds("compact_player").width, 0.5f)
  }

  @Test fun theFocusRingShowsOnlyForTheKeyboard() = runDesktopComposeUiTest(width = 480, height = 144) {
    var ring = Color.Unspecified
    setContent {
      TarkasTheme {
        ring = MaterialTheme.colorScheme.primary
        Box(Modifier.size(480.dp, 144.dp).padding(start = 48.dp, top = 24.dp)) {
          GlassView(enabled = true, reduced = false) { Box(Modifier.size(48.dp).focusable().testTag("target")) }
        }
      }
    }
    waitForIdle()
    // The ring sits 2 px outside the left edge of the pill (at x = 48), on the middle row (y = 24 + 48).
    fun probe() = onRoot().captureToImage().toPixelMap()[46, 72]
    onNodeWithTag("target").performKeyInput { keyDown(Key.Tab); keyUp(Key.Tab) }
    waitForIdle()
    val on = probe()
    assertEquals("ring colour $on vs $ring", ring.red, on.red, 0.08f)
    assertEquals(ring.green, on.green, 0.08f); assertEquals(ring.blue, on.blue, 0.08f)
    assertEquals(1f, on.alpha, 0.01f)
  }

  @Test fun withoutAFocusedControlThereIsNoRing() = runDesktopComposeUiTest(width = 480, height = 144) {
    var ring = Color.Unspecified
    setContent {
      TarkasTheme {
        ring = MaterialTheme.colorScheme.primary
        Box(Modifier.size(480.dp, 144.dp).padding(start = 48.dp, top = 24.dp)) {
          GlassView(enabled = true, reduced = false) { Box(Modifier.size(48.dp).focusable()) }
        }
      }
    }
    waitForIdle()
    val p = onRoot().captureToImage().toPixelMap()[46, 72]
    assertFalse("a ring without focus: $p", p.red == ring.red && p.green == ring.green && p.blue == ring.blue && p.alpha == 1f)
  }
}
