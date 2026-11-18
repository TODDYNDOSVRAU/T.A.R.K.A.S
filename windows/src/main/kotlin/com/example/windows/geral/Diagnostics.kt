package com.example.windows.geral

import com.example.data.diagnostics.CrashHandler
import com.example.data.diagnostics.DiagnosticStore
import com.example.data.diagnostics.Redaction
import java.io.File

/**
 * Diagnostics for the ENGINEERING layer: the shared [DiagnosticStore] (last crashes, recovered errors) kept under
 * %APPDATA%\TARKAS\diagnostics, a crash handler for the JVM, and the exportable report. No music, playlists or keys.
 */
class Diagnostics(val dir: File = defaultDir(), private val appVersion: String = APP_VERSION) {
  val store = DiagnosticStore(dir)

  /** Saves any uncaught crash before the JVM's own handler runs. */
  fun installCrashHandler() {
    Thread.setDefaultUncaughtExceptionHandler(CrashHandler(store, ::header, Thread.getDefaultUncaughtExceptionHandler()))
  }

  fun header(): String =
    "T.A.R.K.A.S. $appVersion · Windows ${System.getProperty("os.version")} (${System.getProperty("os.arch")}) · Java ${System.getProperty("java.version")}"

  fun report(secrets: List<String> = emptyList()): String = Redaction.scrub(buildString {
    appendLine(header())
    val crashes = store.crashes()
    appendLine("Crashes: ${crashes.size}")
    crashes.forEach { appendLine("--- ${java.time.Instant.ofEpochMilli(it.atMs)}"); appendLine(it.text) }
    appendLine("Events:")
    append(store.events())
  }, secrets)

  fun export(to: File, secrets: List<String> = emptyList()) = to.writeText(report(secrets))

  companion object {
    const val APP_VERSION = "2.1"

    fun defaultDir(): File {
      val base = System.getenv("APPDATA")?.let { File(it, "TARKAS") } ?: File(System.getProperty("user.home"), ".tarkas")
      return File(base, "diagnostics").also { it.mkdirs() }
    }
  }
}
