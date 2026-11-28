package com.example.windows.biblioteca

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.roundToInt

/*
 * The Glass Folder's rules without any drawing: its geometry, the poses it takes, where each sheet goes, how it enters, and
 * what Esc and a press outside do. Kept apart so every number can be checked without a window. The geometry and the
 * timings are those of the React component the owner supplied; the Compose component (GlassFolder.kt) only draws them.
 */

/** The stage is drawn in these units and scaled to the width the folder is given. */
internal const val GF_W = 340f
internal const val GF_H = 272f

/** Room above the back panel for the sheets to rise into. */
internal const val GF_HEADROOM = 54f
internal const val GF_FRONT_TOP = 74f
internal const val GF_FRONT_H = GF_H - GF_FRONT_TOP
internal const val GF_SHEET_W = 272f
internal const val GF_SHEET_H = 184f
internal const val GF_SHEET_LEFT = 34f
internal const val GF_SHEET_TOP = 96f

/** At most this many sheets show; the newest are kept, nearest the glass. */
internal const val GF_MAX_VISIBLE = 4

/** The full height of the folder with its headroom, in stage units. */
internal const val GF_FULL_H = GF_H + GF_HEADROOM

internal const val GF_BACK_PATH =
  "M0 56A22 22 0 0 1 22 34L146 34C170 34 172 0 198 0L318 0A22 22 0 0 1 340 22L340 250A22 22 0 0 1 318 272L22 272A22 22 0 0 1 0 250Z"

/** The front glass: the back path's twin, [GF_FRONT_H] tall, built with the same numbers as the original. */
internal const val GF_FRONT_PATH =
  "M0 22A22 22 0 0 1 22 0L118 0C144 0 146 32 172 32L318 32A22 22 0 0 1 340 54L340 176A22 22 0 0 1 318 198L22 198A22 22 0 0 1 0 176Z"

/** `cubic-bezier(.2, .8, .2, 1)` of the original: the settle of everything that is not a spring. */
internal val GfEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** `cubic-bezier(.34, 1.36, .5, 1)` of the original: the hinge, the sheets and the key, with a small overshoot. */
internal val GfSpring = CubicBezierEasing(0.34f, 1.36f, 0.5f, 1f)

internal const val GF_HINGE_MS = 800
internal const val GF_SHEET_MS = 750
internal const val GF_SHEET_STAGGER_MS = 40
internal const val GF_HOVER_LIFT = 18f

/** How far the folder is opened: the five values that the three resting states of the original set. */
internal data class GlassPose(val hinge: Float, val lift: Float, val spread: Float, val fan: Float, val stack: Float)

internal object GlassPoses {
  val Rest = GlassPose(hinge = 0f, lift = 42f, spread = 0f, fan = 0f, stack = 15f)
  val Hover = GlassPose(hinge = -11f, lift = -34f, spread = 8f, fan = 1.4f, stack = 10f)
  val Open = GlassPose(hinge = -24f, lift = -96f, spread = 40f, fan = 5f, stack = 30f)

  /** The pose at `0` rest, `1` hover or focus, `2` open; in between it interpolates, past either end it keeps going (the spring's overshoot). */
  fun at(p: Float): GlassPose = if (p <= 1f) mix(Rest, Hover, p) else mix(Hover, Open, p - 1f)

  /** From [a] to [b] by [t]; past `0..1` it keeps going, which is the overshoot of the spring. */
  fun between(a: GlassPose, b: GlassPose, t: Float): GlassPose = mix(a, b, t)

  /** The pose a state asks for: open wins over hover, hover and keyboard focus are the same. */
  fun target(open: Boolean, hovered: Boolean, focusVisible: Boolean): Float = when {
    open -> 2f
    hovered || focusVisible -> 1f
    else -> 0f
  }

  private fun mix(a: GlassPose, b: GlassPose, t: Float) = GlassPose(
    a.hinge + (b.hinge - a.hinge) * t,
    a.lift + (b.lift - a.lift) * t,
    a.spread + (b.spread - a.spread) * t,
    a.fan + (b.fan - a.fan) * t,
    a.stack + (b.stack - a.stack) * t,
  )
}

/** Where one sheet stands: a shift and a turn about its bottom edge, in stage units and degrees. */
internal data class SheetPlacement(val tx: Float, val ty: Float, val rotation: Float)

/**
 * [slot] is `-1..1` across the fan ([fanSlot]), [depth] counts from the glass (0 is the nearest), [jx] and [jr] are the
 * sheet's own jitter, [hover] is `0..1` while the pointer or focus is on that sheet of an open folder: it lifts and
 * straightens.
 */
internal fun sheetPlacement(pose: GlassPose, slot: Float, depth: Int, jx: Float, jr: Float, hover: Float): SheetPlacement {
  val tx = jx + slot * pose.spread
  val ty = pose.lift - depth * pose.stack - GF_HOVER_LIFT * hover
  val resting = jr + slot * pose.fan
  val lifted = slot * pose.fan * 0.6f
  return SheetPlacement(tx, ty, resting + (lifted - resting) * hover)
}

/**
 * Room above the back panel, in stage units. The original keeps 54 and lets the sheets spill past it; here the layout
 * reserves what the deepest of [count] visible sheets needs when the folder is open and a sheet is lifted, so nothing
 * leaves the box it is hit-tested in.
 */
internal fun headroomFor(count: Int): Float {
  val visible = minOf(count, GF_MAX_VISIBLE)
  val need = (visible - 1).coerceAtLeast(0) * GlassPoses.Open.stack + GF_HOVER_LIFT
  return maxOf(GF_HEADROOM, need)
}

/** A sheet's own tilt, from its name and its place in the folder: always the same for the same sheet. */
internal fun sheetJitter(name: String, id: Int): Pair<Float, Float> {
  val rng = Rng(hashString(name) + id)
  val jx = ((rng.next() - 0.5) * 10).toFloat()
  val jr = ((rng.next() - 0.5) * 3.2).toFloat()
  return round2(jx) to round2(jr)
}

private fun round2(v: Float): Float = (v * 100f).roundToInt() / 100f

/** The indexes of the sheets that show: the last [GF_MAX_VISIBLE]. */
internal fun visibleSheets(count: Int): IntRange = maxOf(0, count - GF_MAX_VISIBLE) until count

/** The folder assembles itself the first time it shows. Times in milliseconds from the start of the entrance. */
internal object GlassEntrance {
  const val BACK_DELAY = 0f
  const val BACK_MS = 1000f
  const val SHEET_DELAY = 180f
  const val SHEET_STEP = 90f
  const val SHEET_MS = 900f
  const val FRONT_DELAY = 420f
  const val FRONT_MS = 1000f
  const val KEY_DELAY = 700f
  const val KEY_ALPHA_MS = 600f
  const val KEY_SCALE_MS = 700f
  const val SHADOW_MS = 900f

  /** Long enough for the slowest layer: the key, and the sheet that is [GF_MAX_VISIBLE] deep. */
  const val TOTAL_MS = 1500

  /** Where the layers start, before they settle (stage units). */
  const val BACK_DROP = 22f
  const val SHEET_RISE = -120f
  const val FRONT_DROP = 46f
  const val KEY_SCALE_FROM = 0.6f

  /** Linear progress `0..1` of a layer that starts at [delay] and lasts [duration]. */
  fun linear(clockMs: Float, delay: Float, duration: Float): Float = ((clockMs - delay) / duration).coerceIn(0f, 1f)

  fun sheetDelay(depth: Int): Float = SHEET_DELAY + depth * SHEET_STEP
}

/** What the folder is doing: opened, and with its menu open. */
internal data class FolderInteraction(val open: Boolean = false, val menuOpen: Boolean = false)

internal enum class FocusTarget { None, Hit, Key }

internal data class InteractionStep(val state: FolderInteraction, val focus: FocusTarget)

/** Clicking the glass opens or closes the folder and always closes the menu. */
internal fun toggleOpen(s: FolderInteraction): FolderInteraction = FolderInteraction(open = !s.open, menuOpen = false)

/** Esc closes the menu first (focus goes to the key), then the folder (focus goes to the glass). With nothing open it is not ours. */
internal fun escape(s: FolderInteraction): InteractionStep = when {
  s.menuOpen -> InteractionStep(s.copy(menuOpen = false), FocusTarget.Key)
  s.open -> InteractionStep(s.copy(open = false), FocusTarget.Hit)
  else -> InteractionStep(s, FocusTarget.None)
}

/** True when Esc has something to close. */
internal fun escapeIsOurs(s: FolderInteraction): Boolean = s.open || s.menuOpen

/**
 * A press somewhere. With the menu open, a press outside the menu and its key closes the menu; an open folder closes
 * with a press outside the folder (a press inside the folder but outside the menu only closes the menu).
 */
internal fun pressAt(s: FolderInteraction, insideFolder: Boolean, insideMenuOrKey: Boolean): FolderInteraction {
  var next = s
  if (s.menuOpen && !insideMenuOrKey) next = next.copy(menuOpen = false)
  if (s.open && !s.menuOpen && !insideFolder) next = next.copy(open = false)
  return next
}

/** The chain the folder's width may take (dp): the 2x chain, so the folder never gets an in-between size. */
private val GF_WIDTH_CHAIN = floatArrayOf(384f, 192f, 96f)

/** The widest chain step whose folder, with its headroom, fits [availableW] by [availableH] (both in dp); the smallest when none does. */
internal fun glassFolderWidth(availableW: Float, availableH: Float): Float {
  for (w in GF_WIDTH_CHAIN) if (w <= availableW && w * GF_FULL_H / GF_W <= availableH) return w
  return GF_WIDTH_CHAIN.last()
}

/*
 * Where the name of a sheet can stand. The sheets overlap when the folder is open (the one in front covers the top of the
 * one behind), so a label is placed only where no sheet in front reaches, whatever the art under it, and it wears a
 * pill of the colour scheme, so its contrast never depends on the art.
 */

/** A point of a sheet (stage units from its top-left corner) in the stage: the sheet turns about the middle of its bottom edge, then shifts. */
internal fun sheetToStage(p: SheetPlacement, x: Float, y: Float): Pair<Float, Float> {
  val cx = GF_SHEET_W / 2f
  val cy = GF_SHEET_H
  val a = Math.toRadians(p.rotation.toDouble())
  val c = kotlin.math.cos(a).toFloat()
  val s = kotlin.math.sin(a).toFloat()
  val dx = x - cx
  val dy = y - cy
  return (cx + dx * c - dy * s + p.tx) to (cy + dx * s + dy * c + p.ty)
}

/** Whether a point of the stage lies on the sheet at [p], grown by [margin] on every side (the corner radius is ignored: that only errs toward covered). */
internal fun sheetCovers(p: SheetPlacement, sx: Float, sy: Float, margin: Float = 0f): Boolean {
  val cx = GF_SHEET_W / 2f
  val cy = GF_SHEET_H
  val a = Math.toRadians(-p.rotation.toDouble())
  val c = kotlin.math.cos(a).toFloat()
  val s = kotlin.math.sin(a).toFloat()
  val dx = sx - p.tx - cx
  val dy = sy - p.ty - cy
  val x = cx + dx * c - dy * s
  val y = cy + dx * s + dy * c
  return x >= -margin && x <= GF_SHEET_W + margin && y >= -margin && y <= GF_SHEET_H + margin
}

/**
 * How wide, in stage units, a label can be when it starts at ([left], [top]) of the sheet at [self], is [height] tall, and
 * has to stay clear of every sheet in [infront] by [margin], never reaching within [right] of the sheet's right edge. The
 * rectangle grows to the right a unit at a time; the width before the first unit that touches a sheet in front is the room.
 */
internal fun labelRoom(
  self: SheetPlacement, infront: List<SheetPlacement>, left: Float, top: Float, height: Float,
  right: Float = left, margin: Float = 2f,
): Float {
  val limit = (GF_SHEET_W - left - right).coerceAtLeast(0f)
  if (infront.isEmpty()) return limit
  fun clear(w: Float): Boolean {
    for (i in 0..4) {
      val y = top + height * i / 4f
      val (sx, sy) = sheetToStage(self, left + w, y)
      if (infront.any { sheetCovers(it, sx, sy, margin) }) return false
    }
    return true
  }
  var w = 0f
  while (w + 1f <= limit && clear(w + 1f)) w += 1f
  return if (clear(limit) && w + 1f > limit) limit else w
}

/**
 * The room of the label of visible sheet [v] of [count], in the open fan. The sheets in front of it are taken as lifted by
 * a pointer (the worst case: lifted they stand higher and cover more), so the label never has to move or be cut while
 * the pointer crosses the fan. [jitters] are the sheets' own tilts, in visible order.
 */
internal fun openLabelRoom(v: Int, count: Int, jitters: List<Pair<Float, Float>>, left: Float, top: Float, height: Float): Float {
  val pose = GlassPoses.Open
  fun at(i: Int, hover: Float) = sheetPlacement(pose, fanSlot(i, count).toFloat(), count - 1 - i, jitters[i].first, jitters[i].second, hover)
  val infront = (v + 1 until count).map { at(it, 1f) }
  return labelRoom(at(v, 0f), infront, left, top, height)
}

/** What the label of a sheet shows: its short name when the full one does not fit, and the meta only when both fit on the line. */
internal data class SheetLabelChoice(val useShort: Boolean, val showMeta: Boolean)

/** Widths in pixels. [shortW] is null when the sheet has no short name. [inner] is the room inside the pill. */
internal fun chooseSheetLabel(nameW: Int, shortW: Int?, metaW: Int, gap: Int, inner: Int): SheetLabelChoice {
  val useShort = shortW != null && nameW > inner && shortW <= inner
  val shown = if (useShort) shortW!! else nameW
  return SheetLabelChoice(useShort, showMeta = metaW > 0 && shown + gap + metaW <= inner)
}
