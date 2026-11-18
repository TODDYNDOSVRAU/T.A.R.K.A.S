package com.example.windows.geral

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray

/** Notification-area icon (the official logo, single colour, from `tarkas-tray.png`). Click or "Open" shows the window. */
@Composable
fun ApplicationScope.TarkasTray(onOpen: () -> Unit, onQuit: () -> Unit) {
  val icon = remember {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("tarkas-tray.png")
    stream?.use { BitmapPainter(loadImageBitmap(it)) }
  }
  if (icon != null) {
    Tray(
      icon = icon,
      tooltip = "T.A.R.K.A.S.",
      onAction = onOpen,
      menu = {
        Item(w("Abrir", "Open"), onClick = onOpen)
        Separator()
        Item(w("Sair", "Quit"), onClick = onQuit)
      }
    )
  }
}
