package com.example.windows.equalizador

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import org.junit.Assert.assertSame
import org.junit.Test

class OutputIconTest {
  @Test fun `the default and plain speakers are a speaker`() {
    assertSame(Icons.Rounded.Speaker, outputIcon(null))
    assertSame(Icons.Rounded.Speaker, outputIcon("Speakers (Realtek(R) Audio)"))
    assertSame(Icons.Rounded.Speaker, outputIcon("Alto-falantes (USB DAC)"))
  }
  @Test fun `headphones in English and Portuguese`() {
    assertSame(Icons.Rounded.Headphones, outputIcon("Headphones (Realtek(R) Audio)"))
    assertSame(Icons.Rounded.Headphones, outputIcon("Fones de ouvido (USB Audio)"))
    assertSame(Icons.Rounded.Headphones, outputIcon("Headset Earphone (Jabra)"))
  }
  @Test fun `bluetooth wins over headset`() {
    assertSame(Icons.Rounded.Bluetooth, outputIcon("Headset (Bluetooth Hands-Free)"))
    assertSame(Icons.Rounded.Bluetooth, outputIcon("AirPods Pro"))
  }
  @Test fun `screens`() {
    assertSame(Icons.Rounded.Tv, outputIcon("LG TV (NVIDIA High Definition Audio)"))
    assertSame(Icons.Rounded.Tv, outputIcon("DELL U2720Q (HDMI)"))
  }
}
