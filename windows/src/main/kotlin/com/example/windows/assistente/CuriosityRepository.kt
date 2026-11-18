package com.example.windows.assistente

import com.example.data.model.Track
import com.example.windows.res.Res
import com.example.windows.res.curiosity_debussy_facts
import com.example.windows.res.curiosity_debussy_sources
import com.example.windows.res.curiosity_debussy_trivia
import com.example.windows.res.curiosity_satie_facts
import com.example.windows.res.curiosity_satie_sources
import com.example.windows.res.curiosity_satie_trivia
import com.example.windows.res.curiosity_sweep_facts
import com.example.windows.res.curiosity_sweep_sources
import com.example.windows.res.curiosity_sweep_trivia
import com.example.windows.res.curiosity_tarkus_facts
import com.example.windows.res.curiosity_tarkus_sources
import com.example.windows.res.curiosity_tarkus_trivia
import com.example.windows.res.curiosity_tubular_facts
import com.example.windows.res.curiosity_tubular_sources
import com.example.windows.res.curiosity_tubular_trivia
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray

/**
 * The verified curiosities of the built-in tracks. The texts are resources, so they follow the app language.
 * A track is recognized by its id. A track without an entry has no curiosity: nothing is invented.
 */
class CuriosityRepository(
  private val string: (StringResource) -> String = { runBlocking { getString(it) } },
  private val array: (StringArrayResource) -> List<String> = { runBlocking { getStringArray(it) } }
) {
  private class Entry(
    val trackId: String,
    val trackTitle: String,
    val artist: String,
    val trivia: StringResource,
    val facts: StringResource,
    val sources: StringArrayResource,
    val status: VerificationStatus,
    val verifiedDate: String
  )

  private val entries = listOf(
    Entry("track_tarkus", "Tarkus: Eruption Suite", "Emerson, Lake & Palmer", Res.string.curiosity_tarkus_trivia,
      Res.string.curiosity_tarkus_facts, Res.array.curiosity_tarkus_sources, VerificationStatus.VERIFIED_FACT, "2026-10-15"),
    Entry("track_tubular", "Tubular Bells (Part One)", "Mike Oldfield", Res.string.curiosity_tubular_trivia,
      Res.string.curiosity_tubular_facts, Res.array.curiosity_tubular_sources, VerificationStatus.VERIFIED_FACT, "2026-10-10"),
    Entry("track_debussy", "Clair de Lune", "Claude Debussy", Res.string.curiosity_debussy_trivia,
      Res.string.curiosity_debussy_facts, Res.array.curiosity_debussy_sources, VerificationStatus.VERIFIED_FACT, "2026-09-28"),
    Entry("track_satie", "Gymnopédie No. 1", "Erik Satie", Res.string.curiosity_satie_trivia,
      Res.string.curiosity_satie_facts, Res.array.curiosity_satie_sources, VerificationStatus.VERIFIED_FACT, "2026-10-02"),
    Entry("track_sweep", "Soundcheck Sweep (20Hz-20kHz)", "Tarkas Acoustic Lab", Res.string.curiosity_sweep_trivia,
      Res.string.curiosity_sweep_facts, Res.array.curiosity_sweep_sources, VerificationStatus.TECHNICAL_SPEC, "2026-10-18")
  )

  // Read on each call, so the text always matches the language the app is showing now.
  private fun build(entry: Entry): Curiosity {
    val trivia = string(entry.trivia).trim()
    return Curiosity(
      trackId = entry.trackId,
      trackTitle = entry.trackTitle,
      artist = entry.artist,
      shortTrivia = trivia,
      wordCount = trivia.split(Regex("\\s+")).size,
      documentedFacts = string(entry.facts).trim(),
      sources = array(entry.sources),
      status = entry.status,
      verifiedDate = entry.verifiedDate
    )
  }

  fun getCuriosityForTrack(track: Track): Curiosity? = entries.firstOrNull { it.trackId == track.id }?.let(::build)

  fun getAllCuriosities(): List<Curiosity> = entries.map(::build)
}
