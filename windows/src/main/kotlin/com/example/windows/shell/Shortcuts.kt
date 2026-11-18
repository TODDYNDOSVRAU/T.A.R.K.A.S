package com.example.windows.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import com.example.ui.theme.Grid
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.state.SettingsStore

/** Desktop shortcut map (docs/WINDOWS-DESKTOP.md §4). Pure: maps a key chord to a command, so it is unit-tested. */
sealed interface Command {
  data object PlayPause : Command
  data object Next : Command
  data object Previous : Command
  data class Seek(val deltaMs: Long) : Command
  data class Volume(val delta: Float) : Command
  data object Search : Command
  data class Go(val tab: TarkasNavTab) : Command
  data object Queue : Command
  data object Lyrics : Command
  data object ToggleEq : Command
  data object Back : Command
}

object Shortcuts {
  const val SEEK_MS = 5_000L
  const val VOLUME_STEP = 0.05f

  /**
   * Chords with Ctrl are global: they are read before any control (window preview), so a text field never eats them.
   * Bare keys (Space, Shift+arrows) are read after the focused control: a text field consumes them first, so typing a
   * space in the search field never pauses playback.
   */
  fun global(key: Key, ctrl: Boolean, shift: Boolean): Command? {
    if (!ctrl) return if (key == Key.Escape) Command.Back else null
    return when (key) {
      Key.DirectionRight -> Command.Next
      Key.DirectionLeft -> Command.Previous
      Key.DirectionUp -> Command.Volume(VOLUME_STEP)
      Key.DirectionDown -> Command.Volume(-VOLUME_STEP)
      Key.F -> Command.Search
      Key.Q -> Command.Queue
      Key.L -> Command.Lyrics
      Key.E -> Command.ToggleEq
      Key.Comma -> Command.Go(TarkasNavTab.SETTINGS)
      Key.One -> Command.Go(NavOrder[0]); Key.Two -> Command.Go(NavOrder[1]); Key.Three -> Command.Go(NavOrder[2])
      Key.Four -> Command.Go(NavOrder[3]); Key.Five -> Command.Go(NavOrder[4]); Key.Six -> Command.Go(NavOrder[5])
      else -> null
    }
  }

  fun unhandled(key: Key, ctrl: Boolean, shift: Boolean): Command? = when {
    ctrl -> null
    key == Key.Spacebar && !shift -> Command.PlayPause
    shift && key == Key.DirectionRight -> Command.Seek(SEEK_MS)
    shift && key == Key.DirectionLeft -> Command.Seek(-SEEK_MS)
    else -> null
  }

  fun global(e: KeyEvent) = if (e.type == KeyEventType.KeyDown) global(e.key, e.isCtrlPressed, e.isShiftPressed) else null
  fun unhandled(e: KeyEvent) = if (e.type == KeyEventType.KeyDown) unhandled(e.key, e.isCtrlPressed, e.isShiftPressed) else null
}

/** Window size, position and maximised state, kept in settings scope `window` between runs. */
object WindowMemory {
  const val SCOPE = "window"
  /** Smallest window: 960 x 576 (5 x 192 by 3 x 192), where the sidebar is already collapsed. Validated sizes start at 1024 x 640. */
  val MinSize = DpSize(Grid.u192 * 5, Grid.u192 * 3)

  fun restore(s: SettingsStore, fallback: DpSize): WindowState {
    val w = s.get(SCOPE, "w")?.toFloatOrNull(); val h = s.get(SCOPE, "h")?.toFloatOrNull()
    val x = s.get(SCOPE, "x")?.toFloatOrNull(); val y = s.get(SCOPE, "y")?.toFloatOrNull()
    val size = if (w != null && h != null && w > 0 && h > 0) DpSize(Dp(w), Dp(h)) else fallback
    val pos = if (x != null && y != null) WindowPosition(Dp(x), Dp(y)) else WindowPosition.PlatformDefault
    val placement = if (s.get(SCOPE, "max") == "1") WindowPlacement.Maximized else WindowPlacement.Floating
    return WindowState(placement = placement, position = pos, size = size)
  }

  fun save(s: SettingsStore, st: WindowState) {
    s.put(SCOPE, "max", if (st.placement == WindowPlacement.Maximized) "1" else "0")
    if (st.placement != WindowPlacement.Floating) return
    s.put(SCOPE, "w", st.size.width.value.toString()); s.put(SCOPE, "h", st.size.height.value.toString())
    val p = st.position
    if (p is WindowPosition.Absolute) { s.put(SCOPE, "x", p.x.value.toString()); s.put(SCOPE, "y", p.y.value.toString()) }
  }
}
