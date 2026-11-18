package com.example.data

import java.text.Normalizer
import java.util.Locale

/** Text comparison for searches: case and accents do not matter, so "acao" finds "Ação". */
object TextMatch {
  private val marks = Regex("\\p{M}+")

  /** Lowercase without accents ("Ação" → "acao", "JOÃO" → "joao"). */
  fun fold(s: String): String = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

  /** Whether [text] holds [query], ignoring case and accents. A blank query matches everything. */
  fun contains(text: String, query: String): Boolean {
    val q = fold(query.trim())
    return q.isEmpty() || q in fold(text)
  }
}
