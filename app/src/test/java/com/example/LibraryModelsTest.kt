package com.example

import com.example.data.model.Track
import com.example.ui.screens.library.LibraryActions
import com.example.ui.screens.library.LibraryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryModelsTest {
  @Test
  fun `an empty state shows nothing and asks for nothing`() {
    val s = LibraryState()
    assertTrue(s.playlists.isEmpty() && s.allTracks.isEmpty() && s.deviceTracks.isEmpty() && s.queue.isEmpty())
    assertFalse(s.isPlaying || s.deviceScanning || s.hasMusicPermission || s.openQueue)
    assertEquals(null, s.currentTrack)
  }

  @Test
  fun `a state keeps what it is given and copy changes only that`() {
    val s = LibraryState(isPlaying = true, playCounts = mapOf("a" to 3))
    val t = s.copy(deviceScanning = true)
    assertTrue(t.isPlaying && t.deviceScanning)
    assertEquals(3, t.playCounts["a"])
  }

  @Test
  fun `default actions can all be called and do nothing`() {
    val a = LibraryActions()
    a.onScanDevice(); a.onClearQueue(); a.onQueueOpened(); a.onNeedPlayCounts()
    a.onRemoveQueueItem(0); a.onMoveQueueItem(0, 1); a.onSetFolderIncluded("x", true)
  }

  @Test
  fun `an action given is the one called`() {
    var called: String? = null
    val a = LibraryActions(onCreatePlaylist = { called = it })
    a.onCreatePlaylist("Rock")
    assertEquals("Rock", called)
  }
}
