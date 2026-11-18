package com.example.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Desktop twin of geral/funcoes/db/AppDatabase.kt: same entities, same version, same DAOs (those files are
 * compiled from app/src). Only the opening differs: a file under the user's profile, bundled SQLite.
 */
@Database(
  entities = [
    TrackEntity::class, FavoriteEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class,
    PlayStatEntity::class, PlaybackStateEntity::class, SettingEntity::class
  ],
  version = 1,
  exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun tracks(): TrackDao
  abstract fun favorites(): FavoriteDao
  abstract fun playlists(): PlaylistDao
  abstract fun playStats(): PlayStatDao
  abstract fun playback(): PlaybackStateDao
  abstract fun settings(): SettingDao

  companion object {
    const val NAME = "tarkas.db"

    /** %APPDATA%\TARKAS on Windows, ~/.tarkas elsewhere. */
    fun defaultFile(): File {
      val base = System.getenv("APPDATA")?.let { File(it, "TARKAS") } ?: File(System.getProperty("user.home"), ".tarkas")
      base.mkdirs()
      return File(base, NAME)
    }

    fun open(file: File = defaultFile()): AppDatabase =
      Room.databaseBuilder<AppDatabase>(file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    /** For tests: lives in memory, gone on close. */
    fun inMemory(): AppDatabase =
      Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
  }
}
