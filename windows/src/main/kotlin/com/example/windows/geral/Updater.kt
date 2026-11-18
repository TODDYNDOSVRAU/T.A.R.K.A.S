package com.example.windows.geral

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Update through the installer. The Android app downloads an APK and hands it to the package installer; here the
// release carries an MSI or EXE built by `packageMsi`/`packageExe`. Same rules as docs/ATUALIZACOES.md: only a
// published (not draft, not pre-release) release, only https assets, the SHA-256 checked before anything runs, and
// nothing installed while music is playing.

/** Everything that can stop an update. Each says what happened and what to do. */
enum class UpdateProblem(val pt: String, val en: String) {
  NO_NETWORK("Sem conexão com o GitHub. Verifique a internet e tente de novo.", "Could not reach GitHub. Check the connection and try again."),
  RATE_LIMITED("O GitHub limitou as consultas. Tente de novo em alguns minutos.", "GitHub is limiting requests. Try again in a few minutes."),
  SERVER("O GitHub respondeu com erro. Tente de novo mais tarde.", "GitHub answered with an error. Try again later."),
  UNREADABLE("A resposta do GitHub não pôde ser lida. Tente de novo mais tarde.", "GitHub's answer could not be read. Try again later."),
  NO_INSTALLER("A versão publicada não tem um instalador MSI ou EXE. Baixe na página de versões se quiser.", "The published version has no MSI or EXE installer. Download it from the releases page if you want it."),
  DOWNLOAD("O download falhou. Tente de novo.", "The download failed. Try again."),
  DISK("Não há espaço em disco para o instalador. Libere espaço e tente de novo.", "There is no disk space for the installer. Free some space and try again."),
  CHECKSUM("O arquivo baixado não confere com o SHA-256 publicado e foi descartado. Tente de novo.", "The downloaded file does not match the published SHA-256 and was discarded. Try again."),
  HASH_UNAVAILABLE("Não foi possível obter o SHA-256 publicado; por segurança nada foi instalado.", "The published SHA-256 could not be fetched; nothing was installed for safety."),
  INSTALL_FAILED("O instalador não pôde ser aberto. Abra o arquivo baixado manualmente.", "The installer could not be opened. Open the downloaded file yourself."),
  BUSY_PLAYING("Há música tocando. Pause e instale quando quiser.", "Music is playing. Pause it and install when you want.");

  val message: String get() = w(pt, en)
}

data class ReleaseInfo(
  val tag: String,
  val versionName: String,
  val assetName: String,
  val assetUrl: String,
  val assetSize: Long,
  /** Lowercase hex from the asset's `digest`, or null. */
  val sha256: String?,
  /** Address of a `<asset>.sha256` file when the release publishes one. */
  val checksumUrl: String?,
  val notes: String,
  val pageUrl: String
)

sealed interface ReleaseReading {
  data class Found(val release: ReleaseInfo) : ReleaseReading
  data class NotInstallable(val reason: UpdateProblem) : ReleaseReading
  data object Unreadable : ReleaseReading
}

/** A dotted version with an optional pre-release suffix ("2.1", "v2.1.0-rc1"); a pre-release is older than its release. */
data class Version(val parts: List<Int>, val preRelease: String?) : Comparable<Version> {
  override fun compareTo(other: Version): Int {
    for (i in 0 until maxOf(parts.size, other.parts.size)) {
      val c = parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
      if (c != 0) return c
    }
    return when {
      preRelease == other.preRelease -> 0
      preRelease == null -> 1
      other.preRelease == null -> -1
      else -> preRelease.compareTo(other.preRelease)
    }
  }

  companion object {
    private val PATTERN = Regex("""^[vV]?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.\-]+?))?(?:\+[0-9A-Za-z.\-]+)?$""")

    fun parse(text: String): Version? {
      val m = PATTERN.matchEntire(text.trim()) ?: return null
      val nums = m.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
      return Version(nums, m.groupValues[2].ifEmpty { null })
    }

    fun isNewer(candidate: String, installed: String): Boolean {
      val a = parse(candidate) ?: return false
      val b = parse(installed) ?: return false
      return a > b
    }
  }
}

/** Reads `GET /repos/{owner}/{repo}/releases/latest`. An MSI wins over an EXE; among several of one kind, the one named TARKAS*. */
object ReleaseParser {
  private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

  fun parse(body: String): ReleaseReading {
    val json = try { JSONObject(body) } catch (_: Exception) { return ReleaseReading.Unreadable }
    val tag = json.optString("tag_name").trim()
    if (tag.isEmpty() || Version.parse(tag) == null) return ReleaseReading.Unreadable
    if (json.optBoolean("draft") || json.optBoolean("prerelease")) return ReleaseReading.NotInstallable(UpdateProblem.NO_INSTALLER)
    val assets = json.optJSONArray("assets") ?: JSONArray()
    val list = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.filter { it.optString("state", "uploaded") == "uploaded" }
    fun pick(ext: String): JSONObject? {
      val c = list.filter { it.optString("name").endsWith(ext, ignoreCase = true) }
      return c.singleOrNull() ?: c.singleOrNull { it.optString("name").startsWith("TARKAS", ignoreCase = true) }
    }
    val asset = pick(".msi") ?: pick(".exe") ?: return ReleaseReading.NotInstallable(UpdateProblem.NO_INSTALLER)
    val url = asset.optString("browser_download_url")
    if (!url.startsWith("https://")) return ReleaseReading.NotInstallable(UpdateProblem.NO_INSTALLER)
    val name = asset.optString("name")
    val digest = asset.optString("digest")
    val sha = digest.removePrefix("sha256:").lowercase().takeIf { digest.startsWith("sha256:") && SHA256_HEX.matches(it) }
    val sidecar = list.firstOrNull { it.optString("name").equals("$name.sha256", ignoreCase = true) }
      ?.optString("browser_download_url")?.takeIf { it.startsWith("https://") }
    return ReleaseReading.Found(
      ReleaseInfo(tag, tag.removePrefix("v").removePrefix("V"), name, url, asset.optLong("size"), sha, sidecar, json.optString("body"), json.optString("html_url"))
    )
  }

  /** First 64-hex token of a `.sha256` file ("<hash>  <name>" or just the hash). */
  fun parseChecksumFile(text: String): String? =
    text.trim().split(Regex("\\s+")).firstOrNull()?.lowercase()?.takeIf { SHA256_HEX.matches(it) }
}

object UpdateIntegrity {
  sealed interface Expected {
    data class Hash(val hex: String) : Expected
    data object NotPublished : Expected
    data object Unavailable : Expected
  }

  fun expected(release: ReleaseInfo, fetch: (String) -> String?): Expected {
    release.sha256?.let { return Expected.Hash(it.lowercase()) }
    val url = release.checksumUrl ?: return Expected.NotPublished
    return fetch(url)?.let { Expected.Hash(it.lowercase()) } ?: Expected.Unavailable
  }

  /** Null when the file may be installed. An installer with no published hash is refused: it runs with the user's rights. */
  fun check(file: File, expected: Expected): UpdateProblem? = when (expected) {
    Expected.NotPublished, Expected.Unavailable -> UpdateProblem.HASH_UNAVAILABLE
    is Expected.Hash -> if (sha256(file) == expected.hex) null else UpdateProblem.CHECKSUM
  }

  fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buf = ByteArray(64 * 1024)
      while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }

  fun discardPartials(dir: File) { dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() } }
}

data class UpdateState(
  val phase: Phase = Phase.IDLE,
  val release: ReleaseInfo? = null,
  val progress: Float = 0f,
  val problem: UpdateProblem? = null,
  val lastCheckedAt: Long = 0L
) {
  enum class Phase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, INSTALLING }
}

/** Talks to GitHub and runs the installer. Network and process are injected so tests need neither. */
class Updater(
  private val installedVersion: String,
  private val downloadDir: File,
  private val http: (String) -> HttpResult = ::httpGet,
  private val launch: (List<String>) -> Boolean = { cmd -> runCatching { ProcessBuilder(cmd).start() }.isSuccess },
  private val now: () -> Long = System::currentTimeMillis
) {
  class HttpResult(val code: Int, val body: String)

  fun check(): UpdateState {
    val res = try { http(LATEST_URL) } catch (_: java.io.IOException) { return UpdateState(problem = UpdateProblem.NO_NETWORK) }
    val at = now()
    return when {
      res.code == 403 || res.code == 429 -> UpdateState(problem = UpdateProblem.RATE_LIMITED)
      res.code >= 500 -> UpdateState(problem = UpdateProblem.SERVER)
      res.code !in 200..299 -> UpdateState(problem = UpdateProblem.UNREADABLE)
      else -> when (val r = ReleaseParser.parse(res.body)) {
        ReleaseReading.Unreadable -> UpdateState(problem = UpdateProblem.UNREADABLE)
        is ReleaseReading.NotInstallable -> UpdateState(problem = r.reason, lastCheckedAt = at)
        is ReleaseReading.Found ->
          if (Version.isNewer(r.release.tag, installedVersion)) UpdateState(UpdateState.Phase.AVAILABLE, r.release, lastCheckedAt = at)
          else UpdateState(UpdateState.Phase.UP_TO_DATE, r.release, lastCheckedAt = at)
      }
    }
  }

  /** Downloads to a `.part` file, checks the SHA-256, renames. [cancelled] is polled between blocks (a cancel returns null, null). */
  fun download(release: ReleaseInfo, progress: (Float) -> Unit, cancelled: () -> Boolean = { false }): Pair<File?, UpdateProblem?> {
    downloadDir.mkdirs()
    UpdateIntegrity.discardPartials(downloadDir)
    val expected = UpdateIntegrity.expected(release) { url ->
      runCatching { http(url).takeIf { it.code in 200..299 }?.body?.let(ReleaseParser::parseChecksumFile) }.getOrNull()
    }
    if (expected !is UpdateIntegrity.Expected.Hash) return null to UpdateProblem.HASH_UNAVAILABLE
    val part = File(downloadDir, release.assetName + ".part")
    val target = File(downloadDir, release.assetName)
    try {
      val c = URL(release.assetUrl).openConnection() as HttpURLConnection
      c.connectTimeout = 15_000; c.readTimeout = 30_000
      if (c.responseCode !in 200..299) return null to UpdateProblem.DOWNLOAD
      val total = c.contentLengthLong.takeIf { it > 0 } ?: release.assetSize
      c.inputStream.use { input ->
        part.outputStream().use { out ->
          val buf = ByteArray(64 * 1024)
          var done = 0L
          while (true) {
            if (cancelled()) { part.delete(); return null to null }
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n); done += n
            if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
          }
        }
      }
    } catch (e: java.io.IOException) {
      part.delete()
      return null to if (e.message?.contains("space", ignoreCase = true) == true) UpdateProblem.DISK else UpdateProblem.DOWNLOAD
    }
    UpdateIntegrity.check(part, expected)?.let { part.delete(); return null to it }
    target.delete()
    if (!part.renameTo(target)) return null to UpdateProblem.DISK
    return target to null
  }

  /** Opens the installer: MSI through msiexec, EXE directly. The caller quits the app when this returns true. */
  fun install(file: File): Boolean = launch(installCommand(file))

  companion object {
    const val REPO = "TODDYNDOSVRAU/T.A.R.K.A.S"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    fun installCommand(file: File): List<String> =
      if (file.name.endsWith(".msi", ignoreCase = true)) listOf("msiexec", "/i", file.absolutePath) else listOf(file.absolutePath)

    fun httpGet(url: String): HttpResult {
      val c = URL(url).openConnection() as HttpURLConnection
      c.connectTimeout = 10_000; c.readTimeout = 15_000
      c.setRequestProperty("Accept", "application/vnd.github+json")
      c.setRequestProperty("User-Agent", "TARKAS-Windows")
      val code = c.responseCode
      val stream = if (code in 200..299) c.inputStream else c.errorStream
      return HttpResult(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
    }
  }
}
