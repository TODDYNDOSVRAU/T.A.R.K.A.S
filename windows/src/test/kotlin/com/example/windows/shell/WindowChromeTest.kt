package com.example.windows.shell

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class WindowChromeTest {
  @Test fun darkBarIsAbsoluteBlackWithPrecisionWhiteText() {
    val c = WindowChrome.colors("dark", systemDark = false)
    assertEquals(0x080808, c.caption)          // 0x00BBGGRR of #080808 (gray: same either way)
    assertEquals(0xF0F2F2, c.text)             // #F2F2F0 as BGR
    assertTrue(c.dark)
  }

  @Test fun amoledIsPureBlackAndLightIsPaper() {
    assertEquals(0x000000, WindowChrome.colors("amoled", false).caption)
    val l = WindowChrome.colors("light", systemDark = true)
    assertEquals(0xE7E9E9, l.caption)          // #E9E9E7 as BGR
    assertEquals(0x080808, l.text)
    assertFalse(l.dark)
  }

  @Test fun systemModeFollowsWindows() {
    assertTrue(WindowChrome.colors("system", true).dark)
    assertFalse(WindowChrome.colors("system", false).dark)
  }

  @Test fun applyNeverThrowsOnAWindowWithoutPeer() {
    // No native peer yet: the call must report false, not crash the app.
    WindowChrome.apply(java.awt.Frame(), WindowChrome.colors("dark", true))
  }

  @Test fun appIconResourceLoads() { assertNotNull(AppIcon.painter()) }
}
