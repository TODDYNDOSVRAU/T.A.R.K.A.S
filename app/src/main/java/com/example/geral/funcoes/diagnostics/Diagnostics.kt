package com.example.data.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The app's own failure record. [install] once per process; after that every uncaught crash is
 * saved before the system ends the process, and [record] keeps a line for failures the app
 * survived. [report] is the text the user can export from Ajustes. Nothing is sent anywhere.
 */
object Diagnostics {
  private const val TAG = "TarkasDiagnostics"
  private const val MAX_PENDING = 50

  @Volatile private var store: DiagnosticStore? = null
  private val pending = ArrayDeque<Triple<String, String, Throwable?>>()
  private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "diagnostics").apply { isDaemon = true } }

  @Synchronized
  fun install(context: Context) {
    if (store != null) return
    val app = context.applicationContext
    val created = DiagnosticStore(File(app.filesDir, "diagnostics"))
    store = created
    Thread.setDefaultUncaughtExceptionHandler(
      CrashHandler(created, { header(app) }, Thread.getDefaultUncaughtExceptionHandler())
    )
    val waiting = pending.toList()
    pending.clear()
    waiting.forEach { (tag, message, error) -> writer.execute { created.record(tag, message, error) } }
  }

  /**
   * Notes a failure the app recovered from. Safe from any thread, the audio thread included: the
   * file is written on a thread of its own. Before [install], the last few are held in memory.
   */
  fun record(tag: String, message: String, error: Throwable? = null) {
    runCatching { Log.w(TAG, "$tag: $message", error) }
    val target = store
    if (target != null) {
      writer.execute { target.record(tag, message, error) }
    } else {
      synchronized(this) {
        if (pending.size >= MAX_PENDING) pending.removeFirst()
        pending.addLast(Triple(tag, message, error))
      }
    }
  }

  /** How many crashes are saved; the settings row shows it. */
  fun crashCount(): Int = store?.crashes()?.size ?: 0

  /** The exportable text: version, device, why the process ended lately, crashes, events. No credentials. */
  fun report(context: Context): String {
    val app = context.applicationContext
    val saved = store
    val text = buildString {
      appendLine("T.A.R.K.A.S. diagnostics")
      appendLine("exported: ${stamp(System.currentTimeMillis())}")
      appendLine(header(app))
      appendLine()
      appendLine("== Process exits ==")
      appendLine(exitReasons(app).ifBlank { "none recorded (needs Android 11 or newer)" })
      appendLine()
      val crashes = saved?.crashes().orEmpty()
      appendLine("== Crashes (${crashes.size}, newest first, at most ${DiagnosticStore.MAX_CRASHES}) ==")
      if (crashes.isEmpty()) appendLine("none")
      crashes.forEach {
        appendLine("-- ${stamp(it.atMs)} --")
        appendLine(it.text.trimEnd())
      }
      appendLine()
      appendLine("== Events (timestamps in ms since epoch) ==")
      appendLine(saved?.events()?.trimEnd().orEmpty().ifBlank { "none" })
    }
    return Redaction.scrub(text, secrets = listOf(BuildConfig.GEMINI_API_KEY))
  }

  private fun header(app: Context): String = buildString {
    appendLine("app: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}")
    appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    append("locale: ${Locale.getDefault().toLanguageTag()}")
  }

  private fun exitReasons(app: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
    return runCatching {
      val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
      manager.getHistoricalProcessExitReasons(null, 0, 5).joinToString("\n") { info ->
        "${stamp(info.timestamp)} reason=${info.reason} status=${info.status} importance=${info.importance} ${info.description.orEmpty()}".trimEnd()
      }
    }.getOrDefault("")
  }

  private fun stamp(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(ms))
}
