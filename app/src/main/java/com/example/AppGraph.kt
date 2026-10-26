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
  lateinit var curiositySpeechManager: CuriositySpeechManager private set
  lateinit var libraryRepository: LibraryRepository private set
  lateinit var playback: PlaybackController private set

  @Synchronized
  fun init(context: Context) {
    if (initialized) return
    val app = context.applicationContext
    musicRepository = MusicRepository(app)
    curiosityRepository = CuriosityRepository()
    audioEngineManager = AudioEngineManager(app)
    preferencesRepository = PreferencesRepository(app)
    curiositySpeechManager = CuriositySpeechManager(app)
    libraryRepository = LibraryRepository(app)
    playback = PlaybackController(
      context = app,
      musicRepository = musicRepository,
      curiosityRepository = curiosityRepository,
      audioEngine = audioEngineManager,
      preferences = preferencesRepository,
      speech = curiositySpeechManager
    )
    initialized = true
  }
}
