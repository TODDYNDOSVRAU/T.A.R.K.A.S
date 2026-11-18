package com.example.ui.theme

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The numbers behind the cover carousel of the full player: the current cover in the middle, the
 * previous tracks' covers on the left and the next tracks' on the right, two on each side. Every
 * property of a card is a pure function of its offset from the centre, in steps (0 the centre, -1 the
 * first left slot, +2 the second right slot), so dragging moves everything continuously and can be
 * tested without a screen.
 */
object CoverCarousel {
  /** A first side cover's size relative to the centre one. */
  const val SideScale = 0.70f
  /**
   * Centre of a first side cover from the centre of the middle one, in widths of the middle cover.
   * Its inner edge is then 0.85 - 0.35 = 0.5 from the centre, exactly the middle cover's edge: the two
   * do not overlap, and read as separate objects.
   */
  const val SideShift = 0.85f
  /** Turn of a side cover about its vertical axis, its outer edge away from the eye. */
  const val SideAngle = 12f
  /** Opacity of a first side cover, and the black laid over it. */
  const val SideAlpha = 0.72f
  const val SideScrim = 0.3f

  /** The second cover on each side: further out, smaller, darker, turned a little more. */
  const val FarScale = 0.56f
  const val FarShift = 1.45f
  const val FarAngle = 18f
  const val FarAlpha = 0.42f
  const val FarScrim = 0.5f

  /** How far past their slots the side covers may swing while the jelly spring overshoots (1 is the slot). */
  const val MaxSpread = 1.3f

  /** Past the second slots a cover keeps going, shrinks and is gone at this offset. */
  private const val GoneAt = 2.6f

  /**
   * The middle cover's side for a carousel [available] pixels wide (the whole screen) and [maxHeight]
   * high: the size the single cover had, the width inside the page's [margin] on each side. The side
   * covers show in those margins (and more while the middle cover is paused and steps back), the rest
   * of them running off the screen.
   */
  fun centreSide(available: Float, maxHeight: Float, margin: Float): Float =
    minOf(available - 2f * margin, maxHeight).coerceAtLeast(0f)

  /**
   * @param scale size relative to the centre cover at rest
   * @param shift horizontal position of the card's centre, in widths of the centre cover
   * @param rotationY degrees about the vertical axis (right card positive, left negative): the outer edge recedes
   * @param alpha opacity of the card
   * @param scrim opacity of the black laid over the image
   */
  data class Slot(val scale: Float, val shift: Float, val rotationY: Float, val alpha: Float, val scrim: Float)

  /**
   * The card at [offset] steps from the centre. Symmetric; with [reduced] motion nothing turns.
   *
   * [spread] (0..1) is how far the side covers have come out from behind the middle one: at 0 every
   * card sits exactly where the middle one is (same place, size and turn) and only the middle one is
   * seen; at 1 they are in their slots. The player animates it from 0 to 1 once it has opened, and
   * back before it closes. With [reduced] motion the covers do not move out: they only fade in.
   */
  fun layoutAt(offset: Float, reduced: Boolean = false, spread: Float = 1f): Slot {
    val rest = restAt(offset, reduced)
    val k = spread.coerceIn(0f, MaxSpread)
    if (k == 1f) return rest
    // Past 1 the covers have overshot their slots (the jelly spring): size, place and turn go on a little
    // beyond, opacity and shade stay where they rest.
    if (k > 1f) {
      if (reduced) return rest
      return rest.copy(
        scale = 1f + (rest.scale - 1f) * k,
        shift = rest.shift * k,
        rotationY = rest.rotationY * k
      )
    }
    val near = abs(offset).coerceAtMost(1f)
    // Fully out of sight at spread 0, fully seen at 1, and the middle card (near 0) is never faded.
    val alpha = rest.alpha * (1f - near * (1f - k))
    if (reduced) return rest.copy(alpha = alpha)
    return Slot(
      scale = 1f + (rest.scale - 1f) * k,
      shift = rest.shift * k,
      rotationY = rest.rotationY * k,
      alpha = alpha,
      scrim = rest.scrim * k
    )
  }

  /** The card at [offset] once the carousel is spread out. */
  private fun restAt(offset: Float, reduced: Boolean): Slot {
    val s = sign(offset)
    val a = abs(offset).coerceAtMost(GoneAt)
    val near = a.coerceAtMost(1f)
    val mid = (a - 1f).coerceIn(0f, 1f)
    val far = (a - 2f).coerceAtLeast(0f)
    val scale = 1f - (1f - SideScale) * near - (SideScale - FarScale) * mid - 0.12f * far
    val shift = s * (SideShift * near + (FarShift - SideShift) * mid + 0.4f * far)
    val angle = if (reduced) 0f else s * (SideAngle * near + (FarAngle - SideAngle) * mid)
    // Opacity falls with the square of the distance near the middle, so a cover nearly in the middle
    // is nearly opaque and the one it passes over does not show through it.
    val alpha = (1f - (1f - SideAlpha) * near * near - (SideAlpha - FarAlpha) * mid) *
      (1f - far / (GoneAt - 2f)).coerceIn(0f, 1f)
    val scrim = SideScrim * near + (FarScrim - SideScrim) * mid
    return Slot(scale, shift, angle, alpha, scrim)
  }

  /**
   * How far one step is for the finger, in pixels, for a centre cover [coverPx] wide: the slope of the
   * middle cover's path where it starts, so under the finger it moves 1:1.
   */
  fun stepPx(coverPx: Float): Float = SideShift * coverPx

  /** Where a gesture released at [position] with [velocity] (units per second) would come to rest. */
  fun project(position: Float, velocity: Float, deceleration: Float = 0.998f): Float =
    position + velocity / 1000f * deceleration / (1f - deceleration)

  /**
   * The step to settle on after a release at [offset] steps from the centre moving at [velocity] steps
   * per second: -1 (previous), 0 (stay) or +1 (next), chosen from where the momentum would carry it,
   * never more than one track, and never towards a side with nothing there.
   */
  fun targetStep(offset: Float, velocity: Float, canPrevious: Boolean, canNext: Boolean): Int {
    val step = project(offset, velocity).roundToInt().coerceIn(-1, 1)
    return when {
      step > 0 && !canNext -> 0
      step < 0 && !canPrevious -> 0
      else -> step
    }
  }

  /** Releases faster than this (steps per second) are a flick, which may settle with a slight bounce. */
  const val FlickVelocity = 1.5f

  /** The furthest a drag can pull past the last cover, in steps: the resistance approaches it. */
  const val RubberLimit = 0.35f

  /**
   * Where the carousel sits for a drag of [raw] steps, between [low] and [high] (the slots that hold a
   * cover): 1:1 inside, with growing resistance beyond, so it never stops dead or jumps.
   */
  fun band(raw: Float, low: Float, high: Float): Float = when {
    raw > high -> high + rubber(raw - high)
    raw < low -> low - rubber(low - raw)
    else -> raw
  }

  /**
   * Cards are placed by a running index (centre, centre - 1, centre + 1, ...) and the camera moves
   * along it, so when the track changes the card that was sliding into the middle simply becomes the
   * centre: nothing is re-laid out and nothing jumps. This picks the index of the new current track
   * among the cards on screen ([cards]: index to track id):
   * the one a swipe was settling on ([pending]) when it holds that track; else the old centre when it
   * still does (the same song again); else the nearest card holding it; else wherever the camera is
   * (a track picked elsewhere: it simply takes the middle). Ids are compared per card, so the same
   * song twice in a row is told apart by position.
   */
  fun rebaseCenter(cards: Map<Int, String>, oldCenter: Int, pending: Int?, newId: String?, camera: Float): Int {
    if (newId != null) {
      if (pending != null && cards[pending] == newId) return pending
      if (cards[oldCenter] == newId) return oldCenter
      cards.filterValues { it == newId }.keys.minByOrNull { abs(it - camera) }?.let { return it }
    }
    return camera.roundToInt()
  }

  /** Apple's rubber band: (1 - 1 / (x·c / d + 1))·d, slope 0.55 at the edge, never past [RubberLimit]. */
  fun rubber(x: Float, limit: Float = RubberLimit): Float {
    if (x <= 0f) return 0f
    return (1f - 1f / (x * 0.55f / limit + 1f)) * limit
  }
}
