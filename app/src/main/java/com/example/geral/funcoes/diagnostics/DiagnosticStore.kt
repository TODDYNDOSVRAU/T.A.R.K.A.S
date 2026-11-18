package com.example.data.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** One saved crash: when it happened and the text that was written at that moment. */
data class CrashEntry(val atMs: Long, val text: String)

/**
 * Where failures are kept on this device: the last [MAX_CRASHES] crashes, one file each, and a short
 * log of failures the app survived (a restore that could not be read, an effect that fell back).
 * Nothing here leaves the device unless the user exports it. Plain files, so it works while the
 * process is dying and can be tested without Android.
 */
class DiagnosticStore(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {
  private val eventsFile get() = File(dir, EVENTS_FILE)

  /** Writes the crash and drops the oldest ones beyond [MAX_CRASHES]. Never throws. */
  @Synchronized
  fun saveCrash(threadName: String, error: Throwable, header: String = "") {
    try {
      dir.mkdirs()
      var at = now()
      while (File(dir, crashName(at)).exists()) at++
      val body = buildString {
        if (header.isNotBlank()) appendLine(header.trimEnd())
        appendLine("thread: $threadName")
        append(stackOf(error))
      }
      File(dir, crashName(at)).writeText(body)
      crashFiles().drop(MAX_CRASHES).forEach { it.delete() }
    } catch (_: Throwable) {
      // The process is already going down; a failed write must not hide the original crash.
    }
  }

  /** Saved crashes, newest first. */
  @Synchronized
  fun crashes(): List<CrashEntry> = crashFiles().mapNotNull { file ->
    val at = file.name.removePrefix(CRASH_PREFIX).removeSuffix(SUFFIX).toLongOrNull() ?: return@mapNotNull null
    runCatching { CrashEntry(at, file.readText()) }.getOrNull()
  }

  /** Adds one line (plus the stack, when there is one) to the event log. Never throws. */
  @Synchronized
  fun record(tag: String, message: String, error: Throwable? = null) {
    try {
      dir.mkdirs()
      val line = buildString {
        append(now()).append(" | ").append(oneLine(tag)).append(" | ").append(oneLine(message))
        if (error != null) append('\n').append(stackOf(error).trimEnd().prependIndent("    "))
        append('\n')
      }
      eventsFile.appendText(line)
      if (eventsFile.length() > MAX_EVENT_BYTES) trimEvents()
    } catch (_: Throwable) {
    }
  }

  /** The event log, oldest first. */
  @Synchronized
  fun events(): String = runCatching { eventsFile.takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")

  @Synchronized
  fun clear() {
    dir.listFiles()?.forEach { it.delete() }
  }

  private fun crashFiles(): List<File> =
    dir.listFiles { f -> f.name.startsWith(CRASH_PREFIX) && f.name.endsWith(SUFFIX) }
      ?.sortedByDescending { it.name.removePrefix(CRASH_PREFIX).removeSuffix(SUFFIX).toLongOrNull() ?: 0L }
      .orEmpty()

  /** Keeps the newer half, cut at a line start, so the file never grows without limit. */
  private fun trimEvents() {
    val text = eventsFile.readText()
    val from = text.indexOf('\n', text.length / 2)
    eventsFile.writeText(if (from < 0) "" else text.substring(from + 1))
  }

  private fun crashName(at: Long) = "$CRASH_PREFIX$at$SUFFIX"

  companion object {
    const val MAX_CRASHES = 5
    const val MAX_EVENT_BYTES = 64 * 1024L
    private const val CRASH_PREFIX = "crash-"
    private const val SUFFIX = ".txt"
    private const val EVENTS_FILE = "events.log"

    fun stackOf(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    private fun oneLine(text: String) = text.replace('\n', ' ').replace('\r', ' ')
  }
}

/**
 * Saves the crash, then hands it to whoever was handling crashes before (the system's handler,
 * which shows the dialog and ends the process). The previous handler always runs.
 */
class CrashHandler(
  private val store: DiagnosticStore,
  private val header: () -> String,
  private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
  override fun uncaughtException(thread: Thread, error: Throwable) {
    try {
      store.saveCrash(thread.name, error, runCatching(header).getOrDefault(""))
    } catch (_: Throwable) {
    } finally {
      previous?.uncaughtException(thread, error)
    }
  }
}

/** Removes anything that could be a credential from text that is about to leave the app. */
object Redaction {
  private val keyParam = Regex("(?i)\\b((?:api[_-]?)?key|token|access_token|authorization)=[^&\\s\"']+")
  private val bearer = Regex("(?i)\\b(Bearer)\\s+[A-Za-z0-9._~+/=-]{8,}")

  fun scrub(text: String, secrets: List<String> = emptyList()): String {
    var out = text
    secrets.filter { it.length >= 6 }.forEach { out = out.replace(it, REMOVED) }
    out = keyParam.replace(out) { "${it.groupValues[1]}=$REMOVED" }
    out = bearer.replace(out) { "${it.groupValues[1]} $REMOVED" }
    return out
  }

  const val REMOVED = "[removed]"
}
