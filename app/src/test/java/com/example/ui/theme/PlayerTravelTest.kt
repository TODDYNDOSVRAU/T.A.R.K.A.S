package com.example.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTravelTest {
  private val mini = Rect(Offset(16f, 1800f), Size(100f, 100f))
  private val rest = Rect(Offset(40f, 300f), Size(1000f, 1000f))

  @Test fun revealIsZeroUntilAboutAFifthOfTheWayAndOneAtTheEnd() {
    assertEquals(0f, PlayerTravel.reveal(0f), 0f)
    assertEquals(0f, PlayerTravel.reveal(0.13f), 0f)
    assertTrue(PlayerTravel.reveal(0.2f) > 0f)
    assertTrue(PlayerTravel.reveal(0.5f) in 0.6f..0.75f)
    assertEquals(1f, PlayerTravel.reveal(1f), 0f)
    assertEquals(1f, PlayerTravel.reveal(1.03f), 0f)
  }

  @Test fun revealNeverGoesBackwardsWhileOpening() {
    var last = 0f
    for (i in 0..100) {
      val r = PlayerTravel.reveal(i / 100f)
      assertTrue(r >= last)
      last = r
    }
  }

  @Test fun originIsGoneAlmostAtOnce() {
    assertEquals(1f, PlayerTravel.originFade(0f), 0f)
    assertTrue(PlayerTravel.originFade(0.01f) < 0.06f)
    assertTrue(PlayerTravel.originFade(0.1f) < 0.0001f)
  }

  @Test fun tabBarIsMostlyOutWithAFewPercentOfTheOpening() {
    assertEquals(0f, PlayerTravel.tabBarOut(0f), 0f)
    assertTrue(PlayerTravel.tabBarOut(0.15f) > 0.94f)
    assertTrue(PlayerTravel.tabBarOut(1f) > 0.999f)
  }

  @Test fun coverRectIsTheOriginAtZeroAndTheRestingPlaceAtOne() {
    assertEquals(mini, PlayerTravel.rectAt(mini, rest, 0f))
    assertEquals(rest, PlayerTravel.rectAt(mini, rest, 1f))
  }

  @Test fun coverRectIsLinearInTheMiddle() {
    val mid = PlayerTravel.rectAt(mini, rest, 0.5f)
    assertEquals(28f, mid.left, 0.001f)
    assertEquals(1050f, mid.top, 0.001f)
    assertEquals(550f, mid.width, 0.001f)
    assertEquals(550f, mid.height, 0.001f)
  }

  @Test fun progressOutsideZeroToOneIsClamped() {
    assertEquals(rest, PlayerTravel.rectAt(mini, rest, 1.02f))
    assertEquals(mini, PlayerTravel.rectAt(mini, rest, -0.1f))
  }

  @Test fun scaleGoesFromTheOriginSizeToOne() {
    assertEquals(0.1f, PlayerTravel.scaleAt(mini, rest, 0f), 0.0001f)
    assertEquals(1f, PlayerTravel.scaleAt(mini, rest, 1f), 0.0001f)
  }

  @Test fun cornerLooksLikeTheStartRadiusAtTheStartDespiteTheScale() {
    // 6 px shown on a cover scaled to 10%: the cover itself needs 60 px.
    assertEquals(60f, PlayerTravel.cornerAt(6f, 14f, 0f, 0.1f), 0.001f)
    assertEquals(14f, PlayerTravel.cornerAt(6f, 14f, 1f, 1f), 0.001f)
  }

  @Test fun miniPlayerIsHiddenWhileTheShrinkIsFarAndCompleteExactlyWhenItEnds() {
    assertEquals(0f, PlayerTravel.miniIn(1f), 0f)
    assertEquals(0f, PlayerTravel.miniIn(PlayerTravel.MiniArrivalStart), 0f)
    assertEquals(1f, PlayerTravel.miniIn(0f), 0f)
    assertEquals(1f, PlayerTravel.miniIn(-0.002f), 0f)
    // Never complete before the shrink is done: still short of 1 at 2% progress.
    assertTrue(PlayerTravel.miniIn(0.02f) < 1f)
  }

  @Test fun miniPlayerOnlyRisesAsTheShrinkAdvances() {
    var last = -1f
    for (i in 100 downTo 0) {
      val m = PlayerTravel.miniIn(i / 100f)
      assertTrue(m >= last)
      last = m
    }
  }

  @Test fun coverScaleByState() {
    assertEquals(1f, PlayerTravel.coverScale(isPlaying = true, hasTrack = true, scrubbing = false), 0f)
    assertEquals(0.86f, PlayerTravel.coverScale(isPlaying = false, hasTrack = true, scrubbing = false), 0f)
    assertEquals(0.95f, PlayerTravel.coverScale(isPlaying = true, hasTrack = true, scrubbing = true), 0f)
    assertEquals(0.95f, PlayerTravel.coverScale(isPlaying = false, hasTrack = true, scrubbing = true), 0f)
    assertEquals(1f, PlayerTravel.coverScale(isPlaying = false, hasTrack = false, scrubbing = true), 0f)
  }

  @Test fun aRowsCoverIsASquareAsTallAsTheRowAtItsLeadingEdge() {
    val row = Rect(Offset(0f, 500f), Size(1000f, 120f))
    assertEquals(Rect(Offset(0f, 500f), Size(120f, 120f)), PlayerTravel.coverIn(row))
  }

  @Test fun aCardsCoverIsASquareAsWideAsTheCard() {
    val card = Rect(Offset(20f, 400f), Size(300f, 380f))
    assertEquals(Rect(Offset(20f, 400f), Size(300f, 300f)), PlayerTravel.coverIn(card))
  }
}
