package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.R
import com.example.data.audio.AudioSynthesizer
import com.example.data.db.AppDatabase
import com.example.data.diagnostics.Diagnostics
import com.example.data.library.DeviceCatalog
import com.example.data.library.FolderPaths
import com.example.data.library.FolderRules
import com.example.data.library.LibraryMigration
import com.example.data.library.MediaDiff
import com.example.data.library.ScannedSong
import com.example.data.library.SerialWorker
import com.example.data.library.Stamp
import com.example.data.library.TrackIds
import com.example.data.library.toEntity
import com.example.data.library.toTrack
import com.example.data.model.Placeholders
import com.example.data.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The library the player lists: files picked one by one (kept in Room), the device's music (read from
 * Android's media library) and the demo tones. Everything the screens read comes from one immutable
 * [MusicState] that is replaced whole; the flows below are views of it.
 */
class MusicRepository(
  private val context: Context,
  database: Lazy<AppDatabase> = lazy { AppDatabase.open(context) },
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
  private val db by database
  private val worker = SerialWorker(scope) { Diagnostics.record("MusicRepository", "a library write failed", it) }

  /** Everything the library is made of. Never changed in place. */
  class MusicState(
    val demo: List<Track> = emptyList(),
    val picked: List<Track> = emptyList(),
    val catalog: DeviceCatalog = DeviceCatalog(),
    val rules: FolderRules = FolderRules.None
  ) {
    /** The device songs the folder choice leaves in. */
    val device: List<Track> = catalog.tracks.filter { rules.isIncluded(catalog.folderOf[it.id].orEmpty()) }

    /** The library: picked files, the device's music, then the demo tones. */
    val tracks: List<Track> = picked + device + demo

    fun copy(
      demo: List<Track> = this.demo, picked: List<Track> = this.picked,
      catalog: DeviceCatalog = this.catalog, rules: FolderRules = this.rules
    ) = MusicState(demo, picked, catalog, rules)
  }

  private val _state = MutableStateFlow(MusicState())
  val state: StateFlow<MusicState> = _state.asStateFlow()

  private val _tracks = MutableStateFlow<List<Track>>(emptyList())
  val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

  private val _deviceTracks = MutableStateFlow<List<Track>>(emptyList())
  /** The music on the device, from Android's media library (needs the music permission). */
  val deviceTracks: StateFlow<List<Track>> = _deviceTracks.asStateFlow()

  private val _deviceFolders = MutableStateFlow<Map<String, Int>>(emptyMap())
  /** How many songs each folder holds on the device, counting the ones the folder choice leaves out. */
  val deviceFolders: StateFlow<Map<String, Int>> = _deviceFolders.asStateFlow()

  private val _folderRules = MutableStateFlow(FolderRules.None)
  /** Which folders are in the library. */
  val folderRules: StateFlow<FolderRules> = _folderRules.asStateFlow()

  private val _currentTrack = MutableStateFlow<Track?>(null)
  val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

  /** Replaces the state whole and publishes the views of it; one writer at a time. */
  private fun update(change: (MusicState) -> MusicState) = synchronized(_state) {
    val old = _state.value
    val new = change(old)
    _state.value = new
    _tracks.value = new.tracks
    _deviceTracks.value = new.device
    if (new.catalog !== old.catalog) _deviceFolders.value = new.catalog.folderCounts
    _folderRules.value = new.rules
  }

  init {
    worker.submit {
      LibraryMigration.ensure(context, db)
      val loaded = db.tracks().picked().map { it.toTrack() }.reversed() // newest first
      update { s -> s.copy(picked = s.picked + loaded.filter { l -> s.picked.none { it.id == l.id } }) }
    }
  }

  /** Returns once what was asked so far is written. */
  suspend fun idle() = worker.idle()

  /** Applies the folder choice to the songs already read, without reading the media library again. */
  fun setFolderRules(rules: FolderRules) = update { it.copy(rules = rules) }

  suspend fun initializeCatalog() {
    val fileMap = AudioSynthesizer.ensureDemoTracksExist(context)

    val demoTracks = listOf(
      Track(
        id = "track_tarkus",
        title = "Tarkus: Eruption Suite",
        artist = "Emerson, Lake & Palmer",
        album = "Tarkus (1971)",
        durationMs = 16000L,
        genre = context.getString(R.string.demo_genre_prog_rock),
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
        genre = context.getString(R.string.demo_genre_minimal_prog),
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
        genre = context.getString(R.string.demo_genre_impressionist),
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
        genre = context.getString(R.string.demo_genre_ambient_modal),
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
        genre = context.getString(R.string.demo_genre_calibration),
        era = "2026",
        filePath = fileMap["track_sweep"],
        colorHex = 0xFF7A8A99,
        isDemo = true
      )
    )

    // No track is picked by itself: the mini player (the way into the player) appears only once
    // the user chooses something to play.
    update { it.copy(demo = demoTracks) }
  }

  private val scanLock = Mutex()
  private var observer: ContentObserver? = null
  private var rescan: Job? = null

  /**
   * Brings the device songs up to date with Android's media library and returns how many there are.
   * Reads only the id and the stamps of every song first; the full rows are read just for the songs
   * that are new or changed ([MediaDiff]). Nothing changed: no row is read again.
   */
  suspend fun scanDevice(): Int = withContext(Dispatchers.IO) {
    scanLock.withLock {
      val collection = collection()
      val known = _state.value.catalog
      val diff = MediaDiff.of(known.stamps, readStamps(collection))
      val catalog = if (diff.isEmpty) known else {
        val ids = if (diff.readsAll(known.stamps.size)) null else diff.toRead
        known.apply(diff, readSongs(collection, ids), COLLATOR)
      }
      if (catalog !== known) update { it.copy(catalog = catalog) }
      watch()
      catalog.tracks.size
    }
  }

  private fun collection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
  } else {
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  }

  private val generationColumn: String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.MediaColumns.GENERATION_MODIFIED else null

  private val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
  private val selectionArgs = arrayOf(MIN_SONG_MS.toString())

  /** The id and stamp of every song, cheap. Empty when the permission was taken back. */
  private fun readStamps(collection: Uri): Map<Long, Stamp> {
    val out = HashMap<Long, Stamp>()
    try {
      val projection = listOfNotNull(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATE_MODIFIED, generationColumn
      ).toTypedArray()
      context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { c ->
        while (c.moveToNext()) {
          out[c.getLong(0)] = Stamp(c.getLong(1), if (generationColumn != null) c.getLong(2) else 0L)
        }
      }
    } catch (e: Exception) {
      Diagnostics.record("MusicRepository", "device music stamps not read", e)
    }
    return out
  }

  /** The full rows of the songs in [ids], or of every song when [ids] is null. */
  private fun readSongs(collection: Uri, ids: Set<Long>?): List<ScannedSong> {
    val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val where = if (q) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
    val projection = listOfNotNull(
      MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DATE_ADDED,
      where, MediaStore.Audio.Media.DATE_MODIFIED, generationColumn
    ).toTypedArray()
    val out = ArrayList<ScannedSong>()
    val groups: List<List<Long>?> = ids?.chunked(ID_CHUNK) ?: listOf(null)
    for (group in groups) {
      try {
        val idFilter = group?.let { " AND ${MediaStore.Audio.Media._ID} IN (${it.joinToString(",")})" }.orEmpty()
        context.contentResolver.query(collection, projection, selection + idFilter, selectionArgs, null)?.use { c ->
          val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
          val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
          val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
          val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
          val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
          val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
          val year = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
          val trackNo = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
          val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
          val path = c.getColumnIndexOrThrow(where)
          val modified = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
          val generation = generationColumn?.let { c.getColumnIndexOrThrow(it) }
          while (c.moveToNext()) {
            val mediaId = c.getLong(id)
            val folder = c.getString(path).let { if (q) FolderPaths.normalize(it) else FolderPaths.ofFile(it) }
            val (disc, number) = com.example.data.playback.AlbumOrder.discAndTrack(c.getInt(trackNo))
            val track = Track(
              id = "ms:$mediaId",
              title = c.getString(title) ?: Placeholders.untitled(),
              artist = c.getString(artist)?.takeUnless { it == MediaStore.UNKNOWN_STRING } ?: Placeholders.unknownArtist(),
              album = c.getString(album)?.takeUnless { it == MediaStore.UNKNOWN_STRING } ?: "",
              durationMs = c.getLong(duration),
              genre = "",
              era = c.getInt(year).takeIf { it > 0 }?.toString() ?: "",
              isLocalFile = true,
              filePath = ContentUris.withAppendedId(collection, mediaId).toString(),
              artworkUrl = ContentUris.withAppendedId(ALBUM_ART, c.getLong(albumId)).toString(),
              colorHex = 0xFF2F8FA8,
              albumId = c.getLong(albumId).takeIf { it > 0 },
              discNumber = disc,
              trackNumber = number,
              dateAddedSec = c.getLong(added)
            )
            out += ScannedSong(mediaId, track, folder, Stamp(c.getLong(modified), generation?.let { c.getLong(it) } ?: 0L))
          }
        }
      } catch (e: Exception) {
        // The permission was taken back, or the media library refused the query: keep what was read.
        Diagnostics.record("MusicRepository", "device music not fully read", e)
      }
    }
    return out
  }

  /** Listens to the media library once, so a song added or removed shows up without opening the tab again. */
  private fun watch() {
    if (observer != null) return
    val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
      override fun onChange(selfChange: Boolean) {
        rescan?.cancel()
        rescan = scope.launch {
          delay(RESCAN_DELAY_MS) // a download or a tag edit sends many changes in a row
          scanDevice()
        }
      }
    }
    observer = o
    runCatching { context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, o) }
  }

  fun stopWatching() {
    observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
    observer = null
    rescan?.cancel()
  }

  fun selectTrack(track: Track) {
    _currentTrack.value = track
  }

  /**
   * Adds a file picked on the device to the library and returns it. The id comes from the file's
   * address ([TrackIds.picked]), so the same file picked again is the same track: nothing is added
   * twice and its favorites and playlist places stay.
   */
  fun addLocalTrack(title: String, artist: String, uriString: String, durationMs: Long): Track {
    val id = TrackIds.picked(uriString)
    var result: Track? = null
    update { s ->
      val existing = s.tracks.firstOrNull { it.id == id || (it.isLocalFile && it.filePath == uriString) }
      if (existing != null) {
        result = existing
        return@update s
      }
      val track = Track(
        id = id, title = title, artist = artist,
        album = context.getString(R.string.library_local_library),
        durationMs = durationMs,
        genre = context.getString(R.string.library_audio_fallback_name),
        era = "Local", isLocalFile = true, filePath = uriString, colorHex = 0xFF3A9FA8,
        dateAddedSec = System.currentTimeMillis() / 1000
      )
      result = track
      s.copy(picked = listOf(track) + s.picked)
    }
    val track = result!!
    // Written on the worker; the date orders picked files, the newest has the biggest.
    worker.submit {
      if (db.tracks().byIds(listOf(track.id)).none { it.picked }) {
        val last = db.tracks().picked().maxOfOrNull { it.dateAddedSec } ?: 0L
        db.tracks().upsert(listOf(track.toEntity(picked = true, dateAddedSec = maxOf(track.dateAddedSec, last + 1))))
      }
    }
    return track
  }

  companion object {
    /** Shorter files are ringtones, notification sounds and voice notes, not songs. */
    const val MIN_SONG_MS = 30_000L
    private const val ID_CHUNK = 500
    private const val RESCAN_DELAY_MS = 1500L
    private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")

    // Sorted as people read titles (accents and case ignored); the media library refuses a locale sort.
    private val COLLATOR: Comparator<Track> = java.text.Collator.getInstance()
      .apply { strength = java.text.Collator.PRIMARY }
      .let { c -> Comparator { a, b -> c.compare(a.title, b.title) } }
  }
}
