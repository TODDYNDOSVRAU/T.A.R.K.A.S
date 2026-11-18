package com.example.windows.geral

import java.io.File

/**
 * Start with Windows: one value under HKCU\Software\Microsoft\Windows\CurrentVersion\Run, so it needs no
 * administrator rights. Only an installed TARKAS.exe can register itself; a development run (java.exe) cannot,
 * and the settings screen says so instead of writing a wrong path.
 */
class Autostart(private val run: (List<String>) -> Pair<Int, String> = ::runProcess) {

  /** The installed executable of this process, or null when running from a JVM (development, tests). */
  fun installedExe(): File? =
    ProcessHandle.current().info().command().orElse(null)?.let(::File)?.takeIf { it.name.equals("TARKAS.exe", ignoreCase = true) }

  fun isEnabled(): Boolean = run(queryArgs()).let { (code, out) -> code == 0 && out.contains(VALUE, ignoreCase = true) }

  /** True when the registry now matches [on]. */
  fun set(on: Boolean): Boolean {
    if (!on) return run(deleteArgs()).first == 0 || !isEnabled()
    val exe = installedExe() ?: return false
    return run(addArgs(exe.absolutePath)).first == 0
  }

  companion object {
    const val KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    const val VALUE = "TARKAS"

    /** Windows starts the app minimised to the tray so it does not cover the desktop at sign-in. */
    const val MINIMIZED_FLAG = "--minimized"

    fun queryArgs() = listOf("reg", "query", KEY, "/v", VALUE)
    fun deleteArgs() = listOf("reg", "delete", KEY, "/v", VALUE, "/f")
    fun addArgs(exePath: String) = listOf("reg", "add", KEY, "/v", VALUE, "/t", "REG_SZ", "/d", "\"$exePath\" $MINIMIZED_FLAG", "/f")
  }
}

internal fun runProcess(cmd: List<String>): Pair<Int, String> = try {
  val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
  val out = p.inputStream.bufferedReader().readText()
  p.waitFor() to out
} catch (e: Exception) {
  -1 to (e.message ?: "")
}
