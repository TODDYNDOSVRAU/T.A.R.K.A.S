package com.example.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Build
import com.example.BuildConfig
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Updates from GitHub Releases: look for a newer release, download its APK, check it, install it.
 *
 * What is automatic and what is not (Android's rule, not ours): the checking and the download need no
 * tap. The install needs none only when this app is the one that installed the current version and
 * "install unknown apps" is allowed for it; the first update of a copy installed by adb or a file
 * manager asks for one confirmation, after which later updates install without it. The app never
 * installs while music is playing.
 */
object UpdateManager {
  const val REPO = "TODDYNDOSVRAU/T.A.R.K.A.S"
  private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

  private lateinit var app: Context
  private lateinit var store: UpdateStore
  private val lock = Mutex()
  private val client = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

  private val _state = MutableStateFlow(UpdateState())
  val state: StateFlow<UpdateState> = _state

  @Synchronized
  fun init(context: Context) {
    if (::app.isInitialized) return
    app = context.applicationContext
    store = UpdateStore(app)
    _state.value = UpdateState(lastCheckedAt = store.lastCheckedAt)
    restore()
  }

  /**
   * Only the published (release) build installs updates. A debug build is signed with the developer's
   * key, not the release key, so Android would refuse the release over it or require erasing its data;
   * it only tells that a newer version exists.
   */
  val installsUpdates: Boolean get() = !BuildConfig.DEBUG

  const val DEV_BUILD_NOTICE =
    "Esta é a versão de desenvolvimento, assinada com outra chave que a versão publicada. " +
      "Por isso ela não se atualiza sozinha: o Android recusaria a instalação ou apagaria os dados. " +
      "Ela só avisa quando há versão nova. Para atualizá-la, instale pelo adb."

  val autoEnabled: Boolean get() = store.autoEnabled
  val wifiOnly: Boolean get() = store.wifiOnly

  fun setAutoEnabled(on: Boolean) {
    store.autoEnabled = on
    UpdateJobService.schedule(app)
  }

  fun setWifiOnly(on: Boolean) {
    store.wifiOnly = on
    UpdateJobService.schedule(app)
  }

  fun canInstallPackages(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

  /** Picks up what an earlier run left: the newest release seen, and whether its APK is ready. */
  private fun restore() {
    val release = store.lastReleaseJson?.let { (ReleaseParser.parse(it) as? ReleaseReading.Found)?.release }
    if (release == null || !Version.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
      store.verifiedTag = null
      store.lastReleaseJson = null
      clearDownloads()
      return
    }
    val ready = store.verifiedTag == release.tag && apkFile(release).isFile
    _state.update {
      it.copy(release = release, phase = if (ready) UpdateState.Phase.READY else UpdateState.Phase.AVAILABLE)
    }
  }

  /** Looks for a newer release. Safe to call often; one run at a time. */
  suspend fun check(): UpdateState = lock.withLock {
    _state.update { it.copy(phase = UpdateState.Phase.CHECKING, problem = null) }
    val outcome = withContext(Dispatchers.IO) { fetchLatest() }
    val now = System.currentTimeMillis()
    when (outcome) {
      is Fetch.Failed -> _state.update { it.copy(phase = idlePhase(it), problem = outcome.problem) }
      is Fetch.Body -> {
        store.lastCheckedAt = now
        when (val reading = ReleaseParser.parse(outcome.text)) {
          ReleaseReading.Unreadable ->
            _state.update { it.copy(phase = idlePhase(it), problem = UpdateProblem.UNREADABLE, lastCheckedAt = now) }
          is ReleaseReading.NotInstallable ->
            _state.update { it.copy(phase = UpdateState.Phase.UP_TO_DATE, release = null, problem = reading.reason, lastCheckedAt = now) }
          is ReleaseReading.Found -> {
            val release = reading.release
            if (Version.isNewer(release.versionName, BuildConfig.VERSION_NAME)) {
              store.lastReleaseJson = outcome.text
              val ready = store.verifiedTag == release.tag && apkFile(release).isFile
              _state.update {
                it.copy(
                  release = release, problem = null, lastCheckedAt = now,
                  phase = if (ready) UpdateState.Phase.READY else UpdateState.Phase.AVAILABLE
                )
              }
            } else {
              store.lastReleaseJson = null
              store.verifiedTag = null
              clearDownloads()
              _state.update { it.copy(phase = UpdateState.Phase.UP_TO_DATE, release = null, problem = null, lastCheckedAt = now) }
            }
          }
        }
      }
    }
    _state.value
  }

  /** The state to fall back to after a failure: keep showing a release already known. */
  private fun idlePhase(s: UpdateState) =
    if (s.release != null) UpdateState.Phase.AVAILABLE else UpdateState.Phase.IDLE

  private sealed interface Fetch {
    data class Body(val text: String) : Fetch
    data class Failed(val problem: UpdateProblem) : Fetch
  }

  private fun fetchLatest(): Fetch {
    if (!online()) return Fetch.Failed(UpdateProblem.NO_NETWORK)
    val request = Request.Builder().url(LATEST_URL)
      .header("Accept", "application/vnd.github+json")
      .header("X-GitHub-Api-Version", "2022-11-28")
      .header("User-Agent", "TARKAS-updater")
      .build()
    return try {
      client.newCall(request).execute().use { r ->
        when {
          r.isSuccessful -> Fetch.Body(r.body?.string().orEmpty())
          r.code == 403 || r.code == 429 -> Fetch.Failed(UpdateProblem.RATE_LIMITED)
          r.code == 404 -> Fetch.Failed(UpdateProblem.NO_APK)
          else -> Fetch.Failed(UpdateProblem.SERVER)
        }
      }
    } catch (_: IOException) {
      Fetch.Failed(UpdateProblem.NO_NETWORK)
    }
  }

  /**
   * Downloads the known release and checks it. With [userInitiated] a metered network is allowed;
   * without, "Só no Wi-Fi" is respected.
   */
  suspend fun download(userInitiated: Boolean): UpdateState = lock.withLock {
    val release = _state.value.release ?: return@withLock _state.value
    if (!installsUpdates) return@withLock _state.value
    if (_state.value.phase == UpdateState.Phase.READY) return@withLock _state.value
    if (!userInitiated && store.wifiOnly && isMetered()) {
      _state.update { it.copy(problem = UpdateProblem.METERED) }
      return@withLock _state.value
    }
    _state.update { it.copy(phase = UpdateState.Phase.DOWNLOADING, progress = 0f, problem = null) }
    val problem = withContext(Dispatchers.IO) { downloadAndVerify(release) }
    if (problem == null) {
      store.verifiedTag = release.tag
      _state.update { it.copy(phase = UpdateState.Phase.READY, progress = 1f, problem = null) }
    } else {
      _state.update { it.copy(phase = UpdateState.Phase.AVAILABLE, progress = 0f, problem = problem) }
    }
    _state.value
  }

  private suspend fun downloadAndVerify(release: ReleaseInfo): UpdateProblem? {
    val dir = downloadsDir().also { it.mkdirs() }
    val target = apkFile(release)
    val part = File(dir, target.name + ".part")
    dir.listFiles()?.filter { it != target && it != part }?.forEach { it.delete() }
    if (release.apkSize > 0 && dir.usableSpace < release.apkSize * 2) return UpdateProblem.DISK
    if (!online()) return UpdateProblem.NO_NETWORK
    try {
      val request = Request.Builder().url(release.apkUrl).header("User-Agent", "TARKAS-updater").build()
      client.newCall(request).execute().use { r ->
        if (!r.isSuccessful) return UpdateProblem.DOWNLOAD
        val body = r.body ?: return UpdateProblem.DOWNLOAD
        val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize
        var done = 0L
        var lastShown = -1
        body.byteStream().use { input ->
          part.outputStream().use { out ->
            val buf = ByteArray(64 * 1024)
            while (true) {
              currentCoroutineContext().ensureActive()
              val n = input.read(buf)
              if (n < 0) break
              out.write(buf, 0, n)
              done += n
              val pct = if (total > 0) (done * 100 / total).toInt() else 0
              if (pct != lastShown) {
                lastShown = pct
                _state.update { it.copy(progress = pct / 100f) }
              }
            }
          }
        }
        if (total > 0 && done != total) { part.delete(); return UpdateProblem.DOWNLOAD }
      }
    } catch (e: InterruptedIOException) {
      part.delete(); return UpdateProblem.DOWNLOAD
    } catch (e: IOException) {
      part.delete()
      return if (e.message?.contains("ENOSPC", true) == true || e.message?.contains("space", true) == true) UpdateProblem.DISK
      else UpdateProblem.DOWNLOAD
    }
    val wanted = release.sha256 ?: release.checksumUrl?.let { fetchChecksum(it) ?: return UpdateProblem.CHECKSUM }
    val problem = verify(part, wanted)
    if (problem != null) { part.delete(); return problem }
    if (target.exists()) target.delete()
    return if (part.renameTo(target)) null else UpdateProblem.DISK
  }

  private fun fetchChecksum(url: String): String? = try {
    val request = Request.Builder().url(url).header("User-Agent", "TARKAS-updater").build()
    client.newCall(request).execute().use { r ->
      if (r.isSuccessful) ReleaseParser.parseChecksumFile(r.body?.string().orEmpty().take(4096)) else null
    }
  } catch (_: IOException) { null }

  /**
   * The checks before any install: the SHA-256 when the release publishes one, the file is an APK of
   * this package, it is newer than the installed version, and it carries the same signing certificate.
   * The signature check is what stops an APK from another key, which Android would refuse anyway.
   */
  private fun verify(file: File, expectedSha256: String?): UpdateProblem? {
    if (expectedSha256 != null && sha256(file) != expectedSha256.lowercase()) return UpdateProblem.CHECKSUM
    val pm = app.packageManager
    val flags = signingFlags()
    val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: return UpdateProblem.NOT_AN_APK
    if (archive.packageName != app.packageName) return UpdateProblem.WRONG_PACKAGE
    val installed = pm.getPackageInfo(app.packageName, flags)
    if (versionCodeOf(archive) <= versionCodeOf(installed)) return UpdateProblem.NOT_NEWER
    val candidate = signerHashes(archive, history = false)
    val current = signerHashes(installed, history = true)
    return if (Signers.compatible(current, candidate)) null else UpdateProblem.SIGNATURE
  }

  @Suppress("DEPRECATION")
  private fun signingFlags(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

  @Suppress("DEPRECATION")
  private fun versionCodeOf(p: PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode else p.versionCode.toLong()

  @Suppress("DEPRECATION")
  private fun signerHashes(p: PackageInfo, history: Boolean): Set<String> {
    val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val info = p.signingInfo ?: return emptySet()
      if (history && !info.hasMultipleSigners()) info.signingCertificateHistory else info.apkContentsSigners
    } else p.signatures
    return sigs.orEmpty().map { sha256(it.toByteArray()) }.toSet()
  }

  /**
   * Installs the downloaded APK through [PackageInstaller]. Never while music plays unless
   * [userInitiated]. The result arrives in [UpdateInstallReceiver].
   */
  suspend fun install(userInitiated: Boolean): UpdateState = lock.withLock {
    val release = _state.value.release ?: return@withLock _state.value
    if (!installsUpdates) return@withLock _state.value
    if (_state.value.phase != UpdateState.Phase.READY) return@withLock _state.value
    if (!userInitiated && isMusicActive()) {
      _state.update { it.copy(problem = UpdateProblem.BUSY_PLAYING) }
      return@withLock _state.value
    }
    if (!canInstallPackages()) {
      _state.update { it.copy(problem = UpdateProblem.NOT_ALLOWED) }
      return@withLock _state.value
    }
    val file = apkFile(release)
    if (!file.isFile) {
      store.verifiedTag = null
      _state.update { it.copy(phase = UpdateState.Phase.AVAILABLE, problem = UpdateProblem.DOWNLOAD) }
      return@withLock _state.value
    }
    _state.update { it.copy(phase = UpdateState.Phase.INSTALLING, problem = null) }
    val problem = withContext(Dispatchers.IO) {
      // Checked again just before installing: the file sat on disk since the download.
      verify(file, release.sha256).also { if (it != null) { file.delete(); store.verifiedTag = null } }
        ?: commit(file)
    }
    if (problem != null) {
      _state.update { it.copy(phase = if (store.verifiedTag != null) UpdateState.Phase.READY else UpdateState.Phase.AVAILABLE, problem = problem) }
    }
    _state.value
  }

  private fun commit(file: File): UpdateProblem? = try {
    val installer = app.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
    params.setAppPackageName(app.packageName)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) params.setRequestUpdateOwnership(true)
    val id = installer.createSession(params)
    installer.openSession(id).use { session ->
      file.inputStream().use { input ->
        session.openWrite("base.apk", 0, file.length()).use { out ->
          input.copyTo(out)
          session.fsync(out)
        }
      }
      val intent = Intent(app, UpdateInstallReceiver::class.java).setPackage(app.packageName)
      val pending = PendingIntent.getBroadcast(app, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
      session.commit(pending.intentSender)
    }
    null
  } catch (e: IOException) {
    if (e.message?.contains("ENOSPC", true) == true) UpdateProblem.DISK else UpdateProblem.INSTALL_FAILED
  } catch (_: SecurityException) {
    UpdateProblem.NOT_ALLOWED
  } catch (_: IllegalStateException) {
    UpdateProblem.INSTALL_FAILED
  }

  /** Called by [UpdateInstallReceiver] with what the installer reported. */
  internal fun onInstallResult(status: Int) {
    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION ->
        _state.update { it.copy(phase = UpdateState.Phase.AWAITING_USER, problem = null) }
      PackageInstaller.STATUS_SUCCESS -> Unit // the process is replaced; the next start restores a clean state
      PackageInstaller.STATUS_FAILURE_ABORTED ->
        _state.update { it.copy(phase = UpdateState.Phase.READY, problem = null) }
      PackageInstaller.STATUS_FAILURE_STORAGE ->
        _state.update { it.copy(phase = UpdateState.Phase.READY, problem = UpdateProblem.DISK) }
      PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
        _state.update { it.copy(phase = UpdateState.Phase.READY, problem = UpdateProblem.SIGNATURE) }
      else -> _state.update { it.copy(phase = UpdateState.Phase.READY, problem = UpdateProblem.INSTALL_FAILED) }
    }
  }

  /** The periodic and on-open routine: look, download if the network allows, install if nothing plays. */
  suspend fun runAutomatic() {
    if (!store.autoEnabled || !installsUpdates) return
    val s = check()
    if (s.release == null) return
    if (s.phase == UpdateState.Phase.AVAILABLE) download(userInitiated = false)
    if (_state.value.phase == UpdateState.Phase.READY) install(userInitiated = false)
  }

  // ---- helpers ----

  private fun downloadsDir() = File(app.cacheDir, "updates")
  private fun apkFile(r: ReleaseInfo) = File(downloadsDir(), "TARKAS-${r.versionName}.apk")
  private fun clearDownloads() { downloadsDir().listFiles()?.forEach { it.delete() } }

  private fun online(): Boolean {
    val cm = app.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  private fun isMetered(): Boolean = app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true

  private fun isMusicActive(): Boolean = app.getSystemService(AudioManager::class.java)?.isMusicActive ?: false

  private fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buf = ByteArray(64 * 1024)
      while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }

  private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** Whether a new APK's signing certificates may replace the installed ones. */
object Signers {
  /** Every certificate of the new APK must be one the installed app has used (its history included). */
  fun compatible(installed: Set<String>, candidate: Set<String>): Boolean =
    installed.isNotEmpty() && candidate.isNotEmpty() && installed.containsAll(candidate)
}
