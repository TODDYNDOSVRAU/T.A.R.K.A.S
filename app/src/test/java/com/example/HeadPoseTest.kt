package com.example

import com.example.data.audio.spatial.HeadPose
import com.example.data.audio.spatial.HeadTrackStatus
import com.example.data.audio.spatial.HeadYawFilter
import com.example.data.audio.spatial.SpatialEngine
import com.example.data.audio.spatial.SpatialParams
import com.example.data.audio.spatial.SphericalHeadHrtf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sin

/** E6: the head's pose becomes a yaw, the yaw is filtered, the status is honest, and the scene really turns. */
class HeadPoseTest {

  private val quarter = (PI / 2).toFloat()

  // ---- pose to yaw ----

  @Test
  fun aTurnAroundTheUpAxisIsTheYaw() {
    assertEquals(90.0, HeadPose.yawDegrees(0f, 0f, quarter), 1e-4)
    assertEquals(-90.0, HeadPose.yawDegrees(0f, 0f, -quarter), 1e-4)
    assertEquals(0.0, HeadPose.yawDegrees(0f, 0f, 0f), 0.0)
  }

  @Test
  fun aTiltOfTheHeadIsNotYaw() {
    // Nodding (about x) and tilting (about y) leave the yaw alone.
    assertEquals(0.0, HeadPose.yawDegrees(0.4f, 0f, 0f), 1e-4)
    assertEquals(0.0, HeadPose.yawDegrees(0f, 0.4f, 0f), 1e-4)
  }

  @Test
  fun aPoseNeedsThreeFiniteNumbers() {
    assertNull(HeadPose.yawFromValues(floatArrayOf(0f, 0f)))
    assertNull(HeadPose.yawFromValues(floatArrayOf(0f, 0f, Float.NaN)))
    // The angular velocity that follows the rotation vector is ignored.
    assertEquals(90.0, HeadPose.yawFromValues(floatArrayOf(0f, 0f, quarter, 1f, 2f, 3f))!!, 1e-4)
  }

  @Test
  fun anglesFoldIntoHalfATurn() {
    assertEquals(-170.0, HeadPose.wrap(190.0), 1e-9)
    assertEquals(170.0, HeadPose.wrap(-190.0), 1e-9)
    assertEquals(180.0, HeadPose.wrap(-180.0), 1e-9)
    assertEquals(10.0, HeadPose.wrap(370.0), 1e-9)
  }

  // ---- filter ----

  @Test
  fun theFirstReadingIsTheFront() {
    val f = HeadYawFilter()
    assertEquals(0.0, f.update(40.0, 0.0), 1e-9)
    assertEquals(0.0, f.update(40.0, 0.02), 1e-9)
  }

  @Test
  fun theSceneGlidesToTheNewDirectionAndStaysThere() {
    val f = HeadYawFilter(timeConstantSec = 0.08)
    f.update(0.0, 0.0)
    val first = f.update(60.0, 0.02)
    assertTrue("one step covers part of the way: $first", first in 5.0..30.0)
    var last = first
    repeat(100) { last = f.update(60.0, 0.02) }
    assertEquals(60.0, last, 0.5)
  }

  @Test
  fun theSceneNeverTurnsMoreThanTheLimit() {
    val f = HeadYawFilter(timeConstantSec = 0.01)
    f.update(0.0, 0.0)
    repeat(50) { f.update(150.0, 0.05) }
    assertEquals(HeadPose.MAX_YAW, f.update(150.0, 0.05), 1e-6)
    repeat(50) { f.update(-150.0, 0.05) }
    assertEquals(-HeadPose.MAX_YAW, f.update(-150.0, 0.05), 1e-6)
  }

  @Test
  fun crossingTheBackOfTheCircleIsNotAJump() {
    val f = HeadYawFilter(timeConstantSec = 0.001)
    f.update(170.0, 0.0)
    // 170 -> -170 is 20 degrees of turn, not 340.
    assertEquals(20.0, f.update(-170.0, 1.0), 1e-3)
  }

  @Test
  fun recenteringMakesTheCurrentDirectionTheFront() {
    val f = HeadYawFilter(timeConstantSec = 0.001)
    f.update(0.0, 0.0)
    f.update(50.0, 1.0)
    f.recenter()
    assertEquals(0.0, f.update(50.0, 1.0), 1e-3)
    assertEquals(-30.0, f.update(20.0, 1.0), 1e-3)
  }

  @Test
  fun resetForgetsTheFront() {
    val f = HeadYawFilter(timeConstantSec = 0.001)
    f.update(0.0, 0.0)
    f.update(50.0, 1.0)
    f.reset()
    assertEquals(0.0, f.update(80.0, 1.0), 1e-9)
  }

  // ---- status ----

  @Test
  fun theStatusSaysWhichCaseApplies() {
    fun s(wanted: Boolean = true, sensor: Boolean = true, since: Long? = 100) =
      HeadPose.status(wanted, sensor, since)
    assertEquals(HeadTrackStatus.OFF, s(wanted = false))
    assertEquals(HeadTrackStatus.NO_SENSOR, s(sensor = false))
    assertEquals(HeadTrackStatus.WAITING, s(since = null))
    assertEquals(HeadTrackStatus.WAITING, s(since = HeadPose.STALE_MS + 1))
    assertEquals(HeadTrackStatus.TRACKING, s())
  }

  // ---- the engine ----

  private companion object {
    const val FS = 48000
    val hrtf by lazy { SphericalHeadHrtf(FS) }
  }

  private fun leftPannedNoise(frames: Int): FloatArray {
    val rng = Random(7)
    val a = (-0.8 + 1) * PI / 4
    return FloatArray(frames * 2).also {
      for (i in 0 until frames) {
        val v = 0.3 * rng.nextGaussian()
        it[2 * i] = (v * cos(a)).toFloat()
        it[2 * i + 1] = (v * sin(a)).toFloat()
      }
    }
  }

  /** Level difference, left ear minus right ear, in dB, of a source panned to the left. */
  private fun earBalanceDb(yaw: Double): Double {
    val frames = 1 shl 15
    val x = leftPannedNoise(frames)
    val eng = SpatialEngine(FS, hrtf, SpatialParams(intensity = 1.0, loudnessMatch = false))
    eng.liveYaw = yaw
    val out = FloatArray((frames + 4096) * 2)
    val scratch = FloatArray((4096 + SpatialEngine.HOP) * 2)
    var written = 0
    var i = 0
    while (i < frames) {
      val n = min(4096, frames - i)
      val w = eng.process(x, 2 * i, n, scratch)
      System.arraycopy(scratch, 0, out, written * 2, w * 2)
      written += w
      i += n
    }
    var l = 0.0; var r = 0.0
    for (k in 8192 until written) { l += out[2 * k].toDouble() * out[2 * k]; r += out[2 * k + 1].toDouble() * out[2 * k + 1] }
    return 10 * log10(l / r)
  }

  @Test
  fun turningTheHeadTowardsASourceBringsItToTheFront() {
    val still = earBalanceDb(0.0)
    val turnedToIt = earBalanceDb(60.0)
    val turnedAway = earBalanceDb(-60.0)
    assertTrue("a source on the left favours the left ear: $still dB", still > 3)
    assertTrue("head turned to the left: the gap closes ($turnedToIt < $still)", turnedToIt < still - 3)
    assertTrue("head turned away: the gap opens ($turnedAway > $still)", turnedAway > still)
  }
}
