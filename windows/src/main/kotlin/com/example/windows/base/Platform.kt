package com.example.windows.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.ui.theme.Grid
import com.example.ui.theme.GridText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

// Platform shims (win-base). Screens ported from Android call these instead of Android APIs:
//   ClipboardManager -> DesktopClipboard     Toast/Snackbar -> Toaster + ToastHost
//   BackHandler      -> BackHandler          Lifecycle      -> AppLifecycle + LifecycleEffect

/** System clipboard (text). */
object DesktopClipboard {
  fun copy(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
  }

  fun paste(): String? = runCatching {
    val cb = Toolkit.getDefaultToolkit().systemClipboard
    if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) cb.getData(DataFlavor.stringFlavor) as String else null
  }.getOrNull()
}

/** A short message with an optional action. */
data class Notice(
  val text: String,
  val actionLabel: String? = null,
  val action: (() -> Unit)? = null,
  val id: Long = System.nanoTime()
)

/** Owns the one visible message. [show] replaces the current one; [ToastHost] draws it and dismisses it. */
class Toaster {
  private val _notice = MutableStateFlow<Notice?>(null)
  val notice: StateFlow<Notice?> = _notice.asStateFlow()
  fun show(notice: Notice) { _notice.value = notice }
  fun show(text: String) = show(Notice(text))
  fun dismiss(id: Long) { if (_notice.value?.id == id) _notice.value = null }
}

val LocalToaster = compositionLocalOf { Toaster() }

/** Draws the current notice at the bottom of the window, on the T.A.L. grid. */
@Composable
fun ToastHost(toaster: Toaster, durationMs: Long = 4000, modifier: Modifier = Modifier) {
  val notice by toaster.notice.collectAsState()
  val n = notice
  LaunchedEffect(n?.id) { if (n != null) { delay(durationMs); toaster.dismiss(n.id) } }
  Box(modifier.fillMaxSize().padding(Grid.margin), contentAlignment = Alignment.BottomCenter) {
    if (n != null) {
      Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, Grid.r12).padding(horizontal = Grid.inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Grid.u12)
      ) {
        Text(n.text, style = GridText.Body, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = Grid.u12))
        val act = n.action
        if (n.actionLabel != null && act != null) {
          Text(
            n.actionLabel, style = GridText.BodyStrong, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { act(); toaster.dismiss(n.id) }.padding(Grid.u12)
          )
        }
      }
    }
  }
}

/** Stack of active back handlers; the shell sends Esc, Alt+Left and the mouse back button to [dispatch]. */
class BackDispatcher {
  class Entry internal constructor(var enabled: Boolean, var onBack: () -> Unit)

  private val handlers = ArrayList<Entry>()
  internal fun register(enabled: Boolean, onBack: () -> Unit): Entry = Entry(enabled, onBack).also { handlers += it }
  internal fun unregister(e: Entry) { handlers.remove(e) }

  /** Calls the most recently added enabled handler. True when one handled it. */
  fun dispatch(): Boolean {
    val e = handlers.lastOrNull { it.enabled } ?: return false
    e.onBack()
    return true
  }
}

val LocalBackDispatcher = compositionLocalOf { BackDispatcher() }

/** Same contract as androidx.activity.compose.BackHandler. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
  val dispatcher = LocalBackDispatcher.current
  val current by rememberUpdatedState(onBack)
  val entry = remember(dispatcher) { dispatcher.register(enabled) { current() } }
  SideEffect { entry.enabled = enabled }
  DisposableEffect(dispatcher) { onDispose { dispatcher.unregister(entry) } }
}

/** Window lifecycle: the desktop twin of Activity onResume/onPause, fed by the shell from window focus. */
class AppLifecycle {
  private val _resumed = MutableStateFlow(true)
  val resumed: StateFlow<Boolean> = _resumed.asStateFlow()
  fun setResumed(value: Boolean) { _resumed.value = value }
}

val LocalAppLifecycle = compositionLocalOf { AppLifecycle() }

/** Runs [onResume] whenever the window has focus (including now) and [onPause] when it loses it. */
@Composable
fun LifecycleEffect(onResume: () -> Unit = {}, onPause: () -> Unit = {}) {
  val resumed by LocalAppLifecycle.current.resumed.collectAsState()
  val r by rememberUpdatedState(onResume)
  val p by rememberUpdatedState(onPause)
  LaunchedEffect(resumed) { if (resumed) r() else p() }
}

/** Provides the shims to a screen tree. The shell calls this once. */
@Composable
fun ProvidePlatform(toaster: Toaster, back: BackDispatcher, lifecycle: AppLifecycle, content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalToaster provides toaster,
    LocalBackDispatcher provides back,
    LocalAppLifecycle provides lifecycle,
    content = content
  )
}
