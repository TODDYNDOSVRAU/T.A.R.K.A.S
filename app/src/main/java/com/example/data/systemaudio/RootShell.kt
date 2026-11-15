package com.example.data.systemaudio

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs commands as root through `su` (Magisk, KernelSU and similar). The first call makes the
 * root manager ask the user to allow the app.
 */
object RootShell {
  private const val TAG = "RootShell"
  private val SU_PATHS = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/debug_ramdisk/su")

  /** A `su` binary exists. It does not mean the user allowed the app yet. */
  fun suPresent(): Boolean = SU_PATHS.any { File(it).exists() }

  /** Asks for root and checks it was granted (the command runs as uid 0). */
  suspend fun granted(): Boolean = run("id")?.contains("uid=0") == true

  /** Output of [command] run as root, or null when root is missing, refused or timed out. */
  suspend fun run(command: String, timeoutMs: Long = 8000): String? = withContext(Dispatchers.IO) {
    if (!suPresent()) return@withContext null
    ShellCommand.run(listOf("su", "-c", command), timeoutMs, requireSuccess = true)
  }

  /** Grants this app the DUMP permission, the same one ADB mode asks the user to grant from a computer. */
  suspend fun grantDump(packageName: String): Boolean =
    run("pm grant $packageName android.permission.DUMP") != null
}
