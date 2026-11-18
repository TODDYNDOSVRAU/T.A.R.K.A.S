package com.example.ui.theme

/** The saved theme choice: "system", "light", "dark" or "amoled". One place decides what each one means. */
object ThemeMode {
  const val SYSTEM = "system"
  const val LIGHT = "light"
  const val DARK = "dark"
  const val AMOLED = "amoled"

  /** Whether the app is drawn dark; `systemDark` is only read for "system" (and any unknown value). */
  fun isDark(mode: String, systemDark: () -> Boolean): Boolean = when (mode) {
    AMOLED, DARK -> true
    LIGHT -> false
    else -> systemDark()
  }

  fun isAmoled(mode: String): Boolean = mode == AMOLED
}
