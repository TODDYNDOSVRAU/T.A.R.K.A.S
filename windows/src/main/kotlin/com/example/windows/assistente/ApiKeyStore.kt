package com.example.windows.assistente

import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Turns a key into text that is useless to anyone but the signed-in user, and back. */
interface KeyProtector {
  fun protect(plain: String): String
  fun unprotect(protectedText: String): String?
}

/** Windows DPAPI (current user scope) through PowerShell. The key travels on stdin, never on a command line. */
object DpapiKeyProtector : KeyProtector {
  private const val PROTECT = "ConvertTo-SecureString ([Console]::In.ReadToEnd().Trim()) -AsPlainText -Force | ConvertFrom-SecureString"
  private const val UNPROTECT =
    "\$s = ConvertTo-SecureString ([Console]::In.ReadToEnd().Trim()); " +
      "[Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR(\$s))"

  override fun protect(plain: String): String =
    runPowerShell(PROTECT, plain) ?: throw IOException("DPAPI protect failed")

  override fun unprotect(protectedText: String): String? = runPowerShell(UNPROTECT, protectedText)

  private fun runPowerShell(command: String, input: String): String? = try {
    val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
      .redirectErrorStream(false).start()
    process.outputStream.use { it.write(input.toByteArray(Charsets.UTF_8)) }
    val out = process.inputStream.readBytes().toString(Charsets.UTF_8).trim()
    process.errorStream.readBytes()
    if (process.waitFor() == 0 && out.isNotEmpty()) out else null
  } catch (_: IOException) {
    null
  }
}

/**
 * Where the Gemini key lives. The key is never part of the build or the installer: the user pastes it in the
 * assistant screen, it is encrypted for the current Windows user and kept in a file in the user profile.
 * The environment variable TARKAS_GEMINI_API_KEY is read as a fallback for developers.
 */
class ApiKeyStore(
  private val file: File = defaultFile(),
  private val protector: KeyProtector = DpapiKeyProtector,
  private val environment: () -> String? = { System.getenv("TARKAS_GEMINI_API_KEY") }
) {
  /** The usable key, or null when none is set. */
  fun load(): String? {
    val stored = try {
      if (file.isFile) protector.unprotect(file.readText().trim()) else null
    } catch (_: IOException) {
      null
    }
    return listOf(stored, environment()).firstNotNullOfOrNull { clean(it) }
  }

  /** True when a key saved by the user exists (the environment fallback does not count). */
  fun hasStored(): Boolean = file.isFile && file.length() > 0

  fun save(key: String) {
    val cleaned = clean(key) ?: throw IllegalArgumentException("blank key")
    file.parentFile?.mkdirs()
    file.writeText(protector.protect(cleaned))
  }

  fun clear() {
    file.delete()
  }

  private fun clean(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() && it != "MY_GEMINI_API_KEY" && ' ' !in it }

  companion object {
    fun defaultFile(): File {
      val base = System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), "AppData/Roaming")
      return File(base, "TarkasWindows/gemini.key")
    }
  }
}

/** The key goes in a header, not in the URL, so it never lands in logs or proxies. */
object HttpGeminiTransport : GeminiTransport {
  private val client: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build() }

  override fun post(apiKey: String, body: String): Pair<Int, String> {
    val request = HttpRequest.newBuilder(URI.create(GEMINI_MODEL_URL))
      .timeout(Duration.ofSeconds(30))
      .header("Content-Type", "application/json")
      .header("x-goog-api-key", apiKey)
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
    return try {
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      response.statusCode() to response.body()
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw IOException("interrupted", e)
    }
  }
}
