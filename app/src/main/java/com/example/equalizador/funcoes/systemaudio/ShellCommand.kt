package com.example.data.systemaudio

import android.util.Log
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs a command and reads its output with a real time limit. A blocking read cannot be cancelled by a
 * coroutine timeout, so the read runs on its own thread and the process is destroyed when time runs out
 * (a hung `su` prompt or a stuck `dumpsys`), which ends the read.
 */
internal object ShellCommand {
  private const val TAG = "ShellCommand"
  private val readers = Executors.newCachedThreadPool { r -> Thread(r, "tarkas-shell").apply { isDaemon = true } }

  /** The command's output; null on timeout, on a failure to start, or (with [requireSuccess]) a non-zero exit. */
  fun run(command: List<String>, timeoutMs: Long, requireSuccess: Boolean = false): String? {
    val process = try {
      ProcessBuilder(command).redirectErrorStream(true).start()
    } catch (e: Exception) {
      Log.w(TAG, "${command.first()} did not start: ${e.javaClass.simpleName}")
      return null
    }
    val output = readers.submit<String> { process.inputStream.bufferedReader().use { it.readText() } }
    return try {
      val text = output.get(timeoutMs, TimeUnit.MILLISECONDS)
      if (!requireSuccess || process.waitFor(1, TimeUnit.SECONDS) && process.exitValue() == 0) text else null
    } catch (e: TimeoutException) {
      Log.w(TAG, "${command.first()} timed out after $timeoutMs ms")
      null
    } catch (e: ExecutionException) {
      null
    } finally {
      process.destroy()
      output.cancel(true)
    }
  }
}
