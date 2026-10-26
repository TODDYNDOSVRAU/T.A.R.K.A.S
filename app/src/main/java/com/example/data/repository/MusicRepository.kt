package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.audio.AudioSynthesizer
import com.example.data.model.Track
import com.example.data.model.TrackJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
    this.demoTracks = demoTracks
    publish()
  }

  private var demoTracks: List<Track> = emptyList()
  private var pickedTracks: List<Track> = emptyList()

  private val _deviceTracks = MutableStateFlow<List<Track>>(emptyList())
  /** The music on the device, from Android's media library (needs the music permission). */
  val deviceTracks: StateFlow<List<Track>> = _deviceTracks.asStateFlow()

  /** The library: files picked one by one, the device's music, then the demo tones. */
  private fun publish() {
    _tracks.value = pickedTracks + _deviceTracks.value + demoTracks
  }

  /**
   * Reads every song in Android's media library (title, artist, album, duration, cover). Returns how
   * many were found. Fast: only the media database is read, not the files.
   */
  suspend fun scanDevice(): Int = withContext(Dispatchers.IO) {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM,
      MediaStore.Audio.Media.ALBUM_ID,
      MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.YEAR
    )
    val found = ArrayList<Track>()
    try {
      context.contentResolver.query(
        collection, projection,
        "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?",
        arrayOf(MIN_SONG_MS.toString()),
        null
      )?.use { c ->
        val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        while (c.moveToNext()) {
          val mediaId = c.getLong(id)
          val artistName = c.getString(artist)?.takeUnless { it == MediaStore.UNKNOWN_STRING } ?: "Artista desconhecido"
          found += Track(
            id = "ms:$mediaId",
            title = c.getString(title) ?: "Sem título",
            artist = artistName,
            album = c.getString(album)?.takeUnless { it == MediaStore.UNKNOWN_STRING } ?: "",
            durationMs = c.getLong(duration),
            genre = "",
            era = c.getInt(year).takeIf { it > 0 }?.toString() ?: "",
            isLocalFile = true,
            filePath = ContentUris.withAppendedId(collection, mediaId).toString(),
            artworkUrl = ContentUris.withAppendedId(ALBUM_ART, c.getLong(albumId)).toString(),
            colorHex = 0xFF2F8FA8
          )
        }
      }
    } catch (e: Exception) {
      // The permission was taken back, or the media library refused the query: show what was read.
      android.util.Log.w("MusicRepository", "Device music scan failed", e)
    }
    // Sorted here, as people read titles (accents and case ignored); the media library refuses a locale sort.
    val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
    found.sortWith { a, b -> collator.compare(a.title, b.title) }
    _deviceTracks.value = found
    publish()
    found.size
  }

  fun selectTrack(track: Track) {
    _currentTrack.value = track
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
    pickedTracks = listOf(newTrack) + pickedTracks
    savePicked()
    publish()
    return newTrack
  }

  // Files picked one by one are kept across launches (the app holds a lasting permission for each).
  private val prefs = context.getSharedPreferences("tarkas_library_files", Context.MODE_PRIVATE)

  private fun savePicked() {
    prefs.edit().putString("picked", TrackJson.listToJson(pickedTracks).toString()).apply()
  }

  init {
    pickedTracks = runCatching { TrackJson.listFromJson(JSONArray(prefs.getString("picked", "[]"))) }.getOrDefault(emptyList())
    publish()
  }

  private companion object {
    /** Shorter files are ringtones, notification sounds and voice notes, not songs. */
    const val MIN_SONG_MS = 30_000L
    val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
  }
}
