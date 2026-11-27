package com.example.windows.equalizador

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icon of the output the sound goes to, read from the Windows mixer name (what the user picked in the output
 * list). Bluetooth first (a headset can be both), then headphones, then screens; everything else, and the
 * Windows default whose kind is not known, is a speaker. Windows names come in the user's language, so both
 * English and Portuguese words are matched.
 */
internal fun outputIcon(mixer: String?): ImageVector {
  val n = mixer?.lowercase().orEmpty()
  fun has(vararg words: String) = words.any { it in n }
  return when {
    has("bluetooth", "airpods", "buds") -> Icons.Rounded.Bluetooth
    has("headphone", "headset", "earphone", "earbud", "fone", "auricular") -> Icons.Rounded.Headphones
    has("hdmi", "displayport", "display", "monitor", "tv", "nvidia high definition", "amd high definition") -> Icons.Rounded.Tv
    else -> Icons.Rounded.Speaker
  }
}
