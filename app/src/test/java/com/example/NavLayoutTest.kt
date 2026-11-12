package com.example

import com.example.ui.components.NavLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLayoutTest {

  private val eps = 1e-3f

  @Test fun widthsAlwaysAddUpToTotal() {
    for (count in 2..5) {
      for (weight in listOf(1f, 1.5f, 2.2f, 4f)) {
        var p = -0.5f
        while (p <= count - 0.5f) {
          val w = NavLayout.slotWidths(p, count, 970f, weight)
          assertEquals("count=$count weight=$weight p=$p", 970f, w.sum(), eps)
          p += 0.05f
        }
      }
    }
  }

  @Test fun wholePositionGivesActiveWeight() {
    for (count in 2..5) {
      for (sel in 0 until count) {
        val w = NavLayout.slotWidths(sel.toFloat(), count, 1000f, 2f)
        val unit = 1000f / (count + 1)
        for (i in 0 until count) assertEquals(if (i == sel) 2 * unit else unit, w[i], eps)
      }
    }
  }

  @Test fun halfwayShareIsSplitEvenly() {
    val w = NavLayout.slotWidths(1.5f, 4, 900f, 3f)
    // weights 1, 2, 2, 1 -> 6 parts of 150
    assertEquals(150f, w[0], eps)
    assertEquals(300f, w[1], eps)
    assertEquals(300f, w[2], eps)
    assertEquals(150f, w[3], eps)
  }

  @Test fun movingRightShrinksTheOldTabAndGrowsTheNewOne() {
    for (count in 2..5) {
      for (k in 0 until count - 1) {
        var prev = NavLayout.slotWidths(k.toFloat(), count, 800f, 2f)
        var f = 0.1f
        while (f <= 1.0001f) {
          val w = NavLayout.slotWidths(k + f, count, 800f, 2f)
          assertTrue(w[k] <= prev[k] + eps)
          assertTrue(w[k + 1] >= prev[k + 1] - eps)
          // Tabs not involved keep their width.
          for (i in 0 until count) if (i != k && i != k + 1) assertEquals(prev[i], w[i], eps)
          prev = w
          f += 0.1f
        }
      }
    }
  }

  @Test fun mirroredPositionGivesMirroredWidths() {
    for (count in 2..5) {
      var p = 0f
      while (p <= count - 1f) {
        val w = NavLayout.slotWidths(p, count, 640f, 1.8f)
        val m = NavLayout.slotWidths(count - 1 - p, count, 640f, 1.8f)
        for (i in 0 until count) assertEquals(w[i], m[count - 1 - i], eps)
        p += 0.125f
      }
    }
  }

  @Test fun positionOutsideTheBarIsClamped() {
    val low = NavLayout.slotWidths(-3f, 4, 400f, 2f)
    val zero = NavLayout.slotWidths(0f, 4, 400f, 2f)
    val high = NavLayout.slotWidths(9f, 4, 400f, 2f)
    val last = NavLayout.slotWidths(3f, 4, 400f, 2f)
    for (i in 0 until 4) {
      assertEquals(zero[i], low[i], eps)
      assertEquals(last[i], high[i], eps)
    }
  }

  @Test fun weightBelowOneIsTreatedAsEqual() {
    val w = NavLayout.slotWidths(1f, 4, 400f, 0.5f)
    for (x in w) assertEquals(100f, x, eps)
  }

  @Test fun inPlaceVersionMatches() {
    val out = FloatArray(5)
    NavLayout.slotWidths(2.3f, 5, 1000f, 2.5f, out)
    val w = NavLayout.slotWidths(2.3f, 5, 1000f, 2.5f)
    for (i in 0 until 5) assertEquals(w[i], out[i], 0f)
  }

  @Test fun weightForGivesTheRequestedActiveWidth() {
    val a = NavLayout.weightFor(120f, 4, 370f)
    val w = NavLayout.slotWidths(0f, 4, 370f, a)
    assertEquals(120f, w[0], eps)
    assertEquals(1f, NavLayout.weightFor(50f, 4, 400f), 0f) // narrower than an equal share
  }

  @Test fun sharesOfNeighboursAddUpToOne() {
    var p = 0f
    while (p <= 3f) {
      var s = 0f
      for (i in 0 until 4) s += NavLayout.share(p, i, 4)
      assertEquals(1f, s, eps)
      p += 0.1f
    }
  }
}
