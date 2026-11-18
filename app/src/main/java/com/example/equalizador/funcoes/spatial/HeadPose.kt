package com.example.data.audio.spatial

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** What head tracking says to the listener. */
enum class HeadTrackStatus {
  /** Turned off by the user. */
  OFF,
  /** No head tracker is connected (headphones with one, and Android 13 or later). */
  NO_SENSOR,
  /** On, with a sensor, but no pose arrives: head tracking may be off in the system sound settings. */
  WAITING,
  /** Poses are arriving and turn the scene. */
  TRACKING
}

/** Pure rules of head tracking: from a pose to a yaw, and from what Android reports to a status. */
object HeadPose {
  /** The scene turns at most this far either way; beyond it the listener is not looking at the music. */
  const val MAX_YAW = 90.0

  /** A pose older than this means the sensor went quiet. */
  const val STALE_MS = 1500L

  /**
   * The yaw of the head, in degrees, from a rotation vector (axis times angle, in radians) of the
   * head in the soundstage frame (x right, y forward, z up). Positive = the head turned to the left
   * (counter-clockwise seen from above), which is the engine's own sign: a source to the left
   * (positive azimuth) comes towards the front when the head turns to it.
   */
  fun yawDegrees(rx: Float, ry: Float, rz: Float): Double {
    val angle = sqrt(rx.toDouble() * rx + ry.toDouble() * ry + rz.toDouble() * rz)
    if (angle < 1e-9) return 0.0
    val s = sin(angle / 2) / angle
    val w = cos(angle / 2)
    val x = rx * s
    val y = ry * s
    val z = rz * s
    return Math.toDegrees(atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z)))
  }

  /** The yaw from the values of a TYPE_HEAD_TRACKER event: the rotation vector comes first. */
  fun yawFromValues(values: FloatArray): Double? =
    if (values.size < 3 || values.take(3).any { it.isNaN() }) null else yawDegrees(values[0], values[1], values[2])

  /** Degrees folded into (-180, 180]. */
  fun wrap(deg: Double): Double {
    var d = deg % 360.0
    if (d > 180.0) d -= 360.0
    if (d <= -180.0) d += 360.0
    return d
  }

  /**
   * @param wanted the user switched it on
   * @param sensor a head tracker is connected
   * @param msSincePose time since the last pose, or null when none arrived yet
   */
  fun status(wanted: Boolean, sensor: Boolean, msSincePose: Long?): HeadTrackStatus = when {
    !wanted -> HeadTrackStatus.OFF
    !sensor -> HeadTrackStatus.NO_SENSOR
    msSincePose == null || msSincePose > STALE_MS -> HeadTrackStatus.WAITING
    else -> HeadTrackStatus.TRACKING
  }
}

/**
 * Turns raw yaw readings into the yaw the scene follows: relative to where the listener was facing
 * when it started (or when they asked to recentre), limited to [HeadPose.MAX_YAW], and smoothed so
 * the scene glides instead of stepping. A recentre also glides.
 */
class HeadYawFilter(private val timeConstantSec: Double = 0.08) {
  private var reference: Double? = null
  private var lastRaw = 0.0
  private var smoothed = 0.0

  /** The scene's yaw for a new reading [dtSec] after the previous one. */
  fun update(rawYaw: Double, dtSec: Double): Double {
    lastRaw = rawYaw
    val ref = reference ?: rawYaw.also { reference = it }
    val relative = HeadPose.wrap(rawYaw - ref).coerceIn(-HeadPose.MAX_YAW, HeadPose.MAX_YAW)
    val alpha = 1 - exp(-dtSec.coerceAtLeast(0.0) / timeConstantSec)
    smoothed += alpha * (relative - smoothed)
    return smoothed
  }

  /** The direction the listener faces now becomes the front. */
  fun recenter() {
    reference = lastRaw
  }

  /** Forget everything: the next reading is the front. */
  fun reset() {
    reference = null
    smoothed = 0.0
  }
}
