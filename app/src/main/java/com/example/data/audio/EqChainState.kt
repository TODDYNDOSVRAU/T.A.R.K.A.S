package com.example.data.audio

import com.example.data.model.EqSettings

/**
 * What the equalizer is told to do, given the screen's settings and the two things that change it
 * without changing the settings: the A/B comparison ([bypass]) and a root equalizer on the output mix
 * ([coveredByOutputMix]). Pure, so the rules are checked without a device.
 */
object EqChainState {

  /**
   * - bypass (unless [ignoreBypass]): the same settings with the equalizer off;
   * - covered by the output mix: the curve goes flat so it is not applied twice. Balance, width and the
   *   limiter stay: the system equalizer has none of them;
   * - otherwise: the settings as they are.
   */
  fun processed(s: EqSettings, bypass: Boolean, coveredByOutputMix: Boolean, ignoreBypass: Boolean = false): EqSettings {
    if (bypass && !ignoreBypass) return s.copy(enabled = false)
    if (!coveredByOutputMix || !s.enabled) return s
    return s.copy(
      bands = s.bands.map { it.copy(gainDb = 0f) },
      preampDb = 0f,
      bassDb = 0f,
      trebleDb = 0f,
      device = null,
      autoPreamp = false
    )
  }

  /** Whether the virtualizer should be on: it follows the equalizer switch and the comparison. */
  fun virtualizerOn(s: EqSettings, bypass: Boolean): Boolean = s.enabled && !bypass && s.virtualizerStrength > 0
}
