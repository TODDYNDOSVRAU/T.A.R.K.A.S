package com.example.windows.biblioteca

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.GridText
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.player.PlayerHost
import com.example.windows.shell.PanelContent
import com.example.windows.shell.Panels
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
    slots[TarkasNavTab.HOME] = PanelContent { app ->
      // The queue stack registers late (win-player), so it is read here, at each composition.
      val stack = Panels.queueStack
      HomeScreen(state(app), Modifier.fillMaxSize(), queueStack = stack?.let { s -> { s.Show(app) } })
    }
    slots[TarkasNavTab.LIBRARY] = PanelContent { app -> LibraryScreen(state(app), Modifier.fillMaxSize()) }
    // The Início greeting takes the place of the route name at the top left of the shell.
    Panels.topTitle[TarkasNavTab.HOME] = PanelContent { app -> HomeGreeting(app) }
  }
}

/** "Bom dia, Ana" in the shell's title style: the nickname, else the Windows account name (the app's `displayName`). */
@Composable
internal fun HomeGreeting(app: TarkasState) {
  val name by app.displayName.collectAsState()
  val hour = remember { java.time.LocalTime.now().hour }
  Text(
    greeting(hour, name), style = GridText.Title, color = MaterialTheme.colorScheme.onSurface,
    maxLines = 1, overflow = TextOverflow.Ellipsis,
  )
}
