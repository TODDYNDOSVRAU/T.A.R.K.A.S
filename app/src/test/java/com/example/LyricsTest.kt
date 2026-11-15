package com.example

import com.example.data.lyrics.Id3Lyrics
import com.example.data.lyrics.LrcParser
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.Lyrics
import com.example.data.lyrics.LyricsSource
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P1: the .lrc reader, the ID3v2 lyrics frames and the line that is active at a given time. */
class LyricsTest {
  // ---- .lrc ----

  @Test fun timedLinesAreReadInTimeOrder() {
    val l = LrcParser.parse("[ti:Song]\n[ar:Ana]\n[00:12.50]second\n[00:01.00]first\n")!!
    assertEquals(listOf(1000L to "first", 12500L to "second"), l.lines.map { it.timeMs!! to it.text })
    assertTrue(l.synced)
  }

  @Test fun severalTimesOnOneLineRepeatTheLine() {
    val l = LrcParser.parse("[00:10.00][01:10.00]chorus\n[00:20.00]verse")!!
    assertEquals(listOf(10_000L to "chorus", 20_000L to "verse", 70_000L to "chorus"), l.lines.map { it.timeMs!! to it.text })
  }

  @Test fun fractionsOfOneTwoAndThreeDigits() {
    val l = LrcParser.parse("[00:01.5]a\n[00:02.25]b\n[00:03.125]c\n[00:04]d")!!
    assertEquals(listOf(1500L, 2250L, 3125L, 4000L), l.lines.map { it.timeMs })
  }

  @Test fun offsetMovesEveryLineAndNeverBelowZero() {
    val l = LrcParser.parse("[00:00.20]a\n[offset:500]\n[00:05.00]b")!!
    assertEquals(listOf(0L, 4500L), l.lines.map { it.timeMs })
  }

  @Test fun wordMarksAreDroppedAndTheLineKeepsItsTime() {
    val l = LrcParser.parse("[00:01.00]<00:01.00>Hel<00:01.40>lo")!!
    assertEquals(LyricLine(1000L, "Hello"), l.lines.single())
  }

  @Test fun fileWithoutTimesIsPlainLyrics() {
    val l = LrcParser.parse("\uFEFFfirst\n\nthird\n")!!
    assertFalse(l.synced)
    assertEquals(listOf("first", "", "third"), l.lines.map { it.text })
  }

  @Test fun emptyOrOnlyTagsIsNothing() {
    assertNull(LrcParser.parse(""))
    assertNull(LrcParser.parse("[ti:x]\n[ar:y]\n"))
  }

  @Test fun windowsLineEndingsWork() {
    val l = LrcParser.parse("[00:01.00]a\r\n[00:02.00]b\r\n")!!
    assertEquals(listOf("a", "b"), l.lines.map { it.text })
  }

  // ---- active line ----

  private val synced = Lyrics(listOf(LyricLine(1000, "a"), LyricLine(5000, "b"), LyricLine(9000, "c")), LyricsSource.SIDECAR_LRC)

  @Test fun noLineIsActiveBeforeTheFirst() = assertEquals(-1, synced.activeIndex(999))

  @Test fun theLastPassedLineIsActive() {
    assertEquals(0, synced.activeIndex(1000))
    assertEquals(0, synced.activeIndex(4999))
    assertEquals(1, synced.activeIndex(5000))
    assertEquals(2, synced.activeIndex(999_999))
  }

  @Test fun plainLyricsHaveNoActiveLine() {
    assertEquals(-1, Lyrics(listOf(LyricLine(null, "x")), LyricsSource.EMBEDDED_PLAIN).activeIndex(5000))
  }

  // ---- ID3v2 ----

  private fun syncsafe(n: Int) = byteArrayOf((n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte())
  private fun int32(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

  private fun frame(version: Int, id: String, data: ByteArray): ByteArray {
    val o = ByteArrayOutputStream()
    if (version == 2) {
      o.write(id.toByteArray()); o.write(byteArrayOf((data.size shr 16).toByte(), (data.size shr 8).toByte(), data.size.toByte()))
    } else {
      o.write(id.toByteArray())
      o.write(if (version == 4) syncsafe(data.size) else int32(data.size))
      o.write(byteArrayOf(0, 0))
    }
    o.write(data)
    return o.toByteArray()
  }

  private fun tag(version: Int, vararg frames: ByteArray, padding: Int = 20): ByteArray {
    val body = ByteArrayOutputStream().also { b -> frames.forEach { b.write(it) }; b.write(ByteArray(padding)) }.toByteArray()
    val o = ByteArrayOutputStream()
    o.write("ID3".toByteArray()); o.write(byteArrayOf(version.toByte(), 0, 0)); o.write(syncsafe(body.size)); o.write(body)
    o.write(byteArrayOf(0x11, 0x22)) // audio after the tag
    return o.toByteArray()
  }

  private fun usltData(text: String, enc: Int = 3): ByteArray {
    val o = ByteArrayOutputStream()
    o.write(enc); o.write("eng".toByteArray())
    if (enc == 1) { o.write(byteArrayOf(0, 0)) } else { o.write(0) } // empty descriptor
    o.write(if (enc == 1) text.toByteArray(Charsets.UTF_16) else text.toByteArray(Charsets.UTF_8))
    return o.toByteArray()
  }

  private fun syltData(vararg lines: Pair<String, Long>, format: Int = 2): ByteArray {
    val o = ByteArrayOutputStream()
    o.write(3); o.write("eng".toByteArray()); o.write(format); o.write(1); o.write(0) // utf-8, lyrics, empty descriptor
    for ((t, ms) in lines) { o.write(t.toByteArray(Charsets.UTF_8)); o.write(0); o.write(int32(ms.toInt())) }
    return o.toByteArray()
  }

  @Test fun plainLyricsFromUsltInEveryVersion() {
    for (v in 2..4) {
      val id = if (v == 2) "ULT" else "USLT"
      val l = Id3Lyrics.read(tag(v, frame(v, id, usltData("one\ntwo\n"))))!!
      assertEquals("v$v", listOf("one", "two"), l.lines.map { it.text })
      assertEquals(LyricsSource.EMBEDDED_PLAIN, l.source)
      assertFalse(l.synced)
    }
  }

  @Test fun utf16WithBomIsRead() {
    val l = Id3Lyrics.read(tag(3, frame(3, "USLT", usltData("olá\nmundo", enc = 1))))!!
    assertEquals(listOf("olá", "mundo"), l.lines.map { it.text })
  }

  @Test fun syncedLyricsFromSyltWithTimes() {
    val l = Id3Lyrics.read(tag(3, frame(3, "SYLT", syltData("b" to 5000L, "a" to 1000L))))!!
    assertEquals(listOf(1000L to "a", 5000L to "b"), l.lines.map { it.timeMs!! to it.text })
    assertEquals(LyricsSource.EMBEDDED_SYNCED, l.source)
  }

  @Test fun syltBeatsUsltWhateverTheOrder() {
    val l = Id3Lyrics.read(tag(4, frame(4, "USLT", usltData("plain")), frame(4, "SYLT", syltData("x" to 100L))))!!
    assertTrue(l.synced)
    assertEquals("x", l.lines.single().text)
  }

  @Test fun syltInMpegFramesFallsBackToThePlainFrame() {
    val l = Id3Lyrics.read(tag(3, frame(3, "SYLT", syltData("x" to 100L, format = 1)), frame(3, "USLT", usltData("plain"))))!!
    assertEquals("plain", l.lines.single().text)
  }

  @Test fun timedLinesInsideUsltAreUsedAsSynced() {
    val l = Id3Lyrics.read(tag(3, frame(3, "USLT", usltData("[00:01.00]a\n[00:02.00]b"))))!!
    assertTrue(l.synced)
    assertEquals(listOf(1000L, 2000L), l.lines.map { it.timeMs })
  }

  @Test fun otherFramesBeforeTheLyricsAreSkipped() {
    val title = frame(3, "TIT2", byteArrayOf(3) + "Song".toByteArray())
    val l = Id3Lyrics.read(tag(3, title, frame(3, "USLT", usltData("words"))))!!
    assertEquals("words", l.lines.single().text)
  }

  @Test fun noLyricsFrameMeansNull() {
    assertNull(Id3Lyrics.read(tag(3, frame(3, "TIT2", byteArrayOf(3) + "Song".toByteArray()))))
  }

  @Test fun notAnId3FileOrTruncatedDataNeverThrows() {
    assertNull(Id3Lyrics.read(ByteArray(0)))
    assertNull(Id3Lyrics.read("RIFFxxxxxxxxxxxx".toByteArray()))
    val whole = tag(3, frame(3, "USLT", usltData("words")))
    for (cut in listOf(10, 14, 20, whole.size - 25)) Id3Lyrics.read(whole.copyOf(cut)) // must not throw
  }

  @Test fun tagSizeComesFromTheHeader() {
    val t = tag(3, frame(3, "USLT", usltData("w")), padding = 0)
    assertEquals(t.size - 2, Id3Lyrics.tagSize(t))
    assertEquals(-1, Id3Lyrics.tagSize("nope".toByteArray()))
  }
}

/** P1: which source wins. */
class LyricsChoiceTest {
  private fun synced(s: com.example.data.lyrics.LyricsSource) = Lyrics(listOf(LyricLine(1, "a")), s)
  private fun plain(s: com.example.data.lyrics.LyricsSource) = Lyrics(listOf(LyricLine(null, "a")), s)
  private val sc = LyricsSource.SIDECAR_LRC
  private val em = LyricsSource.EMBEDDED_SYNCED

  @Test fun fileBesideTheSongWinsWhenBothAreTimed() =
    assertEquals(sc, com.example.data.lyrics.LyricsChoice.best(synced(em), synced(sc))!!.source)

  @Test fun timedTagBeatsPlainFile() =
    assertEquals(em, com.example.data.lyrics.LyricsChoice.best(synced(em), plain(sc))!!.source)

  @Test fun plainFileBeatsPlainTag() =
    assertEquals(sc, com.example.data.lyrics.LyricsChoice.best(plain(LyricsSource.EMBEDDED_PLAIN), plain(sc))!!.source)

  @Test fun nothingIsNull() = assertNull(com.example.data.lyrics.LyricsChoice.best(null, null))
}
