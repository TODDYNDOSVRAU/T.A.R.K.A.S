package com.example

import com.example.data.assistant.AssistantRequest
import com.example.data.assistant.LibraryMatch
import com.example.data.assistant.LibraryPlaylist
import com.example.data.assistant.assistantRequestFor
import com.example.data.assistant.findInLibrary
import com.example.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parser and the library matcher are pure: they need no Android context. */
class AssistantRequestsTest {

  private fun track(id: String, title: String, artist: String = "Artist", album: String = "") =
    Track(id = id, title = title, artist = artist, album = album, durationMs = 1000L, genre = "", era = "")

  // --- parsing: a request is recognised only from a clear phrasing ---

  @Test
  fun playRequestNeedsAName() {
    assertEquals(AssistantRequest.PlayLibrary("tarkus"), assistantRequestFor("tocar tarkus"))
    assertEquals(AssistantRequest.PlayLibrary("clair de lune"), assistantRequestFor("tocar a clair de lune"))
    assertNull(assistantRequestFor("tocar"))
    assertNull("'tocar agora' is the player command, not a name", assistantRequestFor("tocar agora"))
    assertNull(assistantRequestFor("tocar que música é essa"))
  }

  @Test
  fun queueRequests() {
    assertEquals(AssistantRequest.QueueLibrary("tarkus", asNext = false), assistantRequestFor("adicionar tarkus na fila"))
    assertEquals(AssistantRequest.QueueLibrary("tubular", asNext = true), assistantRequestFor("tocar tubular depois"))
    assertEquals(AssistantRequest.ClearQueue, assistantRequestFor("limpar a fila"))
  }

  @Test
  fun sleepRequests() {
    assertEquals(AssistantRequest.SleepIn(15), assistantRequestFor("dormir em 15 minutos"))
    assertEquals(AssistantRequest.SleepAtEndOfTrack, assistantRequestFor("dormir no fim da música"))
    assertEquals(AssistantRequest.CancelSleep, assistantRequestFor("cancelar timer de dormir"))
    assertNull("out of range", assistantRequestFor("dormir em 0 minutos"))
    assertNull("no time given", assistantRequestFor("vou dormir"))
    assertNull("plain pause is left to the player command", assistantRequestFor("pausar a música"))
  }

  @Test
  fun spatialAndVolumeRequests() {
    assertEquals(AssistantRequest.Spatial(on = false), assistantRequestFor("desligar o áudio espacial"))
    assertEquals(AssistantRequest.Spatial(on = true), assistantRequestFor("ligar áudio espacial"))
    assertEquals(AssistantRequest.Volume(40), assistantRequestFor("volume 40"))
    assertEquals(AssistantRequest.Volume(0), assistantRequestFor("volume para 0%"))
    assertNull("above 100 is not a volume", assistantRequestFor("volume 150"))
  }

  @Test
  fun unrelatedSentencesAreNotRequests() {
    assertNull(assistantRequestFor("que horas são"))
    assertNull(assistantRequestFor("olá"))
  }

  // --- matching: exact names first, several results are returned for the user to choose ---

  private val tarkusTracks = listOf(
    track("t1", "Tarkus: Eruption Suite", "Emerson, Lake & Palmer", "Tarkus"),
    track("t2", "Stones of Years", "Emerson, Lake & Palmer", "Tarkus"),
    track("t3", "Clair de Lune", "Claude Debussy", "Suite bergamasque")
  )

  @Test
  fun exactAlbumNameWinsOverPartialTrackNames() {
    val matches = findInLibrary("tarkus", tarkusTracks, emptyList())
    assertEquals(1, matches.size)
    assertEquals(LibraryMatch.Kind.ALBUM, matches[0].kind)
    assertEquals(listOf("t1", "t2"), matches[0].tracks.map { it.id })
  }

  @Test
  fun matchingIgnoresAccentsAndCase() {
    val matches = findInLibrary("CLAIR DE LUNE", tarkusTracks, emptyList())
    assertEquals(listOf("t3"), matches.single().tracks.map { it.id })
  }

  @Test
  fun playlistsAreFound() {
    val playlists = listOf(LibraryPlaylist("Noite Calma", listOf(tarkusTracks[2])))
    val matches = findInLibrary("noite calma", tarkusTracks, playlists)
    assertEquals(LibraryMatch.Kind.PLAYLIST, matches.single().kind)
  }

  @Test
  fun unknownNameFindsNothing() {
    assertEquals(emptyList<LibraryMatch>(), findInLibrary("inexistente", tarkusTracks, emptyList()))
  }

  @Test
  fun partialNameWithSeveralTargetsIsAmbiguous() {
    val matches = findInLibrary("stones", tarkusTracks, emptyList())
    assertEquals(1, matches.size)
    val ambiguous = findInLibrary("e", tarkusTracks, emptyList())
    assertTrue("a partial name must not pick one result silently", ambiguous.size > 1)
  }
}
