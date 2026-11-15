package com.example.ui.screens

import java.text.Normalizer

/** The three layers of the interface (identity rule): what most people need, detailed settings, and diagnostics. */
enum class SettingsLayer(val key: String) {
  ESSENTIAL("essential"),
  ADVANCED("advanced"),
  ENGINEERING("engineering");

  companion object {
    fun fromKey(key: String?): SettingsLayer = entries.firstOrNull { it.key == key } ?: ESSENTIAL
  }
}

/** Lower case with accents removed, so "diagnostico" finds "Diagnóstico". */
internal fun searchKey(text: String): String =
  Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "").lowercase().trim()

/**
 * Whether a group of settings shows. With no search text only the chosen layer shows. With search text the
 * layer no longer limits the result: every group in any layer with a matching title or row shows, so a
 * setting is never hidden in a layer the user did not open.
 */
fun settingsGroupVisible(query: String, selected: SettingsLayer, layer: SettingsLayer, texts: List<String>): Boolean {
  val q = searchKey(query)
  if (q.isEmpty()) return layer == selected
  return texts.any { searchKey(it).contains(q) }
}
