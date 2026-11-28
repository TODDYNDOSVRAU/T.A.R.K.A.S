package com.example.windows.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.junit.Test

// kotlin.test's argument order (the message last), on JUnit 4, so the ported tests read like the originals.
private fun assertTrue(c: Boolean, msg: String = "") = org.junit.Assert.assertTrue(msg, c)
private fun <T> assertEquals(expected: T, actual: T, msg: String = "") = org.junit.Assert.assertEquals(msg, expected, actual)
private fun assertEquals(expected: Float, actual: Float, tol: Float) = org.junit.Assert.assertEquals(expected, actual, tol)
private fun assertNull(a: Any?) = org.junit.Assert.assertNull(a)

/**
 * The tests of oyenza-studio's spring.rs, motion.rs and island.rs (MIT, Jatevo Labs), ported with the code they test,
 * plus the cases of our own island (pill 192x48 to capsule 240x48) and of the content cross-fade (app.rs).
 */
class IslandMotionTest {
  private val pill = IslandGeom(120f, 32f, 16f)
  private val card = IslandGeom(620f, 216f, 40f)
  private val topGap = 8f

  /** A floating island resting as [g], the gap taken before the first frame. */
  private fun floating(g: IslandGeom) = IslandBody(g).apply { setFloating(topGap) }

  /** Step at [hz] until settled (or [maxS]); calls [each] after every frame. Returns the time taken. */
  private fun run(i: IslandBody, hz: Float, maxS: Float, each: (IslandBody) -> Unit = {}): Float {
    val dt = 1f / hz
    var t = 0f
    while (!i.settled() && t < maxS) {
      i.step(dt, t.toDouble())
      t += dt
      each(i)
    }
    return t
  }

  private fun tuning(s: IslandSpring) = s.stiffness to s.damping

  /** Compose's Rect from oyenza's (x, y, w, h). */
  private fun xywh(x: Float, y: Float, w: Float, h: Float) = Rect(x, y, x + w, y + h)

  // ---- spring.rs ----

  @Test
  fun springConvergesToTarget() {
    val s = IslandSpring.bouncy(0f)
    s.setTarget(100f)
    var t = 0f
    while (!s.isSettled() && t < 3f) {
      s.step(1f / 60f)
      t += 1f / 60f
    }
    assertTrue(s.isSettled(), "spring did not settle within 3s")
    assertEquals(100f, s.value)
    assertTrue(t < 1.2f, "spring too slow: ${t}s")
  }

  @Test
  fun bouncyOvershootsALittle() {
    val s = IslandSpring.bouncy(0f)
    s.setTarget(100f)
    var most = 0f
    repeat(180) { s.step(1f / 60f); most = max(most, s.value) }
    assertTrue(most > 100f && most < 115f, "overshoot $most")
  }

  @Test
  fun smoothNeverOvershoots() {
    val s = IslandSpring.smooth(0f)
    s.setTarget(1f)
    repeat(240) { s.step(1f / 60f); assertTrue(s.value <= 1f + 1e-3f) }
  }

  @Test
  fun largeDtIsStable() {
    val s = IslandSpring.bouncy(0f)
    s.setTarget(50f)
    s.step(5f) // clamped to 0.1 s internally
    assertTrue(s.value.isFinite())
  }

  // ---- motion.rs ----

  private fun zeta(t: Pair<Float, Float>) = t.second / (2f * sqrt(t.first))

  @Test
  fun tuningsAreWhatTheCommentsSay() {
    for ((t, z) in listOf(
      IslandMotion.GROW_W to 0.73f, IslandMotion.GROW_H to 0.73f, IslandMotion.SHRINK_W to 0.90f,
      IslandMotion.SHRINK_H to 0.91f, IslandMotion.RADIUS_GROW to 1f, IslandMotion.RADIUS_SHRINK to 1f,
      IslandMotion.TOP to 1f,
    )) assertTrue(abs(zeta(t) - z) < 0.006f, "$t: zeta ${zeta(t)} (comment says $z)")
    // The width leads the height growing, the height leads shrinking.
    assertTrue(IslandMotion.GROW_W.first > IslandMotion.GROW_H.first && IslandMotion.SHRINK_H.first > IslandMotion.SHRINK_W.first)
  }

  @Test
  fun hoverLiftKeepsACapsule() {
    val g = IslandMotion.hoverLift(pill)
    assertEquals(Triple(126f, 38f, 19f), Triple(g.w, g.h, g.r))
    assertEquals(g.h / 2f, g.r)
    // Centred on the resting pill, then 1 dp lower: 2 up into the gap, 4 down.
    assertEquals(-2f, g.dy)
  }

  @Test
  fun contentScalesAboutTheSmallEnd() {
    // Opening: the middle of the body as it grows.
    assertEquals(Offset(250f, 53f), IslandMotion.pivot(xywh(100f, 8f, 300f, 90f), card))
    // Closing: the middle of the pill it's becoming.
    assertEquals(Offset(250f, 24f), IslandMotion.pivot(xywh(40f, 8f, 420f, 140f), pill))
    // At rest, the body's middle.
    assertEquals(Offset(250f, 24f), IslandMotion.pivot(xywh(190f, 8f, 120f, 32f), pill))
  }

  @Test
  fun ghostShrinksWithTheFasterAxis() {
    assertEquals(1f, IslandMotion.ghostScale(card, card, 0.6f))
    // The height leads a floating shrink: it sets the scale.
    assertTrue(abs(IslandMotion.ghostScale(IslandGeom(496f, 162f, 30f), card, 0.6f) - 0.75f) < 1e-6f)
    assertEquals(0.6f, IslandMotion.ghostScale(pill, card, 0.6f))
    // Growing never enlarges the old picture.
    assertEquals(1f, IslandMotion.ghostScale(card, pill, 0.6f))
  }

  // ---- island.rs ----

  @Test
  fun classicTuningsUnchanged() {
    assertEquals(
      listOf(420f to 30f, 300f to 26f, 330f to 33f, 420f to 38f, 420f to 41f),
      listOf(IslandBody.GROW_W, IslandBody.GROW_H, IslandBody.SHRINK_W, IslandBody.SHRINK_H, IslandBody.RADIUS),
    )
    assertEquals(Triple(700f to 52.9f, 0.03, 0.05), Triple(IslandBody.CONTENT, IslandBody.FADE_DELAY_GROW, IslandBody.FADE_DELAY_SHRINK))
    val i = IslandBody(pill)
    i.setFloating(null)
    i.setTarget(card, 0.0, true)
    assertEquals(Triple(IslandBody.GROW_W, IslandBody.GROW_H, IslandBody.RADIUS), Triple(tuning(i.w), tuning(i.h), tuning(i.r)))
    assertEquals(IslandBody.FADE_DELAY_GROW, i.fadeInAt)
    run(i, 60f, 2f) { assertEquals(0f, it.top()) }
    assertEquals(card, i.current())
    i.setTarget(pill, 0.0, true)
    assertEquals(Triple(IslandBody.SHRINK_W, IslandBody.SHRINK_H, IslandBody.RADIUS), Triple(tuning(i.w), tuning(i.h), tuning(i.r)))
    assertEquals(IslandBody.FADE_DELAY_SHRINK, i.fadeInAt)
    // Hanging: no landing floor, the corner radius as given, never floating.
    assertNull(i.floorW); assertNull(i.floorH)
    run(i, 60f, 2f) { assertTrue(!it.floating() && it.top() == 0f) }
    assertEquals(pill, i.current())
  }

  @Test
  fun classicMorphMatchesTheOldIsland() {
    // The spring code path the classic island runs, step for step.
    val i = IslandBody(pill)
    val w = IslandSpring(120f, 420f, 30f)
    val h = IslandSpring(32f, 300f, 26f)
    val r = IslandSpring(16f, 420f, 41f)
    i.setTarget(card, 0.0, false)
    w.target = card.w; h.target = card.h; r.target = card.r
    for (k in 0 until 40) {
      if (k == 12) {
        // Turned around mid-flight.
        i.setTarget(pill, 0.2, false)
        w.retune(IslandBody.SHRINK_W.first, IslandBody.SHRINK_W.second)
        h.retune(IslandBody.SHRINK_H.first, IslandBody.SHRINK_H.second)
        w.target = pill.w; h.target = pill.h; r.target = pill.r
      }
      i.step(1f / 60f, k / 60.0)
      w.step(1f / 60f); h.step(1f / 60f); r.step(1f / 60f)
      val g = i.current()
      assertEquals(listOf(max(w.value, 20f), max(h.value, 8f), max(r.value, 0f), 0f), listOf(g.w, g.h, g.r, g.dy), "frame $k")
    }
  }

  @Test
  fun floatingSpringsSettle() {
    for (hz in listOf(60f, 144f)) {
      val i = floating(pill)
      i.setTarget(card, 0.0, true)
      assertEquals(Triple(IslandMotion.GROW_W, IslandMotion.GROW_H, IslandMotion.RADIUS_GROW), Triple(tuning(i.w), tuning(i.h), tuning(i.r)))
      var t = run(i, hz, 2f)
      assertTrue(i.settled() && t < 0.9f, "grow at $hz Hz settled after $t s")
      assertEquals(card, i.current())
      i.setTarget(pill, 0.0, true)
      assertEquals(Triple(IslandMotion.SHRINK_W, IslandMotion.SHRINK_H, IslandMotion.RADIUS_SHRINK), Triple(tuning(i.w), tuning(i.h), tuning(i.r)))
      t = run(i, hz, 2f)
      assertTrue(i.settled() && t < 0.6f, "shrink at $hz Hz settled after $t s")
      assertEquals(pill, i.current())
      assertEquals(xywh(240f, 8f, 120f, 32f), i.bodyRectAt(300f))
    }
  }

  @Test
  fun floatingGrowStretchesThenDrops() {
    // The width leads: early on the pill is a wider capsule than either end.
    val i = floating(pill)
    i.setTarget(card, 0.0, false)
    var widest = 0f
    var wPeak = 0f
    var hPeak = 0f
    run(i, 60f, 2f) {
      val g = it.current()
      widest = max(widest, g.w / g.h); wPeak = max(wPeak, g.w); hPeak = max(hPeak, g.h)
    }
    assertTrue(widest > pill.w / pill.h + 0.25f, "aspect peaked at $widest")
    // A little overshoot, about 3 %.
    assertTrue(wPeak > card.w && wPeak < card.w * 1.035f, "width peaked at $wPeak")
    assertTrue(hPeak > card.h && hPeak < card.h * 1.035f, "height peaked at $hPeak")
  }

  @Test
  fun floatingShrinkNeverSquashes() {
    // (from, targets with the time they come, the least size allowed): straight down, an axis shrinking while the
    // other grows, and morphs turned around mid-flight (onto a lift just above the pill, and onto a size the width
    // has already passed: it carries on a little, then turns, above the pill).
    val lifted = IslandMotion.hoverLift(pill)
    val cases = listOf(
      Triple(card, listOf(0f to pill), 120f to 32f),
      Triple(IslandGeom(340f, 48f, 24f), listOf(0f to IslandGeom(520f, 32f, 16f)), 340f to 32f),
      Triple(card, listOf(0f to pill, 0.12f to lifted), 126f to 32f),
      Triple(card, listOf(0f to pill, 0.2f to IslandGeom(150f, 36f, 18f)), 120f to 32f),
    )
    for ((from, steps, least) in cases) {
      val i = floating(from)
      var t = 0f
      var next = 0
      var want = from
      while (t < 1.5f) {
        if (next < steps.size && t >= steps[next].first - 1e-4f) {
          want = steps[next].second
          i.setTarget(want, t.toDouble(), false)
          next++
        }
        i.step(1f / 60f, t.toDouble())
        t += 1f / 60f
        val g = i.current()
        assertTrue(g.w >= least.first && g.h >= least.second, "$from -> $want: $g at $t s")
        assertTrue(g.r <= g.h / 2f + 1e-4f, "rounder than a capsule: $g")
      }
      assertTrue(i.settled(), "$from -> $want did not settle")
      assertEquals(want.w to want.h, i.current().w to i.current().h)
    }
  }

  @Test
  fun aTurnedAroundMorphKeepsItsFloor() {
    // Falling fast just above the pill when a slightly wider target comes: it turns around on the pill's size at the
    // lowest, never below it.
    val i = floating(card)
    i.setTarget(pill, 0.0, false)
    i.w.value = 124f; i.w.velocity = -1500f
    i.setTarget(IslandGeom(130f, 32f, 16f), 0.0, false)
    assertEquals(IslandMotion.GROW_W, tuning(i.w))
    var least = Float.MAX_VALUE
    run(i, 60f, 2f) { least = min(least, it.current().w) }
    assertEquals(120f, least)
    assertEquals(130f, i.current().w)
  }

  @Test
  fun capsuleStaysACapsule() {
    val lifted = IslandMotion.hoverLift(pill)
    val i = floating(pill)
    for ((to, maxS) in listOf(lifted to 0.6f, pill to 0.5f, card to 0.9f, pill to 0.6f)) {
      i.setTarget(to, 0.0, false)
      val t = run(i, 60f, 2f) { val g = it.current(); assertTrue(g.r <= g.h / 2f + 1e-4f, "$g") }
      assertTrue(t < maxS, "to $to took $t s")
      val g = i.current()
      assertEquals(Triple(to.w, to.h, to.r), Triple(g.w, g.h, g.r))
    }
  }

  @Test
  fun hoverLiftRisesAndSinks() {
    val lifted = IslandMotion.hoverLift(pill)
    val i = floating(pill)
    i.setTarget(lifted, 0.0, false)
    var highest = 8f
    run(i, 60f, 2f) { highest = min(highest, it.top()) }
    // Centred growth (3 up) less the 1 dp sink: the top settles 2 dp up.
    assertEquals(6f, i.top())
    assertTrue(highest >= 6f - 1e-3f, "rose to $highest")
    assertEquals(xywh(237f, 6f, 126f, 38f), i.bodyRectAt(300f))
    i.setTarget(pill, 0.0, false)
    run(i, 60f, 2f) { assertTrue(it.top() <= 8f + 1e-3f && it.top() >= 6f - 1e-3f) }
    assertEquals(8f, i.top())
    assertEquals(pill, i.current())
  }

  @Test
  fun firstGapIsTakenAtOnce() {
    val i = IslandBody(pill)
    i.setFloating(8f)
    assertEquals(8f, i.top())
    assertTrue(i.floating() && i.settled())
    i.step(1f / 60f, 0.0)
    assertEquals(8f, i.top())
    // Snapping takes the gap at once too.
    val j = IslandBody(pill)
    j.step(1f / 60f, 0.0)
    j.setFloating(8f)
    j.snap(IslandMotion.hoverLift(pill))
    assertEquals(6f, j.top())
    assertTrue(j.settled())
  }

  @Test
  fun topSlidesOffAndBackOn() {
    for (hz in listOf(60f, 144f)) {
      val i = IslandBody(pill)
      i.step(1f / hz, 0.0)
      // Floating turned on: the island drops and settles at the gap.
      i.setFloating(8f)
      assertTrue(i.floating() && !i.settled() && i.top() == 0f)
      var between = 0
      var last = 0f
      val t = run(i, hz, 2f) {
        assertTrue(it.top() >= last && it.top() <= 8f, "top ${it.top()} after $last")
        last = it.top()
        if (it.top() > 0.5f && it.top() < 7.5f) between++
      }
      assertTrue(between >= (0.1f * hz).toInt(), "top jumped: $between frames in between at $hz Hz")
      assertTrue(t < 0.6f, "gap settled after $t s")
      assertEquals(8f, i.top())
      // Floating turned off: it rises back, drawn floating until it docks.
      i.setFloating(null)
      assertTrue(i.floating())
      var up = 8f
      run(i, hz, 2f) {
        assertTrue(it.top() <= up && it.top() >= 0f)
        assertEquals(it.top() > 0f, it.floating())
        up = it.top()
      }
      assertTrue(i.settled() && i.top() == 0f && !i.floating())
    }
  }

  @Test
  fun switchingMidMorphRetunes() {
    val i = IslandBody(pill)
    i.setTarget(card, 0.0, false)
    for (k in 0 until 5) i.step(1f / 60f, k / 60.0)
    val v = i.w.velocity
    i.setFloating(8f)
    // Same motion, floating tunings: nothing jumps.
    assertEquals(IslandMotion.GROW_W to IslandMotion.GROW_H, tuning(i.w) to tuning(i.h))
    assertEquals(v, i.w.velocity)
    i.setFloating(null)
    assertEquals(Triple(IslandBody.GROW_W, IslandBody.GROW_H, IslandBody.RADIUS), Triple(tuning(i.w), tuning(i.h), tuning(i.r)))
  }

  // ---- our island: the pill (192x48) opens into the capsule (240x48) that holds the pages ----

  private val ourPill = IslandGeom(192f, 48f, 24f)
  private val ourCapsule = IslandGeom(240f, 48f, 24f)

  @Test
  fun ourPillOpensAndClosesWithoutSquashing() {
    for (hz in listOf(60f, 144f)) {
      val i = IslandBody(ourPill).apply { setFloating(2f) }
      i.setTarget(ourCapsule, 0.0, true)
      var t = run(i, hz, 2f) { val g = it.current(); assertTrue(g.w >= 192f && g.h >= 48f && g.r <= g.h / 2f + 1e-4f, "$g") }
      assertTrue(i.settled() && t < 0.9f, "open at $hz Hz settled after $t s")
      assertEquals(ourCapsule, i.current())
      i.setTarget(ourPill, 0.0, true)
      t = run(i, hz, 2f) { val g = it.current(); assertTrue(g.w >= 192f && g.h >= 48f, "$g") }
      assertTrue(i.settled() && t < 0.6f, "close at $hz Hz settled after $t s")
      assertEquals(ourPill, i.current())
      // At rest the body sits on its place: 0 dp off it.
      assertEquals(0f, i.current().dy)
    }
  }

  @Test
  fun ourHoverLiftRisesTwoDp() {
    val i = IslandBody(ourPill).apply { setFloating(2f) }
    i.setTarget(IslandMotion.hoverLift(ourPill), 0.0, false)
    run(i, 60f, 2f)
    assertEquals(IslandGeom(198f, 54f, 27f, -2f), i.current())
    assertEquals(0f, i.top())
  }

  // ---- app.rs: the content cross-fade ----

  @Test
  fun outgoingFadesWithASmoothstep() {
    assertEquals(1f, IslandMotion.outgoingLeft(0.0))
    assertEquals(0.5f, IslandMotion.outgoingLeft(0.07), 1e-6f)
    assertEquals(0f, IslandMotion.outgoingLeft(0.14))
    assertEquals(0f, IslandMotion.outgoingLeft(1.0))
    assertEquals(1f, IslandMotion.outgoingLeft(-1.0))
  }

  @Test
  fun newContentCrossFadesAndTheSameContentOnlyResizes() {
    val i = IslandBody(ourPill).apply { setFloating(2f) }
    val c = IslandContent(false)
    // Same key, new size: no fade.
    c.change(i, false, IslandMotion.hoverLift(ourPill), 0.0)
    assertNull(c.outgoing)
    assertEquals(1f, i.contentAlpha())
    // New key: the old content leaves at its alpha, the new one starts transparent after the grow delay.
    c.change(i, true, ourCapsule, 0.0)
    assertEquals(false, c.outgoing)
    assertEquals(true, c.key)
    assertEquals(0f, i.contentAlpha())
    assertEquals(IslandBody.FADE_DELAY_GROW, i.fadeInAt)
    assertEquals(1f, c.ghostAlpha(0.0))
    assertEquals(0.5f, c.ghostAlpha(0.07), 1e-6f)
    // Gone after the fade, then dropped.
    assertEquals(0f, c.ghostAlpha(0.15))
    assertNull(c.outgoing)
  }

  @Test
  fun contentComingBackMidFadePicksUpWhereItWas() {
    val i = IslandBody(ourPill).apply { setFloating(2f) }
    val c = IslandContent(false)
    c.change(i, true, ourCapsule, 0.0)
    // Turned around 70 ms in: the pill was half gone, so it comes back from 0.5, at once.
    c.change(i, false, ourPill, 0.07)
    assertEquals(true, c.outgoing)
    assertEquals(0.5f, i.contentAlpha(), 1e-6f)
    assertNull(i.fadeInAt)
  }

  @Test
  fun incomingContentBloomsFromTheMiddle() {
    assertEquals(0.94f, c0.incomingScale(0f))
    assertEquals(1f, c0.incomingScale(1f))
    assertEquals(-2f, c0.incomingDrop(0f))
    assertEquals(0f, c0.incomingDrop(1f))
  }

  private val c0 = IslandContent(false)
}
