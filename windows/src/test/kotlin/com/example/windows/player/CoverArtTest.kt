package com.example.windows.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CoverArtTest {
  private fun frame(id: String, body: ByteArray) =
    id.toByteArray() + byteArrayOf(0, 0, ((body.size shr 8) and 0xFF).toByte(), (body.size and 0xFF).toByte(), 0, 0) + body

  @Test fun apicFrameOfAnId3v23TagGivesTheImageBytes() {
    val img = byteArrayOf(1, 2, 3, 4, 5)
    val apic = byteArrayOf(0) + "image/png".toByteArray() + byteArrayOf(0, 3) + "front".toByteArray() + byteArrayOf(0) + img
    val tag = frame("TIT2", byteArrayOf(0, 'a'.code.toByte())) + frame("APIC", apic) + ByteArray(8)
    assertArrayEquals(img, CoverArt.id3(3, tag))
  }

  @Test fun tagWithoutApicGivesNull() {
    assertNull(CoverArt.id3(3, frame("TIT2", byteArrayOf(0, 'a'.code.toByte())) + ByteArray(8)))
    assertNull(CoverArt.id3(2, ByteArray(20)))
  }

  @Test fun coverFileBesideTheTrackIsUsedAndMissingFileIsNull() {
    val dir = createTempDir("cover")
    try {
      val song = File(dir, "s.mp3").also { it.writeBytes(ByteArray(32)) }
      assertNull(CoverArt.bytes(song))
      File(dir, "folder.jpg").writeBytes(byteArrayOf(9, 8, 7))
      assertArrayEquals(byteArrayOf(9, 8, 7), CoverArt.bytes(song))
      assertNull(CoverArt.bytes(File(dir, "none.mp3")))
    } finally { dir.deleteRecursively() }
  }
}
