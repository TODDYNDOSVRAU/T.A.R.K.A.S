package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The one database of the app (Fase 3). Version 1 holds what used to live in SharedPreferences as
 * JSON: library, play statistics, player state and settings. The schema of every version is exported
 * to `app/schemas`, and a change of version needs a migration plus a test (`MigrationTestHelper`).
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

    fun open(context: Context): AppDatabase =
      Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME).build()
  }
}
