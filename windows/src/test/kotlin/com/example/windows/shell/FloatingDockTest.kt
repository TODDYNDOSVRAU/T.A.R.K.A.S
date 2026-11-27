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
import androidx.compose.ui.test.runComposeUiTest
import com.example.ui.theme.TarkasTheme
import com.example.ui.viewmodel.TarkasNavTab
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertArrayEquals
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

  @Test fun theIslandHoldsTheFivePagesAndNotAjustes() {
    // Owner D1/D2 (2026-11-27): no search tile, Ajustes is the button at the top right.
    assertEquals(listOf(TarkasNavTab.HOME, TarkasNavTab.LIBRARY, TarkasNavTab.MUSIC, TarkasNavTab.AUDIO, TarkasNavTab.ASSISTANT), DockPages)
    assertFalse(TarkasNavTab.SETTINGS in DockPages)
  }

  @Test fun reservedWidthCoversTheWorstCaseSoNothingAroundMoves() {
    assertEquals(240f, DockEngine.restWidth(5), 0.001f)              // 5 slots of 48, each with its own 6 dp margin
    assertEquals(163.2f, DockEngine.maxGrowth(5), 0.01f)
    assertEquals(403.2f, DockEngine.reservedWidth(5), 0.01f)         // no search field any more: the magnification is the need
  }

  @Test fun slotDrawsTheCircleAtThreeQuartersAndTheIconAtHalf() {
    // 48 -> 36 circle, 24 icon at rest; 96 -> 72 circle, 48 icon at the peak: the 2x chain on both sides.
    assertEquals(36f, DockEngine.REST * DockEngine.CIRCLE, 0.001f)
    assertEquals(24f, DockEngine.REST * DockEngine.ICON, 0.001f)
    assertEquals(72f, DockEngine.MAX * DockEngine.CIRCLE, 0.001f)
    assertEquals(48f, DockEngine.MAX * DockEngine.ICON, 0.001f)
    assertEquals(2f, DockEngine.MAX / DockEngine.REST, 0.001f)
  }

  @Test fun iconsEnterFromTheCentreOutwards() {
    assertEquals(listOf(108L, 84L, 60L, 84L, 108L), (0 until 5).map { DockEngine.enterDelay(it, 5) })
    assertEquals(listOf(132L, 108L, 84L, 60L, 84L, 108L, 132L), (0 until 7).map { DockEngine.enterDelay(it, 7) })
    // Even count: the two middle slots start together.
    assertEquals(listOf(84L, 60L, 60L, 84L), (0 until 4).map { DockEngine.enterDelay(it, 4) })
    assertTrue(DockEngine.ENTER_MS in 150..300 && DockEngine.EXIT_MS in 150..300)
  }

  @Test fun springIsTheOriginalForUnitMass() {
    // mass 0.1, stiffness 150, damping 12: ratio = 12 / (2 sqrt(150 x 0.1)), stiffness = 150 / 0.1
    assertEquals(12f / (2f * kotlin.math.sqrt(15f)), DockEngine.DAMPING_RATIO, 0.001f)
    assertEquals(1500f, DockEngine.STIFFNESS, 0.001f)
  }

  @Test fun compactBreakpointLeavesAMarginBesideTheDock() {
    assertEquals(451.2f, CompactDockBelow.value, 0.01f)              // 403.2 + 48
  }
}

class IslandActivityTest {
  private fun bars(peaks: ByteArray?, f: Float) = FloatArray(IslandActivity.BARS).also { IslandActivity.bars(peaks, f, it) }

  @Test fun noWaveformIsFlatNeverAFakeMeter() {
    val flat = FloatArray(5) { IslandActivity.FLAT }
    assertArrayEquals(flat, bars(null, 0.5f), 0f)
    assertArrayEquals(flat, bars(ByteArray(0), 0.5f), 0f)
    assertArrayEquals(flat, bars(ByteArray(10) { 0xFF.toByte() }, Float.NaN), 0f)
  }

  @Test fun barsAreTheBucketsAroundThePlayPosition() {
    // 11 buckets 0, 25, 51 ... 255: the middle (index 5) is 127.
    val p = ByteArray(11) { (it * 255 / 10).toByte() }
    val b = bars(p, 0.5f)
    val want = listOf(3, 4, 5, 6, 7).map { k -> maxOf(IslandActivity.FLAT, (k * 255 / 10) / 255f) }.toFloatArray()
    assertArrayEquals(want, b, 0.0001f)
    // 255 reads as a full bar (unsigned byte, not -1).
    assertEquals(1f, bars(p, 1f)[2], 0.0001f)
  }

  @Test fun theEdgesOfTheTrackFallBackToFlat() {
    val p = ByteArray(11) { 0xFF.toByte() }
    val start = bars(p, 0f)
    assertEquals(IslandActivity.FLAT, start[0], 0f)
    assertEquals(IslandActivity.FLAT, start[1], 0f)
    assertEquals(1f, start[2], 0f)
    val past = bars(p, 7f)                                            // fraction clamped to the end
    assertEquals(1f, past[2], 0f)
    assertEquals(IslandActivity.FLAT, past[4], 0f)
  }
}

@OptIn(ExperimentalTestApi::class)
class FloatingDockUiTest {
  @Before @After fun clean() { SidePane.close(); ShellIsland.reset(); IslandActivity.reset() }

  private class Host {
    var tab by mutableStateOf(TarkasNavTab.HOME)
    var playing by mutableStateOf(false)
    val selected = mutableListOf<TarkasNavTab>()
    @Composable fun Content(compact: Boolean = false, reduce: Boolean = false) {
      TarkasTheme {
        FloatingDock(current = tab, onSelect = { selected += it; tab = it }, reduceMotion = reduce, compact = compact, playing = playing, fraction = { 0.5f })
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
    onNodeWithTag("island_activity", useUnmergedTree = true).assertDoesNotExist()
    val pill = onNodeWithTag("island_pill").getBoundsInRoot()
    assertEquals(192f, (pill.right - pill.left).value, 0.5f)
    assertEquals(48f, (pill.bottom - pill.top).value, 0.5f)
    val desc = onNodeWithTag("island_pill").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
    assertTrue(desc != null && desc.single().contains(":"))
    assertEquals(IslandState.COMPACT, ShellIsland.state.value)
  }

  @Test fun whilePlayingThePillShowsTheMusicActivity() = runComposeUiTest {
    val h = Host()
    h.playing = true
    setContent { h.Content() }
    onNodeWithTag("island_activity", useUnmergedTree = true).assertExists()
    onNodeWithTag("island_live", useUnmergedTree = true).assertExists()
    onNodeWithTag("island_output", useUnmergedTree = true).assertDoesNotExist()              // no output registered: nothing invented
    val pill = onNodeWithTag("island_pill").getBoundsInRoot()
    assertEquals(192f, (pill.right - pill.left).value, 0.5f)         // same pill, other content
    // The audio area registers the output: its icon appears.
    IslandActivity.output = MutableStateFlow(TarkasNavTab.AUDIO.icon())
    waitForIdle()
    onNodeWithTag("island_output", useUnmergedTree = true).assertExists()
    // Pausing goes back to the page name.
    h.playing = false
    waitForIdle()
    onNodeWithTag("island_activity", useUnmergedTree = true).assertDoesNotExist()
    // The pill still opens the island.
    openIsland()
    assertEquals(IslandState.NAVIGATION, ShellIsland.state.value)
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

  @Test fun theFivePagesAreInTheIslandAndNothingElse() = runComposeUiTest {
    val h = Host()
    setContent { h.Content() }
    openIsland()
    for (t in DockPages) onNodeWithTag("dock_${t.name.lowercase()}").assertExists()
    onNodeWithTag("dock_settings").assertDoesNotExist()
    onNodeWithTag("dock_search").assertDoesNotExist()
    onNodeWithTag("dock_search_field").assertDoesNotExist()
    onNodeWithTag("dock_home").assertIsSelected()
    onNodeWithTag("dock_audio").assertIsNotSelected()
    val desc = onNodeWithTag("dock_audio").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
    assertTrue(desc != null && desc.single().isNotBlank())
  }

  @Test fun onAjustesNoTileIsSelectedAndTheIslandStillOpens() = runComposeUiTest {
    val h = Host()
    h.tab = TarkasNavTab.SETTINGS
    setContent { h.Content(reduce = true) }
    openIsland()
    for (t in DockPages) onNodeWithTag("dock_${t.name.lowercase()}").assertIsNotSelected()
    onNodeWithTag("dock_home").performClick()
    waitForIdle()
    assertEquals(listOf(TarkasNavTab.HOME), h.selected)
  }

  @Test fun clickingAPageNavigatesAndTheIslandCompacts() = runComposeUiTest {
    val h = Host()
    setContent { h.Content(reduce = true) }
    openIsland()
    onNodeWithTag("dock_library").performClick()
    waitForIdle()
    assertEquals(IslandState.COMPACT, ShellIsland.state.value)
    openIsland()
    onNodeWithTag("dock_assistant").performClick()
    waitForIdle()
    assertEquals(listOf(TarkasNavTab.LIBRARY, TarkasNavTab.ASSISTANT), h.selected)
    openIsland()
    onNodeWithTag("dock_assistant").assertIsSelected()
    onNodeWithTag("dock_home").assertIsNotSelected()
  }

  @Test fun mouseOverMagnifiesAndLeavingRestores() = runComposeUiTest {
    val h = Host()
    setContent { h.Content() }
    ShellIsland.send(IslandEvent.OPEN)                                // opened without the mouse, so it starts away
    waitForIdle()
    val rest = onNodeWithTag("floating_dock").getBoundsInRoot()
    assertEquals(48f, (rest.bottom - rest.top).value, 0.5f)          // the pill's height: one 48 dp shape
    assertEquals(403.2f, (rest.right - rest.left).value, 0.5f)
    val slot = onNodeWithTag("dock_audio").getBoundsInRoot()
    assertEquals(48f, (slot.right - slot.left).value, 0.5f)          // drawn at 96, shrunk to its 48 dp slot
    onNodeWithTag("dock_audio").performMouseInput { moveTo(center) }
    waitForIdle()
    val over = onNodeWithTag("floating_dock").getBoundsInRoot()
    assertEquals(96f, (over.bottom - over.top).value, 0.5f)           // room for the slot grown to 96
    assertEquals(403.2f, (over.right - over.left).value, 0.5f)        // width never changes
    val grown = onNodeWithTag("dock_audio").getBoundsInRoot()
    assertEquals(96f, (grown.right - grown.left).value, 1f)           // 2x: 48 -> 96, at its real size
    onNodeWithTag("floating_dock").performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(1f, 1000f)) }
    waitForIdle()
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
    onNodeWithTag("dock_search").assertDoesNotExist()
    onNodeWithTag("dock_home").assertDoesNotExist()
    onNodeWithTag("dock_menu").assertDoesNotExist()
    onNodeWithTag("dock_toggle").performClick()
    waitForIdle()
    onNodeWithTag("dock_menu").assertExists()
    for (t in DockPages) onNodeWithTag("dock_menu_${t.name.lowercase()}").assertExists()
    onNodeWithTag("dock_menu_settings").assertDoesNotExist()
    onNodeWithTag("dock_menu_library").performClick()
    waitForIdle()
    assertEquals(listOf(TarkasNavTab.LIBRARY), h.selected)
    onNodeWithTag("dock_menu").assertDoesNotExist()
  }

  @Test fun compactMenuCascadesOnTheFrameClock() = runComposeUiTest {
    // The cascade is a tween delay, never a real delay: the scene settles and every row is present.
    val h = Host()
    setContent { h.Content(compact = true, reduce = false) }
    onNodeWithTag("dock_toggle").performClick()
    mainClock.advanceTimeBy(600)
    waitForIdle()
    for (t in DockPages) onNodeWithTag("dock_menu_${t.name.lowercase()}").assertExists()
  }
}

class IslandMachineTest {
  private fun IslandState.then(vararg e: IslandEvent) = e.fold(this) { s, x -> IslandMachine.next(s, x) }

  @Test fun escCloses() {
    assertEquals(IslandState.COMPACT, IslandState.COMPACT.then(IslandEvent.OPEN, IslandEvent.ESCAPE))
    assertEquals(IslandState.COMPACT, IslandState.COMPACT.then(IslandEvent.ESCAPE))
  }

  @Test fun openOnlyOpensFromCompact() {
    assertEquals(IslandState.NAVIGATION, IslandState.COMPACT.then(IslandEvent.OPEN))
    assertEquals(IslandState.NAVIGATION, IslandState.NAVIGATION.then(IslandEvent.OPEN))
  }

  @Test fun pageChangeOutsideAndLeaveCompact() {
    for (e in listOf(IslandEvent.NAVIGATED, IslandEvent.OUTSIDE, IslandEvent.LEAVE)) {
      assertEquals(IslandState.COMPACT, IslandState.NAVIGATION.then(e))
      assertEquals(IslandState.COMPACT, IslandState.COMPACT.then(e))
    }
  }

  @Test fun toggleIsTheNarrowList() {
    assertEquals(IslandState.NAVIGATION, IslandState.COMPACT.then(IslandEvent.TOGGLE))
    assertEquals(IslandState.COMPACT, IslandState.NAVIGATION.then(IslandEvent.TOGGLE))
  }

  @Test fun onlyTwoStatesAreLeft() {
    assertEquals(listOf(IslandState.COMPACT, IslandState.NAVIGATION), IslandState.entries.toList())
  }
}
