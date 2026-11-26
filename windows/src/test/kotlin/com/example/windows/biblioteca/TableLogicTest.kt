package com.example.windows.biblioteca

import com.example.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TableLogicTest {
  private fun t(id: String, title: String, artist: String = "A", ms: Long = 1000) = Track(id, title, artist, "", ms, "", "")
  private val order = listOf("a", "b", "c", "d", "e")

  @Test fun plainClickSelectsOne() {
    val s = Selection().click(2, order, ctrl = false, shift = false)
    assertEquals(setOf("c"), s.ids)
  }

  @Test fun ctrlTogglesAndShiftExtendsFromAnchor() {
    var s = Selection().click(1, order, false, false).click(3, order, ctrl = true, shift = false)
    assertEquals(setOf("b", "d"), s.ids)
    s = s.click(3, order, ctrl = true, shift = false)
    assertEquals(setOf("b"), s.ids)
    s = Selection().click(1, order, false, false).click(3, order, ctrl = false, shift = true)
    assertEquals(setOf("b", "c", "d"), s.ids)
  }

  @Test fun contextKeepsSelectionOnlyInsideIt() {
    val s = Selection(setOf("a", "b"), 0, 1)
    assertEquals(setOf("a", "b"), s.context(1, order).ids)
    assertEquals(setOf("d"), s.context(3, order).ids)
  }

  @Test fun arrowsMoveAndShiftGrows() {
    var s = Selection().move(1, order, false)
    assertEquals(setOf("a"), s.ids)
    s = s.move(2, order, shift = true)
    assertEquals(setOf("a", "b", "c"), s.ids)
    assertEquals(setOf("e"), Selection(setOf("e"), 4, 4).move(5, order, false).ids)
  }

  @Test fun withinDropsRemovedIds() {
    assertEquals(setOf("a"), Selection(setOf("a", "z"), 0, 0).within(order).ids)
  }

  @Test fun sortSpecClickTogglesAndRoundTrips() {
    val s = SortSpec(Col.TITLE).click(Col.TITLE)
    assertEquals(false, s.ascending)
    assertEquals(SortSpec(Col.PLAYS, false), SortSpec(Col.TITLE).click(Col.PLAYS))
    assertEquals(s, SortSpec.decode(s.encode()))
    assertNull(SortSpec.decode("nonsense"))
  }

  @Test fun sortIgnoresAccentsAndCase() {
    val list = listOf(t("1", "zebra"), t("2", "Élan"), t("3", "apple"))
    assertEquals(listOf("3", "2", "1"), TableLogic.sort(list, SortSpec(Col.TITLE), emptyMap()).map { it.id })
    assertEquals(listOf("1", "2", "3"), TableLogic.sort(list, SortSpec(Col.PLAYS, false), mapOf("1" to 5, "2" to 3)).map { it.id })
  }

  @Test fun resizeRespectsMinimumAndWidthsRoundTrip() {
    val w = TableLogic.defaultWidths()
    val small = TableLogic.resize(w, Col.ALBUM, -10_000)
    assertEquals(Col.ALBUM.minDp, small[Col.ALBUM])
    assertEquals(small, TableLogic.decodeWidths(TableLogic.encodeWidths(small)))
    assertEquals(w, TableLogic.decodeWidths(null))
  }

  @Test fun fitNeverGoesBelowMinimums() {
    val fitted = TableLogic.fit(TableLogic.defaultWidths(), 300)
    for ((c, v) in fitted) assertTrue(v >= c.minDp)
  }

  @Test fun typeAheadFindsNextMatch() {
    val list = listOf(t("1", "Alpha"), t("2", "Beta"), t("3", "Bravo"))
    assertEquals(1, TableLogic.typeAhead(list, "b"))
    assertEquals(2, TableLogic.typeAhead(list, "br"))
    assertEquals(-1, TableLogic.typeAhead(list, "zz"))
  }

  @Test fun typeAheadBufferJoinsWithinWindowAndResetsAfter() {
    var now = 0L
    val b = TypeAheadBuffer(800) { now }
    assertEquals("b", b.push('b'))
    now = 300
    assertEquals("br", b.push('r'))
    now = 2000
    assertEquals("x", b.push('x'))
  }

  @Test fun importPlanSplitsFoldersAndAudio() {
    val dir = Files.createTempDirectory("imp").toFile()
    val inside = File(dir, "in.mp3").apply { writeText("x") }
    val loose = Files.createTempFile("loose", ".mp3").toFile()
    val other = Files.createTempFile("note", ".txt").toFile()
    val plan = TableLogic.importPlan(listOf(dir, inside, loose, other)) { it.extension == "mp3" }
    assertEquals(listOf(dir.absoluteFile), plan.folders)
    assertEquals(listOf(loose.absoluteFile), plan.audio)
    assertEquals(1, plan.ignored)
    dir.deleteRecursively(); loose.delete(); other.delete()
  }

  @Test fun narrowTableHidesAlbumAndPlaysAndKeepsATitle() {
    val w = TableLogic.defaultWidths()
    val narrow = TableLogic.fitTo(w, 440)
    assertEquals(0, narrow[Col.ALBUM]); assertEquals(0, narrow[Col.PLAYS])
    assertTrue(TableLogic.titleWidth(narrow, 440) >= Col.TITLE.minDp)
    assertEquals(w, TableLogic.fitTo(w, 1200))
  }
}
