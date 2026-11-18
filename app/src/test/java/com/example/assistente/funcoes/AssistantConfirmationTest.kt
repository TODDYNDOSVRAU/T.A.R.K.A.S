package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.assistant.AssistantActions
import com.example.data.assistant.AssistantEngine
import com.example.data.assistant.LibraryPlaylist
import com.example.data.assistant.LibrarySnapshot
import com.example.data.audio.AudioEngineManager
import com.example.data.model.Track
import com.example.data.repository.CuriosityRepository
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Records what the engine asks the app to do. Nothing real is changed. */
private class RecordingActions(private val tracks: List<Track>) : AssistantActions {
  val calls = mutableListOf<String>()
  var playedIds: List<String> = emptyList()

  override fun library() = LibrarySnapshot(tracks, emptyList<LibraryPlaylist>())
  override fun playTracks(tracks: List<Track>): Boolean {
    calls += "play"; playedIds = tracks.map { it.id }; return true
  }
  override fun queueTracks(tracks: List<Track>, asNext: Boolean): Boolean { calls += "queue:$asNext"; return true }
  override fun clearQueue(): Boolean { calls += "clearQueue"; return true }
  override fun sleepIn(minutes: Int): Boolean { calls += "sleepIn:$minutes"; return true }
  override fun sleepAtEndOfTrack(): Boolean { calls += "sleepEnd"; return true }
  override fun cancelSleep(): Boolean { calls += "cancelSleep"; return true }
  override fun setSpatial(on: Boolean): Boolean { calls += "spatial:$on"; return true }
  override fun setVolumePercent(percent: Int): Boolean { calls += "volume:$percent"; return true }
}

/** The engine must ask before every change, and do exactly what it asked, only after "sim". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AssistantConfirmationTest {

  private val tarkus = listOf(
    Track(id = "t1", title = "Tarkus: Eruption Suite", artist = "ELP", album = "Tarkus", durationMs = 1000L, genre = "", era = ""),
    Track(id = "t2", title = "Stones of Years", artist = "ELP", album = "Tarkus", durationMs = 1000L, genre = "", era = ""),
    Track(id = "t3", title = "Stones of Fire", artist = "Other", album = "Fire", durationMs = 1000L, genre = "", era = "")
  )

  private fun engine(actions: AssistantActions): AssistantEngine {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return AssistantEngine(
      context = context,
      musicRepository = MusicRepository(context),
      curiosityRepository = CuriosityRepository(context),
      audioEngineManager = AudioEngineManager(context),
      onTimerCreated = {},
      onTimerCancelled = {},
      callbackContext = Dispatchers.Unconfined,
      actions = actions
    )
  }

  private fun say(engine: AssistantEngine, text: String): String = runBlocking {
    engine.processUserMessage(text, useGeminiIfAvailable = false).text
  }

  @Test
  fun playingAnAlbumWaitsForYes() {
    val actions = RecordingActions(tarkus)
    val engine = engine(actions)
    val question = say(engine, "tocar tarkus")
    assertTrue("the question must be shown", question.contains("?"))
    assertEquals("nothing changes before the answer", emptyList<String>(), actions.calls)

    say(engine, "sim")
    assertEquals(listOf("play"), actions.calls)
    assertEquals(listOf("t1", "t2"), actions.playedIds)
  }

  @Test
  fun noCancelsAndNothingRuns() {
    val actions = RecordingActions(tarkus)
    val engine = engine(actions)
    say(engine, "tocar tarkus")
    say(engine, "não")
    say(engine, "sim")
    assertEquals("a cancelled request must not run later", emptyList<String>(), actions.calls)
  }

  @Test
  fun anotherMessageRepeatsTheQuestionAndKeepsIt() {
    val actions = RecordingActions(tarkus)
    val engine = engine(actions)
    val question = say(engine, "tocar tarkus")
    val again = say(engine, "olá")
    assertTrue("the question is repeated", again.startsWith(question.substringBefore("?")))
    assertEquals(emptyList<String>(), actions.calls)
    say(engine, "sim")
    assertEquals(listOf("play"), actions.calls)
  }

  @Test
  fun ambiguousNameDoesNotPlayAndAsksForTheExactName() {
    val actions = RecordingActions(tarkus)
    val engine = engine(actions)
    say(engine, "tocar stones")
    say(engine, "sim")
    assertEquals("several matches must never be played", emptyList<String>(), actions.calls)
  }

  @Test
  fun queueAndSleepAndVolumeAllNeedYes() {
    val actions = RecordingActions(tarkus)
    val engine = engine(actions)
    say(engine, "adicionar tarkus na fila")
    say(engine, "sim")
    say(engine, "dormir em 10 minutos")
    say(engine, "sim")
    say(engine, "volume 40")
    say(engine, "sim")
    assertEquals(listOf("queue:false", "sleepIn:10", "volume:40"), actions.calls)
  }

  @Test
  fun spatialNeedsYes() {
    val actions = RecordingActions(tarkus)
    val engine = engine(actions)
    say(engine, "ligar o áudio espacial")
    assertEquals(emptyList<String>(), actions.calls)
    say(engine, "Sim.")
    assertEquals(listOf("spatial:true"), actions.calls)
  }
}
