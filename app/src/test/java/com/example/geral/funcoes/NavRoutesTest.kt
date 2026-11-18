package com.example

import com.example.ui.viewmodel.NavStack
import com.example.ui.viewmodel.Route
import com.example.ui.viewmodel.SettingsSubPage
import com.example.ui.viewmodel.TarkasNavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** G7: the route stack replacing the tab enum, the curiosity flag and the two Ajustes sub-page mechanisms. */
class NavRoutesTest {
  private fun tab(t: TarkasNavTab) = Route.Tab(t)

  @Test fun startsOnHomeWithNothingToGoBackTo() {
    val n = NavStack()
    assertEquals(tab(TarkasNavTab.HOME), n.current)
    assertEquals(TarkasNavTab.HOME, n.currentTab)
    assertFalse(n.canGoBack)
  }

  @Test fun aTabBarTapStartsFreshAtThatTab() {
    val n = NavStack()
    n.open(tab(TarkasNavTab.MUSIC))
    n.select(TarkasNavTab.LIBRARY)
    assertEquals(listOf(tab(TarkasNavTab.HOME), tab(TarkasNavTab.LIBRARY)), n.snapshot)
    n.back()
    assertEquals(TarkasNavTab.HOME, n.currentTab)
    assertFalse(n.canGoBack)
  }

  @Test fun selectingHomeLeavesASingleRoute() {
    val n = NavStack()
    n.select(TarkasNavTab.AUDIO)
    n.select(TarkasNavTab.HOME)
    assertEquals(listOf(tab(TarkasNavTab.HOME)), n.snapshot)
  }

  @Test fun aPageOpenedOnTopGoesBackToWhereItWasOpenedFrom() {
    val n = NavStack()
    n.select(TarkasNavTab.LIBRARY)
    n.open(tab(TarkasNavTab.MUSIC))
    assertEquals(TarkasNavTab.MUSIC, n.currentTab)
    n.back()
    assertEquals(TarkasNavTab.LIBRARY, n.currentTab)
  }

  @Test fun openingAPageAlreadyBelowGoesBackToItInsteadOfPilingUpCopies() {
    val n = NavStack()
    n.open(tab(TarkasNavTab.SETTINGS))
    n.open(tab(TarkasNavTab.AUDIO))
    n.open(tab(TarkasNavTab.SETTINGS))
    assertEquals(listOf(tab(TarkasNavTab.HOME), tab(TarkasNavTab.SETTINGS)), n.snapshot)
  }

  @Test fun openingTheCurrentPageChangesNothing() {
    val n = NavStack()
    n.open(tab(TarkasNavTab.MUSIC))
    n.open(tab(TarkasNavTab.MUSIC))
    assertEquals(2, n.snapshot.size)
  }

  @Test fun backOnTheFloorStaysOnHome() {
    val n = NavStack()
    n.back()
    assertEquals(listOf(tab(TarkasNavTab.HOME)), n.snapshot)
  }

  @Test fun backFromATabWithNothingUnderItLandsOnHome() {
    val n = NavStack(listOf(tab(TarkasNavTab.AUDIO)))
    assertTrue(n.canGoBack)
    n.back()
    assertEquals(TarkasNavTab.HOME, n.currentTab)
    assertFalse(n.canGoBack)
  }

  @Test fun aTextOpenedFromAboutGoesBackToAboutThenToAjustes() {
    val n = NavStack()
    n.open(tab(TarkasNavTab.SETTINGS))
    n.open(Route.Settings(SettingsSubPage.ABOUT))
    n.open(Route.Settings(SettingsSubPage.TERMS))
    assertEquals(SettingsSubPage.TERMS, n.settingsPage)
    assertEquals(TarkasNavTab.SETTINGS, n.currentTab)
    n.back()
    assertEquals(SettingsSubPage.ABOUT, n.settingsPage)
    n.back()
    assertNull(n.settingsPage)
    assertEquals(TarkasNavTab.SETTINGS, n.current.let { (it as Route.Tab).tab })
  }

  @Test fun aTextOpenedFromAjustesGoesStraightBackToAjustes() {
    val n = NavStack()
    n.open(tab(TarkasNavTab.SETTINGS))
    n.open(Route.Settings(SettingsSubPage.PRIVACY))
    n.back()
    assertNull(n.settingsPage)
    assertEquals(tab(TarkasNavTab.SETTINGS), n.current)
  }

  @Test fun curiosityIsARouteOverAjustes() {
    val n = NavStack()
    n.open(tab(TarkasNavTab.SETTINGS))
    n.open(Route.CuriositySettings)
    assertTrue(n.showingCuriosity)
    assertEquals(TarkasNavTab.SETTINGS, n.currentTab)
    n.back()
    assertFalse(n.showingCuriosity)
    assertEquals(TarkasNavTab.SETTINGS, n.currentTab)
  }

  @Test fun aTabBarTapLeavesEveryPageOpenedAboveIt() {
    val n = NavStack()
    n.open(tab(TarkasNavTab.SETTINGS))
    n.open(Route.CuriositySettings)
    n.select(TarkasNavTab.MUSIC)
    assertFalse(n.showingCuriosity)
    assertNull(n.settingsPage)
    assertEquals(TarkasNavTab.MUSIC, n.currentTab)
  }

  @Test fun theSavedFormRoundTrips() {
    val n = NavStack()
    n.open(tab(TarkasNavTab.SETTINGS))
    n.open(Route.Settings(SettingsSubPage.ABOUT))
    n.open(Route.Settings(SettingsSubPage.LICENSES))
    val back = NavStack.decode(n.encode())
    assertEquals(n.snapshot, back.snapshot)
    val curiosity = NavStack(listOf(tab(TarkasNavTab.HOME), tab(TarkasNavTab.SETTINGS), Route.CuriositySettings))
    assertEquals(curiosity.snapshot, NavStack.decode(curiosity.encode()).snapshot)
  }

  @Test fun anOldOrDamagedSavedValueFallsBackToHome() {
    assertEquals(listOf(tab(TarkasNavTab.HOME)), NavStack.decode(null).snapshot)
    assertEquals(listOf(tab(TarkasNavTab.HOME)), NavStack.decode(emptyList()).snapshot)
    assertEquals(listOf(tab(TarkasNavTab.HOME)), NavStack.decode(listOf("tab:GONE", "settings:NOPE", "x")).snapshot)
    assertEquals(listOf(tab(TarkasNavTab.AUDIO)), NavStack.decode(listOf("x", "tab:AUDIO")).snapshot)
  }

  @Test fun everyRouteEncodesToItsOwnText() {
    val all = TarkasNavTab.entries.map { tab(it) } + SettingsSubPage.entries.map { Route.Settings(it) } + Route.CuriositySettings
    assertEquals(all.size, all.map { it.encode() }.toSet().size)
    all.forEach { assertEquals(it, Route.decode(it.encode())) }
  }
}
