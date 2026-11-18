package com.example

import com.example.data.model.Track
import com.example.ui.screens.library.letterStarts
import org.junit.Assert.assertEquals
import org.junit.Test

class LetterStartsTest {
  private fun t(id: String) = Track(id = id, title = id, artist = "", album = "", durationMs = 0L, genre = "", era = "")

  @Test
  fun `each letter points at its first track in list order`() {
    val tracks = listOf(t("Ana"), t("Abel"), t("Bia"), t("Caio"), t("Cris"))
    assertEquals(listOf("A" to 0, "B" to 2, "C" to 3), letterStarts(tracks) { it.title.take(1) })
  }

  @Test
  fun `tracks without a letter are skipped and an empty list gives none`() {
    assertEquals(listOf("B" to 1), letterStarts(listOf(t("1x"), t("Bia"))) { if (it.id[0].isLetter()) it.title.take(1) else null })
    assertEquals(emptyList<Pair<String, Int>>(), letterStarts(emptyList()) { "A" })
  }
}
