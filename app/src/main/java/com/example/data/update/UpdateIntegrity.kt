package com.example.data.update

import java.io.File
import java.security.MessageDigest

/**
 * The SHA-256 rules shared by the download check and the check right before installing, so the two
 * can never differ: the hash comes from the asset's `digest` field or, failing that, from the
 * `.sha256` file published next to the APK.
 */
internal object UpdateIntegrity {

  /** What the release says the APK's SHA-256 must be. */
  sealed interface Expected {
    /** Lowercase hex the file must match. */
    data class Hash(val hex: String) : Expected
    /** The release publishes no hash at all (old releases): only package, version and signature are checked. */
    data object NotPublished : Expected
    /** The release points to a `.sha256` file but it could not be fetched or holds no hash. */
    data object Unavailable : Expected
  }

  /** [fetch] returns the hash found at the `.sha256` address, or null when it could not be read. */
  fun expected(release: ReleaseInfo, fetch: (String) -> String?): Expected {
    release.sha256?.let { return Expected.Hash(it.lowercase()) }
    val url = release.checksumUrl ?: return Expected.NotPublished
    return fetch(url)?.let { Expected.Hash(it.lowercase()) } ?: Expected.Unavailable
  }

  /**
   * Null when the file may go on to the other checks; [UpdateProblem.CHECKSUM] when it does not match;
   * [UpdateProblem.HASH_UNAVAILABLE] when the published hash could not be obtained.
   */
  fun check(file: File, expected: Expected): UpdateProblem? = when (expected) {
    Expected.NotPublished -> null
    Expected.Unavailable -> UpdateProblem.HASH_UNAVAILABLE
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

  /** Removes the partial download files in [dir]; the finished APK is left alone. */
  fun discardPartials(dir: File) {
    dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
  }

  /** The state after the user cancels a download: back to "available", no progress, no error. */
  fun afterCancel(s: UpdateState): UpdateState =
    s.copy(phase = UpdateState.Phase.AVAILABLE, progress = 0f, problem = null)
}

/** Release notes as the app shows them: plain text, or null when there is nothing to show. */
object ReleaseNotes {
  private const val MAX_CHARS = 6000

  fun plain(raw: String): String? {
    var t = raw.replace("\r\n", "\n").replace('\r', '\n')
    t = t.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    t = t.replace(Regex("""!?\[([^\]]*)]\([^)]*\)"""), "$1")
    t = t.lines().joinToString("\n") { line ->
      line.trimEnd()
        .replace(Regex("""^\s{0,3}#{1,6}\s+"""), "")
        .replace(Regex("""^(\s*)[*-]\s+"""), "$1• ")
    }
    t = t.replace("**", "").replace("__", "").replace("`", "")
    t = t.replace(Regex("\n{3,}"), "\n\n").trim()
    if (t.isEmpty()) return null
    return if (t.length > MAX_CHARS) t.take(MAX_CHARS).trimEnd() + "…" else t
  }
}
