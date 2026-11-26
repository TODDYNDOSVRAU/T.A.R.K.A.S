package com.example.windows.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.example.ui.theme.ThemeMode
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary

/**
 * The window's title bar in the app's own colours, through DWM, on the native frame.
 * Why not an undecorated window with a drawn bar: that drops Snap Layouts, Aero Snap, the system menu,
 * the resize border and keyboard window commands, and each of them has to be rebuilt by hand. DWM keeps all
 * of it: the system draws the buttons (dark, with the red close on hover) and only the colours change.
 * Caption/text/border colours need Windows 11 (build 22000); on Windows 10 only the dark flag applies,
 * and a failure of any call leaves the standard bar. Colours are 0x00BBGGRR.
 */
object WindowChrome {
  /** Title bar and text colours as 0x00BBGGRR, plus whether the bar uses the dark system buttons. */
  data class Colors(val caption: Int, val text: Int, val dark: Boolean)

  private fun rgb(hex: Int): Int = ((hex and 0xFF) shl 16) or (hex and 0xFF00) or ((hex shr 16) and 0xFF)

  /** The bar matches the page behind it: Absolute Black (#080808), AMOLED #000000 or Paper #E9E9E7. */
  fun colors(mode: String, systemDark: Boolean): Colors = when {
    ThemeMode.isAmoled(mode) -> Colors(rgb(0x000000), rgb(0xF2F2F0), true)
    ThemeMode.isDark(mode) { systemDark } -> Colors(rgb(0x080808), rgb(0xF2F2F0), true)
    else -> Colors(rgb(0xE9E9E7), rgb(0x080808), false)
  }

  private interface Dwm : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attr: Int, value: IntByReference, size: Int): Int
  }

  private const val USE_IMMERSIVE_DARK_MODE = 20
  private const val BORDER_COLOR = 34
  private const val CAPTION_COLOR = 35
  private const val TEXT_COLOR = 36

  private val dwm: Dwm? by lazy {
    if (!System.getProperty("os.name").startsWith("Windows")) null
    else runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull()
  }

  /** Applies [c] to [window]; false when the platform does not support it. Never throws. */
  fun apply(window: java.awt.Window, c: Colors): Boolean {
    val api = dwm ?: return false
    return runCatching {
      val h = WinDef.HWND(Native.getWindowPointer(window) ?: return false)
      var ok = api.DwmSetWindowAttribute(h, USE_IMMERSIVE_DARK_MODE, IntByReference(if (c.dark) 1 else 0), 4) == 0
      // Border in the caption colour: no light 1 px edge around a dark window.
      ok = api.DwmSetWindowAttribute(h, CAPTION_COLOR, IntByReference(c.caption), 4) == 0 && ok
      api.DwmSetWindowAttribute(h, TEXT_COLOR, IntByReference(c.text), 4)
      api.DwmSetWindowAttribute(h, BORDER_COLOR, IntByReference(c.caption), 4)
      ok
    }.getOrDefault(false)
  }
}

/** Keeps [window]'s title bar in the saved theme, including "system" following Windows. */
@Composable
fun WindowChromeEffect(window: java.awt.Window, mode: String) {
  val systemDark = isSystemInDarkTheme()
  LaunchedEffect(window, mode, systemDark) { WindowChrome.apply(window, WindowChrome.colors(mode, systemDark)) }
}

/** The window and taskbar icon: the official logo from `tarkas-icon.png`; null (default icon) if the resource is missing. */
object AppIcon {
  fun painter(): androidx.compose.ui.graphics.painter.Painter? = runCatching {
    Thread.currentThread().contextClassLoader.getResourceAsStream("tarkas-icon.png")?.use {
      androidx.compose.ui.graphics.painter.BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
    }
  }.getOrNull()
}
