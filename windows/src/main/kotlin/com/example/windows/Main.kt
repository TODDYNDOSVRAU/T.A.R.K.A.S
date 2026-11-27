package com.example.windows

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.data.db.AppDatabase
import com.example.ui.theme.Grid
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.base.BackDispatcher
import com.example.windows.geral.TarkasTray
import com.example.windows.player.PlayerHost
import com.example.windows.shell.Command
import com.example.windows.shell.Panels
import com.example.windows.shell.ShellSearch
import com.example.windows.shell.Shortcuts
import com.example.windows.shell.SidePane
import com.example.windows.shell.TarkasShell
import com.example.windows.shell.WindowMemory
import com.example.windows.state.TarkasState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

@OptIn(FlowPreview::class)
fun main(args: Array<String>) = application {
  val db = remember { AppDatabase.open() }
  val player = remember { DesktopPlayer() }
  val state = remember { TarkasState(player, db) }
  val back = remember { BackDispatcher() }
  val lifecycle = remember { AppLifecycle() }
  val geral = remember { com.example.windows.geral.GeralPanels.create(state).also { it.services.diagnostics.installCrashHandler(); it.install() } } // win-geral
  val trayOn by geral.services.geral.tray.collectAsState()
  val closeToTray by geral.services.geral.closeToTray.collectAsState()
  // Start with Windows passes --minimized: hidden in the tray, boot skipped. Without a tray icon the window would be unreachable, so it shows.
  val startMinimized = remember { com.example.windows.geral.Autostart.MINIMIZED_FLAG in args && trayOn }
  var visible by remember { mutableStateOf(!startMinimized) }
  // 1152 x 768 = 6 x 192 by 4 x 192 on first run; afterwards the last size, position and maximised state.
  val windowState = remember { WindowMemory.restore(state.settings, DpSize(Grid.u192 * 6, Grid.u192 * 4)) }
  fun quit() { WindowMemory.save(state.settings, windowState); PlayerHost.shutdown(); player.stop(); db.close(); exitApplication() }
  if (trayOn) TarkasTray(onOpen = { visible = true }, onQuit = ::quit)

  fun run(cmd: Command): Boolean {
    val pc = PlayerHost.controller(state)
    when (cmd) {
      Command.PlayPause -> pc.togglePlayPause()
      Command.Next -> pc.next()
      Command.Previous -> pc.previous()
      is Command.Seek -> pc.seekBy(cmd.deltaMs)
      is Command.Volume -> pc.volumeBy(cmd.delta)
      // Ctrl+F: the Biblioteca keeps its own field; everywhere else the search of the dock opens.
      Command.Search -> ShellSearch.openLibrary(state)
      is Command.Go -> { SidePane.close(); state.selectTab(cmd.tab) }
      Command.Queue -> Panels.toggleQueue?.invoke() ?: return false
      Command.Lyrics -> Panels.toggleLyrics?.invoke() ?: return false
      Command.ToggleEq -> state.setEqEnabled(!state.eqSettings.value.enabled)
      // Esc: the open popup (back stack) first, then the side pane, then the route.
      Command.Back -> return back.dispatch() || Panels.closeOverlay?.invoke() == true || SidePane.close() || ShellSearch.close() || state.goBack()
    }
    return true
  }

  Window(
    visible = visible,
    onCloseRequest = { if (trayOn && closeToTray) { WindowMemory.save(state.settings, windowState); visible = false } else quit() },
    title = "T.A.R.K.A.S.",
    icon = remember { com.example.windows.shell.AppIcon.painter() },
    state = windowState,
    onPreviewKeyEvent = { e -> Shortcuts.global(e)?.let(::run) ?: false },
    onKeyEvent = { e -> Shortcuts.unhandled(e)?.let(::run) ?: false },
  ) {
    val win = window
    com.example.windows.shell.WindowChromeEffect(win, state.themeMode.collectAsState().value)
    remember {
      win.minimumSize = java.awt.Dimension(WindowMemory.MinSize.width.value.toInt(), WindowMemory.MinSize.height.value.toInt())
      PlayerHost.register(Panels.slots) // win-player
      com.example.windows.biblioteca.BibliotecaHost.register(Panels.slots) // win-biblioteca
      com.example.windows.equalizador.AudioHost.register(Panels.slots) // win-audio
      com.example.windows.equalizador.AudioHost.registerIsland(state) // win-audio: output icon on the Island
      com.example.windows.assistente.AssistentePanels.create(state).install(Panels.slots) // win-assistente: the ASSISTANT page
      win.addWindowFocusListener(object : java.awt.event.WindowAdapter() {
        override fun windowGainedFocus(e: java.awt.event.WindowEvent?) = lifecycle.setResumed(true)
        override fun windowLostFocus(e: java.awt.event.WindowEvent?) = lifecycle.setResumed(false)
      })
    }
    // Keep the window geometry on disk while it changes, so a crash or a forced shutdown does not lose it.
    LaunchedEffect(windowState) {
      snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
        .debounce(500).collect { WindowMemory.save(state.settings, windowState) }
    }
    geral.Gate(skipBoot = startMinimized) { TarkasShell(state, back, lifecycle) }
  }
}
