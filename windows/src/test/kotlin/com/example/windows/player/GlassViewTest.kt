package com.example.windows.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.ui.theme.TarkasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The behaviour of `GlassView` as a component: the style it shows, the animated change of style, the fallback and reduced
 * transparency, the tint, the forced scheme and the light that follows the pointer. The scene has a 48 dp margin on the
 * left and 24 dp on top, so the pill (384 x 96) sits at x 48..432, y 24..120 of a 480 x 144 window.
 */
@OptIn(ExperimentalTestApi::class)
class GlassViewTest {
  private val ox = 48; private val oy = 24

  private fun androidx.compose.ui.test.ComposeUiTest.probe(tag: String = "glass") =
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[GlassProbeKey]()

  // ---- The style it shows ----

  @Test fun theDefaultIsRegularAndAvailable() = runDesktopComposeUiTest(width = 480, height = 144) {
    setContent { TarkasTheme { Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(modifier = Modifier.testTag("glass")) {} } } }
    waitForIdle()
    val p = probe()
    assertEquals(GlassStyle.Regular, p.style)
    assertEquals(GlassWeights.of(GlassStyle.Regular), p.weights)
    assertTrue(p.available); assertTrue("the refraction shader is built here", p.refracts)
  }

  @Test fun eachRequestedStyleIsWhatItShows() = runDesktopComposeUiTest(width = 480, height = 144) {
    var effect by mutableStateOf(GlassEffect.Clear)
    setContent { TarkasTheme { Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), effect = effect) {} } } }
    waitForIdle()
    for (s in GlassStyle.entries) {
      effect = GlassEffect(s); waitForIdle()
      assertEquals(s, probe().style); assertEquals(GlassWeights.of(s), probe().weights)
    }
  }

  // ---- The fallback ----

  @Test fun withoutGlassTheViewIsThePlainSurfaceWithoutAnError() = runDesktopComposeUiTest(width = 480, height = 144) {
    setContent {
      TarkasTheme {
        CompositionLocalProvider(LocalGlassAvailable provides false) {
          Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), effect = GlassEffect.Clear) {} }
        }
      }
    }
    waitForIdle()
    val p = probe()
    assertEquals(GlassStyle.None, p.style); assertEquals(GlassWeights.of(GlassStyle.None), p.weights)
    assertEquals(false, p.available); assertEquals(false, p.refracts)
  }

  @Test fun reducedTransparencyShowsThePlainSurfaceEvenWhenGlassIsAvailable() = runDesktopComposeUiTest(width = 480, height = 144) {
    setContent {
      TarkasTheme {
        CompositionLocalProvider(LocalReduceTransparency provides true) {
          Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass")) {} }
        }
      }
    }
    waitForIdle()
    assertEquals(GlassStyle.None, probe().style)
    assertTrue(probe().available)
  }

  @Test fun turningReducedTransparencyOnAtRunTimeSnapsEvenForAnAnimatedEffect() = runDesktopComposeUiTest(width = 480, height = 144) {
    var reduce by mutableStateOf(false)
    setContent {
      TarkasTheme {
        CompositionLocalProvider(LocalReduceTransparency provides reduce) {
          Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), effect = GlassEffect(GlassStyle.Regular, animate = true)) {} }
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    reduce = true
    mainClock.advanceTimeByFrame(); mainClock.advanceTimeByFrame()
    assertEquals(GlassWeights.of(GlassStyle.None), probe().weights)
  }

  // ---- The animated change of style ----

  @Test fun anAnimatedChangeOfStyleInterpolatesAndEndsExactly() = runDesktopComposeUiTest(width = 480, height = 144) {
    var effect by mutableStateOf(GlassEffect(GlassStyle.Regular, animate = true))
    setContent { TarkasTheme { Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), effect = effect) {} } } }
    waitForIdle()
    mainClock.autoAdvance = false
    effect = GlassEffect(GlassStyle.None, animate = true)
    mainClock.advanceTimeByFrame(); mainClock.advanceTimeByFrame()
    mainClock.advanceTimeBy(130)
    val mid = probe().weights
    assertTrue("half way is a mix of regular and none: $mid", mid.regular > 0.05f && mid.none > 0.05f)
    assertEquals(1f, mid.regular + mid.clear + mid.none, 1e-4f)
    assertEquals(GlassStyle.None, probe().style) // the target; what is drawn is the mix
    mainClock.advanceTimeBy(400)
    assertEquals(GlassWeights.of(GlassStyle.None), probe().weights)
  }

  @Test fun theChangeFollowsTheDecelerationCurveAndTheGivenDuration() = runDesktopComposeUiTest(width = 480, height = 144) {
    var effect by mutableStateOf(GlassEffect(GlassStyle.None, animate = true, animationMs = 1000))
    setContent { TarkasTheme { Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), effect = effect) {} } } }
    waitForIdle()
    mainClock.autoAdvance = false
    effect = GlassEffect(GlassStyle.Clear, animate = true, animationMs = 1000)
    mainClock.advanceTimeByFrame(); mainClock.advanceTimeByFrame()
    mainClock.advanceTimeBy(250)
    // cubic-bezier(.4, 0, .2, 1): a gentle start, then it decelerates into the end (three quarters in, well past 3/4).
    val quarter = probe().weights.clear
    assertTrue("it has started and is not past the middle: $quarter", quarter > 0.05f && quarter < 0.5f)
    mainClock.advanceTimeBy(500)
    val late = probe().weights.clear
    assertTrue("three quarters in, more than 85% and not finished: $late", late > 0.85f && late < 1f)
    mainClock.advanceTimeBy(400)
    assertEquals(GlassWeights.of(GlassStyle.Clear), probe().weights)
  }

  @Test fun withoutAnimateTheStyleChangesAtOnce() = runDesktopComposeUiTest(width = 480, height = 144) {
    var effect by mutableStateOf(GlassEffect.Regular)
    setContent { TarkasTheme { Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), effect = effect) {} } } }
    waitForIdle()
    mainClock.autoAdvance = false
    effect = GlassEffect.None
    mainClock.advanceTimeByFrame(); mainClock.advanceTimeByFrame()
    assertEquals(GlassWeights.of(GlassStyle.None), probe().weights)
  }

  @Test fun reducedMotionAlsoChangesAtOnce() = runDesktopComposeUiTest(width = 480, height = 144) {
    var effect by mutableStateOf(GlassEffect(GlassStyle.Regular, animate = true))
    setContent { TarkasTheme { Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), effect = effect, reduced = true) {} } } }
    waitForIdle()
    mainClock.autoAdvance = false
    effect = GlassEffect(GlassStyle.None, animate = true)
    mainClock.advanceTimeByFrame(); mainClock.advanceTimeByFrame()
    assertEquals(GlassWeights.of(GlassStyle.None), probe().weights)
  }

  @Test fun aChangeInTheMiddleOfAnotherStartsFromWhatWasShowingNotFromAJump() = runDesktopComposeUiTest(width = 480, height = 144) {
    var effect by mutableStateOf(GlassEffect(GlassStyle.Regular, animate = true))
    setContent { TarkasTheme { Box(Modifier.padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), effect = effect) {} } } }
    waitForIdle()
    mainClock.autoAdvance = false
    effect = GlassEffect(GlassStyle.Clear, animate = true)
    mainClock.advanceTimeByFrame(); mainClock.advanceTimeByFrame(); mainClock.advanceTimeBy(100)
    val before = probe().weights
    assertTrue("in the middle of regular to clear: $before", before.clear > 0.05f && before.regular > 0.05f)
    effect = GlassEffect(GlassStyle.None, animate = true)
    mainClock.advanceTimeByFrame(); mainClock.advanceTimeByFrame()
    val after = probe().weights
    // Two frames later the mix has moved a little, not jumped: the clear share is near what it was.
    assertEquals("clear share continuous", before.clear, after.clear, 0.12f)
    assertEquals(1f, after.regular + after.clear + after.none, 1e-4f)
    mainClock.advanceTimeBy(500)
    assertEquals(GlassWeights.of(GlassStyle.None), probe().weights)
  }

  // ---- The tint and the scheme (pixels, no backdrop: the surface scrim, the tint and the light are all there is) ----

  private fun px(img: androidx.compose.ui.graphics.ImageBitmap, x: Int, y: Int): Color = img.toPixelMap()[x, y]

  @Test fun aTintColoursTheGlassAndNoTintLeavesItNeutral() = runDesktopComposeUiTest(width = 480, height = 144) {
    var tint by mutableStateOf<Color?>(null)
    setContent {
      TarkasTheme(themeMode = "dark") {
        Box(Modifier.fillMaxSize().background(Color.Black).padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), tint = tint) {} }
      }
    }
    waitForIdle()
    val plain = px(onRoot().captureToImage(), ox + 300, oy + 20)
    tint = Color.Red; waitForIdle()
    val tinted = px(onRoot().captureToImage(), ox + 300, oy + 20)
    assertTrue("red $plain -> $tinted", tinted.red > plain.red + 0.05f && tinted.green <= plain.green + 0.01f)
    assertEquals("a neutral glass over black is neutral", plain.red, plain.blue, 0.03f)
  }

  @Test fun aForcedSchemeOverridesTheAppThemeForTheGlassAndItsContent() = runDesktopComposeUiTest(width = 480, height = 144) {
    var seen = Color.Unspecified
    var scheme by mutableStateOf(GlassScheme.Auto)
    setContent {
      TarkasTheme(themeMode = "dark") {
        Box(Modifier.fillMaxSize().background(Color.Black).padding(start = 48.dp, top = 24.dp)) {
          GlassView(Modifier.testTag("glass"), scheme = scheme) { seen = MaterialTheme.colorScheme.background; Box(Modifier.size(1.dp)) }
        }
      }
    }
    waitForIdle()
    val darkLook = px(onRoot().captureToImage(), ox + 300, oy + 20).luminance(); val darkBg = seen
    scheme = GlassScheme.Light; waitForIdle()
    val lightLook = px(onRoot().captureToImage(), ox + 300, oy + 20).luminance()
    assertTrue("the content now reads the light scheme: $darkBg -> $seen", seen.luminance() > darkBg.luminance() + 0.5f)
    assertTrue("the glass is lighter: $darkLook -> $lightLook", lightLook > darkLook + 0.05f)
    scheme = GlassScheme.Dark; waitForIdle()
    assertEquals(darkBg.luminance(), seen.luminance(), 0.02f)
  }

  // ---- Interactive: the light that follows the pointer ----

  @Test fun anInteractiveGlassLightsUpWhereThePointerIsAndTheLightFollowsIt() = runDesktopComposeUiTest(width = 480, height = 144) {
    setContent {
      TarkasTheme(themeMode = "dark") {
        Box(Modifier.fillMaxSize().background(Color.Black).padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass"), interactive = true) {} }
      }
    }
    waitForIdle()
    val a = Offset(90f, 48f); val b = Offset(300f, 48f)
    fun lum(x: Float) = px(onRoot().captureToImage(), ox + x.toInt(), oy + 48).luminance()
    val rest = lum(a.x)
    onNodeWithTag("glass").performMouseInput { moveTo(a) }
    mainClock.advanceTimeBy(500); waitForIdle()
    val lit = lum(a.x); val farFromIt = lum(b.x)
    assertTrue("lit at the pointer: $rest -> $lit", lit > rest + 0.004f)
    assertTrue("and less far from it: $lit vs $farFromIt", lit > farFromIt + 0.003f)
    onNodeWithTag("glass").performMouseInput { moveTo(b) }
    mainClock.advanceTimeBy(100); waitForIdle()
    assertTrue("the light moved to the new spot: ${lum(b.x)} vs ${lum(a.x)}", lum(b.x) > lum(a.x) + 0.003f)
    onNodeWithTag("glass").performMouseInput { moveTo(Offset(-60f, -60f)) }
    mainClock.advanceTimeBy(500); waitForIdle()
    assertEquals("the light is gone with the pointer", rest, lum(a.x), 0.003f)
  }

  @Test fun aGlassThatIsNotInteractiveHasNoHighlight() = runDesktopComposeUiTest(width = 480, height = 144) {
    setContent {
      TarkasTheme(themeMode = "dark") {
        Box(Modifier.fillMaxSize().background(Color.Black).padding(start = 48.dp, top = 24.dp)) { GlassView(Modifier.testTag("glass")) {} }
      }
    }
    waitForIdle()
    fun lum() = px(onRoot().captureToImage(), ox + 90, oy + 48).luminance()
    val rest = lum()
    onNodeWithTag("glass").performMouseInput { moveTo(Offset(90f, 48f)) }
    mainClock.advanceTimeBy(500); waitForIdle()
    assertEquals(rest, lum(), 0.0005f)
  }
}
