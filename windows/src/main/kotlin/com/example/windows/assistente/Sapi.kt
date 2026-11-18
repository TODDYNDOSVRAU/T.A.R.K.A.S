package com.example.windows.assistente

import java.io.IOException
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

// Windows speech (SAPI, System.Speech) through PowerShell. Text goes in as Base64 on stdin so accents survive
// the console code page, and results come back as UTF-8. No native library, no download.

/** The reply as it is read aloud: the bold and italic markers are dropped so the voice does not say them. */
internal fun speakableReply(text: String): String =
  text.replace("*", "").replace(Regex("\\s+"), " ").trim()

/** Only a reply to a question asked by voice is read aloud, and only a real reply, never an error line. */
internal fun shouldSpeakReply(askedByVoice: Boolean, reply: AssistantMessage): Boolean =
  askedByVoice &&
    reply.sender == MessageSender.ASSISTANT &&
    reply.failure == null &&
    speakableReply(reply.text).isNotEmpty()

/** The first two sentences of the trivia, framed by the intro or outro text the screen supplies. */
internal fun spokenTrivia(shortTrivia: String): String {
  val raw = shortTrivia.trim()
  val sentences = raw.split(Regex("(?<=[.!?])\\s+"))
  return if (sentences.size >= 2) "${sentences[0]} ${sentences[1]}" else raw
}

private fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun percent(factor: Float): String {
  val p = Math.round((factor - 1f) * 100f)
  return (if (p >= 0) "+" else "") + p + "%"
}

/** SSML for one utterance. Pitch and rate are factors around 1.0, like the Android voice settings. */
internal fun buildSsml(text: String, language: String, pitch: Float, rate: Float): String =
  "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"${xmlEscape(language)}\">" +
    "<prosody pitch=\"${percent(pitch.coerceIn(0.5f, 2f))}\" rate=\"${percent(rate.coerceIn(0.5f, 2f))}\">" +
    xmlEscape(text) + "</prosody></speak>"

sealed interface ListenResult {
  data class Heard(val text: String) : ListenResult
  data object Nothing : ListenResult
  /** No speech recognizer for the language is installed in Windows. */
  data object Unavailable : ListenResult
  data object Failed : ListenResult
}

/** A running utterance. [stop] cuts it. */
class SpeechHandle internal constructor(private val process: Process?) {
  fun stop() { process?.destroyForcibly() }
  /** Blocks until the voice finishes or is stopped. Returns true when it ended by itself. */
  fun await(): Boolean = process == null || (process.waitFor() == 0)
}

object Sapi {
  private const val SPEAK =
    "Add-Type -AssemblyName System.Speech; " +
      "\$t = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String([Console]::In.ReadToEnd().Trim())); " +
      "\$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; \$s.SetOutputToDefaultAudioDevice(); \$s.SpeakSsml(\$t)"

  private const val LISTEN =
    "[Console]::OutputEncoding = [Text.Encoding]::UTF8; Add-Type -AssemblyName System.Speech; " +
      "\$lang = [Console]::In.ReadToEnd().Trim(); " +
      "\$info = [System.Speech.Recognition.SpeechRecognitionEngine]::InstalledRecognizers() | " +
      "Where-Object { \$_.Culture.Name -eq \$lang -or \$_.Culture.TwoLetterISOLanguageName -eq \$lang.Substring(0,2) } | Select-Object -First 1; " +
      "if (-not \$info) { exit 3 }; " +
      "\$e = New-Object System.Speech.Recognition.SpeechRecognitionEngine(\$info); " +
      "\$e.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar)); \$e.SetInputToDefaultAudioDevice(); " +
      "\$r = \$e.Recognize([TimeSpan]::FromSeconds(8)); if (\$r) { [Console]::Out.Write(\$r.Text) }"

  /** The BCP-47 tag for the voice, from the app language. */
  fun languageTag(locale: Locale = Locale.getDefault()): String =
    if (locale.language == "pt") "pt-BR" else if (locale.country.isNotEmpty()) locale.toLanguageTag() else "en-US"

  /** Starts speaking and returns at once. Returns a silent handle when PowerShell cannot be started. */
  fun speak(text: String, pitch: Float = 1f, rate: Float = 1f, language: String = languageTag()): SpeechHandle {
    val clean = speakableReply(text)
    if (clean.isEmpty()) return SpeechHandle(null)
    return try {
      val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", SPEAK).start()
      val ssml = buildSsml(clean, language, pitch, rate)
      process.outputStream.use { it.write(Base64.getEncoder().encode(ssml.toByteArray(Charsets.UTF_8))) }
      Thread { runCatching { process.inputStream.readBytes(); process.errorStream.readBytes() } }.apply { isDaemon = true }.start()
      SpeechHandle(process)
    } catch (_: IOException) {
      SpeechHandle(null)
    }
  }

  /** Listens once through the default microphone. Call it only after the user asked for it. */
  fun listen(language: String = languageTag()): ListenResult = try {
    val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", LISTEN).start()
    process.outputStream.use { it.write(language.toByteArray(Charsets.UTF_8)) }
    val out = process.inputStream.readBytes().toString(Charsets.UTF_8).trim()
    process.errorStream.readBytes()
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      ListenResult.Failed
    } else when (process.exitValue()) {
      0 -> if (out.isEmpty()) ListenResult.Nothing else ListenResult.Heard(out)
      3 -> ListenResult.Unavailable
      else -> ListenResult.Failed
    }
  } catch (_: IOException) {
    ListenResult.Failed
  }
}
