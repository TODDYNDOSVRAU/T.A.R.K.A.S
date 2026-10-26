package com.example.data.repository

import android.content.Context
import com.example.data.audio.AudioSynthesizer
import com.example.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicRepository(private val context: Context) {

  private val _tracks = MutableStateFlow<List<Track>>(emptyList())
  val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

  private val _currentTrack = MutableStateFlow<Track?>(null)
  val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

  suspend fun initializeCatalog() {
    val fileMap = AudioSynthesizer.ensureDemoTracksExist(context)

    val demoTracks = listOf(
      Track(
        id = "track_tarkus",
        title = "Tarkus: Eruption Suite",
        artist = "Emerson, Lake & Palmer",
        album = "Tarkus (1971)",
        durationMs = 16000L,
        genre = "Rock Progressivo",
        era = "1971",
        filePath = fileMap["track_tarkus"],
        colorHex = 0xFF46C9D3,
        isDemo = true
      ),
      Track(
        id = "track_tubular",
        title = "Tubular Bells (Part One)",
        artist = "Mike Oldfield",
        album = "Tubular Bells (1973)",
        durationMs = 16000L,
        genre = "Minimalismo / Prog",
        era = "1973",
        filePath = fileMap["track_tubular"],
        colorHex = 0xFF2F8FA8,
        isDemo = true
      ),
      Track(
        id = "track_debussy",
        title = "Clair de Lune",
        artist = "Claude Debussy",
        album = "Suite bergamasque (1905)",
        durationMs = 18000L,
        genre = "Impressionismo Clássico",
        era = "1905",
        filePath = fileMap["track_debussy"],
        colorHex = 0xFF0E7C86,
        isDemo = true
      ),
      Track(
        id = "track_satie",
        title = "Gymnopédie No. 1",
        artist = "Erik Satie",
        album = "Trois Gymnopédies (1888)",
        durationMs = 18000L,
        genre = "Música Ambiente / Modal",
        era = "1888",
        filePath = fileMap["track_satie"],
        colorHex = 0xFF5B7C99,
        isDemo = true
      ),
      Track(
        id = "track_sweep",
        title = "Soundcheck Sweep (20Hz-20kHz)",
        artist = "Tarkas Acoustic Lab",
        album = "Audio Engineering Suite",
        durationMs = 18000L,
        genre = "Calibração Acústica",
        era = "2026",
        filePath = fileMap["track_sweep"],
        colorHex = 0xFF7A8A99,
        isDemo = true
      )
    )

    // No track is picked by itself: the mini player (the way into the player) appears only once
    // the user chooses something to play.
    _tracks.value = demoTracks
  }

  fun selectTrack(track: Track) {
    _currentTrack.value = track
  }

  fun selectPreviousTrack(): Track? {
    val list = _tracks.value
    if (list.isEmpty()) return null
    val current = _currentTrack.value
    val currentIndex = list.indexOfFirst { it.id == current?.id }
    val prevIndex = if (currentIndex > 0) currentIndex - 1 else list.size - 1
    val prev = list[prevIndex]
    _currentTrack.value = prev
    return prev
  }

  /**
   * Adds a file picked on the device to the library and returns it. The same file picked again
   * returns the track already there, so the list never holds it twice.
   */
  fun addLocalTrack(title: String, artist: String, uriString: String, durationMs: Long): Track {
    _tracks.value.firstOrNull { it.isLocalFile && it.filePath == uriString }?.let { return it }
    val newTrack = Track(
      id = "local_${System.currentTimeMillis()}",
      title = title,
      artist = artist,
      album = "Biblioteca Local",
      durationMs = durationMs,
      genre = "Áudio Local",
      era = "Local",
      isLocalFile = true,
      filePath = uriString,
      colorHex = 0xFF3A9FA8
    )
    _tracks.value = listOf(newTrack) + _tracks.value
    return newTrack
  }
}
