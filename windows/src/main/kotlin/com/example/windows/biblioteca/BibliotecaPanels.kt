package com.example.windows.biblioteca

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.player.PlayerHost
import com.example.windows.shell.PanelContent
import com.example.windows.state.TarkasState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** One [BibliotecaState] per run, built on the shared player; registers the Início and Biblioteca slots of the shell. */
object BibliotecaHost {
  @Volatile private var s: BibliotecaState? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  fun state(app: TarkasState): BibliotecaState = s ?: synchronized(this) {
    s ?: BibliotecaState(app, PlayerHost.controller(app), scope).also { s = it }
  }

  fun register(slots: MutableMap<TarkasNavTab, PanelContent>) {
    slots[TarkasNavTab.HOME] = PanelContent { app -> HomeScreen(state(app), Modifier.fillMaxSize()) }
    slots[TarkasNavTab.LIBRARY] = PanelContent { app -> LibraryScreen(state(app), Modifier.fillMaxSize()) }
  }
}
