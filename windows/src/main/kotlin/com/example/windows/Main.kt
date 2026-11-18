package com.example.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.data.model.EqSettings
import com.example.data.db.AppDatabase
import com.example.ui.viewmodel.TarkasNavTab
import com.example.windows.audio.DesktopPlayer
import com.example.windows.base.AppLifecycle
import com.example.windows.geral.TarkasTray
import com.example.windows.base.BackDispatcher
import com.example.windows.shell.PanelContent
import com.example.windows.shell.Panels
import com.example.windows.shell.TarkasShell
import com.example.windows.state.TarkasState
import java.awt.FileDialog
import java.io.File

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
  fun quit() { com.example.windows.player.PlayerHost.shutdown(); player.stop(); db.close(); exitApplication() }
  if (trayOn) TarkasTray(onOpen = { visible = true }, onQuit = ::quit)
  Window(
    visible = visible,
    onCloseRequest = { if (trayOn && closeToTray) visible = false else quit() },
    title = "T.A.R.K.A.S.",
    state = rememberWindowState(size = DpSize(1152.dp, 768.dp)),
    onPreviewKeyEvent = { e ->
      if (e.type != KeyEventType.KeyDown) false
      else when (e.key) {
        Key.Spacebar -> if (state.currentTab.value == TarkasNavTab.MUSIC) { com.example.windows.player.PlayerHost.controller(state).togglePlayPause(); true } else false
        Key.Escape -> back.dispatch() || state.goBack()
        else -> false
      }
    },
  ) {
    val win = window
    remember {
      com.example.windows.player.PlayerHost.register(Panels.slots) // win-player
      com.example.windows.biblioteca.BibliotecaHost.register(Panels.slots) // win-biblioteca
      com.example.windows.equalizador.AudioHost.register(Panels.slots) // win-audio
      win.addWindowFocusListener(object : java.awt.event.WindowAdapter() {
        override fun windowGainedFocus(e: java.awt.event.WindowEvent?) = lifecycle.setResumed(true)
        override fun windowLostFocus(e: java.awt.event.WindowEvent?) = lifecycle.setResumed(false)
      })
    }
    geral.Gate(skipBoot = startMinimized) { TarkasShell(state, back, lifecycle) }
  }
}

private fun mmss(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

/** Playback slice of the port: open a file, play/pause (space), seek, EQ on/off. Other screens: pending. */
@Composable
private fun PlayerPane(player: DesktopPlayer, window: java.awt.Frame) {
  val c = MaterialTheme.colorScheme
  val s by player.state.collectAsState()
  var eqOn by remember { mutableStateOf(true) }
  var dragging by remember { mutableStateOf<Float?>(null) }
  Column(
    Modifier.fillMaxSize().background(c.background).padding(48.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    Text("T.A.R.K.A.S.", color = c.onBackground, fontSize = 24.sp)
    Text(s.file?.nameWithoutExtension ?: "Nenhuma faixa aberta", color = c.onBackground, fontSize = 18.sp)
    s.error?.let { Text(it, color = c.primary, fontSize = 12.sp) }
    Column(Modifier.fillMaxWidth()) {
      Slider(
        value = dragging ?: if (s.durationMs > 0) s.positionMs.toFloat() / s.durationMs else 0f,
        onValueChange = { dragging = it },
        onValueChangeFinished = { dragging?.let { player.seek((it * s.durationMs).toLong()) }; dragging = null },
        enabled = s.durationMs > 0,
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(mmss(s.positionMs), color = c.onSurfaceVariant, fontSize = 12.sp)
        Text(mmss(s.durationMs), color = c.onSurfaceVariant, fontSize = 12.sp)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
      OutlinedButton(onClick = {
        val d = FileDialog(window, "Abrir faixa", FileDialog.LOAD).apply { file = "*.wav;*.mp3;*.flac;*.aiff"; isVisible = true }
        if (d.file != null) player.play(File(d.directory, d.file))
      }, modifier = Modifier.height(48.dp)) { Text("Abrir") }
      Button(onClick = player::togglePause, enabled = s.file != null, modifier = Modifier.height(48.dp)) {
        Text(if (s.playing) "Pausar" else "Tocar")
      }
      Text("EQ", color = c.onBackground, fontSize = 18.sp, modifier = Modifier.padding(start = 24.dp))
      Switch(checked = eqOn, onCheckedChange = { eqOn = it; player.setEq(EqSettings(enabled = it)) })
    }
    Text("Espaço: tocar/pausar. Equalizador completo, biblioteca e demais telas: porte pendente.", color = c.onSurfaceVariant, fontSize = 12.sp)
  }
}
