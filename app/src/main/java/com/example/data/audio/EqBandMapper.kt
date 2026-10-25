package com.example.data.audio

import kotlin.math.ln

/**
 * Maps the app's 15 graphic-EQ bands onto the few bands the device equalizer really has
 * (usually 5). Each hardware band takes the level of the 15-band curve at its centre
 * frequency, interpolated on a logarithmic frequency axis.
 */
object EqBandMapper {

  val CENTERS_HZ = floatArrayOf(
    25f, 40f, 63f, 100f, 160f,
    250f, 400f, 630f, 1000f, 1600f,
    2500f, 4000f, 6300f, 10000f, 16000f
  )

  fun toHardware(
    levelsMb: List<Short>,
    hardwareCentersMilliHz: List<Int>,
    minMb: Int,
    maxMb: Int
  ): List<Short> {
    return hardwareCentersMilliHz.map { milliHz ->
      levelAt(levelsMb, milliHz / 1000f).toInt().coerceIn(minMb, maxMb).toShort()
    }
  }

  /** Level of the 15-band curve (in millibels) at [freqHz]. */
  fun levelAt(levelsMb: List<Short>, freqHz: Float): Float {
    if (levelsMb.isEmpty()) return 0f
    val last = CENTERS_HZ.size - 1
    fun level(i: Int) = levelsMb.getOrElse(i) { 0.toShort() }.toFloat()

    if (freqHz <= CENTERS_HZ[0]) return level(0)
    if (freqHz >= CENTERS_HZ[last]) return level(last)

    var upper = 1
    while (upper < last && CENTERS_HZ[upper] < freqHz) upper++
    val lower = upper - 1

    val span = ln(CENTERS_HZ[upper]) - ln(CENTERS_HZ[lower])
    val t = (ln(freqHz) - ln(CENTERS_HZ[lower])) / span
    return level(lower) + (level(upper) - level(lower)) * t
  }
}
