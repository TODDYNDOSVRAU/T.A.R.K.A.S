package com.example

import com.example.ui.screens.audio.formatDb
import com.example.ui.screens.audio.formatHz
import com.example.ui.screens.audio.formatNumber
import com.example.ui.screens.audio.formatQ
import com.example.ui.screens.audio.formatSampleRate
import com.example.ui.screens.audio.parseNumberInput
import com.example.ui.screens.audio.spokenHz
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** The numbers of the Audio tab follow the interface language: a comma in Portuguese, a dot in English. */
class AudioNumberFormatTest {

  private lateinit var saved: Locale

  @Before fun remember() { saved = Locale.getDefault() }
  @After fun restore() { Locale.setDefault(saved) }

  @Test fun portugueseUsesTheComma() {
    Locale.setDefault(Locale.forLanguageTag("pt-BR"))
    assertEquals("48,0 kHz", formatSampleRate(48000))
    assertEquals("0,0", formatDb(0f))
    assertEquals("-0,5", formatDb(-0.5f))
    assertEquals("+3,5", formatDb(3.5f))
    assertEquals("1,6k", formatHz(1600f))
    assertEquals("1k", formatHz(1000f))
    assertEquals("16k", formatHz(16000f))
    assertEquals("63", formatHz(63f))
    assertEquals("1,6 kHz", spokenHz(1600f))
    assertEquals("2 kHz", spokenHz(2000f))
    assertEquals("0,71", formatQ(0.707f))
  }

  @Test fun englishKeepsTheDot() {
    Locale.setDefault(Locale.US)
    assertEquals("48.0 kHz", formatSampleRate(48000))
    assertEquals("-0.5", formatDb(-0.5f))
    assertEquals("1.6k", formatHz(1600f))
    assertEquals("1k", formatHz(1000f))
    assertEquals("1.6 kHz", spokenHz(1600f))
    assertEquals("0.71", formatQ(0.707f))
  }

  @Test fun whatTheFieldShowsReadsBackInTheSameLanguage() {
    Locale.setDefault(Locale.forLanguageTag("pt-BR"))
    val shown = formatNumber(-2.5f, 1)
    assertEquals("-2,5", shown)
    assertEquals(-2.5f, parseNumberInput(shown, -15f, 15f, false)!!, 0f)
    assertEquals(2.5f, parseNumberInput("2.5", -15f, 15f, false)!!, 0f)
  }
}
