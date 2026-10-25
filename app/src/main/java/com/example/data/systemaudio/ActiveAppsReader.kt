package com.example.data.systemaudio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One audio player that the system reports, as listed by `dumpsys audio`. */
data class PlayingApp(
  val uid: Int,
  val pid: Int,
  val playerType: String,
  val state: String,
  val usage: String?
) {
  val isStarted: Boolean get() = state.equals("started", ignoreCase = true)
}

/** What the Áudio screen shows in "Apps tocando agora". */
sealed interface ActiveAppsState {
  /** The DUMP permission was not granted yet (it can only be granted over adb). */
  data object PermissionMissing : ActiveAppsState

  data class Ready(val apps: List<ListedApp>) : ActiveAppsState

  data class Failed(val message: String) : ActiveAppsState
}

data class ListedApp(val label: String, val packageName: String?, val usage: String?)

object DumpsysAudioParser {

  private val playerLine = Regex(
    """AudioPlaybackConfiguration piid:\d+.*?type:(\S+)\s+u/pid:(\d+)/(\d+)\s+state:(\w+)(?:.*?usage=(\w+))?"""
  )

  /** Reads the `players:` entries of a `dumpsys audio` dump. Unknown lines are ignored. */
  fun parsePlayers(dump: String): List<PlayingApp> {
    return dump.lineSequence().mapNotNull { line ->
      playerLine.find(line)?.let { m ->
        PlayingApp(
          uid = m.groupValues[2].toInt(),
          pid = m.groupValues[3].toInt(),
          playerType = m.groupValues[1],
          state = m.groupValues[4],
          usage = m.groups[5]?.value
        )
      }
    }.toList()
  }
}

/**
 * Lists the apps that are playing audio right now by reading `dumpsys audio`. It only reads which
 * apps play; it does not capture or change their sound. Needs android.permission.DUMP, which
 * Android grants only over adb: `adb shell pm grant <package> android.permission.DUMP`.
 */
class ActiveAppsReader(private val context: Context) {

  companion object {
    private const val TAG = "ActiveAppsReader"
    private const val DUMP_TIMEOUT_MS = 4000L
  }

  fun hasPermission(): Boolean =
    context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

  suspend fun read(): ActiveAppsState {
    if (!hasPermission()) return ActiveAppsState.PermissionMissing
    return withContext(Dispatchers.IO) {
      try {
        val text = withTimeoutOrNull(DUMP_TIMEOUT_MS) {
          val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "audio"))
          process.inputStream.bufferedReader().use { it.readText() }
        } ?: return@withContext ActiveAppsState.Failed("O sistema demorou para responder.")

        val own = context.applicationInfo.uid
        val apps = DumpsysAudioParser.parsePlayers(text)
          .filter { it.isStarted && it.uid != own }
          .distinctBy { it.uid }
          .map { describe(it) }
        ActiveAppsState.Ready(apps)
      } catch (e: Exception) {
        Log.w(TAG, "dumpsys audio failed", e)
        ActiveAppsState.Failed("Não foi possível ler o estado do áudio.")
      }
    }
  }

  private fun describe(player: PlayingApp): ListedApp {
    val pm = context.packageManager
    val pkg = try {
      pm.getPackagesForUid(player.uid)?.firstOrNull()
    } catch (_: Exception) {
      null
    }
    val label = pkg?.let {
      try {
        pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString()
      } catch (_: Exception) {
        it
      }
    } ?: if (player.uid < 10000) "Sistema Android" else "App (uid ${player.uid})"
    return ListedApp(label = label, packageName = pkg, usage = player.usage)
  }
}
