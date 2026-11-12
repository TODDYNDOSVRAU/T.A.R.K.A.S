package com.example.data.update

import org.json.JSONArray
import org.json.JSONObject

/**
 * A GitHub release the app can install: the tag, the APK asset and, when the release publishes one,
 * the SHA-256 of that APK.
 */
data class ReleaseInfo(
  val tag: String,
  val versionName: String,
  val apkName: String,
  val apkUrl: String,
  val apkSize: Long,
  /** Lowercase hex, from the asset's `digest` field or a `.sha256` asset's address in [checksumUrl]. */
  val sha256: String?,
  val checksumUrl: String?,
  val notes: String,
  val pageUrl: String
)

/** What reading the release answer found. */
sealed interface ReleaseReading {
  data class Found(val release: ReleaseInfo) : ReleaseReading
  /** The answer is valid but there is nothing to install (draft, pre-release, or no APK). */
  data class NotInstallable(val reason: UpdateProblem) : ReleaseReading
  data object Unreadable : ReleaseReading
}

/** Reads `GET /repos/{owner}/{repo}/releases/latest`. The release rules are in docs/ATUALIZACOES.md. */
object ReleaseParser {

  fun parse(body: String): ReleaseReading {
    val json = try { JSONObject(body) } catch (_: Exception) { return ReleaseReading.Unreadable }
    val tag = json.optString("tag_name").trim()
    if (tag.isEmpty() || Version.parse(tag) == null) return ReleaseReading.Unreadable
    if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
      return ReleaseReading.NotInstallable(UpdateProblem.NO_APK)
    }
    val assets = json.optJSONArray("assets") ?: JSONArray()
    val list = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
    // An asset only counts once GitHub finished receiving it.
    val apks = list.filter {
      it.optString("name").endsWith(".apk", ignoreCase = true) && it.optString("state", "uploaded") == "uploaded"
    }
    // Several APKs: the one named TARKAS-*.apk wins; otherwise the release is ambiguous and not installed.
    val apk = apks.singleOrNull() ?: apks.singleOrNull { it.optString("name").startsWith("TARKAS", ignoreCase = true) }
      ?: return ReleaseReading.NotInstallable(UpdateProblem.NO_APK)
    val url = apk.optString("browser_download_url")
    if (!url.startsWith("https://")) return ReleaseReading.NotInstallable(UpdateProblem.NO_APK)
    val name = apk.optString("name")

    val digest = apk.optString("digest").removePrefix("sha256:").lowercase()
    val sha = digest.takeIf { SHA256_HEX.matches(it) && apk.optString("digest").startsWith("sha256:") }
    val sidecar = list.firstOrNull { it.optString("name").equals("$name.sha256", ignoreCase = true) }
      ?.optString("browser_download_url")?.takeIf { it.startsWith("https://") }

    return ReleaseReading.Found(
      ReleaseInfo(
        tag = tag,
        versionName = tag.removePrefix("v").removePrefix("V"),
        apkName = name,
        apkUrl = url,
        apkSize = apk.optLong("size", -1L),
        sha256 = sha,
        checksumUrl = if (sha == null) sidecar else null,
        notes = json.optString("body").trim(),
        pageUrl = json.optString("html_url")
      )
    )
  }

  /** First 64-hex word of a `.sha256` file (`<hash>  <name>` or just `<hash>`). */
  fun parseChecksumFile(text: String): String? =
    Regex("""(?i)\b[0-9a-f]{64}\b""").find(text)?.value?.lowercase()

  private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
}

/**
 * A version such as 1.14 or 1.14.2, with an optional pre-release suffix (1.15-beta). Compared number by
 * number, so 1.9 is older than 1.14; a pre-release is older than the same number without a suffix.
 */
data class Version(val parts: List<Int>, val preRelease: String?) : Comparable<Version> {

  override fun compareTo(other: Version): Int {
    val n = maxOf(parts.size, other.parts.size)
    for (i in 0 until n) {
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

    /** Null when the text is not a version, so a stray tag such as "nightly" is ignored, not guessed. */
    fun parse(text: String): Version? {
      val m = PATTERN.matchEntire(text.trim()) ?: return null
      val nums = m.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
      val suffix = m.groupValues[2].ifEmpty { null }
      return Version(nums, suffix)
    }

    /** True when [candidate] is a newer version than [installed]; false if either is unreadable. */
    fun isNewer(candidate: String, installed: String): Boolean {
      val a = parse(candidate) ?: return false
      val b = parse(installed) ?: return false
      return a > b
    }
  }
}

/** Everything that can stop an update, each with what happened and what to do (T.A.L. voice). */
enum class UpdateProblem(val message: String) {
  NO_NETWORK("Não foi possível verificar atualizações: sem conexão com a internet. Conecte-se e tente de novo."),
  RATE_LIMITED("Não foi possível verificar atualizações: o GitHub limitou as consultas deste aparelho. Tente de novo em cerca de uma hora."),
  SERVER("Não foi possível verificar atualizações: o GitHub não respondeu como esperado. Tente de novo mais tarde."),
  UNREADABLE("Não foi possível ler a resposta do GitHub sobre a última versão. Tente de novo mais tarde; se continuar, baixe o APK pela página de versões."),
  NO_APK("A versão publicada não traz um arquivo APK que o app possa instalar. Baixe pela página de versões, se quiser."),
  METERED("A atualização espera por uma rede Wi-Fi. Conecte-se a uma, ou desligue \"Só no Wi-Fi\" para baixar agora."),
  DOWNLOAD("Não foi possível baixar a atualização: a conexão foi interrompida. Tente de novo."),
  DISK("Não foi possível baixar a atualização: não há espaço livre suficiente. Libere espaço e tente de novo."),
  CHECKSUM("O arquivo baixado não confere com o checksum publicado e foi descartado. Tente de novo; se repetir, avise a T.A.L."),
  NOT_AN_APK("O arquivo baixado não é um APK válido e foi descartado. Tente de novo; se repetir, avise a T.A.L."),
  WRONG_PACKAGE("O arquivo baixado é de outro app e foi descartado. Nada foi instalado."),
  NOT_NEWER("O arquivo baixado não é mais novo que a versão instalada e foi descartado. Nada foi instalado."),
  SIGNATURE("O arquivo baixado foi assinado com outra chave, e o Android não o instalaria por cima. Nada foi alterado. Instale a nova versão manualmente, mantendo ou apagando os dados."),
  NOT_ALLOWED("Para instalar atualizações, permita que o T.A.R.K.A.S. instale apps. Abra a permissão, ative e volte."),
  INSTALL_FAILED("O Android recusou a instalação da atualização. Nada foi alterado; tente de novo."),
  BUSY_PLAYING("A atualização está pronta e espera a música parar para não interromper a reprodução. Toque em \"Instalar agora\" quando quiser.")
}

/** What the update screen shows. [problem] is set only after a failure. */
data class UpdateState(
  val phase: Phase = Phase.IDLE,
  val release: ReleaseInfo? = null,
  val progress: Float = 0f,
  val problem: UpdateProblem? = null,
  val lastCheckedAt: Long = 0L
) {
  enum class Phase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, INSTALLING, AWAITING_USER }
}
