package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.assistant.AssistantEngine
import com.example.data.assistant.PlayerCommand
import com.example.data.assistant.playerCommandFor
import com.example.data.audio.AudioEngineManager
import com.example.data.repository.CuriosityRepository
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which sentences become player commands, and what the engine reports after running them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AssistantCommandsTest {

  private fun command(text: String) = playerCommandFor(text.lowercase())?.first

  // --- parsing ---

  @Test
  fun `next commands`() {
    listOf("próxima faixa", "proxima", "pular", "próximo").forEach {
      assertEquals(it, PlayerCommand.NEXT, command(it))
    }
  }

  @Test
  fun `previous commands`() {
    listOf("anterior", "faixa anterior", "voltar").forEach {
      assertEquals(it, PlayerCommand.PREVIOUS, command(it))
    }
  }

  @Test
  fun `play and pause commands`() {
    listOf("tocar", "play", "continuar", "reproduzir").forEach {
      assertEquals(it, PlayerCommand.PLAY, command(it))
    }
    listOf("pausar", "pause", "pausa").forEach {
      assertEquals(it, PlayerCommand.PAUSE, command(it))
    }
  }

  @Test
  fun `shuffle, repeat and favorite commands, with and without accents`() {
    listOf("aleatório", "aleatorio", "embaralhar", "shuffle").forEach {
      assertEquals(it, PlayerCommand.TOGGLE_SHUFFLE, command(it))
    }
    listOf("repetir", "repetição", "repeat").forEach {
      assertEquals(it, PlayerCommand.CYCLE_REPEAT, command(it))
    }
    listOf("favoritar", "curtir").forEach {
      assertEquals(it, PlayerCommand.FAVORITE, command(it))
    }
  }

  @Test
  fun `tool names match the command`() {
    assertEquals("next_track", playerCommandFor("próxima")?.second)
    assertEquals("toggle_shuffle", playerCommandFor("aleatório")?.second)
    assertEquals("cycle_repeat", playerCommandFor("repetir")?.second)
    assertEquals("toggle_favorite", playerCommandFor("favoritar")?.second)
  }

  @Test
  fun `other requests are not player commands`() {
    listOf(
      "que música toca?",
      "tocando agora",
      "analisar música tocando",
      "timer de 15 minutos",
      "diagnóstico de áudio",
      "equalizador flat",
      "olá",
      "help",
      "blablabla"
    ).forEach { assertNull(it, command(it)) }
  }

  @Test
  fun `a command word inside another word is ignored`() {
    assertNull(command("a rotação do disco"))
    assertNull(command("pausado há pouco")) // "pausado" is not "pausar"
    assertNull(command("tocante demais"))
  }

  // --- engine ---

  private fun engine(onPlayerCommand: suspend (PlayerCommand) -> String?): AssistantEngine {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return AssistantEngine(
      musicRepository = MusicRepository(context),
      curiosityRepository = CuriosityRepository(),
      audioEngineManager = AudioEngineManager(context),
      onTimerCreated = {},
      onTimerCancelled = {},
      onPlayerCommand = onPlayerCommand,
      // The test blocks the main thread with runBlocking, so the callbacks must not wait for it.
      callbackContext = kotlinx.coroutines.Dispatchers.Unconfined
    )
  }

  @Test
  fun `the engine runs the command and reports the real result`() {
    val received = mutableListOf<PlayerCommand>()
    val engine = engine { received += it; "Aleatório ligado" }

    val reply = runBlocking { engine.processUserMessage("aleatório") }

    assertEquals(listOf(PlayerCommand.TOGGLE_SHUFFLE), received)
    assertEquals("Aleatório ligado", reply.text)
    val report = reply.toolReport
    assertNotNull(report)
    assertTrue(report!!.success)
    assertEquals("toggle_shuffle", report.toolName)
    assertEquals("Aleatório ligado", report.resultSummary)
  }

  @Test
  fun `the engine reports failure when the command could not run`() {
    val engine = engine { null }

    val reply = runBlocking { engine.processUserMessage("favoritar") }

    assertTrue(reply.text.startsWith("Não consegui"))
    assertFalse(reply.toolReport!!.success)
  }

  @Test
  fun `sentences that are not player commands never reach the player`() {
    val received = mutableListOf<PlayerCommand>()
    val engine = engine { received += it; "ok" }

    runBlocking {
      engine.processUserMessage("que música toca?")
      engine.processUserMessage("timer de 5 minutos")
      engine.processUserMessage("blablabla")
    }

    assertTrue(received.isEmpty())
  }

  @Test
  fun `help lists the player commands`() {
    val reply = runBlocking { engine { "ok" }.processUserMessage("ajuda") }
    assertTrue(reply.text.contains("próxima"))
    assertTrue(reply.text.contains("aleatório"))
    assertTrue(reply.text.contains("favoritar"))
  }
}
