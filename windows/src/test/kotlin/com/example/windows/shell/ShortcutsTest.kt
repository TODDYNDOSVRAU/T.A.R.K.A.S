package com.example.windows.shell

import androidx.compose.ui.input.key.Key
import com.example.ui.viewmodel.TarkasNavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {
  @Test fun ctrlChordsAreGlobal() {
    assertEquals(Command.Next, Shortcuts.global(Key.DirectionRight, ctrl = true, shift = false))
    assertEquals(Command.Previous, Shortcuts.global(Key.DirectionLeft, ctrl = true, shift = false))
    assertEquals(Command.Volume(0.05f), Shortcuts.global(Key.DirectionUp, ctrl = true, shift = false))
    assertEquals(Command.Search, Shortcuts.global(Key.F, ctrl = true, shift = false))
    assertEquals(Command.ToggleEq, Shortcuts.global(Key.E, ctrl = true, shift = false))
    assertEquals(Command.Go(TarkasNavTab.SETTINGS), Shortcuts.global(Key.Comma, ctrl = true, shift = false))
    assertEquals(Command.Back, Shortcuts.global(Key.Escape, ctrl = false, shift = false))
  }

  @Test fun digitsFollowSidebarOrder() {
    val keys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)
    keys.forEachIndexed { i, k -> assertEquals(Command.Go(NavOrder[i]), Shortcuts.global(k, ctrl = true, shift = false)) }
    assertEquals(6, NavOrder.toSet().size)
  }

  @Test fun bareKeysAreNotGlobalSoTextFieldsKeepThem() {
    assertNull(Shortcuts.global(Key.Spacebar, ctrl = false, shift = false))
    assertNull(Shortcuts.global(Key.DirectionRight, ctrl = false, shift = true))
    assertEquals(Command.PlayPause, Shortcuts.unhandled(Key.Spacebar, ctrl = false, shift = false))
    assertEquals(Command.Seek(5_000), Shortcuts.unhandled(Key.DirectionRight, ctrl = false, shift = true))
    assertEquals(Command.Seek(-5_000), Shortcuts.unhandled(Key.DirectionLeft, ctrl = false, shift = true))
    assertNull(Shortcuts.unhandled(Key.DirectionRight, ctrl = false, shift = false))
  }
}
