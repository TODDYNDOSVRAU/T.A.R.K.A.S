package com.example

import com.example.ui.screens.SettingsLayer
import com.example.ui.screens.settingsGroupVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S1: which groups of Ajustes show for a layer and for a search. */
class SettingsLayersTest {
  private val theme = listOf("Theme", "Dark")
  private val diag = listOf("Diagnóstico", "Exportar diagnóstico")

  @Test fun withoutSearchOnlyTheChosenLayerShows() {
    assertTrue(settingsGroupVisible("", SettingsLayer.ESSENTIAL, SettingsLayer.ESSENTIAL, theme))
    assertFalse(settingsGroupVisible("", SettingsLayer.ESSENTIAL, SettingsLayer.ADVANCED, theme))
    assertTrue(settingsGroupVisible("  ", SettingsLayer.ENGINEERING, SettingsLayer.ENGINEERING, diag))
    assertFalse(settingsGroupVisible("", SettingsLayer.ENGINEERING, SettingsLayer.ESSENTIAL, theme))
  }

  @Test fun aSearchLooksInEveryLayer() {
    // The user is on Essential, the match is in Engineering: it still shows.
    assertTrue(settingsGroupVisible("diag", SettingsLayer.ESSENTIAL, SettingsLayer.ENGINEERING, diag))
    assertFalse(settingsGroupVisible("diag", SettingsLayer.ESSENTIAL, SettingsLayer.ESSENTIAL, theme))
  }

  @Test fun accentsAndCaseDoNotMatter() {
    assertTrue(settingsGroupVisible("DIAGNOSTICO", SettingsLayer.ESSENTIAL, SettingsLayer.ENGINEERING, diag))
    assertTrue(settingsGroupVisible("diagnóstico", SettingsLayer.ESSENTIAL, SettingsLayer.ENGINEERING, listOf("Diagnostico")))
  }

  @Test fun aRowTitleFindsItsGroup() {
    assertTrue(settingsGroupVisible("exportar", SettingsLayer.ESSENTIAL, SettingsLayer.ENGINEERING, diag))
  }

  @Test fun noMatchShowsNothing() {
    SettingsLayer.entries.forEach { l ->
      assertFalse(settingsGroupVisible("zzz", SettingsLayer.ESSENTIAL, l, theme + diag))
    }
  }

  @Test fun theSavedKeyRoundTripsAndFallsBackToEssential() {
    SettingsLayer.entries.forEach { assertEquals(it, SettingsLayer.fromKey(it.key)) }
    assertEquals(SettingsLayer.ESSENTIAL, SettingsLayer.fromKey(null))
    assertEquals(SettingsLayer.ESSENTIAL, SettingsLayer.fromKey("gone"))
  }
}
