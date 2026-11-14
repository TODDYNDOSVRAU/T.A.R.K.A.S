package com.example.data.repository

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.example.R
import com.example.data.model.Curiosity
import com.example.data.model.Track
import com.example.data.model.VerificationStatus

/**
 * The verified curiosities of the built-in tracks. The texts are resources, so they follow the app
 * language (English or Portuguese). A track is recognized by its id, whatever the language.
 */
class CuriosityRepository(context: Context) {

  // Only the Resources are kept, not the Context: this repository lives in a static holder (AppGraph).
  private val resources = context.resources

  private class Entry(
    val trackId: String,
    val trackTitle: String,
    val artist: String,
    @StringRes val trivia: Int,
    @StringRes val facts: Int,
    @ArrayRes val sources: Int,
    val status: VerificationStatus,
    val verifiedDate: String
  )

  private val entries = listOf(
    Entry(
      trackId = "track_tarkus",
      trackTitle = "Tarkus: Eruption Suite",
      artist = "Emerson, Lake & Palmer",
      trivia = R.string.curiosity_tarkus_trivia,
      facts = R.string.curiosity_tarkus_facts,
      sources = R.array.curiosity_tarkus_sources,
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-15"
    ),
    Entry(
      trackId = "track_tubular",
      trackTitle = "Tubular Bells (Part One)",
      artist = "Mike Oldfield",
      trivia = R.string.curiosity_tubular_trivia,
      facts = R.string.curiosity_tubular_facts,
      sources = R.array.curiosity_tubular_sources,
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-10"
    ),
    Entry(
      trackId = "track_debussy",
      trackTitle = "Clair de Lune",
      artist = "Claude Debussy",
      trivia = R.string.curiosity_debussy_trivia,
      facts = R.string.curiosity_debussy_facts,
      sources = R.array.curiosity_debussy_sources,
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-09-28"
    ),
    Entry(
      trackId = "track_satie",
      trackTitle = "Gymnopédie No. 1",
      artist = "Erik Satie",
      trivia = R.string.curiosity_satie_trivia,
      facts = R.string.curiosity_satie_facts,
      sources = R.array.curiosity_satie_sources,
      status = VerificationStatus.VERIFIED_FACT,
      verifiedDate = "2026-10-02"
    ),
    Entry(
      trackId = "track_sweep",
      trackTitle = "Soundcheck Sweep (20Hz-20kHz)",
      artist = "Tarkas Acoustic Lab",
      trivia = R.string.curiosity_sweep_trivia,
      facts = R.string.curiosity_sweep_facts,
      sources = R.array.curiosity_sweep_sources,
      status = VerificationStatus.TECHNICAL_SPEC,
      verifiedDate = "2026-10-18"
    )
  )

  // Read on each call, so the text always matches the language the app is showing now.
  private fun build(entry: Entry): Curiosity {
    val trivia = resources.getString(entry.trivia).trim()
    return Curiosity(
      trackId = entry.trackId,
      trackTitle = entry.trackTitle,
      artist = entry.artist,
      shortTrivia = trivia,
      wordCount = trivia.split(Regex("\\s+")).size,
      documentedFacts = resources.getString(entry.facts).trim(),
      sources = resources.getStringArray(entry.sources).toList(),
      status = entry.status,
      verifiedDate = entry.verifiedDate
    )
  }

  /**
   * The verified curiosity of a track, or null when there is none. Nothing is shown, spoken or put
   * in the notification for a track without one: never invent trivia or sources.
   */
  fun getCuriosityForTrack(track: Track): Curiosity? =
    entries.firstOrNull { it.trackId == track.id }?.let(::build)

  fun getAllCuriosities(): List<Curiosity> = entries.map(::build)
}
