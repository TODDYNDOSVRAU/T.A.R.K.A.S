package com.example.windows.player

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser

/**
 * Global media keys (play/pause, next, previous, stop) through RegisterHotKey on a dedicated message-loop thread.
 * Not SMTC: the system media overlay (WinRT) needs a packaged app identity, which the MSI/EXE build does not give;
 * documented in docs/WINDOWS-PLAYER.md. If another app owns a key, that key is skipped and [failed] lists it.
 */
class MediaKeys(private val c: PlayerController) : AutoCloseable {
  @Volatile private var thread: Thread? = null
  @Volatile private var threadId = 0
  val failed = mutableListOf<String>()

  fun start() {
    if (thread != null || !System.getProperty("os.name").startsWith("Windows")) return
    thread = Thread({ loop() }, "tarkas-mediakeys").apply { isDaemon = true; start() }
  }

  private fun loop() {
    val u = User32.INSTANCE
    threadId = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentThreadId()
    val keys = mapOf(1 to (0xB3 to "play/pause"), 2 to (0xB0 to "next"), 3 to (0xB1 to "previous"), 4 to (0xB2 to "stop"))
    keys.forEach { (id, k) -> if (!u.RegisterHotKey(null, id, WinUser.MOD_NOREPEAT, k.first)) failed += k.second }
    val msg = WinUser.MSG()
    while (u.GetMessage(msg, null, 0, 0) > 0) {
      if (msg.message == WinUser.WM_HOTKEY) when (msg.wParam.toInt()) {
        1 -> c.togglePlayPause()
        2 -> c.next()
        3 -> c.previous()
        4 -> c.seekTo(0).also { if (c.engine.value.playing) c.togglePlayPause() }
      }
    }
    keys.keys.forEach { u.UnregisterHotKey(null, it) }
  }

  override fun close() {
    val id = threadId
    if (id != 0) User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, null, null)
    thread = null
  }
}
