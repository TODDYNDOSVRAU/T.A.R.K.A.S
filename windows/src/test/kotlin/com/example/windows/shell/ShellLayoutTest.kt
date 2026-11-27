package com.example.windows.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.example.data.db.AppDatabase
import com.example.ui.theme.Grid
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Wave 0 of the Home v3 plan (owner, 2026-11-27): the island centred on the window whatever is open at the sides, no
 * player column, one Ajustes button at the top right, the corner at the bottom left, the Home title slot, Ctrl+F to
 * the Biblioteca. The whole shell is composed with a real [TarkasState]; the area panels are left out on purpose.
 */
@OptIn(ExperimentalTestApi::class)
class ShellLayoutTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private lateinit var state: TarkasState

  // Panels is process-wide: keep what other tests registered and give it back afterwards.
  private val slots = Panels.slots.toMap()
  private val titles = Panels.topTitle.toMap()
  private val corner = Panels.corner
  private val transport = Panels.transport
  private val sidePlayer = Panels.sidePlayer

  @Before fun setUp() {
    Panels.slots.clear(); Panels.topTitle.clear(); Panels.corner = null; Panels.transport = null; Panels.sidePlayer = null
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset()
    state = TarkasState(DesktopPlayer(), AppDatabase.inMemory(), scope = scope)
  }

  @After fun tearDown() {
    Panels.slots.clear(); Panels.slots.putAll(slots)
    Panels.topTitle.clear(); Panels.topTitle.putAll(titles)
    Panels.corner = corner; Panels.transport = transport; Panels.sidePlayer = sidePlayer
    SidePane.close(); ShellIsland.reset(); IslandActivity.reset()
    scope.cancel()
  }

  private fun ComposeUiTest.show() { setContent { TarkasShell(state, BackDispatcher(), AppLifecycle()) }; waitForIdle() }

  private fun ComposeUiTest.centreX(tag: String): Float {
    val b = onNodeWithTag(tag).getBoundsInRoot()
    return ((b.left + b.right) / 2).value
  }

  private fun ComposeUiTest.rootW() = onRoot().getBoundsInRoot().right.value

  @Test fun islandIsCentredOnTheWindowAtEveryWidth() {
    for (w in listOf(2560, 1920, 1280, 1024, 640)) runDesktopComposeUiTest(width = w, height = 720) {
      show()
      assertEquals("width $w", rootW() / 2, centreX("island_pill"), 1f)
    }
  }

  @Test fun narrowWindowCentresTheCompactToggle() = runDesktopComposeUiTest(width = 420, height = 720) {
    show()
    onNodeWithTag("dock_toggle").assertExists()
    assertEquals(rootW() / 2, centreX("floating_dock"), 1f)
  }

  @Test fun theSidePaneDoesNotPushTheIsland() = runDesktopComposeUiTest(width = 1280, height = 720) {
    show()
    SidePane.open("queue", "Fila") { Box(Modifier.fillMaxSize().testTag("pane_body")) }
    mainClock.advanceTimeBy(400)
    waitForIdle()
    onNodeWithTag("pane_body").assertExists()
    assertEquals(rootW() / 2, centreX("island_pill"), 1f)
  }

  @Test fun noPlayerColumnEvenWhenTheOldOneIsRegistered() = runDesktopComposeUiTest(width = 1280, height = 720) {
    Panels.sidePlayer = PanelContent { Box(Modifier.fillMaxSize().testTag("old_side_player")) }
    show()
    onNodeWithTag("side_player_slot").assertDoesNotExist()
    onNodeWithTag("old_side_player").assertDoesNotExist()
    // The content starts at the left margin of the window, not after a column.
    assertEquals(0f, onNodeWithTag("shell_content").getBoundsInRoot().left.value, 0.5f)
  }

  @Test fun oneAjustesButtonAtTheTopRightThatOpensAjustes() = runDesktopComposeUiTest(width = 1280, height = 720) {
    show()
    onAllNodesWithTag("top_settings").assertCountEquals(1)
    val b = onNodeWithTag("top_settings").getBoundsInRoot()
    assertEquals(48f, (b.right - b.left).value, 0.5f)
    assertEquals(rootW() - 24f, b.right.value, 0.5f)                  // 24 dp side margin
    assertTrue(b.bottom.value <= (Grid.u48 + Grid.u24).value)         // inside the top band
    onNodeWithTag("top_settings").performClick()
    waitForIdle()
    assertEquals(TarkasNavTab.SETTINGS, state.currentTab.value)
    // On Ajustes the island still opens and offers the five pages.
    onNodeWithTag("island_pill").performClick()
    waitForIdle()
    for (t in DockPages) onNodeWithTag("dock_${t.name.lowercase()}").assertExists()
    onNodeWithTag("dock_settings").assertDoesNotExist()
  }

  @Test fun theCornerSitsBottomLeftAndGrowsForTheCardOnTheHome() = runDesktopComposeUiTest(width = 1280, height = 800) {
    Panels.transport = PanelContent { Box(Modifier.fillMaxSize().testTag("transport_body")) }
    show()
    val home = onNodeWithTag("shell_corner").getBoundsInRoot()
    assertEquals(24f, home.left.value, 0.5f)
    assertEquals(800f - 24f, home.bottom.value, 0.5f)
    assertEquals(384f, (home.right - home.left).value, 0.5f)
    assertEquals(96f + 192f + 12f, (home.bottom - home.top).value, 0.5f)   // card + gap + mini-player
    val pill = onNodeWithTag("transport_body").getBoundsInRoot()
    assertEquals(96f, (pill.bottom - pill.top).value, 0.5f)                // the transport keeps 384 x 96 at the bottom
    assertEquals(home.bottom.value, pill.bottom.value, 0.5f)
    state.selectTab(TarkasNavTab.LIBRARY)
    waitForIdle()
    val lib = onNodeWithTag("shell_corner").getBoundsInRoot()
    assertEquals(96f, (lib.bottom - lib.top).value, 0.5f)                  // mini-player only off the Home
    // No band: the page runs to the bottom edge and the corner floats over it.
    assertEquals(800f, onNodeWithTag("shell_content").getBoundsInRoot().bottom.value, 0.5f)
  }

  @Test fun scrollingPagesPadTheirEndByTheCornerReserve() = runDesktopComposeUiTest(width = 1280, height = 800) {
    var reserve: androidx.compose.ui.unit.Dp? = null
    Panels.transport = PanelContent { Box(Modifier.fillMaxSize().testTag("transport_body")) }
    Panels.slots[TarkasNavTab.AUDIO] = PanelContent { reserve = com.example.ui.theme.LocalBarInset.current }
    state.selectTab(TarkasNavTab.AUDIO)
    show()
    assertEquals(Grid.u96 + Grid.u24, reserve)                             // 120 dp for a page's own end padding
  }

  @Test fun theFullPlayerPageHasNoCornerAndNoReserve() = runDesktopComposeUiTest(width = 1280, height = 800) {
    var reserve: androidx.compose.ui.unit.Dp? = null
    Panels.transport = PanelContent { Box(Modifier.fillMaxSize().testTag("transport_body")) }
    Panels.slots[TarkasNavTab.MUSIC] = PanelContent { reserve = com.example.ui.theme.LocalBarInset.current }
    state.selectTab(TarkasNavTab.MUSIC)
    show()
    onNodeWithTag("shell_corner").assertDoesNotExist()                     // Música carries its own transport
    assertEquals(androidx.compose.ui.unit.Dp(0f), reserve)
    state.selectTab(TarkasNavTab.LIBRARY)
    waitForIdle()
    onNodeWithTag("shell_corner").assertExists()                           // and it comes back on the other pages
  }

  @Test fun noCornerMeansNoBottomInset()= runDesktopComposeUiTest(width = 1280, height = 800) {
    show()
    onNodeWithTag("shell_corner").assertDoesNotExist()
    assertEquals(800f, onNodeWithTag("shell_content").getBoundsInRoot().bottom.value, 0.5f)
  }

  @Test fun aRegisteredCornerWinsOverTheTransportAndReadsItsInsets() = runDesktopComposeUiTest(width = 1280, height = 800) {
    var seen: CornerInsets? = null
    Panels.transport = PanelContent { Box(Modifier.fillMaxSize().testTag("transport_body")) }
    Panels.corner = PanelContent { seen = LocalCornerInsets.current; Box(Modifier.fillMaxSize().testTag("corner_body")) }
    show()
    onNodeWithTag("corner_body").assertExists()
    onNodeWithTag("transport_body").assertDoesNotExist()
    assertEquals(CornerInsets(Grid.u384 + Grid.u48, Grid.u96 + Grid.u24 + Grid.u192 + Grid.u12, true), seen)
    state.selectTab(TarkasNavTab.AUDIO)
    waitForIdle()
    assertEquals(CornerInsets(Grid.u384 + Grid.u48, Grid.u96 + Grid.u24, false), seen)
  }

  @Test fun aPageTitleReplacesTheRouteName() = runDesktopComposeUiTest(width = 1280, height = 720) {
    Panels.topTitle[TarkasNavTab.HOME] = PanelContent { Text("Boa noite", Modifier.testTag("greeting")) }
    show()
    onNodeWithTag("greeting").assertExists()
    val t = onNodeWithTag("top_title").getBoundsInRoot()
    assertEquals(384f, (t.right - t.left).value, 0.5f)                // (1280 - 403.2) / 2 leaves room for 384
    assertTrue("title clear of the island", t.right.value <= onNodeWithTag("dynamic_island").getBoundsInRoot().left.value)
    state.selectTab(TarkasNavTab.LIBRARY)
    waitForIdle()
    onNodeWithTag("greeting").assertDoesNotExist()                    // other pages keep their route name
  }

  @Test fun ctrlFGoesToTheBibliotecaAndAsksForItsSearch() = runDesktopComposeUiTest(width = 1280, height = 720) {
    show()
    SidePane.open("lyrics", "Letra") {}
    val before = ShellSearch.requests.value
    ShellSearch.openLibrary(state)
    waitForIdle()
    assertEquals(TarkasNavTab.LIBRARY, state.currentTab.value)
    assertEquals(before + 1, ShellSearch.requests.value)
    assertEquals(null, SidePane.current.value)
    // A second Ctrl+F on the Biblioteca only refocuses.
    ShellSearch.openLibrary(state)
    assertEquals(TarkasNavTab.LIBRARY, state.currentTab.value)
    assertEquals(before + 2, ShellSearch.requests.value)
  }
}
