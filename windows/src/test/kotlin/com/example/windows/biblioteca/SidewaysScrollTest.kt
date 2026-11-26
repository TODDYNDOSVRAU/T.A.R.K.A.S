package com.example.windows.biblioteca

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A sideways row inside a page that scrolls up and down: each wheel or touchpad event goes to one axis only. */
@OptIn(ExperimentalTestApi::class)
class SidewaysScrollTest {
  @Test fun dominantAxisDecidesWhoScrolls() {
    assertEquals(3f, sidewaysDelta(3f, 1f, false)!!, 0f)
    assertEquals(-4f, sidewaysDelta(-4f, 2f, false)!!, 0f)
    assertNull(sidewaysDelta(1f, 3f, false))
    assertNull(sidewaysDelta(0f, 5f, false))
    assertNull(sidewaysDelta(2f, 2f, false))
    assertEquals(5f, sidewaysDelta(0f, 5f, true)!!, 0f)
    assertNull(sidewaysDelta(0f, 0f, true))
  }

  /** Vertical movement over the row scrolls the page and leaves the row; horizontal movement moves the row and leaves the page. */
  @Test fun verticalGoesToThePageAndHorizontalToTheRow() = runDesktopComposeUiTest(800, 600) {
    val page = androidx.compose.foundation.ScrollState(0)
    lateinit var row: androidx.compose.foundation.lazy.LazyListState
    setContent {
      row = rememberLazyListState()
      val scope = rememberCoroutineScope()
      Column(Modifier.size(800.dp, 600.dp).verticalScroll(page)) {
        LazyRow(state = row, modifier = Modifier.width(400.dp).height(200.dp).testTag("row").sidewaysScroll(row, scope, 600f)) {
          items((1..30).toList(), key = { it }) { Box(Modifier.size(192.dp)) }
        }
        Box(Modifier.size(800.dp, 2000.dp))
      }
    }
    waitForIdle()
    onNodeWithTag("row").performMouseInput { scroll(30f, ScrollWheel.Horizontal) }
    waitForIdle()
    mainClock.advanceTimeBy(1000); waitForIdle()
    assertTrue("row should have moved sideways", row.firstVisibleItemIndex > 0 || row.firstVisibleItemScrollOffset > 0)
    assertEquals("page must not move on a horizontal wheel", 0, page.value)
    val moved = row.firstVisibleItemIndex * 10000 + row.firstVisibleItemScrollOffset
    onNodeWithTag("row").performMouseInput { scroll(40f, ScrollWheel.Vertical) }
    waitForIdle()
    assertTrue("page should have scrolled, was ${page.value}", page.value > 0)
    assertEquals("row must not move on a vertical wheel", moved, row.firstVisibleItemIndex * 10000 + row.firstVisibleItemScrollOffset)
  }
}
