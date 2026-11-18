package com.example

import com.example.data.audio.loudness.LoudnessNormalization
import com.example.data.audio.loudness.LoudnessSource
import com.example.data.audio.loudness.NormalizationMode
import com.example.data.audio.loudness.TaggedLoudness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessNormalizationTest {

  @Test
  fun replayGainBecomesLufs() {
    // A gain of −6.2 dB brings the track to −18 LUFS, so the track itself is at −11.8.
    val t = LoudnessNormalization.parseTags(
      listOf("replaygain_track_gain" to "-6.20 dB", "REPLAYGAIN_ALBUM_GAIN" to "+1.50 dB")
    )
    assertEquals(-11.8, t.trackLufs!!, 1e-9)
    assertEquals(-19.5, t.albumLufs!!, 1e-9)
  }

  @Test
  fun readsUnicodeMinusAndCommas() {
    assertEquals(-3.5, LoudnessNormalization.parseDb("−3,50 dB")!!, 1e-9)
    assertEquals(2.0, LoudnessNormalization.parseDb("+2 dB")!!, 1e-9)
    assertNull(LoudnessNormalization.parseDb("loud"))
  }

  @Test
  fun opusR128IsRelativeToMinus23() {
    // −1280 / 256 = −5 dB to reach −23 LUFS: the track is at −18.
    val t = LoudnessNormalization.parseTags(listOf("R128_TRACK_GAIN" to "-1280"))
    assertEquals(-18.0, t.trackLufs!!, 1e-9)
  }

  @Test
  fun replayGainWinsOverR128AndBrokenTagsAreIgnored() {
    val t = LoudnessNormalization.parseTags(
      listOf("R128_TRACK_GAIN" to "0", "REPLAYGAIN_TRACK_GAIN" to "-2 dB", "REPLAYGAIN_ALBUM_GAIN" to "-200 dB", "TITLE" to "x")
    )
    assertEquals(-16.0, t.trackLufs!!, 1e-9)
    assertNull("−200 dB would mean +182 LUFS", t.albumLufs)
  }

  @Test
  fun onlyLowers() {
    assertEquals(-4.0, LoudnessNormalization.gainFor(-10.0), 1e-9)
    assertEquals(0.0, LoudnessNormalization.gainFor(-14.0), 1e-9)
    assertEquals("a quiet track is not raised", 0.0, LoudnessNormalization.gainFor(-20.0), 1e-9)
  }

  @Test
  fun decisionOrder() {
    val tags = TaggedLoudness(trackLufs = -9.0, albumLufs = -11.0)
    val album = LoudnessNormalization.decide(NormalizationMode.ALBUM, tags, -8.0)
    assertEquals(LoudnessSource.TAG_ALBUM, album.source)
    assertEquals(-3.0, album.gainDb, 1e-9)

    val track = LoudnessNormalization.decide(NormalizationMode.TRACK, tags, -8.0)
    assertEquals(LoudnessSource.TAG_TRACK, track.source)
    assertEquals(-5.0, track.gainDb, 1e-9)

    // Album mode without an album value uses the track's.
    val noAlbum = LoudnessNormalization.decide(NormalizationMode.ALBUM, TaggedLoudness(trackLufs = -9.0), null)
    assertEquals(LoudnessSource.TAG_TRACK, noAlbum.source)

    val measured = LoudnessNormalization.decide(NormalizationMode.TRACK, TaggedLoudness(), -12.0)
    assertEquals(LoudnessSource.MEASURED, measured.source)
    assertEquals(-2.0, measured.gainDb, 1e-9)

    val unknown = LoudnessNormalization.decide(NormalizationMode.TRACK, TaggedLoudness(), null)
    assertEquals(LoudnessSource.NONE, unknown.source)
    assertEquals(1f, unknown.linearGain, 0f)

    val off = LoudnessNormalization.decide(NormalizationMode.OFF, tags, -8.0)
    assertEquals(0.0, off.gainDb, 0.0)
    assertNull(off.loudnessLufs)
  }

  @Test
  fun linearGain() {
    val d = LoudnessNormalization.decide(NormalizationMode.TRACK, TaggedLoudness(trackLufs = -8.0), null)
    assertEquals(0.5012f, d.linearGain, 1e-3f) // −6 dB
  }

  @Test
  fun measurementMustCoverMostOfTheTrack() {
    assertTrue(LoudnessNormalization.isMeasurementComplete(180.0, 200_000))
    assertFalse(LoudnessNormalization.isMeasurementComplete(170.0, 200_000))
    assertFalse("unknown length", LoudnessNormalization.isMeasurementComplete(100.0, 0))
  }
}
