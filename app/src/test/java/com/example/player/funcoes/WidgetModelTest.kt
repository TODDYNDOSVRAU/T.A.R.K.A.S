package com.example

import com.example.data.model.Track
import com.example.data.widget.WidgetModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {

  @Test fun noTrackIsEmptyAndNeverPlaying() {
    val m = WidgetModel.of(null, true)
    assertTrue(m.empty)
    assertFalse(m.playing)
    assertNull(m.title)
  }

  @Test fun trackShowsTitleArtistAndState() {
    val m = WidgetModel.of(Track("1", "Wave", "Tom", "Wave", 1000L, "g", "e"), true)
    assertFalse(m.empty)
    assertEquals("Wave", m.title)
    assertEquals("Tom", m.artist)
    assertTrue(m.playing)
  }

  @Test fun blankArtistFallsBackToTheHint() {
    val m = WidgetModel.of(Track("1", "Wave", "", "", 1000L, "g", "e"), false)
    assertNull(m.artist)
    assertEquals("Wave", m.title)
  }
}
