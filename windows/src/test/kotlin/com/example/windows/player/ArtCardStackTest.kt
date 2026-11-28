package com.example.windows.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The cards behind the art card of the Home corner: the playing cover keeps its 192 and its lean, the next tracks sit
 * smaller behind it (the fan of the queue stack of the Player page, which is not touched).
 */
@OptIn(ExperimentalTestApi::class)
class ArtCardStackTest {
  private fun t(n: Int) = Track("t$n", "Title $n", "Artist $n", "A$n", 200_000, "", "", true, "C:/m/t$n.mp3")
  private val list = (1..5).map(::t)

  private class Fixture {
    val controller = PlayerController(FakeOut(), MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
  }

  @Before @After fun reset() { ExpandedPlayer.close(); ExpandedPlayer.progress = 0f }

  private fun ComposeUiTest.corner(f: Fixture, reduced: Boolean = false) =
    setContent { TarkasTheme { Box(Modifier.size(432.dp, 324.dp)) { CompactPlayerBody(f.hooks, f.assets, showCard = true, reduced = reduced) } } }

  private fun ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun ComposeUiTest.backTitles(): List<String> =
    (1..ArtStackMath.MaxBack).mapNotNull { slot ->
      onAllNodesWithTag("art_back_$slot", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
    }

  private fun ComposeUiTest.settle() { mainClock.advanceTimeBy(1200); waitForIdle() }

  // The numbers --------------------------------------------------------------------------------------

  @Test fun theFrontCardKeepsItsSizeAndTheCardsBehindAreSmaller() {
    assertEquals(1f, ArtStackMath.scale(0), 0f)
    assertEquals(0.93f, ArtStackMath.scale(1), 1e-4f)
    assertEquals(0.86f, ArtStackMath.scale(2), 1e-4f)
    // Slots past the second look like the second: nothing smaller than what is drawn.
    assertEquals(ArtStackMath.scale(2), ArtStackMath.scale(3), 0f)
    assertEquals(0f, ArtStackMath.dx(0, true), 0f)
    assertEquals(0f, ArtStackMath.dy(0), 0f)
  }

  @Test fun theFanPeeksToTheRightAndSinksLikeTheQueueStack() {
    assertEquals(24f, ArtStackMath.dx(1, false), 1e-3f)
    assertEquals(45.6f, ArtStackMath.dx(2, false), 1e-3f)
    assertTrue(ArtStackMath.dx(2, true) > ArtStackMath.dx(2, false))
    assertTrue(ArtStackMath.dy(2) > ArtStackMath.dy(1))
    assertEquals(0f, ArtStackMath.rotation(1, false), 0f)
    assertTrue(ArtStackMath.rotation(2, true) > ArtStackMath.rotation(1, true))
  }

  @Test fun everyCardStaysInsideTheBoxTheStackReserves() {
    for (open in listOf(false, true)) for (slot in 1..ArtStackMath.MaxBack) {
      val right = ArtStackMath.rightEdge(slot, open)
      assertTrue("slot $slot open=$open reaches $right", right <= CompactMath.CardDp + ArtStackMath.ReachDp + 0.01f)
    }
    // Bottom edge: the sunk card still ends inside the 192 row, so nothing grows the corner upward or downward.
    for (slot in 1..ArtStackMath.MaxBack) {
      val bottom = CompactMath.CardDp / 2f + ArtStackMath.dy(slot) + ArtStackMath.scale(slot) * CompactMath.CardDp / 2f
      assertTrue("slot $slot bottom $bottom", bottom <= CompactMath.CardDp)
    }
    // The corner column is 384 wide: the card (24 in) plus the stack stays inside it.
    assertTrue(CompactMath.CardInsetDp + CompactMath.CardDp + ArtStackMath.ReachDp < CompactMath.WidthDp)
  }

  @Test fun theVeilGrowsWithTheDistanceAndIsZeroOnTheFront() {
    assertEquals(0f, ArtStackMath.veil(1f), 0f)
    assertEquals(0.3f, ArtStackMath.veil(ArtStackMath.scale(1)), 1e-3f)
    assertEquals(0.6f, ArtStackMath.veil(ArtStackMath.scale(2)), 1e-3f)
  }

  @Test fun theTracksAreThePlayingOneAndTheNextTwo() {
    assertEquals(emptyList<Track>(), ArtStackMath.tracks(null, list))
    assertEquals(listOf(list[0]), ArtStackMath.tracks(list[0], emptyList()))
    assertEquals(list.take(3), ArtStackMath.tracks(list[0], list.drop(1)))
  }

  // The corner ---------------------------------------------------------------------------------------

  @Test fun theFrontCardIsStill192AtTheSameLeftEdgeAndThePillDoesNotMove() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], list)
    corner(f); settle()
    val card = bounds("art_card"); val pill = bounds("compact_player")
    assertEquals(192f, card.width, 0.5f)
    assertEquals(192f, card.height, 0.5f)
    assertEquals(CompactMath.CardInsetDp.toFloat(), card.left - pill.left, 0.5f)
    assertEquals(CompactMath.GapDp.toFloat(), pill.top - card.bottom, 0.5f)
    assertEquals(384f, pill.width, 0.5f)
    assertEquals(96f, pill.height, 0.5f)
  }

  @Test fun theNextTwoTracksSitBehindTheCard() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], list)
    corner(f); settle()
    assertEquals(listOf("Title 2", "Title 3"), backTitles())
    onAllNodesWithTag("art_back_3", useUnmergedTree = true).assertCountEquals0()
  }

  @Test fun aSingleSongDrawsNoCardBehind() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], listOf(list[0]))
    corner(f); settle()
    assertTrue(backTitles().isEmpty())
    assertEquals(192f, bounds("art_card").width, 0.5f)
  }

  @Test fun anEmptyQueueDrawsNoCardBehind() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture()
    corner(f); settle()
    assertTrue(backTitles().isEmpty())
    assertEquals(192f, bounds("art_card").width, 0.5f)
  }

  @Test fun theStackFollowsNextAndPreviousIncludingBackwards() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[1], list)
    corner(f); settle()
    assertEquals(listOf("Title 3", "Title 4"), backTitles())
    f.controller.next(); settle()
    assertEquals(listOf("Title 4", "Title 5"), backTitles())
    f.controller.previous(); settle()
    assertEquals(listOf("Title 3", "Title 4"), backTitles())
    f.controller.previous(); settle()
    assertEquals(listOf("Title 2", "Title 3"), backTitles())
  }

  @Test fun theEndOfTheListWrapsToTheStartBecauseTheNextButtonDoes() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[3], list)
    corner(f); settle()
    // The manual next wraps whatever the repeat mode, so the card behind the last song is the first one (CoverNeighbors, auto = false).
    var guard = 0
    while (f.controller.ui.value.repeat != com.example.data.model.RepeatMode.OFF && guard++ < 4) f.controller.cycleRepeat()
    settle()
    assertEquals(listOf("Title 5", "Title 1"), backTitles())
    f.controller.next(); settle()
    assertEquals("t5", f.controller.ui.value.current?.id)
    assertEquals(listOf("Title 1", "Title 2"), backTitles())
  }

  @Test fun aClickOnTheSliverOfACardBehindPlaysThatTrack() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], list)
    corner(f); settle()
    val stack = bounds("art_stack")
    // The front card covers the middle of the cards behind: the click lands on the sliver to its right.
    val x = CompactMath.CardDp + 8f
    onNodeWithTag("art_stack", useUnmergedTree = true).performTouchInput { click(Offset(x, stack.height / 2f)) }
    settle()
    assertEquals("t2", f.controller.ui.value.current?.id)
  }

  @Test fun aClickOnTheFrontCardStillOpensThePlayerAndNeverChangesTheTrack() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], list)
    corner(f); settle()
    onNodeWithTag("art_card_cover", useUnmergedTree = true).performClick(); waitForIdle()
    assertEquals("t1", f.controller.ui.value.current?.id)
    assertTrue(ExpandedPlayer.open.value)
  }

  @Test fun theFrontCardStillLeansTowardThePointerWithTheStackBehind() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], list)
    corner(f); settle()
    onNodeWithTag("art_card").performMouseInput { moveTo(Offset(180f, 20f)) }
    mainClock.advanceTimeBy(400); waitForIdle()
    assertEquals(192f, bounds("art_card").width, 0.5f)
  }

  @Test fun withReducedMotionTheCardsAreInPlaceAtOnce() = runDesktopComposeUiTest(width = 432, height = 324) {
    val f = Fixture(); f.controller.play(list[0], list)
    mainClock.autoAdvance = false
    corner(f, reduced = true)
    mainClock.advanceTimeByFrame(); mainClock.advanceTimeByFrame()
    assertEquals(listOf("Title 2", "Title 3"), backTitles())
  }

  private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() {
    assertEquals(0, fetchSemanticsNodes(atLeastOneRootRequired = false).size)
  }
}
