package com.example.windows.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.example.ui.theme.TarkasTheme
import com.example.ui.viewmodel.TarkasNavTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DockEngineTest {
  @Test fun tileFollowsTheTentOfTheOriginal() {
    assertEquals(96f, DockEngine.target(0f), 0.001f)
    assertEquals(72f, DockEngine.target(90f), 0.001f)
    assertEquals(72f, DockEngine.target(-90f), 0.001f)
    assertEquals(48f, DockEngine.target(180f), 0.001f)
    assertEquals(48f, DockEngine.target(600f), 0.001f)
  }

  @Test fun reservedWidthCoversTheWorstCaseSoNothingAroundMoves() {
    assertEquals(384f, DockEngine.restWidth(7), 0.001f)              // 7 x 48 + 6 x 6 + 2 x 6
    assertEquals(163.2f, DockEngine.maxGrowth(7), 0.01f)
    assertEquals(624f, DockEngine.reservedWidth(7), 0.01f)           // search field (288 - 48) is the larger need
  }

  @Test fun springIsTheOriginalForUnitMass() {
    // mass 0.1, stiffness 150, damping 12: ratio = 12 / (2 sqrt(150 x 0.1)), stiffness = 150 / 0.1
    assertEquals(12f / (2f * kotlin.math.sqrt(15f)), DockEngine.DAMPING_RATIO, 0.001f)
    assertEquals(1500f, DockEngine.STIFFNESS, 0.001f)
  }

  @Test fun compactBreakpointLeavesAMarginBesideTheDock() {
    assertEquals(672f, CompactDockBelow.value, 0.01f)
  }
}

@OptIn(ExperimentalTestApi::class)
class FloatingDockUiTest {
  @Before @After fun clean() { ShellSearch.close(); SidePane.close(); ShellIsland.reset() }

  private class Host {
    var tab by mutableStateOf(TarkasNavTab.HOME)
    val selected = mutableListOf<TarkasNavTab>()
    var typed = 0
    @Composable fun Content(compact: Boolean = false, reduce: Boolean = false) {
      TarkasTheme {
        FloatingDock(current = tab, onSelect = { selected += it; tab = it }, onTyped = { typed++ }, reduceMotion = reduce, compact = compact)
      }
    }
  }

  private fun androidx.compose.ui.test.ComposeUiTest.openIsland() { onNodeWithTag("island_pill").performClick(); waitForIdle() }

  @Test fun atRestTheIslandIsThePillWithTheCurrentPage() = runComposeUiTest {
    val h = Host()
    h.tab = TarkasNavTab.AUDIO
    setContent { h.Content() }
    onNodeWithTag("island_pill").assertExists()
    onNodeWithTag("dock_home").assertDoesNotExist()
    val pill = onNodeWithTag("island_pill").getBoundsInRoot()
    assertEquals(192f, (pill.right - pill.left).value, 0.5f)
    assertEquals(48f, (pill.bottom - pill.top).value, 0.5f)
    val desc = onNodeWithTag("island_pill").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
    assertTrue(desc != null && desc.single().contains(":"))
    assertEquals(IslandState.COMPACT, ShellIsland.state.value)
  }

  @Test fun openingAndPressingOutsideCloses() = runComposeUiTest {
    val h = Host()
    setContent { h.Content(reduce = true) }
    openIsland()
    assertEquals(IslandState.NAVIGATION, ShellIsland.state.value)
    onNodeWithTag("dock_home").assertExists()
    ShellIsland.press(androidx.compose.ui.geometry.Offset(5000f, 5000f))
    waitForIdle()
    assertEquals(IslandState.COMPACT, ShellIsland.state.value)
    onNodeWithTag("island_pill").assertExists()
    onNodeWithTag("dock_home").assertDoesNotExist()
  }

  @Test fun everyRealPageIsInTheDockAndTheCurrentOneIsSelected() = runComposeUiTest {
    val h = Host()
    setContent { h.Content() }
    openIsland()
    for (t in NavOrder) onNodeWithTag("dock_${t.name.lowercase()}").assertExists()
    onNodeWithTag("dock_search").assertExists()
    onNodeWithTag("dock_home").assertIsSelected()
    onNodeWithTag("dock_audio").assertIsNotSelected()
    val desc = onNodeWithTag("dock_audio").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
    assertTrue(desc != null && desc.single().isNotBlank())
  }

  @Test fun clickingAPageNavigatesAndTheIslandCompacts() = runComposeUiTest {
    val h = Host()
    setContent { h.Content(reduce = true) }
    openIsland()
    onNodeWithTag("dock_library").performClick()
    waitForIdle()
    assertEquals(IslandState.COMPACT, ShellIsland.state.value)
    openIsland()
    onNodeWithTag("dock_settings").performClick()
    waitForIdle()
    assertEquals(listOf(TarkasNavTab.LIBRARY, TarkasNavTab.SETTINGS), h.selected)
    openIsland()
    onNodeWithTag("dock_settings").assertIsSelected()
    onNodeWithTag("dock_home").assertIsNotSelected()
  }

  @Test fun searchOpensTypesAndCloses() = runComposeUiTest {
    val h = Host()
    setContent { h.Content() }
    openIsland()
    onNodeWithTag("dock_search_field").assertDoesNotExist()
    onNodeWithTag("dock_search").performClick()
    waitForIdle()
    assertTrue(ShellSearch.isOpen.value)
    onNodeWithTag("dock_search_field").performTextInput("ana")
    waitForIdle()
    assertEquals("ana", ShellSearch.query.value)
    assertTrue(h.typed > 0)
    assertEquals(IslandState.SEARCH, ShellIsland.state.value)
    // The results live outside the island: a press on them keeps the field and the query.
    ShellIsland.press(androidx.compose.ui.geometry.Offset(5000f, 5000f))
    waitForIdle()
    assertEquals(IslandState.SEARCH, ShellIsland.state.value)
    assertEquals("ana", ShellSearch.query.value)
    onNodeWithTag("dock_search_close").performClick()
    waitForIdle()
    assertFalse(ShellSearch.isOpen.value)
    assertEquals("", ShellSearch.query.value)
    onNodeWithTag("dock_search_field").assertDoesNotExist()
    onNodeWithTag("dock_search").assertExists()
    assertEquals(IslandState.NAVIGATION, ShellIsland.state.value)   // closing the search goes back one layer
  }

  @Test fun ctrlFFromOutsideOpensTheField() = runComposeUiTest {
    val h = Host()
    setContent { h.Content() }
    ShellSearch.openDock()
    waitForIdle()
    onNodeWithTag("dock_search_field").assertExists()
  }

  @Test fun mouseOverMagnifiesAndLeavingRestores() = runComposeUiTest {
    val h = Host()
    setContent { h.Content() }
    ShellIsland.send(IslandEvent.OPEN)                                // opened without the mouse, so it starts away
    waitForIdle()
    val rest = onNodeWithTag("floating_dock").getBoundsInRoot()
    assertEquals(60f, (rest.bottom - rest.top).value, 0.5f)          // 6 + 48 + 6
    assertEquals(624f, (rest.right - rest.left).value, 0.5f)
    onNodeWithTag("dock_audio").performMouseInput { moveTo(center) }
    waitForIdle()
    val over = onNodeWithTag("floating_dock").getBoundsInRoot()
    assertEquals(108f, (over.bottom - over.top).value, 0.5f)          // 6 + 96 + 6: room for the grown tile
    assertEquals(624f, (over.right - over.left).value, 0.5f)          // width never changes
    onNodeWithTag("floating_dock").performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(1f, 1000f)) }
    waitForIdle()
    // Leaving the island: the tiles go back to rest and, after the grace, the island compacts to the pill.
    mainClock.advanceTimeBy(1000)
    waitForIdle()
    assertEquals(IslandState.COMPACT, ShellIsland.state.value)
    onNodeWithTag("island_pill").assertExists()
  }

  @Test fun compactDockShowsTheCurrentPageAndOpensTheMenu() = runComposeUiTest {
    val h = Host()
    h.tab = TarkasNavTab.AUDIO
    setContent { h.Content(compact = true, reduce = true) }
    onNodeWithTag("dock_toggle").assertExists()
    onNodeWithTag("dock_home").assertDoesNotExist()
    onNodeWithTag("dock_menu").assertDoesNotExist()
    onNodeWithTag("dock_toggle").performClick()
    waitForIdle()
    onNodeWithTag("dock_menu").assertExists()
    onNodeWithTag("dock_menu_library").performClick()
    waitForIdle()
    assertEquals(listOf(TarkasNavTab.LIBRARY), h.selected)
    onNodeWithTag("dock_menu").assertDoesNotExist()
  }

  @Test fun compactDockKeepsTheSearch() = runComposeUiTest {
    val h = Host()
    setContent { h.Content(compact = true, reduce = true) }
    onNodeWithTag("dock_search").performClick()
    waitForIdle()
    assertTrue(ShellSearch.isOpen.value)
    onNodeWithTag("dock_search_field").performTextInput("rock")
    waitForIdle()
    assertEquals("rock", ShellSearch.query.value)
  }
}

class IslandMachineTest {
  private fun IslandState.then(vararg e: IslandEvent) = e.fold(this) { s, x -> IslandMachine.next(s, x) }

  @Test fun escClosesLayerByLayer() {
    val s = IslandState.COMPACT.then(IslandEvent.OPEN, IslandEvent.SEARCH_OPENED)
    assertEquals(IslandState.SEARCH, s)
    assertEquals(IslandState.NAVIGATION, s.then(IslandEvent.SEARCH_CLOSED))                    // first Esc
    assertEquals(IslandState.COMPACT, s.then(IslandEvent.SEARCH_CLOSED, IslandEvent.ESCAPE))   // second Esc
    assertEquals(IslandState.COMPACT, IslandState.COMPACT.then(IslandEvent.ESCAPE))
  }

  @Test fun pageChangeCompactsUnlessTyping() {
    assertEquals(IslandState.COMPACT, IslandState.NAVIGATION.then(IslandEvent.NAVIGATED))
    assertEquals(IslandState.SEARCH, IslandState.SEARCH.then(IslandEvent.NAVIGATED))
  }

  @Test fun outsideAndLeaveKeepATypedSearch() {
    assertEquals(IslandState.COMPACT, IslandState.NAVIGATION.then(IslandEvent.OUTSIDE))
    assertEquals(IslandState.COMPACT, IslandState.SEARCH.then(IslandEvent.OUTSIDE))
    assertEquals(IslandState.SEARCH, IslandState.SEARCH.then(IslandEvent.OUTSIDE_TYPED))
    assertEquals(IslandState.SEARCH, IslandState.SEARCH.then(IslandEvent.LEAVE))
    assertEquals(IslandState.COMPACT, IslandState.NAVIGATION.then(IslandEvent.LEAVE))
  }

  @Test fun ctrlFOpensTheSearchFromAnyState() {
    for (s in IslandState.entries) assertEquals(IslandState.SEARCH, s.then(IslandEvent.SEARCH_OPENED))
  }

  @Test fun toggleIsTheNarrowListAndNeverShutsTheSearch() {
    assertEquals(IslandState.NAVIGATION, IslandState.COMPACT.then(IslandEvent.TOGGLE))
    assertEquals(IslandState.COMPACT, IslandState.NAVIGATION.then(IslandEvent.TOGGLE))
    assertEquals(IslandState.SEARCH, IslandState.SEARCH.then(IslandEvent.TOGGLE))
  }
}
