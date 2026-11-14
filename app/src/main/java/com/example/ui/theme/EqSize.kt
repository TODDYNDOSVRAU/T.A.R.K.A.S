package com.example.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Sizes that only the equalizer screens use. The ones every screen shares (a hairline, a gap on the
 * 4 dp grid, an icon) stay in [Size] and [Space]; these exist so the Audio screens carry no loose
 * numbers of their own.
 */
object EqSize {
  /** Height of one level meter bar. */
  val meterBar = 6.dp
  /** The rotary knob, in its default, medium and meter sizes. */
  val knob = 64.dp
  val knobMeter = 60.dp
  val knobSmall = 56.dp
  /** Gap between a knob's ring and its disc, and the inset of a curve thumbnail's line. */
  val knobInset = 3.dp
  /** Height of a fader handle (its width is [Size.icon]). */
  val faderHandleHeight = 38.dp
  /** Distance between two fader scale dots. */
  val faderDotGap = 7.dp
  /** Lines: a fine curve, a strong ring, a bold fill. */
  val lineFine = 1.5.dp
  val lineStrong = 2.5.dp
  val lineBold = 3.dp
  /** Radius of a band node on the response graph, selected and not. */
  val nodeSelected = 6.dp
  /** The small spinner next to a row. */
  val progress = 18.dp
  /** The filter-shape glyph drawn in a band's header. */
  val filterGlyphWidth = 34.dp
  val filterGlyphHeight = 18.dp
  /** The preset list never grows taller than this. */
  val presetListMax = 560.dp
}
