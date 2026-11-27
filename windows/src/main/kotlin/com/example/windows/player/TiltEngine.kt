package com.example.windows.player

import kotlin.math.ceil
import kotlin.math.max

/**
 * The tilt of one surface that leans toward the pointer (D4): two springs, X and Y, with the numbers of the queue
 * stack ([StackTuning.TiltK], [StackTuning.TiltC]) so a cover and a stack card feel alike. It knows nothing of Compose,
 * so the limits and the return to neutral are tested with plain numbers. Angles are degrees, as `graphicsLayer` takes them.
 *
 * [maxRx] and [maxRy] are the limits at the card's edge: [StackTuning.MaxRx] and [StackTuning.MaxRy] by default, and a
 * contained variant (a small cover in a grid) passes a smaller pair. Under [reduced] the pose is always 0.
 */
class TiltEngine(
  val maxRx: Float = StackTuning.MaxRx,
  val maxRy: Float = StackTuning.MaxRy,
  private val k: Float = StackTuning.TiltK,
  private val c: Float = StackTuning.TiltC,
) {
  val rx = Spring()
  val ry = Spring()

  /** Reduced motion: no tilt at all, the pose is 0 and stays there. */
  var reduced = false
    set(v) { field = v; if (v) reset() }

  /** Aims at a point of the card: [px], [py] in the card's own pixels (or dp, as long as [w] and [h] use the same unit). */
  fun aim(px: Float, py: Float, w: Float, h: Float) {
    if (reduced || w <= 0f || h <= 0f) return
    val nx = (px / w * 2f - 1f).coerceIn(-1f, 1f)
    val ny = (py / h * 2f - 1f).coerceIn(-1f, 1f)
    rx.t = -ny * maxRx
    ry.t = nx * maxRy
  }

  /** The keyboard's way to lean: [dx] and [dy] in -1..1 (arrow keys), the same springs and limits. */
  fun lean(dx: Float, dy: Float) {
    if (reduced) return
    rx.t = (-dy * maxRx).coerceIn(-maxRx, maxRx)
    ry.t = (dx * maxRy).coerceIn(-maxRy, maxRy)
  }

  /** The pointer left: the springs go back to 0. */
  fun leave() { rx.t = 0f; ry.t = 0f }

  /** Back to neutral without travelling. */
  fun reset() { rx.jump(0f); ry.jump(0f) }

  /** Frames the engine has advanced, for tests that prove nothing runs once the springs are at rest. */
  var frames = 0
    private set

  val atRest: Boolean get() = rx.settled(Eps) && ry.settled(Eps)

  /** Advances by [dtIn] seconds in 240 Hz sub-steps; true when both springs are at rest (the caller stops its frame loop). */
  fun frame(dtIn: Float): Boolean {
    frames++
    if (reduced) { reset(); return true }
    val dt = dtIn.coerceIn(0f, 0.05f)
    val n = max(1, ceil(dt * 240f).toInt())
    val h = dt / n
    for (i in 0 until n) { rx.step(h, k, c); ry.step(h, k, c) }
    if (atRest && rx.t == 0f && ry.t == 0f) { rx.jump(0f); ry.jump(0f) }
    return atRest
  }

  companion object {
    const val Eps = 0.01f
  }
}
