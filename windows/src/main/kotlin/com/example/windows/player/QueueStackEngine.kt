package com.example.windows.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import com.example.data.model.Track
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * The Mascot Card Stack engine of the Home v2 prompt, ported to Kotlin and fed by the real queue.
 * Units are dp, relative to the centre of the front card; the engine knows nothing of Compose layout, so it can be
 * tested with plain numbers. It never reorders anything: the player's queue is the model, [sync] only animates the
 * change between two orders.
 */
class Spring(var x: Float = 0f, var v: Float = 0f, var t: Float = 0f) {
  fun step(h: Float, k: Float, c: Float) { v += (k * (t - x) - c * v) * h; x += v * h }
  fun settled(eps: Float) = abs(t - x) < eps && abs(v) < eps * 12f
  fun snap() { x = t; v = 0f }
  fun jump(to: Float) { x = to; t = to; v = 0f }
}

/** The numbers of the original engine. */
object StackTuning {
  const val PoseK = 175f
  const val PoseC = 19f
  const val TiltK = 150f
  const val TiltC = 12.5f
  const val MaxRx = 8f
  const val MaxRy = 11f
  /** A drag past this share of the card width throws it. */
  const val ThrowDist = 0.2f
  /** dp per second. */
  const val ThrowSpeed = 420f
  const val DragStartDp = 6f
  /** Back cards drawn at most; the third and further ones stay hidden. */
  const val MaxBack = 2
  const val ArmSeconds = 0.38f
  const val SwingX = -0.86f
  const val SwingY = -0.045f
  const val SwingS = 0.9f
  const val SwingR = -7f
  /** The fan opens this much wider while the pointer is over the stack. */
  const val OpenFactor = 1.3f
  const val FadeRate = 10f
}

class Fan(val x: Float, val s: Float) {
  companion object {
    val Wide = Fan(0.29f, 0.11f)
    val Narrow = Fan(0.125f, 0.07f)
  }
}

/** Where a card sits: translation from the front centre in dp, scale, rotation in degrees, opacity. */
class Pose { var x = 0f; var y = 0f; var s = 1f; var r = 0f; var o = 1f }

/** One card. [key] is the track id plus which copy of it this is (the queue may hold a song twice). */
class StackCard(val key: String) {
  val x = Spring(); val y = Spring(); val r = Spring(); val s = Spring(1f, 0f, 1f)
  val rx = Spring(); val ry = Spring()
  var o = 0f
  var oT = 1f
  var slot = 0
  var leaving = false
  /** The front card leaving after Next: it swings away to the left before fading. */
  var flying = false
  /** Whether the front card's text enters one row after another when it arrives. */
  var entrance = false
  /** True once the front card has settled in place; the entrance of its text starts then. */
  var lead by mutableStateOf(false)
}

enum class Release { Next, Prev, None }

class QueueStackEngine {
  var w = 192f; private set
  var h = 288f; private set
  var fan: Fan = Fan.Narrow; private set
  var reduced = false
    set(v) { if (field != v) { field = v; kick() } }
  var maxBack = StackTuning.MaxBack; private set

  /** Cards to draw (live ones and those still fading out). Replaced on every structural change. */
  var cards by mutableStateOf<List<StackCard>>(emptyList(), neverEqualPolicy()); private set
  /** Counts frames; the draw blocks read it so a frame never recomposes. */
  var tick by mutableLongStateOf(0L); private set
  /** Bumped by every input; [waker] (the frame loop) starts again when it is called. Plain fields, so composition may call [layout]. */
  var wake = 0; private set
  var waker: (() -> Unit)? = null

  var canNext = false
  var canPrev = false

  private val live = ArrayList<StackCard>()
  private val leavers = ArrayList<StackCard>()
  private val pose = Pose()
  private var hover = false
  private var aimX = 0f
  private var aimY = 0f
  private var hoverBack = 0
  private var pressed = false
  private var armLeft = StackTuning.ArmSeconds
  var dragging = false; private set
  private var dragX = 0f
  private var dragY = 0f
  private var dragVx = 0f

  val armed: Boolean get() = armLeft <= 0f
  val liveCount: Int get() = live.size
  fun liveKeys(): List<String> = live.map { it.key }

  /** [back] is how many back cards the stage has room for; the others stay hidden. */
  fun layout(width: Float, height: Float, fan: Fan, back: Int = StackTuning.MaxBack) {
    if (width == w && height == h && fan === this.fan && back == maxBack) return
    w = width; h = height; this.fan = fan; maxBack = back
    kick()
  }

  private fun kick() { wake++; waker?.invoke() }

  // Poses --------------------------------------------------------------------------------------------

  /** The resting pose of [slot]: the front card, or a back card fanned to the right and shrunk. */
  fun slotPose(slot: Int, open: Boolean, hoveredBackSlot: Int, press: Boolean, out: Pose) {
    if (slot == 0) {
      out.x = if (open) -0.03f * w else 0f; out.y = 0f; out.s = if (press) 0.985f else 1f; out.r = 0f; out.o = 1f
      return
    }
    val k = min(slot, 2)
    val s = 1f - fan.s * k - (if (slot > 2) 0.04f else 0f)
    var x = w * fan.x * (if (k == 1) 1f else 1.9f)
    var y = h * (1f - s) * 0.42f
    var r = 0f
    if (open) { x *= StackTuning.OpenFactor; r = 1.6f * k }
    if (open && hoverBack == slot) { y -= 0.035f * h; x += 0.025f * w }
    out.x = x; out.y = y; out.s = s; out.r = r; out.o = if (slot > maxBack) 0f else 1f
  }

  private fun swing(out: Pose) {
    out.x = StackTuning.SwingX * w; out.y = StackTuning.SwingY * h; out.s = StackTuning.SwingS; out.r = StackTuning.SwingR; out.o = 1f
  }

  private val open: Boolean get() = hover && !dragging && !reduced

  // Order changes ------------------------------------------------------------------------------------

  /** Follows the model: [keys] is the front card then the back cards, in the player's order. */
  fun sync(keys: List<String>) {
    val old = live.map { it.key }
    if (keys == old) return
    val first = live.isEmpty() && leavers.isEmpty()
    val advanced = old.size > 1 && keys.isNotEmpty() && keys[0] == old[1]
    val wentBack = keys.isNotEmpty() && old.isNotEmpty() && keys.getOrNull(1) == old[0] && keys[0] != old[0]
    val byKey = HashMap<String, StackCard>(live.size * 2)
    for (c in live) byKey[c.key] = c
    val next = ArrayList<StackCard>(keys.size)
    for ((i, k) in keys.withIndex()) {
      var c = byKey[k]
      if (c == null) {
        c = StackCard(k)
        c.slot = i
        slotPose(i, false, 0, false, pose)
        when {
          first -> { c.x.jump(0f); c.y.jump(0f); c.s.jump(0.96f); c.r.jump(0f); c.entrance = i == 0 }
          i == 0 && wentBack && !reduced -> { swing(pose); c.x.jump(pose.x); c.y.jump(pose.y); c.s.jump(pose.s); c.r.jump(pose.r); c.entrance = true }
          else -> { c.x.jump(pose.x); c.y.jump(pose.y); c.s.jump(pose.s); c.r.jump(pose.r); c.entrance = i == 0 }
        }
        c.o = 0f
      }
      c.slot = i
      c.oT = 1f
      next += c
    }
    val front = live.firstOrNull()
    for (c in live) if (c.key !in keys) {
      c.leaving = true; c.oT = 0f; c.lead = false
      if (c === front && advanced && !reduced) c.flying = true
      leavers += c
    }
    while (leavers.size > 4) leavers.removeAt(0)
    live.clear(); live.addAll(next)
    publish()
    if (first) armLeft = StackTuning.ArmSeconds
    kick()
  }

  private fun publish() {
    val all = ArrayList<StackCard>(leavers.size + live.size)
    all.addAll(leavers); all.addAll(live)
    cards = all
  }

  // Pointer ------------------------------------------------------------------------------------------

  fun hoverMove(px: Float, py: Float) {
    hover = true
    val f = live.firstOrNull()
    val fx = f?.x?.x ?: 0f
    val fy = f?.y?.x ?: 0f
    aimX = ((px - fx) / (w / 2f)).coerceIn(-1f, 1f)
    aimY = ((py - fy) / (h / 2f)).coerceIn(-1f, 1f)
    hoverBack = hitBack(px, py)
    kick()
  }

  fun hoverExit() { hover = false; hoverBack = 0; aimX = 0f; aimY = 0f; kick() }

  fun hitFront(px: Float, py: Float): Boolean {
    val c = live.firstOrNull() ?: return false
    return abs(px - c.x.x) <= w * c.s.x / 2f && abs(py - c.y.x) <= h * c.s.x / 2f
  }

  /** The slot (1 or 2) of the back card under the point, nearest first; 0 when there is none or the front card covers it. */
  fun hitBack(px: Float, py: Float): Int {
    if (hitFront(px, py)) return 0
    for (i in 1 until live.size) {
      val c = live[i]
      if (i > maxBack) break
      if (abs(px - c.x.x) <= w * c.s.x / 2f && abs(py - c.y.x) <= h * c.s.x / 2f) return i
    }
    return 0
  }

  fun press(on: Boolean) { pressed = on; kick() }

  /** True when the front card can be dragged now: armed, in place and not mid-swap. */
  fun canGrab(): Boolean {
    val f = live.firstOrNull() ?: return false
    return armed && !dragging && !f.flying && f.o > 0.5f
  }

  private var x0 = 0f
  private var y0 = 0f

  /** The grab: the card is held where it is now. */
  fun beginDrag() {
    val f = live.firstOrNull()
    x0 = f?.x?.x ?: 0f; y0 = f?.y?.x ?: 0f
    dragging = true
    dragX = x0; dragY = y0
    kick()
  }

  /** [dx] and [dy] are the pointer's travel since the grab, in dp; [vx] its speed in dp/s. */
  fun drag(dx: Float, dy: Float, vx: Float = 0f) {
    if (!dragging) beginDrag()
    val x = x0 + dx
    dragX = if (x > 0f) { val m = 0.5f * w; m * (1f - exp(-x / m)) } else x
    dragY = y0 + dy * 0.14f
    dragVx = vx
    kick()
  }

  private var throwLeft = 0f

  /** The pointer went up: what the throw does. Anything that does not change the track springs back. */
  fun release(vx: Float): Release {
    val x = dragX
    dragging = false; pressed = false
    val far = StackTuning.ThrowDist * w
    val v = StackTuning.ThrowSpeed
    val r = when {
      (x < -far || (vx < -v && x < -6f)) && canNext -> Release.Next
      (x > far || (vx > v && x > 6f)) && canPrev -> Release.Prev
      else -> Release.None
    }
    dragX = 0f; dragY = 0f; dragVx = 0f
    if (r == Release.Next && !reduced) {
      // The card keeps flying left while the player changes track; if the track does not change it comes back (see frame).
      live.firstOrNull()?.flying = true
      throwLeft = 0.45f
    }
    kick()
    return r
  }

  fun cancelDrag() { dragging = false; pressed = false; dragX = 0f; dragY = 0f; dragVx = 0f; kick() }

  // Frame --------------------------------------------------------------------------------------------

  /** Advances by [dtIn] seconds; true when everything is at rest (the caller stops its frame loop). */
  fun frame(dtIn: Float): Boolean {
    val dt = dtIn.coerceIn(0f, 0.05f)
    if (armLeft > 0f) armLeft = max(0f, armLeft - dt)
    val n = max(1, ceil(dt * 240f).toInt())
    val hStep = dt / n
    var rest = armLeft <= 0f && !dragging
    for (idx in 0 until leavers.size + live.size) {
      val c = if (idx < leavers.size) leavers[idx] else live[idx - leavers.size]
      targets(c)
      if (reduced) { c.x.snap(); c.y.snap(); c.r.snap(); c.s.snap(); c.rx.snap(); c.ry.snap() }
      else for (i in 0 until n) {
        c.x.step(hStep, StackTuning.PoseK, StackTuning.PoseC); c.y.step(hStep, StackTuning.PoseK, StackTuning.PoseC)
        c.r.step(hStep, StackTuning.PoseK, StackTuning.PoseC); c.s.step(hStep, StackTuning.PoseK, StackTuning.PoseC)
        c.rx.step(hStep, StackTuning.TiltK, StackTuning.TiltC); c.ry.step(hStep, StackTuning.TiltK, StackTuning.TiltC)
      }
      c.o += (c.oT - c.o) * min(1f, dt * StackTuning.FadeRate)
      if (abs(c.oT - c.o) < 0.01f) c.o = c.oT else rest = false
      if (!c.x.settled(0.15f) || !c.y.settled(0.15f) || !c.r.settled(0.01f) || !c.s.settled(0.0005f) || !c.rx.settled(0.01f) || !c.ry.settled(0.01f)) rest = false
      if (!c.lead && c.slot == 0 && !c.leaving && armed && abs(c.x.x) < 0.12f * w && abs(c.s.x - 1f) < 0.04f) c.lead = true
      else if (c.slot > 0 && c.lead) c.lead = false
    }
    if (throwLeft > 0f) {
      rest = false
      throwLeft -= dt
      val f = live.firstOrNull()
      if (throwLeft <= 0f && f != null) f.flying = false
    }
    var removed = false
    var i = leavers.size - 1
    while (i >= 0) { val c = leavers[i]; if (c.o <= 0.01f && c.oT == 0f) { leavers.removeAt(i); removed = true }; i-- }
    if (removed) publish()
    tick++
    return rest
  }

  private fun targets(c: StackCard) {
    val front = !c.leaving && c.slot == 0
    when {
      c.flying -> { swing(pose); c.x.t = pose.x; c.y.t = pose.y; c.s.t = pose.s; c.r.t = pose.r }
      c.leaving -> {}
      front && dragging -> { c.x.t = dragX; c.y.t = dragY; c.r.t = dragX / w * 9f; c.s.t = 1.025f }
      else -> {
        slotPose(c.slot, open, hoverBack, pressed && front, pose)
        c.x.t = pose.x; c.y.t = pose.y; c.s.t = pose.s; c.r.t = pose.r
        c.oT = pose.o
      }
    }
    if (front && !reduced) {
      if (dragging) { c.rx.t = 0f; c.ry.t = (dragVx * 0.0055f).coerceIn(-16f, 16f) }
      else { c.rx.t = -aimY * StackTuning.MaxRx; c.ry.t = aimX * StackTuning.MaxRx }
    } else { c.rx.t = 0f; c.ry.t = 0f }
  }
}

/** Pointer speed over the last 90 ms of a drag; zero when the pointer rested for more than 80 ms before the release. */
class ThrowVelocity {
  private val ts = LongArray(8)
  private val xs = FloatArray(8)
  private var n = 0

  fun reset() { n = 0 }

  fun add(timeMs: Long, x: Float) {
    if (n == ts.size) { System.arraycopy(ts, 1, ts, 0, n - 1); System.arraycopy(xs, 1, xs, 0, n - 1); n-- }
    ts[n] = timeMs; xs[n] = x; n++
  }

  /** dp per second, clamped to the original's ±3800. */
  fun release(timeMs: Long): Float {
    if (n < 2) return 0f
    val last = ts[n - 1]
    if (timeMs - last > 80) return 0f
    var i = n - 1
    while (i > 0 && last - ts[i - 1] <= 90) i--
    val dt = (last - ts[i]) / 1000f
    if (dt <= 0f) return 0f
    return ((xs[n - 1] - xs[i]) / dt).coerceIn(-3800f, 3800f)
  }
}

/** What the stack shows and how big, apart from the UI so it can be tested. */
object StackLayout {
  /** Window width from which the fan opens wide. */
  const val WideFrom = 600f
  const val LeftShift = 0.03f

  fun fanFor(maxWidth: Float): Fan = if (maxWidth >= WideFrom) Fan.Wide else Fan.Narrow

  /** Card height for a width on the grid chain: 96 is the cover alone, the others add the 96 dp of text. */
  fun cardH(w: Int): Int = if (w < 192) w else w + 96

  /** Width the stage needs for the front card and [back] cards with the fan fully open, plus the front card's shift to the left. */
  fun extent(w: Float, back: Int, fan: Fan): Float {
    var right = w / 2f
    for (k in 1..back) {
      val s = 1f - fan.s * k
      val x = w * fan.x * (if (k == 1) 1f else 1.9f) * StackTuning.OpenFactor + 0.025f * w
      right = max(right, x + w * s / 2f)
    }
    return w / 2f + LeftShift * w + right
  }

  class Fit(val w: Int, val h: Int, val back: Int, val wide: Boolean, val totalW: Float)

  /**
   * The largest card of the grid chain (384, 192, 96) with as many back cards as fit [maxW] by [maxH], nothing cut.
   * Cards of 192 or more come first, then the bare 96 cover; fewer back cards before a smaller card.
   */
  fun fit(maxW: Float, maxH: Float): Fit {
    val wide = maxW >= WideFrom
    val fan = fanFor(maxW)
    for (widths in listOf(listOf(384, 192), listOf(96))) {
      for (back in StackTuning.MaxBack downTo 0) for (w in widths) {
        val ext = extent(w.toFloat(), back, fan)
        if (ext <= maxW && cardH(w) <= maxH) return Fit(w, cardH(w), back, wide, ext)
      }
    }
    return Fit(96, 96, 0, wide, extent(96f, 0, fan))
  }
}

/** The cards of the real queue: what Next would play, in order, as the player computes it. */
object StackModel {
  /** Keys that stay the same for a song across the queue: id plus which copy of it this is. */
  fun keys(current: Track?, after: List<Track>): List<String> {
    if (current == null) return emptyList()
    val seen = HashMap<String, Int>()
    val out = ArrayList<String>(after.size + 1)
    for (t in listOf(current) + after) { val n = seen.merge(t.id, 1, Int::plus)!! - 1; out += "${t.id}#$n" }
    return out
  }
}
