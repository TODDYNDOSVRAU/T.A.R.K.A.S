package com.example

import com.example.data.waveform.FileStamp
import com.example.data.waveform.PeakAccumulator
import com.example.data.waveform.StoredWaveform
import com.example.data.waveform.WaveformStore
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The 100-value loudness shape of a track and where it is saved. */
class WaveformTest {
  @get:Rule val temp = TemporaryFolder()

  private fun mono(frames: Int, amplitude: (Int) -> Int) = ShortArray(frames) { amplitude(it).toShort() }

  private fun u(b: Byte) = b.toInt() and 0xFF

  @Test
  fun silenceGivesZeros() {
    val acc = PeakAccumulator(estimatedFrames = 10_000)
    acc.add(ShortArray(10_000), 10_000, 1)
    assertTrue(acc.result().all { it == 0.toByte() })
  }

  @Test
  fun emptyTrackGivesZeros() {
    val r = PeakAccumulator(estimatedFrames = 1000).result()
    assertEquals(100, r.size)
    assertTrue(r.all { it == 0.toByte() })
  }

  @Test
  fun theLoudestSliceIs255AndTheRestAreRelative() {
    val n = 10_000
    // First half at 1/4 of the second half's level.
    val samples = mono(n) { if (it < n / 2) 4000 else 16000 }
    val acc = PeakAccumulator(estimatedFrames = n.toLong())
    acc.add(samples, n, 1)
    val r = acc.result()
    assertEquals(255, u(r[99]))
    assertEquals(255, u(r[50]))
    assertEquals(64, u(r[0]))
    assertEquals(64, u(r[49]))
  }

  @Test
  fun channelsAreAveragedInEnergy() {
    val n = 2000
    val stereo = ShortArray(n * 2) { if (it % 2 == 0) 10000 else 0 }
    val acc = PeakAccumulator(estimatedFrames = n.toLong())
    acc.add(stereo, stereo.size, 2)
    assertEquals(n.toLong(), acc.frames)
    assertTrue(acc.result().all { u(it) == 255 })
  }

  @Test
  fun feedingInPiecesGivesTheSameResultAsAllAtOnce() {
    val n = 9_999
    val samples = mono(n) { ((it * 37) % 20000) - 10000 }
    val whole = PeakAccumulator(estimatedFrames = n.toLong()).also { it.add(samples, n, 1) }.result()
    val pieces = PeakAccumulator(estimatedFrames = n.toLong()).also {
      var p = 0
      while (p < n) {
        val len = minOf(1234, n - p)
        it.add(samples.copyOfRange(p, p + len), len, 1)
        p += len
      }
    }.result()
    assertArrayEquals(whole, pieces)
  }

  @Test
  fun aTrackShorterThanTheHeaderSaidStillFillsTheBar() {
    // Header promised 20_000 frames, the file had 10_000: loud first half, quiet second half.
    val n = 10_000
    val samples = mono(n) { if (it < n / 2) 20000 else 5000 }
    val acc = PeakAccumulator(estimatedFrames = 20_000)
    acc.add(samples, n, 1)
    val r = acc.result()
    assertEquals(255, u(r[0]))
    assertEquals(64, u(r[99]))
    assertTrue("the last bars are not left empty", r.takeLast(10).all { u(it) > 0 })
  }

  @Test
  fun aTrackLongerThanTheHeaderSaidKeepsItsEnd() {
    val n = 10_000
    val samples = mono(n) { if (it < 9_000) 3000 else 30000 }
    val acc = PeakAccumulator(estimatedFrames = 5_000)
    acc.add(samples, n, 1)
    val r = acc.result()
    assertEquals(100, r.size)
    assertTrue(r.any { u(it) == 255 })
  }

  @Test
  fun storeReadsBackWhatWasWrittenForTheSameFile() {
    val store = WaveformStore(File(temp.root, "waveforms"))
    val stamp = FileStamp(1234, 99)
    val data = ByteArray(100) { it.toByte() }
    assertNull(store.read("ms:1", stamp))
    store.writeReady("ms:1", stamp, data)
    val read = store.read("ms:1", stamp) as StoredWaveform.Ready
    assertArrayEquals(data, read.amplitudes)
  }

  @Test
  fun storeIgnoresAShapeMadeFromAnotherVersionOfTheFile() {
    val store = WaveformStore(File(temp.root, "waveforms"))
    store.writeReady("ms:1", FileStamp(1234, 99), ByteArray(100))
    assertNull(store.read("ms:1", FileStamp(1235, 99)))
    assertNull(store.read("ms:1", FileStamp(1234, 100)))
  }

  @Test
  fun unavailableIsRememberedUntilTheFileChanges() {
    val store = WaveformStore(File(temp.root, "waveforms"))
    store.writeUnavailable("ms:2", FileStamp(10, 1))
    assertEquals(StoredWaveform.Unavailable, store.read("ms:2", FileStamp(10, 1)))
    assertNull(store.read("ms:2", FileStamp(11, 1)))
  }

  @Test
  fun similarIdsDoNotShareAShape() {
    val store = WaveformStore(File(temp.root, "waveforms"))
    val stamp = FileStamp(1, 1)
    store.writeReady("ms:1", stamp, ByteArray(100) { 7 })
    // Same file name once "unsafe" characters are replaced, different track.
    assertNull(store.read("ms_1", stamp))
  }

  @Test
  fun aDamagedFileCountsAsMissing() {
    val dir = File(temp.root, "waveforms")
    val store = WaveformStore(dir)
    val stamp = FileStamp(1, 1)
    store.writeReady("ms:3", stamp, ByteArray(100))
    dir.listFiles()!!.single().writeBytes(byteArrayOf(1, 2, 3))
    assertNull(store.read("ms:3", stamp))
  }

  @Test
  fun retainOnlyRemovesTracksThatAreGone() {
    val dir = File(temp.root, "waveforms")
    val store = WaveformStore(dir)
    val stamp = FileStamp(1, 1)
    store.writeReady("ms:1", stamp, ByteArray(100))
    store.writeReady("ms:2", stamp, ByteArray(100))
    store.retainOnly(setOf("ms:2"))
    assertNull(store.read("ms:1", stamp))
    assertTrue(store.read("ms:2", stamp) is StoredWaveform.Ready)
  }
}
