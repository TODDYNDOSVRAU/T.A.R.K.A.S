package com.example.windows.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.example.data.model.Track
import com.example.ui.theme.TarkasTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The queue card stack: the engine's numbers, then the stack over the real player. */
@OptIn(ExperimentalTestApi::class)
class QueueCardStackTest {
  private fun t(n: Int) = Track("t$n", "Title $n", "Artist $n", "Album $n", 200_000, "", "", true, "C:/m/t$n.mp3")
  private val list = (1..6).map(::t)

  private class Fixture {
    val controller = PlayerController(FakeOut(), MemoryPrefs(), exists = { true })
    val hooks = PlayerHooks(controller, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null))
    val assets = TrackAssets(CoroutineScope(Dispatchers.Unconfined), WaveformReader(null))
    val engine = QueueStackEngine()
  }

  private fun settle(e: QueueStackEngine, limit: Int = 600): Int {
    var n = 0
    while (n < limit && !e.frame(0.016f)) n++
    return n
  }

  private fun engine(canNext: Boolean = true, canPrev: Boolean = true, w: Float = 192f, h: Float = 288f): QueueStackEngine =
    QueueStackEngine().also { it.canNext = canNext; it.canPrev = canPrev; it.layout(w, h, Fan.Narrow, 2); it.sync(listOf("a#0", "b#0", "c#0", "d#0")); settle(it) }

  // Pure ------------------------------------------------------------------------------------------

  @Test fun keysSeparateTheCopiesOfASongAndAreEmptyWithoutATrack() {
    assertEquals(emptyList<String>(), StackModel.keys(null, listOf(t(1))))
    assertEquals(listOf("t1#0", "t2#0", "t1#1", "t2#1"), StackModel.keys(t(1), listOf(t(2), t(1), t(2))))
  }

  @Test fun theLargestCardThatFitsWinsAndNothingIsCut() {
    val big = StackLayout.fit(1000f, 600f)
    assertEquals(384, big.w); assertEquals(480, big.h); assertEquals(2, big.back)
    assertTrue(big.wide)
    val mid = StackLayout.fit(400f, 320f)
    assertEquals(192, mid.w); assertEquals(288, mid.h)
    val tiny = StackLayout.fit(100f, 100f)
    assertEquals(96, tiny.w); assertEquals(0, tiny.back)
    for (mw in 100..1400 step 50) for (mh in 100..800 step 50) {
      val f = StackLayout.fit(mw.toFloat(), mh.toFloat())
      assertTrue("$mw x $mh -> ${f.w}x${f.h}", f.h <= mh && f.totalW <= mw)
      assertTrue(f.w in listOf(96, 192, 384))
    }
  }

  @Test fun fewerBackCardsWhenNarrow() {
    assertTrue(StackLayout.fit(260f, 300f).back <= StackLayout.fit(600f, 300f).back)
    assertEquals(Fan.Narrow, StackLayout.fanFor(599f))
    assertEquals(Fan.Wide, StackLayout.fanFor(600f))
  }

  @Test fun theSpringsComeToRestAtTheirSlots() {
    val e = engine()
    assertTrue(settle(e) < 5)
    val cards = e.cards
    assertEquals(4, cards.size)
    val front = cards.first { it.slot == 0 }
    assertEquals(0f, front.x.x, 0.2f); assertEquals(1f, front.s.x, 0.001f)
    val p = Pose()
    e.slotPose(1, false, 0, false, p)
    val b1 = cards.first { it.slot == 1 }
    assertEquals(p.x, b1.x.x, 0.2f); assertEquals(p.s, b1.s.x, 0.001f)
    assertEquals(0f, cards.first { it.slot == 3 }.o, 0.01f)
  }

  @Test fun theThrowNeedsTwentyPercentOfTheWidthOr420PerSecond() {
    val far = 0.2f * 192f
    fun throwTo(x: Float, vx: Float = 0f, next: Boolean = true, prev: Boolean = true): Release {
      val e = engine(next, prev); e.beginDrag(); e.drag(x, 0f, vx); return e.release(vx)
    }
    assertEquals(Release.Next, throwTo(-far - 1f))
    assertEquals(Release.None, throwTo(-far + 4f))
    assertEquals(Release.Next, throwTo(-10f, vx = -430f))
    assertEquals(Release.None, throwTo(-3f, vx = -2000f))
    assertEquals(Release.None, throwTo(-10f, vx = -400f))
    assertEquals(Release.Prev, throwTo(far * 4f))
    assertEquals(Release.None, throwTo(far * 4f, prev = false))
    assertEquals(Release.None, throwTo(-far * 2f, next = false))
  }

  @Test fun aDragToTheRightIsDampedAndAThrownCardFliesLeftBeforeFading() {
    val e = engine()
    e.beginDrag(); e.drag(1000f, 0f)
    e.frame(0.016f)
    assertTrue(e.cards.first { it.slot == 0 }.x.t < 0.5f * 192f)
    e.release(0f)
    e.beginDrag(); e.drag(-100f, 0f); assertEquals(Release.Next, e.release(0f))
    e.sync(listOf("b#0", "c#0", "d#0", "e#0"))
    val gone = e.cards.first { it.key == "a#0" }
    assertTrue(gone.leaving && gone.flying)
    assertEquals(listOf("b#0", "c#0", "d#0", "e#0"), e.liveKeys())
    settle(e, 200)
    assertTrue(e.cards.none { it.key == "a#0" })
  }

  @Test fun theSyncAnimatesOnlyTheDifferenceBetweenTwoOrders() {
    val e = engine()
    e.sync(listOf("a#0", "c#0", "d#0", "e#0"))
    assertTrue(e.cards.first { it.key == "b#0" }.leaving)
    assertFalse(e.cards.first { it.key == "a#0" }.leaving)
    assertEquals(0f, e.cards.first { it.key == "e#0" }.o, 0.0001f)
    settle(e)
    assertEquals(listOf("a#0", "c#0", "d#0", "e#0"), e.cards.map { it.key })
    val before = engine()
    before.sync(listOf("z#0", "a#0", "b#0", "c#0"))
    val entering = before.cards.first { it.key == "z#0" }
    assertEquals(0.9f, entering.s.x, 0.0001f); assertTrue(entering.entrance)
  }

  @Test fun reducedMotionFadesWithoutFlightOrTilt() {
    val e = QueueStackEngine().also { it.reduced = true; it.canNext = true; it.layout(192f, 288f, Fan.Narrow, 2); it.sync(listOf("a#0", "b#0", "c#0")); settle(it) }
    e.hoverMove(80f, 80f); settle(e)
    val f = e.cards.first { it.slot == 0 }
    assertEquals(0f, f.rx.x, 0.0001f); assertEquals(0f, f.ry.x, 0.0001f)
    val p = Pose(); e.slotPose(1, false, 0, false, p)
    assertEquals(p.x, e.cards.first { it.slot == 1 }.x.x, 0.01f)
    e.sync(listOf("b#0", "c#0", "d#0"))
    assertFalse(e.cards.first { it.key == "a#0" }.flying)
  }

  @Test fun theFanTiltsTowardThePointerAndOpensOnHover() {
    val e = engine()
    e.hoverMove(80f, -100f); settle(e)
    val f = e.cards.first { it.slot == 0 }
    assertTrue(f.ry.x > 0f && f.ry.x <= StackTuning.MaxRy)
    assertTrue(f.rx.x > 0f && f.rx.x <= StackTuning.MaxRx + 0.01f)
    val closed = Pose(); e.slotPose(1, false, 0, false, closed)
    assertTrue(e.cards.first { it.slot == 1 }.x.x > closed.x)
    e.hoverExit(); settle(e)
    assertEquals(closed.x, e.cards.first { it.slot == 1 }.x.x, 0.2f)
    assertEquals(0f, e.cards.first { it.slot == 0 }.ry.x, 0.02f)
  }

  @Test fun hitTestsTellTheFrontFromTheCardsBehind() {
    val e = engine()
    assertTrue(e.hitFront(0f, 0f))
    assertEquals(0, e.hitBack(0f, 0f))
    val b1 = e.cards.first { it.slot == 1 }
    assertEquals(1, e.hitBack(b1.x.x + 192f * b1.s.x / 2f - 4f, b1.y.x))
    assertEquals(0, e.hitBack(-500f, 0f))
  }

  @Test fun theThrowSpeedUsesTheLast90MsAndIsZeroWhenTheHandRested() {
    val v = ThrowVelocity()
    for (i in 0..3) v.add(i * 30L, i * 30f)
    assertEquals(1000f, v.release(90), 1f)
    assertEquals(0f, v.release(300), 0f)
    v.reset(); v.add(0, 0f); v.add(1, 100000f)
    assertEquals(3800f, v.release(1), 0f)
  }

  @Test fun theLoopHasNothingToDoOnceEverythingIsAtRest() {
    val e = engine()
    val t0 = e.tick
    assertTrue(e.frame(0.016f)); assertTrue(e.frame(0.016f))
    assertEquals(t0 + 2, e.tick)
  }

  // Over the real player -----------------------------------------------------------------------------

  private fun ComposeUiTest.stage(f: Fixture, w: Int, h: Int, reduced: Boolean = false) {
    setContent { TarkasTheme { Box(Modifier.size(w.dp, h.dp)) { QueueCardStackBody(f.hooks, f.assets, reduced, Modifier, f.engine) } } }
    mainClock.advanceTimeBy(1000)
    waitForIdle()
  }

  private fun f6(): Fixture = Fixture().also { it.controller.play(list[0], list) }

  @Test fun theCardsAreTheRealQueueInOrder() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = f6()
    f.controller.addToQueue(list[5])
    stage(f, 400, 320)
    val nb = f.controller.neighbors()
    assertEquals(nb.after.map { it.id }, f.engine.liveKeys().drop(1).map { it.substringBefore('#') })
    assertEquals("t1", f.engine.liveKeys().first().substringBefore('#'))
    assertEquals("t6", f.engine.liveKeys()[1].substringBefore('#'))
    onAllNodesWithTag("stack_title", useUnmergedTree = true)[0].assertExists()
    onNodeWithTag("stack_card_1", useUnmergedTree = true).assertExists()
  }

  @Test fun aClickOnACardBehindPlaysThatItemOfTheQueue() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = f6()
    f.controller.addToQueue(list[5])
    stage(f, 400, 320)
    val b = f.engine.cards.first { it.slot == 1 }
    val fit = StackLayout.fit(400f, 320f)
    val at = Offset(fit.w * (0.5f + StackLayout.LeftShift) + b.x.x + fit.w * b.s.x / 2f - 6f, fit.h / 2f + b.y.x)
    onNodeWithTag("queue_stack_stage").performMouseInput { moveTo(at); click(at) }
    waitForIdle()
    assertEquals("t6", f.controller.ui.value.current?.id)
    assertTrue(f.controller.ui.value.queue.isEmpty())
  }

  @Test fun aClickOnTheSecondCardBehindPlaysThatTrackOfTheList() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = f6()
    stage(f, 400, 320)
    val b = f.engine.cards.first { it.slot == 2 }
    val fit = StackLayout.fit(400f, 320f)
    val at = Offset(fit.w * (0.5f + StackLayout.LeftShift) + b.x.x + fit.w * b.s.x / 2f - 6f, fit.h / 2f + b.y.x)
    onNodeWithTag("queue_stack_stage").performMouseInput { moveTo(at); click(at) }
    waitForIdle()
    assertEquals("t3", f.controller.ui.value.current?.id)
  }

  private fun ComposeUiTest.dragFront(fit: StackLayout.Fit, dx: Float) {
    val start = Offset(fit.w * (0.5f + StackLayout.LeftShift), fit.h / 2f)
    onNodeWithTag("queue_stack_stage").performMouseInput {
      moveTo(start); press(); moveBy(Offset(dx / 4f, 0f)); moveBy(Offset(dx / 4f, 0f)); moveBy(Offset(dx / 2f, 0f))
      advanceEventTime(200) // the hand rests before letting go: no throw speed, only the distance counts
      release()
    }
    waitForIdle()
  }

  @Test fun draggingLeftPlaysNextAndRightPlaysPrevious() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = Fixture().also { it.controller.play(list[2], list) }
    stage(f, 400, 320)
    val fit = StackLayout.fit(400f, 320f)
    dragFront(fit, -120f)
    assertEquals("t4", f.controller.ui.value.current?.id)
    mainClock.advanceTimeBy(1000); waitForIdle()
    dragFront(fit, 160f)
    assertEquals("t3", f.controller.ui.value.current?.id)
  }

  @Test fun aShortDragSpringsBackAndChangesNothing() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = f6()
    stage(f, 400, 320)
    dragFront(StackLayout.fit(400f, 320f), -20f)
    mainClock.advanceTimeBy(1000); waitForIdle()
    assertEquals("t1", f.controller.ui.value.current?.id)
    // The pointer is still over the stack, so the front card rests in the open pose, not where the drag left it.
    assertEquals(-0.03f * f.engine.w, f.engine.cards.first { it.slot == 0 }.x.x, 0.5f)
  }

  @Test fun theButtonsOfThePlayerKeepTheStackInSync() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = f6()
    stage(f, 400, 320)
    f.hooks.next(); mainClock.advanceTimeBy(1000); waitForIdle()
    assertEquals(listOf("t2", "t3", "t4", "t5"), f.engine.liveKeys().map { it.substringBefore('#') })
    f.hooks.prev(); mainClock.advanceTimeBy(1000); waitForIdle()
    assertEquals(listOf("t1", "t2", "t3", "t4"), f.engine.liveKeys().map { it.substringBefore('#') })
  }

  @Test fun nothingPlayingShowsTheEmptyMessage() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = Fixture()
    stage(f, 400, 320)
    onNodeWithTag("queue_stack_empty", useUnmergedTree = true).assertExists()
    onNodeWithTag("queue_stack_stage").assertDoesNotExist()
  }

  @Test fun aSingleTrackIsOneCardAndCannotBeThrownAway() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = Fixture().also { it.controller.play(list[0], listOf(list[0])) }
    stage(f, 400, 320)
    assertEquals("t1", f.engine.liveKeys().first().substringBefore('#'))
    assertFalse(f.engine.canPrev)
    dragFront(StackLayout.fit(400f, 320f), 160f)
    assertEquals("t1", f.controller.ui.value.current?.id)
    onNodeWithTag("stack_card_0", useUnmergedTree = true).assertExists()
  }

  @Test fun reducedMotionPlaysWithoutTheFlight() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = f6()
    stage(f, 400, 320, reduced = true)
    dragFront(StackLayout.fit(400f, 320f), -120f)
    assertEquals("t2", f.controller.ui.value.current?.id)
    assertTrue(f.engine.cards.none { it.flying })
  }

  @Test fun theCardsBehindCannotBeFocusedAndTheSwapIsAnnounced() = runDesktopComposeUiTest(width = 400, height = 320) {
    val f = f6()
    stage(f, 400, 320)
    onNodeWithTag("stack_card_1", useUnmergedTree = true).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
    onNodeWithTag("stack_card_1", useUnmergedTree = true).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    val stage = onNodeWithTag("queue_stack_stage")
    stage.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    val d = stage.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
    assertNotNull(d)
    assertTrue(d.toString(), d.toString().contains("Title 1"))
    f.hooks.next(); mainClock.advanceTimeBy(1000); waitForIdle()
    assertTrue(stage.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).toString().contains("Title 2"))
  }
}
