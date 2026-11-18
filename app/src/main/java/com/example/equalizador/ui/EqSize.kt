package com.example.ui.theme

/**
 * Sizes that only the equalizer screens use, all taken from the T.A.L. grid ([Grid]): a line is a
 * fraction of the 6 dp step (1.5 and 3), so the Audio screens carry no loose numbers of their own.
 */
object EqSize {
  /** Height of one level meter bar. */
  val meterBar = Grid.u6
  /** Gap between a knob's ring and its disc, and the inset of a curve thumbnail's line: half of 6. */
  val knobInset = Grid.u6 / 2
  /** Lines: a fine curve or track (a quarter of 6) and a bold ring or fill (half of 6). */
  val lineFine = Grid.u6 / 4
  val lineBold = Grid.u6 / 2
  /** Radius of a selected band node on the response graph. */
  val nodeSelected = Grid.u6
  /** The preset list never grows taller than this: 1.5x of 384, so a tall phone shows more rows. */
  val presetListMax = Grid.u384 + Grid.u192
}
