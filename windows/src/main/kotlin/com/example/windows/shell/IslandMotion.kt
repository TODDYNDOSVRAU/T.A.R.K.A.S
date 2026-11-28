/*
 * The island's motion: a Kotlin port of oyenza-studio's spring, island and content transition
 * (https://github.com/lucacadalora/oyenza-studio: poni-core/src/spring.rs, poni/src/island.rs,
 * poni/src/pill/motion.rs and the content cross-fade of poni/src/app.rs), ported line for line on 2026-11-27 at the
 * owner's request. Only the window-level parts (the shoulders, the hit margin and the window's canvas rects) are left
 * out: here the island lives inside the app's window, so its room is the shell's layout.
 *
 * MIT License
 *
 * Copyright (c) 2026 Jatevo Labs
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.example.windows.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Damped spring used for every island animation (size, corner radius, content opacity). Springs keep momentum when
 * the target changes mid-flight, which is what makes the island feel alive rather than tweened. (spring.rs)
 */
class IslandSpring(var value: Float, var stiffness: Float, var damping: Float) {
  var velocity = 0f
  var target = value

  /** Change stiffness/damping in flight, keeping value and velocity. */
  fun retune(stiffness: Float, damping: Float) { this.stiffness = stiffness; this.damping = damping }

  @JvmName("aimAt") // the property's setter already takes setTarget(F) on the JVM
  fun setTarget(target: Float) { this.target = target }

  /** Jump to [value] with no motion. */
  fun snap(value: Float) { this.value = value; target = value; velocity = 0f }

  /** Advance by [dt] seconds (semi-implicit Euler with fixed sub-steps). */
  fun step(dt: Float) {
    if (isSettled()) { value = target; velocity = 0f; return }
    var remaining = dt.coerceIn(0f, 0.1f)
    while (remaining > 0f) {
      val h = min(remaining, H)
      val force = -stiffness * (value - target) - damping * velocity
      velocity += force * h
      value += velocity * h
      remaining -= h
    }
    if (isSettled()) { value = target; velocity = 0f }
  }

  /** Settled when within 0.05 units of the target and nearly still. Opacity-style springs use [isSettledEps]. */
  fun isSettled(): Boolean = isSettledEps(0.05f)

  fun isSettledEps(eps: Float): Boolean = kotlin.math.abs(value - target) < eps && kotlin.math.abs(velocity) < eps * 4f

  companion object {
    private const val H = 1f / 240f

    /** A slightly under-damped spring (small overshoot), good for the island outline. */
    fun bouncy(value: Float) = IslandSpring(value, 420f, 30f)

    /** A critically damped spring (no overshoot), good for opacity. */
    fun smooth(value: Float): IslandSpring { val k = 380f; return IslandSpring(value, k, 2f * sqrt(k)) }
  }
}

/** The island's shape in dp: width, height, corner radius, and (floating) how far it sits below its gap. */
data class IslandGeom(val w: Float, val h: Float, val r: Float, val dy: Float = 0f) {
  /** The same shape, [dy] dp below the gap while it floats. */
  fun below(dy: Float) = copy(dy = dy)
}

/**
 * How the floating island moves (motion.rs) and how its content crosses over (app.rs). They only shape motion that
 * already runs: nothing here adds a frame at rest.
 */
object IslandMotion {
  /*
   * Spring tunings (stiffness, damping) while the island floats, picked per axis by the way that axis moves.
   * Growing, the width leads and the height trails: the pill first stretches into a wider capsule, then drops into
   * the card, and both overshoot about 3 % and settle, like a drop finding its shape. Shrinking, the height leads and
   * the width gathers last: the grow played backwards, a little under critical damping, and a shrinking axis is never
   * let below its target, so the pill never squashes, even when a morph turns around mid-flight.
   */
  val GROW_W = 500f to 32.5f      // zeta 0.73
  val GROW_H = 250f to 23f        // zeta 0.73
  val SHRINK_W = 400f to 36f      // zeta 0.90
  val SHRINK_H = 560f to 43f      // zeta 0.91
  /** Corners, critically damped (they never wobble): round out at once growing, stay round while the card gathers. */
  val RADIUS_GROW = 1200f to 69.3f
  val RADIUS_SHRINK = 150f to 24.5f
  /** The gap and the hover sink. Critically damped, so it never pokes above its place or bobs (tau about 58 ms). */
  val TOP = 300f to 34.6f

  /** Incoming content drops this many dp as it fades in: a floating island's content blooms about [pivot]. */
  const val CONTENT_DROP = 2f
  /** The resting pill under the pointer swells this much on every side (an even outset keeps a capsule a capsule)... */
  const val HOVER_OUTSET = 3f
  /** ...and sinks this much as it swells, so it reads as hanging a little lower rather than climbing. */
  const val HOVER_SINK = 1f

  /** The outgoing content fades out over this long (seconds), with [outgoingLeft]. */
  const val OUTGOING_FADE = 0.14
  /** Incoming content scales up from this as it fades in. */
  const val CONTENT_SCALE_FROM = 0.94f
  /** The outgoing picture never shrinks below this. */
  const val GHOST_MIN_SCALE = 0.6f
  /** The frame step is clamped to this (seconds), so a stalled frame never throws the springs. */
  const val MAX_DT = 0.05f

  /** The resting island [g] under the pointer. */
  fun hoverLift(g: IslandGeom): IslandGeom {
    val d = HOVER_OUTSET
    return IslandGeom(g.w + 2f * d, g.h + 2f * d, g.r + d).below(g.dy - d + HOVER_SINK)
  }

  /**
   * Where content scales from during a morph to [target]: the middle of the morph's small end, so content grows out
   * of the pill and shrinks back into it.
   */
  fun pivot(body: Rect, target: IslandGeom): Offset = Offset(body.center.x, body.top + min(body.height, target.h) / 2f)

  /**
   * How far the outgoing content, laid out at its old size [was], has shrunk with the outline [now]: as much as the
   * axis that has shrunk more, so the picture shrinks inside the outline. Never below [min], never grown.
   */
  fun ghostScale(now: IslandGeom, was: IslandGeom, min: Float): Float =
    min(now.w / max(was.w, 1f), now.h / max(was.h, 1f)).coerceIn(min, 1f)

  /** What is left of the outgoing content [age] seconds after it started leaving: a smoothstep from 1 to 0. */
  fun outgoingLeft(age: Double): Float {
    val t = (age / OUTGOING_FADE).coerceIn(0.0, 1.0).toFloat()
    return 1f - t * t * (3f - 2f * t)
  }
}

/** Island geometry and its spring animation (island.rs). All sizes in dp, times in seconds. */
class IslandBody(g: IslandGeom) {
  internal val w = IslandSpring(g.w, GROW_W.first, GROW_W.second)
  internal val h = IslandSpring(g.h, GROW_H.first, GROW_H.second)
  internal val r = IslandSpring(g.r, RADIUS.first, RADIUS.second)
  private val content = IslandSpring(1f, CONTENT.first, CONTENT.second)
  /** When set, content fades in once `now` passes this time. */
  internal var fadeInAt: Double? = null
    private set
  var target: IslandGeom = g
    private set
  /** The body's distance below its top: 0 while it hangs, the gap while it floats. */
  private val top = IslandSpring(0f, IslandMotion.TOP.first, IslandMotion.TOP.second)
  /** The gap while the island floats, null while it hangs. */
  private var gap: Float? = null
  /** Floating: the size a shrinking axis lands on and stops at. */
  internal var floorW: Float? = null
    private set
  internal var floorH: Float? = null
    private set
  /** Whether a frame has moved the island yet. Until then a new gap is taken at once, without a slide. */
  private var stepped = false

  /**
   * Float [gap] dp below the top (null: hang from it). A change slides the island (before the first frame it simply
   * starts there), and a morph in flight carries on with the new tunings.
   */
  fun setFloating(gap: Float?) {
    if (gap == this.gap) return
    val retune = (gap != null) != (this.gap != null)
    this.gap = gap
    if (retune) tune()
    aim()
    if (!stepped) top.snap(topTarget())
  }

  /** Drawn floating: while it floats, and while it rises back. */
  fun floating(): Boolean = gap != null || top.value > 0f

  /** The body's distance below its top (dp), where it is now. */
  fun top(): Float = top.value

  /**
   * Animate towards [g]. When [newContent] is true the content cross-fades: the new content starts from transparent and
   * fades in shortly after the outline starts moving (the caller fades the old content out meanwhile). Springs keep
   * their value and velocity; only their tuning changes with the direction of the morph.
   */
  fun setTarget(g: IslandGeom, now: Double, newContent: Boolean) {
    target = g
    val growing = tune()
    aim()
    if (newContent) {
      content.snap(0f)
      fadeInAt = now + if (growing) FADE_DELAY_GROW else FADE_DELAY_SHRINK
    }
  }

  /** Tune the springs for the morph to the target from where the outline is now. Returns whether it grows. */
  private fun tune(): Boolean {
    val g = target
    val growing = g.w * g.h > w.value * h.value
    if (gap != null) {
      // Floating: each axis by its own direction, so an axis that shrinks while the other grows still lands on its size.
      val tw = if (g.w >= w.value) IslandMotion.GROW_W else IslandMotion.SHRINK_W
      val th = if (g.h >= h.value) IslandMotion.GROW_H else IslandMotion.SHRINK_H
      val tr = if (g.r >= r.value) IslandMotion.RADIUS_GROW else IslandMotion.RADIUS_SHRINK
      w.retune(tw.first, tw.second)
      h.retune(th.first, th.second)
      r.retune(tr.first, tr.second)
      floorW = floor(w, g.w, floorW)
      floorH = floor(h, g.h, floorH)
    } else {
      val (tw, th) = if (growing) GROW_W to GROW_H else SHRINK_W to SHRINK_H
      w.retune(tw.first, tw.second)
      h.retune(th.first, th.second)
      r.retune(RADIUS.first, RADIUS.second)
      floorW = null
      floorH = null
    }
    return growing
  }

  /** Point every spring at the target. */
  private fun aim() {
    val g = target
    w.setTarget(g.w)
    h.setTarget(g.h)
    r.setTarget(radiusFor(g))
    top.setTarget(topTarget())
  }

  /** The corner radius the springs aim at for [g]: floating, never rounder than a capsule. */
  private fun radiusFor(g: IslandGeom): Float = if (gap != null) min(g.r, g.h / 2f) else g.r

  private fun topTarget(): Float = gap?.let { max(it + target.dy, 0f) } ?: 0f

  /** The new content fades in at once from [alpha] (the same content coming back mid-fade), not after the delay. */
  fun resumeContent(alpha: Float) {
    content.snap(alpha.coerceIn(0f, 1f))
    content.setTarget(1f)
    fadeInAt = null
  }

  /** Jump straight to [g] without animating. */
  fun snap(g: IslandGeom) {
    target = g
    w.snap(g.w)
    h.snap(g.h)
    r.snap(radiusFor(g))
    top.snap(topTarget())
    floorW = null
    floorH = null
    content.snap(1f)
    fadeInAt = null
  }

  fun step(dt: Float, now: Double) {
    stepped = true
    w.step(dt)
    h.step(dt)
    r.step(dt)
    if (gap != null) {
      land(w, floorW)
      land(h, floorH)
      // A capsule's corners are its ends: once the height has landed, so have they (drawn at half the height anyway).
      if (h.isSettled() && r.target >= h.target / 2f && r.value >= h.value / 2f) r.snap(r.target)
    }
    top.step(dt)
    fadeInAt?.let { if (now >= it) { content.setTarget(1f); fadeInAt = null } }
    content.step(dt)
  }

  fun settled(): Boolean =
    fadeInAt == null && w.isSettled() && h.isSettled() && r.isSettled() && top.isSettled() && content.isSettledEps(0.002f)

  fun current(): IslandGeom {
    val cw = max(w.value, 20f)
    val ch = max(h.value, 8f)
    // Floating, a corner is never drawn rounder than half the height.
    val cr = if (gap != null) min(r.value, ch / 2f) else r.value
    return IslandGeom(cw, ch, max(cr, 0f), top.value - (gap ?: 0f))
  }

  fun contentAlpha(): Float = content.value.coerceIn(0f, 1f)

  /** The island body, centred on [cx] (dp). */
  fun bodyRectAt(cx: Float): Rect {
    val g = current()
    return Rect(cx - g.w / 2f, top.value, cx + g.w / 2f, top.value + g.h)
  }

  internal companion object {
    /*
     * Classic tunings (hanging from an edge): growing goes wide first, then drops; shrinking is nearly critically
     * damped so the small pill doesn't squash; corners never wobble. The floating island uses IslandMotion's.
     */
    val GROW_W = 420f to 30f        // zeta 0.73
    val GROW_H = 300f to 26f        // zeta 0.75
    val SHRINK_W = 330f to 33f      // zeta 0.91
    val SHRINK_H = 420f to 38f      // zeta 0.93
    val RADIUS = 420f to 41f        // critical
    /** Content opacity: critically damped, tau about 38 ms. */
    val CONTENT = 700f to 52.9f
    /** Delay before new content starts fading in (seconds). */
    const val FADE_DELAY_GROW = 0.03
    const val FADE_DELAY_SHRINK = 0.05

    /**
     * The size an axis heading for [to] must not go below: [to] itself when it shrinks there. Growing, a spring still
     * falling from the last morph keeps that morph's floor, so it turns around above it instead of squashing.
     */
    fun floor(s: IslandSpring, to: Float, was: Float?): Float? = when {
      to < s.value -> to
      s.velocity < 0f -> was
      else -> null
    }

    /** A shrinking axis stops on its floor instead of dipping past it. */
    fun land(s: IslandSpring, floor: Float?) {
      if (floor != null && s.value < floor) { s.value = floor; s.velocity = 0f }
    }
  }
}

/**
 * The content cross-fade of app.rs: which content is coming in, and the outgoing one fading out at its old size. [K]
 * is the content's key (here: whether the pages are shown). Call [change] when the content or the size changes, then
 * [body]'s step every frame; [ghostAlpha] and [ghostScale] say how to draw the outgoing content.
 */
class IslandContent<K>(initial: K) {
  var key: K = initial
    private set
  var outgoing: K? = null
    private set
  private var outgoingSince = 0.0
  private var outgoingGeom = IslandGeom(0f, 0f, 0f)
  private var outgoingFrom = 0f

  /** Morph [body] to [g] showing [newKey] at [now]; a new key cross-fades, the same key only resizes. */
  fun change(body: IslandBody, newKey: K, g: IslandGeom, now: Double) {
    val newContent = newKey != key
    if (!newContent) {
      if (g != body.target) body.setTarget(g, now, false)
      return
    }
    // The same content coming back mid-fade picks up from what is left of it, not from transparent.
    val back = if (outgoing == newKey) outgoingFrom * IslandMotion.outgoingLeft(now - outgoingSince) else null
    outgoing = key
    outgoingSince = now
    outgoingGeom = body.target
    outgoingFrom = body.contentAlpha()
    key = newKey
    body.setTarget(g, now, true)
    if (back != null) body.resumeContent(back)
  }

  /** The outgoing content's opacity at [now]; 0 once it is gone (it is then dropped). */
  fun ghostAlpha(now: Double): Float {
    if (outgoing == null) return 0f
    val age = now - outgoingSince
    if (age > IslandMotion.OUTGOING_FADE) { outgoing = null; return 0f }
    return outgoingFrom * IslandMotion.outgoingLeft(age)
  }

  /** The outgoing content's scale with the outline [now]. */
  fun ghostScale(now: IslandGeom): Float = IslandMotion.ghostScale(now, outgoingGeom, IslandMotion.GHOST_MIN_SCALE)

  /** Drop the outgoing content at once (reduced motion, a snap). */
  fun clear() { outgoing = null }

  /** Scale of the incoming content at [alpha]. */
  fun incomingScale(alpha: Float): Float = IslandMotion.CONTENT_SCALE_FROM + (1f - IslandMotion.CONTENT_SCALE_FROM) * alpha

  /** Vertical offset (dp) of the incoming content at [alpha]: it starts a little high and settles. */
  fun incomingDrop(alpha: Float): Float = IslandMotion.CONTENT_DROP * (alpha - 1f)
}
