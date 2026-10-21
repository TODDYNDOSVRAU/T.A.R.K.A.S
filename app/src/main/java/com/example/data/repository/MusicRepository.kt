package com.example.data.repository

import android.content.Context
import com.example.data.audio.AudioSynthesizer
import com.example.data.audius.AudiusApiService
import com.example.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MusicRepository(private val context: Context) {

  private val _tracks = MutableStateFlow<List<Track>>(emptyList())
  val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

  private val _currentTrack = MutableStateFlow<Track?>(null)
  val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

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
        colorHex = 0xFF00E5FF
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
        colorHex = 0xFF38BDF8
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
        colorHex = 0xFFF59E0B
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
        colorHex = 0xFF10B981
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
        colorHex = 0xFFA855F7
      )
    )

    _tracks.value = demoTracks
    if (_currentTrack.value == null) {
      _currentTrack.value = demoTracks.first()
    }
  }

  suspend fun searchAudius(query: String): List<Track> {
    if (query.isBlank()) return emptyList()
    _isSearching.value = true
    try {
      val results = AudiusApiService.searchTracks(query)
      if (results.isNotEmpty()) {
        val existingIds = _tracks.value.map { it.id }.toSet()
        val newTracks = results.filter { it.id !in existingIds }
        _tracks.value = results + _tracks.value
      }
      return results
    } finally {
      _isSearching.value = false
    }
  }

  suspend fun loadAudiusTrending(): List<Track> {
    _isSearching.value = true
    try {
      val results = AudiusApiService.getTrendingTracks()
      if (results.isNotEmpty()) {
        val existingIds = _tracks.value.map { it.id }.toSet()
        val newTracks = results.filter { it.id !in existingIds }
        _tracks.value = results + _tracks.value
      }
      return results
    } finally {
      _isSearching.value = false
    }
  }

  fun selectTrack(track: Track) {
    _currentTrack.value = track
  }

  fun selectNextTrack(): Track? {
    val list = _tracks.value
    if (list.isEmpty()) return null
    val current = _currentTrack.value
    val currentIndex = list.indexOfFirst { it.id == current?.id }
    val nextIndex = if (currentIndex != -1 && currentIndex < list.size - 1) currentIndex + 1 else 0
    val next = list[nextIndex]
    _currentTrack.value = next
    return next
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

  fun addLocalTrack(title: String, artist: String, uriString: String, durationMs: Long) {
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
      colorHex = 0xFF14B8A6
    )
    _tracks.value = listOf(newTrack) + _tracks.value
    _currentTrack.value = newTrack
  }
}
