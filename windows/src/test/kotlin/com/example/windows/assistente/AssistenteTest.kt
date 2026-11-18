package com.example.windows.assistente

import com.example.data.model.EqSettings
import com.example.data.model.Track
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Texts become "<resource key>|args", so a test checks which reply was chosen without depending on the language. */
private object TestTexts : AssistantTexts {
  override fun text(res: StringResource, vararg args: Any) = res.key + args.joinToString("") { "|$it" }
  override fun plural(res: PluralStringResource, quantity: Int, vararg args: Any) = res.key + "|$quantity"
}

private fun track(id: String, title: String, artist: String = "Artist", album: String = "Album") =
  Track(id = id, title = title, artist = artist, album = album, durationMs = 1000, genre = "g", era = "e")

private class PlainProtector : KeyProtector {
  override fun protect(plain: String) = "enc:" + plain.reversed()
  override fun unprotect(protectedText: String) = protectedText.removePrefix("enc:").reversed().takeIf { protectedText.startsWith("enc:") }
}

private fun tempKeyStore(env: String? = null, protector: KeyProtector = PlainProtector()): Pair<ApiKeyStore, File> {
  val file = File(Files.createTempDirectory("tarkas-key").toFile(), "gemini.key")
  return ApiKeyStore(file, protector) { env } to file
}

private class FakeHost : AssistantHost {
  var eq = EqSettings()
  val commands = mutableListOf<PlayerCommand>()
  val timers = mutableListOf<Triple<String, String, Int>>()
  val played = mutableListOf<List<Track>>()
  var lib = LibrarySnapshot(emptyList(), emptyList())
  var commandResult: String? = "ok"
  override fun library() = lib
  override fun playTracks(tracks: List<Track>) = true.also { played += tracks }
  override fun queueTracks(tracks: List<Track>, asNext: Boolean) = true
  override fun clearQueue() = true
  override fun sleepIn(minutes: Int) = true
  override fun sleepAtEndOfTrack() = true
  override fun cancelSleep() = true
  override fun setSpatial(on: Boolean) = true
  override fun setVolumePercent(percent: Int) = true
  override fun currentTrack(): Track? = null
  override suspend fun playerCommand(command: PlayerCommand): String? = commandResult.also { commands += command }
  override fun eqSettings() = eq
  override fun applyEq(settings: EqSettings) { eq = settings }
  override fun audioRoute() = "Speakers" to 48000
  override fun createTimer(id: String, label: String, seconds: Int) { timers += Triple(id, label, seconds) }
  override fun cancelTimers() { timers.clear() }
  override fun curiosityFor(track: Track): Curiosity? = null
  override fun curiosityUpdated(curiosity: Curiosity?) = Unit
}

class AssistantCommandsTest {
  @Test fun `portuguese and english commands map to the player`() {
    assertEquals(PlayerCommand.NEXT, playerCommandFor("próxima")?.first)
    assertEquals(PlayerCommand.NEXT, playerCommandFor("next")?.first)
    assertEquals(PlayerCommand.PAUSE, playerCommandFor("pausar")?.first)
    assertEquals(PlayerCommand.PLAY, playerCommandFor("tocar")?.first)
  }

  @Test fun `a negation cancels the command`() {
    assertNull(playerCommandFor("não pausar"))
    assertNull(playerCommandFor("don't pause"))
  }

  @Test fun `a word that only contains the command does not match`() {
    assertNull(playerCommandFor("pausado"))
  }

  @Test fun `unrelated text is not a command`() {
    assertNull(playerCommandFor("qual é a capital da frança"))
  }

  @Test fun `sleep timer requests`() {
    assertEquals(AssistantRequest.SleepIn(20), assistantRequestFor("dormir em 20 min"))
    assertEquals(AssistantRequest.CancelSleep, assistantRequestFor("cancelar dormir"))
    assertEquals(AssistantRequest.SleepAtEndOfTrack, assistantRequestFor("dormir no fim da faixa"))
    assertNull(assistantRequestFor("dormir"))
  }

  @Test fun `library search finds by title artist and playlist without accents`() {
    val a = track("1", "Clair de Lune", "Claude Debussy")
    val b = track("2", "Gymnopédie No. 1", "Erik Satie")
    val matches = findInLibrary("gymnopedie", listOf(a, b), listOf(LibraryPlaylist("Calma", listOf(a, b))))
    assertTrue(matches.any { it.tracks == listOf(b) })
    val byPlaylist = findInLibrary("calma", listOf(a, b), listOf(LibraryPlaylist("Calma", listOf(a, b))))
    assertTrue(byPlaylist.any { it.kind == LibraryMatch.Kind.PLAYLIST && it.tracks.size == 2 })
    assertTrue(findInLibrary("zzz nada", listOf(a, b), emptyList()).isEmpty())
  }
}

class AssistantGeminiTest {
  @Test fun `reply text is read and trimmed`() {
    val body = """{"candidates":[{"content":{"parts":[{"text":" hello "}]}}]}"""
    assertEquals("hello", geminiReplyText(body))
  }

  @Test fun `a reply cut at the limit is discarded`() {
    val body = """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"half"}]}}]}"""
    assertEquals(AssistantFailure.CUT_OFF, assertFailsWith<AssistantFailureException> { geminiReplyText(body) }.failure)
  }

  @Test fun `an empty or broken body is no reply`() {
    assertEquals(AssistantFailure.NO_REPLY, assertFailsWith<AssistantFailureException> { geminiReplyText("{}") }.failure)
    assertEquals(AssistantFailure.NO_REPLY, assertFailsWith<AssistantFailureException> { geminiReplyText("not json") }.failure)
  }

  @Test fun `http status maps to a failure`() {
    assertEquals(AssistantFailure.KEY_REFUSED, failureForHttpStatus(401))
    assertEquals(AssistantFailure.KEY_REFUSED, failureForHttpStatus(403))
    assertEquals(AssistantFailure.QUOTA, failureForHttpStatus(429))
    assertEquals(AssistantFailure.SERVER, failureForHttpStatus(500))
  }

  @Test fun `request json carries the tools and the user turn`() {
    val json = geminiRequestJson("ctx", org.json.JSONArray().put(geminiUserTurn("oi")))
    assertTrue(json.has("contents"))
    assertNotNull(geminiTools())
  }
}

class AssistantHistoryTest {
  @Test fun `history survives a round trip`() {
    val list = listOf(
      AssistantMessage("1", MessageSender.USER, "oi", 10),
      AssistantMessage("2", MessageSender.ASSISTANT, "olá", 20, toolReport = ToolExecutionReport("t", mapOf("a" to "b"), true, "ok")),
      AssistantMessage("3", MessageSender.SYSTEM, "erro", 30, failure = AssistantFailure.QUOTA)
    )
    val back = assistantHistoryFromJson(assistantHistoryToJson(list))
    assertEquals(list.map { it.text }, back.map { it.text })
    assertEquals(AssistantFailure.QUOTA, back[2].failure)
    assertEquals("ok", back[1].toolReport?.resultSummary)
  }

  @Test fun `broken history gives an empty list`() {
    assertTrue(assistantHistoryFromJson("{not json").isEmpty())
  }

  @Test fun `history keeps only the last messages`() {
    val many = (1..ASSISTANT_HISTORY_LIMIT + 20).map { AssistantMessage("$it", MessageSender.USER, "m$it", it.toLong()) }
    assertTrue(assistantHistoryFromJson(assistantHistoryToJson(many)).size <= ASSISTANT_HISTORY_LIMIT)
  }
}

class AssistantEngineTest {
  private fun engine(host: FakeHost, store: ApiKeyStore = tempKeyStore().first, transport: GeminiTransport = GeminiTransport { _, _ -> throw IOException("offline") }) =
    AssistantEngine(TestTexts, host, store, transport)

  @Test fun `next runs on the player and reports a tool`() = runBlocking {
    val host = FakeHost()
    val reply = engine(host).processUserMessage("próxima")
    assertEquals(listOf(PlayerCommand.NEXT), host.commands)
    assertNotNull(reply.toolReport)
  }

  @Test fun `a command the player could not run says so`() = runBlocking {
    val host = FakeHost().apply { commandResult = null }
    val reply = engine(host).processUserMessage("pausar")
    assertEquals(listOf(PlayerCommand.PAUSE), host.commands)
    assertFalse(reply.toolReport?.success ?: false)
  }

  @Test fun `tone moves by 3 dB and undo restores`() = runBlocking {
    val host = FakeHost()
    val e = engine(host)
    e.processUserMessage("mais graves")
    assertEquals(3f, host.eq.bassDb)
    e.processUserMessage("desfazer")
    assertEquals(0f, host.eq.bassDb)
  }

  @Test fun `timer seconds are clamped to 5 and 7200`() = runBlocking {
    val host = FakeHost()
    val e = engine(host)
    e.processUserMessage("timer de 1 segundo")
    e.processUserMessage("timer de 9999 minutos")
    e.processUserMessage("timer de 5 minutos")
    assertEquals(listOf(5, 7200, 300), host.timers.map { it.third })
  }

  @Test fun `play asks first and only plays after yes`() = runBlocking {
    val host = FakeHost().apply { lib = LibrarySnapshot(listOf(track("1", "Clair de Lune")), emptyList()) }
    val e = engine(host)
    val ask = e.processUserMessage("tocar clair de lune")
    assertTrue("must not play before the user confirms: ${ask.text}", host.played.isEmpty())
    e.processUserMessage("sim")
    assertEquals(1, host.played.size)
  }

  @Test fun `no answer cancels the pending action`() = runBlocking {
    val host = FakeHost().apply { lib = LibrarySnapshot(listOf(track("1", "Clair de Lune")), emptyList()) }
    val e = engine(host)
    e.processUserMessage("tocar clair de lune")
    e.processUserMessage("não")
    assertTrue(host.played.isEmpty())
  }

  @Test fun `with a key the online reply is used and the key goes to the transport`() = runBlocking {
    val (store, _) = tempKeyStore()
    store.save("abc123")
    var seenKey: String? = null
    val e = engine(FakeHost(), store) { key, _ ->
      seenKey = key
      200 to """{"candidates":[{"content":{"parts":[{"text":"resposta"}]}}]}"""
    }
    assertEquals("resposta", e.processUserMessage("qual a história do jazz").text)
    assertEquals("abc123", seenKey)
  }

  @Test fun `online failures become typed failures`() = runBlocking {
    val (store, _) = tempKeyStore()
    store.save("abc123")
    assertEquals(AssistantFailure.KEY_REFUSED, engine(FakeHost(), store) { _, _ -> 403 to "{}" }.processUserMessage("conte uma história").failure)
    assertEquals(AssistantFailure.QUOTA, engine(FakeHost(), store) { _, _ -> 429 to "{}" }.processUserMessage("conte uma história").failure)
    assertEquals(AssistantFailure.NO_CONNECTION, engine(FakeHost(), store) { _, _ -> throw IOException("x") }.processUserMessage("conte uma história").failure)
  }

  @Test fun `online off never calls the transport`() = runBlocking {
    val (store, _) = tempKeyStore()
    store.save("abc123")
    var calls = 0
    val e = engine(FakeHost(), store) { _, _ -> calls++; 200 to "{}" }
    e.processUserMessage("conte uma história", useGeminiIfAvailable = false)
    assertEquals(0, calls)
  }
}

class ApiKeyStoreTest {
  @Test fun `the key is stored encrypted and read back`() {
    val (store, file) = tempKeyStore()
    assertNull(store.load())
    store.save("  secret-key-1  ")
    assertEquals("secret-key-1", store.load())
    assertFalse("the file must not hold the plain key", file.readText().contains("secret-key-1"))
    assertTrue(store.hasStored())
    store.clear()
    assertNull(store.load())
    assertFalse(store.hasStored())
  }

  @Test fun `blank placeholder and spaced keys are refused`() {
    val (store, _) = tempKeyStore()
    assertFailsWith<IllegalArgumentException> { store.save("   ") }
    assertFailsWith<IllegalArgumentException> { store.save("MY_GEMINI_API_KEY") }
    assertFailsWith<IllegalArgumentException> { store.save("two words") }
  }

  @Test fun `the environment variable is the developer fallback and does not count as stored`() {
    val (store, _) = tempKeyStore(env = "env-key")
    assertEquals("env-key", store.load())
    assertFalse(store.hasStored())
  }

  @Test fun `a corrupted file gives no key`() {
    val (store, file) = tempKeyStore()
    file.writeText("garbage")
    assertNull(store.load())
  }

  @Test fun `real DPAPI round trip on Windows`() {
    if (!System.getProperty("os.name").startsWith("Windows")) return
    val (store, file) = tempKeyStore(protector = DpapiKeyProtector)
    store.save("dpapi-secret-9")
    assertEquals("dpapi-secret-9", store.load())
    assertFalse(file.readText().contains("dpapi-secret-9"))
  }

  @Test fun `the installer never holds a key`() {
    val gradle = File("build.gradle.kts").takeIf { it.isFile }?.readText().orEmpty()
    assertFalse(Regex("AIza[0-9A-Za-z_-]{20,}").containsMatchIn(gradle))
  }
}

class SapiTextTest {
  @Test fun `markers are dropped before speaking`() {
    assertEquals("Olá mundo", speakableReply("**Olá**   *mundo*"))
  }

  @Test fun `only a real reply to a voice question is spoken`() {
    val ok = AssistantMessage("1", MessageSender.ASSISTANT, "oi", 0)
    assertTrue(shouldSpeakReply(true, ok))
    assertFalse(shouldSpeakReply(false, ok))
    assertFalse(shouldSpeakReply(true, ok.copy(failure = AssistantFailure.SERVER)))
    assertFalse(shouldSpeakReply(true, ok.copy(sender = MessageSender.SYSTEM)))
    assertFalse(shouldSpeakReply(true, ok.copy(text = "**")))
  }

  @Test fun `ssml escapes text and carries pitch and rate`() {
    val ssml = buildSsml("a < b & c", "pt-BR", 1.1f, 0.9f)
    assertTrue("a &lt; b &amp; c" in ssml)
    assertTrue("pitch=\"+10%\"" in ssml)
    assertTrue("rate=\"-10%\"" in ssml)
    assertTrue("xml:lang=\"pt-BR\"" in ssml)
  }

  @Test fun `trivia keeps two sentences`() {
    assertEquals("Um. Dois.", spokenTrivia("Um. Dois. Três."))
    assertEquals("Só um.", spokenTrivia("Só um."))
  }

  @Test fun `language tag follows the app language`() {
    assertEquals("pt-BR", Sapi.languageTag(java.util.Locale("pt", "PT")))
    assertEquals("en-US", Sapi.languageTag(java.util.Locale("en")))
  }
}

class CuriosityConfigTest {
  @Test fun `config round trip`() {
    val c = CuriosityConfig(enabled = true, timing = CuriosityTiming.BOTH, displayMode = CuriosityDisplayMode.VOICE_ONLY, voicePreset = VoicePreset.DEEP, pitch = 0.8f, speechRate = 1.2f)
    assertEquals(c, configFromJson(configToJson(c)))
  }

  @Test fun `unknown names and out of range values fall back`() {
    val d = CuriosityConfig()
    val c = configFromJson(JSONObject().put("timing", "NOPE").put("pitch", 9.0).put("rate", 0.0).toString())!!
    assertEquals(d.timing, c.timing)
    assertEquals(2f, c.pitch)
    assertEquals(0.5f, c.speechRate)
  }

  @Test fun `unreadable config gives null`() {
    assertNull(configFromJson("{{"))
  }

  @Test fun `the repository follows the injected texts and knows only built in tracks`() {
    val repo = CuriosityRepository(string = { "one two three" }, array = { listOf("src") })
    assertEquals(5, repo.getAllCuriosities().size)
    val c = repo.getCuriosityForTrack(track("track_satie", "x"))
    assertNotNull(c)
    assertEquals(3, c!!.wordCount)
    assertNull(repo.getCuriosityForTrack(track("unknown", "x")))
  }
}

class LocalTimerTest {
  @Test fun `remaining time and format`() {
    val t = LocalTimer("1", "x", endsAtMs = 1_000_000)
    assertEquals(90L, t.remainingSeconds(1_000_000 - 90_000))
    assertEquals("1:30", t.formattedTime(1_000_000 - 90_000))
    assertEquals("1:01:01", t.formattedTime(1_000_000 - 3_661_000))
    assertEquals(0L, t.remainingSeconds(2_000_000))
  }
}

private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
  try { block() } catch (e: Throwable) { if (e is T) return e else throw e }
  throw AssertionError("expected " + T::class.simpleName)
}

class ComposerNewLineTest {
  @Test fun shiftEnterInsertsAtCursor() {
    val v = androidx.compose.ui.text.input.TextFieldValue("ab", androidx.compose.ui.text.TextRange(1))
    val r = insertNewLine(v)
    assertEquals("a\nb", r.text)
    assertEquals(2, r.selection.start)
  }

  @Test fun shiftEnterReplacesSelection() {
    val v = androidx.compose.ui.text.input.TextFieldValue("abcd", androidx.compose.ui.text.TextRange(1, 3))
    assertEquals("a\nd", insertNewLine(v).text)
  }
}
