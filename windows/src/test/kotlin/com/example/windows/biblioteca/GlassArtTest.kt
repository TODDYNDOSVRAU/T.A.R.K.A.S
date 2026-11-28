package com.example.windows.biblioteca

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The art of the Glass Folder against the original generator. Every reference number below was produced by running the
 * original JavaScript (the React spec of the owner's request, verbatim) in node: the same recipe, seed and size must give
 * the same pixels here, to the byte.
 */
class GlassArtTest {
  private fun fnv(bytes: ByteArray): Long {
    var h = 2166136261L.toInt()
    for (b in bytes) h = (h xor (b.toInt() and 0xFF)) * 16777619
    return h.toUInt().toLong()
  }

  private fun sum(bytes: ByteArray): Long = bytes.sumOf { (it.toInt() and 0xFF).toLong() }

  private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

  private class Ref(
    val preset: ArtPreset, val seed: Long, val w: Int, val h: Int, val fnv: Long, val sum: Long,
    val first: IntArray, val mid: IntArray,
  )

  private val refs = listOf(
    Ref(ArtPreset.Aurora, 3, 40, 30, 1735166235, 779894, intArrayOf(40, 34, 49, 255, 40, 33, 48, 255), intArrayOf(44, 30, 41, 255)),
    Ref(ArtPreset.Ember, 11, 33, 25, 3875338105, 379890, intArrayOf(37, 59, 67, 255, 53, 83, 91, 255), intArrayOf(14, 21, 26, 255)),
    Ref(ArtPreset.Glacier, 7, 17, 64, 561094012, 956191, intArrayOf(157, 176, 192, 255, 149, 168, 186, 255), intArrayOf(166, 184, 201, 255)),
    Ref(ArtPreset.Dusk, 42, 64, 48, 3976521508, 1584247, intArrayOf(134, 101, 146, 255, 75, 53, 76, 255), intArrayOf(48, 27, 34, 255)),
    Ref(ArtPreset.Verdant, 5, 31, 31, 214186856, 496211, intArrayOf(174, 141, 70, 255, 206, 178, 107, 255), intArrayOf(20, 24, 18, 255)),
  )

  @Test
  fun `rng matches the original mulberry32`() {
    val r3 = Rng(3)
    val got = DoubleArray(5) { r3.next() }
    val want = doubleArrayOf(
      0.7202267837710679, 0.03866216051392257, 0.4561921926215291, 0.07492800964973867, 0.7630053898319602,
    )
    assertArrayEquals(want, got, 0.0)
    val r0 = Rng(0)
    assertEquals(0.26642920868471265, r0.next(), 0.0)
    assertEquals(0.0003297457005828619, r0.next(), 0.0)
  }

  @Test
  fun `hashString matches the original FNV-1a over UTF-16 units`() {
    assertEquals(1130817578L, hashString("Capa — final"))
    assertEquals(2166136261L, hashString(""))
    assertEquals(440920331L, hashString("abc"))
  }

  @Test
  fun `fanSlot spreads sheets from -1 to 1 and centres a lone one`() {
    assertEquals(0.0, fanSlot(0, 1), 0.0)
    assertEquals(0.0, fanSlot(0, 0), 0.0)
    assertEquals(-1.0, fanSlot(0, 2), 0.0)
    assertEquals(1.0, fanSlot(1, 2), 0.0)
    assertEquals(-1.0, fanSlot(0, 3), 0.0)
    assertEquals(0.0, fanSlot(1, 3), 0.0)
    assertEquals(1.0, fanSlot(2, 3), 0.0)
  }

  @Test
  fun `hexToRgb reads short and long forms and falls back to mid grey`() {
    assertArrayEquals(intArrayOf(0xf4, 0xef, 0xe9), hexToRgb("#f4efe9"))
    assertArrayEquals(intArrayOf(0xff, 0xaa, 0x00), hexToRgb("fa0"))
    assertArrayEquals(intArrayOf(0x80, 0x80, 0x80), hexToRgb("nonsense"))
  }

  @Test
  fun `art is pixel for pixel the original for five recipes`() {
    for (ref in refs) {
      val key = GlassArtPainter.Key(ref.preset.recipe, ref.seed, ref.w, ref.h)
      val px = GlassArtPainter.pixels(key)
      val name = "${ref.preset} seed ${ref.seed} ${ref.w}x${ref.h}"
      assertEquals(name, ref.w * ref.h * 4, px.size)
      assertEquals("$name checksum", ref.fnv, fnv(px))
      assertEquals("$name byte sum", ref.sum, sum(px))
      assertArrayEquals("$name first pixels", bytes(*ref.first), px.copyOfRange(0, 8))
      val mi = ((ref.h / 2) * ref.w + ref.w / 2) * 4
      assertArrayEquals("$name middle pixel", bytes(*ref.mid), px.copyOfRange(mi, mi + 4))
    }
  }

  @Test
  fun `the slice size never changes the picture`() {
    val a = ArtJob(40, 30, ArtPreset.Aurora.recipe, 3)
    while (!a.step(1000)) Unit
    val b = ArtJob(40, 30, ArtPreset.Aurora.recipe, 3)
    var calls = 0
    while (!b.step(1)) calls++
    assertTrue("one row at a time must take several calls", calls > 5)
    assertArrayEquals(a.pixels, b.pixels)
  }

  @Test
  fun `a different seed gives a different picture and the same seed the same`() {
    val a = GlassArtPainter.pixels(GlassArtPainter.Key(ArtPreset.Ember.recipe, 1, 32, 24))
    val b = GlassArtPainter.pixels(GlassArtPainter.Key(ArtPreset.Ember.recipe, 2, 32, 24))
    val c = GlassArtPainter.pixels(GlassArtPainter.Key(ArtPreset.Ember.recipe, 1, 32, 24))
    assertArrayEquals(a, c)
    assertTrue(!a.contentEquals(b))
  }

  @Test
  fun `a recipe with fewer than two colours still paints`() {
    val r = ArtRecipe(ArtStyle.Marble, listOf("#123456"))
    val px = GlassArtPainter.pixels(GlassArtPainter.Key(r, 1, 8, 8))
    assertEquals(8 * 8 * 4, px.size)
    assertTrue(px.indices.filter { it % 4 == 3 }.all { px[it] == 255.toByte() })
  }

  @Test
  fun `paint is cached and shared by recipe, seed and size`() = runBlocking {
    val key = GlassArtPainter.Key(ArtPreset.Dusk.recipe, 99, 48, 32)
    val first = GlassArtPainter.paint(key)
    assertEquals(48, first.width)
    assertEquals(32, first.height)
    assertSame(first, GlassArtPainter.paint(key))
    assertSame(first, GlassArtPainter.cached(key))
    val other = GlassArtPainter.paint(key.copy(seed = 100))
    assertTrue(other !== first)
  }

  @Test
  fun `degenerate sizes are clamped to one pixel`() = runBlocking {
    val bmp = GlassArtPainter.paint(GlassArtPainter.Key(ArtPreset.Glacier.recipe, 1, 0, 0))
    assertEquals(1, bmp.width)
    assertEquals(1, bmp.height)
  }
}
