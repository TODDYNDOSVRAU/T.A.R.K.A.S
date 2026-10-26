package com.example.data.systemaudio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Applies the app's equalizer curve to other apps (ADB and root modes). Every couple of seconds it
 * reads which players are active and their audio sessions from `dumpsys audio` (DUMP permission, or
 * `su` in root mode) and attaches an effect to each one; players that announce their session with
 * Android's open-session broadcast are picked up too. Runs as a foreground service so it keeps
 * working with the app closed.
 */
class GlobalEqService : Service() {

  companion object {
    private const val TAG = "GlobalEqService"
    private const val CHANNEL_ID = "tarkas_global_eq"
    private const val NOTIFICATION_ID = 4107
    private const val POLL_MS = 2000L
    /** The output mix: effects there reach sounds that have no session of their own. */
    private const val GLOBAL_SESSION = 0

    fun start(context: Context) {
      try {
        ContextCompat.startForegroundService(context, Intent(context, GlobalEqService::class.java))
      } catch (e: Exception) {
        Log.w(TAG, "Could not start the global equalizer", e)
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, GlobalEqService::class.java))
    }
  }

  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private val equalizer = SessionEqualizer()
  /** Sessions announced by players with ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION. */
  private val announced = mutableMapOf<Int, String>()
  private var rootDump = false

  private val sessionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      val session = intent?.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1) ?: return
      val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: return
      if (session <= 0 || pkg == packageName) return
      when (intent.action) {
        AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
          announced[session] = pkg
          equalizer.attach(session)
        }
        AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
          announced.remove(session)
          equalizer.detach(session)
        }
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
      } else {
        startForeground(NOTIFICATION_ID, notification())
      }
    } catch (e: Exception) {
      Log.w(TAG, "startForeground failed", e)
      stopSelf()
      return
    }

    val filter = IntentFilter().apply {
      addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
      addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
    }
    // Players in other apps send these, so the receiver must be exported.
    ContextCompat.registerReceiver(this, sessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

    scope.launch { GlobalEq.settings.collect { equalizer.update(it) } }
    scope.launch {
      if (GlobalEq.reach.value == EqReach.ROOT) prepareRoot()
      while (isActive) {
        poll()
        delay(POLL_MS)
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

  /** Root mode: grant DUMP to ourselves; if that fails, read dumpsys through su each time. */
  private suspend fun prepareRoot() {
    if (!hasDump() && RootShell.granted()) {
      if (!RootShell.grantDump(packageName)) rootDump = true
    }
  }

  private fun hasDump() = checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED

  private suspend fun poll() {
    val reach = GlobalEq.reach.value
    val dump: String? = when {
      hasDump() -> readDumpsys()
      reach == EqReach.ROOT && rootDump -> RootShell.run("dumpsys audio")
      else -> null
    }
    val own = applicationInfo.uid
    val players = dump?.let { DumpsysAudioParser.parsePlayers(it) }
      // The web players (YouTube Music, Spotify) run in this app's own process, so own-uid players
      // count too, except the music player, which carries its equalizer already.
      ?.filter {
        it.isStarted && it.sessionId > 0 &&
          (it.uid != own || (com.example.data.web.WebPlayerHost.state.value.active && it.sessionId != GlobalEq.ownPlayerSession))
      }
      .orEmpty()

    val alive = players.map { it.sessionId }.toMutableSet() + announced.keys
    var globalOutput = false
    val targets = if (reach == EqReach.ROOT) {
      globalOutput = equalizer.attach(GLOBAL_SESSION)
      alive + GLOBAL_SESSION
    } else {
      alive
    }
    alive.forEach { equalizer.attach(it) }
    equalizer.retainOnly(targets)

    val names = (players.map { labelFor(it.uid) } + announced.values.map { labelForPackage(it) }).distinct()
    val rootMissing = reach == EqReach.ROOT && !hasDump() && !rootDump
    GlobalEq.publish(
      GlobalEqStatus(
        running = true,
        apps = names,
        needsDump = reach == EqReach.ADB && !hasDump(),
        rootUnavailable = rootMissing,
        globalOutput = globalOutput
      )
    )
  }

  private suspend fun readDumpsys(): String? = withContext(Dispatchers.IO) {
    try {
      withTimeoutOrNull(4000) {
        val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "audio"))
        process.inputStream.bufferedReader().use { it.readText() }
      }
    } catch (e: Exception) {
      Log.w(TAG, "dumpsys audio failed: ${e.javaClass.simpleName}")
      null
    }
  }

  private fun labelFor(uid: Int): String {
    val pkg = runCatching { packageManager.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()
    return pkg?.let(::labelForPackage) ?: if (uid < 10000) "Sistema Android" else "App (uid $uid)"
  }

  private fun labelForPackage(pkg: String): String = runCatching {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
  }.getOrDefault(pkg)

  private fun notification(): android.app.Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      getSystemService(NotificationManager::class.java)?.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Equalizador em todos os apps", NotificationManager.IMPORTANCE_LOW)
      )
    }
    val open = PendingIntent.getActivity(
      this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_media_play)
      .setContentTitle("Equalizador ligado em todos os apps")
      .setContentText("O T.A.R.K.A.S. aplica sua curva ao som dos outros apps.")
      .setContentIntent(open)
      .setOngoing(true)
      .setSilent(true)
      .build()
  }

  override fun onDestroy() {
    scope.cancel()
    runCatching { unregisterReceiver(sessionReceiver) }
    equalizer.releaseAll()
    GlobalEq.publish(GlobalEqStatus())
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
