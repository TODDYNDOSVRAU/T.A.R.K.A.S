package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoverCarouselTest {
  private val eps = 1e-5f

  @Test fun centreIsTheCoverAsItIs() {
    val s = CoverCarousel.layoutAt(0f)
    assertEquals(1f, s.scale, eps)
    assertEquals(0f, s.shift, eps)
    assertEquals(0f, s.rotationY, eps)
    assertEquals(1f, s.alpha, eps)
    assertEquals(0f, s.scrim, eps)
  }

  @Test fun sideSlotsAreSmallerDarkerAndTurnedAway() {
    val r = CoverCarousel.layoutAt(1f)
    assertEquals(CoverCarousel.SideScale, r.scale, eps)
    assertEquals(CoverCarousel.SideShift, r.shift, eps)
    assertEquals(CoverCarousel.SideAngle, r.rotationY, eps)
    assertEquals(CoverCarousel.SideAlpha, r.alpha, eps)
    assertEquals(CoverCarousel.SideScrim, r.scrim, eps)
    assertTrue(r.scale in 0.7f..0.78f)
    assertTrue(r.alpha in 0.6f..0.8f)
    assertTrue(r.scrim in 0.25f..0.35f)
    assertTrue(r.rotationY in 8f..16f)
  }

  @Test fun theSideCoverStandsWellApartFromTheCentre() {
    val r = CoverCarousel.layoutAt(1f)
    // 0.75 to 0.85 of the centre's width from centre to centre.
    assertTrue(r.shift in 0.75f..0.85f)
    // Not behind the centre cover at all (its inner edge meets the centre's edge).
    val behind = (0.5f - (r.shift - r.scale / 2f)) / r.scale
    assertTrue("behind $behind", behind in -0.01f..0.01f)
  }

  @Test fun theSecondSlotIsFurtherSmallerDarkerAndTurnedMore() {
    val one = CoverCarousel.layoutAt(1f)
    val two = CoverCarousel.layoutAt(2f)
    assertEquals(CoverCarousel.FarScale, two.scale, eps)
    assertEquals(CoverCarousel.FarShift, two.shift, eps)
    assertTrue(two.shift > one.shift)
    assertTrue(two.scale < one.scale)
    assertTrue(two.alpha < one.alpha)
    assertTrue(two.scrim > one.scrim)
    assertTrue(two.rotationY > one.rotationY)
    // It stays partly behind the first one.
    assertTrue(two.shift - two.scale / 2f < one.shift + one.scale / 2f)
  }

  @Test fun onAPhoneTheCentreKeepsTheSingleCoversSizeAndTheSidesShowInTheMargins() {
    // S23: 1080 px wide, 63 px margins (24 dp at 2.625), tall enough.
    val w = 1080f
    val margin = 63f
    val side = CoverCarousel.centreSide(w, 5000f, margin)
    assertEquals(954f, side, eps)
    val r = CoverCarousel.layoutAt(1f)
    // The side covers are at least 20% larger than the 459 px they were at 620 px.
    assertTrue(r.scale * side >= 459f * 1.2f)
    // Playing, each first side cover shows in the margin; paused (86%) the strip grows.
    val shownPlaying = w - (w / 2f + side / 2f)
    val shownPaused = w - (w / 2f + PlayerTravel.PausedScale * side / 2f)
    assertEquals(margin, shownPlaying, 0.5f)
    assertTrue(shownPaused > 120f)
    // A short screen: the height decides.
    assertEquals(300f, CoverCarousel.centreSide(w, 300f, margin), eps)
  }

  @Test fun symmetric() {
    for (i in -300..300) {
      val o = i / 100f
      for (k in listOf(0f, 0.4f, 1f)) {
        val l = CoverCarousel.layoutAt(-o, spread = k)
        val r = CoverCarousel.layoutAt(o, spread = k)
        assertEquals(l.scale, r.scale, eps)
        assertEquals(-l.shift, r.shift, eps)
        assertEquals(-l.rotationY, r.rotationY, eps)
        assertEquals(l.alpha, r.alpha, eps)
        assertEquals(l.scrim, r.scrim, eps)
      }
    }
  }

  @Test fun continuousWithNoJumps() {
    var last = CoverCarousel.layoutAt(0f)
    for (i in 1..3000) {
      val s = CoverCarousel.layoutAt(i / 1000f)
      assertTrue(abs(s.scale - last.scale) < 0.002f)
      assertTrue(abs(s.shift - last.shift) < 0.002f)
      assertTrue(abs(s.rotationY - last.rotationY) < 0.05f)
      assertTrue(abs(s.alpha - last.alpha) < 0.003f)
      assertTrue(abs(s.scrim - last.scrim) < 0.002f)
      last = s
    }
  }

  @Test fun monotonicAwayFromTheCentre() {
    var last = CoverCarousel.layoutAt(0f)
    for (i in 1..3000) {
      val s = CoverCarousel.layoutAt(i / 1000f)
      assertTrue(s.scale <= last.scale)
      assertTrue(s.alpha <= last.alpha)
      assertTrue(s.shift >= last.shift)
      assertTrue(s.scrim >= last.scrim)
      assertTrue(abs(s.rotationY) >= abs(last.rotationY))
      last = s
    }
  }

  @Test fun pastTheSecondSlotsItFadesOutAndThenStays() {
    assertTrue(CoverCarousel.layoutAt(2f).alpha > 0.3f)
    assertEquals(0f, CoverCarousel.layoutAt(2.6f).alpha, eps)
    assertEquals(CoverCarousel.layoutAt(3f), CoverCarousel.layoutAt(5f))
    assertEquals(CoverCarousel.layoutAt(-3f), CoverCarousel.layoutAt(-50f))
  }

  @Test fun reducedMotionDoesNotTurn() {
    for (i in -30..30) assertEquals(0f, CoverCarousel.layoutAt(i / 10f, reduced = true).rotationY, eps)
    assertEquals(CoverCarousel.layoutAt(1f).scale, CoverCarousel.layoutAt(1f, reduced = true).scale, eps)
  }

  @Test fun theCentreCoverFollowsTheFingerOneToOne() {
    val cover = 620f
    val step = CoverCarousel.stepPx(cover)
    for (d in listOf(1f, 5f, 10f, 20f, 100f)) {
      val shiftPx = CoverCarousel.layoutAt(d / step).shift * cover
      assertEquals(d, shiftPx, d * 0.01f)
    }
    assertEquals(CoverCarousel.SideShift * cover, CoverCarousel.layoutAt(1f).shift * cover, 0.01f)
  }

  @Test fun atHalfwayTheTwoCoversBarelyOverlap() {
    // Where they change which one is on top, the outgoing cover (-0.5) and the incoming one (+0.5).
    val s = CoverCarousel.layoutAt(0.5f)
    val overlap = s.scale - 2f * s.shift
    assertTrue("overlap $overlap", overlap < 0.12f)
  }

  // ---- spread: the side covers come out from behind the middle one ----

  @Test fun spreadZeroPutsEveryCardExactlyBehindTheCentre() {
    val c = CoverCarousel.layoutAt(0f)
    for (i in -30..30) {
      val s = CoverCarousel.layoutAt(i / 10f, spread = 0f)
      assertEquals(c.scale, s.scale, eps)
      assertEquals(c.shift, s.shift, eps)
      assertEquals(c.rotationY, s.rotationY, eps)
      assertEquals(0f, s.scrim, eps)
      if (abs(i) >= 10) assertEquals(0f, s.alpha, eps)
    }
    // The centre itself is untouched.
    assertEquals(c, CoverCarousel.layoutAt(0f, spread = 0f))
  }

  @Test fun spreadOneIsTheRestingLayout() {
    for (i in -30..30) {
      val o = i / 10f
      assertEquals(CoverCarousel.layoutAt(o), CoverCarousel.layoutAt(o, spread = 1f))
      assertEquals(CoverCarousel.layoutAt(o, reduced = true), CoverCarousel.layoutAt(o, reduced = true, spread = 1f))
    }
  }

  @Test fun spreadMovesTheSidesOutSteadily() {
    var last = CoverCarousel.layoutAt(1f, spread = 0f)
    for (i in 1..100) {
      val s = CoverCarousel.layoutAt(1f, spread = i / 100f)
      assertTrue(s.shift >= last.shift)
      assertTrue(s.alpha >= last.alpha)
      assertTrue(s.scale <= last.scale)
      last = s
    }
  }

  @Test fun spreadPastOneSwingsBeyondTheSlotsAndKeepsTheLook() {
    val rest = CoverCarousel.layoutAt(1f)
    val over = CoverCarousel.layoutAt(1f, spread = 1.13f)
    assertTrue(over.shift > rest.shift)
    assertTrue(over.scale < rest.scale)
    assertTrue(over.rotationY > rest.rotationY)
    assertEquals(rest.alpha, over.alpha, eps)
    assertEquals(rest.scrim, over.scrim, eps)
    // The left side mirrors the right, and the middle card is never moved.
    val left = CoverCarousel.layoutAt(-1f, spread = 1.13f)
    assertEquals(-over.shift, left.shift, eps)
    assertEquals(CoverCarousel.layoutAt(0f), CoverCarousel.layoutAt(0f, spread = 1.13f))
    // Capped, so a far cover stays visible-sized however far the spring goes.
    val capped = CoverCarousel.layoutAt(2f, spread = 5f)
    assertEquals(CoverCarousel.layoutAt(2f, spread = CoverCarousel.MaxSpread), capped)
    assertTrue(capped.scale > 0f)
  }

  @Test fun reducedMotionNeverOvershoots() {
    for (i in -30..30) {
      val o = i / 10f
      assertEquals(CoverCarousel.layoutAt(o, reduced = true), CoverCarousel.layoutAt(o, reduced = true, spread = 1.2f))
    }
  }

  @Test fun reducedMotionSpreadOnlyFades() {
    val rest = CoverCarousel.layoutAt(1f, reduced = true)
    val half = CoverCarousel.layoutAt(1f, reduced = true, spread = 0.5f)
    assertEquals(rest.shift, half.shift, eps)
    assertEquals(rest.scale, half.scale, eps)
    assertEquals(0f, half.rotationY, eps)
    assertEquals(rest.alpha * 0.5f, half.alpha, eps)
    assertEquals(0f, CoverCarousel.layoutAt(-1f, reduced = true, spread = 0f).alpha, eps)
  }

  @Test fun projectionUsesTheDecelerationRate() {
    assertEquals(0.3f, CoverCarousel.project(0.3f, 0f), eps)
    // v/1000 · d/(1-d) with d = 0.998: 1 unit/s carries 0.499 units further.
    assertEquals(0.499f, CoverCarousel.project(0f, 1f), 1e-3f)
    assertEquals(-0.499f, CoverCarousel.project(0f, -1f), 1e-3f)
  }

  @Test fun slowReleaseNearTheCentreStays() {
    assertEquals(0, CoverCarousel.targetStep(0.3f, 0f, canPrevious = true, canNext = true))
    assertEquals(0, CoverCarousel.targetStep(-0.3f, 0f, canPrevious = true, canNext = true))
  }

  @Test fun pastHalfwayItGoes() {
    assertEquals(1, CoverCarousel.targetStep(0.6f, 0f, true, true))
    assertEquals(-1, CoverCarousel.targetStep(-0.6f, 0f, true, true))
  }

  @Test fun aFlickCarriesAShortDragOver() {
    assertEquals(1, CoverCarousel.targetStep(0.15f, 2f, true, true))
    assertEquals(-1, CoverCarousel.targetStep(-0.15f, -2f, true, true))
    // A drag the other way that is thrown back returns.
    assertEquals(0, CoverCarousel.targetStep(0.4f, -1.5f, true, true))
  }

  @Test fun neverMoreThanOneTrack() {
    assertEquals(1, CoverCarousel.targetStep(0.9f, 30f, true, true))
    assertEquals(-1, CoverCarousel.targetStep(-0.9f, -30f, true, true))
  }

  @Test fun neverTowardsAnEmptySide() {
    assertEquals(0, CoverCarousel.targetStep(0.9f, 5f, canPrevious = true, canNext = false))
    assertEquals(0, CoverCarousel.targetStep(-0.9f, -5f, canPrevious = false, canNext = true))
  }

  @Test fun rubberBandIsOneToOneInsideAndResistsOutside() {
    assertEquals(0.5f, CoverCarousel.band(0.5f, -1f, 1f), eps)
    assertEquals(0f, CoverCarousel.band(0f, 0f, 0f), eps)
    // Past an edge it moves, but less and less, and never past the limit.
    var last = 0f
    for (i in 1..400) {
      val v = CoverCarousel.band(i / 100f, 0f, 0f)
      assertTrue(v > last)
      assertTrue(v < CoverCarousel.RubberLimit)
      last = v
    }
    // Slope 0.55 at the edge: no jump and no dead stop.
    assertEquals(0.55f * 0.01f, CoverCarousel.band(0.01f, 0f, 0f), 2e-4f)
    assertEquals(-CoverCarousel.band(0.7f, 0f, 0f), CoverCarousel.band(-0.7f, 0f, 0f), eps)
    assertEquals(1f + CoverCarousel.rubber(0.2f), CoverCarousel.band(1.2f, -1f, 1f), eps)
  }

  @Test fun rebaseKeepsTheCardThatWasSlidingIn() {
    // a | b | c at -1, 0, 1; the swipe settled on 1 and the track is now c.
    val cards = mapOf(-1 to "a", 0 to "b", 1 to "c")
    assertEquals(1, CoverCarousel.rebaseCenter(cards, oldCenter = 0, pending = 1, newId = "c", camera = 1f))
    // The Next button: no swipe, the card holding the new track is found.
    assertEquals(1, CoverCarousel.rebaseCenter(cards, 0, null, "c", 0f))
    assertEquals(-1, CoverCarousel.rebaseCenter(cards, 0, null, "a", 0f))
  }

  @Test fun rebaseWithTwoOnEachSidePicksTheNearestCopy() {
    // A short list shows the same tracks on both sides: c | b | a | b | c.
    val cards = mapOf(-2 to "b", -1 to "c", 0 to "a", 1 to "b", 2 to "c")
    assertEquals(1, CoverCarousel.rebaseCenter(cards, 0, null, "b", 0f))
    assertEquals(-1, CoverCarousel.rebaseCenter(cards, 0, null, "c", 0f))
  }

  @Test fun rebaseWithTheSameSongTwice() {
    // b is playing and queued again: the swipe to the right one keeps the right card.
    val cards = mapOf(-1 to "a", 0 to "b", 1 to "b")
    assertEquals(1, CoverCarousel.rebaseCenter(cards, 0, pending = 1, newId = "b", camera = 1f))
    // Without a swipe, the same id stays in the middle.
    assertEquals(0, CoverCarousel.rebaseCenter(cards, 0, null, "b", 0f))
  }

  @Test fun rebaseToATrackNotOnScreenTakesTheMiddle() {
    val cards = mapOf(-1 to "a", 0 to "b", 1 to "c")
    assertEquals(0, CoverCarousel.rebaseCenter(cards, 0, null, "z", 0.2f))
    assertEquals(1, CoverCarousel.rebaseCenter(cards, 0, pending = 1, newId = "z", camera = 1f))
  }
}
