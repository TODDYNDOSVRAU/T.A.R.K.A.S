package com.example.windows.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedPlayerTest {
  @Test fun slideStartsLowerAndEndsInPlace() {
    assertEquals(80f, ExpandMath.translateY(0f, 1000f), 0.001f)
    assertEquals(0f, ExpandMath.translateY(1f, 1000f), 0.001f)
    assertEquals(0f, ExpandMath.translateY(1.4f, 1000f), 0.001f)
  }

  @Test fun alphaIsClamped() {
    assertEquals(0f, ExpandMath.alpha(-1f), 0f)
    assertEquals(1f, ExpandMath.alpha(2f), 0f)
  }

  @Test fun durationsStayInTheDecelerationWindow() {
    assertTrue(ExpandMath.OpenMs in 150..300 && ExpandMath.CloseMs in 150..300)
  }

  @Test fun drawnOnlyWhileOpenOrMoving() {
    assertFalse(ExpandMath.visible(0f, false))
    assertTrue(ExpandMath.visible(0.4f, false))
    assertTrue(ExpandMath.visible(0f, true))
  }

  @Test fun closeReportsWhetherItWasOpen() {
    ExpandedPlayer.close()
    assertFalse(ExpandedPlayer.close())
    ExpandedPlayer.show()
    assertTrue(ExpandedPlayer.close())
  }
}

class TrackMotionTest {
  private val art = com.example.data.model.Track(id = "a", title = "t", artist = "x", album = "", durationMs = 0, genre = "", era = "", artworkUrl = "https://x/y.jpg", filePath = "C:/m/a.mp3")

  @Test fun nextIsDownPreviousIsUp() {
    assertEquals(1, TrackMotion.direction(1, 2, 5))
    assertEquals(-1, TrackMotion.direction(2, 1, 5))
    assertEquals(0, TrackMotion.direction(3, 3, 5))
  }

  @Test fun wrapAroundKeepsTheButtonDirection() {
    assertEquals(1, TrackMotion.direction(4, 0, 5))
    assertEquals(-1, TrackMotion.direction(0, 4, 5))
  }

  @Test fun apiCoverWinsOverFileAndFileIsTheFallback() {
    assertEquals(CoverSource.Remote("https://x/y.jpg"), CoverSource.pick(art))
    assertTrue(CoverSource.pick(art.copy(artworkUrl = null)) is CoverSource.Local)
    assertTrue(CoverSource.pick(art.copy(artworkUrl = " ", filePath = null)) === CoverSource.None)
  }

  @Test fun apiTrackWithoutFileStillGetsItsCover() {
    assertEquals(CoverSource.Remote("https://x/y.jpg"), CoverSource.pick(art.copy(filePath = null)))
  }

  @Test fun coverSizeFitsHeightAndWidth() {
    assertEquals(384, ExpandMath.coverDp(900f, 1400f))
    assertEquals(192, ExpandMath.coverDp(640f, 1400f))
    assertEquals(96, ExpandMath.coverDp(500f, 1400f))
    assertEquals(192, ExpandMath.coverDp(900f, 460f))
  }

  @Test fun toggleOpensAndClosesWithoutTouchingPlayback() {
    ExpandedPlayer.close()
    assertTrue(ExpandedPlayer.toggle())
    assertFalse(ExpandedPlayer.toggle())
    assertFalse(ExpandedPlayer.open.value)
  }
}

class MiniPlayerTest {
  @Test fun skipButtonsShowOnHoverOnly() {
    assertTrue(MiniMath.showSkip(true))
    assertFalse(MiniMath.showSkip(false))
  }

  @Test fun pillIsOneGridStepWide() = assertEquals(384, MiniMath.WidthDp)

  @Test fun wheelStepIsFiveSeconds() = assertEquals(5000L, SeekMath.WheelStepMs)
}
