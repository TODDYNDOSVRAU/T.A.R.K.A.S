package com.example.windows.equalizador

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract of the EQ graph's fill (2026-11-27 glitch): a triangle strip that is exact, never covers a pixel
 * twice (the fill's alpha is 0.06; a bow tie at a 0 dB crossing would blend twice) and whose vertices stay inside
 * the limits the plot clip expects.
 */
class EqGraphGeometryTest {
  private val w = 600f
  private val h = 192f

  private fun curve(f: (Int) -> Float) = FloatArray(CURVE_POINTS) { f(it) }

  private fun strip(db: FloatArray): Pair<FloatArray, Int> {
    val out = FloatArray(FILL_STRIP_FLOATS)
    return out to fillStrip(db, w, h, out)
  }

  /** Area of the triangle (a, b, c) of the vertex buffer. */
  private fun area(v: FloatArray, a: Int, b: Int, c: Int): Float {
    val ax = v[2 * a]; val ay = v[2 * a + 1]
    val bx = v[2 * b]; val by = v[2 * b + 1]
    val cx = v[2 * c]; val cy = v[2 * c + 1]
    return abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) / 2f
  }

  private fun stripArea(v: FloatArray, count: Int): Float {
    var sum = 0f
    for (t in 0 until count - 2) sum += area(v, t, t + 1, t + 2)
    return sum
  }

  /** Exact area between the curve (piecewise linear in db -> y) and the 0 dB line, split at the crossings. */
  private fun exactArea(db: FloatArray): Float {
    var sum = 0f
    val dx = w / (db.size - 1f)
    for (i in 0 until db.size - 1) {
      val a = db[i].coerceIn(-18f, 18f)
      val b = db[i + 1].coerceIn(-18f, 18f)
      val k = (h / 2f - 14f) / GRAPH_DB_RANGE
      sum += if (a * b >= 0f) (abs(a) + abs(b)) / 2f * k * dx
      else (a * a + b * b) / (2f * (abs(a) + abs(b))) * k * dx
    }
    return sum
  }

  @Test fun aCurveThatStaysOnOneSideHasOnePairPerPoint() {
    val (v, used) = strip(curve { -12f + it * 0.01f })
    assertEquals(2 * CURVE_POINTS, used)
    // every second vertex sits on the 0 dB line, at the x of its curve point
    for (i in 0 until CURVE_POINTS) {
      assertEquals(dbToY(0f, h), v[2 * (2 * i + 1) + 1], 1e-4f)
      assertEquals(v[2 * (2 * i)], v[2 * (2 * i + 1)], 1e-4f)
    }
    assertEquals(0f, v[0], 1e-4f)
    assertEquals(w, v[2 * (2 * CURVE_POINTS - 2)], 1e-3f)
  }

  @Test fun theBufferIsAlwaysFullAndPaddedWithDegenerateTriangles() {
    val (v, used) = strip(curve { 3f })
    assertEquals(FILL_STRIP_FLOATS, v.size)
    val lastX = v[2 * (used - 1)]
    val lastY = v[2 * (used - 1) + 1]
    for (i in used until FILL_STRIP_VERTICES) {
      assertEquals(lastX, v[2 * i], 0f)
      assertEquals(lastY, v[2 * i + 1], 0f)
    }
    // the padding adds no area
    assertEquals(stripArea(v, used), stripArea(v, FILL_STRIP_VERTICES), 1e-2f)
  }

  @Test fun noVertexLeavesTheClampedBandOrTheWidth() {
    // a pre-amp sweep far past the +-15 dB range, as the owner's video: +40 and -40 dB everywhere
    listOf(-40f, -18f, 0f, 18f, 40f).forEach { level ->
      val (v, used) = strip(curve { level })
      val top = dbToY(GRAPH_DB_RANGE + 3f, h)
      val bottom = dbToY(-GRAPH_DB_RANGE - 3f, h)
      for (i in 0 until used) {
        assertTrue("x ${v[2 * i]} outside [0, w]", v[2 * i] in 0f..w)
        assertTrue("y ${v[2 * i + 1]} outside [$top, $bottom]", v[2 * i + 1] in top..bottom)
      }
    }
  }

  @Test fun theClampedCurveStillLiesOutsideThePlotSoTheClipIsWhatKeepsItIn() {
    // dbToY(+-18) is outside [0, h]: this is the cause of the glitch, and the reason the plot clip must exist.
    assertTrue(dbToY(GRAPH_DB_RANGE + 3f, h) < 0f)
    assertTrue(dbToY(-GRAPH_DB_RANGE - 3f, h) > h)
  }

  @Test fun aZeroCrossingIsCoveredOnceNotTwice() {
    // a tilt through 0 dB (the shape of Graves -12 / Agudos +12): bow ties would make strip area > exact area
    val db = curve { (it - 63.4f) * 0.4f }
    val (v, used) = strip(db)
    assertEquals(2 * CURVE_POINTS + 2, used)
    assertEquals(exactArea(db), stripArea(v, used), exactArea(db) * 1e-3f)
  }

  @Test fun everyCrossingAddsOnePairAndTheAreaStaysExact() {
    val db = curve { if (it % 2 == 0) 6f else -6f }
    val (v, used) = strip(db)
    assertEquals(2 * CURVE_POINTS + 2 * (CURVE_POINTS - 1), used)
    assertEquals(FILL_STRIP_VERTICES, used)
    assertEquals(exactArea(db), stripArea(v, used), exactArea(db) * 1e-3f)
  }

  @Test fun theFillOfAFlatCurveIsAZeroAreaLine() {
    val (v, used) = strip(curve { 0f })
    assertEquals(0f, stripArea(v, used), 1e-3f)
  }
}
