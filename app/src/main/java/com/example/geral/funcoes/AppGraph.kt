package com.example

import android.content.Context
import com.example.data.assistant.CuriositySpeechManager
import com.example.data.audio.AudioEngineManager
import com.example.data.playback.PlaybackController
import com.example.data.repository.CuriosityRepository
import com.example.data.repository.LibraryRepository
import com.example.data.repository.MusicRepository
import com.example.data.repository.PreferencesRepository

/**
 * The parts that live as long as the process, not as long as a screen: the player keeps playing after
 * the activity and its view model are gone (screen off, app swiped away), driven by the media session.
 */
object AppGraph {
  @Volatile private var initialized = false

  lateinit var musicRepository: MusicRepository private set
  lateinit var curiosityRepository: CuriosityRepository private set
  lateinit var audioEngineManager: AudioEngineManager private set
  lateinit var preferencesRepository: PreferencesRepository private set
  lateinit var settings: com.example.ui.viewmodel.SettingsStore private set
  lateinit var curiositySpeechManager: CuriositySpeechManager private set
  lateinit var libraryRepository: LibraryRepository private set
  lateinit var playback: PlaybackController private set

  /** The one database (Fase 3). Opened on first use; every access runs off the main thread. */
  val database: com.example.data.db.AppDatabase by lazy { com.example.data.db.AppDatabase.open(appContext) }
  private lateinit var appContext: Context

  /** Where the player reads each track's waveform; the library side replaces it once it can calculate them. */
  @Volatile var waveformSource: com.example.data.repository.WaveformSource = com.example.data.repository.WaveformSource.None

  /** The shape of every local track, measured in the background and saved in the app folder. */
  lateinit var waveforms: com.example.data.waveform.WaveformRepository private set

  @Synchronized
  fun init(context: Context) {
    if (initialized) return
    // Named so the opening cost can be read in a system trace and compared between versions.
    android.os.Trace.beginSection("AppGraph.init")
    try {
      initGraph(context)
    } finally {
      android.os.Trace.endSection()
    }
  }

  private fun initGraph(context: Context) {
    val app = context.applicationContext
    appContext = app
    com.example.data.diagnostics.Diagnostics.install(app)
    // Configure Coil once with bounded memory (20% heap), 50 MB disk cache, and RGB_565 thumbnails.
    coil.Coil.setImageLoader {
      coil.ImageLoader.Builder(app)
        .memoryCache {
          coil.memory.MemoryCache.Builder(app)
            .maxSizePercent(0.20)
            .build()
        }
        .diskCache {
          coil.disk.DiskCache.Builder()
            .directory(java.io.File(app.cacheDir, "coil_covers"))
            .maxSizeBytes(50L * 1024L * 1024L)
            .build()
        }
        .allowRgb565(true)
        .crossfade(150)
        .build()
    }
    musicRepository = MusicRepository(app, lazy { database })
    curiosityRepository = CuriosityRepository(app)
    audioEngineManager = AudioEngineManager(app)
    preferencesRepository = PreferencesRepository(app)
    settings = com.example.ui.viewmodel.SettingsStore(com.example.ui.viewmodel.PreferencesSettings(preferencesRepository))
    musicRepository.setFolderRules(com.example.data.library.FolderRules.decode(preferencesRepository.getFolderRules()))
    com.example.data.audio.spatial.SpatialControl.assetOpener = { name -> runCatching { app.assets.open(name) }.getOrNull() }
    com.example.data.audio.spatial.SpatialControl.configure(
      enabled = preferencesRepository.getSpatialEnabled(),
      preset = com.example.data.audio.spatial.SpatialPreset.fromKey(preferencesRepository.getSpatialPreset()),
      intensity = preferencesRepository.getSpatialIntensity().toDouble(),
      tunings = com.example.data.audio.spatial.SpatialPreset.entries.associateWith { preferencesRepository.getSpatialTuning(it.key) }
    )
    curiositySpeechManager = CuriositySpeechManager(app)
    com.example.data.model.Placeholders.init(app)
    libraryRepository = LibraryRepository(app, database)
    waveforms = com.example.data.waveform.WaveformRepository(
      app, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    )
    waveformSource = com.example.data.repository.WaveformSource { waveforms.observe(it) }
    playback = PlaybackController(
      context = app,
      musicRepository = musicRepository,
      curiosityRepository = curiosityRepository,
      audioEngine = audioEngineManager,
      preferences = preferencesRepository,
      speech = curiositySpeechManager,
      database = database
    )
    initialized = true
  }
}
