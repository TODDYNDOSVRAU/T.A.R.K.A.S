package com.example.windows.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contract of expo-glass-effect, without a window: styles, the animated object, availability, the mix and the container. */
class GlassModelTest {
  // ---- glassEffectStyle ----

  @Test fun theThreeStylesAndTheDefaultIsRegular() {
    assertEquals(listOf(GlassStyle.Regular, GlassStyle.Clear, GlassStyle.None), GlassStyle.entries)
    assertEquals(GlassStyle.Regular, GlassEffect().style)
    assertEquals(GlassEffect.Regular, GlassEffect())
  }

  @Test fun theObjectFormDefaultsToNoAnimationAndTheTalDuration() {
    val e = GlassEffect(GlassStyle.Clear)
    assertFalse(e.animate)
    assertEquals(300, e.durationMs)
    assertEquals(450, GlassEffect(GlassStyle.Clear, animate = true, animationMs = 450).durationMs)
  }

  @Test fun clearHasLessBlurAndNoScrimRegularHasTheScrim() {
    assertTrue(GlassSpec.Clear.blurDp < GlassSpec.Regular.blurDp)
    assertEquals(0f, GlassSpec.Clear.scrim, 0f)
    assertEquals(0.6f, GlassSpec.Regular.scrim, 0f)
    assertTrue(GlassSpec.Regular.scrim < 1f)
    // Every look fits in the copy the glass draws (48 dp past each edge).
    assertTrue(GlassSpec.MaxReachDp <= GlassSpec.PadDp)
  }

  // ---- availability and the fallback ----

  @Test fun glassIsAvailableOnThisMachine() {
    assertTrue(isGlassAvailable())
  }

  @Test fun anUnavailableGlassOrReducedTransparencyShowsTheNoneStyle() {
    for (s in GlassStyle.entries) {
      assertEquals(s, effectiveStyle(s, available = true, reduceTransparency = false))
      assertEquals(GlassStyle.None, effectiveStyle(s, available = false, reduceTransparency = false))
      assertEquals(GlassStyle.None, effectiveStyle(s, available = true, reduceTransparency = true))
      assertEquals(GlassStyle.None, effectiveStyle(s, available = false, reduceTransparency = true))
    }
  }

  // ---- the mix of styles ----

  @Test fun weightsOfAStyleSumToOne() {
    for (s in GlassStyle.entries) {
      val w = GlassWeights.of(s)
      assertEquals(1f, w.regular + w.clear + w.none, 0f)
    }
  }

  @Test fun theTwoLayersComposeToTheExactMix() {
    // Regular below, clear above: the visible share of each equals its weight, the rest is the surface of `none`.
    for ((r, c) in listOf(1f to 0f, 0f to 1f, 0.5f to 0.5f, 0.25f to 0.25f, 0.6f to 0.1f, 0f to 0f)) {
      val ca = clearAlpha(c); val ra = regularAlpha(r, c)
      val clearShare = ca
      val regularShare = ra * (1f - ca)
      assertEquals("clear $r/$c", c, clearShare, 1e-5f)
      assertEquals("regular $r/$c", r, regularShare, 1e-5f)
      assertTrue(ca in 0f..1f && ra in 0f..1f)
    }
  }

  @Test fun noAlphaOnTheGlassItselfTheStylesWeightsAreWhatFades() {
    // Half way from regular to none: half a regular look and half a plain one, never a regular look at alpha .5.
    val a = GlassWeights.of(GlassStyle.Regular); val b = GlassWeights.of(GlassStyle.None)
    val mid = GlassWeights(mix(a.regular, b.regular, .5f), mix(a.clear, b.clear, .5f), mix(a.none, b.none, .5f))
    assertEquals(GlassWeights(.5f, 0f, .5f), mid)
    // Half a regular scrim (60% of the shell's glass alpha) and half a plain surface.
    assertEquals(0.5f * GlassSpec.Regular.scrim + 0.5f, scrimShare(mid.regular, mid.clear, mid.none), 1e-6f)
  }

  @Test fun theScrimFollowsTheStyle() {
    assertEquals(GlassSpec.Regular.scrim, scrimShare(1f, 0f, 0f), 0f)
    assertEquals(0f, scrimShare(0f, 1f, 0f), 0f)
    assertEquals(1f, scrimShare(0f, 0f, 1f), 0f)
    assertEquals(.5f, scrimShare(0f, .5f, .5f), 1e-6f)
  }

  // ---- the container ----

  private fun r(x: Float, y: Float, w: Float = 100f, h: Float = 50f) = Rect(x, y, x + w, y + h)

  @Test fun theGapIsTheShortestDistanceBetweenRectangles() {
    assertEquals(0f, GlassContainerModel.gap(r(0f, 0f), r(50f, 10f)), 0f)           // overlap
    assertEquals(0f, GlassContainerModel.gap(r(0f, 0f), r(100f, 0f)), 0f)           // touching
    assertEquals(20f, GlassContainerModel.gap(r(0f, 0f), r(120f, 0f)), 1e-4f)       // side by side
    assertEquals(30f, GlassContainerModel.gap(r(0f, 0f), r(0f, 80f)), 1e-4f)        // stacked
    assertEquals(5f, GlassContainerModel.gap(r(0f, 0f), r(103f, 54f)), 1e-4f)       // diagonal: 3-4-5
  }

  @Test fun glassesCloserThanTheSpacingMerge() {
    assertTrue(GlassContainerModel.merges(r(0f, 0f), r(120f, 0f), spacing = 24f))
    assertFalse(GlassContainerModel.merges(r(0f, 0f), r(120f, 0f), spacing = 20f))
    assertFalse(GlassContainerModel.merges(r(0f, 0f), r(120f, 0f), spacing = 12f))
  }

  @Test fun mergingIsTransitiveThroughANeighbour() {
    val glasses = listOf(r(0f, 0f), r(110f, 0f), r(220f, 0f), r(600f, 0f))
    // 0-1 and 1-2 are 10 apart: one group; the fourth is far.
    assertEquals(listOf(listOf(0, 1, 2), listOf(3)), GlassContainerModel.groups(glasses, 24f).map { it.sorted() }.sortedBy { it.first() })
    // Without spacing for it, everyone stays alone.
    assertEquals(4, GlassContainerModel.groups(glasses, 5f).size)
    assertEquals(0, GlassContainerModel.groups(emptyList(), 24f).size)
  }
}
