package com.example.windows.player

import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

class PlayerHooksTest {
  private fun t(n: Int) = Track(id = "t$n", title = "T$n", artist = "A", album = "B", durationMs = 200_000, genre = "", era = "", isLocalFile = true, filePath = "C:/x/t$n.wav")

  private fun hooks(out: FakeOut = FakeOut()): Pair<PlayerHooks, FakeOut> {
    val c = PlayerController(out, MemoryPrefs(), exists = { true })
    return PlayerHooks(c, CoroutineScope(Dispatchers.Unconfined), MutableStateFlow(null)) to out
  }

  private fun wav(dir: File, name: String, seconds: Int, loudSecond: Int): File {
    val rate = 8000f
    val n = (rate * seconds).toInt()
    val pcm = ByteArray(n * 2)
    for (i in 0 until n) {
      val loud = i / rate.toInt() == loudSecond
      val v = ((if (loud) 20000 else 800) * Math.sin(i * 0.3)).toInt()
      pcm[2 * i] = v.toByte(); pcm[2 * i + 1] = (v shr 8).toByte()
    }
    val fmt = AudioFormat(rate, 16, 1, true, false)
    val f = File(dir, name)
    AudioSystem.write(AudioInputStream(ByteArrayInputStream(pcm), fmt, n.toLong()), AudioFileFormat.Type.WAVE, f)
    return f
  }

  @Test fun transportStateFollowsEngineAndQueue() {
    val (h, out) = hooks()
    h.controller.play(t(1), listOf(t(1), t(2)))
    h.controller.addToQueue(t(3))
    val s = h.state.value
    assertEquals("t1", s.track?.id); assertTrue(s.playing); assertEquals(1, s.queueSize)
    h.toggle(); assertFalse(h.state.value.playing)
    h.seekFraction(0.5f); assertEquals(100_000L, out.seeks.last())
    h.setVolume(0.4f); assertEquals(0.4f, h.state.value.volume, 0.001f)
    h.volumeBy(0.1f); assertEquals(0.5f, h.state.value.volume, 0.001f)
  }

  @Test fun deleteFromQueueCanBeUndone() {
    val (h, _) = hooks()
    h.controller.play(t(1), listOf(t(1)))
    listOf(t(2), t(3), t(4)).forEach(h.controller::addToQueue)
    h.removeFromQueue(1)
    assertEquals(listOf("t2", "t4"), h.controller.ui.value.queue.map { it.id })
    assertEquals("t3", h.undo.value?.removed?.single()?.id)
    assertTrue(h.undoQueue())
    assertEquals(listOf("t2", "t3", "t4"), h.controller.ui.value.queue.map { it.id })
    assertNull(h.undo.value)
    assertFalse(h.undoQueue())
  }

  @Test fun clearQueueCanBeUndoneAndEmptyClearIsIgnored() {
    val (h, _) = hooks()
    h.clearQueue(); assertNull(h.undo.value)
    h.controller.addToQueue(t(2)); h.controller.addToQueue(t(3))
    h.clearQueue()
    assertTrue(h.controller.ui.value.queue.isEmpty())
    h.undoQueue()
    assertEquals(2, h.controller.ui.value.queue.size)
  }

  @Test fun queueToFrontPlaysItNext() {
    val (h, _) = hooks()
    listOf(t(2), t(3), t(4)).forEach(h.controller::addToQueue)
    h.controller.queueToFront(2)
    assertEquals(listOf("t4", "t2", "t3"), h.controller.ui.value.queue.map { it.id })
  }

  @Test fun paneToggles() {
    val (h, _) = hooks()
    h.togglePane(PlayerPane.QUEUE); assertEquals(PlayerPane.QUEUE, h.pane.value)
    h.togglePane(PlayerPane.LYRICS); assertEquals(PlayerPane.LYRICS, h.pane.value)
    h.togglePane(PlayerPane.LYRICS); assertNull(h.pane.value)
  }

  @Test fun dragTargetRoundsToRowsAndClamps() {
    assertEquals(3, DragMath.target(1, 100f, 48f, 10))
    assertEquals(0, DragMath.target(1, -500f, 48f, 10))
    assertEquals(9, DragMath.target(8, 500f, 48f, 10))
    assertEquals(2, DragMath.target(2, 10f, 48f, 10))
    assertEquals(4, DragMath.target(4, 100f, 0f, 10))
  }

  @Test fun waveformShowsWhereTheLoudPartIs() {
    val dir = createTempDir("wave")
    try {
      val f = wav(dir, "a.wav", seconds = 4, loudSecond = 3)
      val w = WaveformReader.compute(f)
      assertNotNull(w)
      val n = w!!.size
      val first = w.take(n / 2).sumOf { it.toInt() and 0xFF }
      val last = w.drop(n * 3 / 4).sumOf { it.toInt() and 0xFF }
      assertTrue("loud last quarter ($last) must beat quiet first half ($first)", last > first)
      assertEquals(255, w.maxOf { it.toInt() and 0xFF })
    } finally { dir.deleteRecursively() }
  }

  @Test fun waveformIsCachedAndRereadWhenTheFileChanges() {
    val dir = createTempDir("wave")
    try {
      val f = wav(dir, "a.wav", 2, 1)
      val r = WaveformReader(WaveformCache(File(dir, "cache")))
      val first = r.load("id1", f)!!
      assertArrayEquals(first, WaveformCache(File(dir, "cache")).read("id1", FileStamp.of(f)))
      assertNull(WaveformCache(File(dir, "cache")).read("id1", FileStamp(1, 1)))
      assertNull(r.load("id2", File(dir, "missing.wav")))
    } finally { dir.deleteRecursively() }
  }

  @Test fun lyricsComeFromTheSidecarFile() {
    val dir = createTempDir("lyrics")
    try {
      val song = File(dir, "s.mp3").also { it.writeBytes(ByteArray(64)) }
      assertNull(LyricsLoader.load(song))
      File(dir, "s.lrc").writeText("[00:01.00]one\n[00:05.50]two\n")
      val l = LyricsLoader.load(song)!!
      assertTrue(l.synced)
      assertEquals(listOf("one", "two"), l.lines.map { it.text })
      assertEquals(1, l.activeIndex(6000))
      assertNull(LyricsLoader.load(File(dir, "none.mp3")))
    } finally { dir.deleteRecursively() }
  }
}
