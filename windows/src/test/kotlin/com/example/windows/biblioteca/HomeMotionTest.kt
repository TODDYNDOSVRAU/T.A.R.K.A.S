package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.ui.theme.TarkasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules behind the Home retouch: which carousels show, when the placeholder shows, the spring speed cap, the card scale. */
class HomeMotionTest {
  private val now = "now" to listOf("1", "2", "3")
  private fun rails(vararg r: Pair<String, List<String>>) = HomeData.railsToShow(r.toList())

  @Test fun railsSkipEmptyOnes() {
    assertEquals(listOf("now", "lib"), rails(now, "mix" to emptyList(), "lib" to listOf("9")))
  }

  @Test fun railsHideListenAgainWhenItRepeatsContinueListening() {
    assertEquals(listOf("now"), rails(now, "again" to listOf("2", "1")))
    assertEquals(listOf("now", "again"), rails(now, "again" to listOf("2", "7")))
  }

  @Test fun railsKeepPageOrderAndStopAtFour() {
    val r = rails("a" to listOf("1"), "b" to listOf("2"), "c" to listOf("3"), "d" to listOf("4"), "e" to listOf("5"))
    assertEquals(listOf("a", "b", "c", "d"), r)
    assertEquals(HomeData.MAX_RAILS, r.size)
  }

  @Test fun skeletonWaitsThreeHundredMilliseconds() {
    assertFalse(HomeData.skeletonShows(true, 0))
    assertFalse(HomeData.skeletonShows(true, HomeData.SKELETON_DELAY_MS - 1))
    assertTrue(HomeData.skeletonShows(true, HomeData.SKELETON_DELAY_MS))
    assertFalse(HomeData.skeletonShows(false, 10_000))
  }

  @Test fun springSpeedTowardTheTargetIsCappedBelowOvershoot() {
    val d = 100f
    val cap = 0.9f * HomeData.SETTLE_OMEGA * d
    assertEquals(cap, HomeData.springVelocity(1_000_000f, d), 0.01f)
    assertEquals(-cap, HomeData.springVelocity(-1_000_000f, -d), 0.01f)
    assertEquals(50f, HomeData.springVelocity(50f, d), 0.001f)
  }

  @Test fun springSpeedAwayFromTheTargetIsKeptAndZeroDistanceHasNone() {
    assertEquals(-300f, HomeData.springVelocity(-300f, 100f), 0.001f)
    assertEquals(300f, HomeData.springVelocity(300f, -100f), 0.001f)
    assertEquals(0f, HomeData.springVelocity(500f, 0f), 0f)
  }

  @Test fun cardScaleHoverGrowsPressShrinksPressWins() {
    assertEquals(1f, HomeData.cardScale(false, false), 0f)
    assertEquals(1.04f, HomeData.cardScale(true, false), 0f)
    assertEquals(0.96f, HomeData.cardScale(true, true), 0f)
    assertEquals(0.96f, HomeData.cardScale(false, true), 0f)
  }

  @Test fun settleSpringIsCriticallyDampedAboutThreeHundredMilliseconds() {
    // Critically damped, unit mass: omega = sqrt(stiffness); the error falls to 2 % at about 5.8 / omega seconds.
    val seconds = 5.83f / HomeData.SETTLE_OMEGA
    assertTrue("settles in $seconds s", seconds in 0.25f..0.35f)
  }

  /** The placeholder takes exactly the room of a card, so nothing moves when the cards arrive. */
  @OptIn(ExperimentalTestApi::class)
  @Test fun skeletonCardIsAsBigAsACard() = runDesktopComposeUiTest(800, 600) {
    setContent {
      TarkasTheme(themeMode = "dark") {
        Row {
          Box { Card(null, "Title", "Subtitle", emptyList()) {} }
          Box { SkeletonCard(mutableStateOf(1f), mutableStateOf(1f)) }
        }
      }
    }
    waitForIdle()
    val card = onNodeWithTag("home-card").getBoundsInRoot()
    val skel = onNodeWithTag("home-skeleton").getBoundsInRoot()
    assertEquals("width", card.width.value, skel.width.value, 0.6f)
    assertEquals("height", card.height.value, skel.height.value, 0.6f)
  }
}
