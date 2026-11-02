package com.example

import com.example.data.TextMatch
import com.example.data.audio.PlaybackErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Searches without accents, and which playback errors are the connection's fault. */
class TextMatchAndErrorsTest {

  @Test
  fun searchesIgnoreAccentsAndCase() {
    assertTrue(TextMatch.contains("Ação Direta", "acao"))
    assertTrue(TextMatch.contains("João Gilberto", "JOAO"))
    assertTrue(TextMatch.contains("Coração", "coracao"))
    assertTrue(TextMatch.contains("Ágape", "ága"))
    assertTrue(TextMatch.contains("anything", "  "))
    assertFalse(TextMatch.contains("Samba", "rock"))
    assertEquals("crianca", TextMatch.fold("Criança"))
  }

  @Test
  fun aBusyOrFailingServerIsTheConnectionsFault() {
    assertTrue(PlaybackErrors.isNetwork(network = true, httpStatus = null, timeout = false))
    assertTrue(PlaybackErrors.isNetwork(network = false, httpStatus = null, timeout = true))
    listOf(408, 429, 500, 502, 503).forEach { assertTrue("$it", PlaybackErrors.isNetwork(false, it, false)) }
  }

  @Test
  fun theTrackFormatReadsPlainlyAndWarnsOnlyWhenTheOutputIsBelowIt() {
    assertEquals("FLAC · 96 kHz · 24 bits · estéreo", com.example.data.audio.TrackFormat("FLAC", 96000, 2, 24).describe())
    assertEquals("MP3 · 44.1 kHz · mono", com.example.data.audio.TrackFormat("MP3", 44100, 1).describe())
    assertEquals("FLAC", com.example.data.audio.TrackFormat.codecName("audio/flac"))
    assertTrue(com.example.data.audio.TrackFormat.outputBelowTrack(96000, intArrayOf(44100, 48000)))
    assertFalse(com.example.data.audio.TrackFormat.outputBelowTrack(44100, intArrayOf(44100, 48000)))
    // An output that lists no rates takes any rate.
    assertFalse(com.example.data.audio.TrackFormat.outputBelowTrack(96000, IntArray(0)))
  }

  @Test
  fun aMissingTrackOrBadFileIsTheTracksFault() {
    listOf(400, 403, 404, 410).forEach { assertFalse("$it", PlaybackErrors.isNetwork(false, it, false)) }
    assertFalse(PlaybackErrors.isNetwork(network = false, httpStatus = null, timeout = false))
  }
}
